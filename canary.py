"""08:45 watchdog: silent when today's edition and audio are live, one Telegram warning otherwise.

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


def audio_done(date):
    try:
        with open(os.path.join(config.AUDIO_CACHE, "published.txt"), encoding="utf-8") as fh:
            return fh.read().strip() == date
    except OSError:
        return False


def telegram(text):
    import tg
    print(f"telegram sent={tg.send_text(text)}")


def main():
    today = datetime.date.today().isoformat()
    live = live_date()
    problems = []
    if live != today:
        problems.append(f"⚠️ Today's briefing is not live. The site still shows {live}.")
    elif not audio_done(today):
        problems.append("⚠️ Today's briefing is live but has no audio. The TTS step failed.")

    if problems:
        log = os.path.join(config.LOG_DIR, f"daily-{today}.log")
        telegram("\n".join(problems) + f"\n\nLog: {log}")
        return 0

    if os.path.exists(config.PUSH_SUB):
        subprocess.run([config.VENV_PY, os.path.join(config.CODE, "send_push.py"),
                        "Daily Briefing", "Today's edition is live. Tap to read."],
                       capture_output=True, text=True, timeout=90)
    print("canary: all good")
    return 0


if __name__ == "__main__":
    sys.exit(main())
