"""Hermes Pocket pairing door: gives the Hermes login to the server owner's phone, once.

setup.sh downloads this next to its socket and runs it as the systemd service
hermes-pocket-pair, behind `tailscale serve --set-path /hermes-pocket-pair unix:<socket>`.

    python3 -I pair.py SOCKET ENV_FILE OWNER_FILE SECONDS

Tailscale Serve sets Tailscale-User-Login on every request (and drops any copy the caller
sent); Funnel requests and tagged devices get none. Only the account in OWNER_FILE (the one
this server is signed in to Tailscale with) gets the username and password from ENV_FILE.
After one hand-over, or SECONDS, it exits; the service then removes its Serve path and files.
Standard library only, so any python3 runs it.
"""
import json
import os
import socketserver
import sys
import time
from email.header import decode_header, make_header
from http.server import BaseHTTPRequestHandler

sock_path, env_path, owner_path, seconds = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])


def read_env(path):
    values = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            key, sep, value = line.rstrip("\n").partition("=")
            if sep:
                values[key] = value
    return values


def login_of(value):
    # Serve sends ASCII as is and anything else RFC 2047 encoded.
    try:
        return str(make_header(decode_header(value))).strip().lower()
    except Exception:
        return ""


with open(owner_path, encoding="utf-8") as f:
    OWNER = f.read().strip().lower()
ENV = read_env(env_path)
LOGIN = {
    "username": ENV.get("HERMES_DASHBOARD_BASIC_AUTH_USERNAME", ""),
    "password": ENV.get("HERMES_DASHBOARD_BASIC_AUTH_PASSWORD", ""),
}
paired = False


class Door(BaseHTTPRequestHandler):
    timeout = 5  # a caller that never finishes its request can't hold the door

    def do_POST(self):
        global paired
        funnel = self.headers.get("Tailscale-Funnel-Request")
        if funnel or not OWNER or login_of(self.headers.get("Tailscale-User-Login", "")) != OWNER:
            self.log_message("refused a device that is not signed in as this server's account")
            self.answer(403, {"error": "not_owner"})
            return
        if not LOGIN["username"] or not LOGIN["password"]:
            self.answer(503, {"error": "no_login"})
            return
        self.answer(200, LOGIN)
        paired = True
        self.log_message("gave the login to this server's account; closing")

    def do_GET(self):
        self.answer(405, {"error": "use_post"})

    def answer(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def address_string(self):
        return "tailscale-serve"

    def log_message(self, fmt, *args):
        sys.stderr.write("hermes-pocket-pair: " + (fmt % args) + "\n")


os.umask(0o077)
if os.path.exists(sock_path):
    os.unlink(sock_path)
deadline = time.monotonic() + seconds
with socketserver.UnixStreamServer(sock_path, Door) as server:
    while not paired:
        left = deadline - time.monotonic()
        if left <= 0:
            break
        server.timeout = left
        server.handle_request()
