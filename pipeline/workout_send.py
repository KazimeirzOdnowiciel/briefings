"""17:00 workout reminder on training days: today's Hevy routine, set by set, to Telegram."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tg  # noqa: E402
from workout_msg import full_message  # noqa: E402


def main():
    message = full_message()
    if not message.strip():
        return 0
    ok = tg.send_text(message)
    print(f"workout reminder sent={ok}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
