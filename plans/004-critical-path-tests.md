# Plan 004: Add tests for critical paths (3.3% → 30%+ coverage)

## Status
- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Category**: tests
- **Planned at**: commit `601aecb`, 2026-07-11

## Why this matters

Only 2 of 60 source files have tests (3.3%). Critical paths with zero coverage:
- OkHttpGatewayClient (682 lines) — WebSocket communication
- ChatViewModel (1,881 lines) — all chat logic
- ConfigViewModel (1,712 lines) — all settings
- SessionsViewModel (611 lines) — session management
- All screen composables — UI rendering

## Steps

1. Test OkHttpGatewayClient: connect/disconnect/send/receive/reconnect
2. Test RemoteRuntime: detection/install/verify/start/stop
3. Test SessionRepository: CRUD operations
4. Test CronScheduler: schedule/cancel/execute
5. Test ChatUiState: state transitions
6. Test ConfigUiState: state transitions

## Commands
| Purpose | Command |
|---------|---------|
| Test | `./gradlew test` |

## Done criteria
- Test file count: 2 → 10+
- Critical path coverage: 3.3% → 30%+
- All tests pass
