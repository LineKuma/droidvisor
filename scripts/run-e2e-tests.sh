#!/bin/bash
#
# Droidvisor E2E Testing Script
# 功能：在Docker容器中构建APK、启动模拟器、运行Instrumentation测试
# 验证项目自身功能（QemuVmRuntime、VM管理、网络配置等）
# 无外部提供者——所有测试验证项目代码自身功能
# 使用方法：./scripts/run-e2e-tests.sh [options]
# 选项：--skip-build 跳过Docker构建 | --headful 使用有头模式(带UI) | --help 显示帮助
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose-e2e.yml"
BUILD_LOG="${PROJECT_ROOT}/e2e-build.log"
TEST_REPORT_DIR="${PROJECT_ROOT}/app/build/reports/androidTests"
TEST_RESULT_DIR="${PROJECT_ROOT}/app/build/outputs/androidTest-results"

show_help() {
    cat << EOF
Droidvisor E2E Testing Script

用法: $(basename "$0") [选项]

选项:
  --skip-build        跳过Docker镜像构建(使用现有镜像)
  --headful           使用有头模式(需要X11支持)
  --help              显示此帮助信息

示例:
  $(basename "$0")                 # 构建镜像并运行E2E测试(无头模式)
  $(basename "$0") --skip-build    # 使用现有镜像运行E2E测试
  $(basename "$0") --headful       # 使用有头模式运行测试

环境要求:
  - Docker 和 Docker Compose 已安装
  - KVM支持(可选,用于加速虚拟化)
  - docker-compose-e2e.yml 存在于项目根目录

测试说明:
  - 单一容器架构（无外部提供者）
  - 编译APK → 启动模拟器 → 运行Instrumentation测试(US001-US009)
  - 验证项目自身 QemuVmRuntime 功能

EOF
}

log_info() {
    local msg="$1"
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - ${msg}"
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - ${msg}" >> "${BUILD_LOG}"
}

log_error() {
    local msg="$1"
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - ${msg}" >&2
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - ${msg}" >> "${BUILD_LOG}"
}

log_step() {
    local msg="$1"
    echo "[E2E-STEP] $(date '+%Y-%m-%d %H:%M:%S') - ${msg}"
    echo "[E2E-STEP] $(date '+%Y-%m-%d %H:%M:%S') - ${msg}" >> "${BUILD_LOG}"
}

check_prerequisites() {
    log_info "检查E2E测试先决条件..."

    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装或不在 PATH 中"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装或不在 PATH 中"
        exit 1
    fi

    if [ ! -f "${COMPOSE_FILE}" ]; then
        log_error "docker-compose-e2e.yml 不存在于: ${COMPOSE_FILE}"
        exit 1
    fi

    # 检查KVM可用性
    if [ -e /dev/kvm ]; then
        log_info "KVM设备可用,虚拟化将使用硬件加速"
    else
        log_info "KVM设备不可用,将使用软件虚拟化(性能较慢)"
    fi

    log_info "E2E测试先决条件检查通过"
}

cleanup_e2e_environment() {
    log_info "清理旧E2E测试环境..."

    cd "${PROJECT_ROOT}"

    docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans 2>/dev/null || true

    # 清理特定容器
    docker stop droidvisor-android-e2e 2>/dev/null || true
    docker rm droidvisor-android-e2e 2>/dev/null || true

    docker image prune -f 2>/dev/null || true

    log_info "旧E2E测试环境清理完成"
}

build_e2e_docker_images() {
    log_info "构建E2E测试Docker镜像..."

    cd "${PROJECT_ROOT}"

    log_step "构建 android-e2e-test 镜像"
    docker compose -f "${COMPOSE_FILE}" build 2>&1 | tee -a "${BUILD_LOG}"

    if [ $? -eq 0 ]; then
        log_info "E2E Docker镜像构建成功"
    else
        log_error "E2E Docker镜像构建失败"
        exit 1
    fi
}

start_e2e_container() {
    log_info "启动E2E测试容器..."

    cd "${PROJECT_ROOT}"

    docker compose -f "${COMPOSE_FILE}" up -d android-e2e-test

    log_info "等待E2E容器启动完成..."

    local max_wait=60
    local wait_count=0

    while [ $wait_count -lt $max_wait ]; do
        local e2e_status=$(docker inspect -f '{{.State.Running}}' droidvisor-android-e2e 2>/dev/null || echo "false")

        if [ "$e2e_status" = "true" ]; then
            log_info "E2E测试容器已启动"
            log_step "Android E2E测试容器就绪"
            return 0
        fi

        sleep 3
        wait_count=$((wait_count + 3))
    done

    log_error "E2E容器启动超时"
    docker compose -f "${COMPOSE_FILE}" logs
    exit 1
}

compile_apk() {
    log_info "编译APK..."
    log_step "编译Debug APK和测试APK"

    docker exec droidvisor-android-e2e \
        /workspace/gradlew assembleDebug assembleDebugAndroidTest \
        --no-daemon \
        --stacktrace 2>&1 | tee -a "${BUILD_LOG}"

    if [ $? -ne 0 ]; then
        log_error "APK编译失败"
        return 1
    fi

    log_step "APK编译成功"

    # 验证APK生成
    docker exec droidvisor-android-e2e \
        bash -c "test -f /workspace/app/build/outputs/apk/debug/app-debug.apk && echo 'Debug APK ready'"
    docker exec droidvisor-android-e2e \
        bash -c "test -f /workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk && echo 'Test APK ready'"

    log_info "APK编译验证完成"
}

start_emulator() {
    log_info "启动Android模拟器（无头模式）..."
    log_step "启动Android模拟器"

    # 启动Xvfb虚拟显示
    docker exec -d droidvisor-android-e2e \
        bash -c "Xvfb :0 -screen 0 1080x1920x24 &"
    sleep 2

    # 启动adb server
    docker exec droidvisor-android-e2e adb start-server || true

    # 启动emulator（后台运行）
    docker exec -d droidvisor-android-e2e \
        bash -c '
          export DISPLAY=:0
          $ANDROID_HOME/emulator/emulator \
            -avd e2e_test_avd \
            -no-window \
            -gpu off \
            -noaudio \
            -no-boot-anim \
            -no-snapshot \
            -no-snapshot-save \
            -accel off \
            -memory 2048 \
            -netdelay none \
            -netspeed full \
            -verbose 2>&1 | tee /tmp/emulator-launch.log
        '

    # 等待模拟器设备可见
    log_info "等待模拟器设备可见（最长10分钟）..."
    timeout 600 sh -c '
      while ! docker exec droidvisor-android-e2e adb devices 2>/dev/null | grep -q emulator; do
        echo "  [$(date +%H:%M:%S)] Waiting for emulator device..."
        sleep 10
      done
    ' && log_info "模拟器设备已检测到" || log_error "模拟器设备检测超时"

    # 等待系统启动完成
    log_info "等待系统启动完成（最长8分钟）..."
    timeout 480 sh -c '
      while ! docker exec droidvisor-android-e2e adb shell getprop sys.boot_completed 2>/dev/null | grep -q "^1$"; do
        echo "  [$(date +%H:%M:%S)] Boot not completed yet..."
        sleep 10
      done
    ' && log_info "模拟器系统启动完成" || log_error "模拟器启动超时"

    log_step "模拟器就绪"
}

run_instrumentation_tests() {
    log_info "运行 Instrumentation 测试..."
    log_step "执行E2E测试套件(US001-US009)"

    cd "${PROJECT_ROOT}"

    # 检查设备是否可用
    local device_ready=$(docker exec droidvisor-android-e2e adb shell getprop sys.boot_completed 2>/dev/null || echo "")
    if [ "$device_ready" != "1" ]; then
        log_error "模拟器未就绪，跳过E2E测试"
        return 1
    fi

    # 安装APK
    log_step "安装应用APK"
    docker exec droidvisor-android-e2e \
        bash -c "adb uninstall com.droidvisor 2>/dev/null || true && \
                 adb uninstall com.droidvisor.test 2>/dev/null || true && \
                 adb install -r /workspace/app/build/outputs/apk/debug/app-debug.apk && \
                 adb install -r /workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" 2>&1 | tee -a "${BUILD_LOG}"

    # 授予必要权限
    log_step "授予应用权限"
    docker exec droidvisor-android-e2e \
        bash -c "adb shell pm grant com.droidvisor android.permission.READ_MEDIA_IMAGES 2>/dev/null || true && \
                 adb shell pm grant com.droidvisor android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true"

    # 推送 Debian VM 镜像（供应用的 QemuVmRuntime 使用）
    log_step "推送 Debian VM 镜像到设备"
    docker exec droidvisor-android-e2e \
        bash -c "adb shell mkdir -p /data/local/tmp/vm-images/debian && \
                 adb push /opt/vm-images/debian/disk.qcow2 /data/local/tmp/vm-images/debian/ && \
                 adb push /opt/vm-images/debian/vmlinuz /data/local/tmp/vm-images/debian/ && \
                 adb push /opt/vm-images/debian/initrd.img /data/local/tmp/vm-images/debian/" 2>&1 | tee -a "${BUILD_LOG}"

    # 启动Logcat捕获
    log_step "启动Logcat日志捕获"
    docker exec -d droidvisor-android-e2e \
        bash -c "adb logcat -c 2>/dev/null; adb logcat -v time > /workspace/e2e-logcat.log"

    # 运行E2E测试套件
    log_step "执行E2E Instrumentation 测试"
    echo "=========================================="
    echo "运行用户故事 E2E 测试套件"
    echo "测试: US001-US009 共 9 个用户故事"
    echo "验证项目自身功能: QemuVmRuntime, VM管理, 网络等"
    echo "=========================================="

    docker exec droidvisor-android-e2e \
        bash -c "adb shell am instrument -w \
                 -e package com.droidvisor.e2e \
                 -e debug false \
                 com.droidvisor.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee -a "${BUILD_LOG}"

    local test_exit_code=$?

    # 停止Logcat
    docker exec droidvisor-android-e2e bash -c "pkill -f 'adb logcat' || true" 2>&1 | tee -a "${BUILD_LOG}"

    echo "=========================================="
    echo "E2E 测试执行完毕"
    echo "=========================================="

    if [ $test_exit_code -eq 0 ]; then
        log_info "E2E测试执行成功"
        log_step "所有用户故事测试通过"
    else
        log_error "E2E测试执行失败 (退出码: $test_exit_code)"
        return 1
    fi
}

verify_app_qemu_logs() {
    log_info "验证应用 QemuVmRuntime 日志..."
    log_step "检查应用QEMU运行时日志"

    cd "${PROJECT_ROOT}"

    # 从Logcat中提取项目自身的QemuVmRuntime日志
    if docker exec droidvisor-android-e2e test -f /workspace/e2e-logcat.log 2>/dev/null; then
        # 先复制到宿主机
        docker cp droidvisor-android-e2e:/workspace/e2e-logcat.log "${PROJECT_ROOT}/e2e-logcat.log" 2>/dev/null || true

        if [ -f "${PROJECT_ROOT}/e2e-logcat.log" ]; then
            log_step "分析应用的QemuVmRuntime运行日志"

            echo "===== QemuVmRuntime 运行日志 ====="
            grep -E "QemuVmRuntime|QemuDiskManager|QemuProcessManager" "${PROJECT_ROOT}/e2e-logcat.log" | tail -30 || echo "(无QemuVmRuntime日志)"
            echo "===== QemuVmRuntime 日志结束 ====="

            log_step "分析E2E测试步骤日志"
            echo "===== E2E测试步骤日志 ====="
            grep -E "E2E-STEP|E2E-CLEANUP" "${PROJECT_ROOT}/e2e-logcat.log" | tail -30 || echo "(无E2E步骤日志)"
            echo "===== E2E测试步骤日志结束 ====="
        fi
    else
        log_info "Logcat日志不可用（模拟器可能未启动）"
    fi

    log_info "应用QemuVmRuntime日志验证完成"
}

verify_debian_vm_ssh() {
    log_info "最终验证: Debian VM 启动 + SSH 连接 + 基础命令执行..."
    log_step "Debian VM SSH 连接验证"

    cd "${PROJECT_ROOT}"

    echo "=========================================="
    echo "最终验证: 连接应用创建的 Debian VM 并执行命令"
    echo "=========================================="

    # 调用容器内的验证脚本（通过 adb forward 连接应用创建的 VM）
    docker exec droidvisor-android-e2e /opt/vm-images/debian/verify-debian-ssh.sh 2>&1 | tee -a "${BUILD_LOG}"
    local verify_exit_code=$?

    if [ $verify_exit_code -eq 0 ]; then
        log_info "Debian VM SSH 连接验证通过"
        log_step "Debian VM 可启动、可SSH连接、可执行命令"
        return 0
    else
        log_error "Debian VM SSH 连接验证失败 (退出码: $verify_exit_code)"
        return 1
    fi
}

collect_e2e_reports() {
    log_info "收集E2E测试报告..."

    cd "${PROJECT_ROOT}"

    # 从容器复制测试结果到宿主机
    docker cp droidvisor-android-e2e:/workspace/app/build/reports/androidTests/connected "${TEST_REPORT_DIR}/connected" 2>/dev/null || true
    docker cp droidvisor-android-e2e:/workspace/app/build/outputs/androidTest-results/connected "${TEST_RESULT_DIR}/connected" 2>/dev/null || true

    if [ -d "${TEST_REPORT_DIR}" ]; then
        local report_count=$(find "${TEST_REPORT_DIR}" -name "*.html" 2>/dev/null | wc -l)
        log_info "发现 ${report_count} 个HTML测试报告"
    fi

    if [ -f "${PROJECT_ROOT}/e2e-logcat.log" ]; then
        log_info "E2E Logcat日志已保存"
    fi

    log_step "E2E测试报告收集完成"
}

stop_e2e_container() {
    log_info "停止E2E测试容器..."

    cd "${PROJECT_ROOT}"

    # 停止模拟器
    docker exec droidvisor-android-e2e bash -c "adb emu kill || true" 2>/dev/null || true

    # 停止容器
    docker compose -f "${COMPOSE_FILE}" down 2>/dev/null || true

    log_info "E2E测试容器已停止"
}

main() {
    local skip_build=false
    local headful_mode=false

    for arg in "$@"; do
        case $arg in
            --skip-build)
                skip_build=true
                ;;
            --headful)
                headful_mode=true
                ;;
            --help)
                show_help
                exit 0
                ;;
        esac
    done

    echo "========================================" | tee -a "${BUILD_LOG}"
    echo "Droidvisor E2E Testing" | tee -a "${BUILD_LOG}"
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "${BUILD_LOG}"
    echo "架构: 单一容器（无外部提供者）" | tee -a "${BUILD_LOG}"
    echo "验证: 项目自身 QemuVmRuntime 功能" | tee -a "${BUILD_LOG}"
    echo "========================================" | tee -a "${BUILD_LOG}"

    check_prerequisites

    cleanup_e2e_environment

    if [ "$skip_build" = false ]; then
        build_e2e_docker_images
    else
        log_info "跳过Docker镜像构建"
    fi

    start_e2e_container

    # 阶段1: 验证项目编译
    if ! compile_apk; then
        log_error "APK编译失败，终止测试"
        stop_e2e_container
        exit 1
    fi

    # 阶段2: 启动模拟器
    start_emulator

    # 阶段3: 运行Instrumentation测试
    local test_failed=false
    if ! run_instrumentation_tests; then
        test_failed=true
    fi

    # 阶段4: 验证应用QEMU运行时日志
    verify_app_qemu_logs

    # 阶段5: Debian VM SSH 连接验证（最终验证）
    if ! verify_debian_vm_ssh; then
        log_error "Debian VM SSH 连接验证失败"
        test_failed=true
    fi

    # 收集测试报告
    collect_e2e_reports

    # 停止容器
    stop_e2e_container

    echo "========================================" | tee -a "${BUILD_LOG}"
    echo "E2E测试完成时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "${BUILD_LOG}"
    echo "========================================" | tee -a "${BUILD_LOG}"

    if [ "$test_failed" = true ]; then
        log_error "E2E测试执行失败"
        exit 1
    fi

    log_info "所有E2E测试执行成功"
    log_step "端到端测试验证完成"
    exit 0
}

main "$@"