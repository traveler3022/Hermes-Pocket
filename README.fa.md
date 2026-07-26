<div align="right" dir="rtl">

# ⬡ Hermes2 — نسخه Termux

کلاینت اندروید [Hermes Agent](https://github.com/NousResearch/hermes-agent).  
اتصال به ایجنت روی VPS یا Termux.

[![دانلود v3.0](https://img.shields.io/badge/دانلود_APK-v3.0-6750A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/traveler3022/Hermes-android-termux-/releases/tag/v3.0)
[![Build](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/traveler3022/Hermes-android-termux-/actions/workflows/build-apk.yml)

Material 3 · Kotlin/Compose · بدون سرور واسط

</div>

---

## نصب

آخرین APK رو از [Releases](https://github.com/traveler3022/Hermes-android-termux-/releases) دانلود و نصب کن.

اپ به Hermes Agent وصل میشه که روی:
- **VPS** (پیشنهادی) — با `hermes serve` روی سرورت
- **Termux** روی خود گوشی — ببین [Hermes-Pocket](https://github.com/traveler3022/Hermes-Pocket)

---

## بیلد از سورس

```bash
git clone https://github.com/traveler3022/Hermes-android-termux-.git
cd Hermes-android-termux-
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**نیازمندی:** JDK 17 · Android SDK 35

---

## مجوز

MIT

