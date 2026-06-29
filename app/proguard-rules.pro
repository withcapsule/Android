# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.json.** { *; }

-keep class dev.withcapsule.android.data.** { *; }

-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Umami Analytics and its dependencies
-keep class dev.appoutlet.umami.** { *; }
-keep class dev.appoutlet.umami.api.** { *; }

# Ktor (used by Umami)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization internal serializer classes
-keep class * extends kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}

# Keep navigation route classes
-keep class dev.withcapsule.android.Upload { *; }
-keep class dev.withcapsule.android.Download { *; }
-keep class dev.withcapsule.android.History { *; }
-keep class dev.withcapsule.android.Settings { *; }
-keep class dev.withcapsule.android.QRScanner { *; }
-keep class dev.withcapsule.android.ui.screens.ScannerTarget { *; }

# ML Kit Barcode Scanning
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }

# Keep constructors for ML Kit (critical for R8 Full Mode)
-keepclassmembers class com.google.mlkit.** {
    <init>(...);
}

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# Keep native methods to prevent UnsatisfiedLinkErrors
-keepclasseswithmembernames class * {
    native <methods>;
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile