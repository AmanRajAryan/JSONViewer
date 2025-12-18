# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ------------------------------------------------------------
# General Android optimizations
# ------------------------------------------------------------

# Keep annotations used by Android
-keepattributes *Annotation*

# Preserve line numbers for readable crash reports (optional)
-keepattributes SourceFile,LineNumberTable

# ------------------------------------------------------------
# Rosemoe Code Editor (CRITICAL)
# ------------------------------------------------------------

# Core editor
-keep class io.github.rosemoe.** { *; }
-dontwarn io.github.rosemoe.**

# ------------------------------------------------------------
# TextMate (syntax highlighting used by Rosemoe)
# ------------------------------------------------------------

-keep class org.eclipse.tm4e.** { *; }
-dontwarn org.eclipse.tm4e.**

# ------------------------------------------------------------
# AndroidX libraries safety
# ------------------------------------------------------------

-keep class androidx.recyclerview.** { *; }
-keep class androidx.viewpager2.** { *; }

# ------------------------------------------------------------
# Remove Android Log calls in release (APK size + speed)
# ------------------------------------------------------------

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ------------------------------------------------------------
# WebView JavaScript interface (uncomment if used)
# ------------------------------------------------------------
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ------------------------------------------------------------
# Source file name hiding (optional)
# ------------------------------------------------------------
#-renamesourcefileattribute SourceFile