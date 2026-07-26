<div align="center">

# ⬡ Hermes2 — Termux Edition

### نسخه مخصوص Termux 🤖📱

**Hermes2 Termux Edition** کلاینت اندروید برای [Hermes Agent](https://github.com/NousResearch/hermes-agent) — 
سرویس‌دهنده‌ای که روی VPS شما اجرا می‌شه و از طریق گوشی باهاش حرف می‌زنید.

<br>

[![Download APK](https://img.shields.io/badge/⬇_Download_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)

[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-00BCD4?style=flat-square)](LICENSE)
![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=flat-square&logo=materialdesign&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)

**Not affiliated with Nous Research · اپ مستقل از Hermes Agent اصلی**

</div>

---

## 🤔 این چیه؟

Hermes2 یه **اپ اندروید ۱۵ صفحه‌ای** برای ارتباط با **Hermes Agent** هست — ولی نه روی خود گوشی،
بلکه روی یه سرور (VPS) یا داخل Termux روی گوشی اجرا میشه.

این نسخه (`Hermes-android-termux-`) از کدهای **Hermes-Pocket** ارتقا یافته ولی برای **مود Remote (سرور راه دور)** بهینه شده.

> **اگه دنبال نسخه‌ای هستی که همه‌چیز رو خود گوشی اجرا کنه، برو سراغ [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket).**

---

## 🆕 تغییرات v3.0

| | قبلی | v3.0 |
|---|---|---|
| **حجم کد** | ~17,000 خط | **~28,000 خط** |
| **صفحه** | ۴ صفحه | **۱۵+ صفحه** |
| **تست** | ۰ فایل | **۳ فایل تست** |
| **دیتابیس** | ندارد | **۶ Repository** |

**صفحه‌های جدید:**
Tasks, Projects, Pets, Billing, Plugins, Memory Config,
Model Config, Provider Config, Tools Config, Sessions Detail,
Changes Sheet, Chat Approval Sheet, Runtime Setup Content

**رفکتور:** ChatScreen به ۷ فایل مجزا تقسیم شده

---

## ✨ قابلیت‌ها

- 💬 چت زنده با streaming
- 🛠️ ایجنت دستور اجرا می‌کنه، فایل می‌نویسه، جستجو می‌کنه
- ✅ تأیید دستورات قبل از اجرا (Notification)
- 🗂️ سشن‌ها — جستجو، pinn, rename, resume
- 🎨 Material 3 با ۶ تم رنگی — انگلیسی + فارسی
- 🔋 کار در پس‌زمینه (Foreground Service)
- 📎 ارسال فایل و عکس به ایجنت

---

## ⚡ شروع کار

**مود Remote (روی VPS):**

۱. یه VPS با `hermes serve` روشن کن
۲. از این اپ بهش وصل شو
۳. بزن بریم 🚀

**مود Termux (روی گوشی):**

به [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket) مراجعه کن — نسخه مخصوص اجرای مستقیم روی گوشی.

---

## 🛡️ حریم خصوصی

- API key تو فقط بین اپ و VPS/Termux می‌گرده — نه هیچ جای دیگه
- پیام‌ها می‌رن به provider مدلی که انتخاب کردی (همونطور که هر اپ AI دیگه‌ای کار می‌کنه)
- بدون حساب کاربری · بدون telemetry · بدون cloud middleman

---

## 🏗️ معماری

```
UI (Compose) ─► ViewModel ─► HermesRuntime (interface)
                         └─► GatewayClient  (interface)
                                  │ Hilt DI
                                  ▼
                         TermuxBridge / RemoteBridge
                                  ▼
                         Hermes Agent (VPS یا Termux)
```

کد UI و ViewModel فقط به **اینترفیس‌ها** وابسته هستند — عوض کردن runtime فقط یه DI module رو تغییر میده.

### بیلد از سورس

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
```

**نیازمندی:** JDK 17 · Android SDK 35 · Android Studio Ladybug+

---

## 📄 License

**MIT** — see [LICENSE](LICENSE).

<sub>Independent project · not affiliated with Nous Research · "Hermes Agent" belongs to its respective authors.</sub>

<p align="center">
  <br>
  <b>⬡ Built for Android · Powered by Hermes Agent ⬡</b>
</p>
