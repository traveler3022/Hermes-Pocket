<div align="center">

# ⬡ Hermes2 — Termux Edition

Native Android client for [Hermes Agent](https://github.com/NousResearch/hermes-agent).  
Connect to your self-hosted AI agent on a VPS or Termux.

[![Download v3.0](https://img.shields.io/badge/Download_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)
[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)

Material 3 · Kotlin/Compose · No cloud middleman

</div>

---

## Install

**Download the latest APK** from [Releases](https://github.com/traveler3022/Hermes-android-termux-/releases) and install it on your Android device.

The app connects to a Hermes Agent running on:
- **A VPS** (recommended) — start with `hermes serve` on your server
- **Termux** on the same device — see [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket)

---

## Build from source

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**Requirements:** JDK 17 · Android SDK 35

---

## Architecture

```
Compose UI → ViewModel → HermesRuntime (interface) → GatewayClient
                                                            ↓
                                                    Termux / Remote
                                                            ↓
                                                  Hermes Agent
```

---

## License

MIT

