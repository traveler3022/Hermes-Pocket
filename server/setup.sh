#!/usr/bin/env bash
# Hermes server setup, for the Hermes Android app.
#
#   curl -fsSL https://raw.githubusercontent.com/traveler3022/Hermes-android-termux-/main/server/setup.sh | sudo bash
#
# Run it on the server where Hermes Agent is installed. Running it again is safe. It:
#   1. installs Tailscale when it is missing and signs this server in (it prints a link),
#   2. gives the Hermes dashboard a username, a password and a signing secret, kept in
#      ~/.hermes-remote.env (mode 600) of the user Hermes belongs to,
#   3. runs the dashboard as the systemd service hermes-dashboard, on 127.0.0.1 only, so it
#      starts again after every reboot,
#   4. shares it inside your tailnet with `tailscale serve` (HTTPS). Never Funnel: nothing on
#      this server is opened to the internet.
#
# Options (when piping, put them after `bash -s --`):
#   --new-password   make a new password and secret; every signed-in device has to sign in again
#   --username NAME  login name for a new login (default: admin)
#   --user NAME      the Linux user Hermes is installed for (default: who ran sudo, else root)
#   --port N         dashboard port on 127.0.0.1 (default: 9119)
#
# Guide: server/README.md

hermes_setup() {
    set -euo pipefail
    # Nothing here reads the keyboard. Cutting stdin also stops any command from eating the rest
    # of this script when it arrives through a pipe (curl ... | sudo bash).
    exec </dev/null

    local service=hermes-dashboard
    local port=9119 login_name=admin username_given=0 new_password=0 run_user=""

    say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
    fail() { printf '\n\033[1;31mStopped:\033[0m %s\n' "$*" >&2; exit 1; }

    while [ $# -gt 0 ]; do
        case "$1" in
            --new-password) new_password=1 ;;
            --username) login_name="${2:-}"; username_given=1; shift ;;
            --user) run_user="${2:-}"; [ -n "$run_user" ] || fail "--user needs a name"; shift ;;
            --port) port="${2:-}"; shift ;;
            *) fail "Unknown option: $1 (see the top of this script)" ;;
        esac
        shift
    done
    case "$port" in '' | *[!0-9]*) fail "--port needs a number" ;; esac
    case "$login_name" in '' | *[!A-Za-z0-9._-]*) fail "--username can only have letters, digits, '.', '_' and '-'" ;; esac

    [ "$(id -u)" -eq 0 ] || fail "Run it as root or with sudo."
    [ -d /run/systemd/system ] || fail "This needs a Linux server that runs systemd."
    command -v curl >/dev/null || fail "curl is missing. Install it (for example: apt install curl), then run this again."

    # --- Which user Hermes belongs to, and its hermes command ---------------------------------
    home_of() { getent passwd "$1" | cut -d: -f6; }
    has_hermes() {
        local h
        h=$(home_of "$1" || true)
        [ -n "$h" ] && { [ -x "$h/.local/bin/hermes" ] || [ -d "$h/.hermes" ]; }
    }
    if [ -z "$run_user" ]; then
        run_user=root
        if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != root ] && has_hermes "$SUDO_USER"; then
            run_user=$SUDO_USER
        fi
    fi
    local run_home
    run_home=$(home_of "$run_user" || true)
    [ -n "$run_home" ] && [ -d "$run_home" ] || fail "There is no user named $run_user on this server."

    local hermes="" candidate
    for candidate in "$run_home/.local/bin/hermes" "$(command -v hermes || true)" /usr/local/bin/hermes; do
        if [ -n "$candidate" ] && [ -x "$candidate" ]; then
            hermes=$candidate
            break
        fi
    done
    [ -n "$hermes" ] || fail "Hermes Agent is not installed for $run_user. Install it first:
    curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
then run this again. If Hermes belongs to another user, add: --user NAME"

    # --- Tailscale --------------------------------------------------------------------------
    if ! command -v tailscale >/dev/null; then
        say "Installing Tailscale…"
        curl -fsSL https://tailscale.com/install.sh | sh
    fi
    systemctl enable --now tailscaled >/dev/null 2>&1 || true

    ts_json() { tailscale status --json --peers=false 2>/dev/null || true; }
    backend_state() { sed -n 's/^ *"BackendState": *"\([^"]*\)".*/\1/p' <<<"$(ts_json)"; }
    local state
    state=$(backend_state)
    if [ "$state" != Running ]; then
        say "Sign this server in to Tailscale: open the link below and log in with the account you will use in the app."
        tailscale up || fail "Tailscale did not come up. Run 'sudo tailscale up' yourself (with your usual flags), then run this again."
        for _ in $(seq 1 15); do
            state=$(backend_state)
            [ "$state" = Running ] && break
            sleep 1
        done
    fi
    [ "$state" = Running ] || fail "Tailscale is not connected (state: ${state:-unknown}). If your tailnet needs device approval, approve this server in the Tailscale admin console, then run this again."

    local name
    name=$(sed -n 's/^ *"DNSName": *"\([^"]*\)".*/\1/p' <<<"$(ts_json)")
    name=${name%%$'\n'*}
    name=${name%.}
    [ -n "$name" ] || fail "Tailscale gave this server no name. Turn on MagicDNS in the Tailscale admin console (DNS page), then run this again."
    local url="https://$name"

    # --- The Hermes login (kept when it already exists) -------------------------------------
    local env_file="$run_home/.hermes-remote.env" password="" secret=""
    if [ -f "$env_file" ] && [ "$new_password" = 0 ]; then
        local kept_name
        kept_name=$(sed -n 's/^HERMES_DASHBOARD_BASIC_AUTH_USERNAME=//p' "$env_file" | tail -n 1)
        password=$(sed -n 's/^HERMES_DASHBOARD_BASIC_AUTH_PASSWORD=//p' "$env_file" | tail -n 1)
        secret=$(sed -n 's/^HERMES_DASHBOARD_BASIC_AUTH_SECRET=//p' "$env_file" | tail -n 1)
        if [ "$username_given" = 0 ] && [ -n "$kept_name" ]; then
            login_name=$kept_name
        fi
    fi
    rand_hex() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }
    if [ -z "$password" ]; then
        local p
        p=$(rand_hex 10)
        password="${p:0:4}-${p:4:4}-${p:8:4}-${p:12:4}-${p:16:4}"
    fi
    [ -n "$secret" ] || secret=$(rand_hex 32)

    local tmp
    tmp=$(mktemp "$env_file.XXXXXX")
    {
        if [ -f "$env_file" ]; then
            grep -v -E '^HERMES_DASHBOARD_(BASIC_AUTH_USERNAME|BASIC_AUTH_PASSWORD|BASIC_AUTH_SECRET|PUBLIC_URL)=' "$env_file" || true
        fi
        printf '%s\n' \
            "HERMES_DASHBOARD_BASIC_AUTH_USERNAME=$login_name" \
            "HERMES_DASHBOARD_BASIC_AUTH_PASSWORD=$password" \
            "HERMES_DASHBOARD_BASIC_AUTH_SECRET=$secret" \
            "HERMES_DASHBOARD_PUBLIC_URL=$url"
    } >"$tmp"
    chmod 600 "$tmp"
    chown "$run_user" "$tmp"
    mv -f "$tmp" "$env_file"

    # --- The dashboard service, on 127.0.0.1 only --------------------------------------------
    listeners() { ss -ltn "sport = :$port" 2>/dev/null || true; }
    if command -v ss >/dev/null && ! systemctl is-active --quiet "$service" && grep -q '^LISTEN' <<<"$(listeners)"; then
        fail "Something else already uses port $port (a 'hermes dashboard' started by hand?). Stop it or pick another port with --port, then run this again."
    fi

    cat >"/etc/systemd/system/$service.service" <<EOF
[Unit]
Description=Hermes dashboard on 127.0.0.1, shared only through Tailscale Serve
After=network.target

[Service]
User=$run_user
Environment=HOME=$run_home
Environment=PATH=$run_home/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
EnvironmentFile=$env_file
ExecStart=$hermes dashboard --host 127.0.0.1 --port $port --no-open
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable "$service" >/dev/null 2>&1
    say "Starting the Hermes dashboard on 127.0.0.1:$port…"
    systemctl restart "$service"

    local status=""
    for _ in $(seq 1 60); do
        status=$(curl -fsS -m 3 "http://127.0.0.1:$port/api/status" 2>/dev/null) && break
        status=""
        sleep 1
    done
    if [ -z "$status" ]; then
        journalctl -u "$service" -n 30 --no-pager >&2 || true
        fail "The Hermes dashboard did not start (its log is above)."
    fi
    if command -v ss >/dev/null; then
        local line
        while IFS= read -r line; do
            case "$line" in
                LISTEN*" 127.0.0.1:$port "* | LISTEN*" [::1]:$port "*) ;;
                LISTEN*)
                    systemctl stop "$service"
                    fail "The dashboard listened on more than 127.0.0.1, so it was stopped: $line"
                    ;;
            esac
        done <<<"$(listeners)"
    fi
    if ! grep -q '"auth_required": *true' <<<"$status"; then
        systemctl stop "$service"
        fail "The dashboard came up without a login, so it was stopped. Check $env_file."
    fi
    grep -q 'native_pkce' <<<"$status" ||
        printf '\nWarning: this Hermes is too old for signing in from the app. Run: hermes update\n' >&2

    # --- Share it inside the tailnet only ----------------------------------------------------
    say "Sharing Hermes inside your tailnet only (tailscale serve)…"
    printf 'If Tailscale prints a link to turn on HTTPS, open it and turn HTTPS on. This waits for it.\n'
    tailscale serve --bg "http://127.0.0.1:$port"
    if grep -qF "\"$name:443\": true" <<<"$(tailscale serve status --json 2>/dev/null || true)"; then
        systemctl stop "$service"
        fail "Tailscale Funnel is on for $name, which puts it on the internet, so the dashboard was stopped. Turn Funnel off (sudo tailscale funnel --https=443 off), then run this again."
    fi

    say "Done. Hermes listens only on 127.0.0.1; only your own Tailscale devices can reach it."
    cat <<EOF

In the Hermes app: Settings → Server
  1. Sign in to Tailscale with the same account as this server, then tap ${name%%.*} in the list.
  2. Enter the Hermes login:
       Username: $login_name
       Password: $password

Server address: $url
Hermes: $hermes (user $run_user)
The login is kept in $env_file. For a new password, run this again with --new-password.
The first connection can take up to a minute while Tailscale gets the HTTPS certificate.
EOF
}

hermes_setup "$@"
