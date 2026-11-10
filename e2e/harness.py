"""The harness for the end-to-end run (see run.py).

Each suite is a list of tests. A test passes, fails (an assertion about what the app showed), or
errors (the harness itself broke). Crashes, ANRs and native crashes seen in logcat while a test
ran are added to it and fail it. Everything lands in --out: report.md, results.json, logcat.txt,
screenshots (shots/) and screen dumps (dumps/).
"""
import argparse
import json
import os
import sys
import time
import traceback

import device as dev  # noqa: E402
from device import Device, LogWatch, shell  # noqa: E402

MOCK_BASE = os.environ.get("MOCK_BASE", "http://10.0.2.2:18700/v1")
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")


def _strings(folder):
    import glob
    import xml.etree.ElementTree as ET
    out = {}
    for path in glob.glob(os.path.join(RES, folder, "strings*.xml")):
        for el in ET.parse(path).getroot():
            if el.tag == "string" and el.text:
                out[el.attrib["name"]] = el.text.replace("\\'", "'").replace('\\"', '"')
    return out


STRINGS_EN = _strings("values")
STRINGS_FA = _strings("values-fa")


def L(key):
    """Every language's text for a string resource, for finding it whatever the app's locale."""
    return [v for v in (STRINGS_EN.get(key), STRINGS_FA.get(key)) if v]


class Failure(AssertionError):
    pass


class Skip(Exception):
    """The test can't apply here (say, Hermes doesn't offer that tool); not a pass, not a fail."""


def check(condition, message):
    if not condition:
        raise Failure(message)


class Run:
    def __init__(self, out):
        self.out = out
        os.makedirs(out, exist_ok=True)
        self.d = Device(out)
        self.log = LogWatch(os.path.join(out, "logcat.txt"))
        self.results = []
        self.started = time.time()

    # ---- bookkeeping ----
    def emulator_alive(self):
        return dev.run("get-state", timeout=20).strip() == "device" and bool(dev.run("shell", "echo ok", timeout=20).strip())

    def test(self, name, fn, *args, **kwargs):
        if getattr(self, "dead", False):
            return False
        print(f"\n=== {name}", flush=True)
        self.log.mark(name)
        start = time.time()
        status, detail = "pass", ""
        try:
            note = fn(self, *args, **kwargs)
            detail = note or ""
        except Failure as e:
            status, detail = "fail", str(e)
        except Skip as e:
            status, detail = "skip", str(e)
        except Exception as e:  # the harness, adb, a timeout
            status, detail = "error", f"{type(e).__name__}: {e}\n{traceback.format_exc()[-1500:]}"
        if status != "pass" and not self.emulator_alive():
            # The emulator process itself died (seen twice on CI): nothing after this can run.
            self.dead = True
            status = "emulator-died"
            detail = "the emulator stopped answering adb during this test; not an app result. " + detail[:300]
        problems = self.log.take()
        if problems:
            status = "fail"
            detail += "\n" + "\n".join(f"[{p['kind']}] " + "\n  ".join(p["lines"][:25]) for p in problems)
        shot = None
        if status not in ("pass", "skip", "emulator-died"):
            shot = self.d.screenshot(f"{name}-{status}")
            self.d.save_dump(f"{name}-{status}")
        result = {"name": name, "status": status, "seconds": round(time.time() - start, 1),
                  "detail": detail.strip(), "shot": os.path.relpath(shot, self.out) if shot else None,
                  "problems": [p["kind"] for p in problems]}
        self.results.append(result)
        print(f"--- {status.upper()} {name} ({result['seconds']}s) {detail[:300]}", flush=True)
        if (problems or status == "error") and not getattr(self, "dead", False):
            self.recover()
        self.write()
        return status == "pass"

    def recover(self):
        """Back to a usable app after a crash or a harness error."""
        self.d.key(4)
        if self.d.foreground_package() != dev.PACKAGE or not self.d.pid():
            self.d.start_app()
        self.dismiss_system_dialogs()

    def dismiss_system_dialogs(self):
        screen = self.d.dump()
        for label in ("Close app", "Wait", "OK", "Allow", "While using the app"):
            node = screen.first(text=label)
            if node and not screen.in_app():
                self.d.tap(node, screen)
                return True
        return False

    def dismiss_app_dialogs(self, screen=None):
        """The app's own pop-ups that can come up at any time: What's new, a new version."""
        screen = screen or self.d.dump()
        for key in ("whats_new_got_it", "app_update_later"):
            node = screen.first(anyof=L(key))
            if node:
                self.d.tap(node, screen)
                return True
        return False

    def write(self):
        with open(os.path.join(self.out, "results.json"), "w") as f:
            json.dump({"results": self.results, "seconds": round(time.time() - self.started)}, f, indent=1)
        counts = {}
        for r in self.results:
            counts[r["status"]] = counts.get(r["status"], 0) + 1
        lines = [f"# Hermes Pocket e2e — {', '.join(f'{k}: {v}' for k, v in sorted(counts.items()))}", ""]
        lines.append("| test | result | s | detail |")
        lines.append("|---|---|---:|---|")
        for r in self.results:
            detail = r["detail"].split("\n")[0][:160].replace("|", "\\|")
            lines.append(f"| {r['name']} | {r['status']} | {r['seconds']} | {detail} |")
        failing = [r for r in self.results if r["status"] not in ("pass", "skip")]
        if failing:
            lines += ["", "## Details", ""]
            for r in failing:
                lines += [f"### {r['name']} — {r['status']}", "", "```", r["detail"][:4000], "```",
                          f"screenshot: {r['shot']}" if r["shot"] else "", ""]
        with open(os.path.join(self.out, "report.md"), "w") as f:
            f.write("\n".join(lines))

    # ---- shared steps ----
    def chat_ready(self, timeout=60):
        return self.d.wait_for(timeout=timeout, anyof=L("ask_hermes")) or self.d.wait_for(timeout=5, editable=True)

    def wait_idle(self, timeout=300):
        """Until the agent's turn is over: no Stop button in the composer."""
        return self.d.wait_until(lambda s: s.first(anyof=L("stop")) is None, timeout=timeout, interval=3)

    def send_chat(self, message, timeout=90, expect=None, busy="interrupt"):
        """Types a message into the chat box, sends it, and waits for `expect` (text) to show."""
        field = self.chat_ready()
        check(field is not None, "no chat input on screen")
        self.d.tap(field)
        self.d.type_text(message)
        screen = self.d.wait_until(lambda s: s.first(anyof=L("send")) or s.first(anyof=L("send_options")), timeout=10)
        check(screen is not None, "no Send button after typing")
        send = screen.first(anyof=L("send"))
        if send is not None:
            self.d.tap(send, screen)
        else:
            # Mid-turn: Send asks whether to add to the running task or stop it and send.
            self.d.tap(screen.first(anyof=L("send_options")), screen)
            choice = self.d.wait_for(timeout=10, anyof=L("steer_add_title" if busy == "steer" else "interrupt_send_title"))
            check(choice is not None, "the mid-turn send menu did not open")
            self.d.tap(choice)
        if expect is None:
            return None
        node = self.d.wait_for(timeout=timeout, contains=expect)
        check(node is not None, f"'{expect}' never showed after sending '{message}'")
        return node

    def go_home(self):
        """Back to the chat screen from wherever the app is."""
        for _ in range(6):
            screen = self.d.dump()
            if not screen.in_app():
                if not self.dismiss_system_dialogs():
                    self.d.start_app()
                continue
            if self.dismiss_app_dialogs(screen):
                continue
            if screen.first(anyof=L("ask_hermes")):
                return True
            self.d.back()
        self.d.stop_app()
        self.d.start_app()
        return bool(self.chat_ready(timeout=90))
