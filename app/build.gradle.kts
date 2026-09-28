import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// ── Release signing credentials ──────────────────────────────────────────────
// Credentials come from EITHER:
//   • CI: environment variables (GitHub Actions secrets) — KEYSTORE_FILE,
//     KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
//   • Local: a gitignored `keystore.properties` at repo root with keys
//     storeFile, storePassword, keyAlias, keyPassword
// If NEITHER is present, the release build stays UNSIGNED so anyone can still
// run `assembleRelease` without owning the keystore.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
fun signingCred(envName: String, propName: String): String? =
    System.getenv(envName) ?: keystoreProps.getProperty(propName)
val releaseStorePath: String? = signingCred("KEYSTORE_FILE", "storeFile")

android {
    namespace = "com.hermes.android"
    compileSdk = 36  // Latest Stable per ADR-012

    defaultConfig {
        applicationId = "com.hermes.android"
        minSdk = 29    // Android 10 per ADR-012
        targetSdk = 35 // Latest Stable per ADR-012
        versionCode = 26
        versionName = "2.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Fixed, committed debug key. CI runners otherwise mint a fresh ~/.android/debug.keystore
        // per run, so each debug APK had a different signature and could not update the last one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (releaseStorePath != null) {
                storeFile = file(releaseStorePath)
                storePassword = signingCred("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingCred("KEY_ALIAS", "keyAlias")
                keyPassword = signingCred("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    // The built-in Linux for x86_64 (emulators, Intel Chromebooks) is ~10 MB that no ARM phone
    // uses, so it ships only on request: ./gradlew assembleDebug -Phermes.x86_64=true
    if (providers.gradleProperty("hermes.x86_64").orNull == "true") {
        sourceSets.getByName("main") {
            assets.srcDir("src/x86_64/assets")
            jniLibs.srcDir("src/x86_64/jniLibs")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign only when a keystore is actually available; otherwise the
            // release APK is produced unsigned (build never breaks).
            signingConfig = if (releaseStorePath != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // proot runs from nativeLibraryDir, so the .so files must exist on disk.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Test JVM needs more heap for Robolectric
    tasks.withType<Test>().configureEach {
        maxHeapSize = "2g"
        jvmArgs("-Xmx2g", "-XX:MaxMetaspaceSize=512m")
        // Robolectric needs this to find its resources
        systemProperty("robolectric.logging.enabled", "false")
        // One line per test in the CI log, so a green run shows what ran.
        testLogging { events("passed", "skipped", "failed") }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.termux.terminal.view)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Media3 — the in-app audio/video player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.media3.session)

    // Networking — WebSocket client to tui_gateway
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.sse)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // Markdown parsing (CommonMark + GFM tables, strikethrough, task items, bare links) and LaTeX math
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.task.list.items)
    implementation(libs.jlatexmath.android)

    // Coil (image loading for HermesMarkdown)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ── Compose compiler report (render diagnostics) ─────────────────────────────
// Which composables can skip a recomposition, and which parameters are compared
// by instance rather than by value. Printed into the CI log after assembleDebug,
// limited to the chat screen's render path. Changes nothing in the APK.
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

val composeReportDir = layout.buildDirectory.dir("compose_compiler")
val printComposeReport by tasks.registering {
    val dir = composeReportDir
    doLast {
        val chatPath = setOf(
            "ChatScreen", "MessageBubble", "AssistantMessageBubble", "UserMessageBubble",
            "ToolCallCard", "InteractiveRequestCard", "HxThinkingTrace", "HxStatusLine",
            "HxShimmerText", "HermesMarkdown", "MdText", "CodeBlock", "CodeBlockCard",
            "MermaidBlockCard", "InlineImageBlock", "ArtifactCard", "HtmlBlockCard",
            "TypingDots", "InputBar", "WorkspaceDrawerSheet", "EmptyChatBody",
            "AgentTodoCard", "MessageActionIcon",
        )
        val files = dir.get().asFile.listFiles().orEmpty()
        val composables = files.filter { it.name.endsWith("-composables.txt") }
        if (composables.isEmpty()) {
            println("COMPOSE-REPORT: no report files (compile task came from cache?)")
            return@doLast
        }
        println("COMPOSE-REPORT ================================================")
        files.filter { it.name.endsWith("-module.json") }.forEach { println(it.readText()) }
        val entry = Regex("""(?ms)^(\w[^\n]*?)\bfun (\w+)\((.*?)^\)""")
        val notSkippable = mutableListOf<String>()
        composables.forEach { f ->
            entry.findAll(f.readText()).forEach { m ->
                val flags = m.groupValues[1]
                val name = m.groupValues[2]
                if ("restartable" in flags && "skippable" !in flags) notSkippable += name
                if (name in chatPath) {
                    println("COMPOSE-REPORT $flags fun $name(")
                    m.groupValues[3].lines().filter { it.isNotBlank() }
                        .forEach { println("COMPOSE-REPORT   ${it.trim()}") }
                    println("COMPOSE-REPORT )")
                }
            }
        }
        println("COMPOSE-REPORT restartable but not skippable: ${notSkippable.sorted()}")
        files.filter { it.name.endsWith("-classes.txt") }.forEach { f ->
            f.readText().split(Regex("(?m)^(?=\\S)")).filter { it.startsWith("unstable class") }
                .forEach { println("COMPOSE-REPORT " + it.lines().first()) }
        }
        println("COMPOSE-REPORT ================================================")
    }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(printComposeReport) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
