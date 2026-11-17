# Briefings

A daily briefing published on GitHub Pages: <https://kazimeirzodnowiciel.github.io/briefings/>

The editions are written each morning by the **briefing pipeline**, a separate local project at
`C:\Users\lukat\Projects\briefing-pipeline`. It runs headless Claude Code under Windows Task Scheduler.
Until 17 Nov 2026, the Hermes agent (DeepSeek) wrote them.

| | What it is | Who writes it |
|---|---|---|
| **App**: `app/` | Installable web app (PWA): Today, Sports, Saved and Archive, rendered from `editions/*.json` | Hand-written, in this repo |
| **Classic pages**: `index.html`, `YYYY-MM-DD.html` | Static HTML per edition, used for sharing links and RSS | The pipeline, every morning |
| **Data**: `feed.json`, `editions/`, `archive.html`, `feed.xml`, `og.png`, `audio/today.mp3` | Read by the web app and by the native Android app | The pipeline |

The pipeline commits **only** the generated files above, through the GitHub API. It never touches `app/`,
`sw.js`, `site.webmanifest` or this README, so hand edits here are safe. The native Android app
(`D:\Deepseek\briefing-app`) is released as `app-v*` GitHub releases on this repo and reads `feed.json`.

## Web app features

- **Today**: lead headline, live Warsaw weather (Open-Meteo), workout card, spoken briefing, market tiles with
  sparklines, the TL;DR, What changed and Following, sticky section chips, and story cards. A story opens a
  bottom sheet with its summary, why it matters, a source excerpt when a relevant one was extracted, plus Read, Save and Share.
- **Sports**: scoreboards and tonight's fixtures, with Poland highlighted. **Saved**: device-local bookmarks.
  **Archive**: every edition plus full-text search.
- Reading progress, an audio player with lock-screen controls and speed, new-edition checks, light and dark
  themes, text size, a compact layout, two columns on the Fold inner screen, and offline reading.

## Data contract

The web app and the native app read only these files, so keep their shape stable:

- `feed.json`: the latest edition. `editions/YYYY-MM-DD.json`: one per day, same schema (`schema: 1`):
  `{schema, generated, date, greeting, title, workout, audio, read_minutes, sections[]}`. Each section is
  `{name, kind: stories|scores|fixtures, items[]}`. An item is a story `{headline, summary, matters, url, source, image, body, day, paywall}`,
  a score `{group, home, away, home_score, away_score, note, flag}`, a fixture `{text}` or a note `{text}`.
- `archive.html`: the edition list (the web app reads `a[href$=".html"] .t2`).
- `audio/today.mp3`: published right after TTS each morning.

`feed_validate.py` in the pipeline enforces the contract before every publish.

## Known follow-ups

- **Repo growth**: `audio/today.mp3` (~800 KB) and `og.png` (~100 KB) are committed daily. Release assets or an
  orphan branch would stop that.
- **Structured fields**: market levels and weather are only text in the JSON. The web app parses the Money line.
- `manifest.json` (the 3 Nov edition index) is stale and unused.

## Local preview

```sh
python -m http.server 8080
# open http://localhost:8080/app/
```
