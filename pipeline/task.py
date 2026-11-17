"""Launcher used by Windows Task Scheduler: runs one pipeline script windowless, output to a log.

    pythonw.exe task.py run_daily.py [args...]

- Every child process (claude.exe, ffmpeg, edge-tts, gh) is started with CREATE_NO_WINDOW,
  so a 07:30 run never flashes console windows over whatever is on screen.
- stdout/stderr go to ~/.briefing/logs/<script>-<date>.log.
"""
import datetime
import os
import runpy
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import config  # noqa: E402

if os.name == "nt":
    _init = subprocess.Popen.__init__

    def _quiet(self, *args, **kwargs):
        kwargs["creationflags"] = kwargs.get("creationflags", 0) | 0x08000000  # CREATE_NO_WINDOW
        _init(self, *args, **kwargs)

    subprocess.Popen.__init__ = _quiet


def main():
    if len(sys.argv) < 2:
        return 2
    script = sys.argv[1]
    os.makedirs(config.LOG_DIR, exist_ok=True)
    name = os.path.splitext(os.path.basename(script))[0]
    log = open(os.path.join(config.LOG_DIR, f"{name}-{datetime.date.today().isoformat()}.log"),
               "a", encoding="utf-8", buffering=1)
    sys.stdout = sys.stderr = log
    print(f"--- {datetime.datetime.now():%Y-%m-%d %H:%M:%S} {' '.join(sys.argv[1:])}")
    # child scripts call sys.executable; under pythonw that is windowless as well
    sys.argv = [os.path.join(HERE, script)] + sys.argv[2:]
    try:
        runpy.run_path(sys.argv[0], run_name="__main__")
    except SystemExit as exc:
        print("exit", exc.code)
        return exc.code if isinstance(exc.code, int) else 0
    except Exception:
        import traceback
        traceback.print_exc()
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
