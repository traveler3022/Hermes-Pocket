<p align="center">
  <img src="https://img.shields.io/badge/⬡-Hermes2-6750A4?style=for-the-badge&labelColor=1a1a2e&color=6750A4" height="48px"/>
  <br><br>
  <b>همراه نیتیو اندروید برای Hermes Agent</b>
  <br>
  <sub>یک اتاق فرمان متمرکز برای ایجنت هوش مصنوعی — کاملاً روی گوشی.</sub>
  <br><br>
  <a href="https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml"><img src="https://github.com/traveler3022/Hermes2/actions/workflows/build-apk.yml/badge.svg" alt="Build"></a>
  <a href="https://github.com/traveler3022/Hermes2/releases/tag/debug-latest"><img src="https://img.shields.io/badge/⬇_Download-APK-6750A4?style=flat-square&logo=android&logoColor=white" alt="Download APK"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square" alt="License"></a>
  <br><br>
  <a href="README.md">English 🇬🇧</a> · <a href="README.fa.md">فارسی</a>
</p>

<div dir="rtl">

---

## این چیه؟

**[Hermes Agent](https://github.com/NousResearch/hermes-agent)** یک ایجنت هوش مصنوعی متنباز از [Nous Research](https://nousresearch.com) است که محلی روی دستگاه اجرا میشود. کد مینویسد، دستور اجرا میکند، فایل مدیریت میکند، وب مرور میکند، کار را به زیرایجنتها واگذار میکند و به دهها ابزار وصل میشود.

**Hermes2** آن ایجنت را به اندروید میآورد — یک اپ نیتیو Material 3. اپ جلوی صحنه است — Hermes داخل Termux مغز. تمام ارتباطات روی گوشی میماند.

**بدون واسطهی ابری · بدون حساب · بدون تلهمتری**

---

## 💪 قابلیتها

- 💬 **چت زنده** با پاسخهای استریم، نمایش استدلال، و کارتهای فراخوانی ابزار
- 🗂️ **مدیریت سشن** — جستوجو، سنجاق، تغییرنام، ادامهی هر گفتوگوی قبلی
- ✅ **تأیید ابزار** بهصورت اعلان اندروید — Approve یا Deny قبل از هر اجرا
- ⚙️ **Runtime Setup** — تشخیص، نصب و استارت gateway از داخل اپ
- 🎨 **۶ تم رنگی**، حالت روشن/تاریک/سیستم، طراحی کامل Material 3
- 🌐 **دوزبانه** — انگلیسی و فارسی
- 🔋 **Foreground service** — زنده نگهداشتن gateway وقتی صفحه خاموش است
- 📤 **Share intent** — فرستادن متن از هر اپی به چت هرمس۲


---

## 🛡️ حریم خصوصی و امنیت

**روی گوشی میماند:** کلید API تو در تنظیمات Hermes در Termux ذخیره میشود. ارتباط اپ و ایجنت روی `127.0.0.1` اجرا میشود — هرگز دستگاه را ترک نمیکند.

**خارج میشود:** پیامهایت به ارائهدهندهی مدل میرود (Gemini → گوگل، OpenRouter → مختلف). این طبیعت کار هر API هوش مصنوعی است.

```
تو ← Hermes2 ← ارائهدهنده هوش مصنوعی (مثلاً گوگل)
        │
        └─ کلید API فقط روی گوشیت میمونه ✅
```

> ⚠️

*

**تأیید ابزار را روشن نگه دار** — خط دفاعی توست. در شک، Deny بزن.

---

## 📚 مستندات

| | |
|---|---|
| **[راهنمای فنی کامل](docs/RUNNING_ON_ANDROID_TERMUX.md)** | نصب، تنظیم، اولین اتصال و عیبیابی |
| **[نصب در Termux](docs/INSTALL_HERMES_TERMUX.md)** | نصب گام‌به‌گام |
| **[ویزارد راهاندازی](docs/SETUP_HERMES_TERMUX.md)** | راهنمای `hermes setup` |
| **[اتصال اول](docs/GATEWAY_SETUP.md)** | وصلکردن اپ به هرمس |
| **[مستندات رسمی](https://hermes-agent.nousresearch.com/docs)** | مستندات بالادستی |

---

## 📸 اسکرینشات

<p align="center">
  <img src="screenshots/Screenshot_۲۰۲۶۰۷۰۱_۱۱۵۸۵۶_Hermes.jpg" width="150" alt="چت"/>
  <img src="screenshots/Screenshot_۲۰۲۶۰۷۰۱_۱۱۴۷۰۰_Hermes.jpg" width="150" alt="نشستها"/>
  <img src="screenshots/Screenshot_۲۰۲۶۰۷۰۱_۱۱۴۲۲۴_Hermes.jpg" width="150" alt="ابزارها"/>
  <img src="screenshots/Screenshot_۲۰۲۶۰۷۰۱_۱۱۴۲۲۱_Hermes.jpg" width="150" alt="مدلها"/>
  <img src="screenshots/Screenshot_۲۰۲۶۰۷۰۱_۱۱۴۲۱۳_Hermes.jpg" width="150" alt="تنظیمات"/>
</p>

---

## ❓ سؤالات متداول

<details>
<summary><b>اپ روی «Connecting...» گیر کرده</b></summary>

استارت سرد ۳۰–۹۰ ثانیه طول میکشد. لاگ: `cat ~/.hermes/logs/gateway_stdout.log`. اگر هرمس در حال اجراست ولی اپ وصل نمیشود، Termux را force-stop کن.
</details>

<details>
<summary><b>قطعشدن وقتی صفحه خاموش است</b></summary>

تنظیمات → برنامهها → [برنامه] → باتری → بدون محدودیت.
</details>

<details>
<summary><b>چه مدلهایی پشتیبانی میشود؟</b></summary>

هر ارائهدهندهی سازگار با OpenAI: Gemini، OpenRouter، Claude، Mistral، Groq، Ollama، DeepSeek و بیشتر.
</details>

---

## 🛠️ ساخت از سورس

</div>

```bash
git clone https://github.com/traveler3022/Hermes2.git
cd Hermes2
bash ./gradlew :app:assembleDebug
```

<div dir="rtl">

نیاز: JDK 17 · Android SDK 35 · Android Studio Ladybug+

---

## 🤝 مشارکت

issue و PR خوشآمدند. پورت مستقل جامعهمحور — نه محصول رسمی Nous Research. موقع گزارش باگ، نسخهی اندروید، مدل گوشی و لاگهای مرتبط را بگذار.

---

## 📄 لایسنس

**MIT** — [LICENSE](LICENSE).

<sub>پروژهی مستقل · وابسته به Nous Research نیست · «Hermes Agent» متعلق به نویسندگان آن است.</sub>

<p align="center">
  <br>
  <b>⬡ ساختهشده برای اندروید · با قدرت Hermes Agent ⬡</b>
</p>
</div>
