#!/usr/bin/env bash
#
# Droidvisor Docker Entrypoint
# Starts the Android emulator (headless) and waits for it to boot before
# handing off to the user-provided command (or keeping the container alive).
#
set -euo pipefail

: "${AVD_NAME:=droidvisor_test}"
: "${ANDROID_HOME:=/opt/android-sdk}"
: "${ANDROID_AVD_HOME:=/root/.android/avd}"

export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

start_emulator() {
    echo "[entrypoint] Starting emulator for AVD '${AVD_NAME}'..."

    if adb devices 2>/dev/null | grep -q "emulator-"; then
        echo "[entrypoint] Emulator already running"
        return 0
    fi

    emulator -avd "${AVD_NAME}" \
        -no-window \
        -no-audio \
        -gpu swiftshader_indirect \
        -no-snapshot \
        -no-metrics \
        -no-boot-anim \
        -wipe-data \
        &
    EMU_PID=$!
    echo "[entrypoint] Emulator PID: ${EMU_PID}"

    echo "[entrypoint] Waiting for adb device..."
    adb wait-for-device

    echo "[entrypoint] Waiting for boot_completed (timeout 300s)..."
    local elapsed=0
    local max_wait=300
    while [ $elapsed -lt $max_wait ]; do
        local boot
        boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
        if [ "$boot" = "1" ]; then
            echo "[entrypoint] Boot completed after ${elapsed}s"
            break
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done

    if [ $elapsed -ge $max_wait ]; then
        echo "[entrypoint] WARNING: boot timeout reached (${max_wait}s). Emulator may not be ready."
    fi

    echo "[entrypoint] Disabling animations..."
    adb shell settings put global window_animation_scale 0 2>/dev/null || true
    adb shell settings put global transition_animation_scale 2>/dev/null || true
    adb shell settings put global animator_duration_scale 0 2>/dev/null || true

    echo "[entrypoint] Emulator ready."
}

case "${1:-}" in
    --start-emulator)
        shift
        start_emulator
        if [ $# -gt 0 ]; then
            exec "$@"
        else
            echo "[entrypoint] Emulator running. Container will stay alive."
            exec tail -f /dev/null
        fi
        ;;
    --no-emulator)
        shift
        if [ $# -gt 0 ]; then
            exec "$@"
        else
            exec tail -f /dev/null
        fi
        ;;
    "")
        exec tail -f /dev/null
        ;;
    *)
        exec "$@"
        ;;
esac
