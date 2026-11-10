"""Opens every drawer item and every Settings section, one by one, with a screenshot of each.
Deterministic where the crawler is not: whatever happens, each main screen gets seen."""
import re
import time

from flows_crawler import SKIP, actions_of
from harness import L, Failure, Run, check

DRAWER = ["agent", "scheduled", "group_chats", "media", "files", "drawer_settings"]


def open_drawer(r: Run):
    r.go_home()
    node = r.d.wait_for(timeout=15, anyof=L("sessions"))
    check(node is not None, "no Sessions button to open the drawer")
    r.d.tap(node)
    time.sleep(1)


def t_drawer_items(r: Run):
    opened, broken = [], []
    for key in DRAWER:
        open_drawer(r)
        node = r.d.wait_for(timeout=10, anyof=L(key))
        if node is None:
            broken.append(f"{key}: not in the drawer")
            continue
        r.d.tap(node)
        time.sleep(2.5)
        screen = r.d.dump()
        r.d.screenshot(f"drawer-{key}")
        r.d.save_dump(f"drawer-{key}", screen)
        if not screen.in_app() and not (key == "files" and "com.google.android.documentsui" in screen.packages):
            # Files opens the system file manager on the app's Linux (LinuxFilesProvider), by design.
            broken.append(f"{key}: left the app to {sorted(screen.packages)}")
        problems = r.log.take()
        if problems:
            broken.append(f"{key}: {problems[0]['kind']}: " + " | ".join(problems[0]['lines'][:6]))
        opened.append(key)
    check(not broken, "; ".join(broken))
    return f"opened {len(opened)}: {', '.join(opened)}"


def settings_rows(r: Run):
    """Every row label on the Settings screen, scrolling to the end."""
    labels, last = [], None
    for _ in range(10):
        screen = r.d.dump()
        for key, label, node in actions_of(screen):
            if label not in labels and not label.startswith("@"):
                labels.append(label)
        sig = screen.signature()
        if sig == last:
            break
        last = sig
        r.d.scroll_down()
    return labels


def t_settings_sections(r: Run):
    open_drawer(r)
    node = r.d.wait_for(timeout=10, anyof=L("drawer_settings"))
    check(node is not None, "no Settings in the drawer")
    r.d.tap(node)
    time.sleep(2)
    rows = settings_rows(r)
    r.d.save_dump("settings-root")
    visited, broken, skipped = [], [], []
    for label in rows:
        if SKIP.search(label) or label in L("back"):
            skipped.append(label)
            continue
        # Back to the Settings screen, find the row again (scrolling), open it.
        r.go_home()
        open_drawer(r)
        r.d.tap(r.d.wait_for(timeout=10, anyof=L("drawer_settings")))
        time.sleep(1.5)
        node = r.d.scroll_to(text=label, max_swipes=10)
        if node is None:
            node = r.d.scroll_to(contains=label, max_swipes=10)
        if node is None:
            continue
        r.d.tap(node)
        time.sleep(2.5)
        screen = r.d.dump()
        r.d.screenshot(f"settings-{label}")
        r.d.save_dump(f"settings-{label}", screen)
        problems = r.log.take()
        if problems:
            broken.append(f"'{label}': {problems[0]['kind']}: " + " | ".join(problems[0]['lines'][:6]))
            r.recover()
        elif not screen.in_app():
            broken.append(f"'{label}': left the app to {sorted(screen.packages)}")
            r.recover()
        visited.append(label)
    note = f"{len(visited)} sections opened; skipped as risky: {skipped}"
    check(not broken, note + "\n" + "\n".join(broken))
    return note


SWEEP = [
    ("sweep: every drawer item", t_drawer_items),
    ("sweep: every Settings section", t_settings_sections),
]
