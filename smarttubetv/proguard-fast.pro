# YouNew: extra rules for the "Fast" test build only (gradle property -PyounewFast): code and resource shrinking.
# Careful on purpose: nothing is renamed, and everything the app, the player and the libraries it reaches by name
# (reflection, JSON models, native code) is kept. Only code that nothing can reach is removed.
-ignorewarnings
-dontobfuscate

-keep class com.liskovsoft.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-keep class androidx.leanback.** { *; }
-keep class com.eclipsesource.v8.** { *; }
-keep class org.chromium.** { *; }
-keep class com.google.net.cronet.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.jayway.jsonpath.** { *; }
-keep class net.minidev.** { *; }
-keep class com.bumptech.glide.** { *; }
-keep class io.reactivex.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }

-keepclasseswithmembernames class * { native <methods>; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
