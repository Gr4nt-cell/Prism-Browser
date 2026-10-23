# iBrowser R8 rules

# JavaScript bridges called by name from inject.js / page scripts
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Enum names are stored in settings (layout, search engine, site permissions) — keep them stable
-keepclassmembers enum com.gr4nt.browser.** { *; }

# Readable crash stack traces in Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp / Okio optional platform classes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
