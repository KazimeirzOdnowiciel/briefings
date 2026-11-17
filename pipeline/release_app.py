"""Publish the signed Android APK as a GitHub release on the briefings repo.

    python release_app.py "release notes"

Reads the version from android/app/build.gradle.kts, checks the APK really is that
version and is signed (aapt2 + apksigner), then creates release `app-v<version>` with
the APK attached. The app's in-app updater checks /releases/latest on launch and offers
the new build, so this is how an update reaches the phone.

Build first:  cd android && gradle assembleRelease   (see android/README.md)
"""
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
import publish  # noqa: E402

ANDROID = os.path.join(os.path.dirname(config.CODE), "android")
APK = os.path.join(ANDROID, "app", "build", "outputs", "apk", "release", "app-release.apk")
BUILD_TOOLS = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "build-tools", "35.0.0")
JAVA_HOME = r"C:\Program Files\Android\openjdk\jdk-21.0.8"


def version():
    with open(os.path.join(ANDROID, "app", "build.gradle.kts"), encoding="utf-8") as fh:
        return re.search(r'versionName\s*=\s*"([^"]+)"', fh.read()).group(1)


def verify(want):
    badging = subprocess.run([os.path.join(BUILD_TOOLS, "aapt2.exe"), "dump", "badging", APK],
                             capture_output=True, text=True, timeout=60).stdout
    got = re.search(r"versionName='([^']+)'", badging)
    if not got or got.group(1) != want:
        raise SystemExit(f"APK is version {got and got.group(1)}, gradle says {want}: rebuild first")
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    signed = subprocess.run([os.path.join(BUILD_TOOLS, "apksigner.bat"), "verify", "--print-certs", APK],
                            capture_output=True, text=True, timeout=120, env=env, shell=False)
    if signed.returncode != 0 or "Signer #1" not in signed.stdout:
        raise SystemExit("APK is not signed with the release key:\n" + (signed.stderr or signed.stdout)[:400])
    print("verified:", want, "|", [l for l in signed.stdout.splitlines() if "DN:" in l][0].strip())


def api(tok, method, url, body=None, ctype="application/json"):
    data = json.dumps(body).encode() if ctype == "application/json" and body is not None else body
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={**publish.UA, "Authorization": f"Bearer {tok}", "Content-Type": ctype})
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(r.read().decode() or "{}")


def main():
    notes = sys.argv[1] if len(sys.argv) > 1 else ""
    ver = version()
    if not os.path.exists(APK):
        raise SystemExit("no APK at " + APK)
    verify(ver)
    tok = publish.token()
    rel = api(tok, "POST", f"{publish.API}/releases", {
        "tag_name": f"app-v{ver}", "target_commitish": config.BRANCH, "name": f"Briefing app {ver}",
        "body": notes, "draft": False, "prerelease": False})
    upload = rel["upload_url"].split("{")[0]
    with open(APK, "rb") as fh:
        asset = api(tok, "POST", f"{upload}?name=briefing-{ver}.apk", fh.read(),
                    ctype="application/vnd.android.package-archive")
    print("release:", rel["html_url"])
    print("apk:", asset.get("browser_download_url"), asset.get("size"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
