<div align="center">

# ⬡ Hermes Pocket

### Native Android chat client for a self-hosted Hermes Agent 🤖📱


[![Download APK](https://img.shields.io/badge/⬇_Download_APK-latest-0EA5E9?style=flat-square&logo=android)](https://github.com/traveler3022/Hermes-Pocket/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-0EA5E9?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-0EA5E9?style=flat-square)](https://developer.android.com/jetpack/compose)
[![Min API](https://img.shields.io/badge/minSdk-29_(Android_10)-0EA5E9?style=flat-square)](https://developer.android.com/about/versions/android-10)

**Your AI agent, in your pocket · No cloud middleman · No account · Your VPS, your keys, your data**

English · [فارسی](README.fa.md)

</div>

---

## About

[Hermes Agent](https://github.com/NousResearch/hermes_agent) is an open-source AI agent — built by [Nous Research](https://nousresearch.com) — that runs on your own server or VPS. It can execute shell commands, edit files, browse the web, call APIs, and act on your behalf.

**Hermes Pocket is the Android client for it.** A native Jetpack Compose app that connects to your Hermes gateway over a secure WebSocket. No AI runs on your phone — it's purely a remote client.

| Component | Role | Where it runs |
|---|---|---|
| 🧠 **Hermes Agent** | The AI agent | Your server/VPS, behind a TLS proxy |
| 📱 **Hermes Pocket** | Native Android chat client | Your phone, over Wi-Fi or mobile data |

> [!IMPORTANT]
> You need a Hermes Agent server running before this app is useful. See [Hermes Agent](https://github.com/NousResearch/hermes_agent) for server setup.

---

## Screenshots

<div align="center">
  <img src="screenshots/chat-screen.jpg" width="30%" alt="💬 Chat" title="💬 Chat"/>
  <img src="screenshots/provider-models.jpg" width="30%" alt="🧠 Model picker" title="🧠 Model picker"/>
  <img src="screenshots/sessions-list.jpg" width="30%" alt="🗂️ Sessions" title="🗂️ Sessions"/>
  <br>
  <img src="screenshots/control-center.jpg" width="30%" alt="🎛️ Control Center" title="🎛️ Control Center"/>
  <img src="screenshots/task-desk.jpg" width="30%" alt="🤖 Task Desk" title="🤖 Task Desk"/>
  <img src="screenshots/personalization.jpg" width="30%" alt="🎨 Personalization" title="🎨 Personalization"/>
</div>

---

## Features

### 💬 Chat experience
- **Streaming replies** — token-by-token, with the user's question pinned to the top of the viewport (Gemini / ChatGPT mobile pattern) and the reply streaming into the empty space below
- **Visible reasoning** — collapsible "thinking" block with live emotive markers the agent emits
- **Reasoning effort switch** — none → minimal → low → medium → high → xhigh → max, changeable mid-session
- **Tool-call cards** — terminal-style blocks for shell commands, with args + result, expandable
- **Sub-agent cards** — separate visual treatment for spawned sub-agents
- **Slash commands** — type `/` for shortcuts the server exposes
- **Steer mid-turn** — redirect the agent while it's replying
- **Branch conversation** — fork from any point in history
- **Retry / edit** — regenerate the last reply, or edit your last question

### 📎 Attachments & media
- Send files & images from your phone to the agent
- Inline image rendering in replies, with fullscreen viewer
- Code blocks with syntax highlighting + one-tap copy
- Mermaid diagram rendering
- Downloadable artifacts (PDFs, videos, files) — routed through the gateway HTTP client so they work on self-hosted `http://` setups where the system DownloadManager would silently fail

### 🗂️ Sessions & projects
- Search, pin, rename, branch, and resume any past conversation
- Auto-resume of the last session on reconnect
- Draft auto-save (debounced)
- Live history sync with the server
- **Multiple sessions at once** — each session's event stream is isolated, so you can juggle several chats
- **Project-linked sessions** — spin up a session tied to a project straight from the Projects screen

### 🤖 Delegation & tasks (Task Desk)
- **Task Desk** — a dedicated workspace for delegated work: run tasks, watch history, view results, re-run
- **Per-task model picker** — give each task its own model instead of the global default
- **Routine templates** — save reusable task recipes
- **Proactive completion notifications** — get pinged when a background task finishes (via WorkManager, zero-config delivery)

### 🎨 Personalization
- **Top bar identity** — show the assistant's name OR a custom avatar image (your choice)
- **Custom assistant name** — rename the assistant (default "Hermes")
- **Custom avatar** — upload any image; adjustable size 28-48dp
- **6 color themes** — Hermes, Blue Eye, Mocha, Midnight, Indigo, Carbon (light + dark each)
- **Font family** — Vazirmatn (bundled, Persian-shaped) or system font
- **Font size slider** — 80% to 140% scaling across the whole app
- **Warm / night mode** — amber tint for long sessions (f.lux-style)
- **Reduce motion** — respects the OS accessibility setting
- **SOUL.md** — edit the agent's persistent identity directly from the app
- **Personality presets** — switch between presets

### 🧠 Model & provider management
- Add custom OpenAI-compatible providers (base URL + API key)
- Auto-detect available models on provider add
- **"All providers"** view — every model from every provider in one searchable list
- **Active-model hero card** — see and switch your current model at a glance
- **Cross-provider model search** — find any model across all providers
- Fallback chain — pick an ordering; auto-failover across providers on errors
- One-tap live model switch on the active session

### 🛠️ Agent control
- **Tool toggles** — enable/disable tool categories live on the current session
- **Plugins manager** — see installed Hermes plugins, enable/disable
- **Skills catalog** — browse the agent's available skills
- **Cron jobs** — human-readable schedules, last-run status, and presets
- **Control Center hub** — Agent Behavior + Advanced settings in one place
- **Command approval** — manual / smart / off modes for risky actions
- **Platforms** — connect the agent to Telegram, Discord, or Slack bot tokens
- **Memory** — view and edit the agent's persistent memory
- **Environment variables** — manage the agent's env (under Settings > Advanced)

### 🌐 Internationalization
- Full **English + فارسی** support
- Full RTL layout
- Language override independent of device locale

---

## Quick start

1. **Have a Hermes Agent server running** — `hermes dashboard --host 127.0.0.1 --port <port>` behind a TLS reverse proxy, with `HERMES_DASHBOARD_SESSION_TOKEN` set. → [📡 VPS setup guide](docs/VPS_SETUP.md)

2. **Install the app** — [⬇ Download the latest APK](https://github.com/traveler3022/Hermes-Pocket/releases/latest)

3. **Connect** — open the app, go to **Runtime**, enter your server address and session token, tap **Save & Connect**.

---

## Build from source

```bash
git clone https://github.com/traveler3022/Hermes-Pocket.git
cd Hermes-Pocket
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

**Requirements:** JDK 17 · Android SDK 35 · Android Studio Ladybug+

---

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                       Android phone                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  UI (Jetpack Compose, Material 3)                    │  │
│  │  ├── ChatScreen   ConfigScreen   SessionsScreen      │  │
│  │  ├── CronScreen   PluginsScreen  SkillsScreen        │  │
│  │  └── ProjectsScreen  TaskDeskScreen                   │  │
│  │                       │                              │  │
│  │                       ▼                              │  │
│  │  ViewModel (Hilt, StateFlow)                         │  │
│  │  ├── ChatViewModel    ConfigViewModel                │  │
│  │  ├── SessionsViewModel CronViewModel                 │  │
│  │  └── ProjectsViewModel TaskDeskViewModel             │  │
│  │                       │                              │  │
│  │                       ▼                              │  │
│  │  GatewayClient (interface)                           │  │
│  │                       │                              │  │
│  │                       ▼                              │  │
│  │  OkHttpGatewayClient (impl)                          │  │
│  │  └── JSON-RPC over WebSocket (wss://)                │  │
│  └──────────────────────┬───────────────────────────────┘  │
└─────────────────────────┼──────────────────────────────────┘
                          │
                          ▼
              ┌────────────────────────┐
              │  Your Hermes Agent     │
              │  gateway (VPS/server)  │
              │  ┌──────────────────┐  │
              │  │  hermes dashboard│  │
              │  │  behind TLS proxy│  │
              └────────────────────────┘
```

All UI and ViewModel code depends only on the `GatewayClient` interface — swapping the implementation (e.g. for tests) is one Hilt module change. See the [VPS setup guide](docs/VPS_SETUP.md) to run your own Hermes Agent server.

---

## Requirements

| | Minimum | Recommended |
|---|---|---|
| Android | 10 (API 29) | 13+ (API 33+) |
| Server | Hermes gateway reachable over `wss://` | Same, behind a TLS reverse proxy |
| Network | Any internet connection | Stable connection for streaming |

---

## FAQ

<details>
<summary><b>Does this app run an AI on my phone?</b></summary>
No. The app is purely a client. The AI runs on your server.
</details>

<details>
<summary><b>Can I use this with a different AI agent?</b></summary>
The app speaks the Hermes gateway protocol. Any server implementing the same protocol would work, but it's designed and tested against Hermes Agent.
</details>

<details>
<summary><b>What is Task Desk?</b></summary>
A workspace for delegated work — run tasks in the background with their own model and effort level, then review results.
</details>

<details>
<summary><b>Why does the app need a foreground service?</b></summary>
To keep the WebSocket connection alive when backgrounded (for approval notifications). Battery exemption is optional.
</details>

<details>
<summary><b>Where are my chat sessions stored?</b></summary>
On your server, not your phone. The app syncs session history on demand.
</details>

<details>
<summary><b>Is this an official Nous Research product?</b></summary>
No. This is an independent community project. "Hermes Agent" belongs to its respective authors at Nous Research.
</details>

---

## Contributing

Issues and PRs welcome. This is an independent client project — not an official Nous Research product. When reporting bugs, please include: Android version, phone model, whether the issue is client-side or server-side, steps to reproduce, and logs if available (logcat tag: `Hermes`).

```bash
git checkout -b your-feature main
git commit -m "feat(area): description"
git push -u origin your-feature
```

---

## License

**MIT** — see [LICENSE](LICENSE).

---

<div align="center">
<sub>Independent community project · not affiliated with Nous Research · "Hermes Agent" belongs to its respective authors.</sub>
<br><br>
<b>⬡ Hermes Pocket · Your AI agent, in your pocket ⬡</b>
</div>
