"""Validate a briefing feed against the schema the Android app relies on.

    python feed_validate.py [path-to-feed.json]

Exit 0 when the feed is safe to ship to the app, 1 when a rule the app depends on
is broken. Run it in the pipeline before publishing if the contract ever changes.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
HOME = config.SITE_DIR
STORY_KINDS = {"stories"}
SCORE_KINDS = {"scores"}


def check(feed):
    problems = []

    def need(cond, msg):
        if not cond:
            problems.append(msg)

    need(feed.get("schema") == 1, "schema must be 1 (bump it when the shape changes)")
    need(isinstance(feed.get("date"), str) and len(feed["date"]) == 10, "date must be YYYY-MM-DD")
    need(feed.get("title"), "title is required")
    need(isinstance(feed.get("sections"), list) and feed["sections"], "sections must be a non-empty list")

    for sec in feed.get("sections", []):
        name = sec.get("name", "?")
        need(sec.get("kind") in ("stories", "scores", "fixtures"), f"{name}: bad kind {sec.get('kind')!r}")
        need(isinstance(sec.get("items"), list) and sec["items"], f"{name}: no items")
        for item in sec.get("items", []):
            t = item.get("type")
            need(t in ("story", "score", "fixture", "note"), f"{name}: bad item type {t!r}")
            if t == "story":
                need(item.get("headline"), f"{name}: story without headline")
                if item.get("url"):
                    need(str(item["url"]).startswith("http"), f"{name}: bad url {item.get('url')!r}")
                    need(item.get("source"), f"{name}: url without source")
            if t == "score":
                need(isinstance(item.get("home_score"), int) and isinstance(item.get("away_score"), int),
                     f"{name}: score without integer totals")
                need(item.get("home") and item.get("away"), f"{name}: score without both teams")

    if feed.get("audio"):
        need(str(feed["audio"]).endswith(".mp3"), "audio must point at an mp3")
    return problems


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HOME, "feed.json")
    with open(path, encoding="utf-8") as fh:
        feed = json.load(fh)
    problems = check(feed)
    if problems:
        print(f"INVALID {path}")
        for p in problems:
            print("  -", p)
        return 1
    counts = {}
    for s in feed["sections"]:
        for i in s["items"]:
            counts[i["type"]] = counts.get(i["type"], 0) + 1
    print(f"OK {os.path.basename(path)} — {len(feed['sections'])} sections {counts}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
