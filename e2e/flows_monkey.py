"""Android's monkey on the app for as long as the job allows, a new seed each round."""
import os
import re
import time

import device as dev
from device import shell
from harness import Failure, Run


def monkey(r: Run, minutes):
    end = time.time() + minutes * 60
    seed, rounds, events = 1000, 0, 0
    found = []
    while time.time() < end:
        r.go_home()
        r.log.mark(f"monkey seed {seed}")
        out = shell(
            # Every weight given, summing to 100 (monkey refuses to start otherwise: "Event weights
            # != 100%"); rotation 0 because rotating killed the CI emulator.
            f"monkey -p {dev.PACKAGE} -s {seed} --throttle 200 --pct-touch 59 --pct-motion 25 "
            "--pct-pinchzoom 3 --pct-nav 5 --pct-majornav 6 --pct-appswitch 2 --pct-syskeys 0 "
            "--pct-anyevent 0 --pct-trackball 0 --pct-flip 0 --pct-rotation 0 --pct-permission 0 "
            "--kill-process-after-error -v 2500",
            timeout=1800)
        rounds += 1
        m = re.search(r"Events injected: (\d+)", out)
        events += int(m.group(1)) if m else 0
        if rounds == 1 and not m:
            raise Failure("monkey did not run at all:\n" + "\n".join(l for l in out.splitlines() if "arg" not in l)[-800:])
        for marker in ("// CRASH", "// NOT RESPONDING", "// NATIVE CRASH"):
            if marker in out:
                idx = out.index(marker)
                found.append({"seed": seed, "kind": marker[3:].lower(), "excerpt": out[idx:idx + 2500]})
                r.d.screenshot(f"monkey-{seed}")
        for p in r.log.take():
            found.append({"seed": seed, "kind": p["kind"], "excerpt": "\n".join(p["lines"][:30])})
        with open(os.path.join(r.out, "monkey.txt"), "a") as f:
            f.write(f"\n===== seed {seed}\n{out[-3000:]}\n")
        seed += 1
        if not r.d.pid():
            r.d.start_app()
            time.sleep(5)
    note = f"{rounds} rounds, {events} events, {len(found)} problems"
    if found:
        seen, lines = set(), []
        for f_ in found:
            key = f_["excerpt"].split("\n")[0][:120]
            if key in seen:
                continue
            seen.add(key)
            lines.append(f"[{f_['kind']}] seed {f_['seed']} (replay: monkey -s {f_['seed']} ...)\n  "
                         + "\n  ".join(f_["excerpt"].split("\n")[:20]))
        raise Failure(note + "\n" + "\n".join(lines[:12]))
    return note
