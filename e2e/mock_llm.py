#!/usr/bin/env python3
"""OpenAI-compatible mock model for the Hermes Pocket end-to-end tests.

Hermes calls it as a "Custom (OpenAI-compatible)" provider. Every answer is scripted, so a test
gets the same stream every time and costs nothing. What to answer comes from the last user
message (or, after tools ran, from the conversation so far):

  (anything else)      a short streamed echo: "MOCK-REPLY: <the message>"
  #long                long markdown: headings, lists, a table, code, math, links, Persian text
  #slow                one word every 0.3 s for about a minute (Stop / interrupt tests)
  #reason              reasoning first, then the answer
  #error500 / #error429 / #badstream   the provider failing in different ways
  #terminal <command>  a terminal tool call, then a final answer quoting its output
  #tool <name> <json>  any tool call with the given arguments
  #clarify             the clarify tool (a question with choices for the user)
  #danger              a terminal command Hermes asks approval for
  #todo                the todo tool
  #alltools            every tool the request offers, one per turn, with arguments made from
                       its JSON schema; then a summary of which ones answered
  #parallel            three terminal calls in one response

Every request is appended to --log as one JSON line, so a test can check what Hermes sent.
"""
import argparse
import json
import re
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL = "mock-model"
LOCK = threading.Lock()
LOG_PATH = None

# Tools #alltools leaves out: clarify waits for the user (it has its own trigger), and these
# reach outside the test or wait on a person.
SKIP_IN_ALLTOOLS = {"clarify", "send_message", "ha_call_service"}

LONG_MARKDOWN = """# Mock report

Intro paragraph with **bold**, *italic*, `inline code`, a [link](https://example.com) and an emoji 🚀.

## A list

1. First item
2. Second item with a nested list:
   - nested a
   - nested b
3. Third item

> A quote that goes on long enough to wrap onto a second line on a phone screen, to check that wrapping inside quotes works.

## A table

| Name | Size | Note |
|------|-----:|------|
| alpha | 1 | first |
| beta | 22 | second, a longer cell |
| gamma | 333 | ✓ |

## Code

```python
def fib(n: int) -> int:
    a, b = 0, 1
    for _ in range(n):
        a, b = b, a + b
    return a

print([fib(i) for i in range(10)])
```

```bash
for f in *.txt; do echo "$f"; done | sort | uniq -c
```

## Math

Inline $E = mc^2$ and a block:

$$\\int_0^1 x^2\\,dx = \\frac{1}{3}$$

## فارسی

این یک پاراگراف فارسی است که باید راست‌به‌چپ نمایش داده شود، همراه با کلمهٔ English وسط متن و عدد ۱۲۳.

- مورد اول
- مورد دوم

---

The end. """ + "Filler sentence to make the answer long. " * 40


def log(entry):
    if not LOG_PATH:
        return
    with LOCK, open(LOG_PATH, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def text_of(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(part.get("text", "") for part in content if isinstance(part, dict))
    return ""


def example_value(name, schema, depth=0):
    """A small valid value for a JSON schema property."""
    if not isinstance(schema, dict) or depth > 3:
        return "test"
    if "enum" in schema and schema["enum"]:
        return schema["enum"][0]
    kind = schema.get("type")
    if isinstance(kind, list):
        kind = next((k for k in kind if k != "null"), "string")
    lowered = name.lower()
    if kind == "integer":
        return max(1, int(schema.get("minimum", 1)))
    if kind == "number":
        return 1
    if kind == "boolean":
        return False
    if kind == "array":
        item = example_value(name, schema.get("items", {"type": "string"}), depth + 1)
        return [item] if schema.get("minItems", 0) > 0 else []
    if kind == "object":
        return args_for(schema, depth + 1)
    known = {
        "command": "echo mock-terminal-ok",
        "path": "/tmp/mock_e2e.txt",
        "file_path": "/tmp/mock_e2e.txt",
        "content": "written by the mock model\n",
        "url": "https://example.com",
        "query": "hermes",
        "pattern": "mock",
        "code": "print('mock-code-ok')",
        "goal": "Say hello and stop.",
        "prompt": "a small red square",
        "text": "Mock text",
        "name": "mock-e2e",
        "schedule": "every 1h",
        "title": "Mock title",
    }
    for key, value in known.items():
        if key in lowered:
            return value
    return "test"


def args_for(schema, depth=0):
    props = (schema or {}).get("properties", {}) or {}
    required = (schema or {}).get("required", []) or []
    return {name: example_value(name, props.get(name, {}), depth) for name in required}


class Script:
    """What one response should be: text, reasoning, tool calls, or an error."""

    def __init__(self, text="", reasoning="", tool_calls=None, error=None, delay=0.0, chunk_words=3):
        self.text = text
        self.reasoning = reasoning
        self.tool_calls = tool_calls or []
        self.error = error
        self.delay = delay
        self.chunk_words = chunk_words


def call(name, arguments):
    return {"id": "call_" + uuid.uuid4().hex[:12], "type": "function",
            "function": {"name": name, "arguments": json.dumps(arguments)}}


def decide(body):
    messages = body.get("messages", [])
    tools = {t.get("function", {}).get("name"): t.get("function", {}) for t in body.get("tools", []) or []}
    # By position, not by value: a repeated message (#danger twice) must not match the first one.
    user_at = max((i for i, m in enumerate(messages) if m.get("role") == "user"), default=None)
    last_user = messages[user_at] if user_at is not None else None
    user_text = text_of(last_user.get("content")) if last_user else ""
    since_user = messages[user_at + 1:] if user_at is not None else []
    tool_results = [m for m in since_user if m.get("role") == "tool"]
    system_text = " ".join(text_of(m.get("content")) for m in messages if m.get("role") == "system")

    # What Hermes wraps around the user's words: a steer note, or the context of an interrupted answer.
    if user_text.startswith("[OUT-OF-BAND"):
        return Script(text="STEER-RECEIVED: " + user_text.strip().split("\n")[-1][:120])
    if user_text.startswith("[Context from the interrupted") or user_text.startswith("[System:"):
        user_text = [l for l in user_text.strip().split("\n") if l.strip()][-1]

    # Side requests Hermes makes on its own (titles): a short system prompt that asks for a title.
    if not tools and re.search(r"\btitle\b", system_text, re.I) and len(system_text) < 2000 and len(messages) <= 3:
        first = user_text.strip().split("\n")[0][:40]
        return Script(text=first or "Mock title")

    trigger = user_text.strip()
    if trigger.startswith("#alltools"):
        names = sorted(n for n in tools if n and n not in SKIP_IN_ALLTOOLS)
        done = len(tool_results)
        if done < len(names):
            name = names[done]
            return Script(text="", tool_calls=[call(name, args_for(tools[name].get("parameters", {})))])
        lines = []
        for m in tool_results:
            body_text = text_of(m.get("content"))[:80].replace("\n", " ")
            lines.append(f"- {m.get('name') or m.get('tool_call_id')}: {body_text}")
        return Script(text=f"ALLTOOLS-DONE {done} tools\n" + "\n".join(lines))

    if tool_results:
        quoted = " | ".join(text_of(m.get("content"))[:200].replace("\n", " ") for m in tool_results)
        return Script(text=f"TOOL-DONE: {quoted}")

    if trigger.startswith("#long"):
        return Script(text=LONG_MARKDOWN, chunk_words=8)
    if trigger.startswith("#slow"):
        return Script(text=" ".join(f"word{i}" for i in range(200)), delay=0.3, chunk_words=1)
    if trigger.startswith("#reason"):
        return Script(reasoning="Let me think about this step by step. First, the mock considers the question. "
                                "Then it decides. ", text="REASONED-ANSWER: 42")
    if trigger.startswith("#error500"):
        return Script(error=(500, "mock internal error"))
    if trigger.startswith("#error429"):
        return Script(error=(429, "mock rate limit"))
    if trigger.startswith("#badstream"):
        return Script(error=("badstream", ""))
    if trigger.startswith("#terminal"):
        command = trigger[len("#terminal"):].strip() or "echo mock-terminal-ok"
        return Script(tool_calls=[call("terminal", {"command": command})])
    if trigger.startswith("#danger"):
        return Script(tool_calls=[call("terminal", {"command": "rm -rf /tmp/mock-danger-dir"})])
    if trigger.startswith("#parallel"):
        return Script(tool_calls=[call("terminal", {"command": f"echo parallel-{i}"}) for i in range(3)])
    if trigger.startswith("#clarify") and "clarify" in tools:
        return Script(tool_calls=[call("clarify", {"questions": [
            {"question": "Which color do you want?", "choices": ["Red", "Green", "Blue"]}]})])
    if trigger.startswith("#todo") and "todo" in tools:
        return Script(tool_calls=[call("todo", {"todos": [
            {"id": "1", "content": "First mock task", "status": "completed"},
            {"id": "2", "content": "Second mock task", "status": "in_progress"},
            {"id": "3", "content": "Third mock task", "status": "pending"}]})])
    if trigger.startswith("#tool"):
        parts = trigger.split(None, 2)
        name = parts[1] if len(parts) > 1 else ""
        try:
            arguments = json.loads(parts[2]) if len(parts) > 2 else args_for(tools.get(name, {}).get("parameters", {}))
        except json.JSONDecodeError:
            arguments = {}
        return Script(tool_calls=[call(name, arguments)])
    return Script(text="MOCK-REPLY: " + (user_text.strip().split("\n")[0][:120] or "(no user message)"))


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def send_json(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?")[0].rstrip("/")
        log({"t": time.time(), "method": "GET", "path": path})
        if path in ("/v1/models", "/models"):
            self.send_json(200, {"object": "list", "data": [
                {"id": MODEL, "object": "model", "created": 0, "owned_by": "mock"},
                {"id": "mock-model-2", "object": "model", "created": 0, "owned_by": "mock"}]})
        elif path in ("", "/health", "/v1"):
            self.send_json(200, {"ok": True})
        else:
            self.send_json(404, {"error": {"message": "not found: " + path}})

    def do_POST(self):
        path = self.path.split("?")[0].rstrip("/")
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            body = {}
        if path.endswith("/embeddings"):
            inputs = body.get("input") if isinstance(body.get("input"), list) else [body.get("input")]
            self.send_json(200, {"object": "list", "model": body.get("model", MODEL), "data": [
                {"object": "embedding", "index": i, "embedding": [0.01] * 16} for i in range(len(inputs))]})
            return
        if not path.endswith("/chat/completions"):
            log({"t": time.time(), "method": "POST", "path": path, "unhandled": True})
            self.send_json(404, {"error": {"message": "mock only speaks chat/completions: " + path}})
            return
        script = decide(body)
        messages = body.get("messages", [])
        last_user = next((text_of(m.get("content")) for m in reversed(messages) if m.get("role") == "user"), "")
        log({"t": time.time(), "method": "POST", "path": path, "model": body.get("model"),
             "stream": bool(body.get("stream")), "messages": len(messages),
             "tools": sorted(t.get("function", {}).get("name") for t in body.get("tools", []) or []),
             "last_user": last_user[:200], "last_role": messages[-1].get("role") if messages else None,
             "answer": script.text[:80], "tool_calls": [c["function"]["name"] for c in script.tool_calls],
             "tail": " ".join(text_of(m.get("content")) for m in messages[-4:])[-400:],
             "error": script.error[0] if script.error else None})
        if script.error and script.error[0] != "badstream":
            status, message = script.error
            if status == 429:
                self.send_response(429)
                data = json.dumps({"error": {"message": message, "type": "rate_limit_error"}}).encode()
                self.send_header("Retry-After", "1")
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_json(status, {"error": {"message": message, "type": "server_error"}})
            return
        if body.get("stream"):
            self.stream(body, script)
        else:
            self.whole(body, script)

    def whole(self, body, script):
        message = {"role": "assistant", "content": script.text or None}
        if script.reasoning:
            message["reasoning_content"] = script.reasoning
        if script.tool_calls:
            message["tool_calls"] = script.tool_calls
        self.send_json(200, {
            "id": "chatcmpl-" + uuid.uuid4().hex[:12], "object": "chat.completion", "created": int(time.time()),
            "model": body.get("model", MODEL),
            "choices": [{"index": 0, "message": message,
                         "finish_reason": "tool_calls" if script.tool_calls else "stop"}],
            "usage": {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120}})

    def stream(self, body, script):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        cid = "chatcmpl-" + uuid.uuid4().hex[:12]
        model = body.get("model", MODEL)

        def chunk(delta, finish=None):
            payload = {"id": cid, "object": "chat.completion.chunk", "created": int(time.time()), "model": model,
                       "choices": [{"index": 0, "delta": delta, "finish_reason": finish}]}
            self.wfile.write(b"data: " + json.dumps(payload).encode() + b"\n\n")
            self.wfile.flush()

        try:
            chunk({"role": "assistant", "content": ""})
            if script.error and script.error[0] == "badstream":
                self.wfile.write(b"data: {not json\n\n")
                self.wfile.flush()
                return
            for words in pieces(script.reasoning, 4):
                chunk({"reasoning_content": words})
                time.sleep(0.02)
            for words in pieces(script.text, script.chunk_words):
                chunk({"content": words})
                time.sleep(script.delay or 0.02)
            for index, tool_call in enumerate(script.tool_calls):
                arguments = tool_call["function"]["arguments"]
                half = len(arguments) // 2
                chunk({"tool_calls": [{"index": index, "id": tool_call["id"], "type": "function",
                                       "function": {"name": tool_call["function"]["name"], "arguments": arguments[:half]}}]})
                chunk({"tool_calls": [{"index": index, "function": {"arguments": arguments[half:]}}]})
            chunk({}, "tool_calls" if script.tool_calls else "stop")
            if (body.get("stream_options") or {}).get("include_usage"):
                usage = {"id": cid, "object": "chat.completion.chunk", "created": int(time.time()), "model": model,
                         "choices": [], "usage": {"prompt_tokens": 100, "completion_tokens": 20, "total_tokens": 120}}
                self.wfile.write(b"data: " + json.dumps(usage).encode() + b"\n\n")
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            log({"t": time.time(), "event": "client closed the stream"})


def pieces(text, size):
    if not text:
        return []
    words = re.split(r"(\s+)", text)
    out, current = [], ""
    count = 0
    for w in words:
        current += w
        if w.strip():
            count += 1
        if count >= size:
            out.append(current)
            current, count = "", 0
    if current:
        out.append(current)
    return out


def main():
    global LOG_PATH
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18700)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--log", default="mock_llm.jsonl")
    args = parser.parse_args()
    LOG_PATH = args.log
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.daemon_threads = True
    print(f"mock model on {args.host}:{args.port}, log {args.log}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    sys.exit(main())
