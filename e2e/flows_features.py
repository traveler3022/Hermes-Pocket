"""Features past the chat itself: searching chats, profiles, scheduled jobs. Each one creates
what it needs, checks it shows and works, and removes it again."""
import time

from flows_chat import new_chat, wait_text
from flows_sweep import open_drawer
from harness import L, Failure, Run, check


def fill_field(r: Run, label_keys, value):
    """Types into the text field labelled by one of label_keys (or the first empty field)."""
    screen = r.d.dump()
    labels = [t for k in label_keys for t in L(k)]
    target = None
    for node in screen.find(editable=True):
        inner = [n.label for n in screen.nodes if n.index_path.startswith(node.index_path)]
        if any(l in inner or l == node.label for l in labels):
            target = node
            break
    if target is None:
        # The label sits above its field (a Text, not inside the EditText): the first field below it.
        label = next((n for n in screen.nodes if n.label in labels and not n.editable), None)
        if label is not None:
            below = sorted((n for n in screen.find(editable=True) if n.bounds[1] >= label.bounds[1] - 10),
                           key=lambda n: n.bounds[1])
            target = below[0] if below else None
    if target is None:
        empties = [n for n in screen.find(editable=True) if not n.text or n.text in labels]
        target = empties[0] if empties else None
    check(target is not None, f"no text field for {label_keys}")
    r.d.tap(target, screen)
    r.d.type_text(value)
    r.d.key(111)


def t_search_chats(r: Run):
    new_chat(r)
    r.send_chat("findme zebra", expect="MOCK-REPLY: findme zebra")
    open_drawer(r)
    search = r.d.wait_for(timeout=10, anyof=L("search_chats"))
    check(search is not None, "no 'Search chats' in the drawer")
    r.d.tap(search)
    field = r.d.wait_for(timeout=10, editable=True)
    check(field is not None, "no search field")
    r.d.tap(field)
    r.d.type_text("zebra")
    time.sleep(3)
    r.d.screenshot("search-zebra")
    hit = r.d.wait_for(timeout=20, contains="zebra")
    check(hit is not None, "searching 'zebra' found nothing")
    r.d.key(111)
    hits = [n for n in r.d.dump().find(contains="zebra") if not n.editable]
    check(hits, "the search result row is not tappable text")
    r.d.tap(hits[0])
    check(wait_text(r, "MOCK-REPLY: findme zebra", timeout=60), "opening the search result did not open that chat")


def t_profile_lifecycle(r: Run):
    open_drawer(r)
    switch = r.d.wait_for(timeout=10, anyof=L("switch_profile"))
    check(switch is not None, "no 'Switch profile' in the drawer")
    r.d.tap(switch)
    new = r.d.wait_for(timeout=15, anyof=L("new_profile"))
    check(new is not None, "no 'New profile' in the profile sheet")
    r.d.tap(new)
    time.sleep(1)
    fill_field(r, ["name"], "e2etest")
    r.d.screenshot("new-profile-form")
    create = r.d.scroll_to(anyof=L("create_and_switch"), max_swipes=6)
    check(create is not None, "no 'Create and switch'")
    r.d.tap(create)
    check(r.chat_ready(timeout=180) is not None, "after creating a profile, no chat screen")
    r.d.screenshot("profile-created")
    new_chat(r)
    r.send_chat("from profile e2etest", expect="MOCK-REPLY: from profile e2etest", timeout=240)
    # Back to the main profile, then delete the new one.
    open_drawer(r)
    r.d.tap(r.d.wait_for(timeout=10, anyof=L("switch_profile")))
    row = r.d.wait_for(timeout=15, text="e2etest")
    check(row is not None, "the new profile is not in the profile list")
    main = r.d.wait_for(timeout=10, text="Hermes")
    if main:
        r.d.tap(main)
        check(r.chat_ready(timeout=180) is not None, "switching back to the main profile left no chat screen")
    open_drawer(r)
    r.d.tap(r.d.wait_for(timeout=10, anyof=L("switch_profile")))
    row = r.d.wait_for(timeout=15, text="e2etest")
    check(row is not None, "the new profile vanished before deleting it")
    # Each row has its own "Edit profile" icon; take the one on the e2etest row.
    screen = r.d.dump()
    y1, y2 = row.bounds[1], row.bounds[3]
    edits = [n for n in screen.find(anyof=L("edit_profile")) if n.bounds[1] < y2 + 40 and n.bounds[3] > y1 - 40]
    check(edits, "no 'Edit profile' icon on the e2etest row")
    r.d.tap(edits[0], screen)
    time.sleep(1.5)
    delete = r.d.scroll_to(anyof=L("delete_profile"), max_swipes=8)
    if delete is None:
        r.d.screenshot("profile-no-delete")
        raise Failure("could not find 'Delete profile' for the new profile")
    r.d.tap(delete)
    confirm = r.d.wait_for(timeout=10, anyof=L("delete"))
    check(confirm is not None, "no confirmation for deleting the profile")
    r.d.tap(confirm)
    time.sleep(5)
    open_drawer(r)
    r.d.tap(r.d.wait_for(timeout=10, anyof=L("switch_profile")))
    time.sleep(2)
    r.d.screenshot("profile-deleted")
    check(r.d.dump().first(text="e2etest") is None, "the deleted profile is still listed")
    r.d.back()


def t_cron_lifecycle(r: Run):
    open_drawer(r)
    scheduled = r.d.wait_for(timeout=10, anyof=L("scheduled_2") + L("scheduled"))
    check(scheduled is not None, "no 'Scheduled' in the drawer")
    r.d.tap(scheduled)
    time.sleep(3)
    r.d.screenshot("cron-list")
    new = r.d.wait_for(timeout=20, anyof=L("new_job") + L("create_a_job"))
    check(new is not None, "no way to create a job on the Scheduled screen")
    r.d.tap(new)
    time.sleep(1.5)
    # No spaces: the keyboard's autocorrect turned "say cron hello" into garbage on CI.
    fill_field(r, ["name"], "e2ecronjob")
    fill_field(r, ["prompt"], "say-cron-hello")
    hourly = r.d.scroll_to(anyof=L("every_hour"), max_swipes=6)
    if hourly:
        r.d.tap(hourly)
    r.d.screenshot("cron-form")
    create = r.d.scroll_to(anyof=L("create"), max_swipes=8)
    check(create is not None, "no 'Create' on the job form")
    r.d.tap(create)
    closed = r.d.wait_until(lambda s: s.first(anyof=L("create_cron_job")) is None, timeout=60)
    check(closed is not None, "the job form stayed open after Create: " + " / ".join(r.d.dump().texts())[:300])
    row = r.d.wait_for(timeout=60, contains="e2ecronjob")
    r.d.screenshot("cron-created")
    check(row is not None, "the new job is not in the list")
    screen = r.d.dump()
    y1, y2 = row.bounds[1], row.bounds[3]
    deletes = [n for n in screen.find(anyof=L("delete")) if n.bounds[1] < y2 + 200 and n.bounds[3] > y1 - 40]
    check(deletes, "no Delete icon on the job's row")
    r.d.tap(deletes[0], screen)
    confirm = r.d.wait_for(timeout=10, anyof=L("delete"))
    if confirm:
        r.d.tap(confirm)
    time.sleep(4)
    r.d.screenshot("cron-deleted")
    check(r.d.dump().first(contains="e2ecronjob") is None, "the deleted job is still listed")


FEATURES = [
    ("features: search chats", t_search_chats),
    ("features: profile create, use, delete", t_profile_lifecycle),
    ("features: scheduled job create and delete", t_cron_lifecycle),
]
