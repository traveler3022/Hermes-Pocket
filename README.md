<div align="center">

# ⬡ Hermes Pocket

### Native Android chat client for a self-hosted Hermes Agent 🤖📱

[![Build](https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml)
[![Download APK](https://img.shields.io/badge/⬇_Debug_APK-latest-0EA5E9?style=flat-square&logo=android)](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-0EA5E9?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-0EA5E9?style=flat-square)](https://developer.android.com/jetpack/compose)
[![Min API](https://img.shields.io/badge/minSdk-29_(Android_10)-0EA5E9?style=flat-square)](https://developer.android.com/about/versions/android-10)

**Your AI agent, in your pocket · No cloud middleman · No account · Your VPS, your keys, your data**

</div>

---

## 📖 What is Hermes Pocket?

[Hermes Agent](https://github.com/NousResearch/hermes_agent) is an open-source AI agent — built by
[Nous Research](https://nousresearch.com) — that can run shell commands, edit files, browse the web, call
APIs, and act on your behalf. You run it on **your own server or VPS**; nothing about the agent itself
runs on your phone.

**Hermes Pocket is the Android client for it.** It's a native Jetpack Compose app that connects to your
Hermes gateway over a secure WebSocket and gives you a full chat interface — streaming replies, tool-call
cards, file/image attachments, session history, and deep control over the agent's models, tools, plugins,
and scheduled jobs. Carry your agent in your pocket, reach it from anywhere over Wi-Fi or mobile data.

| Piece | What it is | Where it runs |
|---|---|---|
| 🧠 **Hermes Agent** | The AI agent itself (open-source) | Your own server/VPS, via `hermes dashboard` behind a TLS reverse proxy |
| 📱 **Hermes Pocket** *(this app)* | A native Material 3 Android chat client | A normal Android app, connects from anywhere over Wi-Fi or mobile data |

> [!IMPORTANT]
> **You need a Hermes Agent server already running** before this app is useful — this repo is the Android
> client only. See [Hermes Agent](https://github.com/NousResearch/hermes_agent) for server setup.

---

## ✨ Features

### 💬 Chat experience
- **Streaming replies** — token-by-token, with the user's question pinned to the top of the viewport
  (Gemini / ChatGPT mobile pattern) and the reply streaming into the empty space below
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
- Downloadable artifacts (PDFs, videos, files) — routed through the gateway HTTP client so they work on
  self-hosted `http://` setups where the system DownloadManager would silently fail

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
- **Proactive completion notifications** — get pinged when a background task finishes (via WorkManager,
  zero-config delivery)
- 12 more RPCs wired up: changes/rollback, projects, delegation control, pdf/title-suggest, billing, pet,
  and more

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

## ⚡ Quick start

### 1. Have a Hermes Agent server running

Run `hermes dashboard --host 127.0.0.1 --port <port>` on your server, behind a TLS reverse proxy (Caddy,
nginx, etc.) so the app can reach it as `wss://your-domain:port`. Set a session token with
`HERMES_DASHBOARD_SESSION_TOKEN` — this is what the app authenticates with.

See [Hermes Agent](https://github.com/NousResearch/hermes_agent) for full server setup.

### 2. Install the app

[⬇ **Download the latest debug APK**](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest)
→ open the APK → allow "install from unknown sources" → install.

### 3. Connect

Open the app → **Runtime** screen → enter your server's address (`wss://your-domain:port`) and session
token → **Save & Connect**. From then on the app reconnects automatically every time you open it.

That's it — you're chatting with your agent.

---

## 🏗️ Architecture

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

All UI and ViewModel code depends only on the `GatewayClient` interface — swapping the implementation
(e.g. for tests) is one Hilt module change. Design docs and mockups live in [`docs/`](docs/).

### Tech stack

| Layer | Tech |
|---|---|
| UI | Jetpack Compose, Material 3 |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow + StateFlow |
| Networking | OkHttp WebSocket (JSON-RPC) |
| Image loading | Coil |
| Markdown | Markwon |
| Persistence | SharedPreferences (settings) + server-side (sessions, memory) |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 |
| Java | 17 |

### Project layout

```
app/src/main/java/com/hermes/android/
├── MainActivity.kt              # NavHost + theme wiring
├── HermesApplication.kt         # Hilt entry point
├── di/                          # Hilt modules
├── gateway/                     # GatewayClient interface + OkHttp impl
├── runtime/                     # Runtime/connection state
├── service/                     # Foreground service, approval notifications
├── ui/
│   ├── component/               # Reusable composables (HermesMarkdown, etc.)
│   ├── design/                  # Shared design system (colors, shapes, spacing)
│   ├── i18n/                    # t() translation function, AppLanguage
│   ├── screen/                  # All screens (Chat, Config, Sessions, etc.)
│   ├── theme/                   # ColorTheme, Typography, ThemeModeState
│   └── viewmodel/               # All ViewModels (incl. ConfigParsers/ConfigUiState/ConfigUtils)
└── ...
```

---

## 🛠️ Build from source

```bash
git clone https://github.com/traveler3022/Hermes-Pocket.git
cd Hermes-Pocket
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests (when present)
```

**Requirements:** JDK 17 · Android SDK 35 · Android Studio Ladybug+

### Signed release builds

Release signing is wired up via `app/build.gradle.kts` + `.github/workflows/release.yml`. Add these four
repository secrets (Settings → Secrets and variables → Actions):

```
KEYSTORE_BASE64    base64 of your .keystore/.jks file
KEYSTORE_PASSWORD  store password
KEY_ALIAS          key alias
KEY_PASSWORD       key password
```

Cut a release by pushing a version tag:

```bash
git tag v2.0.0 && git push origin v2.0.0
```

CI builds a signed release APK and publishes it as a GitHub Release automatically. Locally, the same
signing config picks up a gitignored `keystore.properties` at the repo root.

### Debug builds via CI

Every push to `main` triggers a debug APK build. The debug keystore is cached across CI runs, so each new
debug APK installs cleanly over the previous one without uninstalling first.

---

## 🛡️ Privacy & security

- **Your session token and server address** are stored locally in the app, never sent anywhere except your
  own server.
- **The connection** is a WebSocket over TLS (`wss://`) straight to your server — no intermediary service,
  no telemetry, no analytics.
- **What the agent can touch** depends entirely on your server-side setup and `approvals.mode` — this app
  just renders what the agent reports and forwards your approve/deny decisions.
- **No accounts, no cloud, no tracking.** The app has zero network dependencies beyond the server you
  configure.

> [!CAUTION]
> Keep your session token private — anyone with it can control your agent. Treat it like a password.

---

## 🤝 Contributing

Issues and PRs welcome. This is an independent client project — not an official Nous Research product. When
reporting bugs, please include:

- Android version
- Phone model
- Whether the issue is **client-side** (this app) or **server-side** (your Hermes gateway)
- Steps to reproduce
- Logs if available (the app logs to logcat under the `Hermes` tag)

### Development workflow

```bash
# Create a feature branch
git checkout -b your-feature main

# Make changes, commit with a clear message
git commit -m "feat(chat): add emoji reactions to messages"

# Push and open a PR
git push -u origin your-feature
```

---

## 📋 Requirements

| | Minimum | Recommended |
|---|---|---|
| Android version | 10 (API 29) | 13+ (API 33+) |
| Server | Hermes Agent gateway running, reachable over `wss://` | Same, behind a proper TLS reverse proxy |
| Network | Any internet connection | Stable connection for long streaming replies |

---

## ❓ FAQ

<details>
<summary><b>Does this app run an AI on my phone?</b></summary>

No. The app is purely a client. The AI agent runs on your server; this app just connects to it over
WebSocket and renders the conversation.
</details>

<details>
<summary><b>Can I use this with a different AI agent?</b></summary>

The app speaks the Hermes Agent gateway protocol (JSON-RPC over WebSocket). Any server implementing the
same protocol would work, but the app is designed and tested against Hermes Agent specifically.
</details>

<details>
<summary><b>What is Task Desk?</b></summary>

Task Desk is the home for delegated work. Instead of chatting back and forth, you hand the agent a task
(with its own model and effort level), and it runs in the background. When it's done, you get a
notification and can open the result sheet, re-run it, or save it as a routine template.
</details>

<details>
<summary><b>Why does the app need a foreground service / battery exemption?</b></summary>

To keep the WebSocket connection alive when the app is backgrounded (so you still get tool-approval
notifications, for example). The battery exemption is optional — without it, the connection may drop when
the phone sleeps, but the app still works while in the foreground.
</details>

<details>
<summary><b>Where are my chat sessions stored?</b></summary>

On **your server**, not your phone. The app syncs session history from the gateway on demand. Your phone
only keeps transient UI state (current draft, last-opened session id) and your settings.
</details>

<details>
<summary><b>Is this an official Nous Research product?</b></summary>

No. This is an independent community project. "Hermes Agent" belongs to its respective authors at Nous
Research. This repo is not affiliated with or endorsed by Nous Research.
</details>

---

## 📄 License

**MIT** — see [LICENSE](LICENSE).

---

<div align="center">

<sub>Independent community project · not affiliated with Nous Research · "Hermes Agent" belongs to its respective authors.</sub>

<br><br>

**⬡ Hermes Pocket · Your AI agent, in your pocket ⬡**

</div>
