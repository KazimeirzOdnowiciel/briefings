"""Telegram delivery through the user's own sender script, with the bot token from the briefing home."""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402

TOKEN_FILE = os.path.join(config.HOME, ".telegram_token")


def _env():
    env = dict(os.environ)
    try:
        with open(TOKEN_FILE, encoding="utf-8") as fh:
            env["TELEGRAM_BOT_TOKEN"] = fh.read().strip()
    except OSError:
        pass
    return env


def send_text(text):
    res = subprocess.run([sys.executable, config.TELEGRAM_SENDER, "-"], input=text, text=True, encoding="utf-8",
                         capture_output=True, timeout=90, env=_env())
    if res.returncode != 0:
        print("telegram text failed:", (res.stderr or res.stdout).strip()[:300])
    return res.returncode == 0


def send_file(path, caption):
    res = subprocess.run([sys.executable, config.TELEGRAM_SENDER, "--file", path, "--caption", caption],
                         text=True, encoding="utf-8", capture_output=True, timeout=180, env=_env())
    if res.returncode != 0:
        print("telegram file failed:", (res.stderr or res.stdout).strip()[:300])
    return res.returncode == 0


if __name__ == "__main__":
    print("sent" if send_text(" ".join(sys.argv[1:]) or "test from the briefing pipeline") else "failed")
