<div dir="rtl">

# نصب Hermes Agent در Termux

راهنمای گام‌به‌گام نصب Hermes Agent روی اندروید از طریق Termux.

</div>

---

## پیش‌نیازها

قبل از شروع، مطمئن شو:

| پیش‌نیاز | توضیح |
|---|---|
| **گوشی اندروید** | اندروید ۱۰ یا بالاتر |
| **Termux** | از F-Droid نصب شده (نه Play Store) |
| **فضای ذخیره** | حداقل ۵۰۰ مگابایت خالی |
| **اینترنت** | برای دانلود پکیج‌ها |
| **کلید API** | حداقل یک ارائه‌دهنده (مثلاً Gemini، OpenRouter، و غیره) |

> [!WARNING]
> Termux را **فقط از F-Droid** نصب کن. نسخه Play Store رها شده و کار نمیکند.
>
> → [دانلود Termux از F-Droid](https://f-droid.org/en/packages/com.termux/)

---

## مرحله ۱ — فعال‌کردن دسترسی اپ‌های بیرونی

<div dir="rtl">

این تنظیم لازم است تا اپ Hermes2 بتواند دستور در Termux اجرا کند.

**در Termux بنویس:**

</div>

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

<div dir="rtl">

**بعد Termux را ببند و دوباره باز کن** (یا force-stop کن از تنظیمات اندروید).

---

## مرحله ۲ — نصب پکیج‌های سیستمی

این پکیج‌ها برای کامپایل و اجرای Hermes لازماند.

</div>

```bash
pkg update -y && pkg upgrade -y
pkg install -y git python clang rust make pkg-config libffi openssl ca-certificates curl llvm lld nodejs ripgrep ffmpeg
```

<div dir="rtl">

> ⏱️ نصب پکیج‌ها ۲ تا ۵ دقیقه طول می‌کشد.

---

## مرحله ۳ — تنظیم محیط کامپایل Rust

بعضی dependency‌های پایتون (مثل `pydantic-core` و `jiter`) با Rust کامپایل می‌شوند. این متغیرها باید تنظیم بشوند.

</div>

```bash
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

<div dir="rtl">

> [!NOTE]
> **این مرحله را باید هر بار که Termux را باز می‌کنی تکرار کنی** (قبل از pip install). یا می‌توانی آن را در `~/.bashrc` بگذاری تا خودکار اجرا شود.

---

## مرحله ۴ — پاک‌سازی نصب‌های قبلی (اگر وجود دارد)

اگر قبلاً نصب ناموفق داشتی:

</div>

```bash
if [ -e "$HOME/.hermes/hermes-agent" ] && [ ! -d "$HOME/.hermes/hermes-agent/.git" ]; then
  mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.broken-$(date +%Y%m%d-%H%M%S)"
fi
```

<div dir="rtl">

اگر می‌خوای کاملاً از اول شروع کنی:

</div>

```bash
mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.backup-$(date +%Y%m%d-%H%M%S)" 2>/dev/null || true
```

---

## مرحله ۵ — دانلود Hermes Agent

<div dir="rtl">

کد رسمی را از GitHub دانلود کن:

</div>

```bash
mkdir -p "$HOME/.hermes"
git clone https://github.com/NousResearch/hermes-agent.git "$HOME/.hermes/hermes-agent"
cd "$HOME/.hermes/hermes-agent"
```

---

## مرحله ۶ — ساخت محیط پایتون و نصب

<div dir="rtl">

یک محیط مجازی پایتون بساز و dependency‌ها را نصب کن:

</div>

```bash
rm -rf venv
python -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
```

<div dir="rtl">

**دوباره متغیرهای Rust را داخل venv تنظیم کن** (چون venv یک شل جدید باز می‌کند):

</div>

```bash
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

<div dir="rtl">

**نصب psutil برای اندروید:**

</div>

```bash
python scripts/install_psutil_android.py --pip "python -m pip"
```

<div dir="rtl">

**نصب پروفایل Termux:**

</div>

```bash
python -m pip install -e '.[termux]' -c constraints-termux.txt 2>&1 | tee ~/hermes-termux-install.log
```

<div dir="rtl">

> ⏱️ این مرحله **۵ تا ۱۵ دقیقه** طول می‌کشد (Rust کد کامپایل می‌شود). صفحه را روشن نگه دار.

**نصب وب‌سرور داشبورد (برای اتصال اپ):**

</div>

```bash
python -m pip install -e '.[web]' -c constraints-termux.txt 2>&1 | tee ~/hermes-web-install.log
```

<div dir="rtl">

**لینک دستور hermes:**

</div>

```bash
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```

---

## مرحله ۷ — بررسی نصب

```bash
which hermes
hermes --version
hermes doctor
```

<div dir="rtl">

خروجی مورد انتظار:

</div>

```text
Hermes Agent v0.17.0
Python: 3.13.x
OpenAI SDK: 2.24.0
```

<div dir="rtl">

> [!NOTE]
> «OpenAI SDK» اسم یک dependency است. **به این معنی نیست** که باید از OpenAI استفاده کنی.

---

## مرحله ۸ — اجرای ویزارد راهاندازی

```bash
hermes setup
```

<div dir="rtl">

ویزارد ۶ صفحه داره. یکی یکی رد کن:

---

### ۸.۱ — انتخاب نحوه راهاندازی

```
How would you like to set up Hermes?
  ( ) Quick Setup (Nous Portal) – free OAuth, no API keys
● ( ) Full setup – configure everything yourself
  ( ) Blank Slate – everything off except bare minimum
```

**→ `Full Setup`** را انتخاب کن. `ENTER` بزن.

---

### ۸.۲ — انتخاب ارائهدهنده

```
Select provider:
→ Nous Portal
  OpenRouter
  Anthropic
  OpenAI
  Gemini
  Google AI Studio
  DeepSeek
  Custom endpoint
  Leave unchanged
```

**→ `Gemini` یا `Google AI Studio`** پیشنهاد میشود. `ENTER` بزن.

ویزارد ازت کلید API میخواهد. کلیدت را وارد کن.

---

### ۸.۳ — انتخاب terminal backend

```
Select terminal backend:
→ (0) Local - run directly on this machine (default)
  (0) Docker
  (0) Modal
  (0) SSH
  (0) Daytona
  (●) Keep current (local)
```

**→ حتماً `Local`** را انتخاب کن. `ENTER` بزن.

---

### ۸.۴ — پلتفرمهای پیامرسان

```
Select platforms to configure:
[ ] Mattermost    [ ] Discord       [ ] Slack
[ ] Signal        [ ] Email         [ ] Telegram
[ ] WhatsApp      [ ] Matrix        [ ] Microsoft Teams
... (25 پلتفرم)
```

**→ فعلاً لازم نیست. فقط `ENTER` بزن تا رد شوی.**

---

### ۸.۵ — ابزارهای CLI (تنظیمات پیشنهادی)

با `SPACE` تیک بزن، بعد `ENTER` تأیید کن.

**فعال کن:**

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

**فعال نکن:**

```
[ ] 🌐 Browser Automation    — اندروید پشتیبانی نمیکنه
[ ] 💻 Computer Use           — مخصوص دسکتاپ
[ ] 🎨 Image Generation       — نیاز به API جدا
[ ] 🎬 Video Generation       — نیاز به API جدا
[ ] 🐦 X (Twitter) Search     — نیاز به xAI OAuth
[ ] 🏠 Home Assistant         — نیاز به HA instance
[ ] 🎵 Spotify                — نیاز به حساب
[ ] 💬 Yuanbao                — نیاز به تنظیم جدا
```

---

### ۸.۶ — انتخاب موتور جستوجو

```
Select Search Provider:
  (0) Brave Search (Free) – 2k queries/mo
→ (0) DuckDuckGo (ddgs) – free, no key
  (0) Tavily – paid
  (0) Skip
```

**→ `DuckDuckGo`** — رایگان، بدون کلید، فوراً کار میکند. `ENTER` بزن.

</div>

---

## مرحله ۹ — تست اتصال مدل

</div>

```bash
hermes -q "سلام، فقط در یک جمله بگو با چه مدلی جواب میدهی."
```

<div dir="rtl">

اگر جواب گرفتی، نصب کامل است. ✅

---

## مرحله ۱۰ — اتصال به اپ Hermes2

</div>

```bash
hermes dashboard --stop
```

<div dir="rtl">

از Termux خارج شو و force-stop کن (تنظیمات → برنامه‌ها → Termux → Force stop).

اپ Hermes2 را باز کن → **Start Agent Gateway** → ۳۰ ثانیه صبر → **✓ Connected**

> [!TIP]
> اگر وصل نشد، مراحل ۹ و ۱۰ را تکرار کن. از دفعه دوم خودکار وصل می‌شود.

---

## عیبیابی

### `hermes: command not found`

</div>

```bash
cd "$HOME/.hermes/hermes-agent"
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
```

### pydantic-core / rustc crash

<div dir="rtl">

مطمئن شو اینها قبل از pip install تنظیم شده‌اند:

</div>

```bash
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

### Cargo mirror 404

```text
Updating `ustc` index
unexpected http status code: 404
```

```bash
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
```

### Termux در پس‌زمینه کشته می‌شود

<div dir="rtl">

تنظیمات اندروید → برنامه‌ها → Termux → باتری → بدون محدودیت

</div>

```bash
termux-wake-lock
```

---

<div dir="rtl">

> [!NOTE]
> **اندروید پلتفرم «Tier 2»** است. Browser Automation و Computer Use روی اندروید کار نمی‌کنند.

---

**→ بعد: [راهنمای فنی کامل](RUNNING_ON_ANDROID_TERMUX.md)** · **[README اصلی](../README.md)**
</div>
