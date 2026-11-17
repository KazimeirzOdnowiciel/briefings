"""Paths and constants for the briefing pipeline (the Hermes replacement).

Code lives in this folder; state lives in BRIEFING_HOME (default ~/.briefing):

    site/            working copy of the published files + _og caches (never committed wholesale)
    editions_md/     the markdown each morning's run wrote, one file per day (continuity source)
    logs/            one log per run
    cache/audio/     TTS output and the once-a-day flag
    venv/            python with edge-tts and pywebpush
    .github_token    token used by the publisher (falls back to `gh auth token`)
    push_*           VAPID keys and the phone's push subscription
"""
import os
import sys

# Child scripts print emoji and "·"; without this a Windows child encodes stdout in cp1252
# and the workout line dies with UnicodeEncodeError. Every script imports config, so
# every process it spawns inherits UTF-8.
os.environ.setdefault("PYTHONUTF8", "1")
os.environ.setdefault("PYTHONIOENCODING", "utf-8")
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

HOME =os.environ.get("BRIEFING_HOME") or os.path.join(os.path.expanduser("~"), ".briefing")
CODE = os.path.dirname(os.path.abspath(__file__))

SITE_DIR = os.path.join(HOME, "site")
MD_DIR = os.path.join(HOME, "editions_md")
LOG_DIR = os.path.join(HOME, "logs")
AUDIO_CACHE = os.path.join(HOME, "cache", "audio")
FEEDBACK = os.path.join(HOME, "briefing_feedback.md")
TOKEN_FILE = os.path.join(HOME, ".github_token")
VENV_PY = os.path.join(HOME, "venv", "Scripts", "python.exe")
PUSH_SUB = os.path.join(HOME, "push_subscription.json")
PUSH_KEY = os.path.join(HOME, "push_vapid_private.pem")

OWNER, REPO, BRANCH = "KazimeirzOdnowiciel", "briefings", "main"
SITE_URL = f"https://{OWNER.lower()}.github.io/{REPO}/"
APP_URL = SITE_URL + "app/"

# Telegram delivery goes through the user's own sender script (bot token lives with it).
TELEGRAM_SENDER = r"D:\Deepseek\tools\telegram-send.py"

# The writer model for the morning run. Any `claude --model` value works.
MODEL = os.environ.get("BRIEFING_MODEL", "sonnet")
CLAUDE = os.environ.get("BRIEFING_CLAUDE") or os.path.join(os.path.expanduser("~"), ".local", "bin", "claude.exe")
