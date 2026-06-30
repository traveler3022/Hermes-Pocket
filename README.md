<div align="center">

# ⬡ Hermes2

### Native Android companion for **[Hermes Agent](https://github.com/NousResearch/hermes-agent)**

![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

[![Build](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml)
[![Download APK](https://img.shields.io/badge/⬇_Download-APK-6750A4?style=flat-square&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes2/releases/tag/debug-latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)

**English** · [فارسی](README.fa.md)

</div>

---

## What is this?

**[Hermes Agent](https://github.com/NousResearch/hermes-agent)** is a powerful open-source AI assistant by [Nous Research](https://nousresearch.com). It's a local-first, privacy-respecting, fully autonomous agent that connects to dozens of external tools and services to get things done on your behalf.

Unlike simple chatbots, Hermes can actually *do things*:

- 🧠 **Work with code** — write, edit, debug, lint, and run code in any language
- 📁 **Manage files** — read, write, search, convert documents (PDF, DOCX, images, audio, video)
- 🌐 **Browse the web** — search (Google, Brave, Perplexity), fetch pages, interact with web apps
- 📅 **Handle productivity** — Gmail, Google Calendar, Slack, Discord, Telegram, Notion, GitHub, Jira, Linear, and more
- 🖼️ **Generate & analyze media** — image generation (DALL·E, Stable Diffusion), OCR, audio transcription, TTS
- 🧩 **Extend itself** — custom skills, plugins, MCP servers, cron jobs, persistent memory with vector search
- 🤖 **Delegate to sub-agents** — spawn specialized agents for parallel tasks
- 🔒 **Stay private** — local-first, your data stays on your machine

It supports any OpenAI-compatible model provider: MiMo, Gemini, OpenRouter, Claude, Mistral, Groq, Ollama, and more. Full docs at **[hermes-agent.nousresearch.com/docs](https://hermes-agent.nousresearch.com/docs)**.

**Hermes2** brings all of that to Android as a native Material 3 app. The app is the front-end — Hermes running inside Termux is the brain. All communication stays on your phone over a local WebSocket.

> **No cloud middleman · No account · No telemetry**

---

## What the app gives you

- **Live chat** with streaming responses, reasoning view, and collapsible tool-call cards
- **Session management** — search, pin, rename, resume any past conversation
- **Tool approval** as Android notifications — Approve or Deny before anything runs
- **Runtime Setup** — detect, install, and start the Hermes gateway from the app
- **Config & model** management without leaving the app
- **6 color themes**, light/dark/system modes, full Material 3 design
- **Bilingual** — English and فارسی onboarding and UI
- **Foreground service** — keeps the gateway alive when the screen is off
- **Share intent** — send text from any app into Hermes2 chat

> [!IMPORTANT]
> **This is a power-user tool, not a casual chat app.** Hermes Agent runs real commands on your device. If you just want to chat with AI, a normal app is better. If you want an agent that actually *does things* on your phone — read on.

---

## Privacy & Security

> [!WARNING]
> Read this before installing.

### 🟢 Stays on your phone

| What | Where |
|---|---|
| **Your API key** | Hermes' config (`~/.hermes/.env`) inside Termux. This app sends it nowhere. |
| **App ↔ agent link** | `127.0.0.1` loopback — never leaves the device. |
| **Session data** | SQLite inside `~/.hermes/` — on your device only. |

### 🟠 Leaves your phone

| What | Where |
|---|---|
| **Your messages** | Go to the model provider you chose — MiMo → Xiaomi, Gemini → Google, etc. |

**Rule of thumb:** Don't send anything you wouldn't want the provider to see.

### 🛡️ What the agent can do on your device

Hermes runs shell commands inside Termux. On a **non-rooted** phone, Android's sandbox confines this to Termux's own storage — the agent **cannot** reach other apps' data or system files.

> [!CAUTION]
> **Do not root your phone to run this.** Rooting removes the sandbox that keeps the agent contained.

### ✅ Tool approval — keep it on

Hermes asks before running commands it considers dangerous. Prompts appear as Android notifications with **Approve / Deny** buttons. **Leave approval enabled** — it's your line of defense. When in doubt, **Deny**, then ask the agent what it was trying to do.

> [!NOTE]
> This app is provided **as-is** under MIT. You are responsible for your keys, your provider, and the commands you approve.

---

## Download

<div align="center">

### [⬇ Download the latest APK](https://github.com/traveler3022/Hermes2/releases/download/debug-latest/app-debug.apk)

*Always-fresh debug build — rebuilt automatically on every push.*

</div>

---

## Setup

You need **Termux** (runs the agent) and the **Hermes2 APK** (the interface).

1. **[Install Termux](https://f-droid.org/en/packages/com.termux/)** — from F-Droid only, not Play Store
2. **[Install Hermes Agent in Termux](docs/INSTALL_HERMES_TERMUX.md)** — the official installer
3. **[Initial Hermes setup](docs/SETUP_HERMES_TERMUX.md)** — API key, model selection, first config
4. **[Download & connect](docs/GATEWAY_SETUP.md)** — install the APK, start the gateway, first chat

---

## Controlling Costs

- 💳 **Set a spending limit** in your provider's dashboard — your single best protection.
- 🆓 Start with a **free model** like `mimo-v2.5-free` to learn how the agent behaves.
- 👀 Watch the first few sessions to gauge token usage.

---

## Troubleshooting

<details>
<summary><b>App stuck on "Connecting..."</b></summary>

Cold-start takes 30–90 s. Check logs in Termux:
```bash
cat ~/.hermes/logs/gateway_stdout.log
```
If Hermes is running but the app won't connect: force-stop and reopen Termux, then start the gateway again.
</details>

<details>
<summary><b><code>jiter</code> / <code>pydantic-core</code> build fails</b></summary>

Set these before running the installer:
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

```bash
cd ~/.hermes/hermes-agent
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
```
</details>

<details>
<summary><b>Disconnects when screen turns off</b></summary>

Set Hermes2 (and Termux) to **Unrestricted** battery: Settings → Apps → [app] → Battery → Unrestricted.
</details>

---

## Build from Source

```bash
git clone https://github.com/traveler3022/Hermes2.git
cd Hermes2
bash ./gradlew :app:assembleDebug
```

Requires: JDK 17 · Android SDK 35 · Android Studio Ladybug+

---

## Contributing

Issues and PRs welcome. This is an independent community port — not an official Nous Research product. When reporting bugs, include your **Android version**, **phone model**, and relevant lines from `~/.hermes/logs/gateway_stdout.log`.

---

**MIT** — see [LICENSE](LICENSE) · Independent project · not affiliated with Nous Research

<div align="center"><br>

**⬡ Built for Android · Powered by Hermes Agent ⬡**

</div>
