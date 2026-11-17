"""Every finished football match of the last day, with ALL goal scorers, from ESPN's JSON.

The football writer gets this as authoritative data, so scorers come from code, not from
whatever round-up a search happens to find. run_daily.py then checks that each score line
names as many goals as the score has.

    python football_data.py            print the block the writer receives

Ekstraklasa is not in ESPN (HTTP 400 for soccer/pol.1): the writer still sources it from
ekstraklasa.org / 90minut.pl, and the same scorer check applies.
"""
import datetime
import json
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = "https://site.api.espn.com/apis/site/v2/sports/soccer/{league}/{what}"
UA = {"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"}
LEAGUES = {
    "eng.1": "Premier League", "esp.1": "La Liga", "ita.1": "Serie A", "ger.1": "Bundesliga", "fra.1": "Ligue 1",
    "uefa.champions": "Champions League", "uefa.europa": "Europa League", "uefa.europa.conf": "Conference League",
    "uefa.nations": "Nations League", "fifa.worldq.uefa": "World Cup qualifying (UEFA)",
    "uefa.euroq": "Euro qualifying", "fifa.friendly": "Friendlies",
}


def _get(league, what, query=""):
    url = API.format(league=league, what=what) + query
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
        return json.load(r)


def _goal_label(event):
    """'Kaprálik 90'+5'' with (pen) / (og) where it applies."""
    kind = (event.get("type") or {}).get("text", "").lower()
    who = (event.get("participants") or [{}])[0].get("athlete", {}).get("displayName", "?")
    minute = (event.get("clock") or {}).get("displayValue", "").strip()
    tag = " (og)" if "own" in kind else " (pen)" if "penalty" in kind else ""
    return f"{who} {minute}{tag}".strip()


def _match(league, event):
    comp = event["competitions"][0]
    if not comp["status"]["type"].get("completed"):
        return None
    sides = {c["homeAway"]: c for c in comp["competitors"]}
    home, away = sides["home"], sides["away"]
    hn, an = home["team"]["displayName"], away["team"]["displayName"]
    try:
        summary = _get(league, "summary", f"?event={event['id']}")
        goals = [k for k in summary.get("keyEvents", []) if k.get("scoringPlay")]
    except Exception:
        goals = None
    scorers = {hn: [], an: []}
    for g in goals or []:
        # an own goal is credited to the team that benefits, which is how ESPN lists it
        team = (g.get("team") or {}).get("displayName")
        scorers.setdefault(team, []).append(_goal_label(g))
    return {
        "competition": LEAGUES[league],
        "kickoff": event.get("date"),
        "home": hn, "away": an,
        "home_score": int(home.get("score") or 0), "away_score": int(away.get("score") or 0),
        "home_scorers": scorers.get(hn, []), "away_scorers": scorers.get(an, []),
        "scorers_complete": goals is not None
        and len(scorers.get(hn, [])) == int(home.get("score") or 0)
        and len(scorers.get(an, [])) == int(away.get("score") or 0),
    }


def collect(now=None, hours=30):
    """Finished matches whose kickoff falls in the last `hours`."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    days = sorted({(now - datetime.timedelta(days=d)).strftime("%Y%m%d") for d in (0, 1)})
    jobs = []
    for league in LEAGUES:
        for day in days:
            try:
                for ev in _get(league, "scoreboard", f"?dates={day}").get("events", []):
                    jobs.append((league, ev))
            except Exception:
                continue
    since = now - datetime.timedelta(hours=hours)
    seen, out = set(), []
    with ThreadPoolExecutor(8) as pool:
        for m in pool.map(lambda j: _match(*j), jobs):
            if not m or (m["home"], m["away"]) in seen:
                continue
            try:
                kick = datetime.datetime.fromisoformat(m["kickoff"].replace("Z", "+00:00"))
                if kick < since or kick > now:
                    continue
            except Exception:
                pass
            seen.add((m["home"], m["away"]))
            out.append(m)
    return out


def block(matches):
    """The text the football writer receives."""
    if not matches:
        return "FOOTBALL DATA: ESPN lists no finished matches in the last 30 hours (Ekstraklasa is not covered: check it separately)."
    lines = ["FOOTBALL DATA (ESPN, authoritative: every finished match of the last 30 h, with every goal):"]
    for m in matches:
        hs = ", ".join(m["home_scorers"]) or "-"
        as_ = ", ".join(m["away_scorers"]) or "-"
        flag = "" if m["scorers_complete"] else "  [ESPN scorer list incomplete: complete it from the match report]"
        lines.append(f"{m['competition']} | {m['home']} {m['home_score']} - {m['away_score']} {m['away']} | "
                     f"{m['home']}: {hs} | {m['away']}: {as_}{flag}")
    return "\n".join(lines)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    print(block(collect()))
