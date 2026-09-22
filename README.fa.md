<div align="center">

# ⬡ Hermes

### یک ایجنت هوش مصنوعی که کاملاً روی گوشی اندرویدی‌ات زندگی می‌کند

[![دانلود APK](https://img.shields.io/badge/⬇_Download-APK-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/debug-latest)

[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white)

[English](README.md) · [فارسی](README.fa.md)

</div>

<div dir="rtl">

---

## 🤔 این چیه؟

**Hermes یک اپ اندرویده** که [Hermes Agent](https://github.com/NousResearch/hermes-agent) رو اجرا می‌کنه —
یک ایجنت هوش مصنوعی متن‌باز که فقط حرف نمی‌زنه، *کار انجام می‌ده*: دستور اجرا می‌کنه، کد می‌نویسه،
فایل‌ها رو مدیریت می‌کنه، توی وب می‌گرده و کارهای زمان‌بندی‌شده انجام می‌ده. همه‌اش روی خود گوشی.
**نه سرور لازمه، نه Termux، نه کامپیوتر.**

اپ یک **لینوکس Alpine** کوچک داخل خودش داره (با `proot`، بدون نیاز به روت). بار اول Hermes Agent رو
داخل همین لینوکس نصب می‌کنه، اجراش می‌کنه و یک رابط چت Material 3 برای حرف زدن باهاش بهت می‌ده.

| بخش | چیه | کجا اجرا می‌شه |
|---|---|---|
| **Hermes Agent** 🧠 | خود ایجنت (متن‌باز، از [Nous Research](https://nousresearch.com)) | لینوکس Alpine داخل اپ |
| **Hermes** 📱 *(همین اپ)* | چت، نصب‌کننده، ترمینال، نمایش دسکتاپ، تنظیمات | یک اپ معمولی اندروید |

تنها چیزی که از گوشی خارج می‌شه، درخواست به سرویس هوش مصنوعی‌ایه که خودت انتخاب کردی
(OpenRouter، Gemini، Anthropic و…). چت‌ها، فایل‌ها و کلید API روی گوشی می‌مونن.

> [!NOTE]
> اگه Termux داری، موقع راه‌اندازی می‌تونی به‌جای لینوکس داخلی **Termux** رو انتخاب کنی.
> لینوکس داخلی به نصب هیچ چیز دیگه‌ای نیاز نداره.

---

## ✨ امکانات

- 💬 **چت** با پاسخ زنده، نمایش استدلال و کارت ابزارها؛ Markdown، جدول و متن فارسی درست نمایش داده می‌شن
- 🛠️ **کار واقعی** — ایجنت داخل لینوکس خودش دستور اجرا می‌کنه، فایل ویرایش می‌کنه و کد اجرا می‌کنه
- ✅ **تأیید دستورها** — کارهای حساس منتظر تأیید / رد تو می‌مونن
- 🌐 **مرورگر و دسکتاپ** — یک Chromium روی دسکتاپ مجازی که ایجنت کنترلش می‌کنه؛ داخل اپ (با noVNC) تماشا کن و هر وقت خواستی کنترل رو بگیر
- 🖥️ **ترمینال** — ترمینال کامل برای لینوکس داخلی
- 📂 **فایل‌ها** — فایل‌های لینوکس توی اپ Files اندروید و انتخابگر فایل دیده می‌شن
- 📎 **پیوست** — فرستادن فایل و عکس برای ایجنت
- ⏰ **کارهای زمان‌بندی‌شده** — فقط بگو *کی* اجرا بشه؛ لازم نیست cron بلد باشی
- 🗂️ **گفتگوها** — منوی کشویی با جستجو، سنجاق، تغییر نام، شمارش نخوانده‌ها و ادامهٔ هر چت
- ⚙️ **مرکز کنترل** — سرویس‌دهنده و مدل، حافظه، مهارت‌ها، ابزارها، پلاگین‌ها، پلتفرم‌ها
- 🔋 **کار در پس‌زمینه** با یک اعلان آرام وقتی ایجنت مشغوله
- 🌙 تم روشن/تیره · 🌐 فارسی + English

---

## ⚡ شروع

**نیازمندی‌ها:** اندروید ۱۰ به بالا، گوشی **arm64** یا **x86_64**، حدود **۱ گیگابایت** فضای خالی و اینترنت پایدار برای نصب اول.

۱. **APK رو دانلود و نصب کن:** **[⬇ آخرین نسخه](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/debug-latest)** (اگه پرسید، «نصب از منابع ناشناس» رو اجازه بده).

۲. **Hermes رو باز کن** ← شروع ← «Hermes کجا اجرا شود؟» ← **لینوکس داخلی** ← **نصب Hermes**.

۳. **صبر کن** تا اپ همه‌چیز رو با نوار پیشرفت نصب کنه: باز کردن Alpine، نصب Python و git و Node.js، دانلود Hermes Agent و پکیج‌هاش، و اجرای Hermes.
اپ رو باز و صفحه رو روشن نگه دار. لاگ کامل داخل لینوکس در `/root/.hermes/logs/app-install.log` ذخیره می‌شه.

۴. **سرویس هوش مصنوعی رو انتخاب کن**، کلید API رو وارد کن و مدل رو انتخاب کن — اپ کلید رو خودش بررسی می‌کنه.

۵. سلام کن. 👋

### سرویس‌های پشتیبانی‌شده

OpenRouter · Anthropic · OpenAI · Google Gemini · DeepSeek · Xiaomi MiMo · Z.AI / GLM · Kimi / Moonshot ·
و هر سرویس **سازگار با OpenAI** (آدرس دلخواه — مثل Ollama، Groq یا سرور محلی).

> [!TIP]
> Gemini سطح رایگان داره و OpenRouter هم مدل‌های رایگان — برای شروع خوبن. قبل از استفادهٔ طولانی، توی پنل سرویس‌دهنده سقف هزینه بذار.

---

## 🛡️ حریم خصوصی و امنیت

- 🟢 **روی گوشی می‌مونه:** کلید API، چت‌ها، فایل‌ها و کل ایجنت. بدون حساب کاربری، بدون ردیابی، بدون سرور ما.
- 🟠 **از گوشی خارج می‌شه:** پیام‌هایی که به سرویس هوش مصنوعی انتخابی *خودت* می‌فرستی — مثل هر اپ هوش مصنوعی دیگه.
- 🔒 **ایزوله:** ایجنت داخل لینوکس خود اپ اجرا می‌شه. بدون روت به دادهٔ اپ‌های دیگه دسترسی نداره؛ فقط فایل‌هایی رو می‌بینه که خودت بهش بدی.
- 🔑 **دسکتاپ محافظت‌شده:** دسکتاپ VNC به‌طور پیش‌فرض فقط روی خود گوشیه و همیشه رمز مخصوص همون نصب رو می‌خواد؛ اشتراکش روی Wi-Fi یک تنظیم اختیاریه.

> [!CAUTION]
> ایجنت دستورهای واقعی اجرا می‌کنه. تأیید ابزارها رو روشن نگه دار و هر وقت مطمئن نبودی **رد** رو بزن و بپرس می‌خواست چی کار کنه.
> کلید API رو هیچ‌وقت توی اسکرین‌شات یا issue عمومی نذار.

---

## 🔧 رفع مشکل

<details>
<summary><b>نصب متوقف شد یا خطا داد</b></summary>

معمولاً اینترنت قطع شده یا فضا پر شده. فضا آزاد کن (حداقل ۱ گیگابایت)، از پایدار بودن اینترنت مطمئن شو و دوباره «نصب» رو بزن.
</details>

<details>
<summary><b>با خاموش شدن صفحه ایجنت متوقف می‌شه</b></summary>

باتری Hermes رو روی **بدون محدودیت** بذار: تنظیمات ← برنامه‌ها ← Hermes ← باتری ← بدون محدودیت.
</details>

<details>
<summary><b>گزارش باگ</b></summary>

**نسخهٔ اندروید**، **مدل گوشی** و بخش مربوط از لاگ نصب/اتصال رو بفرست.
</details>

---

## 🏗️ ساختار و ساخت از سورس

- **فناوری‌ها:** Kotlin · Jetpack Compose · Material 3 · Hilt · OkHttp · MVVM
- **لینوکس داخلی:** `proot` و کتابخانه‌هاش به‌صورت `jniLibs` و Alpine minirootfs به‌صورت asset داخل APK هستن ([`scripts/fetch-proot-runtime.sh`](scripts/fetch-proot-runtime.sh) برای به‌روزرسانی‌شون).
- **ارتباط:** Hermes با `python -m tui_gateway.entry` اجرا می‌شه و اپ از طریق stdin/stdout با JSON-RPC باهاش حرف می‌زنه.

</div>

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug             # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest         # unit tests
```

<div dir="rtl">

**نیازمندی‌ها:** JDK 17 · Android SDK 35. هر push روی GitHub Actions ساخته می‌شه و APK در ریلیز [`debug-latest`](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/debug-latest) منتشر می‌شه.

---

## 📄 مجوز

**MIT** — فایل [LICENSE](LICENSE) رو ببین.

<sub>پروژهٔ مستقل · وابسته به Nous Research نیست · «Hermes Agent» متعلق به سازندگانشه.</sub>

</div>
