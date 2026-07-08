1|# Running Hermes2 on Android + Termux
2|
3|This is the practical runbook for bringing Hermes2 up on a real Android phone.
4|
5|The goal is:
6|
7|```text
8|Hermes2 Android app
9|  → starts/controls Hermes dashboard in Termux
10|  → connects to ws://127.0.0.1:9119/api/ws
11|  → uses Gemini / OpenRouter / other configured providers
12|```
13|
14|---
15|
16|## 0. What you need
17|
18|- Android phone with Termux from F-Droid
19|- Enough storage for Hermes Agent and Python/Rust wheels
20|- Network access
21|- One model provider key, for example:
22|23|  - Gemini API key
24|  - OpenRouter key
25|
26|For the current tested setup we use:
27|
28|```text
29|Gemini as main backend
30|DuckDuckGo/ddgs for free search
31|Local terminal backend
32|No messaging platforms
33|No browser automation / computer-use on Android
34|```
35|
36|---
37|
38|## 1. Install and prepare Termux
39|
40|Install Termux from F-Droid, not Play Store.
41|
42|Then in Termux:
43|
44|```bash
45|pkg update -y
46|pkg upgrade -y
47|pkg install -y git python clang rust make pkg-config libffi openssl ca-certificates curl llvm lld nodejs ripgrep ffmpeg
48|```
49|
50|Set Android/Rust build environment:
51|
52|```bash
53|export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
54|export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
55|export CARGO_HOME="$HOME/.hermes/cargo"
56|mkdir -p "$CARGO_HOME"
57|export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
58|export CARGO_PROFILE_RELEASE_LTO=false
59|export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
60|export CARGO_PROFILE_RELEASE_STRIP=none
61|export CARGO_BUILD_JOBS=1
62|```
63|
64|Why these variables matter:
65|
66|| Variable | Why |
67||---|---|
68|| `ANDROID_API_LEVEL` | Needed by maturin/jiter/pydantic-core builds on Android |
69|| `CARGO_BUILD_TARGET` | Forces Cargo to build for the native Android target |
70|| `CARGO_HOME=$HOME/.hermes/cargo` | Avoids broken user Cargo mirror configs such as USTC 404 |
71|| `CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse` | Uses crates.io sparse index |
72|| `CARGO_PROFILE_RELEASE_LTO=false` | Avoids Termux rustc ICE during pydantic-core builds |
73|| `CARGO_BUILD_JOBS=1` | Reduces phone memory/CPU pressure |
74|
75|---
76|
77|## 2. Allow Hermes2 to control Termux
78|
79|Hermes2 uses Termux `RUN_COMMAND`. Enable external app commands once:
80|
81|```bash
82|mkdir -p ~/.termux
83|cat > ~/.termux/termux.properties <<'EOF'
84|allow-external-apps=true
85|EOF
86|```
87|
88|Then fully restart Termux.
89|
90|Android may also ask the Hermes2 app for `RUN_COMMAND` permission. Grant it.
91|
92|---
93|
94|## 3. Clean old partial installs if needed
95|
96|If a previous failed install left a non-git folder:
97|
98|```bash
99|if [ -e "$HOME/.hermes/hermes-agent" ] && [ ! -d "$HOME/.hermes/hermes-agent/.git" ]; then
100|  mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.broken-$(date +%Y%m%d-%H%M%S)"
101|fi
102|```
103|
104|If you want a fully fresh install:
105|
106|```bash
107|mv "$HOME/.hermes/hermes-agent" "$HOME/.hermes/hermes-agent.backup-$(date +%Y%m%d-%H%M%S)" 2>/dev/null || true
108|```
109|
110|---
111|
112|## 4. Install Hermes Agent manually
113|
114|Clone official upstream:
115|
116|```bash
117|mkdir -p "$HOME/.hermes"
118|git clone https://github.com/NousResearch/hermes-agent.git "$HOME/.hermes/hermes-agent"
119|cd "$HOME/.hermes/hermes-agent"
120|```
121|
122|Create venv:
123|
124|```bash
125|rm -rf venv
126|python -m venv venv
127|source venv/bin/activate
128|python -m pip install --upgrade pip setuptools wheel
129|```
130|
131|Re-export build env inside the venv shell:
132|
133|```bash
134|export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
135|export CARGO_BUILD_TARGET="$(rustc -Vv | awk '/^host:/ {print $2; exit}')"
136|export CARGO_HOME="$HOME/.hermes/cargo"
137|mkdir -p "$CARGO_HOME"
138|export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
139|export CARGO_PROFILE_RELEASE_LTO=false
140|export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
141|export CARGO_PROFILE_RELEASE_STRIP=none
142|export CARGO_BUILD_JOBS=1
143|```
144|
145|Install Android psutil shim:
146|
147|```bash
148|python scripts/install_psutil_android.py --pip "python -m pip"
149|```
150|
151|Install the tested Termux profile:
152|
153|```bash
154|python -m pip install -e '.[termux]' -c constraints-termux.txt 2>&1 | tee ~/hermes-termux-install.log
155|```
156|
157|Install the web/dashboard extra required by the Android app WebSocket:
158|
159|```bash
160|python -m pip install -e '.[web]' -c constraints-termux.txt 2>&1 | tee ~/hermes-web-install.log
161|```
162|
163|Link the command:
164|
165|```bash
166|ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
167|```
168|
169|Verify:
170|
171|```bash
172|which hermes
173|hermes --version
174|hermes doctor
175|```
176|
177|Expected version output example:
178|
179|```text
180|Hermes Agent v0.17.0
181|Python: 3.13.x
182|OpenAI SDK: 2.24.0
183|```
184|
185|OpenAI SDK is a dependency name. It does **not** mean you must use OpenAI as your model provider.
186|
187|---
188|
189|## 5. Configure your model provider
190|
191|Edit env:
192|
193|```bash
194|nano "$HOME/.hermes/.env"
195|```
196|
197|Example for Google Gemini:
198|
199|```env
200|GEMINI_API_KEY=YOUR_GEMINI_KEY
201|
202|```
203|
204|Or for OpenRouter:
205|
206|```env
207|GEMINI_API_KEY=YOUR_TOKEN_PLAN_KEY
208|
209|```
210|
211|Configure Hermes:
212|
213|```bash
214|hermes config set model.provider gemini
215|hermes config set model.default gemini-2.5-flash
216|```
217|
218|Check your config:
219|
220|```bash
221|hermes config set model.default gemini-2.5-flash
222|# or
223|hermes config set model.default gemini-2.5-pro
224|```
225|
226|Check:
227|
228|```bash
229|cat "$HOME/.hermes/config.yaml"
230|hermes doctor
231|```
232|
233|You should see:
234|
235|```text
236|✓ gemini (key configured)
237|```
238|
239|---
240|
241|## 6. Optional: switch providers
242|
243|You can switch providers anytime:
244|
245|```env
246|
247|```
248|
249|
250|
251|```env
252|
253|```
254|
255|
256|
257|```bash
258|hermes config set model.provider gemini
259|hermes config set model.default gemini-2.5-flash
260|```
261|
262|Switch back:
263|
264|```bash
265|hermes config set model.provider gemini
266|hermes config set model.default gemini-2.5-flash
267|```
268|
269|---
270|
271|## 7. Recommended setup wizard choices
272|
273|If `hermes setup` asks:
274|
275|### Setup type
276|
277|Choose:
278|
279|```text
280|Full setup
281|```
282|
283|Do not choose Quick Setup unless you want Nous Portal OAuth.
284|
285|### Terminal backend
286|
287|Choose:
288|
289|```text
290|Keep current (local)
291|```
292|
293|or:
294|
295|```text
296|Local
297|```
298|
299|### Messaging platforms
300|
301|Select none for now.
302|
303|Messaging platforms are only needed if you want Telegram/Discord/WhatsApp/etc. bots.
304|
305|### CLI tools
306|
307|Recommended on Android:
308|
309|Keep enabled:
310|
311|```text
312|Web Search & Scraping
313|Terminal & Processes
314|File Operations
315|Code Execution
316|Text-to-Speech
317|Skills
318|Task Planning
319|Memory
320|Session Search
321|Clarifying Questions
322|Task Delegation
323|Cron Jobs
324|```
325|
326|Disable for now:
327|
328|```text
329|Browser Automation
330|Computer Use
331|Image Generation
332|Video Generation
333|X Search
334|Home Assistant
335|Spotify
336|Yuanbao
337|```
338|
339|### Web search provider
340|
341|Choose:
342|
343|```text
344|DuckDuckGo (ddgs) — free, no key, search only
345|```
346|
347|This still allows Hermes to search and collect web information. Browser automation is different; it controls a real browser and is not needed for normal web search.
348|
349|---
350|
351|## 8. Test Hermes in Termux
352|
353|After provider setup:
354|
355|```bash
356|hermes doctor --fix
357|hermes doctor
358|```
359|
360|Test a model response:
361|
362|```bash
363|hermes -q "سلام، فقط در یک جمله بگو با چه مدلی جواب می‌دهی."
364|```
365|
366|If the CLI syntax changes, use:
367|
368|```bash
369|hermes chat -q "سلام، تست اتصال مدل"
370|```
371|
372|---
373|
374|## 9. Start from the Android app
375|
376|<div dir="rtl">
377|
378|این مراحل را فقط **یکبار**، برای اولین اتصال انجام بده. بعد از آن، هر بار که اپ را باز کنی **خودکار وصل میشود**.
379|
380|</div>
381|
382|### 9a. Release Termux ports
383|
384|Stop any manually running dashboard first:
385|
386|```bash
387|hermes dashboard --stop
388|```
389|
390|Then **leave Termux and force-stop it**:
391|
392|**Android Settings → Apps → Termux → Force stop**
393|
394|<div dir="rtl">
395|
396|> **چرا force-stop؟** موقع نصب، Termux پورتهایی را اشغال میکند و رها نمیکند. با force-stop، تمام پورتهای فعال روی Termux غیرفعال میشوند تا اپ بتواند یک اتصال تمیز بگیرد. این فقط **دفعهی اول** لازم است.
397|
398|</div>
399|
400|### 9b. Connect from the app
401|
402|1. Open **Hermes2** app
403|2. Tap **`Open runtime host app`** → Termux opens
404|3. **Come back to Hermes2**
405|4. Tap **`Start Agent Gateway`**
406|5. **Wait up to 30 seconds** — status turns to **✓ Connected**
407|
408|Why start from the app?
409|
410|The app generates and injects its own `HERMES_DASHBOARD_SESSION_TOKEN`. If you manually start `hermes dashboard` with a different token, the app WebSocket authentication will fail.
411|
412|> [!TIP]
413|> If it doesn't connect within 30 s, **repeat steps 9a and 9b**. The first handshake occasionally needs a second pass — after that it stays automatic.
414|
415|### 9c. From second time onwards
416|
417|<div dir="rtl">
418|
419|فقط اپ را باز کن. خودش وصل میشود. نه Termux، نه force-stop، نه هیچ کار اضافی.
420|
421|</div>
422|
423|After gateway starts, go to:
424|
425|```text
426|Settings
427|```
428|
429|Confirm:
430|
431|```text
432|Provider: gemini
433|Model: gemini-2.5-flash / gemini-2.5-flash / gemini-2.5-pro
434|```
435|
436|The Tools tab should list the capabilities currently exposed by Hermes.
437|
438|---
439|
440|## 10. Debugging
441|
442|### Gateway logs
443|
444|```bash
445|cat "$HOME/.hermes/logs/gateway_stdout.log"
446|```
447|
448|Or from the app:
449|
450|```text
451|Termux & Agent Connection → Fetch & View Logs
452|```
453|
454|### pydantic-core / rustc crash
455|
456|If you see:
457|
458|```text
459|rustc panicked
460|linker-plugin-lto
461|```
462|
463|Make sure these are exported before pip install:
464|
465|```bash
466|export CARGO_PROFILE_RELEASE_LTO=false
467|export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
468|export CARGO_PROFILE_RELEASE_STRIP=none
469|export CARGO_BUILD_JOBS=1
470|```
471|
472|### Cargo mirror 404
473|
474|If you see:
475|
476|```text
477|Updating `ustc` index
478|unexpected http status code: 404
479|```
480|
481|Use clean Cargo home:
482|
483|```bash
484|export CARGO_HOME="$HOME/.hermes/cargo"
485|mkdir -p "$CARGO_HOME"
486|export CARGO_REGISTRIES_CRATES_IO_PROTOCOL=sparse
487|```
488|
489|### `hermes` command missing
490|
491|```bash
492|cd "$HOME/.hermes/hermes-agent"
493|ln -sf "$PWD/venv/bin/hermes" "$PREFIX/bin/hermes"
494|which hermes
495|hermes --version
496|```
497|
498|---
499|
500|## 11. What not to do
501|