# JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# WatermarkNative JNI bridge
-keep class com.shrimpfarm.app.WatermarkNative { *; }

# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.shrimpfarm.app.qa.model.** { *; }
-keep class com.shrimpfarm.app.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Glide
-keep class com.bumptech.glide.** { *; }

# WebView JS interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep crash handler accessible
-keep class com.shrimpfarm.app.CrashHandler { *; }
-keep class com.shrimpfarm.app.CrashReportActivity { *; }