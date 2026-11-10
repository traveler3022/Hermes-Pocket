#!/usr/bin/env python3
"""Hermes Pocket end-to-end run on an emulator: python3 e2e/run.py --suites onboarding,chat --out out/

Each suite is a list of tests. A test passes, fails (an assertion about what the app showed), or
errors (the harness itself broke). Crashes, ANRs and native crashes seen in logcat while a test
ran are added to it and fail it. Everything lands in --out: report.md, results.json, logcat.txt,
screenshots (shots/) and screen dumps (dumps/).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import flows_chat  # noqa: E402
import flows_crawler  # noqa: E402
import flows_features  # noqa: E402
import flows_monkey  # noqa: E402
import flows_onboarding  # noqa: E402
import flows_probe  # noqa: E402
import flows_sweep  # noqa: E402
import flows_torture  # noqa: E402
import flows_upgrade  # noqa: E402
from harness import Run  # noqa: E402

SUITES = {
    "onboarding": flows_onboarding.ONBOARDING,
    "chat": flows_chat.CHAT,
    "torture": flows_torture.TORTURE,
    "sweep": flows_sweep.SWEEP,
    "features": flows_features.FEATURES,
    "upgrade": flows_upgrade.UPGRADE,
    "probe_context": flows_probe.PROBE_CONTEXT,
    "probe_changes": flows_probe.PROBE_CHANGES,
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--suites", default="onboarding")
    parser.add_argument("--out", default="e2e-out")
    parser.add_argument("--crawl-minutes", type=int, default=120)
    parser.add_argument("--monkey-minutes", type=int, default=120)
    parser.add_argument("--locale", default="")
    args = parser.parse_args()
    r = Run(args.out)
    try:
        for suite in args.suites.split(","):
            if suite == "crawl":
                tests = [("crawler: every screen, every button", lambda run: flows_crawler.crawl(run, args.crawl_minutes))]
            elif suite == "monkey":
                tests = [("monkey: random input", lambda run: flows_monkey.monkey(run, args.monkey_minutes))]
            elif suite == "persian":
                tests = flows_torture.PERSIAN
            else:
                tests = SUITES[suite]
            for name, fn in tests:
                ok = r.test(name, fn)
                if not ok and suite == "onboarding":
                    print("onboarding failed; the other suites need it", flush=True)
                    return 1
    finally:
        r.write()
        r.log.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
