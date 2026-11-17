"""Sunday 09:00 week-in-review (replaces the Hermes weekly roll-up).

Headlines come from the last 7 editions/*.json by code, so the writer cannot claim
a story that never ran; Claude only summarises them (no tools). Sent to Telegram.

    python run_weekly.py [--print]   --print shows the message instead of sending it
"""
import datetime
import glob
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
import tg  # noqa: E402

PROMPT = """Write the weekly review of my daily briefing. This is a Telegram message, nothing else: no preamble, no sign-off, no markdown headers.

The headlines of the last {n} editions are below, grouped by date and section. They are the ONLY source.

{editions}

Write, in this order and nothing more:

Line 1: "Week in review — {first} to {last}."
Line 2: blank.
Then **The three that mattered** followed by 3 bullets: the stories that shaped the week, each one line, no links.
Then **What resolved** — 1-2 bullets of threads that closed.
Then **What escalated** — 1-2 bullets of threads that got worse or bigger.
Then **What to watch** — 2 bullets for next week, specific and falsifiable (a vote, a court date, a fixture, an earnings call).

RULES
- Only claim a story appeared if its headline is in the list above. Never invent a story or a trend.
- If there are fewer than 4 editions, say so in one line and summarise what exists instead of padding.
- Under 2000 characters total. One line per bullet. No links, no URLs, no emoji beyond the bold section names.
"""


def editions(days=7):
    files = sorted(glob.glob(os.path.join(config.SITE_DIR, "editions", "20*.json")))[-days:]
    out = []
    for f in files:
        with open(f, encoding="utf-8") as fh:
            ed = json.load(fh)
        lines = [f"## {ed['date']}"]
        for sec in ed.get("sections", []):
            heads = [i.get("headline") for i in sec.get("items", []) if i.get("type") == "story" and i.get("headline")]
            if heads:
                lines.append(f"{sec['name']}: " + " | ".join(heads))
        out.append((ed["date"], "\n".join(lines)))
    return out


def main(argv):
    eds = editions()
    if not eds:
        print("no editions")
        return 1
    fmt = lambda d: datetime.date.fromisoformat(d).strftime("%a %d %b")
    prompt = PROMPT.format(n=len(eds), editions="\n\n".join(t for _, t in eds), first=fmt(eds[0][0]), last=fmt(eds[-1][0]))
    res = subprocess.run([config.CLAUDE, "-p", "--model", config.MODEL, "--tools", "", "--no-session-persistence",
                          "--strict-mcp-config", "--setting-sources", "project", "--output-format", "json"],
                         input=prompt, capture_output=True, text=True, encoding="utf-8", errors="replace",
                         timeout=600, cwd=config.HOME)
    try:
        text = (json.loads(res.stdout).get("result") or "").strip()
    except Exception:
        text = ""
    if not text:
        print("weekly writer failed:", (res.stderr or res.stdout)[:400])
        return 1
    if "--print" in argv:
        print(text)
        return 0
    ok = tg.send_text(text[:3900])
    print("weekly sent:", ok)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
