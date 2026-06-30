<div dir="rtl">

# راهاندازی اولیه Hermes در Termux

بعد از [نصب هرمس](INSTALL_HERMES_TERMUX.md)، باید کلید API و مدل را تنظیم کنی.

</div>

---

## Set your API key

<div dir="rtl">

کلیدت را در فایل `.env` ذخیره کن:

</div>

```bash
nano ~/.hermes/.env
```

<div dir="rtl">

یکی از اینها را اضافه کن (بسته به ارائهدهندهات):

</div>

### Xiaomi MiMo (recommended, low-cost)

```env
XIAOMI_API_KEY=your_k...XIAOMI_BASE_URL=https://api.xiaomimimo.com/v1
```

### Google Gemini

```env
GEMINI_API_KEY=your_k...OpenRouter

```env
OPENROUTER_API_KEY=your_k...````

<div dir="rtl">

ذخیره: `Ctrl+O` → `Enter` → `Ctrl+X`

---

## انتخاب مدل

</div>

### For MiMo

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

Model names to try: `mimo-v2.5-free` → `mimo-v2.5` → `mimo-v2.5-pro`

### For Gemini

```bash
hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

---

## Test it

```bash
hermes chat "hello"
```

<div dir="rtl">

اگر جواب گرفتی، هرمس آماده است.

> [!CAUTION]
> **کلیدت را خصوصی نگه دار.** هرگز توی اسکرینشات، چت یا issue عمومی paste نکن. اگر لو رفت → فوراً در داشبورد ارائهدهنده باطلش کن.

---

**→ بعد: [راهاندازی Gateway](GATEWAY_SETUP.md)**
</div>
