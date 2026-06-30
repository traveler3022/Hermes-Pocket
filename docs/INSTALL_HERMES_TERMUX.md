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

## مرحله ۸ — راهاندازی هرمس

```bash
hermes setup
```

<div dir="rtl">

ویزارد راهاندازی اجرا میشود. راهنمای کامل هر ۶ صفحه ویزارد:

**→ [تنظیم اولیه هرمس](SETUP_HERMES_TERMUX.md)**

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

<div dir="rtl">

نصب کامل شد. حالا اپ را به هرمس وصل کن:

**→ [اتصال Gateway به اپ](GATEWAY_SETUP.md)**

> [!TIP]
> فقط دفعه اول نیاز به تنظیم داره. بعدش خودکار وصل میشود.

</div>

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
