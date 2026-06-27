# Hermes2 — Android UI for Hermes Agent

Hermes2 is an Android-native Kotlin/Compose app that controls **Hermes Agent** running inside **Termux** during the migration phase.

> Current runtime strategy: **Android app UI + Termux-hosted Hermes Python runtime + local WebSocket dashboard API**.
>
> Long-term target: replace the Termux migration adapter with an embedded Python runtime without changing the UI/ViewModel layers.

---

## TL;DR

```text
Android Compose UI
   ↓ GatewayClient / HermesRuntime interfaces
TermuxBridge + OkHttp WebSocket
   ↓ RUN_COMMAND + ws://127.0.0.1:9119/api/ws
Hermes Agent inside Termux
   ↓
NousResearch/hermes-agent official runtime
```

For the tested Android setup, install Hermes manually in Termux first, then start the gateway from the app.

Recommended model setup for the current phone workflow:

```text
Provider: Xiaomi MiMo
Model:    mimo-v2.5-free  (or mimo-v2.5 / mimo-v2.5-pro if your plan exposes those names)
Optional: Gemini key in .env for later switching/fallback
```

---

## Repository Status

| Area | Status |
|---|---|
| Android project bootstrap | ✅ Done |
| Runtime abstraction | ✅ Done |
| Termux migration adapter | ✅ Done |
| WebSocket client | ✅ Done |
| Chat UI | ✅ Done |
| Runtime setup UI | ✅ Done |
| Settings / backend / tools UI | ✅ Done |
| Sessions & memory UI | ✅ Done |
| Skills UI | ✅ Done |
| Cron UI | ✅ Basic UI done |
| Embedded Python runtime | ⏳ Future |

The app currently expects Hermes to run in Termux and exposes a native Android UI over the Hermes dashboard WebSocket API.

---

## Important Runtime Notes

### Official Hermes source

The Termux runtime should install the official upstream project:

```text
https://github.com/NousResearch/hermes-agent
```

The Android app downloads the official installer from:

```text
https://hermes-agent.nousresearch.com/install.sh
```

### Why Termux?

Hermes Agent is a large Python project with many tools, providers, plugins, skills, memory systems, and gateway integrations. Rewriting it in Kotlin is not practical for the migration phase. Termux lets the Android app use the real Hermes runtime while the app provides a native Material 3 UI.

---

## Quick Start for Developers

### 1. Clone this repository

```bash
git clone -b fix/architecture-and-ui-wiring https://github.com/traveler3022/Hermes2.git
cd Hermes2
```

### 2. Open in Android Studio

Use a recent Android Studio with:

- JDK 17+
- Android SDK 35
- Gradle 8.11.1 wrapper

### 3. Build debug APK

```bash
bash ./gradlew :app:assembleDebug
```

Unit tests:

```bash
bash ./gradlew :app:testDebugUnitTest
```

GitHub Actions also builds the debug APK on push.

---

## Device Setup: Termux + Hermes Agent

For the full phone setup guide, see:

- [`docs/RUNNING_ON_ANDROID_TERMUX.md`](docs/RUNNING_ON_ANDROID_TERMUX.md)

Short version:

```bash
pkg update -y
pkg upgrade -y
pkg install -y git python clang rust make pkg-config libffi openssl ca-certificates curl llvm lld nodejs ripgrep ffmpeg

export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1

mkdir -p "$HOME/.hermes"
git clone https://github.com/NousResearch/hermes-agent.git "$HOME/.hermes/hermes-agent"
cd "$HOME/.hermes/hermes-agent"
python -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
python scripts/install_psutil_android.py --pip "python -m pip"
python -m pip install -e '.[termux]' -c constraints-termux.txt
python -m pip install -e '.[web]' -c constraints-termux.txt
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```

Verify:

```bash
hermes --version
hermes doctor
```

---

## Model Setup: Xiaomi MiMo + Gemini

### Xiaomi MiMo

Edit:

```bash
nano "$HOME/.hermes/.env"
```

For normal MiMo API keys:

```env
XIAOMI_API_KEY=YOUR_MIMO_KEY
XIAOMI_BASE_URL=https://api.xiaomimimo.com/v1
```

For MiMo Token Plan:

```env
XIAOMI_API_KEY=YOUR_TOKEN_PLAN_KEY
XIAOMI_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1
```

Configure Hermes:

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

If your MiMo account does not expose `mimo-v2.5-free`, try:

```bash
hermes config set model.default mimo-v2.5
# or
hermes config set model.default mimo-v2.5-pro
```

### Gemini optional key

Add one of these to `~/.hermes/.env`:

```env
GEMINI_API_KEY=YOUR_GEMINI_KEY
```

or:

```env
GOOGLE_API_KEY=YOUR_GEMINI_KEY
```

Switch to Gemini later with:

```bash
hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

---

## Running with the Android App

1. Install Termux from F-Droid.
2. Enable Termux external commands:

   ```bash
   mkdir -p ~/.termux
   echo 'allow-external-apps=true' > ~/.termux/termux.properties
   ```

   Restart Termux after this.

3. Install Hermes in Termux using the guide above.
4. Install/open the Hermes2 APK.
5. Grant `RUN_COMMAND` permission if Android asks.
6. Open:

   ```text
   Termux & Agent Connection → Start Agent Gateway
   ```

7. Then open Settings to confirm:

   ```text
   Provider: xiaomi
   Model: mimo-v2.5-free / mimo-v2.5 / mimo-v2.5-pro
   ```

Do **not** manually start `hermes dashboard` for normal app use unless debugging. The app injects its own WebSocket session token when it starts the gateway.

---

## Architecture

### Layer dependency

```text
UI screens
  ↓
ViewModels
  ↓
Domain interfaces
  ├── HermesRuntime
  └── GatewayClient
  ↓
Infrastructure
  ├── TermuxBridge
  ├── TermuxInstaller
  ├── TermuxCommandExecutor
  └── OkHttpGatewayClient
  ↓
Hermes Agent in Termux
```

### Key files

| File | Purpose |
|---|---|
| `HermesRuntime.kt` | Runtime interface used by the app |
| `TermuxBridge.kt` | Migration adapter for Termux runtime |
| `TermuxInstaller.kt` | Generates the Termux install script |
| `TermuxCommandExecutor.kt` | Sends RUN_COMMAND intents to Termux |
| `GatewayClient.kt` | WebSocket JSON-RPC interface |
| `OkHttpGatewayClient.kt` | Concrete WebSocket implementation |
| `HermesGatewayService.kt` | Foreground service that keeps gateway connection alive |
| `ConfigViewModel.kt` | Loads active backend and tool capabilities |

---

## UI / Material Design

The Material 3 UI guide lives in:

- [`docs/MATERIAL3_UI_GUIDE.md`](docs/MATERIAL3_UI_GUIDE.md)

Current UI surfaces:

| Screen | Purpose |
|---|---|
| Chat | Conversation, streaming responses, tool cards |
| Runtime Setup | Detect/install/start Termux gateway, view logs |
| Settings | Active backend, model list, capability/tool list |
| Sessions | Session list and memory files |
| Skills | Skill discovery/install/reload |
| Cron | Basic scheduled jobs UI |
| Platforms | Messaging platform placeholder/config guidance |

---

## Troubleshooting

### `jiter` or `pydantic-core` fails to build

Use the Termux environment variables from the run guide:

```bash
export CARGO_HOME="$HOME/.hermes/cargo"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

This avoids broken Cargo mirror config and reduces Rust build pressure on Android.

### `Hermes command not found`

Verify the link:

```bash
which hermes
ls -l "$PREFIX/bin/hermes"
ls -l "$HOME/.hermes/hermes-agent/venv/bin/hermes"
```

Fix:

```bash
cd "$HOME/.hermes/hermes-agent"
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```

### Gateway does not connect

Fetch logs from the app or run in Termux:

```bash
cat "$HOME/.hermes/logs/gateway_stdout.log"
```

Stop stale dashboards before letting the app start one:

```bash
hermes dashboard --stop
```

---

## Security

Never commit API keys. Keep provider credentials only in:

```text
~/.hermes/.env
```

If an API key is pasted into chat, rotate/revoke it immediately.

---

## References

- Hermes Agent upstream: <https://github.com/NousResearch/hermes-agent>
- Official Termux guide: <https://hermes-agent.nousresearch.com/docs/getting-started/termux>
- Xiaomi MiMo Hermes guide: <https://platform.xiaomimimo.com/docs/en-US/integration/hermes-agent>
- Migration spec source: <https://github.com/traveler3022/hermes>
