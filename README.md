<div align="center">

# ⬡ Hermes2 — Termux Edition

Native Android chat client for a self-hosted [Hermes Agent](https://github.com/NousResearch/hermes-agent).  
Connect to your VPS-hosted AI agent from your phone, anywhere.

Material 3 · Kotlin/Compose · No cloud middleman

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)
[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)

</div>

---

## What this is

A Material 3 Android app that talks to your Hermes Agent running on a VPS (or Termux).  
Your agent stays on your server — the app is just the remote control.

**This is the opposite of on-device:** the agent runs on your VPS, the app connects to it over WebSocket.  
For the on-device version (agent + app on the same phone) see [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket).

## Features

- Live streaming chat with tool-call cards and reasoning view
- Session management — search, pin, rename, resume
- Config editor — models, providers, tools, memory from inside the app
- Task management, projects, billing, plugins, pet
- Full Material 3 theming · Light/dark · English & Persian
- Foreground service for background connectivity

## Getting started

### Server setup

1. Run Hermes Agent on your VPS
2. Start the backend: `hermes serve --host 127.0.0.1 --port 9119`
3. (Optional) Set up a reverse proxy (Caddy/nginx) for remote access

### App setup

1. Download the APK from [Releases](https://github.com/traveler3022/Hermes-android-termux-/releases)
2. Install and open
3. Enter your server address and token
4. Start chatting

## Build from source

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
```

**Requires:** JDK 17 · Android SDK 35

## Architecture

```
UI (Compose) → ViewModel → HermesRuntime (interface)
                         → GatewayClient
                                ↓
                    OkHttp WebSocket
                                ↓
                    Hermes Agent on VPS
```

All UI code depends on interfaces only — swapping the runtime (Termux → Remote) only touches the DI module.

## Privacy

- API key and sessions stay on your VPS
- Messages go to the AI provider you chose (like any AI app)
- No accounts · No telemetry · No cloud middleman

## License

MIT — see [LICENSE](LICENSE).

*Independent project · Not affiliated with Nous Research*
