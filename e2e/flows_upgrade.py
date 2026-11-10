"""A user on the last release updates to this build: the Linux, Hermes and the chats must survive,
and the new build must work on top of them without a new setup."""
import os
import time

import device as dev
from flows_chat import new_chat, t_agent_apk_install, wait_text
from flows_sweep import open_drawer
from harness import L, Run, check


def t_chat_on_old_version(r: Run):
    new_chat(r)
    r.send_chat("before upgrade", expect="MOCK-REPLY: before upgrade")


def t_install_new_version(r: Run):
    out = dev.run("install", "-r", "-g", os.environ.get("APK", "apk/app-debug.apk"), timeout=600)
    check("Success" in out, "installing the new build over the old one failed: " + out[-400:])
    r.d.start_app()


def t_no_new_setup(r: Run):
    def settled(s):
        if r.dismiss_app_dialogs(s):  # "A new version is out" (this build isn't debug-latest): Later
            return False
        return s.first(anyof=L("ask_hermes")) or s.first(text="Get started") or s.first(text="Where should Hermes run?")
    screen = r.d.wait_until(settled, timeout=240)
    r.d.screenshot("after-upgrade-start")
    check(screen is not None, "after the update the app showed neither the chat nor setup")
    check(screen.first(text="Get started") is None and screen.first(text="Where should Hermes run?") is None,
          "after the update the app asked to set everything up again")


def t_old_chat_still_there(r: Run):
    open_drawer(r)
    row = r.d.wait_for(timeout=30, contains="before upgrade")
    check(row is not None, "the chat from before the update is not in the drawer")
    r.d.tap(row)
    check(wait_text(r, "MOCK-REPLY: before upgrade", timeout=60), "the old chat opened without its messages")


def t_chat_after_upgrade(r: Run):
    new_chat(r)
    r.send_chat("after upgrade", expect="MOCK-REPLY: after upgrade", timeout=300)


UPGRADE = [
    ("upgrade: chat on the old version", t_chat_on_old_version),
    ("upgrade: install this build over it", t_install_new_version),
    ("upgrade: no new setup", t_no_new_setup),
    ("upgrade: the old chat is still there", t_old_chat_still_there),
    ("upgrade: chat works after the update", t_chat_after_upgrade),
    ("upgrade: agent's apk lands in the database", t_agent_apk_install),
]
