# AGENTS.md — Hermes Pocket

## Build / Test

| Purpose | Command |
|---------|---------|
| Build debug | `./gradlew assembleDebug` |
| Build release | `./gradlew assembleRelease` |
| Test | `./gradlew test` |
| Lint | `./gradlew lint` |

## Architecture

- **Stack**: Kotlin, Jetpack Compose, Material 3, Hilt DI, OkHttp WebSocket
- **Pattern**: MVVM with Compose
- **Packages**:
  - `ui/screen/` — Compose screens
  - `ui/viewmodel/` — ViewModels (state holders)
  - `ui/component/` — Reusable composables
  - `gateway/` — WebSocket client (OkHttp)
  - `runtime/` — Hermes runtime abstraction (remote/embedded)
  - `di/` — Hilt modules
  - `service/` — Background services (cron, foreground)

## Conventions

- StateFlow for state, SharedFlow for events
- Sealed classes for results (DetectionResult, InstallResult, etc.)
- Hilt for DI — no manual singleton management
- Timber for logging — never log tokens/credentials
- No force-unwraps (!!)) — use safe calls + null handling

## Key files

- `ChatViewModel.kt` — main chat logic (pending split)
- `OkHttpGatewayClient.kt` — WebSocket communication
- `RemoteRuntime.kt` — remote Hermes backend management
- `gradle/libs.versions.toml` — dependency versions
