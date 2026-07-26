<div align="center">

# ⬡ Hermes2 — Android

Native Material 3 Android client for [Hermes Agent](https://github.com/NousResearch/hermes-agent).  
Run your own AI agent on your phone 🤖📱

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)
[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)

</div>

---

## What this is

Hermes2 is an Android app that runs [Hermes Agent](https://github.com/NousResearch/hermes-agent) — an open-source AI agent — on your phone using Termux.  
It can also connect to a remote agent on a VPS.

## Features

- Live streaming chat
- Session management — search, pin, rename, resume
- Full config editor (models, providers, tools, memory) inside the app
- Tasks, projects, billing, plugins, pet
- Material 3 design · Light/dark · English & Persian
- Foreground service

## Quick start

1. Install [Termux from F-Droid](https://f-droid.org/packages/com.termux/)
2. Open Termux, run: `mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties`, then force-stop Termux
3. Download and install the APK from [Releases](https://github.com/traveler3022/Hermes-android-termux-/releases)
4. Open the app → Runtime Setup → Install Hermes Agent
5. Add your API key and start chatting

## Build from source

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**Requires:** JDK 17 · Android SDK 35

## License

MIT — see [LICENSE](LICENSE).

*Independent project · Not affiliated with Nous Research*
