<div align="center">

# ⬡ Hermes2

### Your own AI agent — living on your Android phone 🤖📱

**Hermes2 is an Android app** that turns your phone into a home for [Hermes Agent](https://github.com/NousResearch/hermes-agent):
an open-source AI that doesn't just chat — it *does things*. It writes code, runs commands,
manages files and searches the web, right on your device.

<br>

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-Install_now-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes2/releases/tag/debug-latest)

[![Build](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)
![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=flat-square&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)

**No cloud middleman · No account · No telemetry · Your API key never leaves your phone**

</div>

---

## 🤔 What is this, in plain words?

You know ChatGPT? Now imagine instead of a chat that only *talks*, you have an **agent** that can
*act*: run a script, organize files, search the web and report back, schedule tasks — and it runs
**on your own phone**, not on someone's server.

That's what this gives you. Two pieces work together:

| Piece | What it is | Where it runs |
|---|---|---|
| **Hermes Agent** 🧠 | The AI agent itself (open-source, by [Nous Research](https://nousresearch.com)) | Inside [Termux](https://f-droid.org/en/packages/com.termux/) — a Linux terminal app for Android |
| **Hermes2** 📱 *(this app)* | A beautiful Material 3 chat interface that installs, starts, and talks to the agent for you | A normal Android app |

The only thing that leaves your phone is the request to the AI model you choose (Gemini, MiMo, OpenRouter…) — exactly like any AI app. Everything else — your sessions, your files, your API key — stays local.

> [!IMPORTANT]
> **This is a power-user tool, not a casual chat app.** The agent can run real commands (safely sandboxed inside Termux, with an Approve/Deny prompt before anything risky). If you just want to chat with an AI, a normal chat app fits better. If you want an AI that can *do things* — welcome. 🚀

---

## ✨ What can it do?

- 💬 **Live chat** with streaming answers, visible reasoning, and tool-call cards
- 🛠️ **Real actions** — the agent runs commands, edits files, executes code in its sandbox
- 📎 **Send files & images** to the agent straight from your phone; view images it sends back
- ✅ **Tool approval as notifications** — nothing risky runs without your Approve
- 🗂️ **Sessions** — search, pin, rename, and resume any past conversation
- 🎨 **6 color themes**, light/dark, full Material 3 design · 🌐 English + فارسی
- 🔋 **Keeps working with the screen off** (foreground service)

---

## ⚡ Start: Auto Install

**The app installs the agent for you.** You do 4 small things once (~2 minutes of typing);
the app does the heavy lifting (~10–15 minutes of automatic installing). Total: one coffee ☕.

### Step 1 — Install Termux (the agent's home)

Download **Termux from F-Droid**: **[f-droid.org/packages/com.termux](https://f-droid.org/en/packages/com.termux/)**

> [!WARNING]
> Only the **F-Droid** version works. The Play Store version is abandoned — do not use it.

### Step 2 — Let this app talk to Termux

Open Termux once and paste this single command (this is the **only** command you'll ever type):

```bash
mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

Then **close Termux completely** (Settings → Apps → Termux → Force stop).
This is a one-time Android security switch — without it, no app is allowed to send commands to Termux.

### Step 3 — Install this app

Grab the APK: **[⬇ Download latest](https://github.com/traveler3022/Hermes2/releases/tag/debug-latest)** → open it → allow "install from unknown sources" → install.

### Step 4 — Tap Install and watch it go 🍿

1. Open **Hermes2** → follow the onboarding to **Runtime Setup**
2. Tap **Install Hermes Agent**
3. Android asks you to allow the `RUN_COMMAND` permission → **Allow**
4. That's it. Sit back — the app now does **everything** automatically:

```
What the app does for you, stage by stage (live progress bar):
  ✓ downloads the official Hermes installer
  ✓ installs system packages (Python, compilers, …)
  ✓ builds and installs the agent (5–15 min — keep the screen on!)
  ✓ recovers automatically from common install failures
  ✓ writes a full log to ~/.hermes/logs/install.log — nothing is hidden
```

### Step 5 — Add your AI key & go

1. In the app: **Start Agent Gateway** → wait up to 30–90 s on first boot → **● Connected**
2. Set your model key (see [Model Setup](#-model-setup) below — Gemini has a free tier)
3. Say hi. 👋

> [!TIP]
> First connection stuck? Force-stop Termux once, reopen Hermes2, tap **Start Agent Gateway** again.
> The very first handshake sometimes needs a second try — after that it connects automatically every time you open the app.

---

## 🛡️ Privacy & Security — read this once

#### 🟢 Stays on your phone
- **Your API key** — stored in Hermes' config inside Termux. We have no server, no account, no telemetry.
- **App ↔ agent link** — runs over `127.0.0.1` (loopback). It physically cannot leave the device.

#### 🟠 Leaves your phone
- **Your messages to the AI** — they go to the model provider *you* chose (pick Gemini → Google sees them). That's how every AI API works. Don't send anything you wouldn't want the provider to read.

#### 🛡️ What the agent can touch
The agent runs commands **inside Termux's sandbox only**. On a non-rooted phone it *cannot* reach your photos, other apps' data, or system files. Sending a file to the agent is explicit: you pick it, the app uploads that one file over loopback.

> [!CAUTION]
> **Don't root your phone for this** (rooting removes the sandbox), and **keep tool approval on** — when unsure, hit Deny and ask the agent what it was trying to do.

---

## 🧠 Model Setup

Set the key once, inside Termux. Two low-cost options that work well on a phone:

<details open>
<summary><b>Option A — Xiaomi MiMo</b> · low-cost & fast</summary>

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
<summary><b>Option B — Google Gemini</b> · free tier available</summary>

<br>

Get a free key at [aistudio.google.com](https://aistudio.google.com), then in Termux: `nano ~/.hermes/.env` and add:

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

**💸 Controlling costs:** set a spending limit in your provider's dashboard (your best protection), start with a free/cheap model, and watch your first few sessions to get a feel for token usage.

---

## 🔧 Troubleshooting

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
<summary><b>Install fails (<code>jiter</code> / <code>pydantic-core</code> build error)</b></summary>

<br>

Set these in Termux **before** retrying the install, then tap Install again:

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

More depth: **[Complete technical guide](docs/RUNNING_ON_ANDROID_TERMUX.md)**

---

## 🏗️ Architecture

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

All UI and ViewModel code depends only on **interfaces** — swapping the Termux runtime for an embedded Python runtime *(ADR-009)* only touches the DI module. The agent link is JSON-RPC over a local WebSocket. Design docs: [`docs/`](docs/)

### Build from source

```bash
git clone https://github.com/traveler3022/Hermes2.git
cd Hermes2
bash ./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
bash ./gradlew :app:testDebugUnitTest    # unit tests
```

**Requires:** JDK 17 · Android SDK 35 · Android Studio Ladybug+

---

## 🤝 Contributing

Issues and PRs welcome. This is an independent, community port — not an official Nous Research product. When reporting a bug, include your **Android version**, **phone model**, and the relevant lines from `~/.hermes/logs/gateway_stdout.log`.

## 📄 License

**MIT** — see [LICENSE](LICENSE).

<sub>Independent project · not affiliated with or endorsed by Nous Research. "Hermes Agent" belongs to its respective authors.</sub>

<div align="center">
<br>

**⬡ Built for Android · Powered by Hermes Agent ⬡**

</div>
