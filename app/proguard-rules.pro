# Code & Line Number Preservations
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Native JNI Bridge Protection (MTE & Memory Zeroing)
-keep class com.hamoon.uncleted.util.NativeSecurityBridge {
    native <methods>;
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# StrongBox & Titan M2 KeyStore Providers
-keep class android.security.keystore.** { *; }
-keep class androidx.security.crypto.** { *; }

# JavaMail and Activation
-keep class javax.mail.** { *; }
-keep class javax.mail.internet.** { *; }
-keep class javax.activation.** { *; }
-keep class java.beans.** { *; }
-dontwarn java.beans.**
-dontwarn javax.activation.**
-keep class com.sun.mail.** { *; }
-keep class com.sun.mail.smtp.** { *; }
-keep class com.sun.mail.handlers.** { *; }
-dontwarn com.sun.mail.**

-keepresources META-INF/javamail.*
-keepresources META-INF/mailcap*

# BouncyCastle Cryptographic Provider (Ed25519 Engine)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# LSPosed / Xposed API Hooks
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep hook implementations & Receivers
-keep class com.hamoon.uncleted.hooks.** { *; }
-keepclassmembers class com.hamoon.uncleted.hooks.** { *; }
-keep class com.hamoon.uncleted.receivers.** { *; }