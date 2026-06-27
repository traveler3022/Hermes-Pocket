# Hermes2 — Android Migration of Hermes Agent

> **Initialized per:** [`migration-spec-v1.0`](https://github.com/traveler3022/hermes/releases/tag/migration-spec-v1.0)
> **Migration specification:** [traveler3022/hermes](https://github.com/traveler3022/hermes)
> **Source project:** [traveler3022/hermes-agent](https://github.com/traveler3022/hermes-agent) (fork of NousResearch/hermes-agent)

---

## What is this?

Hermes2 is the Android port of [Hermes Agent](https://github.com/NousResearch/hermes-agent) — the self-improving AI agent built by Nous Research. This repository contains the Android-native application that wraps the Hermes Python runtime and provides a modern Android UI.

This repository was created **only after** the Migration Specification was frozen at tag `migration-spec-v1.0`. All architectural decisions are documented as ADRs in the spec repo.

---

## Migration Progress

| Step | Title | Status | Commit |
|---:|---|---|---|
| 1 | Project Bootstrap | ✅ Complete | `a381217` |
| 2 | Runtime Abstraction + Termux Bridge | ✅ Complete | `b954905` |
| 3 | WebSocket Client | ✅ Complete | `6314e89` |
| 4 | Chat UI | ✅ Complete | `ebd3ebe` |
| 5 | Configuration UI | ✅ Complete | `dc2a69d` |
| 6 | Foreground Service | ✅ Complete | `84209e3` |
| 7 | Tool Approval Notifications | 🟡 In Progress | — |
| 8 | Messaging Platforms | ⬜ Pending | — |
| 9 | Memory & Sessions | ⬜ Pending | — |
| 10 | Skills Browser | ⬜ Pending | — |
| 11 | Cron Scheduler | ⬜ Pending | — |
| 12 | Embedded Runtime Migration | ⬜ Pending | — |
| 13 | Polish & Release | ⬜ Pending | — |

**Overall completion: 6/13 steps (46%)**

```
████████████████████████████░░░░░░░░░░░░░░░░░░░░░░░░ 46%
```

### Phase 1.5 Quality Gate Status

Every step passes through a 12-rule architecture audit before approval:

| Rule | Description | Status |
|---|---|---|
| 1 | Strict Layer Dependency | ✅ Enforced |
| 2 | Agent Is an Orchestrator Only | ✅ Enforced |
| 3 | Failure Isolation | ✅ Enforced |
| 4 | Debug Isolation | ✅ Enforced |
| 5 | Runtime + Gateway Swap Test | ✅ Enforced |
| 6 | Feature Independence Test | ✅ Enforced |
| 7 | Public API Rule | ✅ Enforced |
| 8 | Debug Boundary | ✅ Enforced |
| 9 | Root Cause Rule | ✅ Enforced |
| 10 | Architecture Fitness Test | ✅ Enforced |
| 10.5 | Layer Architecture Verification | ✅ Enforced |
| 12 | Zero Tolerance Gate Re-Run | ✅ Enforced |

**All 6 completed steps: 12/12 rules PASS on every step.**

See [Step Status Registry](https://github.com/traveler3022/hermes/blob/main/docs/10-foundation-hardening/STEP_STATUS.md) for per-step evidence (timestamp, commit hash, audit report links).

---

## Project Configuration

| Setting | Value | ADR Reference |
|---|---|---|
| **minSdk** | 29 (Android 10) | ADR-012 |
| **targetSdk** | 35 (Latest Stable) | ADR-012 |
| **compileSdk** | 35 (Latest Stable) | ADR-012 |
| **Language** | Kotlin | ADR-002 |
| **UI** | Jetpack Compose (Material 3) | ADR-002, ADR-010 |
| **DI** | Hilt | ADR-002 |
| **Background** | Foreground Service + WorkManager | ADR-004 |
| **Networking** | OkHttp (WebSocket to tui_gateway) | ADR-002 |
| **i18n** | strings.xml + resource qualifiers (RTL-ready) | ADR-013 |

---

## Architecture

### Layer Dependency (Phase 1.5 Rule 1)

```
UI (Compose screens)
 ↓ depends only on
ViewModel (StateFlow + business logic)
 ↓ depends only on
Domain (interfaces — GatewayClient, HermesRuntime)
 ↓ implemented by
Infrastructure (OkHttpGatewayClient, TermuxBridge)
 ↓ bound by
DI (Hilt — the ONLY swap point)
```

### Key Abstractions

| Interface | Purpose | Swap Target |
|---|---|---|
| `HermesRuntime` | Abstracts Python runtime host | TermuxBridge → EmbeddedPythonRuntime (Step 12) |
| `GatewayClient` | Abstracts WebSocket client | OkHttpGatewayClient → mock/stub for tests |

### Swap Test Compliance

Both interfaces pass the compile-time swap test:
- Replacing `TermuxBridge` with `EmbeddedPythonRuntime` affects only `RuntimeModule.kt` (1 file)
- Replacing `OkHttpGatewayClient` with a fake affects only `GatewayModule.kt` (1 file)
- Zero OkHttp/Termux imports in UI or ViewModel layers

---

## Build System

- **Gradle:** 8.11.1
- **AGP:** 8.7.3
- **Kotlin:** 2.0.21
- **Compose BOM:** 2024.12.01
- **JDK:** 17

### Build Pipeline (per step)

```bash
./gradlew assembleDebug    # Build APK
./gradlew test             # Unit tests
./gradlew lintDebug        # Lint
# Phase 1.5 Exit Gate     # 12-rule architecture audit
```

All 4 checks must PASS before a step is marked complete.

---

## Project Structure

```
Hermes2/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/hermes/android/
│       │   ├── HermesApplication.kt          ← @HiltAndroidApp
│       │   ├── MainActivity.kt               ← Single-activity host
│       │   ├── di/
│       │   │   ├── GatewayModule.kt           ← Hilt binding (swap point)
│       │   │   └── RuntimeModule.kt           ← Hilt binding (swap point)
│       │   ├── gateway/                       ← Domain + Infrastructure
│       │   │   ├── GatewayClient.kt           ← Interface
│       │   │   ├── GatewayEvent.kt            ← 30+ event types
│       │   │   ├── OkHttpGatewayClient.kt     ← Concrete impl
│       │   │   └── ...
│       │   ├── runtime/                       ← Domain + Infrastructure
│       │   │   ├── HermesRuntime.kt           ← Interface
│       │   │   ├── termux/TermuxBridge.kt     ← Migration adapter
│       │   │   ├── embedded/EmbeddedPythonRuntime.kt  ← Stub for swap test
│       │   │   └── ...
│       │   ├── service/
│       │   │   ├── HermesGatewayService.kt    ← Foreground service
│       │   │   └── BootReceiver.kt            ← Auto-start on boot
│       │   └── ui/
│       │       ├── screen/                    ← Compose screens
│       │       ├── viewmodel/                 ← ViewModels + UI state
│       │       └── theme/                     ← Material 3 theme
│       └── res/
└── gradle/
    └── libs.versions.toml                     ← Version catalog
```

---

## License

MIT — same as upstream Hermes Agent.

## References

- **Migration spec:** [traveler3022/hermes](https://github.com/traveler3022/hermes) @ `migration-spec-v1.0`
- **Source project:** [traveler3022/hermes-agent](https://github.com/traveler3022/hermes-agent)
- **Original Hermes:** [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent)
