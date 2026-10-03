# Use Hermes on your own server

[فارسی](README.fa.md)

The Hermes app can connect to Hermes Agent running on your own Linux server. The connection goes only through your private Tailscale network:

- Hermes stays on `127.0.0.1` on the server. No port is opened to the internet.
- Only devices signed in to your Tailscale account can reach it, over an encrypted connection.
- Tailscale is built into the app, so you don't need the Tailscale app on your phone.

## You need

- A Linux server with systemd (Ubuntu, Debian, …) where [Hermes Agent](https://github.com/NousResearch/hermes-agent) is installed.
- A free [Tailscale](https://tailscale.com) account.

## 1. Set up the server (one command)

On the server, as the user Hermes is installed for:

```sh
curl -fsSL https://raw.githubusercontent.com/traveler3022/Hermes-Pocket/main/server/setup.sh | sudo bash
```

If you are logged in as root, leave out `sudo`. The script ([setup.sh](setup.sh)):

1. installs Tailscale if it is missing and prints a sign-in link. Open it and log in with the account you will use in the app.
2. If Tailscale prints a link to turn on HTTPS, open it and turn HTTPS on. The script waits.
3. gives the Hermes dashboard a username, a password and a signing secret, saved in `~/.hermes-remote.env` (only that user can read it),
4. runs the dashboard as the `hermes-dashboard` service on `127.0.0.1`, so it starts again after a reboot,
5. shares it inside your tailnet with `tailscale serve`.

At the end it prints the username and password. Running it again is safe: the login is kept.

## 2. Connect the app

In the app, open **Settings → Server**:

1. Tap **Sign in to Tailscale** and log in with the same account as the server.
2. Tap your server in the device list.
3. Enter the username and password from step 1, then tap **Sign in**.

The app keeps the login encrypted on the phone and signs in again by itself, so you type it once.

## Change the password

```sh
curl -fsSL https://raw.githubusercontent.com/traveler3022/Hermes-Pocket/main/server/setup.sh | sudo bash -s -- --new-password
```

Every device is signed out and needs the new password once.

Other options: `--username NAME` (name for a new login, default `admin`), `--user NAME` (the Linux user Hermes belongs to), `--port N` (default `9119`).

## Check, stop, remove

```sh
systemctl status hermes-dashboard          # is it running?
sudo tailscale serve status                # must say "tailnet only"
sudo systemctl disable --now hermes-dashboard
sudo tailscale serve --https=443 off
```

## Safety rules

- Never run `tailscale funnel` for Hermes: Funnel puts it on the internet. The script stops the dashboard when it finds Funnel on.
- Never start the dashboard with `--host 0.0.0.0`, and never put it behind nginx or another public proxy. The dashboard can run any command on the server.
- Tip: turn on **Device approval** in the Tailscale admin console, so every new device needs your OK.

## Troubleshooting

- **"Hostname … not verified"** with some other certificate: Tailscale Serve is off, so the connection reached another web server on the machine. Run the setup command again.
- **"Can't find ….ts.net"**: the app is signed in to a different Tailscale account than the server.
- The first connection can take up to a minute while Tailscale gets the HTTPS certificate.
- A test with curl **from the server itself** never goes through Tailscale Serve, so it can show the wrong certificate even when everything works. Test from another device in your tailnet.
