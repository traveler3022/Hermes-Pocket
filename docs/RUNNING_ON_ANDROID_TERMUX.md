# Running Hermes2 on Android + Termux

This is the practical runbook for bringing Hermes2 up on a real Android phone.

The goal is:

```text
Hermes2 Android app
  → starts/controls Hermes dashboard in Termux
  → connects to ws://127.0.0.1:9119/api/ws
  → uses Xiaomi MiMo / Gemini / other configured providers
```

---

## 0. What you need

- Android phone with Termux from F-Droid
- Enough storage for Hermes Agent and Python/Rust wheels
- Network access
- One model provider key, for example:
  - Xiaomi MiMo API key
  - Gemini API key
  - OpenRouter key

For the current tested setup we use:

```text
Xiaomi MiMo as main backend
DuckDuckGo/ddgs for free search
Local terminal backend
No messaging platforms
No browser automation / computer-use on Android
```

---

## 1. Install and prepare Termux

Install Termux from F-Droid, not Play Store.

Then in Termux:

```bash
pkg update -y
pkg upgrade -y
pkg install -y git python clang rust make pkg-config libffi openssl ca-certificates curl llvm lld nodejs ripgrep ffmpeg
```

Set Android/Rust build environment:

```bash
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

Why these variables matter:

| Variable | Why |
|---|---|
| `ANDROID_API_LEVEL` | Needed by maturin/jiter/pydantic-core builds on Android |
| `CARGO_BUILD_TARGET` | Forces Cargo to build for the native Android target |
| `CARGO_HOME=$HOME/.hermes/cargo` | Avoids broken user Cargo mirror configs such as USTC 404 |
| `CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse` | Uses crates.io sparse index |
| `CARGO_PROFILE_RELEASE_LTO=false` | Avoids Termux rustc ICE during pydantic-core builds |
| `CARGO_BUILD_JOBS=1` | Reduces phone memory/CPU pressure |

---

## 2. Allow Hermes2 to control Termux

Hermes2 uses Termux `RUN_COMMAND`. Enable external app commands once:

```bash
mkdir -p ~/.termux
cat > ~/.termux/termux.properties <<'EOF'
allow-external-apps=true
EOF
```

Then fully restart Termux.

Android may also ask the Hermes2 app for `RUN_COMMAND` permission. Grant it.

---

## 3. Clean old partial installs if needed

If a previous failed install left a non-git folder:

```bash
if [ -e "$HOME/.hermes/hermes-agent" ] && [ ! -d "$HOME/.hermes/hermes-agent/.git" ]; then
  mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.broken-$(date +%Y%m%d-%H%M%S)"
fi
```

If you want a fully fresh install:

```bash
mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.backup-$(date +%Y%m%d-%H%M%S)" 2>/dev/null || true
```

---

## 4. Install Hermes Agent manually

Clone official upstream:

```bash
mkdir -p "$HOME/.hermes"
git clone https://github.com/NousResearch/hermes-agent.git "$HOME/.hermes/hermes-agent"
cd "$HOME/.hermes/hermes-agent"
```

Create venv:

```bash
rm -rf venv
python -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
```

Re-export build env inside the venv shell:

```bash
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

Install Android psutil shim:

```bash
python scripts/install_psutil_android.py --pip "python -m pip"
```

Install the tested Termux profile:

```bash
python -m pip install -e '.[termux]' -c constraints-termux.txt 2>&1 | tee ~/hermes-termux-install.log
```

Install the web/dashboard extra required by the Android app WebSocket:

```bash
python -m pip install -e '.[web]' -c constraints-termux.txt 2>&1 | tee ~/hermes-web-install.log
```

Link the command:

```bash
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```

Verify:

```bash
which hermes
hermes --version
hermes doctor
```

Expected version output example:

```text
Hermes Agent v0.17.0
Python: 3.13.x
OpenAI SDK: 2.24.0
```

OpenAI SDK is a dependency name. It does **not** mean you must use OpenAI as your model provider.

---

## 5. Configure Xiaomi MiMo

Edit env:

```bash
nano "$HOME/.hermes/.env"
```

Normal MiMo API:

```env
XIAOMI_API_KEY=YOUR_MIMO_KEY
XIAOMI_BASE_URL=https://api.xiaomimimo.com/v1
```

MiMo Token Plan:

```env
XIAOMI_API_KEY=YOUR_TOKEN_PLAN_KEY
XIAOMI_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1
```

Configure Hermes:

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

If `mimo-v2.5-free` fails at runtime, switch to one of the known Xiaomi model names:

```bash
hermes config set model.default mimo-v2.5
# or
hermes config set model.default mimo-v2.5-pro
```

Check:

```bash
cat "$HOME/.hermes/config.yaml"
hermes doctor
```

You should see:

```text
✓ xiaomi (key configured)
```

---

## 6. Optional: configure Gemini

Add to `~/.hermes/.env`:

```env
GEMINI_API_KEY=YOUR_GEMINI_KEY
```

or:

```env
GOOGLE_API_KEY=YOUR_GEMINI_KEY
```

Switch to Gemini only when needed:

```bash
hermes config set model.provider gemini
hermes config set model.default gemini-2.5-flash
```

Switch back to MiMo:

```bash
hermes config set model.provider xiaomi
hermes config set model.default mimo-v2.5-free
```

---

## 7. Recommended setup wizard choices

If `hermes setup` asks:

### Setup type

Choose:

```text
Full setup
```

Do not choose Quick Setup unless you want Nous Portal OAuth.

### Terminal backend

Choose:

```text
Keep current (local)
```

or:

```text
Local
```

### Messaging platforms

Select none for now.

Messaging platforms are only needed if you want Telegram/Discord/WhatsApp/etc. bots.

### CLI tools

Recommended on Android:

Keep enabled:

```text
Web Search & Scraping
Terminal & Processes
File Operations
Code Execution
Text-to-Speech
Skills
Task Planning
Memory
Session Search
Clarifying Questions
Task Delegation
Cron Jobs
```

Disable for now:

```text
Browser Automation
Computer Use
Image Generation
Video Generation
X Search
Home Assistant
Spotify
Yuanbao
```

### Web search provider

Choose:

```text
DuckDuckGo (ddgs) — free, no key, search only
```

This still allows Hermes to search and collect web information. Browser automation is different; it controls a real browser and is not needed for normal web search.

---

## 8. Test Hermes in Termux

After provider setup:

```bash
hermes doctor --fix
hermes doctor
```

Test a model response:

```bash
hermes -q "سلام، فقط در یک جمله بگو با چه مدلی جواب می‌دهی."
```

If the CLI syntax changes, use:

```bash
hermes chat -q "سلام، تست اتصال مدل"
```

---

## 9. Start from the Android app

Stop any manually running dashboard first:

```bash
hermes dashboard --stop
```

Open Hermes2 app:

```text
Termux & Agent Connection → Start Agent Gateway
```

Why start from the app?

The app generates and injects its own `HERMES_DASHBOARD_SESSION_TOKEN`. If you manually start `hermes dashboard` with a different token, the app WebSocket authentication will fail.

After gateway starts, go to:

```text
Settings
```

Confirm:

```text
Provider: xiaomi
Model: mimo-v2.5-free / mimo-v2.5 / mimo-v2.5-pro
```

The Tools tab should list the capabilities currently exposed by Hermes.

---

## 10. Debugging

### Gateway logs

```bash
cat "$HOME/.hermes/logs/gateway_stdout.log"
```

Or from the app:

```text
Termux & Agent Connection → Fetch & View Logs
```

### pydantic-core / rustc crash

If you see:

```text
rustc panicked
linker-plugin-lto
```

Make sure these are exported before pip install:

```bash
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

### Cargo mirror 404

If you see:

```text
Updating `ustc` index
unexpected http status code: 404
```

Use clean Cargo home:

```bash
export CARGO_HOME="$HOME/.hermes/cargo"
mkdir -p "$CARGO_HOME"
export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
```

### `hermes` command missing

```bash
cd "$HOME/.hermes/hermes-agent"
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
hermes --version
```

---

## 11. What not to do

Do not paste API keys into GitHub issues, chats, README files, or commits.

Do not start dashboard manually for normal Android app use unless debugging.

Do not use `uv pip install` for this Termux path unless you know exactly why. The tested Android path is `python -m venv` + `python -m pip`.

Do not enable Browser Automation or Computer Use until the Android/Termux browser path is tested separately.
