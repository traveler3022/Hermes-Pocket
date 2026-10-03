<h1 align="center">⬡ Hermes</h1>

<p align="center">
  <strong>Your AI agent, living on your phone.</strong><br>
  A native Android home for <a href="https://github.com/NousResearch/hermes-agent">Hermes Agent</a> — no server, no computer, nothing else to install.
</p>

<p align="center">
  <a href="README.fa.md">فارسی</a> •
  <a href="#-core-features">Features</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-build-from-source">Build</a>
</p>

<p align="center">
  <a href="https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest"><img alt="Download APK" src="https://img.shields.io/badge/⬇_Download_APK-6750A4?style=for-the-badge&logo=android&logoColor=white"></a>
  <br>
  <a href="https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml"><img alt="Build" src="https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml/badge.svg"></a>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <a href="LICENSE"><img alt="GPLv3" src="https://img.shields.io/badge/License-GPLv3-00BCD4?style=flat-square"></a>
</p>

<p align="center">
  <table>
    <tr>
      <td><img src="screenshots/chat.jpg" width="280"></td>
      <td><img src="screenshots/control-center.jpg" width="280"></td>
    </tr>
  </table>
</p>

---

## ⬡ Hermes

**Hermes** puts a real AI agent in your pocket. It doesn't just chat — it runs commands, writes and
executes code, manages files, browses the web and runs scheduled jobs, all on the phone itself.
Install the app, pick your AI provider, and start talking.

The only thing that leaves your phone is the request to the AI provider you chose. Your chats,
files and API key stay on the device — no account, no telemetry, no server of ours.

## ✨ Core Features

- **Polished chat**: Streaming replies, visible reasoning and tool-call cards; Markdown, tables and Persian text rendered the way they read.
- **An agent that acts**: Hermes runs commands, edits files and executes code — and asks for your Approve / Deny before anything risky.
- **Built-in Linux**: A Linux environment ships inside the app and installs itself on first launch, so tools work out of the box. Termux is optional, never required.
- **Browser & desktop**: The agent drives its own Chromium on a virtual desktop; watch it live in the app and take over whenever you want.
- **Terminal & files**: A full terminal, and the agent's files right in Android's Files app and file picker.
- **Scheduled jobs**: Tell it *when* a task should run — no cron syntax.
- **Sessions & Control Center**: Search, pin and resume chats; manage providers, models, memory, skills, tools and plugins.
- **Any provider**: OpenRouter, Anthropic, OpenAI, Gemini, DeepSeek, Xiaomi MiMo, Z.AI / GLM, Kimi, or any OpenAI-compatible endpoint.
- **English + فارسی**, light and dark themes, and it keeps working in the background.

## 🚀 Quick Start

1. **[Download the APK](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest)** and install it.
2. Open Hermes → **Get started** → **Built-in Linux** → **Install Hermes**. Keep the app open while it sets itself up (needs ~1 GB free and a stable connection, once).
3. Choose your AI provider, paste the API key, pick a model.
4. Say hi. 👋

**Needs** Android 10+ on an arm64 or x86_64 device.

> [!CAUTION]
> Hermes runs real commands. Keep tool approval on, and never share your API key in screenshots or issues.

<details>
<summary><b>Troubleshooting</b></summary>

- **Install stopped or failed** — free up space (≥ 1 GB), check your connection, tap Install again.
- **Agent stops when the screen is off** — Settings → Apps → Hermes → Battery → **Unrestricted**.
- **Reporting a bug** — include your Android version, phone model and the install/connection log.
</details>

## 🛠 Build from Source

```bash
git clone https://github.com/traveler3022/Hermes-Pocket.git
cd Hermes-Pocket
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

Needs JDK 17 and Android SDK 35. Kotlin · Jetpack Compose · Material 3 · Hilt · OkHttp.
Every push is built on GitHub Actions and published to the [`debug-latest`](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest) release.

---

## 📄 License

**GPLv3** with additional terms (keep the author attribution) — see [LICENSE](LICENSE) and [ADDITIONAL_TERMS.md](ADDITIONAL_TERMS.md).

<p align="center">
  <sub>Independent community project · not affiliated with Nous Research · "Hermes Agent" belongs to its authors.</sub>
</p>
