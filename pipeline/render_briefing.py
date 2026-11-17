"""Render a briefing markdown file to a styled, self-contained HTML page.

    python render_briefing.py C:\\...\\briefing_today.md [out.html]

Also maintains, in <hermes>\\briefings\\:
  <date>.html + index.html   the edition and the latest one
  manifest.json              title/greeting per edition (drives archive + RSS)
  archive.html               every edition, newest first
  feed.xml                   RSS 2.0
  og.png                     1200x630 social card (best effort, via ffmpeg)

Input contract (what the cron prompt asks the model to emit):
  line 1              greeting with the date  -> page header
  💪/😴 line           workout chip
  **Section name**    section card
  - Headline — summary. · matters: clause. https://link
  - Team 108 - 97 Team — top performer          (rendered as a scoreboard row)
  Short line < 40ch   sub-label (league name)
  anything else       paragraph
"""
import datetime
import email.utils
import html
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402

URL_RE = re.compile(r"\s*(https?://\S+)\s*$")
SCORE_RE = re.compile(
    r"^(?P<a>[^-–—\s][^–—]*?)\s+(?P<sa>\d+)\s*[–-]\s*(?P<sb>\d+)\s+(?P<b>[^–—]+?)"
    r"(?:\s*[–—]\s*(?P<note>.+))?$"
)
MATTERS_RE = re.compile(r"\s*(?:·|•|\|)\s*matters:\s*", re.I)
SECTION_RE = re.compile(r"^\*\*(.+?)\*\*$")
BULLET_RE = re.compile(r"^[-•*]\s+(.*)$")

HOME = config.SITE_DIR
SITE = config.SITE_URL
FONT_BOLD = r"C\:/Windows/Fonts/segoeuib.ttf"
FONT_REG = r"C\:/Windows/Fonts/segoeui.ttf"

PUSH_HTML = """
<details class="notify"><summary>Notifications</summary>
<p>Install this page to your home screen first, open it from there, then tap Enable. Paste the code it
shows into your briefing chat once — after that the 08:45 canary pushes an alert when the edition is live.</p>
<button id="npush">Enable notifications</button>
<textarea id="subout" readonly placeholder="Subscription appears here, then paste it into chat"></textarea>
</details>
<script>(function(){
var KEY="__KEY__";
var b=document.getElementById('npush'),out=document.getElementById('subout');
if(!('serviceWorker' in navigator)||!('PushManager' in window)){b.disabled=true;b.textContent='Not supported here';return;}
function b64(s){var p='='.repeat((4-s.length%4)%4);var t=(s+p).replace(/-/g,'+').replace(/_/g,'/');var r=atob(t),u=new Uint8Array(r.length);for(var i=0;i<r.length;i++)u[i]=r.charCodeAt(i);return u;}
b.addEventListener('click',function(){
  navigator.serviceWorker.ready.then(function(reg){
    return reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:b64(KEY)});
  }).then(function(sub){
    out.value=JSON.stringify(sub.toJSON());out.select();
    try{document.execCommand('copy');b.textContent='Copied - paste it into chat';}catch(e){b.textContent='Copy the code above';}
  }).catch(function(e){out.value='error: '+e.message;});
});
})();</script>
"""

CSS = """
:root{--bg:#0a0e13;--panel:#111823;--line:#1e2a38;--ink:#e8eef7;--dim:#8d9bad;
--accent:#5eead4;--amber:#fbbf24;--rose:#fb7185;--mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
*{box-sizing:border-box}
body{margin:0;padding:28px 22px 48px;background:var(--bg);color:var(--ink);
font:400 15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;max-width:860px}
a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
header{border-bottom:1px solid var(--line);padding-bottom:16px;margin-bottom:22px}
.kicker{font:600 10.5px/1 var(--mono);letter-spacing:.18em;text-transform:uppercase;color:var(--accent)}
h1{margin:9px 0 7px;font-size:30px;line-height:1.15;letter-spacing:-.02em;font-weight:650}
.date{color:var(--dim);font-size:13.5px}
.chips{display:flex;gap:8px;flex-wrap:wrap;margin-top:13px}
.chip{display:inline-flex;align-items:center;gap:6px;padding:5px 11px;border-radius:999px;
border:1px solid var(--line);background:#0e141c;font-size:12px;color:var(--dim)}
.chip.live{border-color:#1f4f45;color:var(--accent)}
.chip.warn{border-color:#4a3a12;color:var(--amber)}
.chip b{color:var(--ink);font-weight:600}
section{margin:0 0 22px}
.head{display:flex;align-items:baseline;gap:9px;margin:0 0 10px}
.head h2{margin:0;font-size:13px;letter-spacing:.06em;text-transform:uppercase;font-weight:700}
.head .n{font:500 10.5px/1 var(--mono);color:var(--dim)}
.card{background:var(--panel);border:1px solid var(--line);border-radius:12px;overflow:hidden}
.story{display:block;padding:12px 15px;border-top:1px solid var(--line);color:inherit}
.story:first-child{border-top:0}
.story:hover{background:#141c28}
.story .hl{font-weight:600;font-size:14.5px;color:var(--ink);display:block;letter-spacing:-.005em}
.story .sm{color:var(--dim);font-size:13px;margin-top:3px;display:block}
.story .why{color:var(--accent);font-size:12.5px;margin-top:4px;display:block;opacity:.9}
.story .src{font:500 10.5px/1 var(--mono);color:#5f7183;margin-top:6px;display:block}
.score{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:10px;
padding:8px 15px;border-top:1px solid var(--line);font-size:14px}
.score:first-child{border-top:0}
.t{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.t.l{text-align:right;color:var(--dim)}
.t.r{text-align:left;color:var(--dim)}
.t.win{color:var(--ink);font-weight:650}
.mid{font:600 13px/1 var(--mono);padding:3px 9px;border-radius:6px;background:#0d141d;border:1px solid var(--line)}
.note{padding:7px 15px;border-top:1px solid var(--line);color:var(--dim);font-size:11.5px;font-family:var(--mono)}
.league{padding:7px 15px;border-top:1px solid var(--line);background:#0d141d;
font:700 10.5px/1 var(--mono);letter-spacing:.12em;text-transform:uppercase;color:var(--amber)}
.para{padding:11px 15px;border-top:1px solid var(--line);color:var(--dim);font-size:13px;margin:0}
footer{border-top:1px solid var(--line);padding-top:12px;color:#5f7183;font:400 11.5px/1.6 var(--mono)}
.rows{list-style:none;margin:0;padding:0}
.rows li{border-top:1px solid var(--line)}
.rows li:first-child{border-top:0}
.rows a{display:flex;justify-content:space-between;gap:14px;padding:12px 15px;color:inherit}
.rows a:hover{background:#141c28;text-decoration:none}
.rows .d{font:500 10.5px/1.6 var(--mono);color:#5f7183;white-space:nowrap}
.rows .t2{font-weight:600;font-size:14px}
.nav{position:sticky;top:0;z-index:5;display:flex;gap:4px;overflow-x:auto;padding:10px 0 11px;margin:0 0 18px;
background:linear-gradient(#0a0e13 72%,rgba(10,14,19,0));border-bottom:1px solid var(--line);scrollbar-width:none}
.nav::-webkit-scrollbar{display:none}
.nav a{flex:0 0 auto;font:600 10px/1 var(--mono);letter-spacing:.09em;text-transform:uppercase;color:var(--dim);
padding:7px 9px;border-radius:999px;border:1px solid transparent;white-space:nowrap}
.nav a:hover{color:var(--ink);border-color:var(--line);text-decoration:none}
.hero{width:100%;height:210px;object-fit:cover;border-radius:12px;border:1px solid var(--line);
margin:0 0 20px;display:block;background:#0e141c}
.story{position:relative}
.story img{width:58px;height:58px;object-fit:cover;border-radius:8px;float:right;margin:0 0 6px 12px;
border:1px solid var(--line);background:#0e141c}
.story .hl{padding-right:0}
@media(max-width:520px){.story img{width:46px;height:46px}}
.series{display:flex;gap:14px;margin:6px 0 16px;flex-wrap:wrap}
.series>div{flex:1 1 92px;min-width:84px}
.series b{display:block;font:600 11.5px/1.35 var(--mono);color:var(--ink)}
.series span{display:block;font:500 10px/1.4 var(--mono);color:var(--dim)}
.series svg{display:block;width:100%;height:24px;margin-top:3px}
audio{width:100%;margin:0 0 18px;filter:invert(.9) hue-rotate(180deg);border-radius:10px}
.notify{margin:24px 0 0;border-top:1px solid var(--line);padding-top:14px}
.notify summary{font:600 10.5px/1 var(--mono);letter-spacing:.12em;text-transform:uppercase;color:var(--dim);cursor:pointer}
.notify p{color:var(--dim);font-size:12.5px;margin:9px 0 11px;max-width:70ch}
.notify button{font:600 11px/1 var(--mono);letter-spacing:.06em;text-transform:uppercase;background:#0e141c;color:var(--ink);border:1px solid var(--line);border-radius:8px;padding:10px 13px;cursor:pointer}
.notify button:hover{border-color:#2a6f63}
.notify textarea{width:100%;margin-top:10px;min-height:70px;background:#0d141d;color:var(--dim);border:1px solid var(--line);border-radius:8px;font:400 11px/1.4 var(--mono);padding:9px}
/* unfolded foldables and desktop: two columns so a 7.6" inner screen is not half empty */
@media(min-width:820px){
  body{max-width:1180px;padding:30px 30px 56px}
  .grid{column-count:2;column-gap:24px}
  .grid>section{break-inside:avoid;-webkit-column-break-inside:avoid;page-break-inside:avoid}
  .hero{height:300px}
  h1{font-size:36px}
  .series>div{flex:1 1 120px}
}
"""


def esc(text):
    return html.escape(text, quote=False)


def domain_of(url):
    try:
        host = urllib.parse.urlparse(url).netloc.lower()
    except ValueError:
        return "link"
    return host[4:] if host.startswith("www.") else host or "link"


def split_trailing_url(body):
    m = URL_RE.search(body)
    if not m:
        return body, ""
    return body[: m.start()].strip(), m.group(1)


def story_html(body):
    text, url = split_trailing_url(body)
    why = ""
    m = MATTERS_RE.search(text)
    if m:
        why = text[m.end():].strip()
        text = text[: m.start()].strip()
    parts = re.split(r"\s+[—–]\s+", text, maxsplit=1)
    headline = parts[0].strip().rstrip(".")
    summary = parts[1].strip() if len(parts) > 1 else ""
    href = f' href="{html.escape(url)}"' if url else ""
    tag = "a" if url else "div"
    out = [f'<{tag} class="story"{href}>', f'<span class="hl">{esc(headline)}</span>']
    if summary:
        out.append(f'<span class="sm">{esc(summary)}</span>')
    if why:
        out.append(f'<span class="why">Why it matters: {esc(why)}</span>')
    if url:
        out.append(f'<span class="src">{esc(domain_of(url))}</span>')
    out.append(f"</{tag}>")
    return "".join(out)


def score_html(body):
    text, url = split_trailing_url(body)
    m = SCORE_RE.match(text)
    if not m:
        return None
    a, b = m.group("a").strip(), m.group("b").strip()
    try:
        sa, sb = int(m.group("sa")), int(m.group("sb"))
    except ValueError:
        return None
    note = (m.group("note") or "").strip()
    return (
        '<div class="score">'
        f'<span class="t l{" win" if sa > sb else ""}">{esc(a)}</span>'
        f'<span class="mid">{sa} – {sb}</span>'
        f'<span class="t r{" win" if sb > sa else ""}">{esc(b)}</span>'
        "</div>" + (f'<div class="note">{esc(note)}</div>' if note else "")
    )


def is_score(line):
    m = SCORE_RE.match(line)
    return bool(m) and len(m.group("a")) <= 28 and len(m.group("b")) <= 28


def parse(markdown):
    greeting, workout = "", ""
    sections, tail, cur = [], [], None
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
            cur = (m.group(1).strip(), [])
            sections.append(cur)
            continue
        if cur is None:
            tail.append(line)
            continue
        name, chunks = cur
        mb = BULLET_RE.match(line)
        if mb:
            body = mb.group(1).strip().replace("**", "")
            sc = score_html(body) if is_score(body) else None
            chunks.append(sc if sc else story_html(body))
            continue
        if len(line) < 40 and not line.endswith(".") and line[0].isupper() and "http" not in line:
            chunks.append(f'<div class="league">{esc(line)}</div>')
            continue
        chunks.append(f'<p class="para">{esc(line)}</p>')
    return greeting, workout, sections, tail


def _nav_label(name):
    return re.sub(r"^[^\w]+", "", name).strip() or name


def _spanish_safe(text):
    return html.escape(text, quote=True)


def fetch_weather(lat=52.23, lon=21.01):
    """One-line Warsaw weather for the header. Never raises."""
    try:
        url = ("https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
               "&current=temperature_2m,precipitation,weather_code&hourly=precipitation_probability"
               "&timezone=Europe%%2FWarsaw&forecast_days=1" % (lat, lon))
        req = urllib.request.Request(url, headers={"User-Agent": "daily-briefing"})
        with urllib.request.urlopen(req, timeout=8) as r:
            data = json.load(r)
    except Exception:
        return ""
    try:
        temp = round(data["current"]["temperature_2m"])
        code = data["current"].get("weather_code", 0)
        icon = "\u2600\ufe0f"
        if code in (1, 2):
            icon = "\U0001f324\ufe0f"
        elif code == 3:
            icon = "\u2601\ufe0f"
        elif code in (45, 48):
            icon = "\U0001f32b\ufe0f"
        elif 51 <= code <= 67 or 80 <= code <= 82:
            icon = "\U0001f326\ufe0f"
        elif 71 <= code <= 77 or 85 <= code <= 86:
            icon = "\U0001f328\ufe0f"
        elif code >= 95:
            icon = "\u26c8\ufe0f"
        hours = data.get("hourly", {})
        times = hours.get("time", [])
        probs = hours.get("precipitation_probability", [])
        now = datetime.datetime.now()
        wet = None
        for t, p in zip(times, probs):
            if p is None:
                continue
            try:
                when = datetime.datetime.fromisoformat(t)
            except ValueError:
                continue
            if 0 <= (when - now).total_seconds() / 3600 <= 6 and p >= 50:
                wet = when.strftime("%H:%M")
                break
        rain = f"rain from {wet}" if wet else "dry next 6h"
        return f"{icon} {temp}\u00b0C \u00b7 {rain}"
    except Exception:
        return ""


def _cover_cache():
    try:
        with open(os.path.join(HOME, "_og", "covers.json"), encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def _fetch_cover(url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"})
        with urllib.request.urlopen(req, timeout=8) as r:
            head = r.read(120000).decode("utf-8", "replace")
    except Exception:
        return None
    m = re.search(r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)', head, re.I)
    if not m:
        m = re.search(r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image["\']', head, re.I)
    if not m:
        return None
    img = html.unescape(m.group(1)).strip()
    if not img.startswith(("http://", "https://")):
        return None
    return img


def gather_covers(urls, limit=10):
    """og:image per story URL, cached on disk, fetched in parallel. Never raises."""
    import concurrent.futures
    cache = _cover_cache()
    todo = [u for u in urls[:limit] if u and u not in cache]
    if todo:
        got = 0
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
                for url, img in zip(todo, pool.map(_fetch_cover, todo)):
                    if img:                       # never cache a failure - it would be permanent
                        cache[url] = img
                        got += 1
        except Exception:
            pass
        print(f"covers: {got}/{len(todo)} fetched, {len(cache)} cached")
        try:
            os.makedirs(os.path.join(HOME, "_og"), exist_ok=True)
            with open(os.path.join(HOME, "_og", "covers.json"), "w", encoding="utf-8") as fh:
                json.dump(cache, fh)
        except Exception:
            pass
    return {k: v for k, v in cache.items() if v}


def _spark(values, width=100, height=26):
    n = len(values)
    lo, hi = min(values), max(values)
    span = (hi - lo) or 1.0
    pts = " ".join(
        f"{i / (n - 1) * width:.1f},{height - 2 - (v - lo) / span * (height - 5):.1f}"
        for i, v in enumerate(values)
    )
    color = "#5eead4" if values[-1] >= values[0] else "#fb7185"
    return (f'<svg viewBox="0 0 {width} {height}" preserveAspectRatio="none">'
            f'<polyline fill="none" stroke="{color}" stroke-width="1.5" '
            f'stroke-linejoin="round" points="{pts}"/></svg>')


def sparklines(days=14):
    """Mini trend charts from the tape history the context script writes."""
    try:
        with open(os.path.join(HOME, "_og", "tape_history.json"), encoding="utf-8") as fh:
            hist = json.load(fh)
    except Exception:
        return ""
    if not isinstance(hist, dict) or len(hist) < 2:
        return ""
    dated = sorted(hist.items())[-days:]
    blocks = []
    for key, fmt in (("WIG20", "{:,.1f}"), ("BTC", "${:,.0f}"), ("Brent", "${:,.2f}"), ("USD/PLN", "{:.3f}")):
        pts = [(d, v.get(key)) for d, v in dated if isinstance(v, dict) and v.get(key)]
        pts = [(d, float(val)) for d, val in pts]
        if len(pts) < 2:
            continue
        vals = [v for _, v in pts]
        change = (vals[-1] / vals[0] - 1) * 100 if vals[0] else 0
        arrow = "\u25b2" if change >= 0 else "\u25bc"
        blocks.append(
            f'<div><b>{key} {fmt.format(vals[-1])}</b>'
            f'<span>{len(vals)}d {arrow} {abs(change):.1f}%</span>{_spark(vals)}</div>'
        )
    return f'<div class="series">{"".join(blocks)}</div>' if blocks else ""


def render(markdown, now=None, covers=None, weather=""):
    now = now or datetime.datetime.now()
    greeting, workout, sections, tail = parse(markdown)

    headline_title = ""
    for name, chunks in sections:
        if "tl;dr" in name.lower():
            m = re.search(r'<span class="hl">(.*?)</span>', "".join(chunks))
            if m:
                headline_title = html.unescape(m.group(1))
            break
    page_title = headline_title or "Daily Briefing"

    minutes = max(1, round(len(re.findall(r"\w+", markdown)) / 220))
    covers = covers or {}

    # the edition carries its own date: a stale markdown must never be stamped "now"
    edition = now.date()
    m = re.search(r"(\d{1,2})\s+([A-Z][a-z]+)\s+(\d{4})", greeting or "")
    if m:
        try:
            edition = datetime.datetime.strptime(" ".join(m.groups()), "%d %B %Y").date()
        except ValueError:
            pass
    fresh = edition == now.date()

    chips = []
    if workout:
        chips.append(f'<span class="chip{" live" if workout.startswith("💪") else ""}">{esc(workout)}</span>')
    chips.append(f'<span class="chip">{len(sections)} sections · {minutes} min read</span>')
    if weather and fresh:
        chips.append(f'<span class="chip">{esc(weather)}</span>')
    if fresh:
        chips.append(f'<span class="chip">{edition:%a %d %b %Y}, {now:%H:%M}</span>')
    else:
        chips.append(f'<span class="chip warn">edition from {edition:%a %d %b %Y} — today\'s not published yet</span>')

    nav_items = "".join(
        f'<a href="#s{i}">{esc(_nav_label(name))}</a>'
        for i, (name, _) in enumerate(sections)
        if _nav_label(name).lower() not in ("tl;dr", "what changed", "following")
    )
    hero = ""
    for name, chunks in sections:
        for c in chunks:
            m = re.search(r'href="([^"]+)"', c)
            if m and m.group(1) in covers:
                hero = covers[m.group(1)]
                break
        if hero:
            break

    out = [
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">",
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        f"<title>{esc(page_title)}</title>",
        f'<meta property="og:title" content="{html.escape(page_title)}">',
        f'<meta property="og:description" content="{html.escape(greeting)}">',
        f'<meta property="og:image" content="{SITE}og.png">',
        '<meta property="og:type" content="article">',
        '<meta name="twitter:card" content="summary_large_image">',
        '<link rel="manifest" href="site.webmanifest">',
        '<meta name="theme-color" content="#0a0e13">',
        '<link rel="apple-touch-icon" href="icon-512.png">',
        f"<style>{CSS}</style></head><body>",
        "<header>",
        '<div class="kicker">Daily Briefing · Warsaw</div>',
        f"<h1>{esc(page_title)}</h1>",
    ]
    if greeting:
        out.append(f'<div class="date">{esc(greeting)}</div>')
    out.append(f'<div class="chips">{"".join(chips)}</div>')
    series = sparklines()
    if series:
        out.append(series)
    if nav_items:
        out.append(f'<nav class="nav">{nav_items}</nav>')
    out.append("</header>")
    if hero:
        out.append(f'<img class="hero" src="{html.escape(hero)}" alt="">')

    out.append('<main class="grid">')
    for i, (name, chunks) in enumerate(sections):
        rendered = []
        for j, c in enumerate(chunks):
            if j < 4:
                m = re.search(r'href="([^"]+)"', c)
                if m and covers.get(m.group(1)):
                    img = f'<img loading="lazy" src="{html.escape(covers[m.group(1)])}" alt="">'
                    cut = c.find(">")
                    if cut > 0:
                        c = c[: cut + 1] + img + c[cut + 1:]
            rendered.append(c)
        n_items = sum(1 for c in rendered if 'class="story' in c)
        label = f'<span class="n">{n_items} stories</span>' if n_items > 1 else ""
        out.append(f'<section id="s{i}"><div class="head"><h2>{esc(name)}</h2>{label}</div>')
        out.append(f'<div class="card">{"".join(rendered)}</div></section>')
    if tail:
        out.append(f'<section><div class="card"><p class="para">{esc(" ".join(tail))}</p></div></section>')
    out.append("</main>")

    try:
        with open(os.path.join(os.path.dirname(HOME), "push_vapid_public.txt"), encoding="utf-8") as fh:
            vapid = fh.read().strip()
    except OSError:
        vapid = ""
    if vapid:
        out.append(PUSH_HTML.replace("__KEY__", vapid))

    out.append('<footer><a href="app/">Open the app</a> · sources linked per story · '
               'facts retrieved in the run that produced this page · '
               '<a href="archive.html">archive</a> · <a href="feed.xml">RSS</a></footer>'
               "<script>if('serviceWorker' in navigator){addEventListener('load',function(){"
               "navigator.serviceWorker.register('sw.js').catch(function(){})})}</script>"
               "</body></html>")
    meta = {
        "date": edition.isoformat(),
        "title": page_title,
        "greeting": greeting,
        "workout": workout,
        "sections": [n for n, _ in sections],
        "minutes": minutes,
    }
    return "\n".join(out), meta


# ---------------------------------------------------------------- archive / rss
def load_manifest():
    try:
        with open(os.path.join(HOME, "manifest.json"), encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, list) else []
    except Exception:
        return []


def save_manifest(entry):
    items = [i for i in load_manifest() if i.get("date") != entry["date"]]
    items.append(entry)
    items.sort(key=lambda i: i.get("date", ""), reverse=True)
    items = items[:400]
    with open(os.path.join(HOME, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(items, fh, ensure_ascii=False, indent=1)
    return items


def write_archive(items):
    rows = []
    for it in items:
        d = it.get("date", "")
        try:
            pretty = datetime.date.fromisoformat(d).strftime("%a %d %b %Y")
        except ValueError:
            pretty = d
        rows.append(
            f'<li><a href="{esc(d)}.html"><span class="t2">{esc(it.get("title", "Briefing"))}</span>'
            f'<span class="d">{esc(pretty)}</span></a></li>'
        )
    body = "".join(rows) or '<li><p class="para">Nothing yet.</p></li>'
    page = (
        '<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        f"<title>Briefing archive</title><style>{CSS}"
        ".search{width:100%;padding:11px 14px;border-radius:10px;border:1px solid var(--line);"
        "background:#0e141c;color:var(--ink);font:400 14px/1.4 inherit;margin-bottom:14px}"
        ".search:focus{outline:none;border-color:#2a6f63}"
        ".empty{padding:14px;color:var(--dim);font-size:13px;display:none}"
        "</style></head><body>"
        '<header><div class="kicker">Daily Briefing · Warsaw</div><h1>Archive</h1>'
        f'<div class="date">{len(items)} editions</div>'
        '<div class="chips"><span class="chip"><a href="index.html">latest edition</a></span>'
        '<span class="chip"><a href="feed.xml">RSS</a></span></div></header>'
        '<section><input class="search" id="q" type="search" placeholder="Search headlines…" '
        'autocomplete="off" aria-label="Search editions">'
        f'<div class="card"><ul class="rows" id="list">{body}</ul>'
        '<p class="empty" id="empty">No edition matches that.</p></div></section>'
        '<footer><a href="app/">Open the app</a></footer>'
        "<script>(function(){var q=document.getElementById('q'),list=document.getElementById('list'),"
        "empty=document.getElementById('empty');q.addEventListener('input',function(){var t=q.value.toLowerCase(),"
        "n=0;Array.prototype.forEach.call(list.children,function(li){if(!li.querySelector)return;"
        "var hit=li.textContent.toLowerCase().indexOf(t)>-1;li.style.display=hit?'':'none';if(hit)n++;});"
        "empty.style.display=n?'none':'block';});})();</script>"
        "</body></html>"
    )
    with open(os.path.join(HOME, "archive.html"), "w", encoding="utf-8") as fh:
        fh.write(page)


def write_feed(items):
    entries = []
    for it in items[:30]:
        d = it.get("date", "")
        try:
            dt = datetime.datetime.fromisoformat(d).replace(hour=8, tzinfo=datetime.timezone.utc)
            stamp = email.utils.format_datetime(dt)
        except ValueError:
            stamp = ""
        entries.append(
            "  <item>\n"
            f"    <title>{html.escape(it.get('title', 'Briefing'))}</title>\n"
            f"    <link>{SITE}{d}.html</link>\n"
            f"    <guid>{SITE}{d}.html</guid>\n"
            f"    <pubDate>{stamp}</pubDate>\n"
            f"    <description>{html.escape(it.get('greeting', ''))}</description>\n"
            "  </item>"
        )
    feed = ('<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0"><channel>\n'
            "  <title>Daily Briefing</title>\n"
            f"  <link>{SITE}</link>\n"
            "  <description>Daily briefing</description>\n"
            "  <language>en</language>\n" + "\n".join(entries) + "\n</channel></rss>\n")
    with open(os.path.join(HOME, "feed.xml"), "w", encoding="utf-8") as fh:
        fh.write(feed)


# ------------------------------------------------------------------ social card
def wrap(text, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        if len(cur) + len(w) + 1 <= width:
            cur = f"{cur} {w}".strip()
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines[:3]


def _plain(text):
    return re.sub(r"[^\w\s\-.,:;'!?%$&()/—–]", "", text).strip()


def write_og_card(title, greeting, workout, out_path):
    """1200x630 card via ffmpeg drawtext. Best effort — never raises."""
    try:
        tmp = os.path.join(HOME, "_og")
        os.makedirs(tmp, exist_ok=True)
        t_all = wrap(_plain(title) or "Daily Briefing", 34)
        t_lines = t_all[:3]
        if len(t_all) > 3:
            t_lines[-1] = t_lines[-1].rstrip(" ,;:") + "…"
        # keep the workout line to its first sentence so it cannot run off the card
        short_workout = re.split(r"\.\s", _plain(workout), maxsplit=1)[0]
        w_lines = wrap(short_workout, 62)[:2] or [""]
        files = {
            "kicker.txt": "DAILY BRIEFING \u00b7 WARSAW",
            "title.txt": "\n".join(t_lines),
            "date.txt": _plain(greeting)[:64],
            "work.txt": "\n".join(w_lines),
            "foot.txt": "kazimeirzodnowiciel.github.io/briefings",
        }
        for name, value in files.items():
            # newline="" keeps LF: a CRLF file makes drawtext break the line twice
            with open(os.path.join(tmp, name), "w", encoding="utf-8", newline="\n") as fh:
                fh.write(value)
        p = lambda n: os.path.join(tmp, n).replace("\\", "/").replace(":", "\\:")

        y_title, lh = 104, 74
        title_block = lh * len(t_lines)
        y_date = y_title + title_block + 34
        y_work = y_date + 56
        box_h = title_block + 46
        vf = (
            f"drawbox=x=64:y=58:w=6:h={box_h}:color=0x5eead4:t=fill,"
            f"drawtext=fontfile='{FONT_BOLD}':textfile='{p('kicker.txt')}':fontcolor=0x5eead4:fontsize=26:x=92:y=58,"
            f"drawtext=fontfile='{FONT_BOLD}':textfile='{p('title.txt')}':fontcolor=0xe8eef7:fontsize=58:"
            f"x=92:y={y_title}:line_spacing=16,"
            f"drawtext=fontfile='{FONT_REG}':textfile='{p('date.txt')}':fontcolor=0x8d9bad:fontsize=28:x=92:y={y_date},"
            f"drawtext=fontfile='{FONT_BOLD}':textfile='{p('work.txt')}':fontcolor=0x5eead4:fontsize=26:"
            f"x=92:y={y_work}:line_spacing=8,"
            f"drawtext=fontfile='{FONT_REG}':textfile='{p('foot.txt')}':fontcolor=0x5f7183:fontsize=22:"
            "x=92:y=580"
        )
        res = subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi",
             "-i", "color=c=0x0a0e13:s=1200x630", "-vf", vf, "-frames:v", "1", out_path],
            capture_output=True, text=True, timeout=90,
        )
        if res.returncode != 0:
            print("og card skipped:", (res.stderr or "").strip()[:200])
            return False
        print("wrote", out_path)
        return True
    except Exception as exc:
        print("og card skipped:", exc)
        return False


def _kind(name):
    n = name.lower()
    if "nba" in n or "football" in n or "soccer" in n:
        return "scores"
    if "tonight" in n or "fixture" in n:
        return "fixtures"
    return "stories"


def _body_cache():
    try:
        with open(os.path.join(HOME, "_og", "bodies2.json"), encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


TAG_RE = re.compile(r"<[^>]+>")
PARA_RE = re.compile(r"<p[^>]*>(.*?)</p>", re.I | re.S)
SCRIPT_RE = re.compile(r"<(script|style|noscript)[^>]*>.*?</\1>", re.I | re.S)
ARTICLE_RE = re.compile(r"<article[^>]*>(.*?)</article>", re.I | re.S)
NOISE_RE = re.compile(
    r"(sign up|log in|newsletter|subscribe|cookie|advertis|navigation|share this|follow us|"
    r"read more|rights reserved|skip to|watch live|download the app|terms of|privacy policy|"
    r"all the latest|breaking news alerts|show more|menu|add as preferred|watch:|"
    r"zaloguj|reklama|e-wydanie|serwisy|wydarzenia|prenumerat|czytaj wi|polecane|"
    r"skróć artykuł|udostępnij|komentarzy|zapytaj o więcej|"
    r"^share\b|^save\b)", re.I)


def _fetch_body(url):
    """First few paragraphs of the article, for the app's reader view."""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"})
        with urllib.request.urlopen(req, timeout=10) as r:
            page = r.read(400000).decode("utf-8", "replace")
    except Exception:
        return None
    page = SCRIPT_RE.sub(" ", page)
    # prefer the article element; nav junk lives outside it on most sites
    match = ARTICLE_RE.search(page)
    scope = match.group(1) if match else page
    chunks = []
    for para in PARA_RE.findall(scope):
        text = html.unescape(TAG_RE.sub(" ", para))
        text = re.sub(r"\s+", " ", text).strip()
        text = re.sub(r"\s+([,.;:!?)\]])", r"\1", re.sub(r"([(\[])\s+", r"\1", text))
        if len(text) < 80 or NOISE_RE.search(text):
            continue
        if text.count(" ") < 14 or not text.rstrip().endswith((".", "!", "?", '"', "'", ":")):
            continue          # a real paragraph reads like a sentence and ends like one
        chunks.append(text)
        if sum(len(c) for c in chunks) > 2600:
            break
    if not chunks:
        return None
    body = "\n\n".join(chunks)          # the app renders one paragraph per block
    return body[:2600].rsplit(" ", 1)[0] + "…" if len(body) > 2600 else body


def gather_bodies(urls, limit=40):
    """Article text per story URL, cached on disk. Never raises."""
    import concurrent.futures
    cache = _body_cache()
    todo = [u for u in urls[:limit] if u and u not in cache]
    if todo:
        got = 0
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
                for url, body in zip(todo, pool.map(_fetch_body, todo)):
                    if body:
                        cache[url] = body
                        got += 1
        except Exception:
            pass
        print(f"bodies: {got}/{len(todo)} extracted, {len(cache)} cached")
        try:
            os.makedirs(os.path.join(HOME, "_og"), exist_ok=True)
            with open(os.path.join(HOME, "_og", "bodies2.json"), "w", encoding="utf-8") as fh:
                json.dump(cache, fh)
        except Exception:
            pass
    return cache


IMG_RE = re.compile(r"<img[^>]+(?:data-src|src)=[\"']([^\"']+\.(?:jpe?g|png|webp))[^\"']*", re.I)
JUNK_IMG = re.compile(
    r"(logo|placeholder|default|fallback|sprite|icon|avatar|no-?image|badge|"
    r"googleplay|app-store|apple|subscribe|banner|promo|play\.google|/thumb|/480/)", re.I)


def _content_image(page):
    """First plausible photograph inside <article> — usually better than og:image,
    which publishers often replace with a generated card that repeats the headline."""
    match = ARTICLE_RE.search(page)
    scope = match.group(1) if match else page
    for candidate in IMG_RE.findall(scope):
        url = html.unescape(candidate).strip()
        if not url.startswith("http") or JUNK_IMG.search(url):
            continue
        if len(url) < 20:
            continue
        return url
    return None


def gather_images(urls, limit=40):
    """Article-body image per story URL, cached on disk. Never raises."""
    import concurrent.futures
    path = os.path.join(HOME, "_og", "images.json")
    try:
        with open(path, encoding="utf-8") as fh:
            cache = json.load(fh)
    except Exception:
        cache = {}
    todo = [u for u in urls[:limit] if u and u not in cache]
    if todo:
        def fetch(u):
            try:
                req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"})
                with urllib.request.urlopen(req, timeout=10) as r:
                    return _content_image(r.read(400000).decode("utf-8", "replace"))
            except Exception:
                return None
        got = 0
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
                for url, img in zip(todo, pool.map(fetch, todo)):
                    if img:
                        cache[url] = img
                        got += 1
        except Exception:
            pass
        print(f"article images: {got}/{len(todo)} found, {len(cache)} cached")
        try:
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(cache, fh)
        except Exception:
            pass
    return cache


def _image_dimensions(blob):
    """(width, height) from a JPEG/PNG/WebP header, or None. No PIL, no full download."""
    try:
        if blob[:8] == b"\x89PNG\r\n\x1a\n":
            w = int.from_bytes(blob[16:20], "big")
            h = int.from_bytes(blob[20:24], "big")
            return w, h
        if blob[:4] == b"RIFF" and blob[8:12] == b"WEBP":
            chunk = blob[12:16]
            if chunk == b"VP8X":
                w = int.from_bytes(blob[24:27], "little") + 1
                h = int.from_bytes(blob[27:30], "little") + 1
                return w, h
            if chunk == b"VP8 ":
                w = int.from_bytes(blob[26:28], "little") & 0x3FFF
                h = int.from_bytes(blob[28:30], "little") & 0x3FFF
                return w, h
            return None
        if blob[:2] == b"\xff\xd8":                      # JPEG: walk to a start-of-frame
            i = 2
            while i < len(blob) - 9:
                if blob[i] != 0xFF:
                    i += 1
                    continue
                marker = blob[i + 1]
                if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
                    h = int.from_bytes(blob[i + 5:i + 7], "big")
                    w = int.from_bytes(blob[i + 7:i + 9], "big")
                    return w, h
                length = int.from_bytes(blob[i + 2:i + 4], "big")
                if length <= 0:
                    return None
                i += 2 + length
    except Exception:
        return None
    return None


def _is_card(url):
    """True when the image is a generated social card (≈1.91:1, headline baked in)."""
    path = os.path.join(HOME, "_og", "image_meta.json")
    try:
        with open(path, encoding="utf-8") as fh:
            meta = json.load(fh)
    except Exception:
        meta = {}
    if url in meta:
        return meta[url] == "card"
    verdict = "unknown"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; daily-briefing)"})
        with urllib.request.urlopen(req, timeout=8) as r:
            blob = r.read(65536)
        size = _image_dimensions(blob)
        if size:
            w, h = size
            ratio = w / h if h else 0
            verdict = "card" if (w >= 1000 and 1.78 <= ratio <= 2.02) else "photo"
    except Exception:
        pass
    meta[url] = verdict
    try:
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(meta, fh)
    except Exception:
        pass
    return verdict == "card"


def drop_cards(mapping):
    """Remove social-card images from a url->image map, in parallel."""
    import concurrent.futures
    urls = list({v for v in mapping.values() if v})
    if not urls:
        return mapping
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            cards = {u for u, is_card in zip(urls, pool.map(_is_card, urls)) if is_card}
    except Exception:
        return mapping
    if cards:
        print(f"dropped {len(cards)} social-card image(s)")
    return {k: v for k, v in mapping.items() if v not in cards}


def _story_item(body, covers, bodies=None, images=None):
    text, url = split_trailing_url(body)
    paywall = "(paywall)" in text.lower()
    text = re.sub(r"\(paywall\)", "", text, flags=re.I).strip()
    why = ""
    m = MATTERS_RE.search(text)
    if m:
        why = text[m.end():].strip()
        text = text[: m.start()].strip()
    parts = re.split(r"\s+[—–]\s+", text, maxsplit=1)
    headline = parts[0].strip().rstrip(".")
    summary = parts[1].strip() if len(parts) > 1 else ""
    day = 2 if re.search(r"\(day 2\)", headline, re.I) else 1
    headline = re.sub(r"\s*\(day 2\)", "", headline, flags=re.I).strip()
    return {
        "type": "story",
        "headline": headline,
        "summary": summary or None,
        "matters": why or None,
        "url": url or None,
        "source": domain_of(url) if url else None,
        "image": ((images or {}).get(url) or (covers or {}).get(url)) if url else None,
        "body": (bodies or {}).get(url) if url else None,
        "day": day,
        "paywall": paywall,
    }


def _score_item(body, group):
    text, _url = split_trailing_url(body)
    m = SCORE_RE.match(text)
    if not m or len(m.group("a")) > 28 or len(m.group("b")) > 28:
        return {"type": "note", "text": text}
    note = (m.group("note") or "").strip()
    flag = None
    if re.search(r"\bOT\b", note):
        flag = "OT"
    if "UPSET" in note.upper():
        flag = "UPSET"
    sa, sb = int(m.group("sa")), int(m.group("sb"))
    home_scorers, away_scorers, note = _split_scorers(note, sa, sb)
    return {
        "type": "score",
        "group": group or None,
        "home": m.group("a").strip().rstrip("."),
        "away": m.group("b").strip().rstrip("."),
        "home_score": sa,
        "away_score": sb,
        "home_scorers": home_scorers or None,
        "away_scorers": away_scorers or None,
        "note": note or None,
        "flag": flag,
    }


GOAL_MINUTE = re.compile(r"\d{1,3}(?:\+\d+)?'")


def _split_scorers(note, sa, sb):
    """"Wirtz 60', 71'; Bozeník 56'; late equaliser" -> (["Wirtz 60', 71'"], ["Bozeník 56'"], "late equaliser").

    Football lines list the home side's goals, then the away side's, separated by "; ".
    A side that did not score has no segment. Anything left over is the note."""
    parts = [p.strip() for p in note.split(";")]
    parts = [p for p in parts if p not in ("", "-", "–")] if parts else []
    goals = lambda p: bool(GOAL_MINUTE.search(p)) and len(p) < 160 and not p.endswith(".")
    home, away = [], []
    if sa and parts and goals(parts[0]):
        home = [g.strip() for g in parts.pop(0).split(",") if g.strip()]
    if sb and parts and goals(parts[0]):
        away = [g.strip() for g in parts.pop(0).split(",") if g.strip()]
    if not home and not away:
        return [], [], note
    return _merge_minutes(home), _merge_minutes(away), "; ".join(parts)


def _merge_minutes(goals):
    """["Wirtz 60'", "71'"] -> ["Wirtz 60', 71'"]: a bare minute belongs to the scorer before it."""
    out = []
    for g in goals:
        if out and re.fullmatch(r"\d{1,3}'?(?:\+\d+)?'(?:\s*\((?:pen|og)\))?", g):
            out[-1] += ", " + g
        else:
            out.append(g)
    return out


def build_feed(markdown, covers=None, now=None, meta=None, bodies=None, images=None):
    """The machine-readable edition the Android app reads. Same source as the page."""
    now = now or datetime.datetime.now()
    meta = meta or {}
    covers = covers or {}
    bodies = bodies or {}
    images = images or {}
    greeting, workout, raw_sections = _raw_sections(markdown)

    sections = []
    for sec in raw_sections:
        kind = _kind(sec["name"])
        items, group = [], None
        for line in sec["lines"]:
            if line.startswith(("- ", "• ")):
                body = line[2:].strip().replace("**", "")
                if kind == "scores":
                    items.append(_score_item(body, group))
                elif kind == "fixtures":
                    items.append({"type": "fixture", "text": _strip_url(body)[0]})
                else:
                    items.append(_story_item(body, covers, bodies, images))
                continue
            if len(line) < 40 and not line.endswith(".") and line[0].isupper() and "http" not in line:
                group = line                      # league label
                continue
            items.append({"type": "note", "text": line})
        sections.append({"name": sec["name"], "kind": kind, "items": items})

    audio = None  # the spoken edition was dropped on 2026-11-17; both apps hide audio when null
    return {
        "schema": 1,
        "generated": now.isoformat(timespec="seconds"),
        "date": meta.get("date") or now.date().isoformat(),
        "greeting": meta.get("greeting") or greeting,
        "title": meta.get("title") or "Daily Briefing",
        "workout": workout or None,
        "audio": audio,
        "read_minutes": meta.get("minutes"),
        "sections": sections,
    }


def _strip_url(text):
    m = URL_RE.search(text)
    return (text[: m.start()].strip(), m.group(1)) if m else (text, "")


def _raw_sections(markdown):
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
            cur = {"name": m.group(1).strip(), "lines": []}
            sections.append(cur)
            continue
        if cur is not None:
            cur["lines"].append(line)
    return greeting, workout, sections


def main():
    if len(sys.argv) < 2:
        print("usage: render_briefing.py <briefing.md> [out.html]")
        return 2
    src = sys.argv[1]
    markdown = open(src, encoding="utf-8").read()
    now = datetime.datetime.now()
    urls = re.findall(r"https?://\S+", markdown)
    covers = gather_covers(urls, limit=14)
    bodies = gather_bodies(urls, limit=40)
    images = drop_cards(gather_images(urls, limit=40))
    covers = drop_cards(covers)
    weather = fetch_weather()
    page, meta = render(markdown, now, covers=covers, weather=weather)

    out = sys.argv[2] if len(sys.argv) > 2 else os.path.splitext(src)[0] + ".html"
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(page)

    os.makedirs(HOME, exist_ok=True)
    for name in (f"{meta['date']}.html", "index.html"):
        with open(os.path.join(HOME, name), "w", encoding="utf-8") as fh:
            fh.write(page)
    items = save_manifest(meta)
    write_archive(items)
    write_feed(items)

    feed = build_feed(markdown, covers, now, meta, bodies, images)
    with open(os.path.join(HOME, "feed.json"), "w", encoding="utf-8") as fh:
        json.dump(feed, fh, ensure_ascii=False, indent=1)
    edir = os.path.join(HOME, "editions")
    os.makedirs(edir, exist_ok=True)
    with open(os.path.join(edir, f"{feed['date']}.json"), "w", encoding="utf-8") as fh:
        json.dump(feed, fh, ensure_ascii=False, indent=1)
    write_og_card(meta["title"], meta["greeting"], meta["workout"], os.path.join(HOME, "og.png"))
    print("wrote", out)
    print("wrote", os.path.join(HOME, "index.html"), "+ archive.html + feed.xml")
    print(f"chars: {len(page)} | sections: {len(meta['sections'])} | read: {meta['minutes']} min")
    return 0


if __name__ == "__main__":
    sys.exit(main())
