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
  7. audio      audio.py: TTS -> site + Telegram (its own failure never fails the run)
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


def build_prompt(now, context, prev_date, prev_md, problems=None):
    with open(os.path.join(config.CODE, "prompts", "daily.md"), encoding="utf-8") as fh:
        text = fh.read()
    text = (text.replace("{{DATE_LONG}}", long_date(now)).replace("{{TIME}}", f"{now:%H:%M}")
            .replace("{{CONTEXT}}", context.strip()).replace("{{PREVIOUS_DATE}}", prev_date)
            .replace("{{PREVIOUS}}", prev_md.strip()))
    if problems:
        text += ("\n\nYOUR PREVIOUS ATTEMPT WAS REJECTED by the format checker for these reasons:\n- "
                 + "\n- ".join(problems) + "\nWrite the complete newsletter again and fix them.\n")
    return text


def claude(prompt, run):
    cmd = [config.CLAUDE, "-p", "--model", config.MODEL,
           "--tools", "WebSearch", "WebFetch", "--allowedTools", "WebSearch", "WebFetch",
           "--no-session-persistence", "--strict-mcp-config", "--setting-sources", "project",
           "--output-format", "json"]
    t0 = time.time()
    res = subprocess.run(cmd, input=prompt, capture_output=True, text=True, encoding="utf-8",
                         errors="replace", timeout=45 * 60, cwd=config.HOME)
    try:
        out = json.loads(res.stdout)
    except Exception:
        raise RuntimeError(f"claude exited {res.returncode}: {(res.stderr or res.stdout).strip()[:400]}")
    run.log(f"writer: {config.MODEL}, {out.get('num_turns')} turns, {time.time() - t0:.0f}s, "
            f"${out.get('total_cost_usd') or 0:.2f}, error={out.get('is_error')}")
    if out.get("is_error"):
        raise RuntimeError("claude reported an error: " + str(out.get("result"))[:400])
    return out.get("result") or "", out


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
        problems, md = None, ""
        for attempt in (1, 2):
            try:
                raw, _ = claude(build_prompt(now, context, prev_date, prev_md, problems), run)
            except Exception as exc:
                if attempt == 2:
                    raise
                run.log("writer failed, retrying:", exc)
                continue
            md = extract_markdown(raw)
            problems = check(md, now)
            if not problems:
                break
            run.log(f"attempt {attempt} rejected:", "; ".join(problems))
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

    step = "audio"
    try:
        r = sh([PY, os.path.join(config.CODE, "audio.py"), today], 900)
        run.log("audio:", (r.stdout or r.stderr).strip().replace("\n", " | ")[-400:])
    except Exception as exc:
        run.log("audio failed (the 08:40 fallback task retries):", exc)
    run.log("=== done")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
