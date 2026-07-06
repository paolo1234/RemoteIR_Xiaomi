# ProGuard rules per IRXiaomi

# Keep IR service reflection classes
-keep class com.xiaomi.ir.** { *; }
-keep class android.hardware.ir.** { *; }

# Keep Room entities
-keep class com.irxiaomi.db.** { *; }

# Keep Moshi serialization
-keep class com.irxiaomi.sync.IrCodeApi { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General Android
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
