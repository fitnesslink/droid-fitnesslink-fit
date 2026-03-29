# Add project specific ProGuard rules here.

# Realm Kotlin SDK
-keep class io.realm.kotlin.** { *; }
-keep class org.mongodb.kbson.** { *; }
-keepnames class * extends io.realm.kotlin.types.RealmObject
