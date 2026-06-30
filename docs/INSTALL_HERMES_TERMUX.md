<div dir="rtl">

# نصب Hermes Agent در Termux

راهنمای نصب Hermes Agent روی اندروید از طریق Termux.

> مستندات رسمی: **[hermes-agent.nousresearch.com/docs/getting-started/termux](https://hermes-agent.nousresearch.com/docs/getting-started/termux)**

</div>

---

## پیش‌نیازها

| پیش‌نیاز | توضیح |
|---|---|
| **گوشی اندروید** | اندروید ۱۰ یا بالاتر |
| **Termux** | از F-Droid نصب شده (نه Play Store) |
| **فضای ذخیره** | حداقل ۵۰۰ مگابایت خالی |
| **اینترنت** | برای دانلود پکیج‌ها |

> [!WARNING]
> Termux را **فقط از F-Droid** نصب کن. نسخه Play Store رها شده و کار نمیکند.
>
> → [دانلود Termux از F-Droid](https://f-droid.org/en/packages/com.termux/)

---

## مرحله ۱ — فعال‌کردن دسترسی اپ‌های بیرونی

<div dir="rtl">

این تنظیم لازم است تا اپ Hermes2 بتواند دستور در Termux اجرا کند.

</div>

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

<div dir="rtl">

**بعد Termux را ببند و دوباره باز کن.**

---

## مرحله ۲ — نصب پکیج‌های سیستمی

</div>

```bash
pkg update -y
pkg install -y git python clang rust make pkg-config libffi openssl nodejs ripgrep ffmpeg
```

<div dir="rtl">

| پکیج | دلیل |
|---|---|
| python | اجرا + venv |
| git | دانلود کد |
| clang, rust, make, pkg-config, libffi, openssl | کامپایل dependency‌های پایتون |
| nodejs | ابزارهای اختیاری |
| ripgrep | جستوجوی سریع فایل |
| ffmpeg | تبدیل صدا/ویدیو |

---

## نصب Hermes Agent

<div dir="rtl">

**دو روش وجود دارد. یکی را انتخاب کن:**

---

### روش A — نصب خودکار (ساده‌تر)

</div>

```bash
curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
```

<div dir="rtl">

نصبکننده خودکار:
- پکیج‌های سیستمی را نصب میکند
- محیط مجازی پایتون میسازد
- اول `.[termux-all]` را امتحان میکند، اگر نشد `.[termux]`، اگر نشد نصب پایه
- `hermes` را در PATH قرار میدهد

> ⏱️ نصب اول **۵ تا ۱۵ دقیقه** طول می‌کشد (Rust کد کامپایل می‌شود). صفحه را روشن نگه دار.

اگر نصبکننده خطا داد، **روش B** را امتحان کن.

---

### روش B — نصب دستی (کامل و شفاف)

#### ۱. تنظیم محیط کامپایل

</div>

```bash
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
```

<div dir="rtl">

> [!NOTE]
> `ANDROID_API_LEVEL` برای کامپایل پکیج‌های Rust مثل `jiter` و `pydantic-core` لازم است.

#### ۲. دانلود کد

</div>

```bash
git clone https://github.com/NousResearch/hermes-agent.git
cd hermes-agent
```

#### ۳. ساخت محیط مجازی پایتون

```bash
python -m venv venv
source venv/bin/activate
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
python -m pip install --upgrade pip setuptools wheel
```

#### ۴. نصب پروفایل Termux

```bash
python -m pip install -e '.[termux]' -c constraints-termux.txt
```

<div dir="rtl">

> ⏱️ این مرحله **۵ تا ۱۵ دقیقه** طول می‌کشد. صفحه را روشن نگه دار.

اگر فقط هسته اصلی ایجنت را میخواهی (بدون ابزارهای اضافی):

</div>

```bash
python -m pip install -e '.' -c constraints-termux.txt
```

#### ۵. قراردادن hermes در PATH

```bash
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```

<div dir="rtl">

`$PREFIX/bin` در Termux از قبل در PATH هست، پس دستور `hermes` در هر شل جدید کار میکند.

---

## مرحله ۳ — بررسی نصب

```bash
hermes version
hermes doctor
```

<div dir="rtl">

خروجی مورد انتظار:

</div>

```text
Hermes Agent v0.17.x
Python: 3.13.x
```

<div dir="rtl">

> [!NOTE]
> اگر `hermes doctor` خطا نشان داد، `hermes doctor --fix` را امتحان کن.

---

## مرحله ۴ — شروع هرمس

```bash
hermes
```

<div dir="rtl">

اگر بار اول باشد، ویزارد راهاندازی اجرا میشود. اگر قبلاً تنظیم کرده باشی، مستقیم وارد چت میشوی.

---

## مرحله ۵ — تنظیم مدل

```bash
hermes model
```

<div dir="rtl">

یا کلید API را مستقیم در `~/.hermes/.env` تنظیم کن. مثال:

</div>

```bash
nano ~/.hermes/.env
```

```env
GEMINI_API_KEY=*** hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

<div dir="rtl">

یا ویزارد کامل راهاندازی:

</div>

```bash
hermes setup
```

<div dir="rtl">

راهنمای کامل ویزارد: **[تنظیم اولیه هرمس](SETUP_HERMES_TERMUX.md)**

---

## مرحله ۶ — اتصال به اپ Hermes2

</div>

```bash
hermes dashboard --stop
```

<div dir="rtl">

از Termux خارج شو → تنظیمات اندروید → برنامه‌ها → Termux → Force stop.

اپ Hermes2 را باز کن → **Start Agent Gateway** → ۳۰ ثانیه صبر → **✓ Connected**

راهنمای کامل اتصال: **[اتصال Gateway به اپ](GATEWAY_SETUP.md)**

> [!TIP]
> فقط دفعه اول نیاز به force-stop داره. بعدش خودکار وصل میشود.

---

## مرحله ۷ — نصب ابزارهای Node (اختیاری)

نصب Termux عمداً ابزارهای Node/browser را رد میکند. اگر میخوای browser tool رو امتحان کنی:

</div>

```bash
pkg install nodejs-lts
npm install
```

<div dir="rtl">

> [!NOTE]
> ابزارهای browser/WhatsApp روی اندروید **تجربی** هستند.

---

## عیبیابی

### `hermes: command not found`

</div>

```bash
cd hermes-agent
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
```

### خطای نصب `.[all]`

<div dir="rtl">

`.[all]` روی اندروید پشتیبانی نمیشود. از پروفایل Termux استفاده کن:

</div>

```bash
python -m pip install -e '.[termux]' -c constraints-termux.txt
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
> **اندروید پلتفرم «Tier 2»** است. Browser Automation، Computer Use، voice transcription و Docker روی اندروید کار نمی‌کنند. بقیه چیزها کار میکنن.

---

**→ [تنظیم اولیه هرمس](SETUP_HERMES_TERMUX.md)** · **[اتصال Gateway](GATEWAY_SETUP.md)** · **[راهنمای فنی کامل](RUNNING_ON_ANDROID_TERMUX.md)**
</div>
