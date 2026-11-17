"""Today's Hevy workout, formatted for a Telegram message.

Shared by workout_line.py (morning one-liner for the news briefing) and run
directly by the 17:00 cron job (no_agent), which delivers stdout verbatim.

Reads the routine live from the Hevy API so a renamed or re-weighted routine in
the app shows up here without touching this file. Any failure falls back to a
static weekday->routine map, then to a generic nudge. Never raises.
"""
import datetime
import json
import os
import sys
import urllib.request

REST_DAYS = (0, 3, 6)  # Monday, Thursday, Sunday

FALLBACK = {
    1: "Tuesday - Row A: Steady State",
    2: "Wednesday - Row B: Intervals",
    4: "Friday - Row C: Rate Ladder",
    5: "Saturday - Row D: Long Pyramid",
}

FOCUS = {
    "Steady": "40 min continuous at 2:20-2:30/500m",
    "Intervals": "8 x 3 min hard, 2 min easy between",
    "Rate Ladder": "5 x 5 min at rates 20-26",
    "Pyramid": "2-4-6-8-6-4-2 min pieces",
}

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
BRIEFING_INDEX = os.path.join(config.SITE_DIR, "index.html")


def _key():
    with open(os.path.expanduser("~/.hevy_key"), encoding="utf-8") as fh:
        key = fh.read().strip()
    if not key:
        raise RuntimeError("empty hevy key")
    return key


def _call(path, key):
    req = urllib.request.Request("https://api.hevyapp.com/v1" + path, headers={"api-key": key})
    with urllib.request.urlopen(req, timeout=25) as r:
        return json.load(r)


def activity(now=None):
    """One short sentence about what was actually trained. Never raises."""
    now = now or datetime.datetime.now()
    try:
        workouts = _call("/workouts?pageSize=10", _key()).get("workouts") or []
    except Exception:
        return ""
    if not workouts:
        return "Nothing logged in Hevy yet."
    last = workouts[0]
    try:
        start = datetime.datetime.fromisoformat(last["start_time"].replace("Z", "+00:00"))
    except Exception:
        return ""
    sets = sum(len(e.get("sets") or []) for e in last.get("exercises") or [])
    week = 0
    for w in workouts:
        try:
            s = datetime.datetime.fromisoformat(w["start_time"].replace("Z", "+00:00"))
        except Exception:
            continue
        if (now - s.replace(tzinfo=None)).days < 7:
            week += 1
    label = (last.get("title") or "session").split("\u2014")[0].strip()
    return f"Last logged {start:%a %d %b}, {sets} sets ({label}) \u00b7 {week} sessions in 7 days."


def briefing_stale(now=None):
    """True when today's briefing page was never written."""
    now = now or datetime.datetime.now()
    try:
        return datetime.date.fromtimestamp(os.path.getmtime(BRIEFING_INDEX)) != now.date()
    except OSError:
        return True


def full_message(now=None):
    now = now or datetime.datetime.now()
    got = today_routine(now)
    prefix = ""
    if briefing_stale(now):
        prefix = "\u26a0\ufe0f No briefing page published today \u2014 the 08:00 run may have failed.\n\n"

    if not got:
        body = ("\U0001f634 Rest day \u2014 no session today. Next one: "
                f"{next_workout_day(now)}.")
        act = activity(now)
        return prefix + body + (f"\n{act}" if act else "")

    title, rows = got
    lines = [f"\U0001f4aa Workout day \u2014 {title}", ""]
    if rows:
        for i, (name, shape) in enumerate(rows, 1):
            lines.append(f"{i}. {name} \u2014 {shape}" if shape else f"{i}. {name}")
        lines.append("")
        act = activity(now)
        if act:
            lines.append(act)
        lines.append("~60 min. Log it in Hevy as you go.")
    else:
        lines.append("Open Hevy for today's routine.")
    return prefix + "\n".join(lines)


def one_line(now=None):
    now = now or datetime.datetime.now()
    got = today_routine(now)
    act = activity(now)
    tail = f" {act}" if act else ""
    if not got:
        return f"\U0001f634 Rest day \u2014 next session {next_workout_day(now)}.{tail}"
    title, _ = got
    key = next((k for k in FOCUS if k in title), None)
    focus = f" \u2014 {FOCUS[key]}" if key else ""
    return f"\U0001f4aa Today is a workout day: {title}{focus}. ~60 min, reminder at 17:00.{tail}"


def _fmt_kg(w):
    if w is None:
        return None
    return f"{int(w) if float(w).is_integer() else round(w, 1)} kg"


def _fmt_dur(sec):
    sec = int(sec)
    return f"{sec // 60} min" if sec >= 60 and sec % 60 == 0 else f"{sec} s"


def _describe(ex):
    sets = ex.get("sets") or []
    if not sets:
        return ""
    first = sets[0]
    n = len(sets)
    durs = [s.get("duration_seconds") for s in sets]
    if all(durs):
        if len(set(durs)) == 1:
            shape = _fmt_dur(durs[0]) if n == 1 else f"{n} \u00d7 {_fmt_dur(durs[0])}"
        else:
            total = sum(durs) / 60
            shape = f"{round(total)} min total"
        if first.get("type") == "warmup":
            shape += " (incl. warm-up)"
        return shape
    if first.get("rep_range"):
        rr = first["rep_range"]
        shape = f"{n} \u00d7 {rr['start']}-{rr['end']}"
    elif first.get("duration_seconds"):
        shape = _fmt_dur(first["duration_seconds"]) if n == 1 else f"{n} \u00d7 {_fmt_dur(first['duration_seconds'])}"
    elif first.get("distance_meters"):
        shape = f"{n} \u00d7 {first['distance_meters']} m"
    elif first.get("reps"):
        shape = f"{n} \u00d7 {first['reps']}"
    else:
        shape = f"{n} sets"
    weight = _fmt_kg(first.get("weight_kg"))
    if weight:
        shape += f" \u00b7 {weight}"
    if first.get("type") == "warmup":
        shape += " (warm-up)"
    return shape


def _fetch():
    key_path = os.path.expanduser("~/.hevy_key")
    with open(key_path, encoding="utf-8") as fh:
        key = fh.read().strip()
    if not key:
        raise RuntimeError("empty hevy key")

    def call(path):
        req = urllib.request.Request("https://api.hevyapp.com/v1" + path, headers={"api-key": key})
        with urllib.request.urlopen(req, timeout=25) as r:
            return json.load(r)

    routines = call("/routines?pageSize=10").get("routines", [])
    for r in routines:
        full = call(f"/routines/{r['id']}")["routine"]
        yield full


def today_routine(now=None):
    """Return (title, [(exercise, shape), ...]) for today, or None on a rest day."""
    now = now or datetime.datetime.now()
    if now.weekday() in REST_DAYS:
        return None
    try:
        for full in _fetch():
            if full["title"].split(" ")[0].lower() == now.strftime("%A").lower():
                return full["title"], [(_short(e["title"]), _describe(e)) for e in full["exercises"]]
    except Exception:
        pass
    return FALLBACK.get(now.weekday()), []


def _short(title):
    for a, b in (("Rowing Machine", "Rower"), (" - Close Grip (Dumbbell)", "")):
        title = title.replace(a, b)
    return title


def next_workout_day(now=None):
    now = now or datetime.datetime.now()
    for i in range(1, 8):
        d = now + datetime.timedelta(days=i)
        if d.weekday() not in REST_DAYS:
            return d.strftime("%A")
    return "Tuesday"


if __name__ == "__main__":
    print(full_message())
