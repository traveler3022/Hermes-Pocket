"""Walks every screen it can reach and taps every button on it, watching for crashes and ANRs.

A screen is known by the labels on it (numbers ignored). For each screen the crawler keeps the
path of taps from the chat screen, so it can come back after a tap leads elsewhere. Taps that
would change what the rest of the run relies on (delete, reset, switching the runtime, signing
out) are left to their own tests; the report lists every label that was skipped this way.
"""
import os
import re
import time

import device as dev
from harness import Failure, Run

SKIP = re.compile(
    r"\b(delete|remove|uninstall|reinstall|reset|wipe|erase|clear (all|everything|data|history)|"
    r"sign ?out|log ?out|disconnect|forget|revoke|factory|archive all|"
    r"termux|tailscale|server|remote|pair|switch runtime|use this runtime|built-in linux|"
    r"update hermes|update now|install update|stop hermes|restart (hermes|gateway)|shut ?down|"
    r"voice input|microphone|camera|record|"
    # Leave the app for Android's own pickers; the CI emulator died after these every time.
    r"attach file|take photo|choose image|پیوست فایل|گرفتن عکس)\b", re.I)


def action_label(screen, node):
    """What a clickable says: its own text, or the first texts inside it."""
    if node.label:
        return node.label.strip()[:60]
    inner = [n.label for n in screen.nodes
             if n.label and n.index_path.startswith(node.index_path + ".")]
    if inner:
        return " · ".join(inner[:2])[:60]
    x1, y1, x2, y2 = node.bounds
    return f"@{(x1 + x2) // 2 // 60},{(y1 + y2) // 2 // 60}"


def actions_of(screen):
    seen, out = set(), []
    for node in screen.nodes:
        if not node.clickable or not node.enabled or node.editable or node.area < 40:
            continue
        label = action_label(screen, node)
        key = label
        n = 2
        while key in seen:
            key = f"{label}#{n}"
            n += 1
        seen.add(key)
        out.append((key, label, node))
    return out


class Crawler:
    def __init__(self, r: Run, minutes):
        self.r = r
        self.d = r.d
        self.end = time.time() + minutes * 60
        self.states = {}   # signature -> {"path": [...], "tried": set(), "actions": [...]}
        self.skipped = set()
        self.crashes = []
        self.left_app = []
        self.taps = 0

    def signature(self, screen):
        return screen.signature()

    def register(self, screen, path):
        sig = self.signature(screen)
        if sig not in self.states:
            acts = []
            for key, label, node in actions_of(screen):
                if SKIP.search(label):
                    self.skipped.add(label)
                    continue
                acts.append(key)
            # What is below the fold of a long screen counts as a screen of its own.
            if any(n.scrollable for n in screen.nodes):
                acts.append("<scroll>")
            self.states[sig] = {"path": list(path), "tried": set(), "actions": acts}
            shot = self.d.screenshot(f"state-{len(self.states)}")
            self.d.save_dump(f"state-{len(self.states)}", screen)
            print(f"   new screen #{len(self.states)} ({len(acts)} actions) via {' > '.join(path[-4:]) or 'start'}",
                  flush=True)
        return sig

    def home(self):
        if not self.r.go_home():
            raise Failure("could not get back to the chat screen")
        return self.d.dump()

    def replay(self, path):
        """From the chat screen, taps the path again; the screen at its end, or None."""
        screen = self.home()
        for step in path:
            if step == "<scroll>":
                self.d.scroll_down()
                screen = self.d.dump()
                continue
            match = next((node for key, _, node in actions_of(screen) if key == step), None)
            if match is None:
                return None
            self.d.tap(match, screen)
            time.sleep(0.8)
            screen = self.d.dump()
            if not screen.in_app():
                return None
        return screen

    def problems(self, context):
        found = self.r.log.take()
        for p in found:
            self.crashes.append({"kind": p["kind"], "after": context, "lines": p["lines"][:30]})
            print(f"   !!! {p['kind']} after {context}", flush=True)
            self.d.screenshot(f"{p['kind']}-{len(self.crashes)}")
        return found

    def run(self):
        screen = self.home()
        current = self.register(screen, [])
        while time.time() < self.end:
            state = self.states[current]
            todo = [a for a in state["actions"] if a not in state["tried"]]
            if not todo:
                target = next((s for s, v in self.states.items()
                               if [a for a in v["actions"] if a not in v["tried"]]), None)
                if target is None:
                    break
                screen = self.replay(self.states[target]["path"])
                if screen is None or self.signature(screen) != target:
                    # Can't get there again (it depended on timing or data): give up on it.
                    self.states[target]["tried"].update(self.states[target]["actions"])
                    continue
                current = target
                continue
            key = todo[0]
            state["tried"].add(key)
            screen = self.d.dump()
            if self.signature(screen) != current:
                screen = self.replay(state["path"])
                if screen is None:
                    continue
            context = " > ".join(state["path"] + [key])
            if key == "<scroll>":
                self.d.scroll_down()
            else:
                node = next((n for k, _, n in actions_of(screen) if k == key), None)
                if node is None:
                    continue
                self.d.tap(node, screen)
            self.taps += 1
            time.sleep(1.0)
            if self.problems(context):
                self.r.recover()
                current = self.signature(self.d.dump())
                continue
            after = self.d.dump()
            if not after.in_app():
                self.left_app.append({"after": context, "to": sorted(after.packages)})
                self.r.dismiss_system_dialogs()
                self.d.back()
                time.sleep(1)
                if not self.d.dump().in_app():
                    self.d.start_app()
                current = self.signature(self.d.dump())
                if current not in self.states:
                    current = self.register(self.d.dump(), [])
                continue
            sig = self.signature(after)
            if sig not in self.states:
                self.register(after, state["path"] + [key])
            current = sig
        return self.summary()

    def summary(self):
        explored = sum(len(v["tried"]) for v in self.states.values())
        report = {
            "screens": len(self.states), "taps": self.taps, "actions_tried": explored,
            "crashes": self.crashes, "left_app": self.left_app[:50], "skipped_labels": sorted(self.skipped),
        }
        path = os.path.join(self.r.out, "crawler.json")
        import json
        with open(path, "w") as f:
            json.dump(report, f, indent=1, ensure_ascii=False)
        return report


def crawl(r: Run, minutes):
    c = Crawler(r, minutes)
    report = c.run()
    note = (f"{report['screens']} screens, {report['taps']} taps, {len(report['crashes'])} crashes/ANRs, "
            f"left the app {len(report['left_app'])}x, skipped {len(report['skipped_labels'])} risky labels")
    if report["crashes"]:
        lines = []
        for c_ in report["crashes"][:15]:
            lines.append(f"[{c_['kind']}] after: {c_['after']}\n  " + "\n  ".join(c_["lines"][:12]))
        raise Failure(note + "\n" + "\n".join(lines))
    return note
