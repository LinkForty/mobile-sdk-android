# LinkForty SDK ProGuard/R8 rules
# These rules are applied to consuming apps

# Keep Moshi-generated adapters
-keep class com.linkforty.sdk.models.**JsonAdapter { *; }
-keep class com.linkforty.sdk.fingerprint.DeviceFingerprintJsonAdapter { *; }
-keep class com.linkforty.sdk.network.**JsonAdapter { *; }

# Keep public API classes
-keep class com.linkforty.sdk.LinkForty { *; }
-keep class com.linkforty.sdk.LinkFortyLogger { *; }
-keep class com.linkforty.sdk.models.** { *; }
-keep class com.linkforty.sdk.errors.LinkFortyError { *; }
-keep class com.linkforty.sdk.errors.LinkFortyError$* { *; }
-keep class com.linkforty.sdk.fingerprint.DeviceFingerprint { *; }

# Keep Moshi annotations
-keepattributes *Annotation*
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Keep Kotlin metadata for Moshi
-keep class kotlin.Metadata { *; }
