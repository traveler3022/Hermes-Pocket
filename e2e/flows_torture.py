"""What a phone does to an app: rotation, background, process death, low memory, no network,
doze, big fonts, dark mode, Persian. After each, the app must still chat."""
import time

import device as dev
from device import shell
from flows_chat import new_chat, wait_text
from harness import L, Run, check


def still_chats(r: Run, tag):
    r.go_home()
    new_chat(r)
    r.send_chat(f"alive {tag}", expect=f"MOCK-REPLY: alive {tag}", timeout=240)


def t_rotate_while_streaming(r: Run):
    new_chat(r)
    r.send_chat("#slow", expect=None)
    check(wait_text(r, "word3", timeout=90, scroll=False), "the slow answer never started")
    for landscape in (True, False, True, False):
        r.d.rotate(landscape)
        r.d.screenshot(f"rotated-{'land' if landscape else 'port'}")
    check(r.d.pid() != "", "the app died while rotating")
    stop = r.d.wait_for(timeout=15, anyof=L("stop"))
    check(stop is not None, "after rotating, the running answer has no Stop button any more")
    r.d.tap(stop)
    still_chats(r, "rotate")


def t_background_and_back(r: Run):
    new_chat(r)
    r.send_chat("before background", expect="MOCK-REPLY: before background")
    r.d.home()
    time.sleep(90)
    r.d.start_app()
    check(wait_text(r, "MOCK-REPLY: before background", timeout=30),
          "coming back from the background, the open chat is gone")
    still_chats(r, "background")


def t_process_death(r: Run):
    new_chat(r)
    r.send_chat("before kill", expect="MOCK-REPLY: before kill")
    r.d.home()
    time.sleep(3)
    r.d.kill_app_process()
    time.sleep(3)
    r.d.start_app()
    r.d.screenshot("after-process-death")
    check(r.chat_ready(timeout=120) is not None, "after Android killed the app, it did not come back to a chat")
    still_chats(r, "killed")


def t_force_stop_midstream(r: Run):
    new_chat(r)
    r.send_chat("#slow", expect=None)
    check(wait_text(r, "word3", timeout=90, scroll=False), "the slow answer never started")
    r.d.stop_app()
    time.sleep(5)
    r.d.start_app()
    r.d.screenshot("after-force-stop")
    check(r.chat_ready(timeout=180) is not None, "after a force stop the app did not reach the chat")
    still_chats(r, "forcestop")


def t_low_memory(r: Run):
    new_chat(r)
    for level in ("RUNNING_MODERATE", "RUNNING_LOW", "RUNNING_CRITICAL"):
        shell(f"am send-trim-memory {dev.PACKAGE} {level}")
        time.sleep(2)
    r.d.home()
    for level in ("BACKGROUND", "MODERATE", "COMPLETE"):
        shell(f"am send-trim-memory {dev.PACKAGE} {level}")
        time.sleep(2)
    r.d.start_app()
    still_chats(r, "lowmem")


def t_network_loss(r: Run):
    new_chat(r)
    shown = None
    r.d.network(False)
    try:
        r.send_chat("while offline", expect=None)
        shown = r.d.wait_until(lambda s: any(k in " ".join(s.texts()).lower()
                                             for k in ("error", "connect", "network", "offline", "failed", "retry")),
                               timeout=240)
        r.d.screenshot("offline")
    finally:
        r.d.network(True)
    time.sleep(10)
    check(shown is not None, "with the network off, sending showed no error at all (or hung)")
    still_chats(r, "network")


def t_doze(r: Run):
    r.d.home()
    shell("dumpsys battery unplug")
    shell("dumpsys deviceidle force-idle")
    time.sleep(120)
    shell("dumpsys deviceidle unforce")
    shell("dumpsys battery reset")
    r.d.start_app()
    still_chats(r, "doze")


def t_big_font_and_density(r: Run):
    # Font only: changing the display density (like rotating it) killed the CI emulator.
    shell("settings put system font_scale 1.6")
    try:
        r.d.stop_app()
        r.d.start_app()
        r.go_home()
        r.d.screenshot("bigfont-chat")
        drawer = r.d.wait_for(timeout=15, anyof=L("sessions"))
        if drawer:
            r.d.tap(drawer)
            r.d.screenshot("bigfont-drawer")
            r.d.back()
        still_chats(r, "bigfont")
    finally:
        shell("settings put system font_scale 1.0")
        r.d.stop_app()
        r.d.start_app()


def t_dark_mode(r: Run):
    shell("cmd uimode night yes")
    try:
        r.d.start_app()
        r.d.screenshot("system-dark")
        still_chats(r, "dark")
    finally:
        shell("cmd uimode night no")


def t_persian(r: Run):
    shell(f"cmd locale set-app-locales {dev.PACKAGE} --locales fa-IR")
    r.d.stop_app()
    r.d.start_app()
    r.d.screenshot("persian-start")
    check(r.chat_ready(timeout=120) is not None, "in Persian the chat screen did not come up")
    still_chats(r, "persian")


def t_gateway_killed(r: Run):
    """Hermes' own process dies (the OOM killer, a crash): the app must bring it back."""
    new_chat(r)
    r.send_chat("before gateway kill", expect="MOCK-REPLY: before gateway kill")
    ps = shell("ps -A -o PID,ARGS")
    pids = [line.split()[0] for line in ps.splitlines() if "tui_gateway" in line or "faulthandler" in line]
    check(pids, "could not find Hermes' gateway process to kill:\n" + ps[-800:])
    for pid in pids:
        shell(f"kill -9 {pid}")
    time.sleep(5)
    r.d.screenshot("gateway-killed")
    r.send_chat("after gateway kill", expect="MOCK-REPLY: after gateway kill", timeout=300)


TORTURE = [
    ("torture: Hermes' process killed", t_gateway_killed),
    ("torture: background for 90 s", t_background_and_back),
    ("torture: process death in the background", t_process_death),
    ("torture: force stop mid-answer", t_force_stop_midstream),
    ("torture: low memory signals", t_low_memory),
    ("torture: network off and on", t_network_loss),
    ("torture: doze", t_doze),
    ("torture: big font", t_big_font_and_density),
    ("torture: system dark mode", t_dark_mode),
    ("torture: rotate while streaming", t_rotate_while_streaming),
]

PERSIAN = [
    ("persian: switch the app to Persian", t_persian),
]
