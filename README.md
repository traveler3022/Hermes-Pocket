<div align="center">

# ⬡ Hermes2

### Native Android companion for **[Hermes Agent](https://github.com/NousResearch/hermes-agent)**

*A focused Android control room for your own AI agent — running entirely on your phone.*

<br>

![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

[![Build](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml)
[![Download APK](https://img.shields.io/badge/⬇_Download-APK-6750A4?style=flat-square&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes2/releases/tag/debug-latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)

<br>

`calm` · `technical` · `readable` · `fast` · `trustworthy`

</div>

---

<div align="center">

```
╭──────────────────────────────────────────────╮
│   Hermes2 App  ◄──── ws://127.0.0.1 ────►  Hermes Agent   │
│   (Material 3 UI)      loopback only        (Termux)      │
╰──────────────────────────────────────────────╯
```

**No cloud middleman · No account · No telemetry**

</div>

---

## ⬡ Contents

<table>
<tr>
<td>

- [What is this?](#-what-is-this)
- [How it works](#-how-it-works)
- [Privacy & Security](#-privacy--security--read-first)
- [Download](#-download)

</td>
<td>

- [Installation](#-installation)
- [Model Setup](#-model-setup)
- [Controlling Costs](#-controlling-costs)
- [Troubleshooting](#-troubleshooting)

</td>
<td>

- [Architecture](#-architecture)
- [Build from Source](#-build-from-source)
- [Contributing](#-contributing)
- [License](#-license)

</td>
</tr>
</table>

---

## ⬡ What is this?

> **Hermes Agent** is a powerful open-source AI agent from [Nous Research](https://github.com/NousResearch/hermes-agent). It normally runs on a desktop.
>
> **Hermes2** is the first native **Android** port — bringing the same agent to your phone with a proper Material 3 interface, instead of typing commands into a terminal.

The agent does the work — chatting, running tools, writing code. This app gives it a comfortable home on Android: a real chat screen, config, sessions, skills, and tool-approval notifications.

> [!IMPORTANT]
> **This is a power-user tool, not a casual chat app.** Hermes Agent can run real commands on your device (inside Termux). If you only want to chat with an AI, a normal chat app fits better. If you want a real agent that can *do things* on your phone — this is for you. **Please read [Privacy & Security](#-privacy--security--read-first) before installing.**

---

## ⬡ How it works

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
│   Your model provider   │   MiMo · Gemini · OpenRouter · …
└─────────────────────────┘
```

The app talks to Hermes Agent over a **local** WebSocket on `127.0.0.1` — that link stays entirely on your device. Hermes runs the AI logic; the Android app is the native front-end.

---

## ⬡ Privacy & Security — read first

> [!WARNING]
> This section matters. Please read it fully before installing.

#### 🟢 Stays on your phone

| | |
|---|---|
| **Your API key** | Stored in Hermes' own config (`~/.hermes/.env`) inside Termux, on your device. This app sends your key to **no** server of ours. No account, no backend, no telemetry. |
| **App ↔ agent link** | Runs over `127.0.0.1` (loopback). Never leaves the device. |

#### 🟠 Leaves your phone

| | |
|---|---|
| **Your messages to the AI** | Go to the **model provider you chose** — that's how any AI API works. Pick MiMo → Xiaomi sees it; pick Gemini → Google sees it. |
| **Rule of thumb** | Don't send anything you wouldn't want the provider to see. Be mindful of providers whose data laws you're unsure about. |

#### 🛡️ What the agent can do on your device

Hermes can run shell commands inside Termux. Thanks to Android's app sandbox, on a **non-rooted** phone this is confined to Termux's own storage — the agent **cannot** reach other apps' data or system files. This is real, built-in protection.

> [!CAUTION]
> **Do not root your phone to run this.** Rooting removes the sandbox that keeps the agent contained.

#### ✅ Tool approval — keep it on

Hermes asks before running commands it considers dangerous. Prompts appear as Android notifications with **Approve / Deny** buttons. **Leave approval enabled**, especially while you're new — it's the line of defense that stops a destructive command before it runs. When in doubt, **Deny**, then ask the agent what it was trying to do.

> [!NOTE]
> This app is provided **as-is** under the MIT license. You are responsible for the keys you use, the provider you choose, and the commands you approve.

---

## ⬡ Download

<div align="center">

### [⬇ Download the latest APK](https://github.com/traveler3022/Hermes2/releases/download/debug-latest/app-debug.apk)

*Always-fresh debug build — rebuilt automatically on every push.*

</div>

> [!NOTE]
> This is a **debug** build (unsigned, for testing). Install only if you trust the source and understand the security notes above.

---

## ⬡ Installation

You need two pieces: **Termux** (runs the agent) and the **Hermes2 app** (the interface). Set up Termux first.

### ① Install Termux

Install **Termux** from F-Droid → **[f-droid.org/packages/com.termux](https://f-droid.org/en/packages/com.termux/)**

> [!WARNING]
> Do **not** install Termux from the Play Store — that version is abandoned and won't work.

### ② Allow external apps in Termux

Lets Hermes2 start commands in Termux:

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

Then **force-stop and reopen Termux** to apply it.

### ③ Install Hermes Agent

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

### ④ Install the App

1. Download the APK from the link above.
2. Enable **"Install from unknown sources"** in Android settings.
3. Open the APK and install.

### ⑤ Connect

1. Open **Hermes2**
2. Tap **Runtime Setup**
3. Tap **Start Gateway**
4. Wait **30–90 s** on first launch *(Python boot + plugin scan — only slow once)*
5. Status shows **● Connected** → start chatting

> Later launches reconnect automatically. No need to repeat setup.

---

## ⬡ Model Setup

Set the key once, inside Termux. Two low-cost options that work well on a phone:

<details open>
<summary><b>Option A — Xiaomi MiMo</b> · recommended, low-cost & fast</summary>

<br>

In Termux: `nano ~/.hermes/.env` and add:

```env
XIAOMI_API_KEY=your_key_here
XIAOMI_BASE_URL=https://api.xiaomimimo.com/v1
```

Save (`Ctrl+O`, `Enter`, `Ctrl+X`), then:

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

Model names to try in order if one is unavailable:
`mimo-v2.5-free` → `mimo-v2.5` → `mimo-v2.5-pro`

</details>

<details>
<summary><b>Option B — Google Gemini</b></summary>

<br>

In Termux: `nano ~/.hermes/.env` and add:

```env
GEMINI_API_KEY=your_key_here
```

Then:

```bash
hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

</details>

> [!CAUTION]
> **Keep your key private.** Anyone with your key can spend your credits. Never paste it into screenshots, chats, or public issues. If it leaks → revoke it in the provider dashboard and generate a new one.

---

## ⬡ Controlling Costs

Most providers bill per token. To avoid surprises:

- 💳 **Set a spending limit** in your provider's dashboard *(MiMo, Google, OpenRouter all support this)* — your single best protection.
- 🆓 Start with a **free / cheap model** like `mimo-v2.5-free` while learning the agent's behavior.
- 👀 Watch the first few sessions to feel how many tokens a task uses.

---

## ⬡ Troubleshooting

<details>
<summary><b>App stuck on "Connecting…" forever</b></summary>

<br>

Cold-start takes 30–90 s. Wait, then check logs in Termux:

```bash
cat ~/.hermes/logs/gateway_stdout.log
```

If Termux says Hermes is running but the app won't connect: force-stop & reopen Termux once (to apply `allow-external-apps=true`), then start the gateway again.

</details>

<details>
<summary><b><code>jiter</code> / <code>pydantic-core</code> build fails during install</b></summary>

<br>

Set these in Termux **before** running the installer, then retry:

```bash
export CARGO_HOME="$HOME/.hermes/cargo"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_BUILD_JOBS=1
```

</details>

<details>
<summary><b><code>hermes: command not found</code></b></summary>

<br>

```bash
cd ~/.hermes/hermes-agent
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
```

</details>

<details>
<summary><b>Disconnects when the screen turns off</b></summary>

<br>

A foreground service keeps the gateway alive, but aggressive battery savers can still kill it:

- **Settings → Apps → Hermes2 → Battery → Unrestricted**
- Do the same for **Termux** if it persists.

</details>

---

## ⬡ Architecture

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

All UI and ViewModel code depends only on **interfaces**. Swapping the Termux runtime for an embedded Python runtime *(ADR-009)* requires changing only the DI module — no UI or ViewModel changes. The agent link is JSON-RPC over a local WebSocket.

📂 Design docs in [`docs/`](docs/):
[`RUNNING_ON_ANDROID_TERMUX.md`](docs/RUNNING_ON_ANDROID_TERMUX.md) · [`MATERIAL3_UI_GUIDE.md`](docs/MATERIAL3_UI_GUIDE.md)

---

## ⬡ Build from Source

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

## ⬡ Contributing

Issues and PRs welcome. This is an independent, community port — not an official Nous Research product. When reporting a bug, include your **Android version**, **phone model**, and the relevant lines from `~/.hermes/logs/gateway_stdout.log`.

---

## ⬡ License

**MIT** — see [LICENSE](LICENSE).

<sub>Independent project · not affiliated with or endorsed by Nous Research. "Hermes Agent" belongs to its respective authors.</sub>

<div align="center">
<br>

**⬡ Built for Android · Powered by Hermes Agent ⬡**

</div>
