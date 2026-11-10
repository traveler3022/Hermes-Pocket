"""Drives the app on an emulator through adb and uiautomator, for hours if need be.

The app has no test tags, so everything is found by what a user sees: text and content
descriptions (Compose exposes both to accessibility, which is what uiautomator reads).
"""
import os
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

PACKAGE = os.environ.get("APP_PACKAGE", "com.hermes.android.debug")
ACTIVITY = "com.hermes.android.MainActivity"

_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


TIMEOUTS = []


def run(*args, timeout=120, check=False, text=True):
    """adb <args>; returns stdout (stderr folded in). A command that hangs returns empty: a run that
    lasts hours must not stop on one stuck adb call (TIMEOUTS keeps count)."""
    try:
        proc = subprocess.run(["adb", *args], capture_output=True, text=text, timeout=timeout)
    except subprocess.TimeoutExpired:
        TIMEOUTS.append((time.time(), " ".join(args)[:80]))
        if check:
            raise
        return "" if text else b""
    out = proc.stdout + (proc.stderr if text else b"")
    if check and proc.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed ({proc.returncode}): {out[-400:]}")
    return out


def shell(command, timeout=120, check=False):
    return run("shell", command, timeout=timeout, check=check)


class Node:
    __slots__ = ("text", "desc", "cls", "rid", "clickable", "scrollable", "checkable", "checked",
                 "enabled", "focused", "selected", "bounds", "editable", "long_clickable", "index_path")

    def __init__(self, el, path):
        a = el.attrib
        self.text = a.get("text", "")
        self.desc = a.get("content-desc", "")
        self.cls = a.get("class", "")
        self.rid = a.get("resource-id", "")
        self.clickable = a.get("clickable") == "true"
        self.long_clickable = a.get("long-clickable") == "true"
        self.scrollable = a.get("scrollable") == "true"
        self.checkable = a.get("checkable") == "true"
        self.checked = a.get("checked") == "true"
        self.enabled = a.get("enabled") == "true"
        self.focused = a.get("focused") == "true"
        self.selected = a.get("selected") == "true"
        self.editable = "EditText" in self.cls
        m = _BOUNDS.match(a.get("bounds", ""))
        self.bounds = tuple(int(v) for v in m.groups()) if m else (0, 0, 0, 0)
        self.index_path = path

    @property
    def center(self):
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    @property
    def area(self):
        x1, y1, x2, y2 = self.bounds
        return max(0, x2 - x1) * max(0, y2 - y1)

    @property
    def label(self):
        return self.text or self.desc

    def __repr__(self):
        return f"<{self.cls.split('.')[-1]} '{self.label[:40]}' {self.bounds}{' click' if self.clickable else ''}>"


class Screen:
    """One uiautomator dump: all nodes, plus the labels of what can be read inside each clickable."""

    def __init__(self, xml_text):
        self.xml = xml_text
        self.nodes = []
        self.packages = set()
        try:
            root = ET.fromstring(xml_text)
        except ET.ParseError:
            root = None
        if root is not None:
            self._walk(root, "0")

    def _walk(self, el, path):
        if el.tag == "node":
            self.nodes.append(Node(el, path))
            if el.attrib.get("package"):
                self.packages.add(el.attrib["package"])
        for i, child in enumerate(el):
            self._walk(child, f"{path}.{i}")

    def texts(self):
        return [n.label for n in self.nodes if n.label]

    def find(self, text=None, desc=None, contains=None, regex=None, cls=None, clickable=None, editable=None,
             anyof=None):
        out = []
        for n in self.nodes:
            if text is not None and n.text != text and n.desc != text:
                continue
            if anyof is not None and n.text not in anyof and n.desc not in anyof:
                continue
            if desc is not None and n.desc != desc:
                continue
            if contains is not None and contains.lower() not in (n.text + " " + n.desc).lower():
                continue
            if regex is not None and not re.search(regex, n.text + " " + n.desc):
                continue
            if cls is not None and cls not in n.cls:
                continue
            if clickable is not None and n.clickable != clickable:
                continue
            if editable is not None and n.editable != editable:
                continue
            out.append(n)
        return out

    def first(self, **kw):
        found = self.find(**kw)
        return found[0] if found else None

    def has(self, **kw):
        return bool(self.find(**kw))

    def clickable_for(self, node):
        """The clickable ancestor (or the node) that a tap on this label should hit."""
        best = None
        for n in self.nodes:
            if not n.clickable:
                continue
            if node.index_path == n.index_path or node.index_path.startswith(n.index_path + "."):
                if best is None or len(n.index_path) > len(best.index_path):
                    best = n
        return best or node

    def signature(self):
        """Roughly which screen this is: the labels, without numbers and changing values."""
        labels = sorted({re.sub(r"\d+", "#", n.label)[:40] for n in self.nodes if n.label})
        return "|".join(labels[:60])

    def in_app(self):
        return PACKAGE in self.packages


class Device:
    def __init__(self, out_dir):
        self.out = out_dir
        os.makedirs(os.path.join(out_dir, "shots"), exist_ok=True)
        os.makedirs(os.path.join(out_dir, "dumps"), exist_ok=True)
        self.size = self._size()
        self.shot_count = 0
        self._last = None

    def _size(self):
        out = shell("wm size")
        m = re.search(r"(\d+)x(\d+)", out.split("Override")[-1])
        return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)

    # ---- reading the screen ----
    def dump(self, retries=4):
        for attempt in range(retries):
            out = run("exec-out", "uiautomator", "dump", "--compressed", "/dev/tty", timeout=25)
            if not out:
                # uiautomator stuck waiting for an idle UI: kill it, try again.
                run("shell", "pkill -f uiautomator", timeout=15)
            start = out.find("<?xml")
            end = out.rfind("</hierarchy>")
            if start >= 0 and end > start:
                self._last = Screen(out[start:end + len("</hierarchy>")])
                return self._last
            time.sleep(1 + attempt)
        self._last = Screen("<hierarchy/>")
        return self._last

    def wait_for(self, timeout=30, interval=1.0, **kw):
        """The first node matching kw within timeout, or None."""
        end = time.time() + timeout
        while True:
            screen = self.dump()
            node = screen.first(**kw)
            if node:
                return node
            if time.time() > end:
                return None
            time.sleep(interval)

    def wait_until(self, predicate, timeout=30, interval=1.0):
        end = time.time() + timeout
        while True:
            screen = self.dump()
            if predicate(screen):
                return screen
            if time.time() > end:
                return None
            time.sleep(interval)

    # ---- acting ----
    def tap_xy(self, x, y):
        shell(f"input tap {x} {y}")

    def tap(self, node, screen=None):
        target = (screen or self._last).clickable_for(node) if (screen or self._last) else node
        x, y = target.center if target.area > 0 else node.center
        self.tap_xy(x, y)
        time.sleep(0.6)

    def long_press(self, node):
        x, y = node.center
        shell(f"input swipe {x} {y} {x} {y} 900")
        time.sleep(0.6)

    def tap_text(self, timeout=20, **kw):
        node = self.wait_for(timeout=timeout, **kw)
        if not node:
            return False
        self.tap(node)
        return True

    def type_text(self, value):
        """ASCII text into the focused field. Spaces and shell characters are escaped."""
        chunks = re.split(r"( )", value)
        for chunk in chunks:
            if chunk == " ":
                shell("input keyevent 62")
            elif chunk:
                escaped = chunk.replace("'", "'\\''")
                shell(f"input text '{escaped}'")
        time.sleep(0.3)

    def clear_field(self, length=80):
        shell("input keyevent 123")  # to the end, then delete backwards
        shell("input keyevent " + " ".join(["67"] * length))

    def key(self, code):
        shell(f"input keyevent {code}")
        time.sleep(0.4)

    def back(self):
        self.key(4)

    def home(self):
        self.key(3)

    def swipe(self, x1, y1, x2, y2, ms=350):
        shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")
        time.sleep(0.5)

    def scroll_down(self, area=None):
        w, h = self.size
        if area:
            x1, y1, x2, y2 = area.bounds
            cx = (x1 + x2) // 2
            self.swipe(cx, y1 + (y2 - y1) * 3 // 4, cx, y1 + (y2 - y1) // 4)
        else:
            self.swipe(w // 2, h * 3 // 4, w // 2, h // 4)

    def scroll_up(self):
        w, h = self.size
        self.swipe(w // 2, h // 4, w // 2, h * 3 // 4)

    def scroll_to(self, max_swipes=12, **kw):
        """Scrolls down until a node matching kw is on screen; returns it or None."""
        last_sig = None
        for _ in range(max_swipes):
            screen = self.dump()
            node = screen.first(**kw)
            if node:
                return node
            sig = screen.signature()
            if sig == last_sig:
                return None
            last_sig = sig
            self.scroll_down()
        return None

    # ---- app and system ----
    def start_app(self):
        shell(f"am start -W -n {PACKAGE}/{ACTIVITY}", timeout=60)
        time.sleep(2)

    def stop_app(self):
        shell(f"am force-stop {PACKAGE}")
        time.sleep(1)

    def kill_app_process(self):
        """As Android does to a backgrounded app under memory pressure."""
        shell(f"am kill {PACKAGE}")
        time.sleep(1)

    def pid(self):
        return shell(f"pidof {PACKAGE}").strip()

    def foreground_package(self):
        out = shell("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
        m = re.search(r"(\S+)/", out)
        return m.group(1).split()[-1] if m else ""

    def screenshot(self, name):
        self.shot_count += 1
        safe = re.sub(r"[^A-Za-z0-9_.-]+", "_", name)[:80]
        path = os.path.join(self.out, "shots", f"{self.shot_count:04d}_{safe}.png")
        data = run("exec-out", "screencap", "-p", timeout=60, text=False)
        if isinstance(data, (bytes, bytearray)) and len(data) > 100:
            with open(path, "wb") as f:
                f.write(data)
            return path
        return None

    def save_dump(self, name, screen=None):
        screen = screen or self._last or self.dump()
        safe = re.sub(r"[^A-Za-z0-9_.-]+", "_", name)[:80]
        path = os.path.join(self.out, "dumps", f"{self.shot_count:04d}_{safe}.xml")
        with open(path, "w", encoding="utf-8") as f:
            f.write(screen.xml)
        return path

    def rotate(self, landscape):
        """Landscape for the app (a configuration change, the activity rebuilt) by swapping the
        screen's width and height: really rotating the display killed the headless CI emulator."""
        w, h = self.size
        shell(f"wm size {max(w, h)}x{min(w, h)}" if landscape else "wm size reset")
        time.sleep(3)

    def network(self, on):
        state = "enable" if on else "disable"
        shell(f"svc wifi {state}")
        shell(f"svc data {state}")
        time.sleep(3)


class LogWatch:
    """Keeps the whole logcat in a file and notices crashes, ANRs and app errors as they come."""

    PATTERNS = [
        ("crash", re.compile(r"FATAL EXCEPTION|AndroidRuntime.*Process: " + re.escape(PACKAGE))),
        ("anr", re.compile(r"ANR in " + re.escape(PACKAGE))),
        ("native-crash", re.compile(r"F DEBUG .*(Fatal signal|>>> " + re.escape(PACKAGE) + ")")),
        ("strictmode", re.compile(r"StrictMode policy violation")),
    ]

    def __init__(self, path):
        self.path = path
        self.events = []
        self.lock = threading.Lock()
        run("logcat", "-c")
        self.proc = subprocess.Popen(["adb", "logcat", "-v", "threadtime"], stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, errors="replace")
        self.context = []
        self.thread = threading.Thread(target=self._read, daemon=True)
        self.thread.start()

    def _read(self):
        with open(self.path, "a", encoding="utf-8") as f:
            capture = None
            for line in self.proc.stdout:
                f.write(line)
                with self.lock:
                    if capture is not None:
                        if len(capture["lines"]) < 60:
                            capture["lines"].append(line.rstrip())
                        else:
                            capture = None
                    for kind, pattern in self.PATTERNS:
                        if pattern.search(line):
                            capture = {"kind": kind, "time": time.time(), "lines": [line.rstrip()], "step": self.step}
                            self.events.append(capture)
                            break

    step = "start"

    def mark(self, step):
        with self.lock:
            self.step = step

    def take(self, kinds=("crash", "anr", "native-crash")):
        """Problems of this app only: another app's crash (the camera app without a camera) is not ours."""
        with self.lock:
            found = [e for e in self.events if e["kind"] in kinds and not e.get("taken")
                     and (e["kind"] == "anr" or any(PACKAGE in line for line in e["lines"]))]
            for e in found:
                e["taken"] = True
            return found

    def stop(self):
        self.proc.kill()
