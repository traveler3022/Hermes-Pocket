<div dir="rtl">

# راه‌اندازی Hermes Agent روی VPS

این راهنما نصب و راه‌اندازی **Hermes Agent** روی یک سرور (VPS) رو پوشش میده. چون Hermes Pocket یه کلاینت اندرویده که به سرور وصل میشه، بدون سرور عملاً بی‌استفاده‌ست.

</div>

---

## پیش‌نیازها

- یه VPS با **Ubuntu 22.04+** یا **Debian 12+**
- یه **دامنه** (مثلاً `hermes.example.com`) که A record به IP سرور اشاره کنه
- دسترسی **SSH**
- حداقل **۱ گیگابایت RAM** (۲ گیگابایت پیشنهادی)

---

## ۱ — نصب Hermes Agent

SSH بزن به سرور:

```bash
ssh user@your-server-ip
```

پیش‌نیازهای سیستم:

```bash
sudo apt update && sudo apt install -y python3 python3-pip python3-venv git curl
```

کلون و نصب:

```bash
git clone https://github.com/NousResearch/hermes_agent.git
cd hermes_agent
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

---

## ۲ — اجرای ویزارد راه‌اندازی

اولین بار که `hermes` رو اجرا می‌کنی، یه ویزارد باز میشه:

```bash
hermes
```

تو ویزارد:

- **Quick Setup** (ورود با OAuth رایگان) یا **Full setup** (کانفیگ دستی) رو انتخاب کن
- ارائه‌دهنده مدل (مثلاً Gemini یا OpenAI) رو ست کن
- کلید API رو وارد کن
- ابزارها (tools) رو طبق نیاز فعال کن

---

## ۳ — تنظیم TLS reverse proxy

اپ موبایل فقط از `wss://` پشتیبانی می‌کنه، پس **حتماً** باید یه پراکسی معکوس با TLS داشته باشی. هرمس رو روی `127.0.0.1` (فقط لوکال) اجرا می‌کنیم و پراکسی ترافیک رو بهش می‌رسونه.

### گزینه الف — Caddy (ساده‌ترین، گواهی‌نامه خودکار)

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
```

حالا هرمس رو اجرا کن:

```bash
hermes dashboard --host 127.0.0.1 --port 8000
```

### گزینه ب — nginx

فایل `/etc/nginx/sites-available/hermes`:

```nginx
server {
    listen 443 ssl;
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
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/hermes /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

---

## ۴ — تنظیم session token

توی فایل کانفیگ هرمس (معمولاً `~/.hermes/config.yaml`):

```yaml
dashboard:
  session_token: "یک-توکن-تصادفی-و-امن-بساز"
```

یا به‌صورت متغیر محیطی:

```bash
export HERMES_DASHBOARD_SESSION_TOKEN="یک-توکن-تصادفی-و-امن-بساز"
```

> [!CAUTION]
> این توکن دقیقاً مثل پسورده — هر کی داشته باشه می‌تونه ایجنتت رو کنترل کنه. ازش توکن تصادفی بساز (مثلاً `openssl rand -hex 32`).

---

## ۵ — اجرای دائمی با systemd

که هرمس بعد از ری‌بوت یا قطع شدن خودکار دوباره بالا بیاد، یه سرویس systemd می‌سازیم:

فایل `/etc/systemd/system/hermes.service`:

```ini
[Unit]
Description=Hermes Agent
After=network.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/home/youruser/hermes_agent
Environment=HERMES_DASHBOARD_SESSION_TOKEN=یک-توکن-تصادفی-و-امن-بساز
ExecStart=/home/youruser/hermes_agent/.venv/bin/hermes dashboard --host 127.0.0.1 --port 8000
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now hermes
sudo systemctl status hermes
```

---

## ۶ — اتصال از اپ Hermes Pocket

۱. اپ رو باز کن
۲. برو به **Runtime**
۳. آدرس سرور رو بنویس: `wss://hermes.example.com`
۴. همون session token که ست کردی رو وارد کن
۵. **Save & Connect** بزن

از این به بعد هر بار اپ رو باز کنی خودکار وصل میشه.

---

<div dir="rtl">

> نکته: اگه ارائه‌دهنده مدل رو OAuth (Nous Portal) ست کردی، نیازی به کلید API جداگانه نداری. برای ارائه‌دهنده‌های دیگه کلید رو از پنل مربوطه بگیر و تو ویزارد وارد کن.

</div>
