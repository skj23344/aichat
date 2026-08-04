# ── Gson 反射 ──
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# DTO 类全部保留（Gson 通过反射反序列化，字段名/枚举不能混淆）
-keep class com.coder.aichat.data.api.dto.** { *; }
-keepclassmembers enum com.coder.aichat.data.api.dto.** { *; }

# 供应商实现类保留（经 ProviderRegistry 多态注册调用）
-keep class com.coder.aichat.data.api.providers.** { *; }

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.stream.** { *; }

# ── OkHttp / Retrofit ──
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# ── Room（官方规则已自动应用，额外兜底） ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
