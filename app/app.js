/* Briefing app: renders the pipeline's editions/*.json into an Android-first reader.
   No build step and no dependencies. The pipeline keeps writing index.html, editions
   and feed.json; this app only reads them, so a daily run can't overwrite it. */
'use strict';

const ROOT = new URL('../', location.href);
const TZ = 'Europe/Warsaw';
const CITY = { name: 'Warsaw', lat: 52.23, lon: 21.01 };
const PUSH_KEY = 'BKqpAbBnzuOjTj06IuYW7rUSWsggv7l9eSETLLA5FJ4JByIXs8BeYBE1w2BA_nqbZSOfgdiDchUCA_sjQTBX7Bo';
const SPEEDS = [1, 1.25, 1.5, 1.75, 2];

/* ---------------- utilities ---------------- */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ESC[c]);
const cap = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);
const clean = (s) => { if (s == null) return null; const t = String(s).replace(/\s+/g, ' ').trim(); return t || null; };
const safeUrl = (u) => (u && /^https?:\/\//i.test(u) ? u : null);
const hostOf = (u) => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch (e) { return null; } };
const hash = (s) => { let h = 5381; for (let i = 0; i < s.length; i++) h = ((h * 33) ^ s.charCodeAt(i)) >>> 0; return h.toString(36); };
const ymd = (d) => new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(d);
const fmt = (iso, o) => new Date(iso + 'T12:00:00Z').toLocaleDateString('en-GB', { timeZone: 'UTC', ...o });
const fmtShort = (iso) => fmt(iso, { weekday: 'short', day: 'numeric', month: 'short' });
const fmtLong = (iso) => fmt(iso, { weekday: 'long', day: 'numeric', month: 'long' });
const mmss = (s) => { s = Math.max(0, Math.floor(s || 0)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
const PL = /[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]/;

const store = {
  get(k, d) { try { const v = localStorage.getItem('bf:' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('bf:' + k, JSON.stringify(v)); } catch (e) { /* private mode / quota */ } },
};

async function pool(items, n, fn) {
  const q = [...items];
  await Promise.all(Array.from({ length: n }, async () => { while (q.length) await fn(q.shift()); }));
}

/* ---------------- icons (24px stroke set) ---------------- */
const ICON = {
  news: '<path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/><path d="M18 14h-8M15 18h-5M10 6h8v4h-8z"/>',
  trophy: '<path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6M18 9h1.5a2.5 2.5 0 0 0 0-5H18M4 22h16M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/>',
  bookmark: '<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"/>',
  bookmarkOn: '<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z" fill="currentColor"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01"/>',
  tune: '<path d="M21 4h-7M10 4H3M21 12h-9M8 12H3M21 20h-5M12 20H3M14 2v4M8 10v4M16 18v4"/>',
  play: '<path d="M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5Z"/>',
  pause: '<rect x="6" y="4" width="4.5" height="16" rx="1.2"/><rect x="13.5" y="4" width="4.5" height="16" rx="1.2"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  share: '<path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13"/>',
  external: '<path d="M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
  left: '<path d="m15 18-6-6 6-6"/>',
  right: '<path d="m9 18 6-6-6-6"/>',
  search: '<circle cx="11" cy="11" r="7.5"/><path d="m21 21-4.3-4.3"/>',
  headphones: '<path d="M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3"/>',
  dumbbell: '<path d="M6.5 6.5v11M17.5 6.5v11M3.5 9.5v5M20.5 9.5v5M6.5 12h11"/>',
  moon: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-2.64-6.36L21 8M21 3v5h-5"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  book: '<path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2zM22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z"/>',
};
const icon = (n, cls = '') => `<svg class="i ${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICON[n] || ''}</svg>`;

/* ---------------- state ---------------- */
const S = {
  latest: null,          // newest edition date
  date: null,            // edition being viewed
  tab: 'today',
  archive: [],           // [{date,title}] newest first
  dates: [],             // edition dates newest first
  missing: new Set(),    // dates without editions/*.json (pre-JSON era)
  editions: new Map(),   // date -> normalized edition
  stories: new Map(),    // id -> story
  read: new Set(store.get('read', [])),
  saved: store.get('saved', []),
  speed: store.get('speed', 1),
  sheetOpen: false,
  rendered: '',
  lastCheck: 0,
  query: '',
  audio: null,           // {url, staleFrom}
};

/* ---------------- data shaping ---------------- */
const HUES = [
  [/warcraft|\bwow\b/i, '#e09a1a'], [/^world/i, '#4f7fe0'], [/polska|poland/i, '#e5484d'],
  [/money|market/i, '#2f9e66'], [/\bai\b|tech/i, '#8e4ec6'], [/gaming/i, '#c026d3'], [/games|dev/i, '#12a594'],
  [/music/i, '#d6409f'], [/culture|film|tv/i, '#f76b15'], [/brussels|\beu\b/i, '#3e63dd'],
  [/tonight|football|nba|sport|score/i, '#65a30d'], [/long read/i, '#a18072'],
];
const hueOf = (label) => (HUES.find(([re]) => re.test(label)) || [0, 'var(--accent)'])[1];

function splitName(name) {
  const m = String(name || '').match(/^([^\p{L}\p{N}]*)(.*)$/u);
  return { emoji: (m[1] || '').trim(), label: (m[2] || '').trim() || 'Section' };
}
function roleOf(label, kind) {
  if (/tl;?dr/i.test(label)) return 'tldr';
  if (/what changed/i.test(label)) return 'changed';
  if (/^following/i.test(label)) return 'following';
  if (kind === 'scores' || kind === 'fixtures') return 'sports';
  if (/long read/i.test(label)) return 'longread';
  return 'news';
}
const teamName = (t) => (clean(t) || '').replace(/\.$/, '');
const SCORE_RE = /^(.+?)\s+(\d{1,3})\s*[-–]\s*(\d{1,3})\s+(.+?)(?:\s+[—–]\s+(.+))?$/;

function mkMatch(home, away, hs, as, note, flag) {
  const h = teamName(home), a = teamName(away);
  return { home: h, away: a, hs, as, note: cap(clean(note)), flag: clean(flag), fav: /poland|polska/i.test(h + ' ' + a) };
}
function groupScores(items) {
  const notes = [], groups = []; let cur = null;
  const push = (g, m) => { if (!cur || cur.name !== g) { cur = { name: g, matches: [] }; groups.push(cur); } cur.matches.push(m); };
  for (const it of items) {
    if (it.type === 'score') push(clean(it.group), mkMatch(it.home, it.away, it.home_score, it.away_score, it.note, it.flag));
    else if (it.type === 'note') {
      const t = clean(it.text) || '';
      const m = SCORE_RE.exec(t);
      // the writer sometimes emits a finished game as a free-text note: "76ers 131 - 124 Pacers — Maxey 27 pts"
      if (m && !/[:,;]/.test(m[1]) && m[1].split(' ').length <= 4) push(cur ? cur.name : null, mkMatch(m[1], m[4], +m[2], +m[3], m[5], null));
      else if (t) notes.push(t);
    }
  }
  return { notes, groups };
}
function groupFixtures(items) {
  const notes = [], groups = []; let cur = null;
  for (const it of items) {
    const t = clean(it.text); if (!t) continue;
    if (it.type !== 'fixture') { notes.push(t); continue; }
    const m = t.match(/^(.+?)\s+[—–]\s+(.+)$/);
    const comp = m ? m[1] : 'Fixtures', line = m ? m[2] : t;
    if (!cur || cur.name !== comp) { cur = { name: comp, lines: [] }; groups.push(cur); }
    cur.lines.push(line);
  }
  return { notes, groups };
}
function parseMarkets(line) {
  if (!line || !/WIG|PLN|BTC|Brent|S&P|Nasdaq|DAX/i.test(line)) return null;
  const out = [];
  for (const part of line.split(/\s+·\s+/)) {
    const m = part.match(/^(.+?)\s+([$€£])?([\d,]+(?:\.\d+)?)\s*(?:\(([+\-−]?\d+(?:\.\d+)?)%\))?\.?$/);
    if (!m) continue;
    out.push({ name: m[1].trim(), raw: (m[2] || '') + m[3], value: parseFloat(m[3].replace(/,/g, '')), pct: m[4] != null ? parseFloat(m[4].replace('−', '-')) : null, pctRaw: m[4] != null ? m[4].replace(/^[+\-−]/, '') : null });
  }
  return out.length >= 2 ? out : null;
}

function mkStory(it, date, sec) {
  const headline = clean(it.headline) || '';
  const url = safeUrl(it.url);
  const st = {
    id: date + ':' + hash(url || headline),
    date, section: sec.label, hue: sec.hue,
    headline,
    summary: cap(clean(it.summary)),
    matters: cap(clean(it.matters)),
    url,
    source: clean(it.source) || (url ? hostOf(url) : null),
    image: safeUrl(it.image),
    body: clean(it.body),
    day: it.day || 1,
    paywall: !!it.paywall,
  };
  st.lang = PL.test(headline + ' ' + (it.summary || '')) ? 'pl' : null;
  S.stories.set(st.id, st);
  return st;
}

function normalize(raw) {
  const date = raw.date;
  const sections = (raw.sections || []).map((s, i) => {
    const { emoji, label } = splitName(s.name);
    const role = roleOf(label, s.kind);
    const sec = { key: 's' + i, emoji, label, kind: s.kind, role, hue: role === 'tldr' ? 'var(--accent)' : hueOf(label) };
    const items = s.items || [];
    if (s.kind === 'scores') sec.board = groupScores(items);
    else if (s.kind === 'fixtures') sec.board = groupFixtures(items);
    else {
      sec.stories = []; sec.status = []; sec.notes = [];
      for (const it of items) {
        if (it.type === 'note') { const t = clean(it.text); if (t) sec.notes.push(t); continue; }
        const st = mkStory(it, date, sec);
        // one-line status rows (market line, "Dalamud 15.0.4.0 · .NET ...") carry no link or summary
        if (role === 'news' && !st.url && !st.summary) { sec.status.push(st.headline); S.stories.delete(st.id); continue; }
        sec.stories.push(st);
      }
    }
    return sec;
  });
  let markets = null;
  const money = sections.find((s) => s.status && /money|market/i.test(s.label));
  if (money) {
    const i = money.status.findIndex((t) => parseMarkets(t));
    if (i >= 0) { markets = parseMarkets(money.status[i]); money.status.splice(i, 1); }
  }
  return {
    date, sections, markets,
    title: clean(raw.title) || 'Daily Briefing',
    greeting: clean(raw.greeting),
    workout: clean(raw.workout),
    audio: raw.audio || null,
    minutes: raw.read_minutes || null,
    generated: raw.generated || null,
  };
}

const readable = (ed) => ed.sections.filter((s) => s.role === 'news' || s.role === 'longread').flatMap((s) => s.stories || []);

function workoutOf(text) {
  if (!text) return null;
  const t = text.replace(/^[^\p{L}\p{N}]+/u, '').trim();
  const sentences = (s) => s.split(/(?<=\.)\s+/).map((x) => x.replace(/\.$/, '').trim()).filter(Boolean);
  const parts = t.split(/\s+—\s+/);
  if (/rest day/i.test(parts[0])) {
    const ss = sentences(parts.slice(1).join(' — '));
    return { rest: true, title: 'Rest day', detail: cap(ss[0] || ''), meta: ss.slice(1) };
  }
  if (parts.length >= 3) {
    const ss = sentences(parts.slice(2).join(' — '));
    return { rest: false, title: parts[1], detail: ss[0] || '', meta: ss.slice(1) };
  }
  return { rest: false, title: 'Workout', detail: t, meta: [] };
}

const words = (s) => new Set((s || '').toLowerCase().replace(/ł/g, 'l').normalize('NFD').replace(/[̀-ͯ]/g, '').match(/[a-z0-9]{4,}/g) || []);
/* The renderer's `body` is raw scraped page text; sometimes nav junk or a different
   article entirely. Only surface it when it is clearly about the story. */
function excerptOf(st) {
  if (!st.body || st.body.length < 160) return null;
  const keys = words(st.headline + ' ' + (st.summary || ''));
  if (keys.size < 3) return null;
  const bw = words(st.body); let hit = 0;
  for (const w of keys) if (bw.has(w)) hit++;
  if (hit / keys.size < 0.45) return null;
  const sents = st.body.split(/(?<=[.!?…"”])\s+(?=[A-Z0-9"“‘ĄĆĘŁŃÓŚŹŻ])/u);
  let start = sents.findIndex((s) => { let h = 0; for (const w of words(s)) if (keys.has(w)) h++; return h >= 2; });
  if (start < 0) start = 0;
  let out = '';
  for (const s of sents.slice(start)) { if (out && out.length + s.length > 900) break; out += (out ? ' ' : '') + s; }
  if (!/[.!?…"”]$/.test(out)) out += '…';
  return out.length >= 120 ? out : null;
}

/* ---------------- loading ---------------- */
async function getJSON(path, fresh) {
  const r = await fetch(new URL(path, ROOT), fresh ? { cache: 'no-cache' } : {});
  if (!r.ok) throw new Error(path + ' ' + r.status);
  return r.json();
}
async function loadEdition(date) {
  if (S.editions.has(date)) return S.editions.get(date);
  try {
    const ed = normalize(await getJSON(`editions/${date}.json`));
    S.editions.set(date, ed);
    return ed;
  } catch (e) { S.missing.add(date); throw e; }
}
async function loadArchive() {
  try {
    const r = await fetch(new URL('archive.html', ROOT), { cache: 'no-cache' });
    const doc = new DOMParser().parseFromString(await r.text(), 'text/html');
    const seen = new Set();
    return $$('a[href]', doc).map((a) => {
      const m = (a.getAttribute('href') || '').match(/(\d{4}-\d{2}-\d{2})\.html$/);
      if (!m || seen.has(m[1])) return null;
      seen.add(m[1]);
      const t = a.querySelector('.t2');
      return { date: m[1], title: clean((t || a).textContent) };
    }).filter(Boolean);
  } catch (e) { return []; }
}
let historyP = null;
function loadHistory() {
  if (!historyP) {
    historyP = pool(S.dates.filter((d) => !S.missing.has(d)).slice(0, 40), 4, (d) => loadEdition(d).catch(() => {}))
      .then(() => { if (S.tab === 'today') paintMarkets(); if (S.tab === 'archive' && S.query) paintSearch(); });
  }
  return historyP;
}

async function weather() {
  const c = store.get('wx');
  if (c && Date.now() - c.t < 30 * 60e3) return c.v;
  const u = `https://api.open-meteo.com/v1/forecast?latitude=${CITY.lat}&longitude=${CITY.lon}&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Europe%2FWarsaw&forecast_days=1`;
  const j = await (await fetch(u)).json();
  const v = { t: Math.round(j.current.temperature_2m), code: j.current.weather_code, day: j.current.is_day, hi: Math.round(j.daily.temperature_2m_max[0]), lo: Math.round(j.daily.temperature_2m_min[0]), pop: j.daily.precipitation_probability_max[0] };
  store.set('wx', { t: Date.now(), v });
  return v;
}
function wmo(c, day) {
  if (c === 0) return [day ? '☀️' : '🌙', 'Clear'];
  if (c <= 2) return [day ? '⛅' : '☁️', 'Partly cloudy'];
  if (c === 3) return ['☁️', 'Overcast'];
  if (c <= 48) return ['🌫️', 'Fog'];
  if (c <= 57) return ['🌦️', 'Drizzle'];
  if (c <= 67) return ['🌧️', 'Rain'];
  if (c <= 77) return ['🌨️', 'Snow'];
  if (c <= 82) return ['🌧️', 'Showers'];
  if (c <= 86) return ['🌨️', 'Snow showers'];
  return ['⛈️', 'Storm'];
}

/* audio/today.mp3 is overwritten in place and some days TTS fails, so the
   file can be yesterday's. GitHub Pages stamps every file with the deploy time,
   so ask the GitHub API when that path last changed instead. */
function repoInfo() {
  const m = location.hostname.match(/^([^.]+)\.github\.io$/i);
  const repo = location.pathname.split('/').filter(Boolean)[0];
  return m && repo ? { owner: m[1], repo } : null;
}
async function audioFrom(ed) {
  const ri = repoInfo(); if (!ri) return null;
  const c = store.get('audiochk');
  if (c && c.for === ed.date && Date.now() - c.t < 20 * 60e3) return c.d;
  const r = await fetch(`https://api.github.com/repos/${ri.owner}/${ri.repo}/commits?path=${encodeURIComponent(ed.audio)}&per_page=1`);
  if (!r.ok) return null;
  const j = await r.json();
  const iso = j && j[0] && j[0].commit && j[0].commit.committer && j[0].commit.committer.date;
  const d = iso ? ymd(new Date(iso)) : null;
  store.set('audiochk', { t: Date.now(), for: ed.date, d });
  return d;
}

/* ---------------- rendering: shared ---------------- */
function badges(st) {
  let b = '';
  if (st.day > 1) b += `<span class="badge d2">Day ${st.day}</span>`;
  if (st.paywall) b += '<span class="badge">Paywall</span>';
  return b ? `<span class="badges">${b}</span>` : '';
}
function storyHTML(st, o = {}) {
  const read = S.read.has(st.id) ? ' is-read' : '';
  const lang = st.lang ? ` lang="${st.lang}"` : '';
  const label = o.showDate ? `${esc(st.source || st.section)} · ${fmt(st.date, { day: 'numeric', month: 'short' })}` : esc(st.source || st.section);
  const text = `<div class="src"><span class="dot"></span>${label}${badges(st)}</div>`
    + `<h3 class="hl">${o.mark ? o.mark(st.headline) : esc(st.headline)}</h3>`
    + (st.summary ? `<p class="sm">${esc(st.summary)}</p>` : '')
    + (st.matters && !o.noWhy ? `<p class="why">${esc(st.matters)}</p>` : '');
  const attrs = `data-id="${esc(st.id)}" style="--hue:${st.hue}"${lang} tabindex="0" role="button"`;
  const img = (cls) => `<img class="${cls}" src="${esc(st.image)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">`;
  if (o.lead && st.image) return `<article class="story lead${read}" ${attrs}>${img('hero')}<div class="in">${text}</div></article>`;
  if (st.image) return `<article class="story row${read}" ${attrs}><div>${text}</div>${img('th')}</article>`;
  return `<article class="story${read}" ${attrs}>${text}</article>`;
}
function secHead(sec, count) {
  return `<header class="sec-head"><span class="bar"></span>${sec.emoji ? `<span class="em" aria-hidden="true">${esc(sec.emoji)}</span>` : ''}<h2>${esc(sec.label)}</h2>${count != null ? `<span class="cnt">${count}</span>` : ''}</header>`;
}
const skeleton = () => '<div class="skel"><div class="sk a"></div><div class="sk b"></div><div class="sk c"></div><div class="sk d"></div><div class="sk e"></div></div>';

/* ---------------- Today ---------------- */
function renderToday(ed) {
  const v = $('#v-today');
  const isLatest = ed.date === S.latest;
  const news = ed.sections.filter((s) => s.role === 'news');
  const tldr = ed.sections.find((s) => s.role === 'tldr');
  const changed = ed.sections.find((s) => s.role === 'changed');
  const following = ed.sections.find((s) => s.role === 'following');
  const longread = ed.sections.find((s) => s.role === 'longread');
  const sports = ed.sections.filter((s) => s.role === 'sports');
  const total = readable(ed).length;
  const greet = (ed.greeting || '').split(/\s+—\s+/)[0] || 'Good morning';
  const wo = workoutOf(ed.workout);
  let h = '';

  if (!isLatest) h += `<div class="banner"><span>You're reading <b>${esc(fmtLong(ed.date))}</b></span><button data-act="latest">Latest edition</button></div>`;

  h += `<header class="mast"><div class="kicker"><span class="sun"></span>${esc(fmtLong(ed.date))} · ${CITY.name}</div>`
    + `<h1>${esc(ed.title)}</h1><p class="greet">${esc(greet)}${ed.minutes ? ` · ${ed.minutes} min read` : ''} · ${total} stories</p>`
    + `<div class="pills" id="pills">${ed.generated ? `<span class="pill">${icon('clock')}Updated <b>${esc(ed.generated.slice(11, 16))}</b></span>` : ''}</div></header>`;

  // workout + listen
  let tiles = '';
  if (wo) {
    tiles += `<div class="tile workout"><div class="ico">${icon(wo.rest ? 'moon' : 'dumbbell')}</div><div class="tx">`
      + `<div class="t1">${wo.rest ? 'Training' : (isLatest ? "Today's session" : 'Session')}</div><div class="t2">${esc(wo.title)}</div>`
      + (wo.detail ? `<div class="t3">${esc(wo.detail)}</div>` : '')
      + (wo.meta.length ? `<div class="meta">${wo.meta.map((m) => `<span class="tag${/nothing logged/i.test(m) ? ' warn' : ''}">${esc(m)}</span>`).join('')}</div>` : '')
      + '</div></div>';
  }
  if (isLatest && ed.audio) {
    tiles += `<div class="tile listen" id="listen"><button class="playbtn" data-act="play" aria-label="Play spoken briefing">${icon(isPlaying() ? 'pause' : 'play')}</button>`
      + `<div class="tx"><div class="t1">Listen</div><div class="t2">Spoken briefing</div><div class="t3" id="listenSub">Plays in the background, keeps your place</div></div></div>`;
  }
  if (tiles) h += `<div class="tiles">${tiles}</div>`;

  h += '<div class="markets" id="mkts"></div>';

  if (tldr && tldr.stories.length) {
    h += `<section class="card brief"><h2>The briefing</h2><ol>${tldr.stories.map((s) => `<li${s.lang ? ' lang="pl"' : ''}>${esc(s.headline)}</li>`).join('')}</ol></section>`;
  }
  const deltaList = (sec, title) => sec && sec.stories.length
    ? `<div class="card"><h2 class="h-label">${title}</h2><ul>${sec.stories.map((s) => `<li${s.lang ? ' lang="pl"' : ''}><b>${esc(s.headline)}</b>${s.summary ? ' — ' + esc(s.summary) : ''}</li>`).join('')}</ul></div>` : '';
  const delta = deltaList(changed, 'What changed') + deltaList(following, 'Following');
  if (delta) h += `<div class="delta">${delta}</div>`;

  if (news.length) {
    h += `<nav class="chips" id="chips" aria-label="Sections">${news.map((s) => `<a class="chip" href="#${s.key}" data-act="jump" data-key="${s.key}" style="--hue:${s.hue}"><span class="dot"></span>${esc(s.label)}<span class="n">${s.stories.length}</span></a>`).join('')}</nav>`;
  }
  h += '<div class="sections">';
  for (const sec of news) {
    const readN = sec.stories.filter((s) => S.read.has(s.id)).length;
    h += `<section class="sec" id="${sec.key}" style="--hue:${sec.hue}">${secHead(sec, sec.stories.length ? `${readN}/${sec.stories.length}` : null)}`
      + sec.status.map((t) => `<div class="status">${esc(t)}</div>`).join('')
      + (sec.stories.length ? `<div class="sec-body">${sec.stories.map((s, i) => storyHTML(s, { lead: i === 0 })).join('')}</div>` : '')
      + sec.notes.map((t) => `<div class="status">${esc(t)}</div>`).join('')
      + '</section>';
  }
  h += '</div>';

  if (sports.length) {
    const parts = sports.map((s) => {
      const n = s.board.groups.reduce((a, g) => a + (g.matches ? g.matches.length : g.lines.length), 0);
      return n ? `${s.label} ${n}` : null;
    }).filter(Boolean);
    const fav = sports.flatMap((s) => s.board.groups.flatMap((g) => (g.lines || []).filter((l) => /poland|polska/i.test(l))));
    h += `<a class="card teaser" href="#/sports${isLatest ? '' : '/' + ed.date}"><div class="ico">${icon('trophy')}</div><div class="tx"><div class="t2">Scores & fixtures</div><div class="t3">${esc(fav[0] ? 'Tonight: ' + fav[0] : parts.join(' · '))}</div></div>${icon('right', 'sm')}</a>`;
  }
  if (longread && longread.stories[0]) {
    const st = longread.stories[0];
    h += `<article class="longread" data-id="${esc(st.id)}" tabindex="0" role="button"><div class="k">${icon('book', 'xs')} Long read</div><div class="hl">${esc(st.headline)}</div>${st.summary ? `<p>${esc(st.summary)}</p>` : ''}${st.source ? `<div class="src">${esc(st.source)}</div>` : ''}</article>`;
  }
  h += `<footer class="foot">Written by the briefing pipeline${ed.generated ? ' at ' + esc(ed.generated.replace('T', ' ').slice(0, 16)) : ''}. Facts come from the linked sources at the time of the run. `
    + `<a href="../${ed.date}.html">Classic page</a> · <a href="../feed.xml">RSS</a></footer>`;

  v.innerHTML = h;
  paintMarkets();
  spyChips();
  if (isLatest) { paintWeather(); paintListen(ed); }
}

function spark(points) {
  if (points.length < 2) return '';
  const vs = points.map((p) => p.v), min = Math.min(...vs), max = Math.max(...vs), rng = max - min || 1;
  const xy = vs.map((v, i) => [(i / (vs.length - 1)) * 100, 3 + (1 - (v - min) / rng) * 20]);
  const line = xy.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const ch = vs[vs.length - 1] - vs[0];
  const col = ch > 0 ? 'var(--up)' : ch < 0 ? 'var(--down)' : 'var(--dim)';
  return `<svg viewBox="0 0 100 26" preserveAspectRatio="none" aria-hidden="true"><polygon class="area" fill="${col}" points="0,26 ${line} 100,26"/><polyline stroke="${col}" points="${line}"/></svg>`;
}
function paintMarkets() {
  const box = $('#mkts'); if (!box) return;
  const ed = S.editions.get(S.date); if (!ed || !ed.markets) { box.remove(); return; }
  const dates = [...S.editions.keys()].filter((d) => d <= ed.date).sort();
  box.innerHTML = ed.markets.map((m) => {
    const pts = dates.map((d) => { const x = (S.editions.get(d).markets || []).find((y) => y.name === m.name); return x ? { d, v: x.value } : null; }).filter(Boolean).slice(-14);
    const dir = m.pct == null ? 'flat' : m.pct > 0 ? 'up' : m.pct < 0 ? 'down' : 'flat';
    const span = pts.length >= 2 ? (pts[pts.length - 1].v - pts[0].v) / pts[0].v * 100 : null;
    return `<div class="mkt"><div class="n">${esc(m.name)}</div><div class="v">${esc(m.raw)}</div>`
      + `<div class="c ${dir}">${m.pct == null ? 'day —' : (m.pct > 0 ? '▲ ' : m.pct < 0 ? '▼ ' : '') + m.pctRaw + '%'}</div>`
      + spark(pts)
      + (span != null ? `<div class="d">${pts.length}d ${span >= 0 ? '+' : '−'}${Math.abs(span).toFixed(1)}%</div>` : '')
      + '</div>';
  }).join('');
}
async function paintWeather() {
  try {
    const w = await weather();
    const box = $('#pills'); if (!box || S.date !== S.latest) return;
    const [em, desc] = wmo(w.code, w.day);
    box.insertAdjacentHTML('afterbegin', `<span class="pill"><span aria-hidden="true">${em}</span><b>${w.t}°</b> ${desc} · ${w.hi}°/${w.lo}°${w.pop >= 30 ? ` · ☔ ${w.pop}%` : ''}</span>`);
  } catch (e) { /* offline: no weather pill */ }
}
async function paintListen(ed) {
  S.audio = { url: new URL(ed.audio, ROOT).href, staleFrom: null, date: ed.date, title: ed.title };
  try {
    const from = await audioFrom(ed);
    if (from && from < ed.date) {
      S.audio.staleFrom = from;
      const t = $('#listen'); if (!t) return;
      t.classList.add('stale');
      $('#listenSub').innerHTML = `<span class="tag warn">Not updated today: this is the ${esc(fmtShort(from))} audio</span>`;
    }
  } catch (e) { /* rate-limited or offline: assume fresh */ }
}

let spyObs = null;
function spyChips() {
  if (spyObs) spyObs.disconnect();
  const chips = $('#chips'); if (!chips || !('IntersectionObserver' in window)) return;
  spyObs = new IntersectionObserver((ents) => {
    for (const e of ents) {
      if (!e.isIntersecting) continue;
      $$('.chip', chips).forEach((c) => c.classList.toggle('on', c.dataset.key === e.target.id));
      const on = $('.chip.on', chips);
      if (on) chips.scrollTo({ left: on.offsetLeft - 16, behavior: 'smooth' });
    }
  }, { rootMargin: '-45% 0px -50% 0px' });
  $$('.sec').forEach((s) => spyObs.observe(s));
}

/* ---------------- Sports ---------------- */
function boardHTML(sec) {
  const b = sec.board; let h = '';
  for (const n of b.notes) h += `<div class="snote">${esc(n)}</div>`;
  for (const g of b.groups) {
    if (g.name && (b.groups.length > 1 || sec.kind === 'fixtures' || g.name !== sec.label)) h += `<div class="grp">${esc(g.name)}</div>`;
    if (g.matches) {
      for (const m of g.matches) {
        const w = m.hs > m.as ? 'l' : m.as > m.hs ? 'r' : '';
        const flag = m.flag ? `<span class="badge ${/ot/i.test(m.flag) ? 'ot' : /upset/i.test(m.flag) ? 'upset' : ''}">${esc(m.flag)}</span>` : '';
        h += `<div class="match${m.fav ? ' fav' : ''}"><span class="tm l${w === 'l' ? ' w' : ''}">${esc(m.home)}</span><span class="sc">${m.hs}<i>–</i>${m.as}</span><span class="tm${w === 'r' ? ' w' : ''}">${esc(m.away)}</span></div>`;
        h += (m.note || flag) ? `<div class="mnote">${flag}${esc(m.note || '')}</div>` : '<div class="mnote"></div>';
      }
    } else {
      for (const l of g.lines) {
        const fav = /poland|polska/i.test(l);
        h += `<div class="fx${fav ? ' fav' : ''}">${esc(l).replace(/\b(\d{1,2}:\d{2})\b/g, '<time>$1</time>')}</div>`;
      }
    }
  }
  return h;
}
function renderSports(ed) {
  const v = $('#v-sports');
  const secs = ed.sections.filter((s) => s.role === 'sports');
  let h = (ed.date !== S.latest ? `<div class="banner"><span>Scores from <b>${esc(fmtLong(ed.date))}</b></span><button data-act="latest-sports">Latest</button></div>` : '')
    + `<header class="page-head"><div class="kicker"><span class="sun"></span>${esc(fmtLong(ed.date))}</div><h1>Scores & fixtures</h1><p>Overnight results and what's on tonight, Warsaw time.</p></header>`;
  if (!secs.length) h += `<div class="empty">${icon('trophy')}No sport in this edition.</div>`;
  h += '<div class="board-cols">';
  for (const sec of secs) h += `<section class="sec" style="--hue:${sec.hue}">${secHead(sec)}<div class="sec-body board">${boardHTML(sec)}</div></section>`;
  v.innerHTML = h + '</div>';
}

/* ---------------- Saved ---------------- */
function renderSaved() {
  const v = $('#v-saved');
  let h = '<header class="page-head"><h1>Saved</h1><p>Stories you bookmarked. Stored on this device.</p></header>';
  if (!S.saved.length) h += `<div class="empty">${icon('bookmark')}Nothing saved yet.<br>Open a story and tap Save to keep it here.</div>`;
  else h += `<div class="sec-body list">${S.saved.map((s) => storyHTML(s, { showDate: true, noWhy: true })).join('')}</div>`;
  v.innerHTML = h;
}

/* ---------------- Archive + search ---------------- */
function renderArchive() {
  const v = $('#v-archive');
  v.innerHTML = '<header class="page-head"><h1>Archive</h1><p>Every edition, plus search across all of them.</p></header>'
    + `<label class="search">${icon('search')}<span class="sr">Search stories</span><input id="q" type="search" placeholder="Search all editions" autocomplete="off" value="${esc(S.query)}"></label>`
    + '<div id="results"></div>';
  paintSearch();
}
function paintSearch() {
  const box = $('#results'); if (!box) return;
  const q = S.query.trim();
  if (!q) {
    box.innerHTML = `<div class="sec-body list">${S.archive.map((a) => {
      const missing = S.missing.has(a.date);
      const href = missing ? `../${a.date}.html` : `#/today/${a.date}`;
      return `<a class="ed${a.date === S.date ? ' cur' : ''}" href="${href}"><div class="dt"><b>${fmt(a.date, { day: 'numeric' })}</b><span>${fmt(a.date, { month: 'short' })}</span></div>`
        + `<div><div class="tt">${esc(a.title || 'Edition')}</div><div class="mt">${esc(fmt(a.date, { weekday: 'long' }))}${a.date === S.latest ? ' · latest' : ''}${missing ? ' · classic page only' : ''}</div></div></a>`;
    }).join('')}</div>`;
    return;
  }
  loadHistory();
  const terms = q.toLowerCase().split(/\s+/).filter(Boolean);
  const hits = [];
  for (const d of [...S.editions.keys()].sort().reverse()) {
    for (const st of readable(S.editions.get(d))) {
      const hay = (st.headline + ' ' + (st.summary || '') + ' ' + (st.matters || '') + ' ' + (st.source || '') + ' ' + st.section).toLowerCase();
      if (terms.every((t) => hay.includes(t))) hits.push(st);
    }
  }
  const re = new RegExp('(' + terms.map((t) => esc(t).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|') + ')', 'gi');
  const mark = (s) => esc(s).replace(re, '<mark>$1</mark>');
  box.innerHTML = `<div class="list-head"><span class="h-label">${hits.length} ${hits.length === 1 ? 'story' : 'stories'}</span><span class="h-label">${S.editions.size} editions searched</span></div>`
    + (hits.length ? `<div class="sec-body list">${hits.slice(0, 80).map((s) => storyHTML(s, { showDate: true, noWhy: true, mark })).join('')}</div>` : `<div class="empty">${icon('search')}No stories match “${esc(q)}”.</div>`);
}

/* ---------------- story sheet ---------------- */
function markRead(st) {
  if (S.read.has(st.id)) return;
  S.read.add(st.id);
  store.set('read', [...S.read].slice(-3000));
  $$(`[data-id="${CSS.escape(st.id)}"]`).forEach((el) => el.classList.add('is-read'));
  updateProgress();
}
const isSaved = (id) => S.saved.some((s) => s.id === id);
function openStory(id) {
  const st = S.stories.get(id) || S.saved.find((s) => s.id === id);
  if (!st) return;
  markRead(st);
  const ex = excerptOf(st);
  const saved = isSaved(st.id);
  const canShare = st.url && navigator.share;
  showSheet(`<div style="--hue:${st.hue}"${st.lang ? ` lang="${st.lang}"` : ''}>`
    + (st.image ? `<img class="hero" src="${esc(st.image)}" alt="" referrerpolicy="no-referrer">` : '')
    + `<div class="src"><span class="dot"></span>${esc(st.section)}${st.source ? ' · ' + esc(st.source) : ''} · ${esc(fmt(st.date, { day: 'numeric', month: 'short' }))}${badges(st)}</div>`
    + `<h2>${esc(st.headline)}</h2>`
    + (st.summary ? `<p class="sm">${esc(st.summary)}</p>` : '')
    + (st.matters ? `<div class="why" lang="en"><b>Why it matters</b><span${st.lang ? ` lang="${st.lang}"` : ''}>${esc(st.matters)}</span></div>` : '')
    + (ex ? `<details><summary>From the source <span>auto-extracted</span></summary><p class="ex">${esc(ex)}</p></details>` : '')
    + '<div class="actions">'
    + (st.url ? `<a class="btn primary" href="${esc(st.url)}" target="_blank" rel="noopener">${icon('external')}Read on ${esc(st.source || 'source')}</a>` : '')
    + `<button class="btn${saved ? ' on' : ''}" data-act="save" data-sid="${esc(st.id)}">${icon(saved ? 'bookmarkOn' : 'bookmark')}${saved ? 'Saved' : 'Save'}</button>`
    + (canShare ? `<button class="btn" data-act="share" data-sid="${esc(st.id)}" aria-label="Share">${icon('share')}</button>` : '')
    + '</div></div>');
}
function toggleSave(id, btn) {
  const st = S.stories.get(id) || S.saved.find((s) => s.id === id);
  if (!st) return;
  if (isSaved(id)) S.saved = S.saved.filter((s) => s.id !== id);
  else S.saved.unshift({ ...st });
  store.set('saved', S.saved.slice(0, 300));
  const on = isSaved(id);
  if (btn) { btn.classList.toggle('on', on); btn.innerHTML = icon(on ? 'bookmarkOn' : 'bookmark') + (on ? 'Saved' : 'Save'); }
  if (S.tab === 'saved') renderSaved();
  snack(on ? 'Saved for later' : 'Removed from saved');
}

const sheet = () => $('#sheet');
function showSheet(html) {
  const sh = sheet(), sc = $('#scrim');
  $('#sheetBody').innerHTML = html;
  sh.hidden = false; sc.hidden = false; sh.scrollTop = 0; sh.style.transform = '';
  requestAnimationFrame(() => { sh.classList.add('on'); sc.classList.add('on'); });
  if (!S.sheetOpen) { history.pushState({ sheet: 1 }, ''); S.sheetOpen = true; }
  document.body.style.overflow = 'hidden';
}
function hideSheet(fromPop) {
  if (!S.sheetOpen) return;
  S.sheetOpen = false;
  const sh = sheet(), sc = $('#scrim');
  sh.classList.remove('on'); sc.classList.remove('on'); sh.style.transform = '';
  document.body.style.overflow = '';
  setTimeout(() => { if (!S.sheetOpen) { sh.hidden = true; sc.hidden = true; } }, 300);
  if (!fromPop) history.back();
}
function dragToClose() {
  const sh = sheet(); let y0 = null, dy = 0;
  sh.addEventListener('touchstart', (e) => { if (matchMedia('(min-width: 840px)').matches) return; if (sh.scrollTop <= 0) { y0 = e.touches[0].clientY; dy = 0; } }, { passive: true });
  sh.addEventListener('touchmove', (e) => {
    if (y0 == null) return;
    dy = e.touches[0].clientY - y0;
    if (dy > 0 && sh.scrollTop <= 0) { sh.style.transition = 'none'; sh.style.transform = `translateY(${dy}px)`; } else if (dy < 0) { y0 = null; sh.style.transform = ''; }
  }, { passive: true });
  sh.addEventListener('touchend', () => {
    if (y0 == null) return;
    sh.style.transition = '';
    if (dy > 110) hideSheet(); else sh.style.transform = '';
    y0 = null;
  });
}

/* ---------------- settings ---------------- */
function seg(name, cur, opts) {
  return `<div class="seg" role="group">${opts.map(([v, l]) => `<button data-act="set" data-k="${name}" data-v="${v}" aria-pressed="${cur === v}">${l}</button>`).join('')}</div>`;
}
function openSettings() {
  const theme = store.get('theme', 'system'), size = store.get('size', 'm'), dens = store.get('density', 'comfy');
  showSheet(`<div class="set"><h2>Settings</h2>`
    + `<div class="row"><div class="lbl">Theme</div><div class="hint">Follows your phone by default.</div>${seg('theme', theme, [['system', 'System'], ['light', 'Light'], ['dark', 'Dark']])}</div>`
    + `<div class="row"><div class="lbl">Text size</div><div class="hint">Applies to headlines and summaries.</div>${seg('size', size, [['s', 'Small'], ['m', 'Default'], ['l', 'Large']])}</div>`
    + `<div class="row"><div class="lbl">Layout</div><div class="hint">Compact hides summaries so you can scan headlines.</div>${seg('density', dens, [['comfy', 'Comfortable'], ['compact', 'Compact']])}</div>`
    + `<div class="row"><div class="lbl">New-edition alerts</div><div class="hint">Open the app from your home screen, tap Enable, then save the code it shows as push_subscription.json in the briefing folder on the PC (or hand it to Claude). The 08:50 check then pushes an alert when the edition is live.</div>`
    + `<button class="btn" data-act="push">${icon('bell')}Enable notifications</button><textarea id="subout" readonly placeholder="Your subscription code appears here" hidden></textarea></div>`
    + `<div class="row"><div class="lbl">More</div><div class="hint">Weather by Open-Meteo. Content written each morning by the briefing pipeline.</div><div class="links">`
    + `<a class="btn" href="../index.html">Classic page</a><a class="btn" href="../feed.xml">RSS</a><button class="btn" data-act="refresh">${icon('refresh')}Check for update</button></div></div>`
    + '</div>');
}
function applySettings() {
  const theme = store.get('theme', 'system'), size = store.get('size', 'm');
  const root = document.documentElement;
  if (theme === 'light' || theme === 'dark') root.dataset.theme = theme; else delete root.dataset.theme;
  if (size === 's' || size === 'l') root.dataset.size = size; else delete root.dataset.size;
  $('#main').classList.toggle('compact', store.get('density', 'comfy') === 'compact');
  const metas = $$('meta[name="theme-color"]');
  if (theme === 'system') { metas[0].content = '#f5f1e8'; metas[1].content = '#100f0d'; }
  else metas.forEach((m) => { m.content = theme === 'dark' ? '#100f0d' : '#f5f1e8'; });
}
async function enablePush(btn) {
  const out = $('#subout');
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) { btn.textContent = 'Not supported in this browser'; btn.disabled = true; return; }
  try {
    const b64 = (s) => { const p = '='.repeat((4 - (s.length % 4)) % 4); const r = atob((s + p).replace(/-/g, '+').replace(/_/g, '/')); return Uint8Array.from(r, (c) => c.charCodeAt(0)); };
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64(PUSH_KEY) });
    out.hidden = false; out.value = JSON.stringify(sub.toJSON()); out.select();
    try { await navigator.clipboard.writeText(out.value); btn.textContent = 'Copied: save it on the PC as push_subscription.json'; } catch (e) { btn.textContent = 'Copy the code below'; }
  } catch (e) { out.hidden = false; out.value = 'Error: ' + e.message; }
}

/* ---------------- audio player ---------------- */
const A = () => $('#audio');
const isPlaying = () => { const a = A(); return a && !a.paused && !a.ended && a.src; };
function playToggle() {
  const a = A(), info = S.audio; if (!info) return;
  if (a.src !== info.url) {
    a.src = info.url;
    const pos = store.get('pos:' + info.date, 0);
    a.addEventListener('loadedmetadata', () => { if (pos && pos < a.duration - 5) a.currentTime = pos; }, { once: true });
  }
  a.defaultPlaybackRate = a.playbackRate = S.speed;
  if (a.paused) a.play().catch(() => snack('Could not play audio')); else a.pause();
  $('#player').hidden = false; document.body.classList.add('has-player');
  $('#ptitle').textContent = info.title;
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({ title: info.title, artist: 'Daily Briefing', album: fmtLong(info.date), artwork: [{ src: new URL('app/icons/icon-512.png', ROOT).href, sizes: '512x512', type: 'image/png' }] });
    const ms = navigator.mediaSession;
    ms.setActionHandler('play', () => a.play());
    ms.setActionHandler('pause', () => a.pause());
    ms.setActionHandler('seekbackward', () => { a.currentTime = Math.max(0, a.currentTime - 15); });
    ms.setActionHandler('seekforward', () => { a.currentTime = Math.min(a.duration || 0, a.currentTime + 30); });
    try { ms.setActionHandler('seekto', (d) => { a.currentTime = d.seekTime; }); } catch (e) { /* older Chrome */ }
  }
}
function bindPlayer() {
  const a = A(); let lastSave = 0;
  const paint = () => {
    const playing = !a.paused;
    $('#pp').innerHTML = icon(playing ? 'pause' : 'play');
    $('#pp').setAttribute('aria-label', playing ? 'Pause' : 'Play');
    const lb = $('#listen .playbtn'); if (lb) lb.innerHTML = icon(playing ? 'pause' : 'play');
  };
  a.addEventListener('play', paint); a.addEventListener('pause', paint);
  a.addEventListener('timeupdate', () => {
    const d = a.duration || 0;
    $('#pseek').value = d ? Math.round((a.currentTime / d) * 1000) : 0;
    $('#ptime').textContent = `${mmss(a.currentTime)} / ${mmss(d)}`;
    if (S.audio && Date.now() - lastSave > 4000) { lastSave = Date.now(); store.set('pos:' + S.audio.date, a.currentTime); }
  });
  a.addEventListener('ended', () => { if (S.audio) store.set('pos:' + S.audio.date, 0); paint(); });
  $('#pseek').addEventListener('input', (e) => { if (a.duration) a.currentTime = (e.target.value / 1000) * a.duration; });
  $('#pp').addEventListener('click', playToggle);
  $('#pspeed').textContent = S.speed + '×';
  $('#pspeed').addEventListener('click', () => {
    S.speed = SPEEDS[(SPEEDS.indexOf(S.speed) + 1) % SPEEDS.length] || 1;
    a.defaultPlaybackRate = a.playbackRate = S.speed;
    store.set('speed', S.speed);
    $('#pspeed').textContent = S.speed + '×';
  });
  $('#pclose').addEventListener('click', () => { a.pause(); $('#player').hidden = true; document.body.classList.remove('has-player'); });
}

/* ---------------- chrome: nav, progress, snackbar ---------------- */
function updateProgress() {
  const bar = $('#progress'); const ed = S.editions.get(S.date);
  if (!ed || S.tab !== 'today') { bar.style.width = '0'; return; }
  const all = readable(ed); const n = all.filter((s) => S.read.has(s.id)).length;
  bar.style.width = all.length ? (n / all.length) * 100 + '%' : '0';
  $$('#v-today .sec').forEach((secEl) => {
    const sec = ed.sections.find((s) => s.key === secEl.id); const c = $('.cnt', secEl);
    if (sec && c && sec.stories.length) c.textContent = `${sec.stories.filter((s) => S.read.has(s.id)).length}/${sec.stories.length}`;
  });
}
function availableDates() { return S.dates.filter((d) => !S.missing.has(d)); }
function updateChrome() {
  const dates = availableDates(); const i = dates.indexOf(S.date);
  const dated = S.tab === 'today' || S.tab === 'sports';
  $('#daynav').style.visibility = dated ? 'visible' : 'hidden';
  $('#prevDay').disabled = i < 0 || i >= dates.length - 1;
  $('#nextDay').disabled = i <= 0;
  $('#dayLbl').textContent = S.date === S.latest && S.date === ymd(new Date()) ? 'Today' : (S.date ? fmt(S.date, { weekday: 'short', day: 'numeric', month: 'short' }) : '—');
  const suffix = S.date && S.date !== S.latest ? '/' + S.date : '';
  $$('#nav a').forEach((a) => {
    const t = a.dataset.tab;
    a.setAttribute('href', t === 'today' || t === 'sports' ? `#/${t}${suffix}` : `#/${t}`);
    if (t === S.tab) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
}
let snackT = 0;
function snack(text, action, fn) {
  const el = $('#snack'), b = $('#snackBtn');
  $('#snackText').textContent = text;
  b.hidden = !action; b.textContent = action || ''; b.onclick = () => { el.classList.remove('on'); fn && fn(); };
  el.classList.add('on');
  clearTimeout(snackT); snackT = setTimeout(() => el.classList.remove('on'), action ? 8000 : 2600);
}

/* ---------------- routing ---------------- */
function parseRoute() {
  const [tab, date] = location.hash.replace(/^#\/?/, '').split('/');
  return { tab: ['today', 'sports', 'saved', 'archive'].includes(tab) ? tab : 'today', date: /^\d{4}-\d{2}-\d{2}$/.test(date || '') ? date : null };
}
async function route() {
  const r = parseRoute();
  if (r.tab === 'today' || r.tab === 'sports') S.date = r.date || S.latest;
  else if (!S.date) S.date = S.latest;
  S.tab = r.tab;
  $$('.view').forEach((v) => { v.hidden = v.id !== 'v-' + S.tab; });
  updateChrome();
  const key = S.tab + ':' + (S.tab === 'today' || S.tab === 'sports' ? S.date : '');
  if (key !== S.rendered) window.scrollTo(0, 0);
  S.rendered = key;

  if (S.tab === 'today' || S.tab === 'sports') {
    const v = $('#v-' + S.tab), d = S.date;
    let ed = S.editions.get(d);
    if (!ed) {
      v.innerHTML = skeleton();
      try { ed = await loadEdition(d); } catch (e) {
        v.innerHTML = `<div class="err"><p>No app edition for ${esc(fmtLong(d))}.</p><a class="btn" href="../${d}.html">Open the classic page</a></div>`;
        updateChrome(); return;
      }
      if (S.date !== d || S.tab !== (v.id.slice(2))) return;
    }
    if (S.tab === 'today') renderToday(ed); else renderSports(ed);
  } else if (S.tab === 'saved') renderSaved();
  else renderArchive();
  updateChrome(); updateProgress();
}
function goDay(step) {
  const dates = availableDates(); const i = dates.indexOf(S.date); const d = dates[i + step];
  if (!d) return;
  location.hash = `#/${S.tab === 'sports' ? 'sports' : 'today'}${d === S.latest ? '' : '/' + d}`;
}

/* ---------------- new edition check ---------------- */
async function checkNew(manual) {
  S.lastCheck = Date.now();
  try {
    const raw = await getJSON('feed.json', true);
    const cur = S.editions.get(raw.date);
    if (raw.date > S.latest) {
      S.editions.set(raw.date, normalize(raw));
      const prev = S.latest; S.latest = raw.date;
      if (!S.dates.includes(raw.date)) S.dates.unshift(raw.date);
      if (!S.archive.some((a) => a.date === raw.date)) S.archive.unshift({ date: raw.date, title: clean(raw.title) });
      if (S.date === prev && S.tab === 'today') snack(`New edition · ${fmtShort(raw.date)}`, 'Read now', () => { location.hash = '#/today'; route(); });
      else updateChrome();
    } else if (cur && raw.generated && raw.generated !== cur.generated) {
      S.editions.set(raw.date, normalize(raw));
      if (S.date === raw.date && (S.tab === 'today' || S.tab === 'sports')) snack('This edition was updated', 'Refresh', () => route());
    } else if (manual) snack("You're up to date");
  } catch (e) { if (manual) snack("Offline: showing what's saved"); }
}

/* ---------------- events ---------------- */
function bind() {
  document.addEventListener('click', (e) => {
    const act = e.target.closest('[data-act]');
    if (act) {
      const a = act.dataset.act;
      if (a === 'jump') { e.preventDefault(); const el = document.getElementById(act.dataset.key); if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' }); return; }
      if (a === 'latest') { location.hash = '#/today'; return; }
      if (a === 'latest-sports') { location.hash = '#/sports'; return; }
      if (a === 'play') { playToggle(); return; }
      if (a === 'save') { toggleSave(act.dataset.sid, act); return; }
      if (a === 'share') { const st = S.stories.get(act.dataset.sid) || S.saved.find((s) => s.id === act.dataset.sid); if (st) navigator.share({ title: st.headline, url: st.url }).catch(() => {}); return; }
      if (a === 'set') {
        store.set(act.dataset.k, act.dataset.v);
        $$(`[data-k="${act.dataset.k}"]`).forEach((b) => b.setAttribute('aria-pressed', String(b === act)));
        applySettings(); return;
      }
      if (a === 'push') { enablePush(act); return; }
      if (a === 'refresh') { checkNew(true); return; }
    }
    const story = e.target.closest('[data-id]');
    if (story && !e.target.closest('a')) openStory(story.dataset.id);
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && S.sheetOpen) hideSheet();
    if (e.key === 'Enter' && e.target.matches && e.target.matches('[data-id]')) openStory(e.target.dataset.id);
  });
  document.addEventListener('error', (e) => { if (e.target.tagName === 'IMG') e.target.remove(); }, true);
  document.addEventListener('input', (e) => {
    if (e.target.id !== 'q') return;
    S.query = e.target.value;
    clearTimeout(bind.qt); bind.qt = setTimeout(paintSearch, 140);
  });
  $('#nav').addEventListener('click', (e) => {
    const a = e.target.closest('a'); if (!a || a.dataset.tab !== S.tab) return;
    e.preventDefault();
    if (S.tab === 'today' && S.date !== S.latest) location.hash = '#/today';
    else window.scrollTo({ top: 0, behavior: 'smooth' });
  });
  $('#scrim').addEventListener('click', () => hideSheet());
  $('#prevDay').addEventListener('click', () => goDay(1));
  $('#nextDay').addEventListener('click', () => goDay(-1));
  $('#openSettings').addEventListener('click', openSettings);
  window.addEventListener('hashchange', () => { if (S.sheetOpen) hideSheet(true); route(); });
  window.addEventListener('popstate', () => { if (S.sheetOpen) hideSheet(true); });
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && Date.now() - S.lastCheck > 60e3) checkNew(); });
  dragToClose();
  bindPlayer();
}
function paintStaticIcons() {
  $('#prevDay').innerHTML = icon('left', 'sm');
  $('#nextDay').innerHTML = icon('right', 'sm');
  $('#openSettings').innerHTML = icon('tune');
  $('#pp').innerHTML = icon('play');
  $('#pclose').innerHTML = icon('x', 'sm');
  $$('[data-icon]').forEach((el) => { el.innerHTML = icon(el.dataset.icon); });
}

/* ---------------- boot ---------------- */
async function init() {
  paintStaticIcons();
  applySettings();
  bind();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('../sw.js', { scope: '../' }).catch(() => {});
  $('#v-today').innerHTML = skeleton();

  const [raw, archive] = await Promise.all([getJSON('feed.json', true).catch(() => null), loadArchive()]);
  if (raw) { const ed = normalize(raw); S.editions.set(ed.date, ed); S.latest = ed.date; }
  S.archive = archive;
  if (raw && !S.archive.some((a) => a.date === S.latest)) S.archive.unshift({ date: S.latest, title: clean(raw.title) });
  S.archive.sort((a, b) => (a.date < b.date ? 1 : -1));
  S.dates = S.archive.map((a) => a.date);
  if (!S.latest) S.latest = S.dates[0];
  S.lastCheck = Date.now();
  if (!S.latest) {
    $('#v-today').innerHTML = `<div class="err"><p>Couldn't load the briefing. Check your connection.</p><button class="btn" onclick="location.reload()">${icon('refresh')}Try again</button></div>`;
    return;
  }
  await route();
  // older editions feed the market sparklines and archive search
  setTimeout(loadHistory, 800);
}
init();
