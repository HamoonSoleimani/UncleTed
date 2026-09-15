# Add project specific ProGuard rules here.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

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

# BouncyCastle Cryptographic Provider
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# LSPosed / Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep hook implementations
-keep class com.hamoon.uncleted.hooks.** { *; }
-keepclassmembers class com.hamoon.uncleted.hooks.** { *; }