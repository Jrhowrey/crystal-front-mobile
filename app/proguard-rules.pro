# Keep ADPF & JNI bridge methods
-keepclassmembers class com.crystalfront.mobilecommand.MainActivity$AdpfJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
