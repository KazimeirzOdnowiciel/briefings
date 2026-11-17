# SOURCES — research for `sections.md`

Checked 2026-11-17 with a plain HTTP client (`urllib`-style, Chrome UA, no JS, no cookies) — the same shape as `briefing_context.py`.
Method: every URL below was fetched directly; status, body size, item count and newest timestamp recorded; for feeds, the newest item's article URL was fetched too, so "fetch: ok" means the article body came back as real text, not a shell.

Status legend: **ok** = real text on a plain GET · **js** = returns a JS shell / bot interstitial · **blocked** = 401/403 · **feed-only** = feed is fine but article pages are blocked.

Headline finding: three of the five World sources currently named in `sections.md` are hard-blocked to a plain client — **Reuters 401 (DataDome)**, **AP 403 (Cloudflare "Just a moment")**, and **PAP behind Incapsula** (its homepage, its RSS and `biznes.pap.pl` all fail). The prompt is telling the writers to start where they cannot read.

---

## 🌍 World

1. **BBC News — World RSS** — https://feeds.bbci.co.uk/news/world/rss.xml
   Why: fastest reliable wire of record, 21 items, clean XML, per-item descriptions.
   Freshness: top item ~11:00 today; article page returns 12.3k chars of text.
   Fetch: **ok**
2. **Al Jazeera — all.xml** — https://www.aljazeera.com/xml/rss/all.xml
   Why: best free live coverage of Middle East/Global South; 25 items with summaries.
   Freshness: top item same hour; article page 9.5k chars.
   Fetch: **ok**
3. **Deutsche Welle — world RDF** — https://rss.dw.com/rdf/rss-en-world
   Why: European/German angle, no paywall, stable RDF for 15 years.
   Freshness: updated hourly.
   Fetch: **ok**
4. **The Guardian — World RSS** — https://www.theguardian.com/world/rss
   Why: long, well-summarised feed (150 KB), good for "why it matters" clauses; free.
   Freshness: top item minutes old.
   Fetch: **ok**
5. **Euronews — news theme RSS** — https://www.euronews.com/rss?level=theme&name=news
   Why: EU-adjacent breaking news, very high cadence, plain XML.
   Freshness: top item 11:31 today.
   Fetch: **ok**
6. **France 24 — English RSS** — https://www.france24.com/en/rss
   Why: fast, good Africa/Levant coverage, 24 items with dek.
   Freshness: top item 11:04 today.
   Fetch: **feed-only** (article pages 403 — quote the feed summary, don't fetch the article)
7. **Wikipedia — Portal:Current events** — https://en.wikipedia.org/wiki/Portal:Current_events
   Why: best single page for "what actually happened in the last 24h", dated, sourced per bullet; catches stories the wires under-play.
   Freshness: edited continuously, day-by-day sections.
   Fetch: **ok** (41k chars of text)
8. **Google News RSS (fallback only)** — https://news.google.com/rss/search?q=world+news&hl=en-US&gl=US&ceid=US:en
   Why: when a story is clearly big but no feed above carries it, this proves existence and gives the outlet name in the title.
   Freshness: minutes.
   Fetch: **ok** (but the item links are Google redirects — use it to find the story, then cite the outlet's own URL)

## 🇵🇱 Polska

1. **TVN24 — najnowsze** — https://tvn24.pl/najnowsze.xml
   Why: fastest Polish newsroom, 24 items, full summaries; article pages carry 9.5k chars.
   Freshness: top item 11:04 today.
   Fetch: **ok**
2. **Onet Wiadomości** — https://wiadomosci.onet.pl/.feed
   Why: biggest Polish portal, 20 items, good national politics coverage.
   Freshness: 11:04 today; article 10.7k chars.
   Fetch: **ok**
3. **RMF24** — https://www.rmf24.pl/feed
   Why: 50 items per pull, radio wire speed, strong on breaking domestic.
   Freshness: 12:05 today.
   Fetch: **ok**
4. **Polsat News** — https://www.polsatnews.pl/rss/wszystkie.xml
   Why: 52 KB feed, all-sections, fills gaps TVN24/Onet miss.
   Freshness: 11:51 today.
   Fetch: **ok**
5. **Money.pl** — https://www.money.pl/rss/
   Why: the Polish economy/politics overlap (CPI, budget, zloty) that the general feeds bury.
   Freshness: 11:31 today; article 8k chars.
   Fetch: **ok**
6. **300Gospodarka** — https://300gospodarka.pl/feed
   Why: full-text feed (205 KB), best free Polish macro/energy explainers.
   Freshness: last night.
   Fetch: **ok**
7. **OKO.press** — https://oko.press/feed/
   Why: investigative, free, no paywall; catches stories the commercial portals skip.
   Freshness: 11:03 today.
   Fetch: **ok**
8. **Notes from Poland (EN)** — https://notesfrompoland.com/feed/
   Why: English summaries of the same Polish stories with dates and legal context — useful for checking a Polish-language claim before writing it.
   Freshness: 10:28 today.
   Fetch: **ok**

## 💰 Money

1. **Bankier.pl — wiadomości RSS** — https://www.bankier.pl/rss/wiadomosci.xml
   Why: the best free Polish markets/macro feed, 50 items; article pages give 17.8k chars including NBP, GPW and bond detail.
   Freshness: 11:30 today.
   Fetch: **ok**
2. **Money.pl — RSS** — https://www.money.pl/rss/
   Why: Polish consumer/macro angle, already used by Polska; keep for zloty, CPI, budget.
   Freshness: 11:31 today.
   Fetch: **ok**
3. **CNBC — top news RSS** — https://www.cnbc.com/id/100003114/device/rss/rss.html
   Why: fastest free US markets wire; replaces Reuters markets, which is 401.
   Freshness: 11:05 GMT today.
   Fetch: **ok**
4. **MarketWatch — top stories (Dow Jones feed)** — https://feeds.content.dowjones.io/public/rss/mw_topstories
   Why: the *feed* is open even though marketwatch.com itself is 401 — gives US market context and earnings.
   Freshness: 10:47 GMT today.
   Fetch: **ok (feed)** — do not fetch marketwatch.com article URLs
5. **ECB — press releases RSS** — https://www.ecb.europa.eu/rss/press.html
   Why: primary source for euro-area rates/decisions; no intermediary, no paywall.
   Freshness: Nov 16.
   Fetch: **ok**
6. **Federal Reserve — all press releases** — https://www.federalreserve.gov/feeds/press_all.xml
   Why: primary source for Fed/FOMC/supervision; plain XML.
   Freshness: current week.
   Fetch: **ok**
7. **Trading Economics** — https://tradingeconomics.com/
   Why: one page with the day's macro calendar and prints (CPI, PMI, payrolls) — lets the writer say what *moved* the tape.
   Freshness: live.
   Fetch: **ok** (HTML, 11k chars of text; calendar detail is partly gated)
8. **GUS** — https://stat.gov.pl/
   Why: Polish statistical primary source (CPI, GDP, wages) when a number needs confirming.
   Freshness: release-driven.
   Fetch: **ok**

## 💻 AI & Tech

1. **Ars Technica — index** — https://feeds.arstechnica.com/arstechnica/index
   Why: technically literate, fast, free; covers chips, security and policy without gadget filler.
   Freshness: 22:58 yesterday; article 9.5k chars.
   Fetch: **ok**
2. **The Register — headlines atom** — https://www.theregister.com/headlines.atom
   Why: 330 KB atom with long entries; best free source for enterprise/infra/outage and AI-business stories.
   Freshness: 10:15 today.
   Fetch: **ok**
3. **The Verge — index** — https://www.theverge.com/rss/index.xml
   Why: fast on platform news, model launches and outages; use for the consumer-facing frame.
   Freshness: 01:02 today.
   Fetch: **ok**
4. **NVIDIA Newsroom — releases** — https://nvidianews.nvidia.com/releases.xml
   Why: primary source for chips/accelerators; the only way to get GPU claims right.
   Freshness: 10:04 today.
   Fetch: **ok**
5. **OpenAI — news RSS** — https://openai.com/news/rss.xml
   Why: primary source for model releases and policy posts; 850 KB feed with full text.
   Freshness: today.
   Fetch: **ok**
6. **Google — AI blog RSS** — https://blog.google/technology/ai/rss/ (and https://deepmind.google/blog/rss.xml)
   Why: primary source for Gemini/DeepMind launches.
   Freshness: Nov 16 / Nov 12.
   Fetch: **ok**
7. **BleepingComputer** — https://www.bleepingcomputer.com/feed/
   Why: fastest free feed for exploited vulns, ransomware and breach disclosures — the "security" half of this section.
   Freshness: 11:05 today.
   Fetch: **ok**
8. **404 Media** — https://www.404media.co/rss/
   Why: original reporting on AI, platform moderation and outages that the majors pick up a day later.
   Freshness: Nov 16.
   Fetch: **ok**
   (Also solid: TechCrunch https://techcrunch.com/feed/, MIT Tech Review https://www.technologyreview.com/feed/, Wired https://www.wired.com/feed/rss — all **ok**, Wired partially metered.)

## 🎮 Games & Dev

1. **FFXIV Lodestone — News** — https://na.finalfantasyxiv.com/lodestone/news/
   Why: the authoritative list; item links are `/lodestone/news/detail/<sha>`, titles carry dates ("Companion App Updated (Nov. 12)"); detail pages return 6.7k chars.
   Freshness: quiet week — newest Nov 12 (no patch in the last 24h, which is itself the honest answer).
   Fetch: **ok**
2. **Lodestone — Updates category** — https://na.finalfantasyxiv.com/lodestone/news/category/3
   Why: this is where "FINAL FANTASY XIV Updated (Nov. 9)" lands — the build/hotfix changes a plugin dev cares about.
   Freshness: Nov 9–12.
   Fetch: **ok**
3. **Dalamud — release version API (kamori)** — https://kamori.goats.dev/Dalamud/Release/VersionInfo?track=release
   Why: the same endpoint XIVLauncher checks; returns `assemblyVersion`, `runtimeVersion`, `supportedGameVer`, `supportedGameVer` build date. Beats every news source for "did the API level move".
   Freshness: live (currently 15.0.4.0, .NET 10.0.14, game build 2026.11.03).
   Fetch: **ok** (JSON)
4. **Dalamud — commits atom** — https://github.com/goatcorp/Dalamud/commits/master.atom
   Why: "Update ClientStructs" / signature commits are the earliest visible signal of an API break, hours before release notes.
   Freshness: 2026-11-10.
   Fetch: **ok**
5. **Dalamud — releases (API + atom)** — https://api.github.com/repos/goatcorp/Dalamud/releases?per_page=5 and https://github.com/goatcorp/Dalamud/releases.atom
   Why: version + changelog text; atom needs no token, JSON is richer. (Note: tags are *not* stale any more — 15.0.4.0 is tagged 2026-11-10, contrary to the comment in `briefing_context.py`.)
   Freshness: 2026-11-10.
   Fetch: **ok**
6. **.NET Blog** — https://devblogs.microsoft.com/dotnet/feed/ (releases: https://github.com/dotnet/runtime/releases.atom)
   Why: the runtime/SDK notes a Dalamud plugin actually compiles against; atom feed is the fastest free signal of a servicing release.
   Freshness: blog Nov 10; runtime 9.0.23 on 2026-11-10.
   Fetch: **ok**
7. **Unity Blog** — https://unity.com/blog/rss
   Why: editor/engine releases and deprecations; 25 KB feed, dated items.
   Freshness: today.
   Fetch: **ok**
8. **Blender** — https://www.blender.org/feed/ (dev: https://developer.blender.org/)
   Why: release/news cadence for the Blender half of the section.
   Freshness: Nov 10.
   Fetch: **ok**
   (Also: GameDeveloper https://www.gamedeveloper.com/rss.xml — **ok**, Nov 16.)

## 🐉 World of Warcraft Forever

1. **Blizzard News — WoW hub** — https://news.blizzard.com/en-us/world-of-warcraft
   Why: the official firehose, and it is server-rendered: 2.3 MB of HTML with headlines, dates and article links inline (`/en-us/article/<id>/<slug>`). This is where "WoW: Forever Hotfixes - November 16, 2026" lives.
   Freshness: hotfix post dated Nov 16 (yesterday).
   Fetch: **ok**
2. **Blizzard — hotfix article pages** — e.g. https://news.blizzard.com/en-us/article/24315281/wow-forever-hotfixes-november-16-2026
   Why: complete hotfix text (32.5k chars: classes, dungeons, fishing, etc.) — exact class/spell names, no aggregation error.
   Freshness: Nov 16, with Nov 6 archived at the same pattern.
   Fetch: **ok**
3. **MMO-Champion — RSS** — https://www.mmo-champion.com/external.php?type=RSS2
   Why: the single best WoW firehose: 98 items, full post text (163k chars), includes blue-post roundups and datamining. The *site* is 403 but this endpoint is open.
   Freshness: 09:43 today.
   Fetch: **ok (feed)** — never fetch `mmo-champion.com/content/` article pages (403)
4. **Blizzard forums — Discourse JSON** — https://us.forums.blizzard.com/en/wow/latest.json
   Why: blue posts and dev replies, structured JSON with timestamps, no Cloudflare.
   Freshness: live.
   Fetch: **ok**
5. **worldofwarcraft.blizzard.com — News** — https://worldofwarcraft.blizzard.com/en-us/news
   Why: alternate official index; article pages under `/news/<id>/` render (12.9k chars).
   Freshness: today.
   Fetch: **ok** but the index is JS-hydrated (only one article link in the HTML) — prefer #1
6. **Warcraft Tavern — RSS** — https://www.warcrafttavern.com/feed/
   Why: thin but free, occasionally first on class tuning summaries.
   Freshness: 05:00 today.
   Fetch: **ok** (1 item only — fallback)

## 🎵 Music

1. **Pitchfork — news RSS** — https://pitchfork.com/feed/feed-news/rss
   Why: the release/tour/festival agenda-setter; 30 items.
   Freshness: 22:58 yesterday. Article body is thin (3.3k chars) — treat the feed summary as the source.
   Fetch: **ok (feed reads best)**
2. **Stereogum** — https://www.stereogum.com/feed/
   Why: faster than Pitchfork on releases and tour announcements, full summaries.
   Freshness: 22:05 yesterday.
   Fetch: **ok**
3. **Billboard** — https://www.billboard.com/feed/
   Why: charts (Hot 100, Billboard 200) and industry moves — the only free chart-of-record feed.
   Freshness: 09:10 today.
   Fetch: **ok**
4. **NME** — https://www.nme.com/feed
   Why: 156 KB feed, 70k chars of text; strong on tours, festivals and UK scene.
   Freshness: 10:48 today.
   Fetch: **ok**
5. **CGM.pl** — https://cgm.pl/feed/
   Why: the Polish music feed (rap/alternative) — the local angle Polish readers expect.
   Freshness: 08:28 today.
   Fetch: **ok**
6. **Muzyka Interia** — https://muzyka.interia.pl/feed
   Why: mainstream Polish music/pop coverage; complements CGM.
   Freshness: 10:21 today.
   Fetch: **ok**
7. **Music Business Worldwide** — https://www.musicbusinessworldwide.com/feed/
   Why: label/streaming/publishing deals — the "industry move" stories, free.
   Freshness: 08:01 today.
   Fetch: **ok**
8. **Consequence** — https://consequence.net/feed/ (metal: https://metalinjection.net/feed)
   Why: fills release/tour gaps and heavier genres.
   Freshness: 01:38 today.
   Fetch: **ok**

## 🎬 Culture

1. **Variety** — https://variety.com/feed/
   Why: the trade paper of record for releases, renewals, casting, box office; free feed.
   Freshness: 11:00 today; article 10.5k chars.
   Fetch: **ok**
2. **Deadline** — https://deadline.com/feed/
   Why: fastest on renewals/cancellations and streaming orders.
   Freshness: 11:05 today.
   Fetch: **ok**
3. **The Hollywood Reporter** — https://www.hollywoodreporter.com/feed/
   Why: awards-season and business coverage; free feed.
   Freshness: 10:30 today.
   Fetch: **ok**
4. **The Guardian — Film** — https://www.theguardian.com/film/rss
   Why: 124 KB feed with reviews and festival coverage; gives you a usable "worth watching" verdict.
   Freshness: 10:56 today.
   Fetch: **ok**
5. **IndieWire** — https://www.indiewire.com/feed/
   Why: festival/awards and trailer news with editorial judgement.
   Freshness: 06:58 today.
   Fetch: **ok**
6. **Collider** — https://collider.com/feed/ (trailers: same feed)
   Why: trailer/streaming release dates — the practical "what's out" data.
   Freshness: 11:00 GMT today.
   Fetch: **ok**
7. **Letterboxd Journal** — https://letterboxd.com/journal/rss/
   Why: for the one-item "actually worth watching" pick, with a real critical argument.
   Freshness: Nov 14 (not daily).
   Fetch: **ok**
8. **Filmweb** — https://www.filmweb.pl/
   Why: Polish release calendar and cinema dates (19.5k chars of text).
   Freshness: live.
   Fetch: **ok**

## 🏛️ Brussels

1. **European Commission — digital strategy RSS** — https://digital-strategy.ec.europa.eu/en/rss.xml
   Why: primary source for AI Act, DSA/DMA and data-rule milestones, with the Commission's own wording; 62 KB feed.
   Freshness: Nov 16 ("Commission hosts Apply AI Summit").
   Fetch: **ok**
2. **European Commission — press corner RSS** — https://ec.europa.eu/commission/presscorner/api/rss?language=en
   Why: every press release/IP with today's timestamps — the fastest official confirmation of an enforcement action.
   Freshness: 09:50 today.
   Fetch: **ok**
3. **EDPB — news** — https://www.edpb.europa.eu/news/news_en
   Why: the privacy/GDPR authority that decides what "data and privacy rules" mean in practice.
   Freshness: current.
   Fetch: **ok**
4. **EU Artificial Intelligence Act tracker** — https://artificialintelligenceact.eu/feed/
   Why: the best free running commentary on AI Act deadlines, codes of practice and implementation dates, aimed at builders.
   Freshness: Nov 13.
   Fetch: **ok**
5. **EURACTIV** — https://www.euractiv.com/feed/
   Why: EU policy news at wire speed, free; covers trilogue/committee stage detail the Commission won't.
   Freshness: 10:00 today.
   Fetch: **ok**
6. **POLITICO Europe** — https://www.politico.eu/feed/
   Why: 200 KB feed, strongest on DMA/DSA politics and who is lobbying whom.
   Freshness: 10:48 today.
   Fetch: **ok** (some items are Pro-only — do not cite those)
7. **European Commission — AI Act page** — https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai
   Why: canonical text/dates for obligations and prohibitions; use to avoid paraphrasing the Act wrongly.
   Freshness: updated per milestone.
   Fetch: **ok**

## 📅 Tonight

1. **ESPN scoreboard API — UEFA Nations League** — https://site.api.espn.com/apis/site/v2/sports/soccer/uefa.nations/scoreboard
   Why: today's fixtures in JSON with ISO timestamps (currently 8 matches, all 14:45 EST = 20:45 Warsaw). Convert once, no scraping.
   Freshness: live.
   Fetch: **ok**
2. **ESPN scoreboard API — other international competitions** — same path with `fifa.worldq.uefa`, `fifa.worldq.conmebol`, `uefa.euroq`, `fifa.friendly`
   Why: covers the international break completely (verified: WCQ-UEFA playoffs "FT-Pens", CONMEBOL "FT", 18 friendlies).
   Freshness: live.
   Fetch: **ok**
3. **ESPN scoreboard API — NBA, by date** — https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=20261117
   Why: exactly tonight's slate (5 games verified) with US tip times; add 6h for Warsaw (19:30 EST → 01:30).
   Freshness: live.
   Fetch: **ok**
4. **Ekstraklasa — terminarz** — https://www.ekstraklasa.org/terminarz
   Why: official Polish fixture list with round, date and kickoff ("15. kolejka, 20.11 18:00 Piast – Korona"), in Polish, ready to quote.
   Freshness: current round (15, 20–23.11).
   Fetch: **ok**
5. **ESPN scoreboard API — the five leagues** — `eng.1`, `esp.1`, `ita.1`, `ger.1`, `fra.1`
   Why: when the leagues are playing, this is the same one-call-per-league fixture list with kickoff timestamps.
   Freshness: live (currently returns next round, 21–23.11).
   Fetch: **ok**
6. **90minut.pl (over http, not https)** — http://www.90minut.pl/
   Why: Polish fixture/results detail down to lower divisions; `https://` fails the TLS handshake, `http://` works.
   Freshness: live.
   Fetch: **ok (http only)**

## 🏀 NBA

1. **ESPN scoreboard — NBA (last night)** — https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard
   Why: the full slate in one call — 8 games verified, each with `status.type.detail = "Final"`, team scores and an event id. This is how you guarantee "every game, none dropped".
   Freshness: last night's games, one call.
   Fetch: **ok**
2. **ESPN game summary — box score** — https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=401909994
   Why: 466 KB JSON with `boxscore.players[].statistics[].labels = [MIN,PTS,FG,3PT,FT,REB,AST,TO,STL,BLK,OREB,DREB]` and per-player `stats` arrays, grouped by team. A top performer is computable exactly, and cannot be attributed to the wrong team.
   Freshness: per game, final.
   Fetch: **ok**
3. **ESPN `leaders` block (same payload)** — inside the summary above
   Why: pre-computed "Points / Assists" leaders per team (e.g. "Jayson Tatum 24") — a cheap cross-check before writing the line.
   Freshness: per game.
   Fetch: **ok**
4. **ESPN scoreboard — `?dates=YYYYMMDD`** — same URL with a date
   Why: needed for the Tonight section and for a late West Coast game that finishes after the 00:00Z boundary.
   Freshness: per date.
   Fetch: **ok**

## ⚽ Football

1. **ESPN scoreboards — PL / La Liga / Serie A / Bundesliga / Ligue 1** — https://site.api.espn.com/apis/site/v2/sports/soccer/{eng.1|esp.1|ita.1|ger.1|fra.1}/scoreboard
   Why: one call per league returns every fixture with score, status ("FT", "FT-Pens") and an event id; add `?dates=YYYYMMDD` for a specific matchday (verified: `ita.1?dates=20261108` → 5 FT results including "Lazio at Napoli").
   Freshness: live.
   Fetch: **ok**
2. **ESPN match summary — scorers** — https://site.api.espn.com/apis/site/v2/sports/soccer/ita.1/summary?event=401874810
   Why: `keyEvents` carries the minute and the scorer in plain text: "Goal! Napoli 0, Lazio 1. Davide Frattesi (Lazio) left footed shot…" and "Own Goal by Alfonso Pedraza, Lazio." This is the scorer source the section needs — no round-up required.
   Freshness: immediately post-match.
   Fetch: **ok**
3. **ESPN international competitions** — `uefa.nations`, `fifa.worldq.uefa`, `fifa.worldq.conmebol`, `uefa.euroq`, `fifa.friendly`
   Why: during the break this *is* the Football section; verified FT/FT-Pens statuses and the same summary/scorer endpoint.
   Freshness: live.
   Fetch: **ok**
4. **Ekstraklasa — terminarz** — https://www.ekstraklasa.org/terminarz
   Why: the official Polish source for the one league ESPN does not cover (Ekstraklasa returns HTTP 400 on ESPN).
   Freshness: current round.
   Fetch: **ok**
5. **90minut.pl** — http://www.90minut.pl/ (Szczegóły meczu pages per match)
   Why: Polish-language scorers, cards and lineups for Ekstraklasa when the official site's match centre is thin.
   Freshness: post-match.
   Fetch: **ok (http only)**
6. **Transfermarkt — Ekstraklasa fixtures** — https://www.transfermarkt.com/ekstraklasa/gesamtspielplan/wettbewerb/PL1
   Why: fallback full fixture/results table with matchday grouping (750 KB, heavy but parseable).
   Freshness: current.
   Fetch: **ok** (slow, big — last resort)

## 📌 Long read

1. **The Guardian — The Long Read RSS** — https://www.theguardian.com/news/series/the-long-read/rss
   Why: the most consistent weekly 8–12 minute piece, free, full text in the feed (74 KB).
   Freshness: 11:07 today.
   Fetch: **ok**
2. **Longreads** — https://longreads.com/feed/
   Why: curated aggregator across magazines — best hit-rate for "one thing worth 10 minutes" on any given day.
   Freshness: 11:00 today.
   Fetch: **ok**
3. **Aeon** — https://aeon.co/feed.rss
   Why: essays with an argument, not news; ideal when the day is heavy and the reader needs distance.
   Freshness: 11:00 today.
   Fetch: **ok**
4. **ProPublica** — https://www.propublica.org/feeds/propublica/main
   Why: investigative long-form, free, no paywall — strongest "substance" per word.
   Freshness: 11:00 today.
   Fetch: **ok**
5. **Nautilus** — https://nautil.us/feed/
   Why: science/long-form, shorter than Aeon, good for variety.
   Freshness: 00:30 today.
   Fetch: **ok**
6. **Noema** — https://www.noemamag.com/feed/
   Why: tech/philosophy essays at book-chapter length; fits the AI-heavy reader.
   Freshness: Nov 13.
   Fetch: **ok**

---

## Avoid (verified failures)

| Source | Failure |
|---|---|
| reuters.com (any section) | **401** DataDome — "Please enable JS and disable any ad blocker" |
| apnews.com (hub pages, articles) | **403** Cloudflare "Just a moment" |
| pap.pl — homepage, `/rss.xml`, `/aktualnosci`, biznes.pap.pl | Incapsula incident page; RSS returns a 942-byte "Request unsuccessful" stub |
| bloomberg.com | **403** "Are you a robot?" |
| ft.com | **403** "Security Verification" |
| wsj.com | **401** DataDome |
| marketwatch.com (site) | **401** — but the Dow Jones RSS feed works (use that) |
| wyborcza.pl | loads, but body is a subscription wall ("Wybierz prenumeratę") |
| rp.pl | RSS works but article bodies are paywalled/short — cite other outlets |
| tvp.info | 22 KB page, 108 chars of text — JS shell; `/rss` 404 |
| gazeta.pl | `/wiadomosci/rss.xml` 404 |
| stooq.pl | "This site requires JavaScript to verify your browser" |
| nbp.pl | "Pardon Our Interruption" bot wall |
| investing.com | **403** Cloudflare |
| wowhead.com (news, `/news/rss`) | **403** CloudFront "Request blocked"; RSS 404 |
| icy-veins.com, warcraftlogs.com, wowesports.com, mmo-champion.com HTML pages | **403** Cloudflare (MMO-C's RSS endpoint is the exception) |
| raider.io | JS-only ("makes extensive use of JavaScript") |
| reddit.com (r/ffxiv, r/wow JSON) | **403** "blocked by network security" |
| espn.com HTML pages (`/soccer/scoreboard`) | **202** "we need to verify that you're not a robot" — the `site.api.espn.com` JSON is unaffected |
| uefa.com fixtures pages | connection timeout from a plain client |
| fotmob.com API | returns 0 bytes without a generated `x-mas` header — not usable by a plain fetcher |
| worldfootball.net | **403** Cloudflare |
| soccerway / flashscore / livescore | JS-only scoreboards |
| ESPN `soccer/pol.1` | HTTP **400** "Failed to get events endpoint" — Ekstraklasa is not in ESPN |
| 90minut.pl over **https** | TLS handshake failure — must use `http://` |
| worksinprogress.co/feed/ | 404 (feed moved) |
| france24.com article pages | **403** despite a healthy RSS feed |
| pitchfork.com article bodies | only ~3.3k chars of text — use the feed summary |

---

## Suggested prompt edits

One line per section, written to drop into `sections.md` in place of the current source sentence.

- **🌍 World** — "Start with BBC World RSS (feeds.bbci.co.uk/news/world/rss.xml), Al Jazeera (aljazeera.com/xml/rss/all.xml), DW (rss.dw.com/rdf/rss-en-world), Guardian World (theguardian.com/world/rss) and Euronews (euronews.com/rss?level=theme&name=news); Reuters, AP and Bloomberg are blocked to a plain fetch, and France 24 works only through its RSS (article pages 403)."
- **🇵🇱 Polska** — "Sources: TVN24 (tvn24.pl/najnowsze.xml), Onet (wiadomosci.onet.pl/.feed), RMF24 (rmf24.pl/feed), Polsat News (polsatnews.pl/rss/wszystkie.xml), plus Money.pl and OKO.press for politics/economy; PAP is behind Incapsula and unusable — use Google News PL RSS (news.google.com/rss?hl=pl&gl=PL&ceid=PL:pl) to catch PAP-sourced stories, then cite the outlet that carries them. Keep Polish links as they are."
- **💰 Money** — "Sources: Bankier (bankier.pl/rss/wiadomosci.xml), Money.pl, CNBC top-news RSS, the MarketWatch feed (feeds.content.dowjones.io/public/rss/mw_topstories — the site itself is 401), plus ECB (ecb.europa.eu/rss/press.html) and Fed (federalreserve.gov/feeds/press_all.xml) for the primary decisions; Bloomberg, FT and WSJ are blocked, stooq and NBP are JS/bot-walled."
- **💻 AI & Tech** — "Sources: Ars Technica (feeds.arstechnica.com/arstechnica/index), The Register (theregister.com/headlines.atom), The Verge, TechCrunch, NVIDIA Newsroom (nvidianews.nvidia.com/releases.xml) and OpenAI (openai.com/news/rss.xml) for primary announcements, plus BleepingComputer and 404 Media for security and outages."
- **🎮 Games & Dev** — "Sources: FFXIV Lodestone news (na.finalfantasyxiv.com/lodestone/news/ and the Updates category), the Dalamud version API (kamori.goats.dev/Dalamud/Release/VersionInfo?track=release), Dalamud commits and releases on GitHub, the .NET blog/feed, Unity blog RSS and blender.org/feed/."
- **🐉 World of Warcraft Forever** — "Sources: the official hub at news.blizzard.com/en-us/world-of-warcraft, then the hotfix article itself (/en-us/article/<id>/<slug>), MMO-Champion's RSS (mmo-champion.com/external.php?type=RSS2) and the Blizzard forums JSON (us.forums.blizzard.com/en/wow/latest.json); Wowhead, Icy Veins and Warcraft Logs all return 403, and MMO-Champion's article pages are 403 too — read that site through its RSS only."
- **🎵 Music** — "Sources: Pitchfork news RSS, Stereogum, Billboard, NME, Music Business Worldwide for label/streaming moves, and CGM.pl plus Muzyka Interia for the Polish angle."
- **🎬 Culture** — "Sources: Variety, Deadline, The Hollywood Reporter, IndieWire, Guardian Film RSS and Collider; use Letterboxd Journal or a Guardian review for the one thing worth watching, and Filmweb for Polish release dates."
- **🏛️ Brussels** — "Sources: the Commission's digital-strategy RSS (digital-strategy.ec.europa.eu/en/rss.xml) and press corner (ec.europa.eu/commission/presscorner/api/rss?language=en) first, then EDPB for privacy, the AI Act tracker (artificialintelligenceact.eu/feed/), EURACTIV and POLITICO Europe; never cite POLITICO Pro-only items."
- **📅 Tonight** — "Get fixtures from the ESPN scoreboard JSON — uefa.nations, fifa.worldq.uefa, fifa.worldq.conmebol, uefa.euroq, fifa.friendly for football and basketball/nba/scoreboard?dates=YYYYMMDD for tip-offs — plus ekstraklasa.org/terminarz for Ekstraklasa and 90minut.pl over http; convert every kickoff to Europe/Warsaw."
- **🏀 NBA** — "Get the full slate from site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard, then each game's box score from .../nba/summary?event=<id> (labels: MIN, PTS, FG, 3PT, FT, REB, AST, TO, STL, BLK); take the performer from that game's own team block, never from a round-up."
- **⚽ Football** — "Get scores from the ESPN scoreboard JSON for eng.1, esp.1, ita.1, ger.1, fra.1 (and uefa.nations/fifa.worldq.* in a break), and the scorers from that match's summary `keyEvents` ('Goal! Napoli 0, Lazio 1. Davide Frattesi (Lazio)…'); Ekstraklasa is not in ESPN — use ekstraklasa.org/terminarz and 90minut.pl over http."
- **📌 Long read** — "Sources: The Guardian Long Read RSS, Longreads, Aeon, ProPublica, Nautilus and Noema — all free and full-text; skip The Atlantic and The New Yorker, which are paywalled."
