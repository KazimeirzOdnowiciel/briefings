"""Standings and stat leaders for the sport sections, straight from the data APIs (never a writer).

Each sport section in feed.json gets a "tables" list; the app shows them as tabs beside the
results. A table is {"tab", "title", "columns", "rows", "align"}: rows are lists of strings,
"align" marks each column "l" or "r". Every fetch is independent and failure-tolerant — a
broken endpoint costs that one table, never the edition.

    python stats.py            print every table as text
"""
import json
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

UA = {"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"}
ESPN = "https://site.api.espn.com/apis"
JOLPICA = "https://api.jolpi.ca/ergast/f1/current"
LEAGUES = [("eng.1", "Premier League"), ("esp.1", "La Liga"), ("ita.1", "Serie A"),
           ("ger.1", "Bundesliga"), ("fra.1", "Ligue 1")]
NBA_LEADERS = [("pointsPerGame", "Points", "PPG"), ("reboundsPerGame", "Rebounds", "RPG"),
               ("assistsPerGame", "Assists", "APG"), ("stealsPerGame", "Steals", "SPG"),
               ("blocksPerGame", "Blocks", "BPG"), ("3PointsMadePerGame", "Threes", "3PM")]


def _get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
        return json.load(r)


def _table(tab, title, columns, rows, align, cuts=None):
    """cuts: row counts after which a line is drawn (playoff places, the drop zone)."""
    t = {"tab": tab, "title": title, "columns": columns, "rows": rows, "align": align}
    if cuts:
        t["cuts"] = sorted(c for c in cuts if 0 < c < len(rows))
    return t


def _stats(entry):
    return {s.get("name") or s.get("type"): s.get("displayValue", "") for s in entry.get("stats", [])}


def nba():
    out = []
    data = _get(f"{ESPN}/v2/sports/basketball/nba/standings")
    for conf in data.get("children", []):
        entries = conf["standings"]["entries"]
        entries.sort(key=lambda e: float(_stats(e).get("playoffSeed") or 99))
        rows = []
        for e in entries:
            s = _stats(e)
            rows.append([s.get("playoffSeed", ""), e["team"]["shortDisplayName"], s.get("overall", ""),
                         s.get("gamesBehind", ""), s.get("streak", "")])
        # top six go straight to the playoffs, 7-10 to the play-in
        out.append(_table("Standings", conf["name"], ["#", "Team", "W-L", "GB", "Strk"], rows, "llrrr", [6, 10]))
    leaders = _get(f"{ESPN}/site/v3/sports/basketball/nba/leaders")["leaders"]["categories"]
    by_name = {c["name"]: c for c in leaders}
    for key, title, short in NBA_LEADERS:
        cat = by_name.get(key)
        if not cat:
            continue
        rows = [[str(i + 1), x["athlete"]["displayName"], (x.get("team") or {}).get("abbreviation", ""), x["displayValue"]]
                for i, x in enumerate(cat["leaders"][:5])]
        out.append(_table("Leaders", title, ["#", "Player", "Team", short], rows, "lllr"))
    return out


def _league(code, name):
    tables = []
    data = _get(f"{ESPN}/v2/sports/soccer/{code}/standings")
    entries = data["children"][0]["standings"]["entries"]
    entries.sort(key=lambda e: int(_stats(e).get("rank") or 99))
    rows = []
    for e in entries:
        s = _stats(e)
        rows.append([s.get("rank", ""), e["team"].get("shortDisplayName") or e["team"]["displayName"],
                     s.get("gamesPlayed", ""), s.get("pointDifferential", ""), s.get("points", "")])
    # the Champions League places, and the bottom three
    tables.append(_table("Tables", name, ["#", "Team", "P", "GD", "Pts"], rows, "llrrr", [4, len(rows) - 3]))
    stats = {s["name"]: s for s in _get(f"{ESPN}/site/v2/sports/soccer/{code}/statistics").get("stats", [])}
    for key, title, short in (("goalsLeaders", "Top scorers", "G"), ("assistsLeaders", "Assists", "A")):
        cat = stats.get(key)
        if not cat:
            continue
        rows = [[str(i + 1), x["athlete"]["displayName"], (x["athlete"].get("team") or {}).get("name", ""), f"{x['value']:g}"]
                for i, x in enumerate(cat["leaders"][:5])]
        tables.append(_table("Top players", f"{name} · {title}", ["#", "Player", "Club", short], rows, "lllr"))
    return tables


def football():
    with ThreadPoolExecutor(len(LEAGUES)) as pool:
        results = list(pool.map(lambda lg: _safe(_league, *lg), LEAGUES))
    # tables first (one per league), then the player tables, so tabs read in league order
    flat = [t for r in results for t in r]
    return [t for t in flat if t["tab"] == "Tables"] + [t for t in flat if t["tab"] != "Tables"]


def f1():
    out = []
    lists = _get(f"{JOLPICA}/driverStandings.json")["MRData"]["StandingsTable"]["StandingsLists"]
    if lists:
        rnd = lists[0]["round"]
        rows = [[d["position"], f"{d['Driver']['givenName']} {d['Driver']['familyName']}",
                 (d["Constructors"][-1]["name"] if d["Constructors"] else ""), d["points"]]
                for d in lists[0]["DriverStandings"]]
        out.append(_table("Standings", f"Drivers after round {rnd}", ["#", "Driver", "Team", "Pts"], rows, "lllr"))
    lists = _get(f"{JOLPICA}/constructorStandings.json")["MRData"]["StandingsTable"]["StandingsLists"]
    if lists:
        rows = [[c["position"], c["Constructor"]["name"], c["wins"], c["points"]] for c in lists[0]["ConstructorStandings"]]
        out.append(_table("Standings", "Constructors", ["#", "Team", "Wins", "Pts"], rows, "llrr"))
    return out


def _safe(fn, *args):
    try:
        return fn(*args)
    except Exception as exc:
        print(f"stats: {fn.__name__}{args} failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        return []


def collect():
    """{'nba': [...], 'football': [...], 'f1': [...]} — fetched in parallel."""
    with ThreadPoolExecutor(3) as pool:
        jobs = {k: pool.submit(_safe, fn) for k, fn in (("nba", nba), ("football", football), ("f1", f1))}
    return {k: j.result() for k, j in jobs.items()}


def attach(feed, tables=None):
    """Hang each sport's tables on its section of the feed. Returns how many tables were attached."""
    tables = tables if tables is not None else collect()
    n = 0
    for sec in feed.get("sections", []):
        name = sec.get("name", "").lower()
        key = "nba" if "nba" in name else "football" if "football" in name else "f1" if "f1" in name.split() or "formula" in name else None
        if key and tables.get(key):
            sec["tables"] = tables[key]
            n += len(tables[key])
    return n


if __name__ == "__main__":
    for sport, ts in collect().items():
        for t in ts:
            print(f"\n== {sport} / {t['tab']} / {t['title']}")
            for r in [t["columns"]] + t["rows"][:6]:
                print("  " + " | ".join(r))
