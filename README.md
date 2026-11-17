# Briefing pipeline

Writes, publishes and delivers the daily briefing at
<https://kazimeirzodnowiciel.github.io/briefings/>. Replaces the Hermes cron jobs
(the DeepSeek writer) with headless Claude Code driven by Windows Task Scheduler.
The deterministic parts are Hermes's scripts, ported.

## Schedule (`install_tasks.ps1`, Task Scheduler folder `\Briefing\`)

| Task | When | What |
|---|---|---|
| Morning briefing | daily 07:30 (wakes the PC) | `run_daily.py`: context → Claude writes → render → validate → publish → Telegram digest → audio |
| Audio fallback | daily 08:40 | `audio.py`: redoes TTS/publish/send only if the morning run didn't finish it |
| Canary | daily 08:50 | `canary.py`: Telegram warning if the live site isn't today's edition or has no audio; web push when all is well |
| Workout reminder | Tue/Wed/Fri/Sat 17:00 | `workout_send.py`: today's Hevy routine |
| Weekly review | Sun 09:00 | `run_weekly.py`: week in review from the last 7 editions |

Every task runs `pythonw task.py <script>`: no console windows. Output goes to `~/.briefing/logs/`.

## Files

| File | Role |
|---|---|
| `config.py` | Paths, repo, model (`BRIEFING_MODEL`, default `sonnet`), UTF-8 for every child process |
| `prompts/daily.md` | The writer prompt: Hermes's editorial rules, word for word, with date, context and previous-edition slots |
| `run_daily.py` | Orchestrator. `--dry-run` writes and renders without publishing; `--force` reruns a published day |
| `briefing_context.py` | Workout (Hevy), market tape (Yahoo, CoinGecko), Dalamud version, standing preferences |
| `render_briefing.py` | Markdown → dated page, index, archive, RSS, `feed.json`, `editions/<date>.json`, OG card |
| `feed_validate.py` | The `feed.json` contract the Android app depends on. A failure blocks the publish |
| `publish.py` | One commit via the GitHub API, **only the generated files**, never force. Waits until Pages serves it |
| `audio.py` | edge-tts → `audio/today.mp3` published immediately, plus the Telegram audio message |
| `tg.py` | Telegram through `D:\Deepseek\tools\telegram-send.py` with the token from the briefing home |
| `run_weekly.py`, `workout_send.py`, `canary.py`, `send_push.py` | As in the schedule |

## State: `~/.briefing` (override with `BRIEFING_HOME`)

`site/` (working copy of the generated files plus `_og` caches), `editions_md/` (each day's markdown, the
continuity source), `logs/`, `cache/`, `venv/` (edge-tts, pywebpush), `state.json`, and the secrets
`.github_token`, `.telegram_token` and `push_vapid_private.pem`. Copied from Hermes on 2026-11-17. The Hermes
folder was left untouched.

## Changing things

- **Sections or editorial rules:** edit `prompts/daily.md`, then `python run_daily.py --dry-run` and read
  `~/.briefing/editions_md/<today>.md`.
- **Reader preferences:** edit `## Standing preferences` in `~/.briefing/briefing_feedback.md`. They become the
  `PREF:` line.
- **Writer model:** set `BRIEFING_MODEL` (e.g. `opus`) on the task, or change the default in `config.py`.
- **Hand-written site files** (`app/`, `sw.js`, `site.webmanifest`) live in the briefings repo, and this pipeline
  never touches them.

## What changed vs Hermes

- The writer is Claude (headless `claude -p`, WebSearch and WebFetch only, no other tools). Publishing,
  validation, the digest and audio are code, not model instructions.
- Bad output is checked (date in the greeting, ≥8 sections, ≥25 linked stories, `matters:` clauses), with one
  retry that includes the reasons.
- Site audio is published right after TTS. Hermes never published it, so the site always played yesterday's audio.
- The publisher writes only the files it generated. Hermes mirrored a folder and would have reverted the hand-written `sw.js` and manifest.
- NBA lines whose team starts with a digit ("76ers") parse as scores, team names lose trailing full stops,
  and the OG card no longer overlaps the greeting or cuts the title mid-word.
- The canary checks the live site, not local file times.
