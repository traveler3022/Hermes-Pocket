
<p align="center">
  <strong>⬡ Hermes2</strong><br>
  <em>Native Android companion for Hermes Agent</em><br><br>
  <a href="https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml"><img src="https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg" alt="Build"></a>
  <a href="https://github.com/traveler3022/Hermes2/releases/tag/debug-latest"><img src="https://img.shields.io/badge/Download-APK-blue?logo=android" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License">
</p>

---

A focused Android control room for your own AI agent — running entirely on your phone.

> Material 3 chat UI · WebSocket JSON-RPC · Termux runtime bridge · Kotlin/Compose

---

## How it works

```
┌─────────────────────────┐
│   Hermes2 Android App   │   Material 3 UI
└────────────┬────────────┘
             │  WebSocket · ws://127.0.0.1:9119/api/ws
             │  loopback — never leaves your phone
┌────────────▼────────────┐
│   Hermes Agent (Python) │   running inside Termux
└────────────┬────────────┘
             │  HTTPS
┌────────────▼────────────┐
│   Your model provider   │   MiMo · Gemini · Agnes · ...
└─────────────────────────┘
```

The app talks to Hermes Agent over a local WebSocket on `127.0.0.1`.  
That link stays entirely on your device. Hermes runs the AI logic;  
the Android app is the native front-end.

> **No cloud middleman · No account · No telemetry**

---

## Privacy & Security

### 🟢 Stays on your phone

| | What | Where |
|---|---|---|
| **API key** | Stored in `~/.hermes/.env` inside Termux | On your device only |
| **App ↔ Agent link** | WebSocket on `127.0.0.1` | Never leaves the device |
| **Session data** | SQLite inside `~/.hermes/` | On your device only |

### 🟠 Leaves your phone

| | What | Where |
|---|---|---|
| **Your messages** | Sent to the model provider you chose | MiMo → Xiaomi, Gemini → Google, etc. |

**Rule of thumb:** Don't send anything you wouldn't want the provider to see.

### 🛡️ What the agent can do

Hermes can run shell commands inside Termux. On a non-rooted phone,  
Android's sandbox keeps this confined to Termux's own storage —  
the agent **cannot** reach other apps' data or system files.

> ⚠️ **Do not root your phone to run this.** Rooting removes the sandbox.

### ✅ Tool approval

Hermes asks before running dangerous commands. Prompts appear as  
Android notifications with **Approve / Deny** buttons.  
Leave approval enabled — it's the line of defense that stops a destructive command.

> When in doubt → **Deny**, then ask the agent what it was trying to do.

---

## 🔐 Security notes

### Never share your API key
Not in chat, not in screenshots, not masked, not partially.  
If someone sees it, your account is gone. Keys belong in `.env` or `auth.json` — nowhere else.

### Don't bring sensitive files here
Files like `auth.json`, `.env`, `config.yaml` — anything with keys or secrets.  
Don't paste their content, don't screenshot them.

### Script output leaks to provider
Everything Hermes runs in terminal → goes to the model's context → goes to the provider.  
If a key is in the output, the provider sees it. For sensitive work, redirect output to `/dev/null`.

### Key leaked?
Rotate it immediately. Not "later". Hermes supports credential pools — add a new key, remove the old one.

---

## Download

**⬇ Download the latest APK**

Always-fresh debug build — rebuilt automatically on every push.

> This is a debug build (unsigned, for testing). Install only if you trust the source.

---

## Installation

You need two pieces: **Termux** (runs the agent) and **Hermes2** (the interface).

### Step 1 — Install Termux

Install from **F-Droid** (Play Store version is abandoned):

→ [Termux on F-Droid](https://f-droid.org/en/packages/com.termux/)

> ⚠️ Do **not** install from Play Store.

### Step 2 — Allow external apps

Open Termux and run:

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

**Force-stop and reopen Termux** for the setting to take effect.

### Step 3 — Install Hermes Agent

Run the official installer in Termux:

```bash
pkg update -y && pkg install -y curl
curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
```

> ⏱️ First install takes **5–15 min** (compiles Rust crates for ARM). Keep the screen on.

Verify:

```bash
hermes --version
hermes doctor
```

### Step 4 — Install the app

1. Download the APK from the link above
2. Enable "Install from unknown sources" in Android settings
3. Install the APK

### Step 5 — Connect

1. Open **Hermes2**
2. Tap **Runtime Setup**
3. Tap **Start Gateway**
4. Wait **30–90s** on first launch (Python boot + plugin scan)
5. Status shows **● Connected** → start chatting

> Later launches reconnect automatically. No need to repeat setup.

---

## Model setup

Set the key once, inside Termux.

### Option A — Xiaomi MiMo (recommended)

```bash
nano ~/.hermes/.env
```

Add:

```env
XIAOMI_API_KEY=YOUR_KEY_HERE
XIAOMI_BASE_URL=https://api.xiaomimimo.com/v1
```

Save (`Ctrl+O`, `Enter`, `Ctrl+X`), then:

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

Models to try: `mimo-v2.5-free` → `mimo-v2.5` → `mimo-v2.5-pro`

### Option B — Google Gemini

```bash
nano ~/.hermes/.env
```

Add:

```env
GEMINI_API_KEY=YOUR_KEY_HERE
```

```bash
hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

### Option C — Agnes AI (free)

```bash
nano ~/.hermes/.env
```

Add:

```env
AGNES_API_KEY=YOUR_KEY_HERE
AGNES_BASE_URL=https://apihub.agnes-ai.com/v1
```

```bash
hermes config set model.provider agnes
hermes config set model.default agnes-1.5-flash
```

---

## Controlling costs

Most providers bill per token. To avoid surprises:

| Action | Why |
|---|---|
| **Set a spending limit** in your provider's dashboard | Your single best protection |
| **Start with free/cheap models** (mimo-v2.5-free) | Learn how tokens work before spending |
| **Watch early sessions** | Feel how many tokens a task uses |

---

## Troubleshooting

### App stuck on "Connecting..."

Cold-start takes 30–90s. Wait, then check logs:

```bash
cat ~/.hermes/logs/gateway_stdout.log
```

If Termux says Hermes is running but the app won't connect:  
force-stop & reopen Termux once, then start the gateway again.

### `jiter` / `pydantic-core` build fails

Set these before running the installer:

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

A foreground service keeps the gateway alive, but aggressive battery savers can kill it:

**Settings → Apps → Hermes2 → Battery → Unrestricted**

Do the same for Termux if it persists.

---

## Architecture

```
UI (Compose) ─► ViewModel ─► HermesRuntime  (interface)
                         └─► GatewayClient   (interface)
                                  │ Hilt DI
                                  ▼
                         TermuxBridge        (impl)
                         OkHttpGatewayClient (impl)
                                  ▼
                         Hermes Agent in Termux
```

All UI and ViewModel code depends only on **interfaces**.  
Swapping the Termux runtime for an embedded Python runtime (ADR-009)  
requires changing only the DI module — no UI or ViewModel changes.

📂 Design docs: `docs/RUNNING_ON_ANDROID_TERMUX.md` · `docs/MATERIAL3_UI_GUIDE.md`

---

## Build from source

```bash
git clone https://github.com/traveler3022/Hermes2.git
cd Hermes2

# Debug APK
bash ./gradlew :app:assembleDebug

# Unit tests
bash ./gradlew :app:testDebugUnitTest
```

**Requires:** JDK 17 · Android SDK 35 · Android Studio Ladybug+

**Output:** `app/build/outputs/apk/debug/`

---

## Contributing

Issues and PRs welcome. This is an independent community port —  
not an official Nous Research product.

When reporting a bug, include:
- Android version
- Phone model
- Relevant lines from `~/.hermes/logs/gateway_stdout.log`

---

## References

- [Hermes Agent](https://github.com/NousResearch/hermes-agent) — the agent this app wraps
- [Termux on F-Droid](https://f-droid.org/en/packages/com.termux/) — required runtime
- [Xiaomi MiMo API](https://platform.xiaomimimo.com) — recommended model provider

---

## License

**MIT** — see [LICENSE](LICENSE).

Independent project · not affiliated with or endorsed by Nous Research.  
"Hermes Agent" belongs to its respective authors.

---

<p align="center">
  <strong>⬡ Built for Android · Powered by Hermes Agent ⬡</strong>
</p>
