"""Speak the day's briefing: edge-tts -> Telegram audio message + audio/today.mp3 on the site.

    python audio.py [YYYY-MM-DD]

Run by run_daily.py right after the page is published, and again by a fallback
scheduled task. Idempotent per day: separate flags for "published to the site"
and "sent to Telegram", so a retry only redoes the half that failed.

Hermes wrote today.mp3 locally and never published it, so the site always played
the previous day's audio. This publishes it the moment it exists.
"""
import datetime
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
import publish  # noqa: E402
import tg  # noqa: E402

VOICE = "en-GB-SoniaNeural"

SECTION_RE = re.compile(r"^\*\*(.+?)\*\*$")
BULLET_RE = re.compile(r"^[-•*]\s+(.*)$")
URL_RE = re.compile(r"\s*https?://\S+\s*$")
EMOJI_RE = re.compile("[\U0001F1E6-\U0001F1FF\U0001F300-\U0001FAFF\u2600-\u27BF\uFE0F]+")


def clean(text):
    text = URL_RE.sub("", text)
    text = EMOJI_RE.sub("", text)
    text = re.split(r"\s*[·•|]\s*matters:", text)[0]
    return re.sub(r"\s+", " ", text).strip()


def sentence(text):
    text = clean(text).rstrip(".")
    return text + "." if text else ""


def split_headline(body):
    text = clean(body)
    return re.split(r"\s+[—–]\s+", text, maxsplit=1)[0].strip().rstrip(".")


def build_script(markdown):
    greeting, workout = "", ""
    sections, cur = [], None
    for raw in markdown.splitlines():
        line = raw.strip()
        if not line:
            continue
        if not greeting and not line.startswith(("**", "-", "•")) and line[0] not in "💪😴":
            greeting = line
            continue
        if line[0] in "💪😴" and not workout:
            workout = line
            continue
        m = SECTION_RE.match(line)
        if m:
            cur = (clean(m.group(1)), [])
            sections.append(cur)
            continue
        if cur is not None:
            mb = BULLET_RE.match(line)
            if mb:
                cur[1].append(mb.group(1))

    out = ["Good morning."]
    if greeting:
        g = clean(greeting)
        g = re.sub(r"^Good morning\s*[—–-]\s*", "", g, flags=re.I)
        out.append(sentence(g))
    if workout:
        out.append(sentence(clean(workout).replace(" ~ ", " about ")))
    for name, bullets in sections:
        if not bullets:
            continue
        if any(k in name.lower() for k in ("tl;dr", "tl:dr", "tldr")):
            out.append("In short.")
            for b in bullets[:3]:
                out.append(sentence(split_headline(b)))
            continue
        out.append(sentence(name))
        for b in bullets[:2]:
            out.append(sentence(split_headline(b)))
    out.append("That's the briefing. Full stories and links are in the app.")
    return " ".join(p for p in out if p).strip()


def flag(name, date, write=False):
    path = os.path.join(config.AUDIO_CACHE, f"{name}.txt")
    if write:
        os.makedirs(config.AUDIO_CACHE, exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(date)
        return True
    try:
        with open(path, encoding="utf-8") as fh:
            return fh.read().strip() == date
    except OSError:
        return False


def main():
    date = sys.argv[1] if len(sys.argv) > 1 else datetime.date.today().isoformat()
    md = os.path.join(config.MD_DIR, f"{date}.md")
    if not os.path.exists(md):
        print("no briefing for", date, "- nothing to say")
        return 0
    if flag("published", date) and flag("sent", date):
        print("audio already done for", date)
        return 0

    text = build_script(open(md, encoding="utf-8").read())
    words = len(text.split())
    if words < 40:
        print("briefing too thin to voice")
        return 0

    os.makedirs(config.AUDIO_CACHE, exist_ok=True)
    txt = os.path.join(config.AUDIO_CACHE, "script.txt")
    mp3 = os.path.join(config.AUDIO_CACHE, f"{date}.mp3")
    if not (os.path.exists(mp3) and os.path.getsize(mp3) > 5000):
        with open(txt, "w", encoding="utf-8", newline=chr(10)) as fh:
            fh.write(text)
        tts = subprocess.run([config.VENV_PY, "-m", "edge_tts", "-v", VOICE, "--rate=+8%",
                              "-f", txt, "--write-media", mp3],
                             capture_output=True, text=True, timeout=300)
        if tts.returncode != 0 or not os.path.exists(mp3) or os.path.getsize(mp3) < 5000:
            print("tts failed:", (tts.stderr or tts.stdout).strip()[:300])
            return 1

    ok = True
    if not flag("published", date):
        web = os.path.join(config.SITE_DIR, "audio", "today.mp3")
        os.makedirs(os.path.dirname(web), exist_ok=True)
        # 32 kbps mono keeps the repo from growing by a megabyte a day
        enc = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-ac", "1", "-b:a", "32k", web],
                             capture_output=True, text=True, timeout=120)
        if enc.returncode != 0:
            import shutil
            shutil.copyfile(mp3, web)
        try:
            publish.publish(["audio/today.mp3"], f"audio: {date}")
            flag("published", date, write=True)
        except Exception as exc:
            ok = False
            print("audio publish failed:", exc)

    if not flag("sent", date):
        if tg.send_file(mp3, f"🎧 Today's briefing — {words} words, {max(1, round(words / 165))} min"):
            flag("sent", date, write=True)
        else:
            ok = False
    print(f"audio done ok={ok} words={words}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
