<h1 align="center">⬡ Hermes</h1>

<p align="center" dir="rtl">
  <strong>ایجنت هوش مصنوعی‌ات، روی گوشی خودت.</strong><br>
  خانهٔ نیتیو اندروید برای <a href="https://github.com/NousResearch/hermes-agent">Hermes Agent</a>: داخل خود اپ، توی ترموکس، یا روی سرور خودت.
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="#-امکانات">امکانات</a> •
  <a href="#-شروع-سریع">شروع سریع</a> •
  <a href="#-ساخت-از-سورس">ساخت از سورس</a>
</p>

<p align="center">
  <a href="https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest"><img alt="دانلود APK" src="https://img.shields.io/badge/⬇_Download_APK-0EA5E9?style=for-the-badge&logo=android&logoColor=white"></a>
  <br>
  <a href="https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml"><img alt="Build" src="https://github.com/traveler3022/Hermes-Pocket/actions/workflows/build-apk.yml/badge.svg"></a>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <a href="LICENSE"><img alt="GPLv3" src="https://img.shields.io/badge/License-GPLv3-00BCD4?style=flat-square"></a>
</p>

<p align="center">
  <table>
    <tr>
      <td><img src="screenshots/chat-fa.png" width="200"></td>
      <td><img src="screenshots/drawer-fa.png" width="200"></td>
      <td><img src="screenshots/control-center-fa.png" width="200"></td>
      <td><img src="screenshots/runtime-fa.png" width="200"></td>
    </tr>
  </table>
</p>

<div dir="rtl">

---

## ⬡ هرمس

**Hermes** یک ایجنت هوش مصنوعی واقعی رو توی جیبت می‌ذاره. فقط حرف نمی‌زنه: دستور اجرا می‌کنه، کد می‌نویسه و
اجرا می‌کنه، فایل‌ها رو مدیریت می‌کنه، توی وب می‌گرده و کارها رو سر وقتش انجام می‌ده.

خودت انتخاب می‌کنی خود ایجنت کجا اجرا بشه، و بعداً هم از کارت بالای **میز فرمان** عوضش می‌کنی:

| | Hermes Agent کجا اجرا می‌شه | چی لازمه |
|---|---|---|
| **🐧 لینوکس داخلی** *(پیشنهادی)* | داخل همین اپ، روی گوشی | هیچی. یک بار حدود ۱ گیگ جای خالی |
| **📟 ترموکس** | توی اپ جداگانهٔ Termux روی گوشی | نصب بودن Termux |
| **🖥️ سرور (ریموت)** | روی سرور لینوکسی خودت، از راه Tailscale | یک سرور + حساب رایگان Tailscale ← [راهنمای سرور](server/README.fa.md) |

توی دو حالت روی گوشی، گفتگوها، فایل‌ها و کلید API روی خود گوشی می‌مونن و تنها درخواستی که بیرون می‌ره
به ارائه‌دهندهٔ هوش مصنوعی‌ایه که خودت انتخاب کردی. بدون حساب کاربری، بدون ردیابی، بدون سرورِ ما.

## ✨ امکانات

**گفتگو**
- جواب‌های زنده با بخش تاشوی *افکار* که فکر کردن ایجنت و ابزارهایی که صدا زده رو نشون می‌ده.
- Markdown، جدول، کد و متن فارسی درست و خوانا؛ عکس، فایل و پیش‌نمایش HTML داخل گفتگو.
- ورودی صوتی، پیوست فایل، هدایت ایجنت وسط جواب، ویرایش یا انشعاب از هر پیام.
- قبل از هر کار پرخطر تأیید / رد می‌خواد؛ سخت‌گیری تأیید رو از میز فرمان تنظیم می‌کنی.

**فضای کار** (کشوی کنار)
- **میز کار**: یک کار رو بسپار، برو، برگرد و نتیجه رو ببین.
- **عامل**: پروفایل‌ها، شخصیت (SOUL.md) و حافظه.
- **زمان‌بندی‌شده**: فقط بگو کی انجام بشه، بدون cron.
- **گفتگوهای گروهی**: ۲ تا ۶ پروفایل توی یک اتاق؛ با هم جواب می‌دن و با @نام همدیگه رو صدا می‌زنن.
- **کانبان**: کارهایی که پروفایل‌ها روشون کار می‌کنن.
- **فایل‌ها**: فایل‌های ایجنت، که توی اپ Files اندروید و انتخابگر فایل هم هستن.

**میز فرمان**
- **مدل‌ها و ارائه‌دهنده‌ها**: OpenRouter، Anthropic، OpenAI، Gemini، DeepSeek، Xiaomi MiMo، Z.AI / GLM، Kimi یا هر endpoint سازگار با OpenAI؛ با کلید API یا ورود با حساب؛ عمق تفکر.
- **ابزارها**: گروه‌های ابزار، مهارت‌ها، افزونه‌ها و سرورهای MCP.
- **لینوکس**: ترمینال کامل، بسته‌ها، و مرورگر روی دسکتاپ مجازی که می‌تونی تماشاش کنی و کنترلش رو بگیری.
- **پلتفرم‌ها**: از تلگرام، دیسکورد یا اسلک به ایجنتت پیام بده.
- **پروژه‌ها**، پاک‌کن **فضای ذخیره‌سازی**، **به‌روزرسانی** اپ و هستهٔ هرمس.
- فارسی + English، هفت تم رنگی روشن و تیره، و توی پس‌زمینه هم کار می‌کنه.

## 🚀 شروع سریع

1. **[APK رو دانلود کن](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest)** و نصبش کن.
2. هرمس رو باز کن ← **شروع** ← انتخاب کن کجا اجرا بشه:
   - **لینوکس داخلی** ← **Install Hermes**. تا خودش رو راه بندازه اپ رو باز نگه دار (یک بار، حدود ۱ گیگ جای خالی و اینترنت پایدار).
   - **ترموکس** ← مراحلی که اپ نشون می‌ده رو برای نصب Hermes توی Termux دنبال کن.
   - **سرور** ← سرور رو با یک دستور آماده کن و از اپ وارد شو: [راهنمای سرور](server/README.fa.md).
3. ارائه‌دهندهٔ هوش مصنوعی رو انتخاب کن، کلید API رو بچسبون (یا با حساب وارد شو) و یک مدل انتخاب کن.
4. سلام کن. 👋

**پیش‌نیاز:** اندروید ۱۰ به بالا روی گوشی arm64 یا x86_64.

> [!CAUTION]
> هرمس دستورهای واقعی اجرا می‌کنه. تأیید فرمان‌ها رو روشن نگه دار و کلید API رو هیچ‌وقت توی اسکرین‌شات یا issue نذار.

<details>
<summary><b>رفع مشکل</b></summary>

- **نصب وسط کار متوقف شد یا خطا داد**: جا خالی کن (حداقل ۱ گیگ)، اینترنت رو چک کن و دوباره Install رو بزن.
- **با خاموش شدن صفحه ایجنت می‌ایسته**: تنظیمات ← برنامه‌ها ← Hermes ← باتری ← **بدون محدودیت**.
- **به سرور وصل نمی‌شه**: گوشی و سرور باید با یک حساب Tailscale وارد شده باشن؛ [راهنمای سرور](server/README.fa.md) رو ببین.
- **گزارش باگ**: نسخهٔ اندروید، مدل گوشی، حالتی که استفاده می‌کنی و لاگ نصب/اتصال رو بفرست.
</details>

## 🛠 ساخت از سورس

```bash
git clone https://github.com/traveler3022/Hermes-Pocket.git
cd Hermes-Pocket
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # تست‌ها
```

JDK 17 و Android SDK 36 لازمه. Kotlin · Jetpack Compose · Material 3 · Hilt · OkHttp.
هر push روی GitHub Actions ساخته می‌شه و در ریلیز [`debug-latest`](https://github.com/traveler3022/Hermes-Pocket/releases/tag/debug-latest) منتشر می‌شه.
اسکرین‌شات‌های بالا با workflow **README Screenshots** مستقیم از کد UI خود اپ رندر می‌شن.

---

## 📄 مجوز

**GPLv3** با چند شرط اضافه (نام سازنده باید بمونه): فایل‌های [LICENSE](LICENSE) و [ADDITIONAL_TERMS.md](ADDITIONAL_TERMS.md) رو ببین.

</div>

<p align="center" dir="rtl">
  <sub>پروژهٔ مستقل · وابسته به Nous Research نیست · «Hermes Agent» متعلق به سازندگانشه.</sub>
</p>
