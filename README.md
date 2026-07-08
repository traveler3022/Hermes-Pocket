<div align="center">

# ⬡ Hermes Android

### A native Android client for your own Hermes Agent 🤖📱

**This app does not run an AI on your phone.** It connects — over a secure WebSocket — to a
[Hermes Agent](https://github.com/NousResearch/hermes-agent) gateway running on **your own server or VPS**,
and gives you a full Material 3 chat interface for it: streaming replies, tool-call cards, file/image
attachments, session history, and deep control over the agent's models, tools, plugins, and scheduled jobs.

<br>

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-Install_now-0EA5E9?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/hermes-android-vps-/releases/tag/debug-latest)

[![Build](https://github.com/traveler3022/hermes-android-vps-/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/hermes-android-vps-/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-0EA5E9?style=flat-square)](LICENSE)
![Material 3](https://img.shields.io/badge/Material_3-0EA5E9?style=flat-square&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)

**No cloud middleman · No account · Your server, your keys, your data**

</div>

---

## 🤔 What is this?

You run [Hermes Agent](https://github.com/NousResearch/hermes-agent) — an open-source AI agent that can run
commands, edit files, browse the web, and act — on a server or VPS you control. This app is the phone in your
pocket for it: a proper native chat client instead of SSH-ing in from a terminal app every time you want to
talk to your agent.

| Piece | What it is | Where it runs |
|---|---|---|
| **Hermes Agent** 🧠 | The AI agent itself (open-source, by [Nous Research](https://nousresearch.com)) | Your own server/VPS, via `hermes dashboard` behind a TLS reverse proxy |
| **Hermes Android** 📱 *(this app)* | A native Material 3 chat client that connects to it over a WebSocket | A normal Android app, connects from anywhere |

The app talks to your server's `hermes dashboard` WebSocket endpoint (`wss://your-server/api/ws`) using a
session token you set up once. Nothing about the agent itself lives on the phone — the app is purely a
client, so it works the same over Wi-Fi or mobile data, from anywhere.

> [!IMPORTANT]
> **You need a Hermes Agent server already running somewhere** before this app is useful — this repo is the
> Android client only. See [Hermes Agent](https://github.com/NousResearch/hermes-agent) for server setup.

---

## ✨ What can it do?

- 💬 **Live chat** — streaming replies, visible reasoning/thinking (with a quick reasoning-effort switch:
  none → minimal → low → medium → high → xhigh → max), tool-call cards, sub-agent cards
- 📎 **Attachments** — send files & images to the agent from your phone; inline image rendering, code
  blocks, Mermaid diagrams, and downloadable artifacts in replies
- 🗂️ **Sessions** — search, pin, rename, branch, and resume any past conversation
- 🎨 **Personalization** — rename the assistant, upload a custom avatar image, pick from 6 color themes
  (light/dark), and customize the agent's personality preset + persistent identity (`SOUL.md`)
- 🧠 **Model & provider management** — add custom OpenAI-compatible providers, auto-detect their available
  models, build a fallback chain, and one-tap auto-failover across every configured provider
- 🛠️ **Tool control** — toggle which tool categories the agent can use, live on the current session
- 🔌 **Plugins manager** — see installed Hermes plugins and enable/disable them
- ⏰ **Scheduled jobs (Cron)** — view and manage the agent's scheduled tasks
- 🧩 **Skills catalog** — browse the agent's available skills
- 🤝 **Platform bots** — connect the agent to Telegram, Discord, or Slack bot tokens
- ✅ **Command approval** — manual / smart / off approval modes for risky actions, matching the server's
  `approvals.mode`
- 🌐 **English + فارسی**, full RTL support

---

## ⚡ Setup

### 1 — Have a Hermes Agent server running

You need `hermes dashboard --host 127.0.0.1 --port <port>` running on your server, behind a TLS reverse
proxy (Caddy, nginx, etc.) so the app can reach it as `wss://your-domain:port`. Set a session token with
`HERMES_DASHBOARD_SESSION_TOKEN` — this is what the app authenticates with.

This part is entirely server-side and out of scope for this repo; see the
[Hermes Agent](https://github.com/NousResearch/hermes-agent) project for how to install and run the gateway
itself.

### 2 — Install the app

**[⬇ Download the latest debug build](https://github.com/traveler3022/hermes-android-vps-/releases/tag/debug-latest)**
→ open the APK → allow "install from unknown sources" → install.

### 3 — Connect

Open the app → **Runtime** screen → enter your server's address and session token → **Save & Connect**.
That's it — from then on the app reconnects automatically every time you open it.

---

## 🛡️ Privacy & Security

- **Your session token** and server address are stored locally in the app, never sent anywhere except your
  own server.
- **The connection** is a WebSocket over TLS (`wss://`) straight to the server you configured — no
  intermediary service.
- **What the agent can touch** depends entirely on your server-side setup and `approvals.mode` — this app
  just renders what the agent reports and forwards your approve/deny decisions.

> [!CAUTION]
> Keep your session token private — anyone with it can control your agent. Treat it like a password.

---

## 🏗️ Architecture

```
UI (Compose) ─► ViewModel ─► GatewayClient (interface)
                                  │ Hilt DI
                                  ▼
                         OkHttpGatewayClient (impl)
                                  │ JSON-RPC over WebSocket (wss://)
                                  ▼
                         Hermes Agent gateway (your server)
```

All UI and ViewModel code depends only on the `GatewayClient` interface. Design docs: [`docs/`](docs/)

### Build from source

```bash
git clone https://github.com/traveler3022/hermes-android-vps-.git
cd hermes-android-vps-
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

**Requires:** JDK 17 · Android SDK 35 · Android Studio Ladybug+

### Signed release builds

Release signing is already wired up (`app/build.gradle.kts` + `.github/workflows/release.yml`) — it just
needs your keystore. Add these four repository secrets (Settings → Secrets and variables → Actions):

```
KEYSTORE_BASE64    base64 of your .keystore/.jks file
KEYSTORE_PASSWORD  store password
KEY_ALIAS          key alias
KEY_PASSWORD       key password
```

Then cut a release by pushing a version tag:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

CI builds a signed release APK and publishes it as a GitHub Release automatically. Locally, the same signing
config picks up a gitignored `keystore.properties` at the repo root (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`) — without either source, `assembleRelease` still produces an unsigned APK, so the
build never breaks for contributors who don't have the keystore.

---

## 🤝 Contributing

Issues and PRs welcome. This is an independent client project — not an official Nous Research product. When
reporting bugs, include your **Android version**, **phone model**, and whether the issue is client-side (this
app) or server-side (your Hermes gateway).

## 📄 License

**MIT** — see [LICENSE](LICENSE).

<sub>Independent project · not affiliated with Nous Research · "Hermes Agent" belongs to its respective authors.</sub>

<p align="center">
  <br>
  <b>⬡ Built for Android · Powered by Hermes Agent ⬡</b>
</p>
