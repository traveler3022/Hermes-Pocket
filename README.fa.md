<div align="center">

# ⬡ Hermes2

### همراه نیتیو اندروید برای **[Hermes Agent](https://github.com/NousResearch/hermes-agent)**

![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

[![Build](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg)](https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml)
[![Download APK](https://img.shields.io/badge/⬇_Download-APK-6750A4?style=flat-square&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes2/releases/tag/debug-latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)

[English 🇬🇧](README.md) · **فارسی**

</div>

<div dir="rtl">

---

## این چیه؟

**[Hermes Agent](https://github.com/NousResearch/hermes-agent)** یک دستیار هوش مصنوعی متنباز و قدرتمند از [Nous Research](https://nousresearch.com) است. یک ایجنت کاملاً مستقل، با احترام به حریم خصوصی، که به دهها ابزار و سرویس بیرونی وصل میشود و به نیابت از تو کار انجام میدهد.

برخلاف چتباتهای ساده، Hermes واقعاً *کار انجام میدهد*:

- 🧠 **کدنویسی** — نوشتن، ویرایش، دیباگ، lint و اجرای کد در هر زبانی
- 📁 **مدیریت فایل** — خواندن، نوشتن، جستوجو، تبدیل اسناد (PDF، DOCX، تصویر، صدا، ویدیو)
- 🌐 **مرور وب** — جستوجو (Google، Brave، Perplexity)، دریافت صفحه، کار با اپهای وب
- 📅 **ابزارهای بهرهوری** — Gmail، Google Calendar، Slack، Discord، Telegram، Notion، GitHub، Jira، Linear و بیشتر
- 🖼️ **تولید و تحلیل رسانه** — تولید تصویر (DALL·E، Stable Diffusion)، OCR، تبدیل گفتار به متن، TTS
- 🧩 **قابل گسترش** — مهارت سفارشی، پلاگین، سرور MCP، زمانبند cron، حافظهی پایدار با جستوجوی برداری
- 🤖 **واگذاری به زیرایجنت** — اسپن ایجنتهای تخصصی برای کارهای موازی
- 🔒 **حفظ حریم خصوصی** — local-first، دادهات روی دستگاهت میماند

از هر ارائهدهندهی مدل سازگار با OpenAI پشتیبانی میکند: Gemini، OpenRouter، Claude، Mistral، Groq، Ollama و بیشتر. مستندات کامل: **[hermes-agent.nousresearch.com/docs](https://hermes-agent.nousresearch.com/docs)**

**Hermes2** همهی اینها را به اندروید میآورد — یک اپ نیتیو Material 3. اپ جلوی صحنه است — Hermes داخل Termux مغز است. تمام ارتباطات روی گوشی و از طریق WebSocket محلی میماند.

**بدون واسطهی ابری · بدون حساب کاربری · بدون تلهمتری**

---

## این اپ چه میدهد

- **چت زنده** با پاسخهای استریم، نمایش استدلال، و کارتهای فراخوانی ابزار جمعشونده
- **مدیریت سشن** — جستوجو، سنجاق، تغییرنام، ادامهی هر گفتوگوی قبلی
- **تأیید ابزار** بهصورت اعلان اندروید — Approve یا Deny قبل از هر اجرا
- **Runtime Setup** — تشخیص، نصب و استارت gateway از داخل اپ
- **مدیریت تنظیمات و مدل** بدون خروج از اپ
- **۶ تم رنگی**، حالت روشن/تاریک/سیستم، طراحی کامل Material 3
- **دوزبانه** — انگلیسی و فارسی
- **Foreground service** — زنده نگهداشتن gateway وقتی صفحه خاموش است
- **Share intent** — فرستادن متن از هر اپی به چت هرمس۲

> [!IMPORTANT]
> **این یک ابزار حرفهای است، نه اپ چت معمولی.** Hermes Agent دستورهای واقعی روی دستگاهت اجرا میکند. اگر فقط میخواهی گپ بزنی، اپ معمولی بهتر است. اگر ایجنت واقعی میخواهی — بخوان.

---

## حریم خصوصی و امنیت

> [!WARNING]
> قبل از نصب بخوان.

### 🟢 روی گوشیات میماند

| چی | کجا |
|---|---|
| **کلید API** | تنظیمات Hermes در Termux. این اپ هیچجا نمیفرستد. |
| **ارتباط اپ ↔ ایجنت** | `127.0.0.1` — هرگز دستگاه را ترک نمیکند. |
| **دادهی سشن** | SQLite داخل `~/.hermes/` — فقط روی دستگاه. |

### 🟠 خارج میشود

| چی | کجا |
|---|---|
| **پیامهایت** | به ارائهدهندهی مدل — Gemini → گوگل، OpenRouter → ارائهدهندگان مختلف. |

**قاعده:** چیزی نفرست که نمیخواهی ارائهدهنده ببیند.

### 🛡️ sandbox اندروید

روی گوشی **روتنشده**، ایجنت فقط به حافظهی Termux دسترسی دارد.

> [!CAUTION]
> **گوشیات را روت نکن.** روت sandbox را از بین میبرد.

### ✅ تأیید ابزار را روشن نگه دار

Hermes قبل از دستورهای خطرناک میپرسد. در شک، **Deny** بزن.

> [!NOTE]
> این اپ همانطور که هست تحت MIT ارائه میشود. مسئولیت کلیدها و دستورها با خودت است.

---

## راهاندازی

راهنمای کامل: **[اجرای هرمس۲ روی اندروید + Termux](docs/RUNNING_ON_ANDROID_TERMUX.md)** — نصب، تنظیم، اولین اتصال و عیبیابی.

---

## کنترل هزینه

- 💳 **سقف خرج** در داشبورد ارائهدهنده — بهترین محافظت.
- 🆓 با **مدل رایگان** شروع کن.
- 👀 چند سشن اول را تماشا کن.

---

## عیبیابی

<details>
<summary><b>اپ روی «Connecting...» گیر کرده</b></summary>

استارت سرد ۳۰–۹۰ ثانیه طول میکشد. لاگ: `cat ~/.hermes/logs/gateway_stdout.log`. اگر هرمس در حال اجراست ولی اپ وصل نمیشود، Termux را force-stop کن.
</details>

<details>
<summary><b>بیلد <code>jiter</code> / <code>pydantic-core</code> شکست میخورد</b></summary>

```bash
export CARGO_HOME="$HOME/.hermes/cargo"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_BUILD_JOBS=1
```
</details>

<details>
<summary><b><code>hermes: command not found</code></b></summary>

```bash
cd ~/.hermes/hermes-agent
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```
</details>

<details>
<summary><b>قطعشدن وقتی صفحه خاموش است</b></summary>

تنظیمات → برنامهها → [برنامه] → باتری → بدون محدودیت
</details>

---

## ساخت از سورس

```bash
git clone https://github.com/traveler3022/Hermes2.git
cd Hermes2
bash ./gradlew :app:assembleDebug
```

نیاز: JDK 17 · Android SDK 35 · Android Studio Ladybug+

---

## مشارکت

issue و PR خوشآمدند. پورت مستقل جامعهمحور — نه محصول رسمی Nous Research.

---

**MIT** — [LICENSE](LICENSE) · پروژهی مستقل · وابسته به Nous Research نیست

<div align="center"><br>

**⬡ ساختهشده برای اندروید · با قدرت Hermes Agent ⬡**

</div>
