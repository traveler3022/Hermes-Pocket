<p align="center">
  <img src="https://img.shields.io/badge/⬡-Hermes_Android-0EA5E9?style=for-the-badge&labelColor=141414&color=0EA5E9" height="48px"/>
  <br><br>
  <b>یک کلاینت نیتیو اندروید برای Hermes Agent خودت</b>
  <br>
  <sub>این اپ هوش مصنوعی رو روی گوشی اجرا نمی‌کنه — به gateway‌ای که خودت رو سرور/VPS داری وصل می‌شه.</sub>
  <br><br>
  <a href="https://github.com/traveler3022/hermes-android-vps-/actions/workflows/build-apk.yml"><img src="https://github.com/traveler3022/hermes-android-vps-/actions/workflows/build-apk.yml/badge.svg" alt="Build"></a>
  <a href="https://github.com/traveler3022/hermes-android-vps-/releases/tag/debug-latest"><img src="https://img.shields.io/badge/⬇_دانلود-APK-0EA5E9?style=flat-square&logo=android&logoColor=white" alt="Download APK"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-0EA5E9?style=flat-square" alt="License"></a>
  <br><br>
  <a href="README.md">English 🇬🇧</a> · <a href="README.fa.md">فارسی</a>
</p>

<div dir="rtl">

---

## این چیه؟

خودت **[Hermes Agent](https://github.com/NousResearch/hermes-agent)** — یه ایجنت هوش مصنوعی متن‌باز که می‌تونه دستور اجرا کنه، فایل ویرایش کنه، وب بگرده و کار انجام بده — رو روی سرور یا VPS خودت داری اجرا می‌کنی. این اپ همون گوشی تو جیبته برای اون ایجنت: یه کلاینت چت نیتیو واقعی، به‌جای SSH زدن از یه اپ ترمینال هر بار که می‌خوای با ایجنتت حرف بزنی.

| بخش | چیه | کجا اجرا می‌شه |
|---|---|---|
| **Hermes Agent** 🧠 | خودِ ایجنت هوش مصنوعی (متن‌باز، از [Nous Research](https://nousresearch.com)) | سرور/VPS خودت، از طریق `hermes dashboard` پشت یه reverse proxy با TLS |
| **Hermes Android** 📱 *(همین اپ)* | یه کلاینت چت نیتیو Material 3 که بهش وصل می‌شه | یه اپ عادی اندروید، از هرجا وصل می‌شه |

اپ به WebSocket سرورت (`wss://your-server/api/ws`) با یه توکن سشن که یه‌بار تنظیمش می‌کنی وصل می‌شه. هیچی از خودِ ایجنت روی گوشی نیست — اپ فقط یه کلاینته، پس چه با وای‌فای چه با موبایل‌دیتا، از هرجا یکسان کار می‌کنه.

> [!IMPORTANT]
> **قبل از استفاده از این اپ باید یه سرور Hermes Agent جایی در حال اجرا داشته باشی** — این ریپو فقط کلاینت اندرویده. برای راه‌اندازی سرور به [Hermes Agent](https://github.com/NousResearch/hermes-agent) مراجعه کن.

---

## ✨ قابلیت‌ها

- 💬 **چت زنده** — پاسخ‌های استریم‌شونده، نمایش استدلال/تفکر (با سوییچ سریع سطح استدلال: none → minimal → low → medium → high → xhigh → max)، کارت‌های فراخوانی ابزار، کارت‌های زیرایجنت
- 📎 **پیوست‌ها** — ارسال فایل و عکس به ایجنت از گوشی؛ نمایش inline عکس، بلوک کد، نمودار Mermaid، و آرتیفکت‌های قابل‌دانلود تو پاسخ‌ها
- 🗂️ **مدیریت سشن‌ها** — جستجو، سنجاق، تغییرنام، انشعاب (branch)، و ادامه‌ی هر گفتگوی قبلی
- 🎨 **شخصی‌سازی** — تغییر نام دستیار، آپلود آواتار دلخواه، انتخاب از بین ۶ تم رنگی (روشن/تیره)، و شخصی‌سازی پریست شخصیت + هویت ماندگار ایجنت (`SOUL.md`)
- 🧠 **مدیریت مدل و provider** — اضافه‌کردن provider سازگار با OpenAI، تشخیص خودکار مدل‌های در دسترسش، ساخت زنجیره‌ی fallback، و auto-failover یک‌لمسی بین همه‌ی provider‌های تنظیم‌شده
- 🛠️ **کنترل ابزارها** — فعال/غیرفعال کردن دسته‌های ابزار ایجنت، به‌صورت زنده روی سشن فعلی
- 🔌 **مدیریت پلاگین‌ها** — دیدن پلاگین‌های نصب‌شده‌ی هرمس و فعال/غیرفعال کردنشون
- ⏰ **وظایف زمان‌بندی‌شده (Cron)** — دیدن و مدیریت تسک‌های زمان‌بندی‌شده‌ی ایجنت
- 🧩 **کاتالوگ مهارت‌ها** — مرور مهارت‌های در دسترس ایجنت
- 🤝 **اتصال به پلتفرم‌ها** — وصل کردن ایجنت به توکن بات‌های Telegram، Discord، یا Slack
- ✅ **تأیید دستور** — حالت‌های manual / smart / off برای عملیات ریسک‌دار، هماهنگ با `approvals.mode` سرور
- 🌐 **انگلیسی + فارسی**، پشتیبانی کامل RTL

---

## ⚡ راه‌اندازی

### ۱ — یه سرور Hermes Agent در حال اجرا داشته باش

باید `hermes dashboard --host 127.0.0.1 --port <پورت>` روی سرورت اجرا شده باشه، پشت یه reverse proxy با TLS (Caddy، nginx و غیره) تا اپ بتونه به‌صورت `wss://your-domain:port` بهش برسه. یه توکن سشن هم با `HERMES_DASHBOARD_SESSION_TOKEN` تنظیم کن — همینه که اپ باهاش احراز هویت می‌کنه.

این بخش کاملاً سمت سرور و خارج از محدوده‌ی این ریپوئه؛ برای نحوه‌ی نصب و اجرای خودِ gateway به [Hermes Agent](https://github.com/NousResearch/hermes-agent) مراجعه کن.

### ۲ — اپ رو نصب کن

**[⬇ دانلود آخرین نسخه](https://github.com/traveler3022/hermes-android-vps-/releases/tag/debug-latest)** → APK رو باز کن → «نصب از منابع ناشناس» رو فعال کن → نصب کن.

### ۳ — وصل شو

اپ رو باز کن → صفحه‌ی **Runtime** → آدرس سرور و توکن سشن رو وارد کن → **Save & Connect**. همین — از این به بعد هر بار که اپ رو باز کنی خودش وصل می‌شه.

---

## 🛡️ حریم خصوصی و امنیت

- **توکن سشن** و آدرس سرورت فقط رو خودِ اپ ذخیره می‌شه، هیچ‌جا جز سرور خودت فرستاده نمی‌شه.
- **اتصال** یه WebSocket روی TLS (`wss://`) مستقیم به سروری که خودت تنظیم کردی هست — هیچ سرویس واسطه‌ای وجود نداره.
- **دسترسی ایجنت** کاملاً به تنظیمات سمت سرور خودت و `approvals.mode` بستگی داره — این اپ فقط چیزی که ایجنت گزارش می‌ده رو نمایش می‌ده و تصمیم approve/deny تو رو بهش می‌فرسته.

> [!CAUTION]
> توکن سشن رو محرمانه نگه دار — هرکی اون رو داشته باشه می‌تونه ایجنتت رو کنترل کنه. مثل یه رمز عبور باهاش رفتار کن.

---

## 🏗️ معماری

```
UI (Compose) ─► ViewModel ─► GatewayClient (اینترفیس)
                                  │ Hilt DI
                                  ▼
                         OkHttpGatewayClient (پیاده‌سازی)
                                  │ JSON-RPC روی WebSocket (wss://)
                                  ▼
                         Gateway هرمس (سرور خودت)
```

تمام کد UI و ViewModel فقط به اینترفیس `GatewayClient` وابسته‌ست. مستندات طراحی: [`docs/`](docs/)

## 🛠️ ساخت از سورس

</div>

```bash
git clone https://github.com/traveler3022/hermes-android-vps-.git
cd hermes-android-vps-
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # تست‌های واحد
```

<div dir="rtl">

نیاز: JDK 17 · Android SDK 35 · Android Studio Ladybug+

### نسخه‌ی release امضاشده

زیرساخت امضای release از قبل آماده‌ست (`app/build.gradle.kts` + `.github/workflows/release.yml`) — فقط keystore خودت رو لازم داره. این چهار secret رو به ریپو اضافه کن (Settings → Secrets and variables → Actions):

</div>

```
KEYSTORE_BASE64    base64 فایل .keystore/.jks
KEYSTORE_PASSWORD  رمز store
KEY_ALIAS          alias کلید
KEY_PASSWORD       رمز کلید
```

<div dir="rtl">

بعد با push کردن یه تگ نسخه، release بساز:

</div>

```bash
git tag v0.1.0 && git push origin v0.1.0
```

<div dir="rtl">

CI خودش یه APK امضاشده می‌سازه و به‌صورت خودکار به‌عنوان GitHub Release منتشرش می‌کنه. به‌صورت لوکال هم همین تنظیمات امضا یه فایل `keystore.properties` (تو gitignore) رو کنار ریپو می‌خونه (`storeFile`، `storePassword`، `keyAlias`، `keyPassword`) — بدون هیچ‌کدوم از این دو منبع، `assembleRelease` بازم یه APK بدون امضا می‌سازه، یعنی build برای مشارکت‌کننده‌هایی که keystore رو ندارن هیچ‌وقت خراب نمی‌شه.

---

## 🤝 مشارکت

issue و PR خوش‌آمدند. این یه پروژه‌ی کلاینت مستقله — نه محصول رسمی Nous Research. موقع گزارش باگ، نسخه‌ی اندروید، مدل گوشی، و اینکه مشکل سمت کلاینته (این اپ) یا سمت سرور (gateway هرمست) رو بگو.

## 📄 لایسنس

**MIT** — [LICENSE](LICENSE).

<sub>پروژه‌ی مستقل · وابسته به Nous Research نیست · «Hermes Agent» متعلق به نویسندگان آن است.</sub>

<p align="center">
  <br>
  <b>⬡ ساخته‌شده برای اندروید · با قدرت Hermes Agent ⬡</b>
</p>
</div>
