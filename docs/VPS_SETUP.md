# Setting up Hermes Agent on a VPS

This guide walks through installing and running **Hermes Agent** on a server (VPS), step by step. Since Hermes Pocket is an Android client that connects to your server, it is essentially useless without one.

---

## Prerequisites

| Item | Realistic minimum | Recommended |
|------|-------------------|-------------|
| OS | Ubuntu 22.04 / Debian 12 | Ubuntu 24.04 LTS |
| RAM | 1 GB (swap) | 2 GB |
| CPU | 1 core | 2 cores |
| Disk | 5 GB free | 10 GB |
| Access | SSH sudo user | sudo user |
| Domain | Optional (for TLS) | Required for full security |

> [!WARNING]
> **1 GB of RAM is not enough.** Hermes Agent, together with the OS and a reverse proxy, uses roughly 600-900 MB of RAM. On 1 GB the OOM killer will kill the process or it will constantly swap (very slow). At least 2 GB with swap is recommended.

---

## 1 — Connect to the server and update

```bash
ssh user@your-server-ip
```

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y python3 python3-pip python3-venv git curl ufw
```

---

## 2 — Install Hermes Agent

```bash
git clone https://github.com/NousResearch/hermes_agent.git
cd hermes_agent
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -e .
```

After installing, verify the command works:

```bash
hermes --version
```

Expected output is similar to:

```
Hermes Agent v0.17.0
Python: 3.12.x
```

---

## 3 — Run the initial setup wizard

The first time you run `hermes`, an interactive wizard opens:

```bash
hermes
```

Wizard options:

| Step | Suggested choice | Notes |
|------|------------------|-------|
| Setup mode | **Full setup** | Everything manual; for full control |
| Provider | whichever provider you prefer | enter your own API key |
| Terminal backend | **Local** | runs on this same server |
| Platforms | none (press ENTER) | configured later from the app |
| Tools | all except Browser/Computer Use | work on the server |
| Search provider | whichever you like | some are free and keyless |

When it asks for an API key, enter it (the env var name depends on the provider, e.g. `YOUR_PROVIDER_API_KEY`).

When the wizard finishes, test the model:

```bash
hermes doctor --fix
hermes -q "Hello, in one sentence tell me which model you answer with."
```

If you get a reply, Hermes is ready.

---

## 4 — Set up a TLS reverse proxy

The mobile app only supports `wss://` (encrypted WebSocket). So you **must** have a reverse proxy that forwards HTTPS traffic to the local Hermes port.

### Option A — Caddy (simplest, automatic certs)

Install Caddy:

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

Write `/etc/caddy/Caddyfile` like this (assuming Hermes runs on port 8000):

```
hermes.example.com {
    reverse_proxy localhost:8000
}
```

```bash
sudo systemctl reload caddy
sudo systemctl enable caddy
```

Now run Hermes (on localhost, not the public IP):

```bash
hermes dashboard --host 127.0.0.1 --port 8000
```

Caddy automatically obtains a Let's Encrypt certificate and serves HTTPS. The address `wss://hermes.example.com` is now available.

### Option B — nginx + Certbot

Install:

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

File `/etc/nginx/sites-available/hermes`:

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

## 5 — Configure the session token

In the Hermes config file (usually `~/.hermes/config.yaml`):

```yaml
dashboard:
  session_token: "generate-a-random-secure-token"
```

Or as an environment variable (recommended for systemd):

```bash
export HERMES_DASHBOARD_SESSION_TOKEN="generate-a-random-secure-token"
```

Generate the token with:

```bash
openssl rand -hex 32
```

> [!CAUTION]
> This token is exactly like a password — whoever has it can control your agent. Protect it via the env var or config file.

---

## 6 — Run persistently with systemd

So Hermes comes back up after a reboot or crash:

File `/etc/systemd/system/hermes.service`:

```ini
[Unit]
Description=Hermes Agent
After=network.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/home/youruser/hermes_agent
Environment=HERMES_DASHBOARD_SESSION_TOKEN=the-token-you-generated
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

The output should show `active (running)`.

---

## 7 — Firewall

If `ufw` is active, only open ports 80 and 443 (not 8000):

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

---

## 8 — Connect from the Hermes Pocket app

1. Open the app
2. Go to **Runtime**
3. Enter the server address: `wss://hermes.example.com`
4. Enter the same session token you set
5. Tap **Save & Connect**

From then on, every time you open the app it connects automatically.

---

## Connecting via IP (no domain)

If you don't have a domain, **you can still connect** but with a limitation — because public CAs (like Let's Encrypt) do **not** issue certificates to an IP address; they only sign a domain name. So with an IP you have two options:

### Option 1 — Use `ws://` (no encryption)

In the Hermes config, open the port on the public IP:

```bash
hermes dashboard --host 0.0.0.0 --port 8000
```

And in the app enter the address `ws://your-server-ip:8000`.

> [!WARNING]
> This method sends the token in **plaintext**. Use it only for testing on a trusted network. Never recommended in a public environment.

### Option 2 — Self-signed certificate (preferred)

You generate a self-signed certificate with openssl that is signed on your own server (no external service needed):

```bash
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/ssl/private/hermes.key \
  -out /etc/ssl/certs/hermes.crt \
  -subj "/CN=your-server-ip"
```

Then configure it in nginx/Caddy and enter the address `wss://your-server-ip` in the app. Because the certificate is not signed by a trusted CA, the app shows a warning — you must trust it (or install the certificate manually on the phone).

### Option 3 — Domain + valid certificate (most secure)

If you want a fully valid connection with no warnings, you need a domain name that points to your server's IP. General steps:

**1. Get a domain**
Buy a domain name from whatever provider you like (or use a service that gives free subdomains). The choice is entirely yours.

**2. Point the domain to your server IP**
In the domain's management panel, create an **A record** that directs your domain name (e.g. `hermes.example.com`) to your server's IP:

```
Type: A
Name: hermes (or @)
Value: your-server-ip
TTL: 300 (or default)
```

After a few minutes (up to an hour), the domain points to your server. Check:

```bash
dig +short hermes.example.com
# should return your server IP
```

**3. Obtain a free certificate**
Once the domain points to the IP, use a certificate management tool (like certbot or Caddy) to get a free certificate from a public CA. These tools perform the domain ownership proof themselves and issue the certificate.

**4. Configure the proxy**
In nginx/Caddy set the domain address and point to the certificate files (fullchain + private key) that the certificate tool created.

**5. Connect from the app**
In the app enter the address `wss://hermes.example.com`. Since the certificate is signed by a trusted CA, the app connects with no warning.

> [!NOTE]
> The choice of domain provider, certificate management tool, and how to set DNS records is entirely yours — only the general steps are described here so you know which path to take.

---

## Troubleshooting

### Problem 1 — App won't connect (Connection failed)

**Symptom:** App shows "Connection failed" or timeout.

**Possible causes:**
- Hermes not running on the server → check: `sudo systemctl status hermes`
- Port 8000 closed → check firewall: `sudo ufw status`
- Domain not pointing to IP → check: `dig hermes.example.com`
- Certificate expired → check: `sudo certbot renew`

**Fix:**
```bash
# view Hermes logs
journalctl -u hermes -f

# test connection manually
curl -v https://hermes.example.com
```

---

### Problem 2 — Authentication error (Auth failed)

**Symptom:** App connects but says "Invalid session token".

**Cause:** The token entered in the app differs from the server's token.

**Fix:**
```bash
# view current token
grep session_token ~/.hermes/config.yaml

# or from the environment variable
systemctl show hermes --property=Environment
```

Make sure the exact same token is entered in the app.

---

### Problem 3 — WebSocket upgrade fails

**Symptom:** Connection starts but drops immediately.

**Cause:** The proxy is not forwarding the `Upgrade` header correctly.

**Fix (nginx):**
Make sure these lines are in the config:
```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

For Caddy these lines are present by default.

---

### Problem 4 — Hermes doesn't start after reboot

**Symptom:** You reboot the server, the app can't connect.

**Cause:** The systemd service isn't enabled, or there's an error in the service file.

**Fix:**
```bash
sudo systemctl enable hermes
sudo systemctl status hermes
# if failed:
journalctl -u hermes -n 50
```

---

### Problem 5 — Rust/build error during install

**Symptom:** `pip install -e .` crashes with a rustc error.

**Cause:** This only happens on Termux/Android. On a normal VPS you shouldn't see it.

**Fix:** Make sure you're on Ubuntu/Debian server, not Termux. If you see this on a VPS, install `build-essential`:
```bash
sudo apt install -y build-essential
```

---

### Problem 6 — Model doesn't answer

**Symptom:** `hermes doctor` says the key isn't set.

**Fix:**
```bash
hermes config set model.provider <provider-name>
hermes config set model.default <model-id>
hermes doctor
```

You should see `✓ <provider> (key configured)`.

---

> Final note: choose the model provider yourself and get its API key from the relevant panel, then enter it in the wizard. Some providers use OAuth login (no separate key needed), others require a direct key.
