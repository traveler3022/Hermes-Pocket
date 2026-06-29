# Hermes2

**Native Android companion for [Hermes Agent](https://github.com/NousResearch/hermes-agent)**

Material 3 chat UI · WebSocket JSON-RPC · Termux runtime bridge · Kotlin/Compose

[![Build](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml)
[![Download APK](https://img.shields.io/badge/Download-APK-blue?logo=android)](https://github.com/traveler3022/Hermes2/releases/tag/debug-latest)

---

## Download

Always-fresh debug APK — rebuilt automatically on every push:

**[→ Download latest APK](https://github.com/traveler3022/Hermes2/releases/download/debug-latest/app-debug.apk)**

---

## How it works

```
Hermes2 Android App
      ↕  WebSocket  ws://127.0.0.1:9119/api/ws
Hermes Agent  (Python, running inside Termux)
```

The app talks to Hermes Agent over a local WebSocket. Hermes runs the AI logic; the Android app provides a native Material 3 chat interface.

---

## Installation Guide

### Step 1 — Install Termux

Install **Termux** from F-Droid (the Play Store version is abandoned):

[→ Termux on F-Droid](https://f-droid.org/en/packages/com.termux/)

> Do **not** install from Play Store — it no longer receives updates.

---

### Step 2 — Enable external commands in Termux

Open Termux and run:

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

Then **force-stop and reopen Termux** for the setting to take effect.

---

### Step 3 — Install Hermes Agent in Termux

Run the official installer inside Termux:

```bash
pkg update -y && pkg install -y curl
curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
```

The install takes **5–15 minutes** on the first run (compiles Rust crates for ARM).

Verify when done:

```bash
hermes --version
hermes doctor
```

---

### Step 4 — Install the Android App

1. Download the APK from the link at the top of this page
2. Enable "Install from unknown sources" in Android settings
3. Install the APK

---

### Step 5 — Connect

1. Open **Hermes2**
2. Tap **Runtime Setup** (wrench icon or via the title bar)
3. Tap **Start Gateway**
4. Wait ~30–90 seconds for Hermes to boot (first launch is slow — Python + plugin scan)
5. Once the status shows **● Connected**, you can chat

> On subsequent launches the app reconnects automatically — you don't need to go through setup again.

---

## Model Configuration

Hermes2 works with any model Hermes Agent supports. Recommended for Android (low-cost, fast):

### Xiaomi MiMo (recommended)

```bash
# In Termux:
nano ~/.hermes/.env
```

Add:

```env
XIAOMI_API_KEY=your_key_here
XIAOMI_BASE_URL=https://api.xiaomimimo.com/v1
```

Then set it as default:

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

Available model names (try in order if one doesn't work):
- `mimo-v2.5-free`
- `mimo-v2.5`
- `mimo-v2.5-pro`

### Google Gemini (alternative)

```bash
# In Termux:
nano ~/.hermes/.env
```

Add:

```env
GEMINI_API_KEY=your_key_here
```

```bash
hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

---

## After App Reinstall

If you reinstall Hermes2, it automatically syncs the session token from Termux — you don't need to restart Hermes or do anything manually. Just open the app and wait for it to reconnect.

---

## Troubleshooting

### App shows "Connecting…" forever

Hermes cold-start takes 30–90 seconds on a phone. Wait a bit, then:

```bash
# Check gateway logs in Termux:
cat ~/.hermes/logs/gateway_stdout.log
```

### `jiter` / `pydantic-core` build fails

Set these before installing:

```bash
export CARGO_HOME="$HOME/.hermes/cargo"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_BUILD_JOBS=1
```

### `hermes: command not found`

```bash
cd ~/.hermes/hermes-agent
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
```

### Disconnects when screen turns off

This is handled by the foreground service. If it still disconnects:
- Check that battery optimization is **disabled** for Hermes2 in Android settings
- Go to Settings → Apps → Hermes2 → Battery → Unrestricted

---

## Architecture

```
UI (Compose)  →  ViewModel  →  HermesRuntime (interface)
                           →  GatewayClient  (interface)
                                    ↓ Hilt DI
                            TermuxBridge  (impl)
                            OkHttpGatewayClient (impl)
                                    ↓
                         Hermes Agent in Termux
```

All UI and ViewModel code depends only on **interfaces** — swapping the Termux runtime for an embedded Python runtime (ADR-009) requires changing only the DI module.

---

## Build from Source

```bash
git clone https://github.com/traveler3022/Hermes2.git
cd Hermes2

# Debug APK
bash ./gradlew :app:assembleDebug

# Run unit tests
bash ./gradlew :app:testDebugUnitTest
```

Requires: JDK 17 · Android SDK 35 · Android Studio Ladybug or newer

---

## References

- [Hermes Agent](https://github.com/NousResearch/hermes-agent)
- [Termux on F-Droid](https://f-droid.org/en/packages/com.termux/)
- [Xiaomi MiMo API](https://platform.xiaomimimo.com)
