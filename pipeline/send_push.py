"""Send a Web Push notification to the stored phone subscription (PWA only).

Runs under the pipeline venv, where pywebpush lives:

    %USERPROFILE%\\.briefing\\venv\\Scripts\\python.exe send_push.py "Title" "Body"

To subscribe: open the web app's Settings, tap Enable notifications, and save the
code it shows as push_subscription.json in the briefing home. Silent when absent.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402


def main():
    title = sys.argv[1] if len(sys.argv) > 1 else "Daily Briefing"
    body = sys.argv[2] if len(sys.argv) > 2 else "A new edition is ready."
    if not os.path.exists(config.PUSH_SUB):
        print("no subscription stored - skipping push")
        return 0
    try:
        with open(config.PUSH_SUB, encoding="utf-8") as fh:
            sub = json.load(fh)
        from pywebpush import webpush
        webpush(subscription_info=sub,
                data=json.dumps({"title": title, "body": body, "url": config.APP_URL}),
                vapid_private_key=config.PUSH_KEY,
                vapid_claims={"sub": "mailto:briefing@kazimeirzodnowiciel.github.io"},
                timeout=20)
        print("push sent")
        return 0
    except Exception as exc:
        print("push failed:", type(exc).__name__, str(exc)[:200])
        return 1


if __name__ == "__main__":
    sys.exit(main())
