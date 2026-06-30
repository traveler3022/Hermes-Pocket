<div dir="rtl">

# نصب Hermes Agent در Termux

</div>

## Prerequisites

- Android 10+ recommended
- ~500 MB free storage
- Internet connection

Install **Termux** from F-Droid → **[f-droid.org/packages/com.termux](https://f-droid.org/en/packages/com.termux/)**

> [!WARNING]
> Do **not** install from Play Store — that version is abandoned and won't work.

## Enable external apps

This lets Hermes2 start commands in Termux:

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

Then **force-stop and reopen Termux** to apply.

## Install dependencies

```bash
pkg update -y && pkg upgrade -y
pkg install -y nodejs-lts git python openssh jq
```

## Grant storage permission

```bash
termux-setup-storage
```

This lets Hermes access files outside Termux's sandbox.

## Install Hermes Agent

```bash
npm install -g hermes-agent
```

> ⏱️ First install takes **5–15 min** on ARM (compiles native modules). Keep the screen on.

## Verify

```bash
hermes --version
```

Then launch and go through the setup wizard:

```bash
hermes
```

<div dir="rtl">

> [!NOTE]
> مستندات کامل رسمی: **[hermes-agent.nousresearch.com/docs/getting-started/termux](https://hermes-agent.nousresearch.com/docs/getting-started/termux)**

---

## عیبیابی

### `npm: command not found`

nodejs-lts را دوباره نصب کن:
```bash
pkg install -y nodejs-lts
```

### `hermes: command not found`

npm global bin را به PATH اضافه کن:
```bash
export PATH="$PATH:$(npm config get prefix)/bin"
echo 'export PATH="$PATH:$(npm config get prefix)/bin"' >> ~/.bashrc
```

### Segfault / crash موقع اجرا

حافظهی Node را محدود کن:
```bash
export NODE_OPTIONS="--max-old-space-size=512"
```

### خطای SSL / شبکه

```bash
pkg update -y && pkg install -y ca-certificates
```

اگر پراکسی داری، متغیرهای محیطی HTTP_PROXY/HTTPS_PROXY را تنظیم کن.

### Termux در پسزمینه کشته میشود

بهینهسازی باتری را غیرفعال کن:
- **Settings → Apps → Termux → Battery → Unrestricted**

قفل بیداری بگذار:
```bash
termux-wake-lock
```

برای استارت خودکار بعد از ریبوت، **Termux:Boot** را نصب کن.

### ENOSPC (فضای دیسک تمام شده)

```bash
npm cache clean --force
df -h
```

### Storage permission denied

```bash
termux-setup-storage
```

اگر کار نکرد، در تنظیمات اندروید دسترسی ذخیرهسازی Termux را چک کن.

---

## پکیجهای اختیاری پیشنهادی

```bash
pkg install -y termux-api
```

---

**→ بعد: [تنظیم اولیه هرمس](SETUP_HERMES_TERMUX.md)**
</div>
