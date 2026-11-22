# Add project specific ProGuard rules here.

# Realm Kotlin SDK
-keep class io.realm.kotlin.** { *; }
-keep class org.mongodb.kbson.** { *; }
-keepnames class * extends io.realm.kotlin.types.RealmObject

# --- Gson -------------------------------------------------------------------
# Wire models and the local JSON cache are (de)serialized reflectively by field
# name, and most models do not use @SerializedName. Renaming or removing fields
# would break API parsing and make JSON already stored on devices unreadable, so
# keep field names and constructors of app classes. Class and method names can
# still be obfuscated, and library code is fully optimized.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class com.fitnesslink.fit.** {
    <fields>;
    <init>(...);
}

# LenientEnumAdapterFactory (network/ApiClient.kt) reads enum constants via
# getField(name) / getDeclaredField("rawValue" | "displayName" | "label").
-keepclassmembers enum com.fitnesslink.fit.** { *; }

# Adapters referenced via @JsonAdapter or registered on GsonBuilder are created
# reflectively.
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
