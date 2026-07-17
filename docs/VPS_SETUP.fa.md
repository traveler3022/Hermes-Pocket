<div dir="rtl">

# راه‌اندازی Hermes Agent روی VPS

این راهنما نصب و راه‌اندازی **Hermes Agent** روی یک سرور (VPS) رو قدم‌به‌قدم پوشش میده. چون Hermes Pocket یه کلاینت اندرویده که به سرور وصل میشه، بدون سرور عملاً بی‌استفاده‌ست.

</div>

---

## پیش‌نیازها

| مورد | حداقل واقعی | پیشنهادی |
|------|---------------|----------|
| سیستم‌عامل | Ubuntu 22.04 / Debian 12 | Ubuntu 24.04 LTS |
| RAM | 1 گیگابایت (همراه swap) | 2 گیگابایت |
| CPU | ۱ هسته | ۲ هسته |
| فضای دیسک | 20 گیگابایت آزاد | 30 گیگابایت |
| دسترسی | SSH sudo user | sudo user |
| دامنه | اختیاری (برای TLS) | اجباری برای امنیت کامل |

> [!WARNING]
> **۱ گیگابایت RAM کافی نیست.** Hermes Agent همراه با سیستم‌عامل و پراکسی معکوس، حدود ۶۰۰-۹۰۰ مگابایت رم اشغال می‌کنه. روی ۱ گیگ یا OOM killer برنامه رو می‌کشه یا مدام swap می‌کنه (بسیار کند). حداقل ۲ گیگابایت همراه با swap توصیه میشه.

---

## ۱ — اتصال به سرور و به‌روزرسانی

```bash
ssh user@your-server-ip
```

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y python3 python3-pip python3-venv git curl ufw
```

---

## ۲ — نصب Hermes Agent

```bash
git clone https://github.com/NousResearch/hermes_agent.git
cd hermes_agent
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -e .
```

بعد از نصب چک کن که دستور کار می‌کنه:

```bash
hermes --version
```

خروجی باید شبیه این باشه:

```
Hermes Agent v0.17.0
Python: 3.12.x
```

---

## ۳ — اجرای ویزارد راه‌اندازی اولیه

اولین بار که `hermes` رو اجرا می‌کنی، یه ویزارد تعاملی باز میشه:

```bash
hermes
```

گزینه‌های ویزارد:

| مرحله | گزینه پیشنهادی | توضیح |
|------|---------------|-------|
| Setup mode | **Full setup** | همه‌چیز دستی؛ برای کنترل کامل |
| Provider | هر ارائه‌دهنده‌ای که ترجیح می‌دی | کلید API خودت رو وارد کن |
| Terminal backend | **Local** | اجرا روی همین سرور |
| Platforms | هیچ‌کدام (ENTER) | بعداً از اپ تنظیم میشن |
| Tools | همه به‌جز Browser/Computer Use | روی سرور کار می‌کنن |
| Search provider | هر کدوم که دوست داری | بعضی رایگان و بدون کلیدن |

وقتی کلید API خواست، اونو وارد کن (نام متغیر محیطی بستگی به ارائه‌دهنده داره، مثلاً `YOUR_PROVIDER_API_KEY`).

ویزارد که تموم شد، مدل رو تست کن:

```bash
hermes doctor --fix
hermes -q "سلام، فقط در یک جمله بگو با چه مدلی جواب می‌دهی."
```

اگر جواب گرفتی، هرمس آماده‌ست.

---

## ۴ — تنظیم TLS reverse proxy

اپ موبایل فقط از `wss://` (WebSocket رمزنگاری‌شده) پشتیبانی می‌کنه. پس **حتماً** باید یه پراکسی معکوس داشته باشی که ترافیک رو از HTTPS به پورت محلی هرمس برسونه.

### گزینه الف — Caddy (ساده‌ترین، گواهی خودکار)

نصب Caddy:

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

فایل `/etc/caddy/Caddyfile` رو اینطوری بنویس (پورت هرمس رو ۸۰۰۰ فرض می‌کنیم):

```
hermes.example.com {
    reverse_proxy localhost:8000
}
```

```bash
sudo systemctl reload caddy
sudo systemctl enable caddy
```

حالا هرمس رو اجرا کن (روی لوکال‌هاست، نه روی IP عمومی):

```bash
hermes dashboard --host 127.0.0.1 --port 8000
```

Caddy خودش گواهی Let's Encrypt رو می‌گیره و HTTPS می‌سازه. حالا آدرس `wss://hermes.example.com` در دسترسه.

### گزینه ب — nginx + Certbot

نصب:

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

فایل `/etc/nginx/sites-available/hermes`:

```nginx
server {
    listen 443 ssl http2;
    server_name hermes.example.com;

    ssl_certificate     /etc/letsencrypt/live/hermes.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/hermes.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/hermes /etc/nginx/sites-enabled/
sudo certbot --nginx -d hermes.example.com
sudo nginx -t && sudo systemctl reload nginx
```

---

## ۵ — تنظیم Session Token

توی فایل کانفیگ هرمس (معمولاً `~/.hermes/config.yaml`):

```yaml
dashboard:
  session_token: "یک-توکن-تصادفی-و-امن-بساز"
```

یا به‌صورت متغیر محیطی (پیشنهادی برای systemd):

```bash
export HERMES_DASHBOARD_SESSION_TOKEN="یک-توکن-تصادفی-و-امن-بساز"
```

توکن رو با این دستور بساز:

```bash
openssl rand -hex 32
```

> [!CAUTION]
> این توکن دقیقاً مثل پسورده — هر کی داشته باشه می‌تونه ایجنتت رو کنترل کنه. از متغیر محیطی یا فایل کانفیگ محافظت کن.

---

## ۶ — اجرای دائمی با systemd

که هرمس بعد از ری‌بوت یا قطع شدن خودکار دوباره بالا بیاد:

فایل `/etc/systemd/system/hermes.service`:

```ini
[Unit]
Description=Hermes Agent
After=network.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/home/youruser/hermes_agent
Environment=HERMES_DASHBOARD_SESSION_TOKEN=توکنی-که-ساختی
ExecStart=/home/youruser/hermes_agent/.venv/bin/hermes dashboard --host 127.0.0.1 --port 8000
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now hermes
sudo systemctl status hermes
```

خروجی باید `active (running)` رو نشون بده.

---

## ۷ — فایروال

اگر `ufw` فعاله، فقط پورت‌های ۸۰ و ۴۴۳ رو باز کن (نه ۸۰۰۰):

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

---

## ۸ — اتصال از اپ Hermes Pocket

۱. اپ رو باز کن
۲. برو به **Runtime**
۳. آدرس سرور رو بنویس: `wss://hermes.example.com`
۴. همون session token که ست کردی رو وارد کن
۵. **Save & Connect** بزن

از این به بعد هر بار اپ رو باز کنی خودکار وصل میشه.

---

## اتصال با IP (بدون دامنه)

اگر دامنه نداری، **باز هم میشه** وصل شد ولی با محدودیت — چون CAهای عمومی (مثل Let's Encrypt) به آدرس IP گواهی **نمی‌دن** و فقط نام دامنه رو امضا می‌کنن. پس با IP فقط دو تا راه داری:

### روش ۱ — استفاده از `ws://` (بدون رمزنگاری)

توی فایل کانفیگ هرمس، پورت رو روی IP عمومی باز کن:

```bash
hermes dashboard --host 0.0.0.0 --port 8000
```

و توی اپ آدرس رو `ws://your-server-ip:8000` بنویس.

> [!WARNING]
> این روش توکن رو **plaintext** میفرسته. فقط برای تست در شبکه امن استفاده کن. هرگز در محیط عمومی پیشنهاد نمیشه.

### روش ۲ — گواهی self-signed (پیشنهادی‌تر)

یه گواهی self-signed با openssl می‌سازی که روی خودِ سرورت امضا میشه (نیازی به هیچ سرویس خارجی نداره):

```bash
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/ssl/private/hermes.key \
  -out /etc/ssl/certs/hermes.crt \
  -subj "/CN=your-server-ip"
```

بعد توی nginx/Caddy تنظیمش کن و آدرس `wss://your-server-ip` رو توی اپ وارد کن. چون گواهی توسط CA معتبری امضا نشده، اپ هشدار میده — باید بهش اعتماد کنی (یا گواهی رو به صورت دستی روی گوشی نصب کنی).

### روش ۳ — دامنه + گواهی معتبر (امن‌ترین راه)

اگر می‌خوای اتصال کاملاً معتبر و بدون هشدار باشه، باید یه نام دامنه داشته باشی که به IP سرورت اشاره کنه. مراحل کلی:

**۱. تهیه دامنه**
یه نام دامنه از هر ارائه‌دهنده‌ای که دوست داری بخر (یا از سرویس‌هایی که زیرمجموعه رایگان می‌دن استفاده کن). انتخابش کاملاً سلیقه خودته.

**۲. اشاره دامنه به IP سرور**
توی پنل مدیریت دامنه، یه رکورد **A** بساز که نام دامنه‌ت (مثلاً `hermes.example.com`) رو به آدرس IP سرورت هدایت کنه:

```
Type: A
Name: hermes (یا @)
Value: your-server-ip
TTL: 300 (یا پیش‌فرض)
```

بعد از چند دقیقه (تا یک ساعت)، دامنه به سرورت اشاره می‌کنه. چک کن:

```bash
dig +short hermes.example.com
# باید IP سرورت رو برگردونه
```

**۳. دریافت گواهی رایگان**
وقتی دامنه به IP اشاره کرد، با ابزارهای مدیریت گواهی (مثل certbot یا Caddy) یه گواهی رایگان از یه CA عمومی می‌گیری. این ابزارها خودشون فرآیند اثبات مالکیت دامنه رو انجام می‌دن و گواهی رو صادر می‌کنن.

**۴. تنظیم پراکسی**
توی nginx/Caddy آدرس دامنه رو بذار و فایل‌های گواهی (fullchain + private key) رو که ابزار مدیریت گواهی ساخته معرفی کن.

**۵. اتصال از اپ**
توی اپ آدرس رو `wss://hermes.example.com` بنویس. چون گواهی توسط CA معتبر امضا شده، اپ بدون هیچ هشداری وصل میشه.

> [!NOTE]
> انتخاب ارائه‌دهنده دامنه، ابزار مدیریت گواهی، و نحوه تنظیم رکوردهای DNS کاملاً سلیقه‌ خودته — اینجا فقط مراحل کلی توضیح داده شد تا بدونی چه مسیری رو باید بری.

---

## عیب‌یابی (Troubleshooting)

### مشکل ۱ — اپ وصل نمیشه (Connection failed)

**علامت:** توی اپ خطای «Connection failed» یا timeout.

**دلایل محتمل:**
- هرمس روی سرور اجرا نمیشه → چک کن: `sudo systemctl status hermes`
- پورت ۸۰۰۰ بسته‌ست → چک کن فایروال: `sudo ufw status`
- دامنه به IP اشاره نمی‌کنه → چک کن: `dig hermes.example.com`
- گواهی منقضی شده → چک کن: `sudo certbot renew`

**راه‌حل:**
```bash
# لاگ هرمس رو ببین
journalctl -u hermes -f

# تست دستی اتصال
curl -v https://hermes.example.com
```

---

### مشکل ۲ — خطای احراز هویت (Auth failed)

**علامت:** اپ وصل میشه ولی پیام «Invalid session token» میده.

**دلیل:** توکنی که توی اپ وارد کردی با توکن سرور یکی نیست.

**راه‌حل:**
```bash
# توکن فعلی رو ببین
grep session_token ~/.hermes/config.yaml

# یا از متغیر محیطی
systemctl show hermes --property=Environment
```

مطمئن شو همون توکن دقیقاً توی اپ وارد شده.

---

### مشکل ۳ — WebSocket upgrade نمیشه

**علامت:** اتصال شروع میشه ولی بلافاصله قطع میشه.

**دلیل:** پراکسی `Upgrade` header رو درست فوروارد نمی‌کنه.

**راه‌حل (nginx):**
مطمئن شو این خطوط تو تنظیمات هست:
```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

برای Caddy این خطوط به‌صورت پیش‌فرض هستن.

---

### مشکل ۴ — هرمس بعد از ری‌بوت بالا نمیاد

**علامت:** سرور رو ری‌بوت می‌کنی، اپ وصل نمیشه.

**دلیل:** سرویس systemd enable نشده یا خطایی توی فایل سرویس هست.

**راه‌حل:**
```bash
sudo systemctl enable hermes
sudo systemctl status hermes
# اگه failed بود:
journalctl -u hermes -n 50
```

---

### مشکل ۵ — خطای Rust/build موقع نصب

**علامت:** `pip install -e .` با خطای rustc crash میشه.

**دلیل:** این فقط روی Termux/اندروید اتفاق میفته. روی VPS معمولی نباید ببینی.

**راه‌حل:** مطمئن شو روی سرور اوبونتو/دبیان هستی نه Termux. اگر روی VPS داری این خطا رو می‌بینی، `build-essential` رو نصب کن:
```bash
sudo apt install -y build-essential
```

---

### مشکل ۶ — مدل جواب نمیده

**علامت:** `hermes doctor` میگه کلید ست نشده.

**راه‌حل:**
```bash
hermes config set model.provider <provider-name>
hermes config set model.default <model-id>
hermes doctor
```

باید `✓ <provider> (key configured)` ببینی.

---

<div dir="rtl">

> نکته نهایی: ارائه‌دهنده مدل رو خودت انتخاب کن و کلید API‌اش رو از پنل مربوطه بگیر و تو ویزارد وارد کن. بعضی ارائه‌دهنده‌ها ورود با OAuth دارن (نیاز به کلید جداگانه ندارن)، بقیه کلید مستقیم می‌خوان.

</div>
