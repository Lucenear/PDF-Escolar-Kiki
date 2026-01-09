# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# AndroidPdfViewer Rules
-keep class com.github.barteksc.pdfviewer.** { *; }
-keep class com.shockwave.** { *; }
-dontwarn com.github.barteksc.pdfviewer.**