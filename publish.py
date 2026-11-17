"""Publish generated files to GitHub Pages as ONE commit via the Git Data API.

    python publish.py edition [YYYY-MM-DD]   dated page, index, archive, feeds, edition json, og card
    python publish.py audio                  audio/today.mp3
    python publish.py files <path> ...       explicit paths relative to the site working copy

Only the listed files are written. Everything else in the repo (app/, sw.js,
site.webmanifest, README) is hand-maintained and never touched, which is why
this does not mirror a whole folder the way the Hermes publisher did.

No `git push`: from a scheduled task, git can hand the credential refresh to a
GUI helper and hang. The API needs only a token (stored file, else `gh auth token`).
Never force-pushes: if main moved under us the commit is rebuilt on the new head.
"""
import base64
import datetime
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402

API = f"https://api.github.com/repos/{config.OWNER}/{config.REPO}"
UA = {"Accept": "application/vnd.github+json", "User-Agent": "daily-briefing",
      "X-GitHub-Api-Version": "2022-11-28"}


def token():
    try:
        with open(config.TOKEN_FILE, encoding="utf-8") as fh:
            tok = fh.read().strip()
        if tok:
            return tok
    except OSError:
        pass
    try:
        out = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, timeout=20).stdout.strip()
        if out:
            return out
    except Exception:
        pass
    raise SystemExit("no GitHub token: put one in %s or log in with `gh auth login`" % config.TOKEN_FILE)


def api(tok, method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method,
                                 headers={**UA, "Authorization": f"Bearer {tok}",
                                          **({"Content-Type": "application/json"} if data else {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        raw = r.read().decode()
        return json.loads(raw) if raw.strip() else {}


def blob_sha(data):
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def edition_files(date):
    return [f"{date}.html", "index.html", "archive.html", "feed.xml", "feed.json",
            f"editions/{date}.json", "og.png"]


def publish(paths, message):
    """Commit `paths` (relative to SITE_DIR). Returns the short sha, or None when nothing changed."""
    files = {}
    for rel in paths:
        full = os.path.join(config.SITE_DIR, rel)
        if os.path.exists(full):
            with open(full, "rb") as fh:
                files[rel] = fh.read()
        else:
            print("skip missing", rel)
    if not files:
        raise SystemExit("nothing to publish")

    tok = token()
    for attempt in range(3):
        head = api(tok, "GET", f"/git/ref/heads/{config.BRANCH}")["object"]["sha"]
        base_tree = api(tok, "GET", f"/git/commits/{head}")["tree"]["sha"]
        remote = {e["path"]: e["sha"] for e in api(tok, "GET", f"/git/trees/{base_tree}?recursive=1").get("tree", [])}
        changed = {p: b for p, b in files.items() if remote.get(p) != blob_sha(b)}
        if not changed:
            print("published: nothing changed")
            return None
        tree = []
        for rel, data in sorted(changed.items()):
            sha = api(tok, "POST", "/git/blobs", {"content": base64.b64encode(data).decode(), "encoding": "base64"})["sha"]
            tree.append({"path": rel, "mode": "100644", "type": "blob", "sha": sha})
        new_tree = api(tok, "POST", "/git/trees", {"base_tree": base_tree, "tree": tree})["sha"]
        commit = api(tok, "POST", "/git/commits", {"message": message, "tree": new_tree, "parents": [head]})["sha"]
        try:
            api(tok, "PATCH", f"/git/refs/heads/{config.BRANCH}", {"sha": commit, "force": False})
        except urllib.error.HTTPError as exc:
            if exc.code == 422 and attempt < 2:  # main moved meanwhile: rebuild on the new head
                print("main moved, retrying")
                time.sleep(2)
                continue
            raise
        print(f"published {len(changed)} file(s) in {commit[:7]}: {', '.join(sorted(changed))}")
        return commit[:7]
    return None


def wait_live(date, timeout=240):
    """Pages lags 30-90 s. Poll feed.json (cache-busted) until it carries `date`."""
    end = time.time() + timeout
    while time.time() < end:
        try:
            url = f"{config.SITE_URL}feed.json?cb={int(time.time() * 1000)}"
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
                if json.loads(r.read().decode()).get("date") == date:
                    return True
        except Exception:
            pass
        time.sleep(15)
    return False


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    cmd = argv[0]
    if cmd == "edition":
        date = argv[1] if len(argv) > 1 else datetime.date.today().isoformat()
        publish(edition_files(date), f"briefing: {date}")
    elif cmd == "audio":
        publish(["audio/today.mp3"], f"audio: {datetime.date.today().isoformat()}")
    elif cmd == "files":
        publish(argv[1:], "briefing: update")
    else:
        print(__doc__)
        return 2
    print("URL: " + config.SITE_URL)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
