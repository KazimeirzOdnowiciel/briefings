# Briefings

A daily briefing published on GitHub Pages: <https://kazimeirzodnowiciel.github.io/briefings/>

The editions are written each morning by the **briefing pipeline** (`pipeline/`), which runs headless Claude Code
under Windows Task Scheduler. Until 17 Nov 2026, the Hermes agent (DeepSeek) wrote them.

## What's in this repo

| Folder | What | Docs |
|---|---|---|
| `pipeline/` | Writes, renders and publishes each edition; Telegram digest, workout reminder, weekly review | [pipeline/README.md](pipeline/README.md) |
| `android/` | Native Android app (Kotlin/Compose). APKs are on [Releases](https://github.com/KazimeirzOdnowiciel/briefings/releases/latest) | [android/README.md](android/README.md) |
| `app/` | Installable web app (PWA) served by GitHub Pages | below |
| repo root | The published site: dated pages, `feed.json`, `editions/`, archive, RSS | below |

**Get the Android app:** download `briefing-*.apk` from the
[latest release](https://github.com/KazimeirzOdnowiciel/briefings/releases/latest). After that it updates itself.

| | What it is | Who writes it |
|---|---|---|
| **App**: `app/` | Installable web app (PWA): Today, Sports, Saved and Archive, rendered from `editions/*.json` | Hand-written, in this repo |
| **Classic pages**: `index.html`, `YYYY-MM-DD.html` | Static HTML per edition, used for sharing links and RSS | The pipeline, every morning |
| **Data**: `feed.json`, `editions/`, `archive.html`, `feed.xml`, `og.png` | Read by the web app and by the native Android app | The pipeline |

The pipeline commits **only** the generated files above, through the GitHub API. It never touches `app/`,
`android/`, `pipeline/`, `sw.js`, `site.webmanifest` or this README, so hand edits there are safe.

## Web app features

- **Today**: lead headline, live Warsaw weather (Open-Meteo), workout card, market tiles with
  sparklines, the TL;DR, What changed and Following, sticky section chips, and story cards. A story opens a
  bottom sheet with its summary, why it matters, a source excerpt when a relevant one was extracted, plus Read, Save and Share.
- **Sports**: scoreboards and tonight's fixtures, with Poland highlighted. **Saved**: device-local bookmarks.
  **Archive**: every edition plus full-text search.
- Reading progress, new-edition checks, light and dark
  themes, text size, a compact layout, two columns on the Fold inner screen, and offline reading.

## Data contract

The web app and the native app read only these files, so keep their shape stable:

- `feed.json`: the latest edition. `editions/YYYY-MM-DD.json`: one per day, same schema (`schema: 1`):
  `{schema, generated, date, greeting, title, workout, audio, read_minutes, sections[]}`. Each section is
  `{name, kind: stories|scores|fixtures, items[]}`. An item is a story `{headline, summary, matters, url, source, image, body, day, paywall}`,
  a score `{group, home, away, home_score, away_score, note, flag}`, a fixture `{text}` or a note `{text}`.
- `archive.html`: the edition list (the web app reads `a[href$=".html"] .t2`).
- `audio` is always `null`: the spoken edition was dropped on 2026-11-17.

`feed_validate.py` in the pipeline enforces the contract before every publish.

## Known follow-ups

- **Repo growth**: `og.png` (~100 KB) is committed daily. Release assets or an
  orphan branch would stop that.
- **Structured fields**: market levels and weather are only text in the JSON. The web app parses the Money line.
- `manifest.json` (the 3 Nov edition index) is stale and unused.

## Local preview

```sh
python -m http.server 8080
# open http://localhost:8080/app/
```
