<h1 align="center">⬡ Hermes</h1>

<p align="center">
  <strong>Your AI agent, living on your phone.</strong><br>
  A native Android home for <a href="https://github.com/NousResearch/hermes-agent">Hermes Agent</a>: run it inside the app, in Termux, or on your own server.
</p>

<p align="center">
  <a href="README.fa.md">فارسی</a> •
  <a href="#-features">Features</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-build-from-source">Build</a>
</p>

<p align="center">
  <a href="https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest"><img alt="Download APK" src="https://img.shields.io/badge/⬇_Download_APK-0EA5E9?style=for-the-badge&logo=android&logoColor=white"></a>
  <br>
  <a href="https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml"><img alt="Build" src="https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml/badge.svg"></a>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <a href="LICENSE"><img alt="GPLv3" src="https://img.shields.io/badge/License-GPLv3-00BCD4?style=flat-square"></a>
</p>

<p align="center">
  <table>
    <tr>
      <td><img src="screenshots/chat-en.png" width="200"></td>
      <td><img src="screenshots/drawer-en.png" width="200"></td>
      <td><img src="screenshots/control-center-en.png" width="200"></td>
      <td><img src="screenshots/runtime-en.png" width="200"></td>
    </tr>
  </table>
</p>

---

## ⬡ Hermes

**Hermes** puts a real AI agent in your pocket. It doesn't just chat: it runs commands, writes and
runs code, manages files, browses the web and runs jobs on a schedule.

You pick where the agent itself runs, and you can switch later from the top card of **Control Center**:

| | Where Hermes Agent runs | What you need |
|---|---|---|
| **🐧 Built-in Linux** *(recommended)* | Inside the app, on the phone | Nothing else. ~1 GB free, once |
| **📟 Termux** | In the separate Termux app on the phone | Termux installed |
| **🖥️ Server** | On your own Linux server, reached over Tailscale | A server + free Tailscale account → [server guide](server/README.md) |

In the two on-phone modes your chats, files and API key stay on the device, and the only request
that leaves it goes to the AI provider you chose. No account, no telemetry, no server of ours.

## ✨ Features

**Chat**
- Streaming replies with a folding *Thoughts* trace of the agent's reasoning and tool calls.
- Markdown, tables, code and Persian text rendered the way they read; images, files and inline HTML previews.
- Voice input, attachments, steer the agent mid-reply, edit or branch from any message.
- Approve / Deny before anything risky; choose how strict approval is in Control Center.

**Workspace** (the side drawer)
- **Task Desk**: fire off a task, leave, and come back to the result.
- **Agent**: profiles, personality (SOUL.md) and memory.
- **Scheduled**: tell it *when* a task should run, no cron syntax.
- **Group chats**: put 2–6 profiles in one room; they answer together and call each other with @name.
- **Kanban**: the tasks your profiles are working on.
- **Files**: the agent's files, also in Android's Files app and file picker.

**Control Center**
- **Models & providers**: OpenRouter, Anthropic, OpenAI, Gemini, DeepSeek, Xiaomi MiMo, Z.AI / GLM, Kimi or any OpenAI-compatible endpoint; API key or account sign-in; reasoning depth.
- **Tools**: toolsets, skills, plugins and MCP servers.
- **Linux**: a full terminal, packages, and a browser on a virtual desktop you can watch and take over.
- **Platforms**: reach your agent from Telegram, Discord or Slack.
- **Projects**, **Storage** cleaner, **Updates** for the app and the Hermes core.
- English + فارسی, seven color themes in light and dark, and it keeps working in the background.

## 🚀 Quick Start

1. **[Download the APK](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest)** and install it.
2. Open Hermes → **Get started** → pick where it runs:
   - **Built-in Linux** → **Install Hermes**. Keep the app open while it sets itself up (needs ~1 GB free and a stable connection, once).
   - **Termux** → follow the steps the app shows to install Hermes in Termux.
   - **Server** → set up the server with one command, then sign in from the app: see the [server guide](server/README.md).
3. Choose your AI provider, paste the API key (or sign in), pick a model.
4. Say hi. 👋

**Needs** Android 10+ on an arm64 or x86_64 device.

> [!CAUTION]
> Hermes runs real commands. Keep command approval on, and never share your API key in screenshots or issues.

<details>
<summary><b>Troubleshooting</b></summary>

- **Install stopped or failed**: free up space (≥ 1 GB), check your connection, tap Install again.
- **Agent stops when the screen is off**: Settings → Apps → Hermes → Battery → **Unrestricted**.
- **Can't reach the server**: the phone and the server must be signed in to the same Tailscale account; see the [server guide](server/README.md).
- **Reporting a bug**: include your Android version, phone model, which mode you use, and the install/connection log.
</details>

## 🛠 Build from Source

```bash
git clone https://github.com/traveler3022/Hermes-Pocket.git
cd Hermes-Pocket
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

Needs JDK 17 and Android SDK 36. Kotlin · Jetpack Compose · Material 3 · Hilt · OkHttp.
Every push is built on GitHub Actions and published to the [`debug-latest`](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest) release.
The screenshots above are rendered from the app's own UI code by the **README Screenshots** workflow.

---

## 📄 License

**GPLv3** with additional terms (keep the author attribution): see [LICENSE](LICENSE) and [ADDITIONAL_TERMS.md](ADDITIONAL_TERMS.md).

<p align="center">
  <sub>Independent community project · not affiliated with Nous Research · "Hermes Agent" belongs to its authors.</sub>
</p>
