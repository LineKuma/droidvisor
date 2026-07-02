# Droidvisor Project Memory

## Project Overview
- Android virtualization/emulation app (Kotlin, Jetpack Compose, Material 3)
- Supports two runtime modes: AVF (Android Virtualization Framework) and QEMU
- Architecture: MVVM with bound Android Services (no DI framework)
- minSdk 34, targetSdk 35, compileSdk 35
- AVF API accessed via reflection (android.system.virtualmachine.* classes)

## Key Files
- `app/src/main/java/com/droidvisor/MainActivity.kt` - Entry point, service binding, navigation
- `app/src/main/java/com/droidvisor/ui/screen/PermissionScreen.kt` - Environment detection page (one-time display)
- `app/src/main/java/com/droidvisor/ui/viewmodel/PermissionViewModel.kt` - Permission state management
- `app/src/main/java/com/droidvisor/vm/AvfCapabilityChecker.kt` - AVF/QEMU/KVM capability detection
- `app/src/main/java/com/droidvisor/vm/VmManagerService.kt` - VM lifecycle management, mode selection
- `app/src/main/java/com/droidvisor/vm/qemu/VmRuntime.kt` - RuntimeType enum (AVF/QEMU)

## Mode Selection Logic
- Priority: AVF > QEMU (in VmManagerService.startVm())
- AVF availability checked via `_isAvfAvailable.value` (based on AvfCapabilityChecker.canRunRealVm)
- QEMU initialized in VmManagerService.onCreate() with optional KVM acceleration
- No runtime available → startVm() returns error (no simulation fallback)
- PermissionScreen is read-only, one-time display (persisted via DataStore)

## Build Environment
- Gradle wrapper at project root
- JDK: Android Studio JBR at C:/Users/LineCat/AppData/Local/Programs/Android Studio/jbr
- Android SDK: C:\Users\LineCat\AppData\Local\Android\Sdk
- detekt configured with maxIssues: 0 (baseline in detekt-baseline.xml)
