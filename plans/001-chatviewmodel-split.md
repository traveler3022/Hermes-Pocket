# Plan 001: Split ChatViewModel.kt (1,881 lines → 4-5 focused ViewModels)

## Status
- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `601aecb`, 2026-07-11

## Why this matters

ChatViewModel.kt is 1,881 lines handling: WebSocket connection, message
sending/receiving, streaming, session management, attachments, cron jobs,
skill management, scroll state, and UI state. Any change risks breaking
unrelated features. Testing is impossible without mocking 15+ dependencies.

## Current state

- File: `app/src/main/java/com/hermes/android/ui/viewmodel/ChatViewModel.kt`
- Lines: 1,881
- Responsibilities: WebSocket lifecycle, message send/receive, streaming,
  session switching, file attachments, cron scheduling, skill execution,
  scroll position, connection state, error handling
- Dependencies: GatewayClient, HermesRuntime, SessionRepository, WorkManager

## Commands

| Purpose | Command |
|---------|---------|
| Build | `./gradlew assembleDebug` |
| Test | `./gradlew test` |
| Lint | `./gradlew lint` |

## Steps

1. Extract `ChatConnectionViewModel` — WebSocket connect/disconnect/reconnect
2. Extract `ChatMessagesViewModel` — send/receive/stream messages
3. Extract `ChatSessionsViewModel` — session list/switch/create/delete
4. Extract `ChatAttachmentsViewModel` — file pick/attach/send
5. Keep `ChatViewModel` as coordinator — delegates to sub-ViewModels
6. Update ChatScreen.kt to use new ViewModel structure
7. Run build + tests

## STOP conditions
- If Compose ViewModel composition pattern doesn't support nested ViewModels easily
- If session state sharing between ViewModels requires complex state hoisting
