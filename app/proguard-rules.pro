# Definitive ProGuard Rules for FindBack

# 1. Keep EVERYTHING in our package to prevent renaming/stripping
# This is the safest way to ensure release APK works exactly like debug
-keep class com.balaji.findback.** { *; }
-keep interface com.balaji.findback.** { *; }
-keepclassmembers class com.balaji.findback.** { *; }

# 2. Keep BuildConfig strictly to ensure API Keys are never stripped or inlined incorrectly
-keep class com.balaji.findback.BuildConfig { *; }

# 3. Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# 4. GSON Rules (Required for Chat History persistence)
-keepattributes Signature, *Annotation*, EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory

# 5. Glide & Lottie (UI stability)
-keep class com.github.bumptech.glide.** { *; }
-keep class com.airbnb.lottie.** { *; }

# 6. General Stability
-dontoptimize
-dontobfuscate
-keepattributes SourceFile, LineNumberTable
-keep class org.json.** { *; }
