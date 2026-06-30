<div dir="rtl">

# نصب Hermes Agent در Termux

</div>

## Prerequisites

- Android phone with Termux from F-Droid
- ~500 MB free storage (Python/Rust wheels are heavy)
- Network access

Install **Termux** from F-Droid → **[f-droid.org/packages/com.termux](https://f-droid.org/en/packages/com.termux/)**

> [!WARNING]
> Do **not** install from Play Store — that version is abandoned.

## Enable external apps

This lets Hermes2 start commands in Termux:

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

Then **force-stop and reopen Termux** to apply.

## Install system packages

```bash
pkg update -y
pkg upgrade -y
pkg install -y git python clang rust make pkg-config libffi openssl ca-certificates curl llvm lld nodejs ripgrep ffmpeg
```

## Set Rust/Android build environment

<div dir="rtl">

این متغیرها برای کامپایل Rust روی اندروید ضروری هستند:

</div>

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

| Variable | Why |
|---|---|
| `ANDROID_API_LEVEL` | Needed by maturin/jiter/pydantic-core builds on Android |
| `CARGO_BUILD_TARGET` | Forces Cargo to build for the native Android target |
| `CARGO_HOME=$HOME/.hermes/cargo` | Avoids broken user Cargo mirror configs (e.g. USTC 404) |
| `CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse` | Uses crates.io sparse index |
| `CARGO_PROFILE_RELEASE_LTO=false` | Avoids Termux rustc ICE during pydantic-core builds |
| `CARGO_BUILD_JOBS=1` | Reduces phone memory/CPU pressure |

## Clean old partial installs (if any)

```bash
if [ -e "$HOME/.hermes/hermes-agent" ] && [ ! -d "$HOME/.hermes/hermes-agent/.git" ]; then
  mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.broken-$(date +%Y%m%d-%H%M%S)"
fi
```

## Clone and install Hermes Agent

```bash
mkdir -p "$HOME/.hermes"
git clone https://github.com/NousResearch/hermes-agent.git "$HOME/.hermes/hermes-agent"
cd "$HOME/.hermes/hermes-agent"
```

Create venv and install:

```bash
rm -rf venv
python -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
```

Re-export build env inside the venv shell (same exports as above):

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

Install the Termux profile:

```bash
python -m pip install -e '.[termux]' -c constraints-termux.txt 2>&1 | tee ~/hermes-termux-install.log
```

Install the web/dashboard extra (required by the Android app WebSocket):

```bash
python -m pip install -e '.[web]' -c constraints-termux.txt 2>&1 | tee ~/hermes-web-install.log
```

Link the command:

```bash
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
```

## Verify

```bash
which hermes
hermes --version
hermes doctor
```

Expected output:

```text
Hermes Agent v0.17.0
Python: 3.13.x
OpenAI SDK: 2.24.0
```

> [!NOTE]
> "OpenAI SDK" is a dependency name. It does **not** mean you must use OpenAI as your provider.

<div dir="rtl">

## عیبیابی

### `hermes: command not found`

</div>

```bash
cd "$HOME/.hermes/hermes-agent"
ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
which hermes
```

### pydantic-core / rustc crash

Make sure these are exported before pip install:

```bash
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_PROFILE_RELEASE_STRIP=none
export CARGO_BUILD_JOBS=1
```

### Cargo mirror 404

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

<div dir="rtl">

> [!NOTE]
> **اندروید پلتفرم «Tier 2»** است. Browser Automation و Computer Use روی اندروید تست نشدهاند.

---

**→ بعد: [تنظیم اولیه هرمس](SETUP_HERMES_TERMUX.md)** · **[راهنمای فنی کامل](RUNNING_ON_ANDROID_TERMUX.md)**
</div>
