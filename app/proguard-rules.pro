# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Keep Compose metadata
-dontwarn androidx.compose.**

# Keep OkHttp (WebSocket client)
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Coroutines
-dontwarn kotlinx.coroutines.**

# Keep WorkManager
-keep class androidx.work.** { *; }
