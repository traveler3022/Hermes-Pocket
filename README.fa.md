<div align="right" dir="rtl">

# ⬡ Hermes2 — اندروید

کلاینت Material 3 اندروید برای [Hermes Agent](https://github.com/NousResearch/hermes-agent).  
اجرای ایجنت هوش مصنوعی شخصی روی گوشی 🤖📱

[![دانلود APK](https://img.shields.io/badge/⬇_دانلود_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)
[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)

</div>

---

## معرفی

اپ اندروید برای اجرای Hermes Agent روی گوشی با Termux.  
همچنین میتونه به ایجنت روی VPS وصل بشه.

## امکانات

- چت زنده
- مدیریت سشن‌ها
- کانفیگ کامل مدل و provider و ابزارها از داخل اپ
- Tasks, Projects, Billing, Plugins, Pets
- Material 3 · دارک/لایت · فارسی و انگلیسی
- سرویس پس‌زمینه

## شروع سریع

۱. Termux رو از [F-Droid](https://f-droid.org/packages/com.termux/) نصب کن
۲. تو Termux بزن: `mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties` و Force Stop کن
۳. APK رو از [Releases](https://github.com/traveler3022/Hermes-android-termux-/releases) دانلود و نصب کن
۴. اپ رو باز کن → Runtime Setup → Install Hermes Agent
۵. API key رو اضافه کن و شروع کن

## بیلد از سورس

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**نیازمندی:** JDK 17 · Android SDK 35

## مجوز

MIT

*پروژه مستقل · وابسته به Nous Research نیست*
