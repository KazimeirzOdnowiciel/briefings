"""Last night's NBA slate from ESPN's JSON: every game, its status, final score and top performers.

Two uses:
  * 07:30 — the NBA writer gets block() as authoritative data, so no game is missed and a game
    still being played is labelled as such instead of passed off as a result.
  * 08:50 — fix_markdown() rewrites any line that was not final when the edition went out
    (late West Coast games end around 07:00 in Warsaw) and adds games the writer missed.

    python nba_data.py                 print the block the writer receives
    python nba_data.py fix 2026-11-24  dry-run the 08:50 fix on that edition's markdown
"""
import datetime
import json
import re
import sys
import urllib.request

API = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates={day}"
UA = {"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"}
GAME_URL = "https://www.espn.com/nba/game/_/gameId/{id}"
HEADING = "**🏀 NBA**"


def slate_day(edition_date):
    """An edition dated D covers the US evening of D-1 (07:30 in Warsaw is 01:30 in New York)."""
    return datetime.date.fromisoformat(edition_date) - datetime.timedelta(days=1)


def _stat(text):
    """'31 PTS, 7 AST' -> '31 pts, 7 ast'."""
    return text.lower().replace(" pts", " pts").strip()


def collect(edition_date=None):
    edition_date = edition_date or datetime.date.today().isoformat()
    day = slate_day(edition_date).strftime("%Y%m%d")
    with urllib.request.urlopen(urllib.request.Request(API.format(day=day), headers=UA), timeout=20) as r:
        data = json.load(r)
    games = []
    for event in data.get("events", []):
        comp = event["competitions"][0]
        status = comp["status"]["type"]
        sides = []
        for c in comp["competitors"]:
            lead = next((l["leaders"][0] for l in c.get("leaders", []) if l.get("name") == "rating" and l.get("leaders")), None)
            sides.append({
                "name": c["team"]["shortDisplayName"],
                "city": c["team"].get("location") or c["team"]["shortDisplayName"],
                "score": int(c.get("score") or 0),
                "home": c["homeAway"] == "home",
                "star": lead["athlete"]["displayName"] if lead else None,
                "stat": _stat(lead["displayValue"]) if lead else None,
            })
        winner, loser = sorted(sides, key=lambda s: -s["score"])
        games.append({
            "id": event["id"],
            "final": bool(status.get("completed")),
            "started": status.get("state") != "pre",
            "detail": status.get("detail") or status.get("shortDetail") or "",
            "ot": "OT" in (status.get("detail") or ""),
            "winner": winner, "loser": loser,
        })
    return games


def line(g):
    """The markdown score line for a finished game, in the writer's own format."""
    w, l = g["winner"], g["loser"]
    head = f"- {w['name']} {w['score']} - {l['score']} {l['name']}" + (" (OT)" if g["ot"] else "")
    notes = []
    if w["star"]:
        notes.append(f"{w['star']} {w['stat']} for {w['city']}")
    if l["star"]:
        notes.append(f"{l['city']}'s {l['star']} had {l['stat']}")
    note = "; ".join(notes) + "." if notes else "stats n/a."
    return f"{head} — {note} {GAME_URL.format(id=g['id'])}"


def block(games):
    if not games:
        return "NBA DATA: ESPN lists no games last night."
    out = ["NBA DATA (ESPN, authoritative: every game last night, with status and each side's top performer):"]
    for g in games:
        w, l = g["winner"], g["loser"]
        state = "FINAL" + (" (OT)" if g["ot"] else "") if g["final"] else (
            f"NOT FINAL — still in progress ({g['detail']}); say so in the line" if g["started"] else "NOT STARTED")
        perf = " | ".join(f"{s['name']}: {s['star']} {s['stat']}" for s in (w, l) if s["star"])
        out.append(f"{w['name']} {w['score']} - {l['score']} {l['name']} | {state} | {perf} | {GAME_URL.format(id=g['id'])}")
    return "\n".join(out)


def fix_markdown(md, games):
    """Rewrite NBA lines that were not final, or whose score ESPN now has differently, and add
    finished games the section is missing. Returns (markdown, list of what changed)."""
    lines = md.split("\n")
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == HEADING)
    except StopIteration:
        return md, []
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("**")), len(lines))
    by_id = {g["id"]: g for g in games}
    seen, changes = set(), []
    for i in range(start + 1, end):
        text = lines[i]
        if not text.startswith("- "):
            continue
        m = re.search(r"gameId/(\d+)", text)
        g = by_id.get(m.group(1)) if m else next(
            (g for g in games if g["winner"]["name"] in text and g["loser"]["name"] in text), None)
        if not g:
            continue
        seen.add(g["id"])
        if not g["final"]:
            continue
        w, l = g["winner"], g["loser"]
        stale = re.search(r"not final|in progress|still playing|live\b", text, re.I)
        score_ok = re.search(rf"\b{w['score']}\s*-\s*{l['score']}\b", text)
        if stale or not score_ok:
            lines[i] = line(g)
            changes.append(f"{w['name']} {w['score']}-{l['score']} {l['name']} (was: {text[2:60]}…)")
    missing = [g for g in games if g["final"] and g["id"] not in seen]
    if missing:
        last = max((i for i in range(start + 1, end) if lines[i].startswith("- ")), default=start)
        for k, g in enumerate(missing):
            lines.insert(last + 1 + k, line(g))
            changes.append(f"added {g['winner']['name']} {g['winner']['score']}-{g['loser']['score']} {g['loser']['name']}")
    return "\n".join(lines), changes


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "fix":
        import config
        import os
        date = sys.argv[2]
        md = open(os.path.join(config.MD_DIR, f"{date}.md"), encoding="utf-8").read()
        _, changes = fix_markdown(md, collect(date))
        print("\n".join(changes) or "nothing to fix")
    else:
        print(block(collect(sys.argv[1] if len(sys.argv) > 1 else None)))
