<div align="right" dir="rtl">

# ⬡ Hermes2 — نسخه Termux

### کلاینت اندروید Hermes Agent مخصوص VPS و Termux 🤖📱

<br>

[![دانلود APK](https://img.shields.io/badge/⬇_دانلود_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)

[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)
![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=flat-square&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)

**اپ مستقل · وابسته به Nous Research نیست**

</div>

---

## ℹ️ توضیح

این اپ یه **کلاینت ۱۵ صفحه‌ای** برای [Hermes Agent](https://github.com/NousResearch/hermes-agent) هست.
ایجنت روی یه **VPS** یا داخل **Termux** اجرا میشه و شما از طریق این اپ باهاش حرف می‌زنید.

**این نسخه از روی Hermes-Pocket ارتقا یافته** ولی برای استفاده با سرور راه دور (Remote) بهینه شده:
فایل‌های Remote runtime، سیستم صورتحساب، Pets، پروژه‌ها، تسک‌ها، کانفیگ کامل providerها و مدل‌ها
همش اضافه شده.

> **اگه می‌خوای همه‌چیز رو خود گوشی اجرا کنی (بدون VPS)، [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket) رو ببین.**

---

## 🆕 تغییرات v3.0

**از v0.2 به v3.0:**

- **+۱۵٬۷۶۳ خط کد新增 · ۶۵٪ رشد** (از ۱۷K به ۲۸K خط)
- **۴ → ۱۵+ صفحه** (Tasks, Projects, Pets, Billing, Plugins, Memory, Model Config, Provider Config, Tools Config, ...)
- **دیتا لایه کامل** · ۶ Repository (Session, Billing, Pet, Project, Task, Completion Tracker)
- **رفع باگ NonExistentClass** — فایل‌های RemoteServerConfig + RemoteServerSettings اضافه شدن
- **رفکتور ChatScreen** به ۷ فایل مجزا
- **تست** · ۳ فایل تست جدید
- **DesignSystem** اختصاصی

---

## ✨ امکانات

- 💬 **چت زنده** با streaming + نمایش reasoning
- 🛠️ **ایجنت عملی** — دستور اجرا می‌کنه، فایل می‌نویسه، جستجو می‌کنه
- ✅ **تأیید دستورات** — هیچ کاری بدون اجازه‌ت انجام نمیشه
- 🗂️ **سشن‌ها** — جستجو، pinn, rename, resume
- 🎨 **Material 3** — ۶ تم رنگی، دارک/لایت، فارسی + انگلیسی
- 🔋 **کار در پس‌زمینه** — حتی با صفحه خاموش
- 📎 **ارسال فایل و عکس** به ایجنت

---

## 🚀 شروع

**روی VPS (مود Remote):**
۱. VPS داشته باش با `hermes serve` روشن
۲. اپ رو نصب کن
۳. آدرس سرور و توکن رو بزن → وصل شو

---

## 🛡️ حریم خصوصی

- API key فقط بین اپ و VPS می‌گرده
- از گوشی خارج نمیشه مگر پیام‌هایی که به provider مدل می‌ره
- بدون حساب کاربری · بدون telemetry · بدون سرور واسط

---

## 🏗️ بیلد از سورس

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

نیازمندی: JDK 17 · Android SDK 35

---

## 📄 مجوز

**MIT** — فایل [LICENSE](LICENSE)

<sub>پروژه مستقل · وابسته به Nous Research نیست · "Hermes Agent" متعلق به نویسندگانش است.</sub>

<p align="center">
  <br>
  <b>⬡ ساخته شده برای اندروید · با قدرت Hermes Agent ⬡</b>
</p>
