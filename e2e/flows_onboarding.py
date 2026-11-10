"""A new user's first start, through the app's own UI, on the built-in Linux."""
import time

import device as dev
from device import shell
from harness import MOCK_BASE, Failure, Run, check

# Onboarding: a new user's first start, through the app's own UI, on the built-in Linux
# ======================================================================================

def t_first_launch(r: Run):
    shell(f"pm grant {dev.PACKAGE} android.permission.POST_NOTIFICATIONS")
    r.d.start_app()
    r.d.screenshot("first-launch")
    end = time.time() + 90
    node = None
    while time.time() < end and node is None:
        screen = r.d.dump()
        if r.dismiss_app_dialogs(screen) or (not screen.in_app() and r.dismiss_system_dialogs()):
            continue
        node = screen.first(text="Get started")
        if node is None:
            time.sleep(2)
    check(node is not None, "the welcome screen with 'Get started' did not appear: " + " / ".join(r.d.dump().texts())[:300])
    r.d.tap(node)


def t_choose_builtin_linux(r: Run):
    check(r.d.wait_for(timeout=30, text="Where should Hermes run?") is not None, "runtime choice screen missing")
    linux = r.d.wait_for(timeout=10, text="Built-in Linux")
    check(linux is not None, "no 'Built-in Linux' option")
    r.d.tap(linux)
    r.d.screenshot("runtime-choice")
    install = r.d.scroll_to(text="Install Hermes")
    check(install is not None, "no 'Install Hermes' button")
    r.d.tap(install)


def t_install_linux_and_hermes(r: Run, limit_minutes=75):
    """The full install the app does: rootfs, packages, Node, Hermes. Long; screenshots on the way."""
    end = time.time() + limit_minutes * 60
    last_shot = 0
    last_text = ""
    while time.time() < end:
        screen = r.d.dump()
        if screen.first(text="Hermes is running") or screen.first(text="Choose your AI provider"):
            r.d.screenshot("install-done")
            return f"installed in {round((time.time() - (end - limit_minutes * 60)) / 60, 1)} min"
        start = screen.first(text="Start Hermes")
        if start:
            r.d.tap(start, screen)
        retry = screen.first(regex=r"^(Retry|Try again)$")
        if retry:
            r.d.screenshot("install-retry")
            r.d.save_dump("install-retry", screen)
            r.d.tap(retry, screen)
        if not screen.in_app():
            r.dismiss_system_dialogs()
        texts = " / ".join(t for t in screen.texts() if len(t) < 120)[-300:]
        if texts != last_text:
            print("   install:", texts, flush=True)
            last_text = texts
        if time.time() - last_shot > 120:
            r.d.screenshot("installing")
            last_shot = time.time()
        time.sleep(5)
    r.d.save_dump("install-timeout")
    raise Failure(f"install did not finish in {limit_minutes} min; last screen: {last_text}")


def t_provider_custom_mock(r: Run):
    cont = r.d.wait_for(timeout=20, text="Continue")
    if cont and not r.d.dump().first(text="Choose your AI provider"):
        r.d.tap(cont)
    check(r.d.wait_for(timeout=60, text="Choose your AI provider") is not None, "provider step missing")
    custom = r.d.scroll_to(text="Custom (OpenAI-compatible)", max_swipes=15)
    check(custom is not None, "no 'Custom (OpenAI-compatible)' provider in the list")
    r.d.tap(custom)
    base = r.d.wait_for(timeout=20, editable=True)
    check(base is not None, "no Base URL field")
    fields = r.d.dump().find(editable=True)
    r.d.tap(fields[0])
    r.d.type_text(MOCK_BASE)
    if len(fields) > 1:
        r.d.tap(fields[1])
        r.d.type_text("e2e-test-key")
    r.d.key(111)  # escape: close the keyboard
    verify = r.d.scroll_to(text="Verify and continue")
    check(verify is not None, "no 'Verify and continue'")
    r.d.tap(verify)
    model = r.d.wait_for(timeout=90, text="mock-model")
    if model is None:
        r.d.save_dump("provider-after-verify")
        raise Failure("the mock's model list never showed: " + " / ".join(r.d.dump().texts())[:400])
    r.d.tap(model)
    expensive = r.d.wait_for(timeout=5, text="Use it")
    if expensive:
        r.d.tap(expensive)


def t_reach_chat(r: Run):
    field = r.chat_ready(timeout=120)
    if field is None:
        r.dismiss_system_dialogs()
        field = r.chat_ready(timeout=30)
    r.d.screenshot("chat-ready")
    check(field is not None, "never reached the chat screen after setup: " + " / ".join(r.d.dump().texts())[:300])


def t_first_message(r: Run):
    r.send_chat("hello e2e", timeout=180, expect="MOCK-REPLY: hello e2e")
    r.d.screenshot("first-reply")


ONBOARDING = [
    ("onboarding: first launch", t_first_launch),
    ("onboarding: choose built-in Linux", t_choose_builtin_linux),
    ("onboarding: install Linux and Hermes", t_install_linux_and_hermes),
    ("onboarding: custom provider = mock model", t_provider_custom_mock),
    ("onboarding: reach the chat", t_reach_chat),
    ("chat: first message gets the mock's reply", t_first_message),
]
