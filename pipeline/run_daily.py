"""The morning run (replaces the Hermes 08:00 job).

    python run_daily.py            normal scheduled run; exits early if today is already published
    python run_daily.py --force    run again even if today is published (treats it as a draft)
    python run_daily.py --dry-run  write + render, but do not publish or send anything

Steps, each logged to ~/.briefing/logs/daily-<date>.log:
  1. context    briefing_context.py: workout (Hevy), market tape, Dalamud version, standing prefs
  2. write      headless Claude with WebSearch/WebFetch writes the markdown (one retry on bad output)
  3. render     render_briefing.py -> dated page, index, archive, feeds, edition json, og card
  4. validate   feed_validate.py (the contract the Android app depends on)
  5. publish    publish.py: one commit with only the generated files, then wait until Pages serves it
  6. digest     one line per section to Telegram
Any failure in 1-5 sends one Telegram alert with the step and the log path.
"""
import datetime
import json
import os
import re
import subprocess
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
import publish  # noqa: E402
import tg  # noqa: E402

PY = sys.executable
STATE = os.path.join(config.HOME, "state.json")
HEADING_RE = re.compile(r"^\*\*(.+?)\*\*\s*$")


class Run:
    def __init__(self, date):
        os.makedirs(config.LOG_DIR, exist_ok=True)
        self.path = os.path.join(config.LOG_DIR, f"daily-{date}.log")
        self.fh = open(self.path, "a", encoding="utf-8")

    def log(self, *parts):
        line = f"[{datetime.datetime.now():%H:%M:%S}] " + " ".join(str(p) for p in parts)
        print(line, flush=True)
        self.fh.write(line + "\n")
        self.fh.flush()


def sh(cmd, timeout, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace",
                          timeout=timeout, cwd=config.CODE, **kw)


def load_state():
    try:
        with open(STATE, encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def save_state(**kw):
    st = load_state()
    st.update(kw)
    with open(STATE, "w", encoding="utf-8") as fh:
        json.dump(st, fh, indent=1)


def long_date(d):
    return f"{d:%A}, {d.day} {d:%B %Y}"


def previous_edition(today):
    names = sorted(n for n in os.listdir(config.MD_DIR) if re.fullmatch(r"\d{4}-\d{2}-\d{2}\.md", n))
    if f"{today}.md" in names:
        pick = f"{today}.md"  # a same-day draft: the prompt tells the writer how to treat it
    else:
        older = [n for n in names if n < f"{today}.md"]
        pick = older[-1] if older else None
    if not pick:
        return "(none)", "(no previous edition)"
    with open(os.path.join(config.MD_DIR, pick), encoding="utf-8") as fh:
        return pick[:-3], fh.read()


# sections whose lines are schedules or results, where a link can legitimately come back
REPEATABLE = {"🔜 Coming up", "📅 Tonight", "🏀 NBA", "⚽ Football"}
LINK_RE = re.compile(r"(https?://\S+?)[.,;)]?\s*(?:\(paywall\))?\s*$", re.I)


def recent_stories(today, days=7):
    """{url: (date, headline)} for every story of the last week's editions, today's drafts excluded.

    The writers only see yesterday's edition, so a story from two or three days back came round
    again (seven repeats on 2026-11-27). This list goes into the prompt, and write_group drops
    any story whose link is on it."""
    cutoff = (datetime.date.fromisoformat(today) - datetime.timedelta(days=days)).isoformat()
    skip = {norm(n) for n in REPEATABLE}
    out = {}
    for name in sorted(os.listdir(config.MD_DIR)):
        m = re.fullmatch(r"(\d{4}-\d{2}-\d{2})\.md", name)
        if not m or not cutoff <= m.group(1) < today:
            continue
        section = None
        with open(os.path.join(config.MD_DIR, name), encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                head = HEADING_RE.match(line)
                if head:
                    section = norm(head.group(1))
                    continue
                link = LINK_RE.search(line) if line.startswith("- ") and section not in skip else None
                if link:
                    head = re.split(r"\s+[—–]\s+", line[2:], maxsplit=1)[0][:110]
                    out[link.group(1)] = (m.group(1), head, section)
    return out


def recent_listing(recent, names):
    """The prompt's <recently_run> list, for this writer's own sections only (the full week is ~10k tokens)."""
    mine = {norm(n) for n in names}
    rows = sorted((d, h, u) for u, (d, h, sec) in recent.items() if sec in mine)
    return "\n".join(f"- {d} — {h} — {u}" for d, h, u in rows) or "(none)"


def drop_repeats(got, recent):
    """Remove story lines whose link already ran this week. Returns (got, [descriptions])."""
    skip = {norm(n) for n in REPEATABLE}
    dropped = []
    for name, lines in got.items():
        if norm(name) in skip:
            continue
        keep = []
        for line in lines:
            link = LINK_RE.search(line.strip()) if line.lstrip().startswith("- ") else None
            if link and link.group(1) in recent:
                date, head, _section = recent[link.group(1)]
                dropped.append(f'{name}: "{head}" (ran {date})')
                continue
            keep.append(line)
        got[name] = keep
    return got, dropped


# Eight writers research in parallel, each owning a beat, then an editor writes the top block.
# One writer for all fifteen sections skimped (2 Polish stories, no long read); split, each
# beat gets a full research budget and wall time stays the same.
TOP = ["🎯 TL;DR", "🔄 What changed", "🔁 Following"]
GROUPS = [
    ("world", ["🌍 World", "🏛️ Brussels"]),
    ("poland-money", ["🇵🇱 Polska", "🧜‍♀️ Warszawa", "💰 Money"]),
    ("tech-games", ["💻 AI & Tech", "🎮 Games & Dev", "🐉 World of Warcraft Forever"]),
    ("culture", ["🎵 Music", "🎬 Culture", "📌 Long read"]),
    ("football", ["📅 Tonight", "⚽ Football"]),
    ("nba", ["🏀 NBA", "🏎️ F1"]),
    ("science", ["🔬 Science"]),
    ("agenda", ["🔜 Coming up"]),
]
ORDER = TOP + ["🌍 World", "🇵🇱 Polska", "🧜‍♀️ Warszawa", "💰 Money", "💻 AI & Tech", "🔬 Science", "🎮 Games & Dev",
               "🐉 World of Warcraft Forever", "🎵 Music", "🎬 Culture", "🏛️ Brussels",
               "🔜 Coming up", "📅 Tonight", "🏀 NBA", "⚽ Football", "🏎️ F1", "📌 Long read"]
# fewest story lines a section may come back with before its writer is asked once more
MINIMUM = {"🌍 World": 4, "🇵🇱 Polska": 4, "💰 Money": 4, "💻 AI & Tech": 4, "🎮 Games & Dev": 3,
           "🎵 Music": 4, "🎬 Culture": 4, "🏛️ Brussels": 2, "🔬 Science": 4, "🧜‍♀️ Warszawa": 3, "🏎️ F1": 2, "📌 Long read": 1}


def prompt_file(name):
    with open(os.path.join(config.CODE, "prompts", name), encoding="utf-8") as fh:
        return fh.read()


def section_blocks():
    blocks = {}
    for block in re.split(r"\n(?=\*\*[^*\n]+\*\*\n)", "\n" + prompt_file("sections.md")):
        block = block.strip()
        if block:
            blocks[block.splitlines()[0].strip("* ")] = block
    return blocks


def norm(name):
    return re.sub(r"\W+", "", name).lower()


def fill(text, **values):
    for key, value in values.items():
        text = text.replace("{{" + key + "}}", value)
    return text


def split_sections(md):
    """{heading name: [lines]} for every **heading** in md, in order."""
    out, cur = {}, None
    for line in md.splitlines():
        m = HEADING_RE.match(line.strip())
        if m:
            cur = m.group(1).strip()
            out[cur] = []
        elif cur is not None and line.strip():
            out[cur].append(line.rstrip())
    return out


def claude(prompt, run, label, tools=True, timeout=30 * 60):
    cmd = [config.CLAUDE, "-p", "--model", config.MODEL]
    cmd += ["--tools", "WebSearch", "WebFetch", "--allowedTools", "WebSearch", "WebFetch"] if tools else ["--tools", ""]
    cmd += ["--no-session-persistence", "--strict-mcp-config", "--setting-sources", "project", "--output-format", "json"]
    t0 = time.time()
    res = subprocess.run(cmd, input=prompt, capture_output=True, text=True, encoding="utf-8",
                         errors="replace", timeout=timeout, cwd=config.HOME)
    try:
        out = json.loads(res.stdout)
    except Exception:
        raise RuntimeError(f"{label}: claude exited {res.returncode}: {(res.stderr or res.stdout).strip()[:300]}")
    run.log(f"{label}: {out.get('num_turns')} turns, {time.time() - t0:.0f}s, "
            f"${out.get('total_cost_usd') or 0:.2f}, error={out.get('is_error')}")
    if out.get("is_error"):
        raise RuntimeError(f"{label}: claude reported an error: " + str(out.get("result"))[:300])
    return re.sub(r"^```[a-z]*\s*$", "", (out.get("result") or "").strip(), flags=re.M).strip()


MERGE_FLOOR = 3   # never thin a section below this to remove a duplicate


def merge_duplicates(parts, run):
    """The same event written up by two writers (they run in parallel and can't see each other):
    keep it in the section that owns the subject and drop the other copy.

    Matching is by meaning, not words: World is in English and Money in Polish, so one short
    model call with no tools reads the headlines and names the duplicates. Never raises."""
    skip = {norm(n) for n in REPEATABLE | {"📌 Long read"}}
    index, rows = {}, []
    for name, lines in parts.items():
        if norm(name) in skip:
            continue
        for i, line in enumerate(lines):
            if line.lstrip().startswith("- ") and LINK_RE.search(line.strip()):
                sid = f"S{len(index) + 1}"
                index[sid] = (name, i)
                text = re.sub(r"\s*·\s*matters:.*$|\s+https?://\S+\s*$", "", line.strip()[2:])
                rows.append(f"{sid} [{name}] {text[:220]}")
    if len(rows) < 2:
        return parts
    try:
        answer = claude(fill(prompt_file("merge.md"), STORIES="\n".join(rows)), run, "merge", tools=False, timeout=300)
        drop = json.loads(re.search(r"\{.*\}", answer, re.S).group(0)).get("drop", [])
    except Exception as exc:
        run.log("merge skipped:", exc)
        return parts
    gone = {}
    for sid in drop:
        if sid not in index:
            continue
        name, i = index[sid]
        stories = sum(1 for l in parts[name] if l.lstrip().startswith("- ") and LINK_RE.search(l.strip()))
        if stories - len(gone.get(name, set())) <= MERGE_FLOOR:
            run.log(f"merge: kept {sid} in {name}, which is down to {MERGE_FLOOR} stories")
            continue
        gone.setdefault(name, set()).add(i)
        run.log(f"merge: dropped from {name}:", parts[name][i].strip()[2:90])
    return {n: [l for i, l in enumerate(lines) if i not in gone.get(n, set())] for n, lines in parts.items()}


def shortfalls(names, got):
    problems = []
    for name in names:
        lines = [l for l in got.get(name, []) if l.lstrip().startswith("- ")]
        need = MINIMUM.get(name, 0)
        if need and len(lines) < need:
            problems.append(f"{name} has {len(lines)} stories; its rule asks for more. Search harder before settling.")
        if name in MINIMUM and name != "📌 Long read":
            bare = [l for l in lines if re.search(r"https?://", l) and "matters:" not in l]
            if len(bare) > 1:
                problems.append(f"{name}: {len(bare)} story lines lack the mandatory '· matters:' clause.")
        if name == "⚽ Football":
            problems += [f"Football: {g}. List EVERY scorer with the minute." for g in scorer_gaps(lines)]
    return problems


SCORE_LINE = re.compile(r"^-\s*(.+?)\s+(\d{1,2})\s*[-–]\s*(\d{1,2})\s+(.+?)(?:\s+[—–]\s+(.*))?$")
GOAL_MINUTE = re.compile(r"\d{1,3}(?:\+\d+)?'(?:\+\d+')?")


def scorer_gaps(lines):
    """Score lines that name fewer goal minutes than goals were scored, e.g. a 4-0 with one scorer."""
    gaps = []
    for line in lines:
        m = SCORE_LINE.match(line.strip())
        if not m:
            continue
        goals = int(m.group(2)) + int(m.group(3))
        found = len(GOAL_MINUTE.findall(m.group(5) or ""))
        if goals and found < goals:
            gaps.append(f"'{m.group(1)} {m.group(2)}-{m.group(3)} {m.group(4)}' names {found} of {goals} goals")
    return gaps


def write_group(key, names, base, blocks, run, recent=None):
    sections = "\n\n".join(blocks[n] for n in names if n in blocks)
    matches = []
    if "⚽ Football" in names:
        # scorers come from code (ESPN match data), not from whatever a search finds
        try:
            import football_data
            matches = football_data.collect()
            data = football_data.block(matches)
        except Exception as exc:
            data = f"FOOTBALL DATA unavailable this morning ({type(exc).__name__}): find scorers from match reports."
        run.log(data.splitlines()[0], f"({len(data.splitlines()) - 1} matches)")
        sections += "\n\n<football_data>\n" + data + "\n</football_data>"
    if "🏀 NBA" in names:
        # the slate, scores and performers come from ESPN's JSON; the writer adds the context
        try:
            import nba_data
            data = nba_data.block(nba_data.collect())
        except Exception as exc:
            data = f"NBA DATA unavailable this morning ({type(exc).__name__}): use the ESPN scoreboard yourself."
        run.log(data.splitlines()[0], f"({len(data.splitlines()) - 1} games)")
        sections += "\n\n<nba_data>\n" + data + "\n</nba_data>"
    if "🔜 Coming up" in names:
        # F1 sessions and UEFA nights are known exactly; the writer adds everything else
        try:
            import agenda_data
            data = agenda_data.block(agenda_data.collect())
        except Exception as exc:
            data = f"CALENDAR DATA unavailable this morning ({type(exc).__name__}): check Jolpica and ESPN yourself."
        run.log(data.splitlines()[0][:60], f"({len(data.splitlines()) - 1} entries)")
        sections += "\n\n<calendar_data>\n" + data + "\n</calendar_data>"
    feedback, best = "", {}
    for attempt in (1, 2):
        prompt = fill(prompt_file("researcher.md"), NAMES=", ".join(names), SECTIONS=sections,
                      FEEDBACK=feedback, **base)
        prompt = fill(prompt, RECENT=recent_listing(recent or {}, names))
        try:
            got = split_sections(claude(prompt, run, f"writer[{key}#{attempt}]"))
        except Exception as exc:
            run.log(f"writer[{key}] failed:", exc)
            continue
        canon = {norm(n): n for n in names}  # "🏛 Brussels" and "🏛️ Brussels" are the same section
        got = {canon[norm(n)]: v for n, v in got.items() if norm(n) in canon}
        got, repeats = drop_repeats(got, recent or {})
        if repeats:
            run.log(f"writer[{key}] repeats dropped:", "; ".join(repeats))
        for n, lines in got.items():  # keep the fuller version of each section across attempts
            if len(lines) >= len(best.get(n, [])):  # ties go to the newer, feedback-driven draft
                best[n] = lines
        problems = shortfalls(names, best)
        if not problems:
            break
        run.log(f"writer[{key}] short:", "; ".join(problems))
        feedback = ("\nA FIRST DRAFT CAME BACK SHORT. Fix this and return ALL your sections again in full:\n- "
                    + "\n- ".join(problems) + "\n")
        if repeats:
            feedback += ("These stories already ran earlier this week and were removed; replace them with new ones:\n- "
                         + "\n- ".join(repeats) + "\n")
    if matches and "⚽ Football" in best:
        best["⚽ Football"], patched = patch_scorers(best["⚽ Football"], matches)
        if patched:
            run.log(f"football: filled scorers from ESPN data on {patched} line(s) the writer left short")
        left = scorer_gaps(best["⚽ Football"])
        if left:
            run.log("football: still missing scorers (not in ESPN data):", "; ".join(left))
    return best


def _team_key(name):
    return re.sub(r"[^a-z]", "", name.lower().replace("fc", "").replace(".", ""))


def patch_scorers(lines, matches):
    """Last line of defence: rewrite any short score line from ESPN's goal list, when ESPN has the match."""
    index = {(_team_key(m["home"]), _team_key(m["away"])): m for m in matches if m["scorers_complete"]}
    out, patched = [], 0
    for line in lines:
        m = SCORE_LINE.match(line.strip())
        if m and scorer_gaps([line]):
            hit = index.get((_team_key(m.group(1)), _team_key(m.group(4))))
            if hit:
                surname = lambda s: re.sub(r"^(?:\S+\s+)+(?=\S+ \d)", "", s)  # "Florian Wirtz 60'" -> "Wirtz 60'"
                home = ", ".join(surname(x) for x in hit["home_scorers"])
                away = ", ".join(surname(x) for x in hit["away_scorers"])
                line = f"- {m.group(1)} {m.group(2)} - {m.group(3)} {m.group(4)} — {home or '-'}; {away or '-'}"
                patched += 1
        out.append(line)
    return out, patched


def write_edition(now, context, prev_date, prev_md, run):
    from concurrent.futures import ThreadPoolExecutor
    blocks = section_blocks()
    recent = recent_stories(now.date().isoformat())   # {{RECENT}} stays for write_group to fill per writer
    base = {"DATE_LONG": long_date(now), "TIME": f"{now:%H:%M}",
            "COMMON": fill(prompt_file("common.md"), CONTEXT=context.strip(), PREVIOUS_DATE=prev_date,
                           PREVIOUS=prev_md.strip(), DATE_LONG=long_date(now))}
    with ThreadPoolExecutor(len(GROUPS)) as pool:
        futures = [pool.submit(write_group, k, names, base, blocks, run, recent) for k, names in GROUPS]
        parts = {}
        for f in futures:
            parts.update(f.result())
    # belt and braces: the prompt forbids process notes, this drops any that slip through
    junk = re.compile(r"^\s*(sources?:|note:|i could not|i couldn't)", re.I)
    parts = {n: [l for l in lines if not junk.match(l)] for n, lines in parts.items()}
    # a home side that didn't score leaves "— ; Sturm 29'": drop the empty half
    if "⚽ Football" in parts:
        parts["⚽ Football"] = [re.sub(r"\s[—–]\s*[-–]?\s*;\s*", " — ", l) for l in parts["⚽ Football"]]
    parts = merge_duplicates(parts, run)
    body ="\n\n".join(f"**{n}**\n" + "\n".join(parts[n]) for n in ORDER if n not in TOP and parts.get(n))
    if not body:
        raise RuntimeError("every writer failed")

    top = claude(fill(prompt_file("editor.md"), DATE_LONG=long_date(now), CONTEXT=context.strip(),
                      PREVIOUS_DATE=prev_date, PREVIOUS=prev_md.strip(), BODY=body,
                      TOP_SECTIONS="\n\n".join(blocks[n] for n in TOP)),
                 run, "editor", tools=False, timeout=10 * 60)
    return extract_markdown(top.strip() + "\n\n" + body)


def extract_markdown(text):
    text = re.sub(r"^```[a-z]*\s*$", "", text.strip(), flags=re.M).strip()
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.strip().lower().startswith("good morning"):
            return "\n".join(lines[i:]).strip() + "\n"
    return text + "\n"


def check(md, now):
    """Problems that would break the page or betray a bad run. Empty list = fine."""
    lines = [l.strip() for l in md.splitlines() if l.strip()]
    problems = []
    if not lines or not lines[0].lower().startswith("good morning"):
        problems.append("the first line must be the greeting starting 'Good morning'")
    elif str(now.day) not in lines[0] or now.strftime("%B") not in lines[0]:
        problems.append(f"the greeting must carry today's date, {long_date(now)}")
    heads = [l for l in lines if HEADING_RE.match(l)]
    if len(heads) < 8:
        problems.append(f"only {len(heads)} section headings (**emoji Name**); expected at least 8")
    links = sum(1 for l in lines if l.startswith("- ") and re.search(r"https?://\S+", l))
    if links < 25:
        problems.append(f"only {links} story lines end with a source URL; expected 25 or more")
    no_matters = [l for l in lines if l.startswith("- ") and re.search(r"https?://", l) and "matters:" not in l
                  and not re.search(r"\d+\s*-\s*\d+", l)]
    if len(no_matters) > 6:
        problems.append(f"{len(no_matters)} story lines are missing the mandatory '· matters:' clause")
    return problems


def digest(md):
    lines = [l.strip() for l in md.splitlines() if l.strip()]
    out = [lines[0]]
    if len(lines) > 1 and lines[1][:1] in "💪😴":
        out.append(lines[1])
    cur, firsts = None, {}
    for line in lines:
        m = HEADING_RE.match(line)
        if m:
            cur = m.group(1).strip()
            firsts.setdefault(cur, None)
        elif cur and line.startswith("- ") and firsts.get(cur) is None:
            text = re.sub(r"\s*https?://\S+.*$", "", line[2:])
            text = re.split(r"\s+[—–]\s+|\s*·\s*matters:", text)[0].strip().rstrip(".")
            words = text.split()
            firsts[cur] = " ".join(words[:9]) + ("…" if len(words) > 9 else "")
    out += [f"{name}: {first}" for name, first in firsts.items() if first]
    out.append(config.SITE_URL)
    return "\n".join(out)[:3900]


def main(argv):
    force, dry = "--force" in argv, "--dry-run" in argv
    now = datetime.datetime.now()
    today = now.date().isoformat()
    run = Run(today)
    run.log(f"=== morning run {today} force={force} dry={dry}")
    if not force and not dry and load_state().get("last_published") == today:
        run.log("already published today; use --force to run again")
        return 0

    step = "context"
    try:
        r = sh([PY, os.path.join(config.CODE, "briefing_context.py")], 120)
        context = r.stdout.strip()
        run.log("context:\n  " + context.replace("\n", "\n  "))

        step = "write"
        prev_date, prev_md = previous_edition(today)
        run.log("previous edition:", prev_date)
        md = write_edition(now, context, prev_date, prev_md, run)
        try:  # a score the writer got wrong, or a game it left out, is corrected from ESPN
            import nba_data
            md, fixed = nba_data.fix_markdown(md, nba_data.collect(today))
            if fixed:
                run.log("nba: corrected from ESPN:", "; ".join(fixed))
        except Exception as exc:
            run.log("nba check skipped:", exc)
        try:  # F1 sessions and UEFA nights the agenda writer dropped
            import agenda_data
            md, added = agenda_data.fix_markdown(md, agenda_data.collect(today))
            if added:
                run.log("agenda: added from data:", "; ".join(added))
        except Exception as exc:
            run.log("agenda check skipped:", exc)
        problems = check(md, now)
        if problems:
            heads = sum(1 for l in md.splitlines() if HEADING_RE.match(l.strip()))
            if heads < 4 or not md.lower().startswith("good morning"):
                raise RuntimeError("writer output unusable: " + "; ".join(problems))
            run.log("publishing a thinner edition despite:", "; ".join(problems))

        md_path = os.path.join(config.MD_DIR, f"{today}.md")
        with open(md_path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(md)
        run.log(f"wrote {md_path} ({len(md)} chars)")

        step = "render"
        out_html = os.path.join(config.HOME, "cache", "render-out.html")
        r = sh([PY, os.path.join(config.CODE, "render_briefing.py"), md_path, out_html], 600)
        run.log("render:", (r.stdout or "").strip().replace("\n", " | ")[-600:])
        if r.returncode != 0:
            raise RuntimeError("render failed: " + (r.stderr or r.stdout).strip()[-400:])

        step = "validate"
        r = sh([PY, os.path.join(config.CODE, "feed_validate.py")], 60)
        run.log("validate:", r.stdout.strip().replace("\n", " | "))
        if r.returncode != 0:
            raise RuntimeError("feed.json breaks the app contract: " + r.stdout.strip()[:400])

        if dry:
            run.log("dry run: stopping before publish")
            return 0

        step = "publish"
        sha = publish.publish(publish.edition_files(today), f"briefing: {today}")
        live = publish.wait_live(today)
        run.log(f"published {sha}, live={live}")
        save_state(last_published=today, last_commit=sha, model=config.MODEL)
    except Exception as exc:
        run.log(f"FAILED at {step}: {exc}\n{traceback.format_exc()}")
        if not dry:
            tg.send_text(f"⚠️ Briefing failed at step '{step}': {str(exc)[:300]}\n\nLog: {run.path}")
        return 1

    step = "digest"
    run.log("telegram digest sent:", tg.send_text(digest(md)))

    run.log("=== done")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
