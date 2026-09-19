# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Keep Compose metadata
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep OkHttp (WebSocket client)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hermes.android.**$$serializer { *; }
-keepclassmembers class com.hermes.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.hermes.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Coroutines
-dontwarn kotlinx.coroutines.**

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep Timber (logging)
-keep class timber.log.** { *; }

# Keep runtime models (serialized)
-keep class com.hermes.android.runtime.** { *; }
-keep class com.hermes.android.gateway.** { *; }

# Keep Hilt @Inject constructors
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Keep enum values (used in when expressions)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
