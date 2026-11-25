"""The sports half of "🔜 Coming up", from the data APIs rather than a search.

The agenda writer is good at central banks, releases and patches, but it tends to skip the
sport that is just as easy to know in advance. These entries come from code:

  * F1: qualifying, sprint and race of any Grand Prix in the next seven days (Jolpica)
  * UEFA club nights and Poland internationals (ESPN), with Polish clubs called out

The writer receives them as <calendar_data>; afterwards fix_markdown() adds any it dropped
and keeps the section in date order.

    python agenda_data.py [YYYY-MM-DD]      print the entries for that edition
"""
import datetime
import json
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

UA = {"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"}
ESPN = "https://site.api.espn.com/apis/site/v2/sports/soccer/{league}/scoreboard?dates={day}"
JOLPICA = "https://api.jolpi.ca/ergast/f1/current"
UEFA = {"uefa.champions": "Champions League", "uefa.europa": "Europa League",
        "uefa.europa.conf": "Conference League"}
NATIONAL = ["fifa.worldq.uefa", "uefa.nations", "uefa.euroq", "fifa.friendly"]
POLISH = re.compile(r"\b(Legia|Lech|Jagiellonia|Rak[oó]w|Pogo[nń]|G[oó]rnik|Widzew|Cracovia|Wis[lł]a|"
                    r"Zag[lł][eę]bie|[SŚ]l[aą]sk|Motor Lublin|Korona|Piast|Radomiak|Poland)\b", re.I)
BIG = re.compile(r"\b(Real Madrid|Barcelona|Atl[eé]tico|Bayern|Dortmund|Liverpool|Arsenal|Manchester|Chelsea|"
                 r"Tottenham|Newcastle|Aston Villa|Paris Saint-Germain|PSG|Inter|Milan|Juventus|Napoli|Roma|"
                 r"Lazio|Atalanta|Benfica|Porto|Sporting|Ajax|PSV|Celtic|Leverkusen)\b", re.I)


def _get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
        return json.load(r)


def warsaw(utc):
    """UTC datetime -> Warsaw wall clock (CET, or CEST between the last Sundays of March and October)."""
    def last_sunday(month):
        d = datetime.datetime(utc.year, month, 31 if month in (3, 10) else 30, 1, tzinfo=datetime.timezone.utc)
        return d - datetime.timedelta(days=(d.weekday() + 1) % 7)
    summer = last_sunday(3) <= utc < last_sunday(10)
    return (utc + datetime.timedelta(hours=2 if summer else 1)).replace(tzinfo=None)


def _utc(date, time="00:00:00Z"):
    return datetime.datetime.fromisoformat(f"{date}T{time.replace('Z', '')}").replace(tzinfo=datetime.timezone.utc)


def _window(edition):
    start = datetime.date.fromisoformat(str(edition)) if edition else datetime.date.today()
    return start + datetime.timedelta(days=1), start + datetime.timedelta(days=7)


def f1(edition=None):
    first, last = _window(edition)
    season = _get(JOLPICA + ".json")["MRData"]["RaceTable"]["Races"]
    try:
        top = _get(JOLPICA + "/driverStandings.json")["MRData"]["StandingsTable"]["StandingsLists"][0]["DriverStandings"]
        leader = f"{top[0]['Driver']['familyName']} leads on {top[0]['points']} pts"
    except Exception:
        leader = ""
    out = []
    for race in season:
        gp = race["raceName"].replace("Grand Prix", "GP")
        rnd = f"round {race['round']} of {len(season)}"
        sessions = [("Sprint", "sprint", f"extra points before Sunday, {rnd}"),
                    ("Qualifying", "qualifying", f"sets the grid for {rnd}")]
        for field, label, note in sessions:
            s = race.get(field)
            if s:
                out.append((s["date"], s.get("time", "00:00:00Z"), f"{gp} {label}", note))
        out.append((race["date"], race.get("time", "00:00:00Z"), f"{gp} race",
                    rnd + (f"; {leader}" if leader else "")))
    entries = []
    for date, time, text, note in out:
        local = warsaw(_utc(date, time))
        if first <= local.date() <= last:
            entries.append({"date": local.date().isoformat(), "time": local.strftime("%H:%M"), "emoji": "🏎️",
                            "text": text, "note": note, "key": text.split()[-1],
                            "url": f"https://www.formula1.com/en/racing/{local.year}"})
    return entries


def _fixtures(league, day):
    try:
        return _get(ESPN.format(league=league, day=day.strftime("%Y%m%d"))).get("events", [])
    except Exception:
        return []


def _and(words):
    return words[0] if len(words) == 1 else ", ".join(words[:-1]) + " and " + words[-1]


def _tie(event):
    sides = {c["homeAway"]: c["team"]["displayName"] for c in event["competitions"][0]["competitors"]}
    return f"{sides.get('home', '?')} v {sides.get('away', '?')}"


def football(edition=None):
    first, last = _window(edition)
    days = [first + datetime.timedelta(days=i) for i in range((last - first).days + 1)]
    jobs = [(lg, d) for lg in list(UEFA) + NATIONAL for d in days]
    with ThreadPoolExecutor(12) as pool:
        results = list(pool.map(lambda j: _fixtures(*j), jobs))
    by_night = {}
    for (league, _day), events in zip(jobs, results):
        for ev in events:
            kick = warsaw(datetime.datetime.fromisoformat(ev["date"].replace("Z", "+00:00")))
            if not first <= kick.date() <= last:
                continue
            tie = _tie(ev)
            if league in NATIONAL and not re.search(r"\bPoland\b", tie):
                continue
            by_night.setdefault((league, kick.date().isoformat()), []).append((kick, tie))
    entries = []
    for (league, date), games in sorted(by_night.items(), key=lambda kv: kv[0][1]):
        games = sorted(set(games))
        times = sorted({k.strftime("%H:%M") for k, _ in games})
        ties = [t for _, t in games]
        if league in NATIONAL:
            for kick, tie in games:
                entries.append({"date": date, "time": kick.strftime("%H:%M"), "emoji": "⚽", "text": tie,
                                "note": "Poland international", "key": "Poland",
                                "url": f"https://www.espn.com/soccer/fixtures/_/date/{kick:%Y%m%d}/league/{league}"})
            continue
        name = UEFA[league]
        polish = [t for t in ties if POLISH.search(t)]
        headline = (polish + [t for t in ties if BIG.search(t) and t not in polish])[:2] or ties[:2]
        more = len(ties) - len(headline)
        text = f"{name}: " + " and ".join(headline) + (f", plus {more} more" if more else "")
        clubs = list(dict.fromkeys(POLISH.search(t).group(0) for t in polish))
        note = (f"{_and(clubs)} in action; " if clubs else "") + f"{len(ties)} matches, kick-offs {_and(times)}"
        entries.append({"date": date, "time": times[0], "emoji": "⚽", "text": text, "note": note,
                        "key": name, "url": f"https://www.espn.com/soccer/fixtures/_/date/{date.replace('-', '')}/league/{league}"})
    return entries


def collect(edition=None):
    out = []
    for part in (f1, football):
        try:
            out += part(edition)
        except Exception as exc:
            print(f"agenda_data: {part.__name__} skipped ({type(exc).__name__}: {exc})", file=sys.stderr)
    return sorted(out, key=lambda e: (e["date"], e["time"] or "99:99"))


def line(e):
    when = e["date"] + (f" {e['time']}" if e.get("time") else "")
    return f"- {when} — {e['emoji']} {e['text']} — {e['note']}. {e['url']}"


def block(entries):
    if not entries:
        return "CALENDAR DATA: no F1 or UEFA dates in the next seven days."
    return "\n".join(["CALENDAR DATA (from the F1 and ESPN APIs, authoritative; include every line, "
                      "you may tighten the wording but keep the date, time and link):"] + [line(e) for e in entries])


HEAD_RE = re.compile(r"^\*\*(.+?)\*\*\s*$")
ENTRY_RE = re.compile(r"^- (\d{4}-\d{2}-\d{2})(?: (\d{2}:\d{2}))?\b")


def fix_markdown(md, entries):
    """Add the calendar entries the writer left out, and keep 🔜 Coming up in date order."""
    lines = md.split("\n")
    start = next((i for i, l in enumerate(lines) if HEAD_RE.match(l.strip()) and "coming up" in l.lower()), None)
    if start is None or not entries:
        return md, []
    end = next((i for i in range(start + 1, len(lines)) if HEAD_RE.match(lines[i].strip())), len(lines))
    body = [l for l in lines[start + 1:end] if l.strip()]
    added = []
    for e in entries:
        same_day = [l for l in body if l.startswith(f"- {e['date']}")]
        if not any(re.search(re.escape(e["key"]), l, re.I) for l in same_day):
            body.append(line(e))
            added.append(f"{e['date']} {e['text']}")
    dated = [l for l in body if ENTRY_RE.match(l)]
    other = [l for l in body if not ENTRY_RE.match(l)]
    dated.sort(key=lambda l: (ENTRY_RE.match(l).group(1), ENTRY_RE.match(l).group(2) or "99:99"))
    lines[start + 1:end] = dated + other + [""]
    return "\n".join(lines), added


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    print(block(collect(sys.argv[1] if len(sys.argv) > 1 else None)))
