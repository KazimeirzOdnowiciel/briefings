"""Pre-run context for the daily briefing: workout, market tape, dev releases.

Cron runs this before the briefing prompt and injects stdout, so three things
the model must never invent come from code instead:

    💪 Today is a workout day: ...      (or 😴 rest day)   -> line 2 of the digest
    TAPE: WIG20 ... · USD/PLN ...                            -> first Money bullet
    DEV: Dalamud ... · XIVLauncher ...                        -> first Games & Dev bullet

Every fetch is independent, timeout-bounded and non-fatal: a dead endpoint drops
its own instrument and nothing else. The script always exits 0.
"""
import datetime
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402

HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) daily-briefing"}
GH = {"Accept": "application/vnd.github+json", "User-Agent": "daily-briefing"}
TIMEOUT = 12


def _get(url, headers=None):
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers or HDR), timeout=TIMEOUT) as r:
        return r.read().decode()


def _yahoo(symbol):
    """(last, pct_change) or (last, None) or (None, None)."""
    url = f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=3mo"
    try:
        res = json.loads(_get(url))["chart"]["result"]
        closes = [c for c in (res[0]["indicators"]["quote"][0].get("close") or []) if c]
    except Exception:
        return None, None
    if not closes:
        return None, None
    last = closes[-1]
    if len(closes) < 2:
        return last, None
    return last, (last / closes[-2] - 1) * 100


def _num(value, decimals=2):
    if value is None:
        return None
    return f"{value:,.{decimals}f}"


def _moved(pct, decimals=1):
    if pct is None:
        return ""
    return f" ({pct:+.{decimals}f}%)".replace("+", "+")


def tape():
    parts = []
    values = {}
    wig, wig_pct = _yahoo("WIG20.WA")
    if wig is not None:
        parts.append(f"WIG20 {_num(wig, 1)}{_moved(wig_pct)}")
        values["WIG20"] = round(wig, 2)

    for symbol, label, dec in (("USDPLN=X", "USD/PLN", 3), ("EURPLN=X", "EUR/PLN", 3)):
        last, pct = _yahoo(symbol)
        if last is not None:
            parts.append(f"{label} {_num(last, dec)}{_moved(pct, 2)}")
            values[label] = round(last, 4)

    try:
        btc = json.loads(_get("https://api.coingecko.com/api/v3/simple/price"
                              "?ids=bitcoin&vs_currencies=usd&include_24hr_change=true"))
        usd = btc["bitcoin"]["usd"]
        chg = btc["bitcoin"].get("usd_24h_change")
        parts.append(f"BTC ${_num(usd, 0)}{_moved(chg)}")
        values["BTC"] = round(usd, 1)
    except Exception:
        pass

    brent, brent_pct = _yahoo("BZ=F")
    if brent is not None:
        parts.append(f"Brent ${_num(brent)}{_moved(brent_pct)}")
        values["Brent"] = round(brent, 2)

    remember(values)
    if not parts:
        return "TAPE: unavailable this morning - do not state market levels today."
    return "TAPE: " + " \u00b7 ".join(parts)


def remember(values):
    """Keep a daily history so the page can draw a sparkline. Never raises."""
    if not values:
        return
    path = os.path.join(config.SITE_DIR, "_og", "tape_history.json")
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        try:
            with open(path, encoding="utf-8") as fh:
                hist = json.load(fh)
        except Exception:
            hist = {}
        hist[datetime.date.today().isoformat()] = values
        for old in sorted(hist)[:-90]:
            hist.pop(old, None)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(hist, fh, indent=1, sort_keys=True)
    except Exception:
        pass


def dev_watch(days=14):
    """Dalamud's own distribution API — the authoritative API level and game build.

    GitHub releases/tags on goatcorp/Dalamud are stale (latest tag 14.0.4.0 from
    March while the live build is 15.x), so version news comes from kamori, which
    is what XIVLauncher itself checks.
    """
    try:
        info = json.loads(_get("https://kamori.goats.dev/Dalamud/Release/VersionInfo?track=release",
                               {"User-Agent": "XIVLauncher"}))
    except Exception:
        return "DEV: Dalamud version lookup failed - do not state an API level today."
    ver = info.get("assemblyVersion") or "?"
    runtime = info.get("runtimeVersion") or "?"
    game = (info.get("supportedGameVer") or "")[:10]
    log = (info.get("changelog") or {}).get("date") or ""
    bits = [f"Dalamud {ver}", f".NET {runtime}"]
    if game:
        bits.append(f"game build {game}")
    if log:
        try:
            when = datetime.datetime.fromisoformat(log)
            bits.append(f"last changelog {when:%d %b}")
        except ValueError:
            pass
    return "DEV: " + " \u00b7 ".join(bits)


def feedback():
    """The reader's standing preferences, as one injected line. Never raises."""
    path = config.FEEDBACK
    try:
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
    except OSError:
        return ""
    prefs = []
    in_block = False
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("## "):
            in_block = "standing" in s.lower()
            continue
        if in_block and s.startswith("- "):
            prefs.append(s[2:].strip())
    if not prefs:
        return ""
    return "PREF: " + " | ".join(prefs[:12])


def main():
    try:
        from workout_msg import one_line
        print(one_line())
    except Exception as exc:
        print(f"WORKOUT_LOOKUP_FAILED: {exc}")
    print(tape())
    print(dev_watch())
    pref = feedback()
    if pref:
        print(pref)
    return 0


if __name__ == "__main__":
    sys.exit(main())
