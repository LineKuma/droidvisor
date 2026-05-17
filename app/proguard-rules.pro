# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep model classes
-keep class com.droidvisor.docker.model.** { *; }
-keep class com.droidvisor.vm.model.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences$Key {
    public static final *;
}

# Keep Compose
-keep @androidx.compose.runtime.Composable class * { *; }

# Keep Kotlin serializable
-keepattributes *Annotation*
-keep @kotlinx.serialization.Serializable class * {*;}

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }

# Preserve line numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*
