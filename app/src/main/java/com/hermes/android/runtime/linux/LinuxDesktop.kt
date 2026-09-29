package com.hermes.android.runtime.linux

import android.content.Context
import com.hermes.android.R
import com.hermes.android.gateway.StdioGatewayHub
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aether's Alpine Chrome (`AlpineChromeController`), for Hermes: a virtual X display (Xvnc :99)
 * running openbox and Chromium, shown in the app through noVNC. Hermes' own browser tools drive
 * this Chromium through `browser.cdp_url`, so the user watches — and can take over — the same
 * browser the agent uses. Other programs the agent starts with `DISPLAY=:99` (xdotool, xterm, …)
 * show up on the same desktop.
 *
 * Every app on the phone shares 127.0.0.1, so Chromium opens no debugging port of its own: it
 * speaks CDP over a pipe to `cdp_pipe.py` (assets/desktop), which serves it on [CdpPort] only
 * under a per-install secret path ([cdpUrl]) that Hermes and this class know.
 *
 * The stack is the guest script `hermes-desktop` ([DesktopScript]), so the agent can also
 * start it from its terminal; this class only launches it, watches the ports and stops it.
 */
@Singleton
class LinuxDesktop @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: ProotEnvironment,
    private val stdioHub: StdioGatewayHub,
) {
    /** [pageWidth]: how wide Chromium lays pages out (CSS px), drawn scaled into [width] pixels. */
    enum class Resolution(
        val width: Int,
        val height: Int,
        val titleEn: String,
        val titleFa: String,
        val pageWidth: Int = width,
    ) {
        // Not the phone's full 1080x2040: every frame is drawn on the CPU and sent over VNC, and
        // half the pixels gave ~4x the frames on the server mock; the viewer scales it up. Pages
        // still lay out 1080 wide: at 720, desktop sites (Google…) overflowed and overlapped.
        PHONE(720, 1360, "Phone (portrait)", "گوشی (عمودی)", pageWidth = 1080),
        TABLET(1600, 1000, "Tablet (landscape)", "تبلت (افقی)"),
        DESKTOP(1920, 1080, "Desktop (1080p)", "دسکتاپ (1080p)"),
    }

    data class Settings(
        /** Hermes' browser tools use this Chromium (`browser.cdp_url`); it starts on demand ([beforeHermesStarts]). */
        val agentBrowser: Boolean = true,
        val resolution: Resolution = Resolution.PHONE,
        val homepage: String = DefaultHomepage,
        /** Serve VNC on the local network (port [VncPort]) with [vncPassword]. */
        val vncLan: Boolean = false,
        val vncPassword: String = "",
    )

    sealed interface State {
        data object Stopped : State
        data object Starting : State
        data object Running : State
        data class Error(val message: String) : State
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var process: Process? = null

    /** websockify, held for as long as the user is watching. */
    @Volatile
    private var viewerProcess: Process? = null

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _viewing = MutableStateFlow(false)

    /** Whether the VNC bridge is up — i.e. the desktop is streamable right now. */
    val viewing: StateFlow<Boolean> = _viewing.asStateFlow()

    /** Port of the running VNC bridge; a free one is picked each time the viewer opens. */
    @Volatile
    private var viewerPort = NoVncPort

    /**
     * noVNC's page for the in-app viewer; carries the VNC password, which is never empty.
     * Load it only after [startViewer] succeeds: that is what proves the port is ours.
     */
    val viewerUrl: String
        get() {
            val password = URLEncoder.encode(_settings.value.effectiveVncPassword, "UTF-8")
            return "http://127.0.0.1:$viewerPort/vnc_lite.html?autoconnect=true&scale=true&show_dot=true" +
                "&path=websockify&password=$password"
        }

    /** Per-install secret in Chromium's DevTools address; without it the port answers 404. */
    private val cdpSecret: String by lazy {
        prefs.getString(KEY_CDP_SECRET, null)?.takeIf { it.isNotBlank() }
            ?: randomHex(16).also { prefs.edit().putString(KEY_CDP_SECRET, it).apply() }
    }

    /** What Hermes' `browser.cdp_url` is set to. */
    private val cdpUrl: String get() = "http://127.0.0.1:$CdpPort/$cdpSecret"

    /**
     * The password the VNC server enforces: the user's when they share on the LAN, an auto-generated
     * per-install secret otherwise.
     *
     * There is no such thing as a private loopback on Android — 127.0.0.1 is the same
     * interface for every app on the device, and INTERNET is a permission users are never
     * asked about. An unauthenticated VNC server therefore handed any
     * installed app full view and control of this desktop, including whatever the user is
     * signed into in its Chromium. `-localhost` does not help: it only excludes the LAN.
     */
    private val Settings.effectiveVncPassword: String
        get() = if (vncLan && vncPassword.isNotEmpty()) vncPassword else localVncSecret

    /** Per-install VNC secret, so the desktop is never reachable without one. */
    private val localVncSecret: String by lazy {
        prefs.getString(KEY_VNC_SECRET, null)?.takeIf { it.isNotBlank() }
            ?: newVncSecret().also { prefs.edit().putString(KEY_VNC_SECRET, it).apply() }
    }

    /**
     * A fresh VNC secret. VncAuth's DES key is 8 bytes and `vncpasswd` silently truncates
     * anything longer, so 8 characters is the whole budget — spend it on a wide alphabet.
     */
    private fun newVncSecret(): String {
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        return (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun randomHex(bytes: Int): String =
        ByteArray(bytes).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Chromium, the X server, noVNC, agent-browser and the CDP proxy's python3 are all present. */
    suspend fun isInstalled(): Boolean =
        environment.isRootfsInstalled && runCatching { environment.run(VerifyCommand).ok }.getOrDefault(false)

    /** Re-reads whether the desktop runs (it may have been started or stopped by the agent). */
    suspend fun refresh() {
        if (_state.value is State.Starting) return
        val up = isUp()
        _state.value = when {
            up -> State.Running
            _state.value is State.Error -> _state.value
            else -> State.Stopped
        }
    }

    suspend fun start(): Result<Unit> = mutex.withLock {
        if (isUp()) {
            _state.value = State.Running
            return@withLock Result.success(Unit)
        }
        _state.value = State.Starting
        runCatching {
            check(environment.isRootfsInstalled) { "Install the built-in Linux runtime first." }
            withContext(Dispatchers.IO) { writeGuestFiles() }
            val started = withContext(Dispatchers.IO) {
                environment.processBuilder("hermes-desktop start")
                    .redirectOutput(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
                    .start()
                    .also { it.outputStream.close() }
            }
            process = started
            waitUntilUp(started)
            _state.value = State.Running
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Timber.w(error, "[Desktop] Start failed")
            stopLocked()
            _state.value = State.Error(error.message ?: "The desktop did not start.")
        }
    }

    suspend fun stop() = mutex.withLock { stopLocked() }

    /**
     * Brings up the VNC bridge the user watches through, starting the desktop first if the
     * agent has not already. Kept apart from [start] deliberately: the agent's browsing needs
     * Chromium on the X display, not a live feed of it, so websockify only costs memory while
     * somebody is actually looking — and nothing is streamable until the user asks.
     */
    suspend fun startViewer(): Result<Unit> {
        start().onFailure { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            if (viewerProcess?.isAlive == true && portOpen(viewerPort)) {
                _viewing.value = true
                return@withContext Result.success(Unit)
            }
            runCatching {
                stopViewerProcess()
                // A port nobody holds right now, not a fixed one another app could sit on.
                val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
                // The page URL carries the VNC password, so the listener must prove it is our
                // websockify before the viewer loads it: it serves this file, fresh each time.
                val nonce = randomHex(16)
                environment.guestFile(ViewerNoncePath).writeText(nonce)
                val log = environment.guestFile(LogPath).also { it.parentFile?.mkdirs() }
                val started = environment.processBuilder("hermes-desktop view $port")
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                    .start()
                    .also { it.outputStream.close() }
                viewerProcess = started
                check(waitForPort(port)) { "The VNC bridge did not come up." }
                check(servedText(port, ViewerNoncePath.substringAfterLast('/')) == nonce) {
                    "Another app is using the viewer's port; try again."
                }
                viewerPort = port
                _viewing.value = true
            }.onFailure {
                stopViewerProcess()
                Timber.w(it, "[Desktop] Could not start the viewer bridge")
            }
        }
    }

    /**
     * Fire-and-forget [stopViewer] on a scope that outlives the viewer screen — a ViewModel's
     * own scope is already cancelled by the time its onDispose runs, which would have left
     * the bridge up exactly when the user walked away from it.
     */
    fun stopViewerAsync() {
        appScope.launch { stopViewer() }
    }

    /** Stops the VNC bridge; the desktop itself keeps running for the agent. */
    suspend fun stopViewer() {
        withContext(Dispatchers.IO) { stopViewerProcess() }
        _viewing.value = false
    }

    private fun stopViewerProcess() {
        val running = viewerProcess ?: return
        viewerProcess = null
        running.destroy()
        if (!running.waitFor(3, TimeUnit.SECONDS)) running.destroyForcibly()
    }

    private suspend fun waitForPort(port: Int): Boolean {
        repeat(40) {
            if (portOpen(port)) return true
            delay(100)
        }
        return false
    }

    suspend fun restart(): Result<Unit> {
        stop()
        return start()
    }

    /**
     * Saves [next], rewrites the guest config and points Hermes' browser tools at (or away
     * from) this Chromium. Returns true when a running desktop must restart to apply it.
     */
    suspend fun update(next: Settings): Boolean {
        val previous = _settings.value
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_AGENT_BROWSER, next.agentBrowser)
            .putString(KEY_RESOLUTION, next.resolution.name)
            .putString(KEY_HOMEPAGE, next.homepage)
            .putBoolean(KEY_VNC_LAN, next.vncLan)
            .putString(KEY_VNC_PASSWORD, next.vncPassword)
            .apply()
        if (environment.isRootfsInstalled) withContext(Dispatchers.IO) { writeGuestFiles() }
        if (previous.agentBrowser != next.agentBrowser) applyAgentBrowser(next.agentBrowser)
        val needsRestart = previous.resolution != next.resolution ||
            previous.vncLan != next.vncLan ||
            (next.vncLan && previous.vncPassword != next.vncPassword)
        return needsRestart && _state.value == State.Running
    }

    /**
     * Called just before Hermes starts, so the gateway reads a current `gateway.env` (the
     * browser's secret address). It used to run once Hermes was up, and the gateway after an
     * install or an update ran with no or a stale `BROWSER_CDP_URL`. Writes the guest config
     * and stops there: Chromium and its X server are several hundred megabytes of RAM that
     * most sessions never touch, so the desktop starts when something actually needs it — the
     * agent's first browser call (the skill tells it to run `hermes-desktop start`) or the user
     * opening the viewer.
     */
    suspend fun beforeHermesStarts() {
        if (!environment.isRootfsInstalled) return
        withContext(Dispatchers.IO) { runCatching { writeGuestFiles() } }
    }

    /**
     * Points Hermes' browser tools at this Chromium. The next gateway start reads
     * `BROWSER_CDP_URL` from the env file [writeGuestFiles] wrote — free. Only a gateway that
     * is already running needs `hermes config`, which costs seconds of Python startup, so it
     * runs just for that case.
     */
    suspend fun applyAgentBrowser(enabled: Boolean): Result<Unit> = runCatching {
        if (!environment.isRootfsInstalled || !stdioHub.isReady) return@runCatching
        val command = if (enabled) {
            "hermes config set browser.cdp_url $cdpUrl"
        } else {
            "hermes config unset browser.cdp_url"
        }
        val result = environment.run(command)
        check(result.ok) { result.output.lines().lastOrNull { it.isNotBlank() }.orEmpty() }
    }.onFailure { Timber.w(it, "[Desktop] Could not update browser.cdp_url") }

    /** Deletes Chromium's profile (cookies, logins, history). The desktop must be stopped. */
    suspend fun clearBrowserData(): Result<Unit> = runCatching {
        stop()
        val result = environment.run("rm -rf $ProfileDir")
        check(result.ok) { result.output }
    }

    /** Wi-Fi address other devices can reach VNC on, when LAN access is on. */
    fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()?.let { "$it:$VncPort" }

    /**
     * Whether the page's focused element takes text — Aether's `shouldShowKeyboardAfterClick`,
     * so a tap on a text field in the viewer brings up the phone's keyboard.
     */
    suspend fun focusedElementIsEditable(y: Int): Boolean {
        if (y in BrowserUiTop..BrowserUiBottom) return true
        delay(140)
        return runCatching { evaluate(FocusedEditableScript) == true }.getOrDefault(false)
    }

    private suspend fun stopLocked() {
        withContext(Dispatchers.IO) {
            if (environment.isRootfsInstalled) {
                runCatching { environment.run("hermes-desktop stop") }
            }
            process?.let { running ->
                running.destroy()
                if (!running.waitFor(3, TimeUnit.SECONDS)) running.destroyForcibly()
            }
        }
        process = null
        withContext(Dispatchers.IO) { stopViewerProcess() }
        _viewing.value = false
        _state.value = State.Stopped
    }

    private suspend fun waitUntilUp(started: Process) {
        val deadline = System.currentTimeMillis() + StartTimeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (isUp()) return
            // Exit 0 means another copy (started by the agent) already owns the desktop.
            if (!started.isAlive && started.exitValue() != 0) break
            delay(300)
        }
        error(
            "The desktop did not start." +
                logTail().takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty(),
        )
    }

    /**
     * The desktop is up when Chromium answers through our proxy; the noVNC bridge is a
     * separate, user-driven thing. The proxy proves it knows the secret without it being sent,
     * so another app holding the port is neither mistaken for the desktop nor handed the secret.
     */
    private suspend fun isUp(): Boolean = withContext(Dispatchers.IO) {
        val challenge = randomHex(16)
        servedText(CdpPort, "hermes-probe?c=$challenge") == cdpProbeAnswer(cdpSecret, challenge)
    }

    /** Body of `http://127.0.0.1:[port]/[path]`, or null when nothing (or an error) answers. */
    private fun servedText(port: Int, path: String): String? = runCatching {
        http.newCall(Request.Builder().url("http://127.0.0.1:$port/$path").build()).execute().use { reply ->
            if (reply.isSuccessful) reply.body?.string()?.trim() else null
        }
    }.getOrNull()

    private fun portOpen(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }
    }.isSuccess

    private fun logTail(): String = runCatching {
        environment.guestFile(LogPath).readLines().takeLast(12).joinToString("\n")
    }.getOrDefault("")

    private fun writeGuestFiles() {
        val script = environment.guestFile(ScriptPath)
        script.parentFile?.mkdirs()
        script.writeText(DesktopScript)
        script.setExecutable(true, false)
        val proxy = environment.guestFile(CdpProxyPath)
        proxy.parentFile?.mkdirs()
        context.assets.open(CdpProxyAsset).use { input -> proxy.outputStream().use { input.copyTo(it) } }

        val current = _settings.value
        val stateDir = environment.guestFile(StateDir).apply { mkdirs() }
        val onLan = current.vncLan && current.vncPassword.isNotEmpty()
        stateDir.resolve("desktop.env").writeText(
            buildString {
                appendLine("WIDTH=${current.resolution.width}")
                appendLine("HEIGHT=${current.resolution.height}")
                val resolution = current.resolution
                appendLine("PAGE_W=${resolution.pageWidth}")
                appendLine("PAGE_H=${resolution.height * resolution.pageWidth / resolution.width}")
                appendLine("SCALE=${"%.4f".format(java.util.Locale.ROOT, resolution.width.toFloat() / resolution.pageWidth)}")
                appendLine("HOMEPAGE=${shellQuote(current.homepage.ifBlank { DefaultHomepage })}")
                // VNC_LAN only decides whether the VNC server also listens off-device; the password
                // below is enforced either way.
                appendLine("VNC_LAN=${if (onLan) 1 else 0}")
                appendLine("VNC_PASSWORD=${shellQuote(current.effectiveVncPassword)}")
                appendLine("CDP_SECRET=$cdpSecret")
            },
        )
        stateDir.resolve("desktop.env").setReadable(false, false)
        stateDir.resolve("desktop.env").setReadable(true, true)
        // Read by the gateway script at startup — the env wins over config.yaml.
        stateDir.resolve("gateway.env").writeText(
            if (current.agentBrowser) "export BROWSER_CDP_URL=$cdpUrl\n" else "",
        )
        stateDir.resolve("gateway.env").setReadable(false, false)
        stateDir.resolve("gateway.env").setReadable(true, true)
        writeSkill()
        writeFonts()
    }

    /**
     * Persian in Chromium: the app's own Vazirmatn goes into the guest, and fontconfig picks it
     * for Persian and Arabic text. Latin text keeps the system fonts.
     */
    private fun writeFonts() {
        val dir = environment.guestFile(FontDir).apply { mkdirs() }
        for ((resource, name) in BundledFonts) {
            val target = File(dir, name)
            if (target.isFile && target.length() > 0L) continue
            context.resources.openRawResource(resource).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        // Rewritten only on change: a new rule costs the desktop a full font-cache rebuild.
        val config = environment.guestFile(FontConfigPath).apply { parentFile?.mkdirs() }
        if (!config.isFile || config.readText() != PersianFontConfig) config.writeText(PersianFontConfig)
    }

    /** Tells the agent how to use the desktop (screenshots, xdotool) beyond the browser tools. */
    private fun writeSkill() {
        val skill = environment.guestFile("/root/.hermes/skills/android/android-desktop/SKILL.md")
        skill.parentFile?.mkdirs()
        skill.writeText(DesktopSkill)
    }

    private suspend fun evaluate(expression: String): Any? = withContext(Dispatchers.IO) {
        val targets = http.newCall(Request.Builder().url("$cdpUrl/json/list").build())
            .execute().use { JSONArray(it.body?.string().orEmpty()) }
        val page = (0 until targets.length()).map { targets.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "page" && it.optString("webSocketDebuggerUrl").isNotBlank() }
            ?: return@withContext null
        val reply = CompletableDeferred<JSONObject>()
        val socket = http.newWebSocket(
            Request.Builder().url(page.getString("webSocketDebuggerUrl")).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        JSONObject()
                            .put("id", 1)
                            .put("method", "Runtime.evaluate")
                            .put("params", JSONObject().put("expression", expression).put("returnByValue", true))
                            .toString(),
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                    if (message.optInt("id") == 1) reply.complete(message)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reply.completeExceptionally(t)
                }
            },
        )
        try {
            withTimeoutOrNull(3_000) { reply.await() }
                ?.optJSONObject("result")?.optJSONObject("result")?.opt("value")
        } finally {
            socket.close(1000, null)
        }
    }

    private fun loadSettings() = Settings(
        agentBrowser = prefs.getBoolean(KEY_AGENT_BROWSER, true),
        resolution = prefs.getString(KEY_RESOLUTION, null)
            ?.let { name -> Resolution.values().firstOrNull { it.name == name } }
            ?: Resolution.PHONE,
        homepage = prefs.getString(KEY_HOMEPAGE, null) ?: DefaultHomepage,
        vncLan = prefs.getBoolean(KEY_VNC_LAN, false),
        vncPassword = prefs.getString(KEY_VNC_PASSWORD, null).orEmpty(),
    )

    companion object {
        const val CdpPort = 9222
        const val VncPort = 5900
        const val NoVncPort = 6080
        const val DefaultHomepage = "https://duckduckgo.com"
        const val AgentBrowserSpec = "agent-browser@^0.26.0"

        /**
         * `apk add` set for the desktop. The Linux's repo has the X server (Xvfb) and the
         * libraries, but not Chromium, a VNC server or the X tools: [PostInstall] brings those.
         * The library list is what the [DebianPackages] programs load from this Linux (checked
         * with ldd on Alpaquita); the rest are Chrome's and the CDP proxy's.
         */
        val Packages = listOf(
            "xvfb", "xauth", "fontconfig", "font-dejavu", "python3", "unzip",
            // Xvnc, xdotool, openbox, xprop, xclip, xterm
            "cairo", "freetype", "glib", "harfbuzz", "libjpeg-turbo", "libpng", "libssl3", "libx11",
            "libxaw", "libxcursor", "libxdamage", "libxext", "libxfixes", "libxft", "libxi",
            "libxinerama", "libxkbcommon", "libxml2", "libxmu", "libxpm", "libxrandr", "libxrender",
            "libxt", "libxtst", "libncursesw", "pango", "zlib", "dbus-libs", "linux-pam",
            // Chrome for Testing
            "nss", "gtk+3.0", "at-spi2-core", "libxcomposite", "mesa-gbm", "alsa-lib", "cups-libs",
            "libdrm", "libxshmfence", "eudev-libs",
        )

        // 154 exits at start under proot (SIGTRAP, any flags); 153 runs. Checked 2026-09-27.
        private const val ChromeVersion = "153.0.8010.52"
        /** Installs [DebianPackages]; copied from the APK into the Linux before [PostInstall] runs. */
        const val DebianFetchAsset = "desktop/debian_fetch.py"
        const val DebianFetchPath = "/usr/local/lib/hermes/debian_fetch.py"

        /**
         * The X programs the Linux's repo lacks, from Debian's official archive (see
         * debian_fetch.py), with the libraries of theirs this Linux does not have.
         */
        private const val DebianPackages =
            "tigervnc-standalone-server tigervnc-tools libjpeg62-turbo libaudit1 libcap-ng0 libselinux1 " +
                "libsystemd0 libunwind8 libxcvt0 " +
                "xdotool libxdo3 " +
                "openbox libobrender32v5 libobt2v5 libstartup-notification0 libxcb-util1 libimlib2t64 " +
                "x11-utils xclip xterm libutempter0 libtinfo6"

        /**
         * After apk: the X programs from Debian (see [DebianPackages]), noVNC 1.6.0 (the viewer
         * loads its vnc_lite.html),
         * websockify, Google's Chrome for Testing (it has arm64 Linux builds; the repo has no
         * Chromium), and the CLI Hermes' browser tools talk to Chromium through.
         */
        const val PostInstall = "set -e; a=\$(uname -m); cd /tmp; " +
            "python3 $DebianFetchPath trixie $DebianPackages; " +
            // Debian names them through its alternatives system, which is not run here.
            "ln -sf /usr/local/bin/Xtigervnc /usr/local/bin/Xvnc; " +
            "ln -sf /usr/local/bin/tigervncpasswd /usr/local/bin/vncpasswd; " +
            "mkdir -p /usr/share/novnc; " +
            "curl -fsSL --retry 3 https://github.com/novnc/noVNC/archive/refs/tags/v1.6.0.tar.gz " +
            "| tar -xz --strip-components=1 -C /usr/share/novnc; " +
            "[ -x /opt/websockify/bin/pip ] || python3 -m venv /opt/websockify; " +
            "/opt/websockify/bin/pip install -q websockify==0.13.0; " +
            "ln -sf /opt/websockify/bin/websockify /usr/local/bin/websockify; " +
            "case \$a in aarch64) p=linux-arm64 ;; *) p=linux64 ;; esac; " +
            "if [ \"\$(cat /opt/chrome/.version 2>/dev/null)\" != $ChromeVersion ]; then " +
            "curl -fL --retry 3 -o /tmp/chrome.zip " +
            "https://storage.googleapis.com/chrome-for-testing-public/$ChromeVersion/\$p/chrome-\$p.zip; " +
            "rm -rf /opt/chrome /tmp/chrome-\$p; unzip -q /tmp/chrome.zip -d /tmp; " +
            "mv /tmp/chrome-\$p /opt/chrome; echo $ChromeVersion > /opt/chrome/.version; rm -f /tmp/chrome.zip; fi; " +
            "ln -sf /opt/chrome/chrome /usr/local/bin/chromium; " +
            "npm install -g --no-fund --no-audit $AgentBrowserSpec"

        const val VerifyCommand =
            "(chromium-browser --version || chromium --version) && command -v Xvnc && command -v openbox && " +
                "command -v xprop && command -v websockify && test -d /usr/share/novnc && " +
                "command -v xdotool && command -v agent-browser && command -v python3"

        private const val PREFS_NAME = "hermes_linux_desktop"
        private const val KEY_AGENT_BROWSER = "agent_browser"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_HOMEPAGE = "homepage"
        private const val KEY_VNC_LAN = "vnc_lan"
        private const val KEY_VNC_PASSWORD = "vnc_password"
        private const val KEY_VNC_SECRET = "vnc_local_secret"
        private const val KEY_CDP_SECRET = "cdp_secret"

        private const val StartTimeoutMillis = 60_000L
        private const val ScriptPath = "/usr/local/bin/hermes-desktop"
        private const val CdpProxyAsset = "desktop/cdp_pipe.py"
        private const val CdpProxyPath = "/usr/local/lib/hermes/cdp_pipe.py"
        /** Served by the viewer's websockify (its --web root is /usr/share/novnc); see [startViewer]. */
        private const val ViewerNoncePath = "/usr/share/novnc/hermes-viewer.txt"
        private const val StateDir = "/root/.hermes/android"
        private const val ProfileDir = "/root/.hermes/chrome-profile"
        private const val LogPath = "/root/.hermes/logs/desktop.log"
        private const val LogMaxBytes = 20_000_000
        private const val FontDir = "/usr/share/fonts/hermes"
        private const val FontConfigPath = "/etc/fonts/conf.d/65-hermes-persian.conf"

        private val BundledFonts = listOf(
            R.font.vazirmatn_regular to "Vazirmatn-Regular.ttf",
            R.font.vazirmatn_medium to "Vazirmatn-Medium.ttf",
            R.font.vazirmatn_semibold to "Vazirmatn-SemiBold.ttf",
            R.font.vazirmatn_bold to "Vazirmatn-Bold.ttf",
        )

        // Every font but Vazirmatn gives up the Arabic-script ranges at scan time, so Persian
        // letters always fall back to Vazirmatn (DejaVu Sans' basic Arabic won otherwise), while
        // Latin text keeps the system fonts. Pages marked Persian or Arabic get it first too.
        private val PersianFontConfig = """
            <?xml version="1.0"?>
            <!DOCTYPE fontconfig SYSTEM "fonts.dtd">
            <!-- Written by the Hermes Android app; edits are overwritten. -->
            <fontconfig>
              <match target="scan">
                <test name="family" compare="not_eq" qual="all"><string>Vazirmatn</string></test>
                <edit name="charset" mode="assign">
                  <minus>
                    <name>charset</name>
                    <charset>
                      <range><int>0x0600</int><int>0x06FF</int></range>
                      <range><int>0x0750</int><int>0x077F</int></range>
                      <range><int>0x08A0</int><int>0x08FF</int></range>
                      <range><int>0xFB50</int><int>0xFDFF</int></range>
                      <range><int>0xFE70</int><int>0xFEFF</int></range>
                    </charset>
                  </minus>
                </edit>
              </match>
              <match target="pattern">
                <test name="lang" compare="contains"><string>fa</string></test>
                <edit name="family" mode="prepend" binding="strong"><string>Vazirmatn</string></edit>
              </match>
              <match target="pattern">
                <test name="lang" compare="contains"><string>ar</string></test>
                <edit name="family" mode="prepend" binding="strong"><string>Vazirmatn</string></edit>
              </match>
            </fontconfig>
        """.trimIndent() + "\n"

        // Chromium's own toolbar (address bar) in the Phone layout — Aether's keyboard band.
        private const val BrowserUiTop = 34
        private const val BrowserUiBottom = 100

        private val FocusedEditableScript = """
            (() => {
              const e = document.activeElement;
              return Boolean(e && !e.disabled && !e.readOnly && (e.isContentEditable ||
                e.tagName === 'INPUT' || e.tagName === 'TEXTAREA' || e.tagName === 'SELECT'));
            })()
        """.trimIndent()

        private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

        /**
         * Aether's Chrome launch, as a standalone guest command. Chromium restarts if its
         * window is closed (the agent's CDP endpoint must stay up); the desktop lives as long
         * as the X server (Xvnc) does.
         */
        private val DesktopScript = """
            #!/bin/sh
            # Written by the Hermes Android app on every start — edits are overwritten.
            # Usage: hermes-desktop [start|stop|status|wait|view PORT]
            STATE=$StateDir
            PIDS="${'$'}STATE/desktop.pids"
            LOG=$LogPath
            CDP_PORT=$CdpPort VNC_PORT=$VncPort NOVNC_PORT=$NoVncPort
            CDP_PROXY=$CdpProxyPath
            WIDTH=1080 HEIGHT=2040 HOMEPAGE=about:blank VNC_LAN=0 VNC_PASSWORD= CDP_SECRET=
            [ -f "${'$'}STATE/desktop.env" ] && . "${'$'}STATE/desktop.env"
            export DISPLAY=:99 CDP_SECRET

            # Chromium answers through cdp_pipe.py, which proves it knows CDP_SECRET without
            # it being sent: another app holding the port gets no secret and no "running".
            running() { python3 "${'$'}CDP_PROXY" probe --port "${'$'}CDP_PORT" >/dev/null 2>&1; }
            # The desktop's own process is alive. Unlike running(), this does not depend on a
            # busy Chromium answering within two seconds.
            alive() { [ -f "${'$'}PIDS" ] && kill -0 "${'$'}(cut -d' ' -f1 "${'$'}PIDS")" 2>/dev/null; }

            stop_desktop() {
                if [ -f "${'$'}PIDS" ]; then
                    for pid in ${'$'}(cat "${'$'}PIDS"); do kill "${'$'}pid" 2>/dev/null || true; done
                    rm -f "${'$'}PIDS"
                fi
                pkill -x chromium 2>/dev/null || true; pkill -x chrome 2>/dev/null || true
            }

            case "${'$'}{1:-start}" in
                status) if running; then echo running; exit 0; fi; echo stopped; exit 1 ;;
                stop) stop_desktop; echo stopped; exit 0 ;;
                # Runs in the foreground: whoever starts it owns it, and killing that
                # process is what turns the stream off. proot's --kill-on-exit would reap a
                # backgrounded one the moment this script returned.
                view)
                    alive || running || { echo "the desktop is not running" >&2; exit 1; }
                    for i in ${'$'}(seq 1 50); do [ -S "${'$'}STATE/vnc.sock" ] && break; sleep 0.1; done
                    exec websockify --web=/usr/share/novnc \
                        --unix-target="${'$'}STATE/vnc.sock" 127.0.0.1:"${'$'}{2:-${'$'}NOVNC_PORT}" ;;
                wait)
                    for i in ${'$'}(seq 1 120); do running && { echo running; exit 0; }; sleep 0.5; done
                    echo "desktop did not start; see ${'$'}LOG" >&2; exit 1 ;;
                start) ;;
                *) echo "usage: hermes-desktop [start|stop|status|wait|view PORT]" >&2; exit 2 ;;
            esac

            if running || alive; then
                # Never restart a live desktop: that would throw away the user's browser.
                echo "Desktop already running: DISPLAY=:99, CDP http://127.0.0.1:${'$'}CDP_PORT"
                exit 0
            fi
            stop_desktop
            mkdir -p "${'$'}STATE" "${'$'}(dirname "${'$'}LOG")" $ProfileDir /tmp/.X11-unix
            # Chromium can repeat one error hundreds of times a second: a 12-hour session left an
            # 870 MB log of a single zygote line. Output goes through a filter that folds repeats
            # (ignoring Chromium's [pid:tid:time] prefix) and stops writing at LOG_MAX bytes; an
            # oversized log from an earlier run is kept once as desktop.log.1.
            LOG_MAX=$LogMaxBytes
            [ "${'$'}( (wc -c < "${'$'}LOG") 2>/dev/null || echo 0)" -lt "${'$'}LOG_MAX" ] || mv -f "${'$'}LOG" "${'$'}LOG.1"
            FIFO="${'$'}STATE/desktop.log.fifo"
            rm -f "${'$'}FIFO"
            if mkfifo "${'$'}FIFO" 2>/dev/null; then
                awk -v max="${'$'}LOG_MAX" -v size="${'$'}( (wc -c < "${'$'}LOG") 2>/dev/null || echo 0)" '
                    function out(s) {
                        if (size >= max) return
                        size += length(s) + 1; print s
                        if (size >= max) print "=== desktop.log reached its size limit; the rest of this run is not logged"
                        fflush()
                    }
                    { key = ${'$'}0; sub(/^\[[^]]*\]/, "", key) }
                    key == last { n++; next }
                    { if (n) out("    (last line repeated " n " more times)"); n = 0; last = key; out(${'$'}0) }
                    END { if (n) out("    (last line repeated " n " more times)") }
                ' < "${'$'}FIFO" >> "${'$'}LOG" 2>&1 &
                exec > "${'$'}FIFO" 2>&1
                rm -f "${'$'}FIFO"
            else
                exec >>"${'$'}LOG" 2>&1
            fi
            # Persian font (see writeFonts()). Its scan rule only reaches fonts that are already
            # cached through a full rebuild, so that runs once per change of the rule.
            if [ ! -f "${'$'}STATE/fonts.stamp" ] || [ $FontConfigPath -nt "${'$'}STATE/fonts.stamp" ]; then
                fc-cache -f >/dev/null 2>&1 && touch "${'$'}STATE/fonts.stamp"
            else
                fc-cache $FontDir >/dev/null 2>&1 || true
            fi
            echo "=== hermes-desktop start ${'$'}(date) ${'$'}{WIDTH}x${'$'}{HEIGHT}"
            set -eu
            rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 $ProfileDir/SingletonLock \
                $ProfileDir/SingletonSocket $ProfileDir/SingletonCookie
            CHROME_BIN="${'$'}(command -v chromium-browser || command -v chromium || true)"
            if [ -z "${'$'}CHROME_BIN" ]; then echo "Chromium is not installed"; exit 1; fi
            # Without a secret the browser would be anybody's; the app writes one into desktop.env.
            if [ -z "${'$'}CDP_SECRET" ]; then echo "No CDP secret in desktop.env; start the desktop from the app"; exit 1; fi

            # Always authenticate. Every app on an Android device shares 127.0.0.1, so an
            # open VNC server is an open door, not a local-only convenience. A stale desktop.env
            # without a password gets a random one — the viewer failing to connect is the
            # safe outcome; an unauthenticated desktop is not.
            if [ -z "${'$'}VNC_PASSWORD" ]; then
                VNC_PASSWORD=${'$'}(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n' | cut -c1-8)
            fi
            printf '%s\n' "${'$'}VNC_PASSWORD" | vncpasswd -f > "${'$'}STATE/vncpasswd"
            chmod 600 "${'$'}STATE/vncpasswd"
            # No TCP port unless the user deliberately shares on the LAN: Xvnc listens on a
            # unix socket inside the rootfs, which the Android sandbox really does keep to
            # this app — unlike 127.0.0.1, which every app on the phone shares. websockify
            # bridges that socket to the viewer, and only while somebody is watching.
            rm -f "${'$'}STATE/vnc.sock"
            VNC_ACCESS="-SecurityTypes VncAuth -PasswordFile ${'$'}STATE/vncpasswd"
            VNC_ACCESS="${'$'}VNC_ACCESS -rfbunixpath ${'$'}STATE/vnc.sock -rfbunixmode 0600"
            if [ "${'$'}VNC_LAN" = 1 ]; then
                VNC_ACCESS="${'$'}VNC_ACCESS -rfbport ${'$'}VNC_PORT"
            else
                VNC_ACCESS="${'$'}VNC_ACCESS -rfbport -1"
            fi

            cleanup() {
                trap - EXIT INT TERM
                echo "=== hermes-desktop stopped ${'$'}(date)"
                for pid in ${'$'}{CHROME_LOOP:-} ${'$'}{OPENBOX_PID:-} ${'$'}{VNC_PID:-}; do
                    kill "${'$'}pid" 2>/dev/null || true
                done
                pkill -x chromium 2>/dev/null || true; pkill -x chrome 2>/dev/null || true
                rm -f "${'$'}PIDS"
            }
            trap cleanup EXIT INT TERM

            # Xvnc is the X server and the VNC server in one (TigerVNC, from Debian): with Xvfb +
            # x11vnc every frame was copied between two processes without shared memory (none on
            # Android), about 1/6 of the frames on the server mock.
            # shellcheck disable=SC2086
            Xvnc :99 -geometry "${'$'}{WIDTH}x${'$'}{HEIGHT}" -depth 24 ${'$'}VNC_ACCESS \
                -AlwaysShared -extension MIT-SHM -nolock -ac &
            VNC_PID=${'$'}!
            echo ${'$'}${'$'} > "${'$'}PIDS"
            for i in ${'$'}(seq 1 50); do [ -S /tmp/.X11-unix/X99 ] && break; sleep 0.1; done
            test -S /tmp/.X11-unix/X99
            openbox &
            OPENBOX_PID=${'$'}!

            # Chromium opens no debugging port: it speaks CDP over a pipe to cdp_pipe.py, which
            # serves it on CDP_PORT under CDP_SECRET only, and restarts Chromium if it exits
            # (up to five quick failures), as the agent's endpoint must stay up.
            python3 "${'$'}CDP_PROXY" serve --port "${'$'}CDP_PORT" -- \
                "${'$'}CHROME_BIN" --no-sandbox --disable-dev-shm-usage --disable-gpu \
                --disable-gpu-compositing --disable-gpu-rasterization --no-first-run \
                --no-default-browser-check --password-store=basic \
                --user-data-dir=$ProfileDir \
                --force-device-scale-factor="${'$'}{SCALE:-1}" \
                --window-size="${'$'}{PAGE_W:-${'$'}WIDTH},${'$'}{PAGE_H:-${'$'}HEIGHT}" --window-position=0,0 --start-maximized \
                --ozone-platform=x11 "${'$'}HOMEPAGE" &
            CHROME_LOOP=${'$'}!

            for i in ${'$'}(seq 1 300); do
                xprop -root _NET_CLIENT_LIST 2>/dev/null | grep -q '0x' && break
                kill -0 "${'$'}CHROME_LOOP" 2>/dev/null || exit 1
                sleep 0.1
            done
            echo ${'$'}${'$'} ${'$'}VNC_PID ${'$'}OPENBOX_PID ${'$'}CHROME_LOOP > "${'$'}PIDS"
            echo "desktop up: DISPLAY=:99 CDP=${'$'}CDP_PORT VNC=${'$'}VNC_PORT (run 'view on' to watch)"
            wait "${'$'}VNC_PID" || echo "Xvnc exited with ${'$'}?"
        """.trimIndent() + "\n"

        private val DesktopSkill = """
            ---
            name: android-desktop
            description: "The phone's built-in Linux desktop: Chromium (used by the browser tools) on a VNC display :99 the user can watch; screenshots and mouse/keyboard via xdotool."
            version: 1.0.0
            author: Hermes Android
            platforms: [linux]
            metadata:
              hermes:
                tags: [Browser, Desktop, VNC, Android]
            prerequisites:
              commands: [hermes-desktop, xdotool]
            ---

            # Built-in desktop (Hermes Android)

            Hermes runs inside a small Linux on the user's phone. The app provides a virtual
            desktop — X display `:99`, openbox, Chromium — which the user can watch live in
            the app's VNC viewer (Linux → Browser & desktop). Anything you do there, they can
            see, but only while they have that screen open.

            **The desktop is not running by default.** Chromium and its X server cost the
            phone several hundred megabytes, so they start on demand and nothing streams
            until the user asks for it.

            ## Browser
            - Start the desktop before your first browser call of a session:
              `nohup hermes-desktop start >/dev/null 2>&1 &` then `hermes-desktop wait`.
              It is idempotent and returns at once when the desktop is already up.
            - The `browser_*` tools then drive this Chromium over CDP; `browser.cdp_url` is
              already set, and its secret path is the only way in (a bare
              `http://127.0.0.1:9222` answers 404), so no other app on the phone can drive
              this browser. Prefer these tools for web pages.
            - A browser tool that fails to connect means the desktop is down — start it as
              above and retry.
            - `hermes-desktop stop` when a long job no longer needs a browser; it gives the
              phone its memory back.
            - Logins and cookies persist in `/root/.hermes/chrome-profile`.

            ## Whole desktop (VNC)
            Use these for things outside a web page (dialogs, other X programs):
            - Screenshot: `DISPLAY=:99 scrot -o /tmp/screen.png`, then look at it with the
              vision tool.
            - Mouse: `DISPLAY=:99 xdotool mousemove X Y click 1` (screen size:
              `DISPLAY=:99 xdotool getdisplaygeometry`).
            - Keyboard: `DISPLAY=:99 xdotool type --delay 20 'text'`,
              `DISPLAY=:99 xdotool key ctrl+l Return`.
            - Clipboard: `DISPLAY=:99 xclip -selection clipboard -o`.
            - Start a GUI program: `DISPLAY=:99 nohup xterm >/dev/null 2>&1 &`.
            - Status / stop: `hermes-desktop status`, `hermes-desktop stop`.

            Ask before typing passwords or payment details; the user can also take over
            by tapping in the viewer.
        """.trimIndent() + "\n"
    }
}

/** What cdp_pipe.py answers to `/hermes-probe?c=[challenge]`: HMAC-SHA256 keyed by the secret, hex. */
internal fun cdpProbeAnswer(secret: String, challenge: String): String {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(challenge.toByteArray()).joinToString("") { "%02x".format(it) }
}
