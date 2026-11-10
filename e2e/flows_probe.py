"""Which item of the chat's More menu takes the emulator down (crawler runs died right after
opening it, every time)? Each probe opens one item on its own emulator and records the app's
memory and the screen every few seconds, so the last record shows what was happening."""
import os
import time

import device as dev
from device import shell
from flows_chat import new_chat
from harness import L, Run, check


def probe(r: Run, key):
    new_chat(r)
    r.send_chat("probe one", expect="MOCK-REPLY: probe one")
    r.send_chat("#terminal echo probe > /root/probe.txt", expect=None)
    r.wait_idle(timeout=120)
    more = r.d.wait_for(timeout=10, anyof=L("more"))
    check(more is not None, "no More button on the chat screen")
    r.d.tap(more)
    item = r.d.wait_for(timeout=10, anyof=L(key))
    check(item is not None, f"no '{key}' in the More menu")
    log = open(os.path.join(r.out, f"probe-{key}.txt"), "a")
    r.d.tap(item)
    for i in range(24):
        mem = shell(f"dumpsys meminfo {dev.PACKAGE} | grep -E 'TOTAL PSS|Graphics|GL mtrack|EGL mtrack|Views:'", timeout=30)
        alive = r.emulator_alive()
        log.write(f"--- {i * 5}s alive={alive}\n{mem}\n")
        log.flush()
        if not alive:
            break
        if i % 4 == 0:
            r.d.screenshot(f"probe-{key}-{i * 5}s")
        time.sleep(5)
    check(r.emulator_alive(), f"the emulator died after opening '{key}'")
    r.d.save_dump(f"probe-{key}")
    r.d.back()


PROBE_CONTEXT = [("probe: open Context from the More menu", lambda r: probe(r, "context"))]
PROBE_CHANGES = [("probe: open Changes from the More menu", lambda r: probe(r, "changes"))]
