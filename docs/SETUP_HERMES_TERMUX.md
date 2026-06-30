<div dir="rtl">

# راهاندازی اولیه Hermes در Termux

بعد از [نصب هرمس](INSTALL_HERMES_TERMUX.md)، اولین اجرای `hermes` یک ویزارد راهاندازی نشانت میدهد. این راهنما هر صفحه و گزینه را توضیح میدهد.

</div>

---

## Step 1 — Select terminal backend

<div dir="rtl">

اولین سوال: کدوم محیط اجرای دستور را میخواهی؟

</div>

```
Select terminal backend:
↑↓ navigate  ENTER/SPACE select  ESC cancel

→ (0) Local - run directly on this machine (default)
  (0) Docker - isolated container with configurable resources
  (0) Modal - serverless cloud sandbox
  (0) SSH - run on a remote machine
  (0) Daytona - persistent cloud development environment
  (●) Keep current (local)
```

<div dir="rtl">

| گزینه | توضیح |
|---|---|
| **Local** | دستورات مستقیم روی Termux اجرا میشوند. **این گزینه برای اندروید درست است.** |
| **Docker** | اجرای دستورات در کانتینر Docker (اندروید پشتیبانی نمیکند) |
| **Modal** | سرورلس ابری (نیاز به حساب Modal دارد) |
| **SSH** | اتصال به ماشین راه دور |
| **Daytona** | محیط توسعهی ابری پایدار |
| **Keep current** | همان تنظیمات فعلی را نگه دار |

**→ اندروید: `Local` را انتخاب کن.** فشار `ENTER`.

---

## Step 2 — How would you like to set up Hermes?

</div>

```
How would you like to set up Hermes?
↑↓ navigate  ENTER/SPACE select  ESC cancel

  ( ) Quick Setup (Nous Portal) – free OAuth login, no API keys, mode...
● ( ) Full setup – configure every provider, tool & option yourself (...
  ( ) Blank Slate – everything off except the bare minimum; opt in to...
```

<div dir="rtl">

| گزینه | توضیح |
|---|---|
| **Quick Setup** | ورود با OAuth از طریق Nous Portal. بدون نیاز به کلید API. مدلها و ابزارهای پیشفرض فعال میشوند. **مناسب مبتدیها.** |
| **Full Setup** | همهچیز دستی تنظیم میشود: ارائهدهنده، ابزار، پلاگین. **برای کاربران حرفهای.** |
| **Blank Slate** | همهچیز خاموش بهجز حداقلها. بعداً خودت هر چیزی که لازم داری فعال میکنی. **برای کنترل کامل.** |

**→ اگر میخوای همهچیز خودت تنظیم کنی: `Full Setup`**
**→ اگر تازهکاری: `Quick Setup`**

---

## Step 3 — Select provider

<div dir="rtl">

انتخاب ارائهدهندهی مدل هوش مصنوعی:

</div>

```
Select provider:
↓↑ navigate  ENTER/SPACE select  ESC cancel

→ Nous Portal
  OpenRouter
  Mixture of Agents
  NovitaAI
  LM Studio
  Anthropic
  OpenAI
  Qwen Cloud / DashScope
  xAI Grok
  Xiaomi MiMo
  Tencent TokenHub
  NVIDIA NIM
  GitHub Copilot
  Hugging Face Inference Providers
  Google AI Studio
  DeepSeek
  Z.AI / GLM
  Kimi / Moonshot
  StepFun Step Plan
  MiniMax
  Ollama Cloud
  Arcee AI
  GMI Cloud
  Kilo Code
  OpenCode
  AWS Bedrock
  Azure Foundry
  ...
  Custom endpoint (enter URL manually)
  Leave unchanged
```

<div dir="rtl">

گزینههای مهم:

| ارائهدهنده | توضیح |
|---|---|
| **Xiaomi MiMo** | ارزان و سریع، مناسب اندروید. نیاز به `XIAOMI_API_KEY` |
| **Google AI Studio** | Gemini مستقیم. نیاز به `GEMINI_API_KEY` |
| **OpenRouter** | آگریگیتور pay-per-use، صدها مدل |
| **Anthropic** | مدل Claude. نیاز به `ANTHROPIC_API_KEY` |
| **OpenAI** | GPT-4o و غیره. نیاز به `OPENAI_API_KEY` |
| **DeepSeek** | ارزان و قوی برای کدنویسی |
| **Nous Portal** | ورود OAuth، ۳۰۰+ مدل |
| **Custom endpoint** | URL دستی برای هر API سازگار با OpenAI |
| **Leave unchanged** | تغییر نده |

**→ برای اندروید: `Xiaomi MiMo` یا `Google AI Studio` پیشنهاد میشود.**

بعد از انتخاب، احتمالاً ازت کلید API میخواهد (مگر اینکه Nous Portal یا OAuth انتخاب کرده باشی).

---

## Step 4 — Select platforms to configure

<div dir="rtl">

انتخاب پلتفرمهای پیامرسان برای اتصال:

</div>

```
Select platforms to configure:
↑↓ navigate  SPACE toggle  ENTER confirm  ESC cancel

[ ] Mattermost
[ ] Signal
[ ] Weixin / WeChat
[ ] BlueBubbles (iMessage)
[ ] QQ Bot
[ ] Yuanbao
[ ] DingTalk
[ ] Discord
[ ] Email
[ ] Feishu / Lark
[ ] Google Chat
[ ] Home Assistant
[ ] IRC
[ ] LINE
[ ] Matrix
[ ] ntfy
[ ] iMessage via Photon
[ ] Raft
[ ] SimpleX Chat
[ ] Slack
[ ] SMS (Twilio)
[ ] Microsoft Teams
[ ] Telegram
[ ] WeCom (Enterprise WeChat)
[ ] WhatsApp
```

<div dir="rtl">

| کنترل | عمل |
|---|---|
| `↑↓` | حرکت بین گزینهها |
| `SPACE` | تیک زدن / برداشتن تیک |
| `ENTER` | تأیید انتخابها |
| `ESC` | رد شدن |

**→ برای شروع، لازم نیست پلتفرمی انتخاب کنی.** بعداً از داخل اپ Hermes2 هم میتوانی تنظیم کنی. `ESC` بزن تا رد شوی یا فقط `ENTER` بدون انتخاب.

---

## Step 5 — Tools for CLI

<div dir="rtl">

فعال/غیرفعالکردن ابزارهای ایجنت:

</div>

```
Tools for 📱 CLI
↑↓ navigate  SPACE toggle  ENTER confirm  ESC cancel

→ [x] 🔍 Web Search & Scraping (web_search, web_extract)
  [ ] 🌐 Browser Automation (navigate, click, type, scroll)
  [ ] 💻 Terminal & Processes (terminal, process)
  [ ] 📁 File Operations (read, write, patch, search)
  [ ] ⚡ Code Execution (execute_code)
  [ ] 🔬 Vision / Image Analysis (vision_analyze)
  [ ] 🎥 Video Analysis (video_analyze)
  [ ] 🎨 Image Generation (image_generate)
  [ ] 🎬 Video Generation (video_generate)
  [ ] 🐦 X (Twitter) Search (x_search)
  [ ] 🔊 Text-to-Speech (text_to_speech)
  [ ] 📚 Skills (list, view, manage)
  [ ] 📝 Task Planning (todo)
  [ ] 🧠 Memory (persistent memory across sessions)
  [ ] 🔄 Context Engine (runtime tools)
  [ ] 🔍 Session Search (search past conversations)
  [ ] ❓ Clarifying Questions (clarify)
  [ ] 📤 Task Delegation (delegate_task)
  [ ] 🕒 Cron Jobs (create/list/update/pause/resume/run)
  [ ] 🏠 Home Assistant (smart home)
  [ ] 🎵 Spotify (playback, search, playlists)
  [ ] 💬 Yuanbao (group info, member queries)
  [ ] 💻 Computer Use (macOS/Windows/Linux)
```

<div dir="rtl">

ابزارهای پیشنهادی برای شروع روی اندروید:

| ابزار | فعال؟ | چرا |
|---|---|---|
| **Web Search** | ✅ | جستوجوی وب — خیلی کاربردی |
| **Terminal & Processes** | ✅ | اجرای دستور — هستهی اصلی |
| **File Operations** | ✅ | مدیریت فایل — ضروری |
| **Code Execution** | ✅ | اجرای کد — قابلیت کلیدی |
| **Memory** | ✅ | حافظهی پایدار بین سشنها |
| **Session Search** | ✅ | جستوجوی تاریخچه |
| **Skills** | ✅ | مدیریت مهارتها |
| **Task Planning** | ✅ | لیست کارها |
| **Clarifying Questions** | ✅ | سؤال برای شفافسازی |
| **Browser Automation** | ❌ | اندروید Tier 2 — پشتیبانی نمیشود |
| **Image/Video Gen** | ❌ | نیاز به مدل خاص + GPU |
| **Spotify/Home Assistant** | ❌ | نیاز به تنظیمات اضافی |

**→ ابزارهای بالا را با `SPACE` فعال کن، بعد `ENTER` بزن.**

---

## Step 6 — Select Search Provider

<div dir="rtl">

انتخاب موتور جستوجوی وب:

</div>

```
Select Search Provider:
↑↓ navigate  ENTER/SPACE select  ESC cancel

  (0) Nous Subscription [subscription]
  (0) Firecrawl Self-Hosted [free - self-hosted]
  (0) Brave Search (Free) [free] - 2k queries/mo
→ (0) DuckDuckGo (ddgs) [free - no key - search only]
  (0) Exa [paid]
  (0) Firecrawl [paid]
  (0) Parallel [paid]
  (0) SearXNG [free - self-hosted]
  (0) Tavily [paid]
  (0) xAI Web Search (Grok) [paid]
  (0) Skip - keep defaults / configure later
```

<div dir="rtl">

| ارائهدهنده | هزینه | نیاز به کلید | توضیح |
|---|---|---|---|
| **DuckDuckGo** | رایگان | ❌ | بدون کلید، فقط جستوجو (بدون استخراج محتوا). **بهترین گزینه برای شروع.** |
| **Brave Search** | رایگان | ✅ | ۲۰۰۰ پرسوجو در ماه |
| **Nous Subscription** | اشتراکی | ✅ | Firecrawl مدیریتشده |
| **SearXNG** | رایگان | ❌ | خودمیزبان، نیاز به سرور |
| **Tavily** | پولی | ✅ | جستوجو + استخراج |
| **Firecrawl** | پولی | ✅ | جستوجو + استخراج کامل |
| **xAI Grok** | پولی | ✅ | جستوجوی هوشمند |
| **Skip** | — | — | بعداً تنظیم کن |

**→ برای شروع: `DuckDuckGo` — رایگان، بدون کلید، فوراً کار میکند.**

---

## خلاصهی گزینههای پیشنهادی برای اندروید

<div dir="rtl">

| مرحله | گزینه |
|---|---|
| Terminal backend | **Local** |
| Setup mode | **Full Setup** (یا Quick Setup اگر تازهکاری) |
| Provider | **Xiaomi MiMo** یا **Google AI Studio** |
| Platforms | **ESC** — بعداً از اپ تنظیم کن |
| Tools | **Web Search, Terminal, File, Code, Memory, Skills, Session Search, Task Planning, Clarify** |
| Search Provider | **DuckDuckGo** (رایگان، بدون کلید) |

---

**→ بعد: [نصب APK و اتصال Gateway](GATEWAY_SETUP.md)**

**← بازگشت: [نصب هرمس](INSTALL_HERMES_TERMUX.md)** · **[README اصلی](../README.md)**
</div>
