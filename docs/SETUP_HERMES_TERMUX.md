<div dir="rtl">

# راهاندازی اولیه Hermes در Termux

بعد از [نصب هرمس](INSTALL_HERMES_TERMUX.md)، اولین اجرای `hermes` یک ویزارد راهاندازی نشانت میدهد. این راهنما هر صفحه و گزینه را توضیح میدهد.

</div>

---

## Step 1 — How would you like to set up Hermes?

<div dir="rtl">

اولین سوال: چطوری میخوای هرمس را تنظیم کنی؟

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
| **Quick Setup** | ورود با OAuth از طریق Nous Portal. بدون نیاز به کلید API. **مناسب مبتدیها.** |
| **Full Setup** | همهچیز دستی تنظیم میشود. **برای کاربران حرفهای.** |
| **Blank Slate** | همهچیز خاموش بهجز حداقلها. بعداً خودت فعال میکنی. |

**→ `Full Setup` را انتخاب کن.** `ENTER` بزن.

---

## Step 2 — Select provider

انتخاب ارائهدهندهی مدل هوش مصنوعی:

</div>

```
Select provider:
↓↑ navigate  ENTER/SPACE select  ESC cancel

→ Nous Portal
  OpenRouter
  Anthropic
  OpenAI
  Gemini
  Google AI Studio
  DeepSeek
  ...
  Custom endpoint (enter URL manually)
  Leave unchanged
```

<div dir="rtl">

| ارائهدهنده | توضیح |
|---|---|
| **Gemini** | ارزان و سریع، مناسب اندروید. نیاز به `GEMINI_API_KEY` |
| **Google AI Studio** | Gemini مستقیم. نیاز به `GEMINI_API_KEY` |
| **OpenRouter** | آگریگیتور pay-per-use، صدها مدل |
| **Nous Portal** | ورود OAuth، ۳۰۰+ مدل |

**→ برای اندروید: `Gemini` یا `Google AI Studio` پیشنهاد میشود.**

اگر ارائهدهندهای انتخاب کنی که کلید API لازم دارد، ویزارد ازت کلید میخواهد. قبلاً در [تنظیم اولیه](SETUP_HERMES_TERMUX.md) کلید را در `~/.hermes/.env` تنظیم کردهای.

`ENTER` بزن.

---

## Step 3 — Select terminal backend

<div dir="rtl">

**حتماً باید روی `Local` باشد.** این تنظیم برای اندروید ضروری است.

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
| **Docker** | کانتینر Docker (اندروید پشتیبانی نمیکند) |
| **Modal** | سرورلس ابری (نیاز به حساب Modal) |
| **SSH** | اتصال به ماشین راه دور |
| **Daytona** | محیط توسعهی ابری پایدار |
| **Keep current** | همان تنظیمات فعلی |

**→ حتماً `Local` را انتخاب کن.** `ENTER` بزن.

---

## Step 4 — Select platforms to configure

<div dir="rtl">

انتخاب پلتفرمهای پیامرسان. اینها فقط برای ربات تلگرام/دیسکورد/واتساپ و غیره لازماند. **فعلاً لازمشان نداری.**

</div>

```
Select platforms to configure:
↑↓ navigate  SPACE toggle  ENTER confirm  ESC cancel

[ ] Mattermost    [ ] Discord       [ ] Slack
[ ] Signal        [ ] Email         [ ] Telegram
[ ] WeChat        [ ] Google Chat   [ ] WhatsApp
[ ] LINE          [ ] Matrix        [ ] Microsoft Teams
... (25 پلتفرم)
```

<div dir="rtl">

**→ فقط `ENTER` بزن تا رد شوی.** بعداً از داخل اپ Hermes2 هم میتوانی تنظیم کنی.

---

## Step 5 — Tools for CLI (تنظیمات پیشنهادی)

ابزارهای ایجنت. با `SPACE` تیک بزن، بعد `ENTER` تأیید کن.

</div>

```
Tools for 📱 CLI
↑↓ navigate  SPACE toggle  ENTER confirm  ESC cancel

→ [x] 🔍 Web Search & Scraping (web_search, web_extract)
  [ ] 🌐 Browser Automation (navigate, click, type, scroll)
  [ ] 💻 Terminal & Processes (terminal, process)
  [ ] 📁 File Operations (read, write, patch, search)
  ...
```

<div dir="rtl">

**فعال کن** (روی اندروید کار میکنن):

</div>

```
[x] 🔍 Web Search & Scraping
[x] 💻 Terminal & Processes
[x] 📁 File Operations
[x] ⚡ Code Execution
[x] 🔊 Text-to-Speech
[x] 📚 Skills
[x] 📝 Task Planning
[x] 🧠 Memory
[x] 🔍 Session Search
[x] ❓ Clarifying Questions
[x] 📤 Task Delegation
[x] 🕒 Cron Jobs
```

<div dir="rtl">

**فعال نکن** (اندروید پشتیبانی نمیکنه یا نیاز به تنظیم اضافی داره):

</div>

```
[ ] 🌐 Browser Automation    — اندروید Tier 2
[ ] 💻 Computer Use           — مخصوص دسکتاپ
[ ] 🎨 Image Generation       — نیاز به API جداگانه
[ ] 🎬 Video Generation       — نیاز به API جداگانه
[ ] 🐦 X (Twitter) Search     — نیاز به xAI OAuth
[ ] 🏠 Home Assistant         — نیاز به HA instance
[ ] 🎵 Spotify                — نیاز به حساب Spotify
[ ] 💬 Yuanbao                — نیاز به تنظیم جداگانه
```

<div dir="rtl">

**→ ابزارهای بالا را با `SPACE` فعال/غیرفعال کن، بعد `ENTER` بزن.**

---

## Step 6 — Select Search Provider

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
  (0) Tavily [paid]
  (0) Skip - keep defaults / configure later
```

<div dir="rtl">

| ارائهدهنده | هزینه | نیاز به کلید | توضیح |
|---|---|---|---|
| **DuckDuckGo** | رایگان | ❌ | بدون کلید، فقط جستوجو. **بهترین گزینه برای شروع.** |
| **Brave Search** | رایگان | ✅ | ۲۰۰۰ پرسوجو در ماه |
| **Tavily** | پولی | ✅ | جستوجو + استخراج |
| **Skip** | — | — | بعداً تنظیم کن |

**→ `DuckDuckGo` — رایگان، بدون کلید، فوراً کار میکند.**

> [!NOTE]
> Browser Automation با Web Search فرق دارد. DuckDuckGo جستوجوی وب انجام میدهد. Browser Automation مرورگر واقعی کنترل میکند و روی اندروید کار نمیکند.

---

## Step 7 — تست اتصال

<div dir="rtl">

بعد از ویزارد، مدل را تست کن:

</div>

```bash
hermes doctor --fix
hermes doctor
```

```bash
hermes -q "سلام، فقط در یک جمله بگو با چه مدلی جواب میدهی."
```

<div dir="rtl">

اگر جواب گرفتی، هرمس آماده است.

---

## خلاصهی گزینههای پیشنهادی

| مرحله | گزینه |
|---|---|
| ۱. Setup mode | **Full Setup** |
| ۲. Provider | **Gemini** یا **Google AI Studio** |
| ۳. Terminal backend | **Local** (حتماً) |
| ۴. Platforms | **ENTER** — بعداً از اپ تنظیم کن |
| ۵. Tools | ابزارهای پیشنهادی بالا |
| ۶. Search Provider | **DuckDuckGo** (رایگان، بدون کلید) |

---

**→ بعد: [نصب APK و اتصال Gateway](GATEWAY_SETUP.md)** · **[راهنمای فنی کامل](RUNNING_ON_ANDROID_TERMUX.md)**

**← بازگشت: [نصب هرمس](INSTALL_HERMES_TERMUX.md)** · **[README اصلی](../README.md)**
</div>
