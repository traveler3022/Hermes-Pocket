# Upstream Protocol Reference (vendored)

Snapshot of the **protocol-defining files** from
[NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent),
vendored here so every feature/bugfix in this app is ported **from the
reference** instead of reverse-engineered.

| | |
|---|---|
| **Upstream repo** | `NousResearch/hermes-agent` (MIT, © 2025 Nous Research) |
| **Pinned commit** | `605727e3b471f22a11ba3698f75d4171f5534674` |
| **Snapshot date** | 2026-07-05 |

## Files

| File | Upstream path | What it is |
|---|---|---|
| `ws.py` | `tui_gateway/ws.py` | **Server side** — the wire protocol source of truth (JSON-RPC over WebSocket) |
| `gatewayTypes.ts` | `ui-tui/src/gatewayTypes.ts` | All gateway **event types + RPC response shapes** (the contract) |
| `gatewayClient.ts` | `ui-tui/src/gatewayClient.ts` | Reference client — connect/reconnect, request routing (≈ our `OkHttpGatewayClient.kt`) |
| `createGatewayEventHandler.ts` | `ui-tui/src/app/createGatewayEventHandler.ts` | Event → UI-state mapping (≈ our `ChatViewModel.kt`) |
| `turnController.ts` | `ui-tui/src/app/turnController.ts` | Turn lifecycle: streaming, tools, reasoning (≈ our chat turn handling) |

## How to use this

- **Adding a feature?** Find how `ui-tui` does it in these files, port the
  logic to Kotlin. Do not guess payload shapes — read `gatewayTypes.ts`.
- **Chasing a protocol bug?** Diff our `GatewayEvent.kt` /
  `OkHttpGatewayClient.kt` against `gatewayTypes.ts` / `gatewayClient.ts` first.
- **Checking for upstream changes?** Run `scripts/check-upstream-drift.sh`.
  If it reports drift, review the diff, port what matters, then re-vendor
  (see below) so the pin moves forward deliberately.

## Re-vendoring (moving the pin)

```bash
scripts/check-upstream-drift.sh --update   # fetches latest, overwrites snapshot
# then update the pinned commit + date in this README and commit everything together
```

## Deliberately NOT ported (do not "rediscover" these)

Kept out of the Android app on purpose — revisit only with a real use case:

- `terminal.resize`, `clipboard.paste`, `input.detect_drop` — terminal/desktop-only concepts
- `rollback.list/diff/restore` — file rollback UI; heavy, low value on phone
- `moa.reference` / `moa.aggregating` events — mixture-of-agents display mode
- `voice.toggle` / `voice.record` RPCs — until the app grows a voice feature
- `spawn_tree.*`, `delegation.*` — deep subagent tree UI
- `billing.*` beyond the step-up event we already parse
