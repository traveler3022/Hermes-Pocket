"""Chat with the mock model: every kind of answer Hermes can stream, tools, approvals, errors."""
import json
import os
import time

from harness import L, Failure, Run, Skip, check


def new_chat(r: Run):
    r.go_home()
    r.wait_idle(timeout=120)
    node = r.d.wait_for(timeout=10, anyof=L("new_chat"))
    if node:
        r.d.tap(node)
    field = r.chat_ready(timeout=30)
    check(field is not None, "no empty chat after 'New chat'")
    # A draft left in another chat follows into this one (see t_draft_stays_in_its_chat); start clean.
    if field.text and field.text not in L("ask_hermes"):
        r.d.tap(field)
        r.d.clear_field(len(field.text) + 5)


def scroll_until(r: Run, text, swipes=20):
    """Swipes up through the chat until text is on screen (no Scroll to bottom button)."""
    for _ in range(swipes):
        if r.d.dump().first(contains=text):
            return True
        r.d.scroll_down()
    return bool(r.d.dump().first(contains=text))


def wait_text(r: Run, text, timeout=120, scroll=True):
    """Waits for text in the chat, scrolling to the bottom if the list grew past the screen."""
    end = time.time() + timeout
    while time.time() < end:
        screen = r.d.dump()
        if screen.first(contains=text):
            return True
        if scroll:
            down = screen.first(anyof=L("scroll_to_bottom"))
            if down:
                r.d.tap(down, screen)
        time.sleep(2)
    return False


def mock_requests():
    path = os.path.join(os.environ.get("E2E_OUT", "e2e-out"), "mock_llm.jsonl")
    try:
        with open(path) as f:
            return [json.loads(line) for line in f if line.strip()]
    except FileNotFoundError:
        return []


def t_plain(r: Run):
    new_chat(r)
    r.send_chat("plain message one", expect="MOCK-REPLY: plain message one")


def t_long_markdown(r: Run):
    new_chat(r)
    r.send_chat("#long", expect=None)
    check(r.wait_idle(timeout=180) is not None, "the long answer was still streaming after 3 min")
    seen = set()
    for _ in range(14):
        screen = r.d.dump()
        for needle in ("Mock report", "A table", "gamma", "fib", "فارسی", "Math", "The end."):
            if screen.first(contains=needle):
                seen.add(needle)
        r.d.scroll_down()
    r.d.screenshot("long-markdown")
    missing = {"Mock report", "A table", "gamma", "fib", "The end."} - seen
    check(not missing, f"parts of the long markdown never showed: {sorted(missing)}")


def t_scroll_to_bottom_long(r: Run):
    """'Scroll to bottom' after a long answer must show its end (ChatScreen: animateScrollToItem(lastIndex)
    puts the TOP of the last message on screen)."""
    new_chat(r)
    r.send_chat("#long", expect=None)
    check(r.wait_idle(timeout=180) is not None, "the long answer was still streaming after 3 min")
    for _ in range(12):
        r.d.scroll_up()
    button = r.d.wait_for(timeout=10, anyof=L("scroll_to_bottom"))
    check(button is not None, "no Scroll to bottom button after scrolling up a long chat")
    r.d.tap(button)
    time.sleep(2)
    r.d.screenshot("after-scroll-to-bottom")
    screen = r.d.dump()
    at_end = screen.first(contains="The end.") is not None
    still = screen.first(anyof=L("scroll_to_bottom")) is not None
    check(at_end, "BUG: 'Scroll to bottom' stopped at the start of the last (long) answer; its end is not on "
                  f"screen and the button is {'still' if still else 'no longer'} shown")


def t_draft_stays_in_its_chat(r: Run):
    """Unsent text typed in one chat must not show up in a new chat (ChatViewModel keeps one global
    draft, KEY_DRAFT, and clearForNewChat() doesn't clear inputText)."""
    new_chat(r)
    r.send_chat("draft owner chat", expect="MOCK-REPLY: draft owner chat")
    field = r.chat_ready()
    r.d.tap(field)
    r.d.type_text("unsent-draft-xyz")
    r.d.key(111)
    r.d.tap(r.d.wait_for(timeout=10, anyof=L("new_chat")))
    time.sleep(2)
    field = r.chat_ready(timeout=30)
    leaked = field is not None and "unsent-draft-xyz" in (field.text or "")
    r.d.screenshot("draft-after-new-chat")
    if leaked:
        r.d.tap(field)
        r.d.clear_field(40)
    check(not leaked, "BUG?: text typed but not sent in one chat is still in the box after 'New chat' "
                      "(sending would put it in the wrong chat)")


def t_reasoning(r: Run):
    new_chat(r)
    r.send_chat("#reason", expect="REASONED-ANSWER: 42")
    r.d.screenshot("reasoning")


def t_terminal_tool(r: Run):
    new_chat(r)
    r.send_chat("#terminal echo e2e-tool-ok", expect=None)
    check(wait_text(r, "TOOL-DONE", timeout=120), "no final answer after the terminal tool")
    check(r.d.dump().first(contains="e2e-tool-ok") is not None, "the tool's output is not on screen")
    r.d.screenshot("terminal-tool")


def t_parallel_tools(r: Run):
    new_chat(r)
    r.send_chat("#parallel", expect=None)
    check(wait_text(r, "TOOL-DONE", timeout=150), "no final answer after three parallel tool calls")
    text = " ".join(r.d.dump().texts())
    missing = [f"parallel-{i}" for i in range(3) if f"parallel-{i}" not in text]
    r.d.screenshot("parallel-tools")
    check(not missing, f"outputs missing from the final answer: {missing}")


def t_todo(r: Run):
    offered = set()
    for q in mock_requests():
        offered.update(q.get("tools") or [])
    if offered and "todo" not in offered:
        raise Skip("Hermes doesn't offer the todo tool in the app's sessions (tools: " + ", ".join(sorted(offered)) + ")")
    new_chat(r)
    r.send_chat("#todo", expect=None)
    check(wait_text(r, "TOOL-DONE", timeout=120), "no final answer after the todo tool")
    r.d.screenshot("todo")


def t_clarify(r: Run):
    new_chat(r)
    r.send_chat("#clarify", expect=None)
    choice = r.d.wait_for(timeout=120, text="Green")
    r.d.screenshot("clarify")
    check(choice is not None, "the clarify question's choices never showed")
    r.d.tap(choice)
    send = r.d.wait_for(timeout=10, anyof=L("send"))  # the question card's own Send
    check(send is not None, "no Send under the clarify question after choosing")
    r.d.tap(send)
    check(wait_text(r, "TOOL-DONE", timeout=120), "no answer after choosing 'Green' and Send")
    check(r.d.dump().first(contains="Green") is not None, "the chosen answer did not reach the model")


def t_approval_deny_then_allow(r: Run):
    new_chat(r)
    r.send_chat("#danger", expect=None)
    deny = r.d.wait_for(timeout=120, text="Deny")
    r.d.screenshot("approval")
    if deny is None:
        # Some approval modes let it through; that's a finding, not a harness error.
        check(wait_text(r, "TOOL-DONE", timeout=60), "neither an approval prompt nor an answer")
        return "no approval was asked for rm -rf (approval mode lets it run)"
    r.d.tap(deny)
    check(wait_text(r, "TOOL-DONE", timeout=120), "no answer after Deny")
    r.send_chat("#danger", expect=None)
    allow = r.d.wait_for(timeout=120, text="Allow once")
    check(allow is not None, "the second approval prompt did not show")
    r.d.tap(allow)
    check(wait_text(r, "TOOL-DONE", timeout=120), "no answer after Allow once")


def t_stop_midstream(r: Run):
    new_chat(r)
    r.send_chat("#slow", expect=None)
    check(wait_text(r, "word3", timeout=90, scroll=False), "the slow answer never started")
    stop = r.d.wait_for(timeout=10, anyof=L("stop"))
    check(stop is not None, "no Stop button while streaming")
    r.d.tap(stop)
    time.sleep(3)
    before = " ".join(r.d.dump().texts())
    time.sleep(5)
    after = " ".join(r.d.dump().texts())
    r.d.screenshot("after-stop")
    check(before == after or "word199" not in after, "the answer kept streaming after Stop")
    check("word199" not in after, "the whole slow answer arrived despite Stop")
    r.send_chat("after stop", expect="MOCK-REPLY: after stop")


def t_provider_error_500(r: Run):
    new_chat(r)
    r.send_chat("#error500", expect=None)
    retrying = r.d.wait_for(timeout=120, contains="retrying")
    r.d.screenshot("error500-retrying")
    idle = r.wait_idle(timeout=900)
    r.d.screenshot("error500-gave-up")
    check(idle is not None, "15 minutes after a provider 500 the turn still hadn't ended")
    text = " ".join(r.d.dump().texts()).lower()
    check(any(k in text for k in ("error", "500", "failed", "unavailable")),
          "after retries the provider 500 left no error message in the chat")
    r.send_chat("after error", expect="MOCK-REPLY: after error", timeout=150)
    return "showed a retry notice" if retrying else "no retry notice"


def t_provider_error_429(r: Run):
    new_chat(r)
    r.send_chat("#error429", expect=None)
    shown = r.d.wait_until(lambda s: any(k in " ".join(s.texts()).lower()
                                         for k in ("rate", "429", "error", "limit", "retry")), timeout=240)
    r.d.screenshot("error429")
    check(shown is not None, "a provider 429 showed nothing")
    r.send_chat("after ratelimit", expect="MOCK-REPLY: after ratelimit", timeout=240)


def t_bad_stream(r: Run):
    new_chat(r)
    r.send_chat("#badstream", expect=None)
    time.sleep(20)
    r.d.screenshot("badstream")
    r.send_chat("after badstream", expect="MOCK-REPLY: after badstream", timeout=180)


def t_queue_while_busy(r: Run):
    new_chat(r)
    r.send_chat("#slow", expect=None)
    check(wait_text(r, "word2", timeout=90, scroll=False), "the slow answer never started")
    r.send_chat("cut in while busy", expect=None, busy="interrupt")
    r.d.screenshot("stop-and-send")
    check(wait_text(r, "MOCK-REPLY: cut in while busy", timeout=180),
          "'Stop and send' mid-turn never got its own answer")


def t_steer(r: Run):
    """'Add to current task' lands with the next tool result (Hermes' session.steer)."""
    new_chat(r)
    r.send_chat("#terminal sleep 15 && echo steer-base", expect=None)
    check(r.d.wait_for(timeout=60, anyof=L("stop")) is not None, "the tool turn never started")
    time.sleep(3)
    r.send_chat("steer note xyz", expect=None, busy="steer")
    r.wait_idle(timeout=180)
    reached = any("steer note xyz" in (q.get("tail") or "") or "steer note xyz" in (q.get("last_user") or "")
                  for q in mock_requests())
    r.d.screenshot("steer")
    check(reached, "the steer note never reached the model")


def t_subagent(r: Run):
    new_chat(r)
    r.send_chat('#tool delegate_task {"goal": "Say hello and stop."}', expect=None)
    # Hermes runs it in the background: a Sub-agent card, then the batch-complete note answered.
    check(wait_text(r, "Subagent Task Completed", timeout=240), "the subagent never reported done")
    check(wait_text(r, "ASYNC DELEGATION", timeout=120), "the main agent never got the subagent's result")
    r.d.screenshot("subagent")


def t_all_tools(r: Run):
    new_chat(r)
    r.send_chat("#alltools", expect=None)
    end = time.time() + 20 * 60
    approvals = 0
    while time.time() < end:
        screen = r.d.dump()
        if screen.first(contains="ALLTOOLS-DONE"):
            break
        allow = screen.first(text="Allow once")
        if allow:
            approvals += 1
            r.d.tap(allow, screen)
        skip = screen.first(text="Skip")
        if skip:
            r.d.tap(skip, screen)
        down = screen.first(anyof=L("scroll_to_bottom"))
        if down:
            r.d.tap(down, screen)
        time.sleep(4)
    r.d.screenshot("alltools-end")
    calls = [q for q in mock_requests() if (q.get("last_user") or "").startswith("#alltools")]
    check(r.d.dump().first(contains="ALLTOOLS-DONE") is not None,
          f"not every tool finished in 20 min ({len(calls)} model calls so far)")
    return f"{len(calls)} model calls, {approvals} approvals"


def t_model_switch(r: Run):
    new_chat(r)
    picker = r.d.wait_for(timeout=15, anyof=L("change_model"))
    check(picker is not None, "no model picker on the chat screen")
    r.d.tap(picker)
    other = r.d.wait_for(timeout=5, text="mock-model-2")
    if other is None:
        provider = r.d.scroll_to(contains="10.0.2.2", max_swipes=6)
        check(provider is not None, "the custom provider is not in the model picker")
        r.d.tap(provider)
        other = r.d.scroll_to(text="mock-model-2", max_swipes=8)
    r.d.screenshot("model-picker")
    check(other is not None, "mock-model-2 is not listed under the custom provider")
    r.d.tap(other)
    time.sleep(3)
    r.d.back() if r.d.dump().first(text="mock-model-2") else None
    before = len(mock_requests())
    r.send_chat("which model", expect="MOCK-REPLY: which model", timeout=150)
    used = [q.get("model") for q in mock_requests()[before:] if q.get("path", "").endswith("chat/completions")]
    check("mock-model-2" in used, f"the switch did not reach Hermes; models used: {used}")


def t_drawer_and_history(r: Run):
    r.go_home()
    sessions = r.d.wait_for(timeout=10, anyof=L("sessions"))
    check(sessions is not None, "no Sessions (drawer) button")
    r.d.tap(sessions)
    r.d.screenshot("drawer")
    entry = r.d.wait_for(timeout=20, contains="plain message one")
    check(entry is not None, "an earlier chat ('plain message one') is not in the drawer")
    r.d.tap(entry)
    check(wait_text(r, "MOCK-REPLY: plain message one", timeout=60), "reopening the chat did not show its messages")


def t_drawer_lists_new_chat(r: Run):
    new_chat(r)
    r.send_chat("drawer target chat", expect="MOCK-REPLY: drawer target chat")
    r.wait_idle(timeout=60)
    time.sleep(5)
    r.d.tap(r.d.wait_for(timeout=10, anyof=L("sessions")))
    entry = r.d.wait_for(timeout=20, contains="drawer target chat")
    r.d.screenshot("drawer-new-chat")
    check(entry is not None, "the chat just made is not in the drawer")
    r.d.tap(entry)
    check(wait_text(r, "MOCK-REPLY: drawer target chat", timeout=60), "reopening it did not show its messages")


def t_agent_apk_install(r: Run):
    """The agent installs a package with apk: it must land in apk's database (GuestApkBridge)."""
    new_chat(r)
    r.send_chat("#terminal apk add jq && apk info -e jq && echo APK-DB-OK", expect=None)
    check(wait_text(r, "TOOL-DONE", timeout=300), "no answer after the agent ran apk add")
    text = " ".join(r.d.dump().texts())
    r.d.screenshot("agent-apk")
    check("APK-DB-OK" in text, "apk add from the agent did not end up in apk's database: " + text[-300:])
    check("failed to write database" not in text, "apk still says 'failed to write database'")


CHAT = [
    ("chat: agent installs a package with apk", t_agent_apk_install),
    ("chat: plain message", t_plain),
    ("chat: long markdown", t_long_markdown),
    ("chat: reasoning", t_reasoning),
    ("chat: terminal tool", t_terminal_tool),
    ("chat: three tools in parallel", t_parallel_tools),
    ("chat: todo card", t_todo),
    ("chat: clarify question", t_clarify),
    ("chat: approval deny then allow", t_approval_deny_then_allow),
    ("chat: Stop mid-stream", t_stop_midstream),
    ("chat: Add to current task (steer)", t_steer),
    ("chat: Scroll to bottom after a long answer", t_scroll_to_bottom_long),
    ("chat: a draft stays in its own chat", t_draft_stays_in_its_chat),
    ("chat: provider error 500", t_provider_error_500),
    ("chat: provider rate limit 429", t_provider_error_429),
    ("chat: broken stream", t_bad_stream),
    ("chat: Stop and send mid-turn", t_queue_while_busy),
    ("chat: subagent", t_subagent),
    ("chat: model switch", t_model_switch),
    ("chat: the drawer lists a new chat and reopens it", t_drawer_lists_new_chat),
    ("chat: every tool Hermes offers", t_all_tools),
]
