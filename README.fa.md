<div align="right" dir="rtl">

# ⬡ Hermes2 — نسخه Termux

کلاینت اندروید [Hermes Agent](https://github.com/NousResearch/hermes-agent).  
اتصال به ایجنت هوش مصنوعی روی VPS از طریق گوشی.

Material 3 · Kotlin/Compose · بدون سرور واسط

[![دانلود APK](https://img.shields.io/badge/⬇_دانلود_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)
[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)

</div>

---

## این چیه

یه اپ اندروید Material 3 که به Hermes Agent روی VPS وصل میشه.  
ایجنت روی سرورته — اپ فقط کنترل از راه دورشه.

**این نسخه برای استفاده ریموت (VPS) طراحی شده.**  
اگه می‌خوای ایجنت روی خود گوشی اجرا بشه، برو سراغ [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket).

## امکانات

- چت زنده با streaming و نمایش reasoning
- مدیریت سشن‌ها — جستجو، pinn، rename، resume
- کانفیگ مدل، provider، ابزارها و memory از داخل اپ
- مدیریت تسک‌ها، پروژه‌ها، صورتحساب، پلاگین‌ها، pets
- Material 3 · دارک/لایت · فارسی و انگلیسی
- سرویس پس‌زمینه برای اتصال مداوم

## شروع کار

### راه‌اندازی سرور

۱. Hermes Agent رو روی VPS نصب کن
۲. بزن: `hermes serve --host 127.0.0.1 --port 9119`
۳. (دلخواه) Caddy یا nginx واسه دسترسی از بیرون

### راه‌اندازی اپ

۱. APK رو از [Releases](https://github.com/traveler3022/Hermes-android-termux-/releases) دانلود کن
۲. نصب کن و باز کن
۳. آدرس سرور و توکن رو وارد کن
۴. شروع کن به چت

## بیلد از سورس

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**نیازمندی:** JDK 17 · Android SDK 35

## حریم خصوصی

- API key و سشن‌ها روی VPS خودت می‌مونن
- پیام‌ها به provider مدلی که انتخاب کردی می‌رن
- بدون حساب · بدون telemetry · بدون سرور واسط

## مجوز

MIT

*پروژه مستقل · وابسته به Nous Research نیست*
