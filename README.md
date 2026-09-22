<div align="center">

# ⬡ Hermes

### An AI agent that lives entirely on your Android phone

**Hermes is an Android app** that runs [Hermes Agent](https://github.com/NousResearch/hermes-agent) —
an open-source AI agent that doesn't just chat, it *does things*: runs commands, writes code,
manages files, browses the web and runs scheduled tasks. All of it on your phone.
**No server, no Termux, no computer needed.**

<br>

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-Install_now-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/debug-latest)

[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)

[English](README.md) · [فارسی](README.fa.md)

</div>

<p align="center">
  <img src="screenshots/chat.jpg" width="270" alt="Chat">
  &nbsp;&nbsp;
  <img src="screenshots/control-center.jpg" width="270" alt="Control Center">
</p>

---

## 🤔 What is it?

The app ships with its own small **Alpine Linux** system inside it (run with `proot`, no root
required). On first launch it installs Hermes Agent into that Linux, starts it, and gives you a
Material 3 chat interface to talk to it.

| Piece | What it is | Where it runs |
|---|---|---|
| **Hermes Agent** 🧠 | The AI agent (open source, by [Nous Research](https://nousresearch.com)) | The built-in Alpine Linux, inside the app |
| **Hermes** 📱 *(this app)* | Chat UI, installer, terminal, desktop viewer, settings | A normal Android app |

The only thing that leaves your phone is the request to the AI provider you pick
(OpenRouter, Gemini, Anthropic…). Your chats, files and API key stay on the device.

> [!NOTE]
> Already use Termux? During setup you can choose **Termux** instead of the built-in Linux,
> and the app will run Hermes there. The built-in Linux needs nothing else installed.

---

## ✨ Features

- 💬 **Chat** with streaming replies, visible reasoning and tool-call cards; Markdown, tables and Persian text rendered properly
- 🛠️ **Real actions** — the agent runs commands, edits files and executes code in its Linux
- ✅ **Approval prompts** — risky tool calls wait for your Approve / Deny
- 🌐 **Browser & desktop** — a Chromium on a virtual desktop that the agent drives; watch it (and take over) in the app via noVNC
- 🖥️ **Terminal** — a full terminal into the built-in Alpine
- 📂 **Files** — the Linux filesystem shows up in Android's Files app and file picker
- 📎 **Attachments** — send files and images to the agent
- ⏰ **Scheduled tasks** — pick *when* a job runs; no cron syntax needed
- 🗂️ **Sessions** — drawer with search, pin, rename, unread counts, resume any chat
- ⚙️ **Control Center** — providers & models, memory, skills, tools, plugins, platforms
- 🔋 **Works in the background** with a quiet notification while the agent is busy
- 🌙 Light/dark themes · 🌐 English + فارسی

---

## ⚡ Getting started

**Requirements:** Android 10+, an **arm64** or **x86_64** device, ~**1 GB** free space and a stable connection for the first install.

1. **Download & install** the APK: **[⬇ latest build](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/debug-latest)** (allow "install from unknown sources" when asked).
2. **Open Hermes** → *Get started* → *Where should Hermes run?* → **Built-in Linux** → **Install Hermes**.
3. **Wait** while the app installs everything, with a live progress bar:
   ```
   ✓ unpacks Alpine Linux
   ✓ installs Python, git, Node.js
   ✓ downloads Hermes Agent and its Python packages
   ✓ starts Hermes
   ```
   Keep the app open and the screen on. The full log is kept at `/root/.hermes/logs/app-install.log` inside the Linux.
4. **Choose your AI provider**, paste the API key, pick a model — the app verifies the key for you.
5. Say hi. 👋

### Supported providers

OpenRouter · Anthropic · OpenAI · Google Gemini · DeepSeek · Xiaomi MiMo · Z.AI / GLM · Kimi / Moonshot ·
any **OpenAI-compatible** endpoint (custom base URL — e.g. Ollama, Groq, a local server).

> [!TIP]
> Gemini has a free tier, and OpenRouter has free models — good for a first try. Set a spending
> limit in your provider's dashboard before long sessions.

---

## 🛡️ Privacy & security

- 🟢 **Stays on the phone:** your API key, chats, files and the whole agent. No account, no telemetry, no server of ours.
- 🟠 **Leaves the phone:** your messages to the AI provider *you* chose — like any AI app.
- 🔒 **Sandboxed:** the agent runs inside the app's own Linux. Without root it can't reach other apps' data; it only sees files you give it.
- 🔑 **Desktop protected:** the VNC desktop is local to the phone by default and always needs a per-install password; sharing it on your Wi-Fi is an opt-in setting.

> [!CAUTION]
> The agent runs real commands. Keep tool approval on, and when unsure hit **Deny** and ask what it was trying to do.
> Never paste your API key into screenshots or public issues.

---

## 🔧 Troubleshooting

<details>
<summary><b>Install stops or fails</b></summary>

Usually the connection dropped or storage ran out. Free up space (≥ 1 GB), make sure the
connection is stable and tap Install again.
</details>

<details>
<summary><b>The agent stops when the screen is off</b></summary>

Set Hermes to **Unrestricted** battery: Settings → Apps → Hermes → Battery → Unrestricted.
</details>

<details>
<summary><b>Reporting a bug</b></summary>

Include your **Android version**, **phone model**, and the relevant part of the install/connection log.
</details>

---

## 🏗️ Architecture

```
Compose UI ─► ViewModels ─► HermesRuntime (interface) ─► ProotLinuxRuntime   (built-in Alpine)
                        │                            └─► TermuxBridge        (optional)
                        └─► GatewayClient ─► JSON-RPC over stdio / local WebSocket ─► Hermes Agent
```

- **Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · OkHttp · MVVM
- **Built-in runtime:** `proot` + its libs are packaged as `jniLibs` (the only app location Android lets you exec from on targetSdk ≥ 29); the Alpine minirootfs is an APK asset. Both are committed; [`scripts/fetch-proot-runtime.sh`](scripts/fetch-proot-runtime.sh) re-fetches and checksum-verifies them when updating.
- **Gateway:** Hermes runs `python -m tui_gateway.entry` and the app speaks JSON-RPC to it over stdin/stdout.

### Build from source

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug             # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest         # unit tests
```

**Requires:** JDK 17 · Android SDK 35. Every push also builds on GitHub Actions and publishes the APK to the [`debug-latest`](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/debug-latest) release.

---

## 📄 License

**MIT** — see [LICENSE](LICENSE).

<sub>Independent community project · not affiliated with Nous Research · "Hermes Agent" belongs to its authors.</sub>
