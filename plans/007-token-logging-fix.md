# Plan 007: Stop logging WebSocket URL with token

## Status
- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Category**: security
- **Planned at**: commit `601aecb`, 2026-07-11

## Current state

3 Timber log calls include the full WebSocket URL which contains the session token:
- `RuntimeViewModel.kt:105` — `Timber.i("[Runtime] Connected to remote server: ${handle.webSocketUrl}")`
- `RuntimeViewModel.kt:189` — `webSocketUrl = runtimeState.gateway.webSocketUrl`
- `RuntimeViewModel.kt:256` — `Timber.i("[Runtime] Gateway started: ${handle.webSocketUrl}")`

## Steps
1. Replace `${handle.webSocketUrl}` with `${handle.webSocketUrl.substringBefore("?token=")}` in all log calls
2. Or use a helper: `fun redactUrl(url: String) = url.substringBefore("?token=")`
3. Run build

## Done criteria
- No Timber/Log call includes the raw token
- Build passes
