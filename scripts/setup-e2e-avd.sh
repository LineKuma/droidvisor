#!/usr/bin/env bash
#
# Droidvisor E2E setup: installs required SDK components, creates an AVD,
# builds & installs the debug APK, and runs the homepage-route E2E tests.
#
# Run on the host machine where Android Studio and adb are available.
#
set -euo pipefail

: "${ANDROID_HOME:=$HOME/Android/Sdk}"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/cmdline-tools/cmdline-tools/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

AVD_NAME="droidvisor_test"
SYSTEM_IMAGE="system-images;android-34;default;x86_64"

echo "==> Accepting SDK licenses"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

echo "==> Installing SDK components (this may take a few minutes)"
sdkmanager --install \
    "platform-tools" \
    "emulator" \
    "platforms;android-34" \
    "platforms;android-35" \
    "build-tools;34.0.0" \
    "$SYSTEM_IMAGE"

echo "==> Creating AVD '$AVD_NAME'"
if avdmanager list avd 2>/dev/null | grep -q "$AVD_NAME"; then
    echo "    AVD already exists, skipping"
else
    echo "no" | avdmanager create avd \
        --force \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE" \
        --device pixel_6 \
        --abi "x86_64"
fi

echo "==> Starting AVD (headless, no-window)"
if ! adb devices | grep -q "emulator-"; then
    emulator -avd "$AVD_NAME" -no-window -no-audio -gpu swiftshader_indirect -no-snapshot &
    EMU_PID=$!
    echo "    Emulator PID: $EMU_PID"
fi

echo "==> Waiting for device to boot"
adb wait-for-device
for i in $(seq 1 60); do
    BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [ "$BOOT" = "1" ]; then
        echo "    Boot complete after ${i}s"
        break
    fi
    sleep 2
done

BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
if [ "$BOOT" != "1" ]; then
    echo "    ERROR: Emulator failed to boot within timeout"
    exit 1
fi

echo "==> Disabling animations for faster E2E"
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

echo "==> Building & installing debug APK, then running E2E"
cd "$(dirname "$0")/.."
./gradlew :app:installDebug :app:installDebugAndroidTest

echo "==> Granting AVF permission"
adb shell pm grant com.droidvisor android.permission.MANAGE_VIRTUAL_MACHINE || true

echo "==> Running HomepageRouteE2ETest"
adb shell am instrument -w -r \
    -e class com.droidvisor.e2e.HomepageRouteE2ETest \
    -e debug false \
    com.droidvisor.test/androidx.test.runner.AndroidJUnitRunner
