<div dir="rtl">

# راهاندازی Gateway و اولین اتصال

بعد از [نصب هرمس](INSTALL_HERMES_TERMUX.md) و [تنظیم اولیه](SETUP_HERMES_TERMUX.md)، نوبت نصب اپ و وصلشدنش به هرمس است.

</div>

---

## Install the APK

1. **[Download the latest APK](https://github.com/traveler3022/Hermes2/releases/download/debug-latest/app-debug.apk)**
2. Enable **"Install from unknown sources"** in Android settings
3. Open the APK and install

---

## First-time connection

<div dir="rtl">

اولین اتصال به یک دستدادن کوتاه و **یکباره** نیاز دارد. بعد از آن، هر اجرای بعدی خودکار وصل میشود.

> [!IMPORTANT]
> این مراحل را **یکبار، بهترتیب** انجام بده:

</div>

### Step 1 — Stop the existing dashboard

**In Termux:**

```bash
hermes dashboard --stop
```

### Step 2 — Release the port

<div dir="rtl">

**از Termux خارج شو و force-stop کن:**

تنظیمات اندروید → برنامهها → Termux → Force stop

> *چرا؟* سشن نصب، پورت gateway را اشغال نگه میدارد. force-stop آزادش میکند.

</div>

### Step 3 — Connect from the app

1. Open **Hermes2**
2. Tap **`Start Agent Gateway`**
3. Wait up to ~30 seconds for **✓ Connected**
4. Start chatting

> [!TIP]
> If it doesn't connect within ~30 s, repeat steps 1–3. The first handshake sometimes needs a second pass — after that it stays automatic.

---

## After app reinstall

<div dir="rtl">

اگر اپ را حذف و دوباره نصب کردی، خودکار توکن را از Termux هماهنگ میکند. فقط اپ را باز کن و صبر کن تا وصل شود.

---

## Disconnects when screen turns off

یک foreground service، gateway را زنده نگه میدارد. ولی بهینهساز باتری تهاجمی میتواند بکشدش:

</div>

- **Settings → Apps → Hermes2 → Battery → Unrestricted**
- Do the same for **Termux** if it persists

---

## How it works

```
┌─────────────────────────┐
│   Hermes2 Android App   │   Material 3 chat UI
└────────────┬────────────┘
             │  WebSocket · ws://127.0.0.1:9119
             │  loopback — never leaves your phone
┌────────────▼────────────┐
│   Hermes Agent (Python) │   running inside Termux
└────────────┬────────────┘
             │  HTTPS
┌────────────▼────────────┐
│   Your model provider   │   MiMo · Gemini · OpenRouter · ...
└─────────────────────────┘
```

<div dir="rtl">

**← بازگشت: [README اصلی](../README.md)**
</div>
