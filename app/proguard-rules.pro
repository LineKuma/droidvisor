# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep model classes
-keep class com.droidvisor.docker.model.** { *; }
-keep class com.droidvisor.vm.model.** { *; }

# Keep AVF (Android Virtualization Framework) classes accessed via reflection
# VirtualMachineManagerService uses reflection to access these API classes
-keep class android.system.virtualmachine.VirtualMachineManager { *; }
-keep class android.system.virtualmachine.VirtualMachine { *; }
-keep class android.system.virtualmachine.VirtualMachineConfig { *; }
-keep class android.system.virtualmachine.VirtualMachineConfig$Builder { *; }
-keep class android.system.virtualmachine.VirtualMachineCallback { *; }
-keepclassmembers class android.system.virtualmachine.VirtualMachineManager {
    public *;
}
-keepclassmembers class android.system.virtualmachine.VirtualMachine {
    public *;
}
-keepclassmembers class android.system.virtualmachine.VirtualMachineConfig {
    public *;
}
-keepclassmembers class android.system.virtualmachine.VirtualMachineConfig$Builder {
    public *;
}
-keepclassmembers class android.system.virtualmachine.VirtualMachineCallback {
    public *;
}

# Keep VsockParcelables (used in AVF inter-process communication)
-keep class android.system.virtualmachine.VirtualMachineDescriptor { *; }

# Keep Logger utility (used across all services)
-keep class com.droidvisor.util.Logger { *; }

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
-keep class kotlinx.serialization.internal.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

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
