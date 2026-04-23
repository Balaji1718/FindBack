# Firebase ProGuard Rules
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }

# Keep AI related models for GSON/JSON parsing
-keep class com.balaji.findback.ChatMessage { *; }
-keep class com.balaji.findback.ChatSession { *; }
-keep class com.balaji.findback.UserModel { *; }
-keep class com.balaji.findback.Item { *; }
-keep class com.balaji.findback.Claim { *; }
-keep class com.balaji.findback.NotificationModel { *; }

# GSON rules to prevent field renaming
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.balaji.findback.** { <fields>; }

# Keep BuildConfig to ensure API keys are accessible
-keep class com.balaji.findback.BuildConfig { *; }

# Prevent shrinking of JSON response classes in NVIDIA/GROQ services
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# General project-wide keep rules for stability
-keep class com.balaji.findback.** { *; }
-dontwarn com.balaji.findback.**
