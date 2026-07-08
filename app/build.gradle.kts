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
    compileSdk = 35  // Latest Stable per ADR-012

    defaultConfig {
        applicationId = "com.hermes.android"
        minSdk = 29    // Android 10 per ADR-012
        targetSdk = 35 // Latest Stable per ADR-012
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (releaseStorePath != null) {
                storeFile = file(releaseStorePath)
                storePassword = signingCred("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingCred("KEY_ALIAS", "keyAlias")
                keyPassword = signingCred("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
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

    // Markdown rendering (Fix S4F01)
    implementation(libs.compose.markdown)

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
