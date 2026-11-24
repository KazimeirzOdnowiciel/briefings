"""08:50 watchdog: silent when today's edition is live, one Telegram warning otherwise.

Checks the LIVE site (feed.json date) rather than local file times, so a run that
rendered but failed to publish is caught too. When everything landed it sends the
phone a web push, if a subscription has been stored.
"""
import datetime
import json
import os
import subprocess
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402


def live_date():
    try:
        url = f"{config.SITE_URL}feed.json?cb={int(datetime.datetime.now().timestamp() * 1000)}"
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "daily-briefing"}), timeout=20) as r:
            return json.loads(r.read().decode()).get("date")
    except Exception as exc:
        return f"unreachable ({type(exc).__name__})"


def telegram(text):
    import tg
    print(f"telegram sent={tg.send_text(text)}")


def finish_nba(today):
    """Late West Coast games end around 07:00 in Warsaw, after the edition was written. Rewrite
    any line that was not final (or missing) with ESPN's final, then re-render and republish."""
    try:
        import nba_data
        md_path = os.path.join(config.MD_DIR, f"{today}.md")
        md = open(md_path, encoding="utf-8").read()
        md, changes = nba_data.fix_markdown(md, nba_data.collect(today))
    except Exception as exc:
        print(f"nba: check skipped ({type(exc).__name__}: {exc})")
        return
    if not changes:
        print("nba: every game was final")
        return
    print("nba: " + "; ".join(changes))
    with open(md_path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(md)
    py = sys.executable
    out_html = os.path.join(config.HOME, "cache", "render-out.html")
    for script, args, timeout in (("render_briefing.py", [md_path, out_html], 600), ("feed_validate.py", [], 60)):
        r = subprocess.run([py, os.path.join(config.CODE, script), *args], capture_output=True, text=True, timeout=timeout)
        if r.returncode != 0:
            print(f"nba: {script} failed, edition left as published: {(r.stderr or r.stdout)[-300:]}")
            return
    import publish
    sha = publish.publish(publish.edition_files(today), f"briefing: {today} (late NBA finals)")
    print(f"nba: republished {sha}")


def main():
    today = datetime.date.today().isoformat()
    live = live_date()
    problems = []
    if live != today:
        problems.append(f"⚠️ Today's briefing is not live. The site still shows {live}.")

    if problems:
        log = os.path.join(config.LOG_DIR, f"daily-{today}.log")
        telegram("\n".join(problems) + f"\n\nLog: {log}")
        return 0

    finish_nba(today)

    if os.path.exists(config.PUSH_SUB):
        subprocess.run([config.VENV_PY, os.path.join(config.CODE, "send_push.py"),
                        "Daily Briefing", "Today's edition is live. Tap to read."],
                       capture_output=True, text=True, timeout=90)
    print("canary: all good")
    return 0


if __name__ == "__main__":
    sys.exit(main())
