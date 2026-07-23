# RFC: Native Plan Seeding via `todo.seed` RPC

> **Status:** Draft — not yet implemented  
> **Date:** 2026-07-23  
> **Author:** traveler3022  
> **Component:** Hermes Agent Gateway (`tui_gateway/server.py`) + Hermes Pocket

---

## Problem

The Pocket app's Task Desk now has a **multi-step plan builder**: the user
creates numbered steps (Step 1 → Step 2 → ...) before launching a task.
These steps are currently sent as a plain numbered-text prompt:

```
1. Do the first thing
2. Then do the second thing
3. Finally do the third thing
```

The agent *might* call its `todo` tool to internalise the plan — or it
might not. There's no guarantee. The current flow depends on the model's
willingness to call `todo` after reading the prompt text.

**What we want:** the app should be able to **pre-seed** the `TodoStore`
directly via RPC, so the agent starts the turn with a fully-populated task
list already in its store — no reliance on the model's initiative.

---

## Current Architecture (as of v0.19.0)

### TodoStore (`tools/todo_tool.py`)

```
TodoStore (in-memory, one per AIAgent/session)
├── _items: List[{id, content, status}]
├── write(todos, merge=False)  → replace entire list
├── write(todos, merge=True)   → update by id, append new
├── read()                     → returns full list
├── has_items()                → bool
└── format_for_injection()     → string for post-compression re-injection
```

- One `TodoStore` instance lives on the `AIAgent` (one per session).
- The `todo` tool reads/writes it.
- After context compression, active items are re-injected into conversation
  via `format_for_injection()`.

### Gateway events (what the app already receives)

| Event | When | Todos? |
|-------|------|--------|
| `tool.start` | Agent calls a tool | `todos` field if tool is `todo` |
| `tool.complete` | Tool returns | `todos` field (full list from result) |
| `message.complete` | Turn ends | — |
| `background.complete` | Background turn ends | — |

### What's missing

There is **no RPC method** to seed `TodoStore` before `prompt.submit`.

The gateway has `prompt.submit` (starts a turn) but no pre-turn hook to
inject state into the agent's tools.

---

## Proposed Design

### New RPC method: `todo.seed`

```json
{
  "method": "todo.seed",
  "params": {
    "session_id": "abc123",
    "todos": [
      {"id": "1", "content": "Build the API endpoint", "status": "pending"},
      {"id": "2", "content": "Write tests", "status": "pending"},
      {"id": "3", "content": "Update documentation", "status": "pending"}
    ]
  }
}
```

**Response:**
```json
{
  "result": {
    "todos": [...],
    "summary": {"total": 3, "pending": 3, "in_progress": 0, "completed": 0, "cancelled": 0}
  }
}
```

### Behaviour

1. **Before `prompt.submit`:** Client calls `todo.seed` with the plan
   steps. The gateway locates the session's `AIAgent` instance (or creates
   it lazily), gets its `TodoStore`, and calls `store.write(todos)`.

2. **During the turn:** When the agent starts, `TodoStore` already has
   items. The agent sees them via `format_for_injection()` (already
   happens post-compression) or by calling `todo` (read mode).

3. **Tool schema injection (optional enhancement):** The agent's system
   prompt could include a hint that todos are pre-seeded:
   ```
   A task list has been pre-populated for this session. Call the `todo`
   tool with no arguments to see it, then mark the first item as
   in_progress and begin.
   ```

### Lifecycle

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  todo.seed  │ ──► │ prompt.submit│ ──► │  Agent turn     │
│ (pre-seed)  │     │ (start turn) │     │  • reads todo   │
│             │     │              │     │  • marks items  │
│             │     │              │     │  • completes    │
└─────────────┘     └──────────────┘     └─────────────────┘
```

### Error cases

| Case | Error code | Message |
|------|-----------|---------|
| Session not found | 4004 | "session not found" |
| Agent already running | 4009 | "turn in progress — cannot seed todos" |
| Invalid todos format | 4004 | "todos must be a list of {id, content, status}" |

---

## Gateway implementation sketch

In `tui_gateway/server.py`:

```python
@method("todo.seed")
def _(rid, params: dict) -> dict:
    sid = params.get("session_id", "")
    todos = params.get("todos", [])
    session, err = _sess_nowait(params, rid)
    if err:
        return err

    with session["history_lock"]:
        if session.get("running"):
            return _err(rid, 4009, "turn in progress — cannot seed todos")

        agent = session.get("agent")
        if agent is None:
            # Lazy-build the agent so TodoStore exists
            agent = _build_agent(session)
            session["agent"] = agent

        store = agent.todo_store  # or however it's accessed
        if not isinstance(todos, list):
            return _err(rid, 4004, "todos must be a list")

        items = store.write(todos, merge=False)

    return {
        "result": {
            "todos": items,
            "summary": {
                "total": len(items),
                "pending": sum(1 for i in items if i["status"] == "pending"),
                "in_progress": sum(1 for i in items if i["status"] == "in_progress"),
                "completed": sum(1 for i in items if i["status"] == "completed"),
                "cancelled": sum(1 for i in items if i["status"] == "cancelled"),
            },
        }
    }
```

---

## Pocket client changes (future)

In `TasksViewModel.launchTask()`:

```kotlin
// 1. Seed the todo list before submitting the prompt
if (steps.size > 1) {
    val todos = steps.mapIndexed { i, step ->
        mapOf(
            "id" to "${i + 1}",
            "content" to step.trim(),
            "status" to "pending",
        )
    }
    gatewayClient.request("todo.seed", mapOf(
        "session_id" to sessionId,
        "todos" to todos,
    ))
}

// 2. Then submit the prompt as usual
gatewayClient.request("prompt.submit", mapOf(
    "session_id" to sessionId,
    "text" to prompt,
))
```

---

## Alternatives considered

### A. Prepend plan to system prompt

Modify `prompt.submit` to accept a `system_note` that gets injected into
the agent's system prompt before the turn.

**Rejected:** System prompt mutation is fragile and bypasses the `TodoStore`
mechanism. The agent might not call `todo` at all.

### B. Post-submit injection via `tool.start` replay

Send a synthetic `tool.start` event for `todo` immediately after
`prompt.submit`.

**Rejected:** Hacky, races with the agent's own tool calls, and the
gateway's event system is not designed for client-side injection.

### C. New `plan.submit` method (separate from todo)

A separate planning RPC that creates a `Plan` entity on the agent, distinct
from `TodoStore`.

**Rejected:** Adds a parallel concept. `TodoStore` already does everything
we need — just needs a way to pre-seed it.

---

## Open questions

1. **Should `todo.seed` work mid-turn?** (merge into running TodoStore?)
   Current proposal: No — reject with 4009 if `session.running`.

2. **Should the prompt text also include the plan?** Yes — for redundancy.
   The agent sees both the structured todo list (via TodoStore) and the
   numbered text in the prompt. Belt and suspenders.

3. **Should `todo.seed` reset all items to `pending`?** Yes — it's a fresh
   plan. Use `merge=False` (replace mode).

4. **Access to TodoStore from the gateway:** Need to verify how
   `AIAgent` exposes `TodoStore` (`agent.todo_store` vs `agent.store` vs
   a method). Check `agent/agent_runtime_helpers.py` or
   `agent/agent_init.py`.
