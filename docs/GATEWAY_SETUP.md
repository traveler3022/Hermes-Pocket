<div dir="rtl">

# اتصال Gateway به اپ — اولین بار

</div>

<div dir="rtl">

این مراحل را فقط **یکبار**، برای اولین اتصال انجام میدهی. بعد از آن، هر بار که اپ را باز کنی **خودکار وصل میشود** و دیگر لازمشان نداری.

</div>

---

## مرحله ۱ — آزادکردن پورت Termux

<div dir="rtl">

بعد از پایان [راهاندازی هرمس](SETUP_HERMES_TERMUX.md)، Termux یک خطفرمان تازه باز میکند. در آن بنویس:

</div>

```bash
hermes dashboard --stop
```

<div dir="rtl">

حالا **از Termux خارج شو** و آن را force-stop کن:

**تنظیمات اندروید → برنامهها → Termux → Force stop**

> **چرا force-stop؟** موقع نصب، Termux پورتهایی را اشغال میکند و رها نمیکند. با force-stop، تمام پورتهای فعال روی Termux غیرفعال میشوند تا اپ بتواند یک اتصال تمیز بگیرد. این فقط **دفعهی اول** لازم است.

---

## مرحله ۲ — اتصال از داخل اپ

۱. اپ **Hermes2** را باز کن
۲. روی **`Open runtime host app`** بزن → Termux باز میشود
۳. **برگرد به اپ Hermes2**
۴. روی **`Start Agent Gateway`** بزن
۵. **تا ۳۰ ثانیه صبر کن** — وضعیت به **✓ Connected** تغییر میکند

> **چرا از اپ استارت کنیم نه دستی؟**
> اپ خودش یک `HERMES_DASHBOARD_SESSION_TOKEN` تولید و تزریق میکند. اگر دستی `hermes dashboard` را با توکن دیگری استارت کنی، احراز هویت WebSocket شکست میخورد.

> [!TIP]
> اگر تا ۳۰ ثانیه وصل نشد، **مراحل ۱ و ۲ را دوباره تکرار کن.** دستدادن اول گاهی یکبار دوم لازم دارد — بعدش هر بار خودکار وصل میشود.

---

## از دفعهی دوم به بعد

<div dir="rtl">

فقط اپ را باز کن. خودش وصل میشود. نه Termux، نه force-stop، نه هیچ کار اضافی.

---

## بعد از اتصال

به **Settings** برو و چک کن:

</div>

```text
Provider: gemini (یا هر ارائهدهندهای که انتخاب کردی)
Model: gemini-2.5-flash / gemini-2.5-flash / gemini-2.5-pro
```

<div dir="rtl">

تب **Tools** هم باید ابزارهای فعال هرمس را نشان بدهد.

---

## اگر صفحه خاموش شد قطع شد

</div>

- **Settings → Apps → Hermes2 → Battery → Unrestricted**
- همین کار را برای **Termux** هم انجام بده

---

## عیبیابی

### لاگ gateway

```bash
cat "$HOME/.hermes/logs/gateway_stdout.log"
```

یا از داخل اپ: **Termux & Agent Connection → Fetch & View Logs**

### از کجا بفهمم درست کار میکنه؟

<div dir="rtl">

در Termux:

</div>

```bash
hermes doctor
```

<div dir="rtl">

باید `✓ gemini (key configured)` ببینی.

---

**← بازگشت: [README اصلی](../README.md)** · **[راهنمای فنی کامل](RUNNING_ON_ANDROID_TERMUX.md)**
</div>
