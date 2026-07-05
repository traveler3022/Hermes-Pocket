# Dev Session Log

Running log of development sessions. **Newest session on top.** Each session
records: what was decided, what was done, what was deliberately skipped, and
where to pick up next. Read this first when resuming work.

---

## 2026-07-05 — Stop the whack-a-mole: port from reference, not from scratch

**Branch:** `claude/dev-session-log-review-lmpwa7`

### Problem statement

Features were being reverse-engineered one at a time against a moving target
(the gateway protocol), so every fix surfaced a new mystery. Hypothesis
tested and **confirmed**: the protocol is identical for every Hermes client
(Termux-local, remote server, upstream's own TUI/desktop) — only the
transport URL differs. The upstream repo ships reference clients whose wiring
can be ported 1:1 instead of rediscovered.

### Decisions

1. **Single source of truth**: all protocol work is ported from
   `docs/upstream-reference/` (snapshot of NousResearch/hermes-agent
   @ `605727e3`, see its README). No more guessing payload shapes.
2. **Do not port everything.** The reference README keeps a
   "deliberately NOT ported" list (terminal.resize, rollback.*, moa.*,
   voice RPCs, spawn_tree.*, billing.*) — don't rediscover those.
3. Protocol drift is checked with `scripts/check-upstream-drift.sh`
   (run it when something protocol-shaped breaks, or periodically).

### Done this session

- **Vendored upstream reference** → `docs/upstream-reference/`
  (`ws.py`, `gatewayTypes.ts`, `gatewayClient.ts`,
  `createGatewayEventHandler.ts`, `turnController.ts` + README with the
  pinned commit and usage guide). Commit `569e37c`.
- **Drift-check script** → `scripts/check-upstream-drift.sh`
  (`--update` moves the pin). Verified green against upstream main.
  Commit `a69f38a`.
- **Closed protocol gaps** (commit `77aa082`), all ported from the reference:
  - `tool.complete.error` was never parsed → tool failures were silently
    shown as successes. Now parsed and rendered on the ToolCall card.
  - `todos` on `tool.start`/`tool.complete` → new `AgentTodoCard`
    (collapsible, above input bar) shows the agent's live task list.
    Cleared on each new prompt (per-turn, like upstream turnController).
  - `approval.request.allow_permanent` → notification now offers
    **Approve / Always / Deny**; "Always" hidden when the gateway caps
    scope. Canonical response choices (`once`/`always`/`deny`) replace the
    non-canonical `"approve"`, which used to skip the gateway's
    persistence logic entirely.

### Findings worth remembering

- **False alarm**: suspected method renames (`reload.mcp` vs `mcp.reload`,
  `config.get` vs `config.get_value`) were wrong — verified against
  upstream `server.py` dispatch table; our `GatewayMethods` names are all
  correct. The suspicion came from an unreliable web summary — always
  verify against the vendored files.
- Upstream `approval.request` no longer documents `pattern_keys`; we still
  parse it (harmless if absent) for older gateways.
- Approval choice values accepted by the gateway:
  `once` | `session` | `always` | `deny` (`tools/approval.py`,
  `_ApprovalEntry.result`). Anything not "deny" unblocks, but only
  `session`/`always` persist.

### Not done / next candidates (in rough priority order)

1. ~~**Verify build via CI**~~ ✅ done — run #42 (`build-apk.yml`) green on
   `395d96c`, so all of the above compiles and the debug APK builds.
2. `session.steer` — redirect the agent mid-turn (upstream has it, big UX win).
3. `session.undo` / `session.compress` / `session.save` — session hygiene.
4. `prompt.background` + `background.complete` UI — long tasks without
   blocking the chat.
5. UI option to answer approvals in-chat (card buttons), not only via
   notification — the in-chat Status message is currently display-only.
6. `moa.*`, voice, rollback — see "deliberately NOT ported" before picking
   any of these up.

### How to continue from here

Read `docs/upstream-reference/README.md`, run
`scripts/check-upstream-drift.sh`, pick the next candidate above, find its
handling in the reference TS files, port to Kotlin. Log the session here.
