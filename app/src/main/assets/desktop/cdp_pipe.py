#!/usr/bin/env python3
# Written by the Hermes Android app on every desktop start; edits are overwritten.
"""Chromium's DevTools for Hermes only.

Every app on an Android phone shares 127.0.0.1, so Chromium's own --remote-debugging-port
would hand the browser, logins and cookies included, to any installed app. Here Chromium
speaks CDP only over --remote-debugging-pipe, to this process, which serves it on
127.0.0.1:PORT under a secret path (BROWSER_CDP_URL=http://127.0.0.1:PORT/SECRET). Each
client gets a browser session of its own (Target.attachToBrowserTarget), so clients stay
as separate as on Chromium's own port. Chromium is restarted when it exits.

  cdp_pipe.py serve --port PORT -- chromium [args...]   secret in $CDP_SECRET
  cdp_pipe.py probe --port PORT                          exit 0 if this proxy answers

The probe proves the listener knows the secret without sending it: another app holding
the port learns nothing from it.
"""
import asyncio
import base64
import fcntl
import hashlib
import hmac
import json
import os
import secrets
import signal
import struct
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
MAX_FAST_FAILS = 5
FAST_FAIL_SECONDS = 15
MAX_CLIENT_MESSAGE = 64 * 1024 * 1024
MAX_CLIENT_BACKLOG = 256 * 1024 * 1024
READY_TIMEOUT = 90
ACTIVITY_FILE = os.environ.get("HERMES_DESKTOP_ACTIVITY", "")


def log(message):
    print(f"[cdp-pipe] {message}", file=sys.stderr, flush=True)


def probe_answer(secret, challenge):
    return hmac.new(secret.encode(), challenge.encode(), hashlib.sha256).hexdigest()


class Client:
    """One WebSocket client: a browser-level connection, or a page one (/devtools/page/ID)."""

    def __init__(self, writer, page_target=None):
        self.writer = writer
        self.page_target = page_target
        self.browser_session = None
        self.page_session = None
        self.closed = False

    @property
    def main_session(self):
        return self.page_session if self.page_target else self.browser_session

    def send_text(self, text):
        if self.closed:
            return
        data = text.encode()
        size = len(data)
        if size < 126:
            header = struct.pack(">BB", 0x81, size)
        elif size < 65536:
            header = struct.pack(">BBH", 0x81, 126, size)
        else:
            header = struct.pack(">BBQ", 0x81, 127, size)
        self.send_raw(header + data)

    def send_raw(self, data):
        if self.closed:
            return
        transport = self.writer.transport
        if transport.is_closing() or transport.get_write_buffer_size() > MAX_CLIENT_BACKLOG:
            self.close()
            return
        self.writer.write(data)

    def close(self):
        if self.closed:
            return
        self.closed = True
        try:
            self.writer.write(struct.pack(">BB", 0x88, 0))
            self.writer.close()
        except Exception:
            pass


class Proxy:
    def __init__(self, port, secret, chrome_args):
        self.port = port
        self.secret = secret
        self.chrome_args = chrome_args
        self.browser_id = str(uuid.uuid4())
        self.next_id = 1
        self.root_pending = {}
        self.pending = {}
        self.sessions = {}
        self.clients = set()
        self.pipe_out = None
        self.reader_task = None
        self.ready = asyncio.Event()
        self.process = None
        self.last_active = 0.0

    # ── Chromium over the pipe ──────────────────────────────────────────────────────────

    async def run_chromium(self):
        loop = asyncio.get_running_loop()
        fails = 0
        while fails < MAX_FAST_FAILS:
            began = time.monotonic()
            try:
                await self.start_chromium()
                await loop.run_in_executor(None, self.process.wait)
            except Exception as error:
                log(f"Chromium failed: {error}")
                if self.process is not None:
                    await loop.run_in_executor(None, self.stop)
            self.ready.clear()
            self.drop_everything()
            fails = fails + 1 if time.monotonic() - began < FAST_FAIL_SECONDS else 0
            await asyncio.sleep(1)
        log("Chromium keeps exiting; giving up")

    async def start_chromium(self):
        loop = asyncio.get_running_loop()
        self.process = None
        to_chrome_r, to_chrome_w = os.pipe()
        from_chrome_r, from_chrome_w = os.pipe()
        # Chromium reads commands on fd 3 and writes replies on fd 4. Park the ends well above
        # 4 first, so no dup2 below overwrites the other one.
        child_in = fcntl.fcntl(to_chrome_r, fcntl.F_DUPFD, 100)
        child_out = fcntl.fcntl(from_chrome_w, fcntl.F_DUPFD, 100)
        os.close(to_chrome_r)
        os.close(from_chrome_w)

        def wire():
            os.dup2(child_in, 3)
            os.dup2(child_out, 4)

        env = {k: v for k, v in os.environ.items() if k != "CDP_SECRET"}
        args = [a for a in self.chrome_args if not a.startswith("--remote-debugging-")]
        self.process = subprocess.Popen(
            args + ["--remote-debugging-pipe"],
            pass_fds=(3, 4),
            preexec_fn=wire,
            start_new_session=True,
            env=env,
        )
        os.close(child_in)
        os.close(child_out)

        reader = asyncio.StreamReader(limit=2 ** 31)
        await loop.connect_read_pipe(lambda: asyncio.StreamReaderProtocol(reader), os.fdopen(from_chrome_r, "rb", 0))
        self.pipe_out, _ = await loop.connect_write_pipe(asyncio.Protocol, os.fdopen(to_chrome_w, "wb", 0))
        # Held here: asyncio keeps only weak references to tasks, and a collected reader
        # left every reply from Chromium unread.
        self.reader_task = loop.create_task(self.read_chromium(reader))
        await asyncio.wait_for(self.root_call("Browser.getVersion"), READY_TIMEOUT)
        self.ready.set()
        log(f"Chromium {self.process.pid} ready")

    async def read_chromium(self, reader):
        while True:
            try:
                raw = await reader.readuntil(b"\0")
            except (asyncio.IncompleteReadError, ConnectionError):
                return
            try:
                self.from_chromium(json.loads(raw[:-1]))
            except Exception as error:
                log(f"bad message from Chromium: {error}")

    def write_chromium(self, message):
        if self.pipe_out is None or self.pipe_out.is_closing():
            raise ConnectionError("Chromium is not running")
        self.pipe_out.write(json.dumps(message).encode() + b"\0")

    def call(self, method, params=None, session=None):
        future = asyncio.get_running_loop().create_future()
        message_id = self.take_id()
        self.root_pending[message_id] = future
        message = {"id": message_id, "method": method, "params": params or {}}
        if session:
            message["sessionId"] = session
        try:
            self.write_chromium(message)
        except ConnectionError as error:
            self.root_pending.pop(message_id, None)
            future.set_exception(error)
        return future

    async def root_call(self, method, params=None, session=None):
        reply = await self.call(method, params, session)
        if "error" in reply:
            raise RuntimeError(reply["error"].get("message", "CDP error"))
        return reply.get("result", {})

    def take_id(self):
        self.next_id += 1
        return self.next_id

    def from_chromium(self, message):
        message_id = message.get("id")
        if message_id is not None:
            future = self.root_pending.pop(message_id, None)
            if future is not None:
                if not future.done():
                    future.set_result(message)
                return
            entry = self.pending.pop(message_id, None)
            if entry is None:
                return
            client, original_id = entry
            message["id"] = original_id
            created = (message.get("result") or {}).get("sessionId")
            if created:
                self.sessions[created] = client
            self.deliver(client, message)
            return
        session = message.get("sessionId")
        client = self.sessions.get(session) if session else None
        if client is None:
            return
        params = message.get("params") or {}
        if message.get("method") == "Target.attachedToTarget" and params.get("sessionId"):
            self.sessions[params["sessionId"]] = client
        elif message.get("method") == "Target.detachedFromTarget" and params.get("sessionId"):
            self.sessions.pop(params["sessionId"], None)
        if client.page_target and session == client.browser_session:
            return  # a page client asked nothing of the browser session it rides on
        self.deliver(client, message)

    def deliver(self, client, message):
        if message.get("sessionId") == client.main_session:
            message.pop("sessionId", None)
        client.send_text(json.dumps(message))

    def from_client(self, client, text):
        message = json.loads(text)
        if not isinstance(message, dict) or "method" not in message:
            return
        self.mark_active()
        proxy_id = self.take_id()
        self.pending[proxy_id] = (client, message.get("id"))
        message["id"] = proxy_id
        if not message.get("sessionId"):
            message["sessionId"] = client.main_session
        self.write_chromium(message)

    def mark_active(self):
        """Tells hermes-desktop the browser is in use (it stops the desktop when idle)."""
        now = time.monotonic()
        if ACTIVITY_FILE and now - self.last_active > 30:
            self.last_active = now
            try:
                os.utime(ACTIVITY_FILE)
            except OSError:
                pass

    def forget(self, client):
        self.clients.discard(client)
        for session, owner in list(self.sessions.items()):
            if owner is client:
                del self.sessions[session]
        for message_id, (owner, _) in list(self.pending.items()):
            if owner is client:
                del self.pending[message_id]
        if client.browser_session and self.ready.is_set():
            # Ends the client's child sessions with it; nobody waits for the reply.
            detach = self.call("Target.detachFromTarget", {"sessionId": client.browser_session})
            detach.add_done_callback(lambda f: f.cancelled() or f.exception())

    def drop_everything(self):
        for future in self.root_pending.values():
            if not future.done():
                future.set_exception(ConnectionError("Chromium exited"))
        self.root_pending.clear()
        self.pending.clear()
        self.sessions.clear()
        for client in list(self.clients):
            client.close()
        self.clients.clear()
        if self.pipe_out is not None:
            self.pipe_out.close()
            self.pipe_out = None

    def stop(self):
        """Ends Chromium and its helpers (their own process group); blocks up to a few seconds."""
        process = self.process
        if process is None or process.poll() is not None:
            return
        for signum, grace in ((signal.SIGTERM, 5), (signal.SIGKILL, 5)):
            try:
                os.killpg(process.pid, signum)
                process.wait(grace)
                return
            except ProcessLookupError:
                return
            except subprocess.TimeoutExpired:
                continue

    # ── HTTP and WebSocket ──────────────────────────────────────────────────────────────

    async def serve_connection(self, reader, writer):
        try:
            head = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 10)
            lines = head.decode("latin-1").split("\r\n")
            method, target, _ = (lines[0].split(" ", 2) + ["", ""])[:3]
            headers = {}
            for line in lines[1:]:
                if ":" in line:
                    name, value = line.split(":", 1)
                    headers[name.strip().lower()] = value.strip()
            url = urllib.parse.urlsplit(target)
            await self.route(method, url, headers, reader, writer)
        except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError, ConnectionError):
            pass
        except Exception as error:
            log(f"request failed: {error}")
        finally:
            try:
                writer.close()
            except Exception:
                pass

    async def route(self, method, url, headers, reader, writer):
        parts = [p for p in url.path.split("/") if p]
        if parts == ["hermes-probe"]:
            challenge = urllib.parse.parse_qs(url.query).get("c", [""])[0][:128]
            if not challenge or not self.ready.is_set():
                return self.respond(writer, 503, "text/plain", b"not ready")
            return self.respond(writer, 200, "text/plain", probe_answer(self.secret, challenge).encode())
        if not parts or not hmac.compare_digest(parts[0].encode(), self.secret.encode()):
            return self.respond(writer, 404, "text/plain", b"not found")
        rest = parts[1:]
        if not await self.wait_ready(READY_TIMEOUT):
            return self.respond(writer, 503, "text/plain", b"browser not ready")
        base = f"127.0.0.1:{self.port}/{self.secret}"
        if rest in (["json", "version"], ["json", "version", ""]):
            version = await self.root_call("Browser.getVersion")
            body = {
                "Browser": version.get("product", ""),
                "Protocol-Version": version.get("protocolVersion", "1.3"),
                "User-Agent": version.get("userAgent", ""),
                "V8-Version": version.get("jsVersion", ""),
                "WebKit-Version": version.get("revision", ""),
                "webSocketDebuggerUrl": f"ws://{base}/devtools/browser/{self.browser_id}",
            }
            return self.respond(writer, 200, "application/json", json.dumps(body).encode())
        if rest in (["json"], ["json", "list"]):
            targets = (await self.root_call("Target.getTargets")).get("targetInfos", [])
            body = [
                {
                    "description": "",
                    "id": t["targetId"],
                    "title": t.get("title", ""),
                    "type": t.get("type", ""),
                    "url": t.get("url", ""),
                    "webSocketDebuggerUrl": f"ws://{base}/devtools/page/{t['targetId']}",
                }
                for t in targets
                if t.get("type") in ("page", "iframe", "worker", "service_worker", "other")
            ]
            return self.respond(writer, 200, "application/json", json.dumps(body).encode())
        if len(rest) >= 2 and rest[0] == "devtools" and rest[1] in ("browser", "page"):
            if headers.get("upgrade", "").lower() != "websocket" or "sec-websocket-key" not in headers:
                return self.respond(writer, 400, "text/plain", b"websocket expected")
            page = rest[2] if rest[1] == "page" and len(rest) > 2 else None
            if rest[1] == "page" and not page:
                return self.respond(writer, 404, "text/plain", b"no such target")
            return await self.websocket(headers, reader, writer, page)
        return self.respond(writer, 404, "text/plain", b"not found")

    async def wait_ready(self, seconds):
        try:
            await asyncio.wait_for(self.ready.wait(), seconds)
            return True
        except asyncio.TimeoutError:
            return False

    def respond(self, writer, status, content_type, body):
        reason = {200: "OK", 400: "Bad Request", 404: "Not Found", 503: "Service Unavailable"}.get(status, "")
        writer.write(
            f"HTTP/1.1 {status} {reason}\r\nContent-Type: {content_type}\r\n"
            f"Content-Length: {len(body)}\r\nConnection: close\r\n\r\n".encode() + body
        )

    async def websocket(self, headers, reader, writer, page_target):
        client = Client(writer, page_target)
        try:
            client.browser_session = (await self.root_call("Target.attachToBrowserTarget"))["sessionId"]
            self.sessions[client.browser_session] = client
            if page_target:
                attached = await self.root_call(
                    "Target.attachToTarget", {"targetId": page_target, "flatten": True}, client.browser_session
                )
                client.page_session = attached["sessionId"]
                self.sessions[client.page_session] = client
        except Exception as error:
            self.forget(client)
            return self.respond(writer, 404, "text/plain", str(error).encode())
        accept = base64.b64encode(hashlib.sha1((headers["sec-websocket-key"] + WS_GUID).encode()).digest()).decode()
        writer.write(
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Accept: {accept}\r\n\r\n".encode()
        )
        self.clients.add(client)
        try:
            await self.read_frames(client, reader)
        finally:
            self.forget(client)
            client.close()

    async def read_frames(self, client, reader):
        pieces = []
        while not client.closed:
            first, second = await reader.readexactly(2)
            opcode = first & 0x0F
            size = second & 0x7F
            if size == 126:
                size = struct.unpack(">H", await reader.readexactly(2))[0]
            elif size == 127:
                size = struct.unpack(">Q", await reader.readexactly(8))[0]
            if size > MAX_CLIENT_MESSAGE:
                return
            mask = await reader.readexactly(4) if second & 0x80 else None
            payload = unmask(await reader.readexactly(size), mask) if size else b""
            if opcode == 0x8:
                return
            if opcode == 0x9:
                client.send_raw(struct.pack(">BB", 0x8A, len(payload)) + payload[:125])
                continue
            if opcode == 0xA:
                continue
            pieces.append(payload)
            if sum(len(p) for p in pieces) > MAX_CLIENT_MESSAGE:
                return
            if first & 0x80:
                text = b"".join(pieces).decode("utf-8", "replace")
                pieces = []
                try:
                    self.from_client(client, text)
                except (ValueError, ConnectionError) as error:
                    log(f"dropped a client message: {error}")


def unmask(data, mask):
    if not mask:
        return data
    repeated = (mask * (len(data) // 4 + 1))[: len(data)]
    return (int.from_bytes(data, "big") ^ int.from_bytes(repeated, "big")).to_bytes(len(data), "big")


async def serve(port, secret, chrome_args):
    proxy = Proxy(port, secret, chrome_args)
    # Hold the port before Chromium exists: another app must not be able to take it between
    # a Chromium restart and our next bind.
    server = await asyncio.start_server(proxy.serve_connection, "127.0.0.1", port, reuse_address=True)
    loop = asyncio.get_running_loop()
    done = loop.create_future()
    for signum in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        loop.add_signal_handler(signum, lambda: done.done() or done.set_result(None))
    chromium = loop.create_task(proxy.run_chromium())
    await asyncio.wait([chromium, done], return_when=asyncio.FIRST_COMPLETED)
    server.close()
    await loop.run_in_executor(None, proxy.stop)


def probe(port, secret):
    challenge = secrets.token_hex(16)
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/hermes-probe?c={challenge}", timeout=3) as reply:
            answer = reply.read().decode().strip()
    except Exception:
        return 1
    return 0 if hmac.compare_digest(answer.encode(), probe_answer(secret, challenge).encode()) else 1


def main(argv):
    secret = os.environ.get("CDP_SECRET", "")
    if len(argv) < 3 or argv[1] != "--port" or not secret:
        print(__doc__, file=sys.stderr)
        return 2
    port = int(argv[2])
    if argv[0] == "probe":
        return probe(port, secret)
    if argv[0] == "serve" and len(argv) > 4 and argv[3] == "--":
        try:
            asyncio.run(serve(port, secret, argv[4:]))
        except OSError as error:
            log(f"cannot listen on 127.0.0.1:{port}: {error}")
            return 1
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
