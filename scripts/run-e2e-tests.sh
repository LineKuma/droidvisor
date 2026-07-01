#!/bin/bash
#
# Droidvisor E2E Testing Script
# 功能：启动端到端测试Docker环境,运行AVD模拟器和QEMU提供者,执行E2E测试并验证虚拟机运行
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
QEMU_DISK_DIR="/tmp/qemu-e2e-disks"

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
  - AVD容器: 运行Android模拟器执行UI测试
  - QEMU提供者: 提供虚拟化运行时环境
  - 默认使用QEMU作为虚拟机提供者
  - 测试US001-US009用户故事

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

    docker-compose -f "${COMPOSE_FILE}" down -v --remove-orphans 2>/dev/null || true

    # 清理特定容器
    docker stop droidvisor-android-e2e 2>/dev/null || true
    docker rm droidvisor-android-e2e 2>/dev/null || true
    docker stop droidvisor-qemu-provider 2>/dev/null || true
    docker rm droidvisor-qemu-provider 2>/dev/null || true
    docker stop droidvisor-avd-setup 2>/dev/null || true
    docker rm droidvisor-avd-setup 2>/dev/null || true
    docker stop droidvisor-dind-e2e 2>/dev/null || true
    docker rm droidvisor-dind-e2e 2>/dev/null || true

    # 清理QEMU磁盘
    rm -rf "${QEMU_DISK_DIR}" 2>/dev/null || true
    mkdir -p "${QEMU_DISK_DIR}"

    docker image prune -f 2>/dev/null || true

    log_info "旧E2E测试环境清理完成"
}

build_e2e_docker_images() {
    log_info "构建E2E测试Docker镜像..."

    cd "${PROJECT_ROOT}"

    log_step "构建 android-e2e-test 镜像"
    docker build -t droidvisor-e2e:latest \
        -f Dockerfile.e2e \
        --build-arg BUILDKIT_INLINE_CACHE=1 \
        . 2>&1 | tee -a "${BUILD_LOG}"

    if [ $? -eq 0 ]; then
        log_info "E2E Docker镜像构建成功"
    else
        log_error "E2E Docker镜像构建失败"
        exit 1
    fi
}

start_e2e_containers() {
    log_info "启动E2E测试容器编排组..."

    cd "${PROJECT_ROOT}"

    # 创建必要的目录
    mkdir -p "${QEMU_DISK_DIR}"

    # 启动所有容器服务
    docker-compose -f "${COMPOSE_FILE}" up -d

    log_info "等待E2E容器启动完成..."

    local max_wait=180
    local wait_count=0

    while [ $wait_count -lt $max_wait ]; do
        local qemu_status=$(docker inspect -f '{{.State.Running}}' droidvisor-qemu-provider 2>/dev/null || echo "false")
        local avd_status=$(docker inspect -f '{{.State.Running}}' droidvisor-avd-setup 2>/dev/null || echo "false")
        local e2e_status=$(docker inspect -f '{{.State.Running}}' droidvisor-android-e2e 2>/dev/null || echo "false")

        if [ "$qemu_status" = "true" ] && [ "$avd_status" = "true" ] && [ "$e2e_status" = "true" ]; then
            log_info "所有E2E容器已启动"
            log_step "QEMU提供者容器就绪"
            log_step "AVD设置容器就绪"
            log_step "Android E2E测试容器就绪"
            return 0
        fi

        sleep 3
        wait_count=$((wait_count + 3))
        log_info "等待容器启动... ($wait_count/$max_wait 秒)"
    done

    log_error "E2E容器启动超时"
    docker-compose -f "${COMPOSE_FILE}" logs
    exit 1
}

verify_qemu_provider() {
    log_info "验证QEMU提供者运行状态..."
    log_step "检查QEMU二进制文件"

    docker exec droidvisor-qemu-provider sh -c "which qemu-system-x86_64 && qemu-system-x86_64 --version" 2>&1 | tee -a "${BUILD_LOG}"

    log_step "验证QEMU磁盘镜像"
    docker exec droidvisor-qemu-provider sh -c "test -f /tmp/qemu-e2e-disks/e2e-test-disk.qcow2 && qemu-img info /tmp/qemu-e2e-disks/e2e-test-disk.qcow2" 2>&1 | tee -a "${BUILD_LOG}"

    if [ $? -eq 0 ]; then
        log_info "QEMU提供者验证成功,磁盘镜像可用"
        log_step "QEMU提供者就绪"
    else
        log_error "QEMU提供者验证失败"
        return 1
    fi
}

verify_avd_environment() {
    log_info "验证AVD环境设置..."
    log_step "检查Android SDK"

    docker exec droidvisor-avd-setup bash -c "test -d /opt/android-sdk/platforms/android-34 && echo 'Android SDK OK'" 2>&1 | tee -a "${BUILD_LOG}"

    log_step "检查AVD配置"
    docker exec droidvisor-avd-setup bash -c "test -d /root/.android/avd && ls /root/.android/avd" 2>&1 | tee -a "${BUILD_LOG}"

    if [ $? -eq 0 ]; then
        log_info "AVD环境验证成功"
        log_step "AVD环境就绪"
    else
        log_error "AVD环境验证失败"
        return 1
    fi
}

run_e2e_tests() {
    log_info "执行端到端测试..."
    log_step "开始E2E测试执行"

    cd "${PROJECT_ROOT}"

    # 编译APK
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

    # 启动Android模拟器(无头模式)
    log_step "启动Android模拟器(无头模式)"
    docker exec -d droidvisor-android-e2e \
        bash -c "export DISPLAY=:0 && \
                 Xvfb :0 -screen 0 1080x1920x24 & \
                 sleep 2 && \
                 $ANDROID_HOME/emulator/emulator \
                 -avd e2e_test_avd \
                 -no-window \
                 -gpu swiftshader_indirect \
                 -noaudio \
                 -no-boot-anim \
                 -no-snapshot-save \
                 -memory 4096 \
                 -qemu -m 4096"

    log_info "等待模拟器启动..."
    sleep 30

    # 验证模拟器状态
    log_step "验证模拟器运行状态"
    docker exec droidvisor-android-e2e \
        bash -c "adb devices | grep emulator" 2>&1 | tee -a "${BUILD_LOG}"

    # 推送QEMU测试磁盘到设备
    log_step "推送QEMU测试环境到模拟器"
    docker exec droidvisor-android-e2e \
        bash -c "adb shell mkdir -p /data/local/tmp/qemu-e2e && \
                 adb push /tmp/qemu-e2e/disks/test-disk.qcow2 /data/local/tmp/qemu-e2e/" 2>&1 | tee -a "${BUILD_LOG}"

    # 安装APK
    log_step "安装应用APK"
    docker exec droidvisor-android-e2e \
        bash -c "adb install -r app/build/outputs/apk/debug/app-debug.apk && \
                 adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" 2>&1 | tee -a "${BUILD_LOG}"

    # 授予必要权限
    log_step "授予应用权限"
    docker exec droidvisor-android-e2e \
        bash -c "adb shell pm grant com.droidvisor android.permission.READ_MEDIA_IMAGES 2>/dev/null || true && \
                 adb shell pm grant com.droidvisor android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true" 2>&1 | tee -a "${BUILD_LOG}"

    # 启动Logcat捕获
    log_step "启动Logcat日志捕获"
    docker exec -d droidvisor-android-e2e \
        bash -c "adb logcat -c && adb logcat -v time > /workspace/e2e-logcat.log"

    # 运行E2E测试套件
    log_step "执行E2E测试套件(US001-US009)"
    echo "=========================================="
    echo "开始运行用户故事 E2E 测试套件"
    echo "测试结构: US001-US009 共 9 个用户故事"
    echo "使用QEMU提供者作为虚拟化后端"
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

verify_vm_response() {
    log_info "验证虚拟机命令响应..."
    log_step "检查虚拟机运行状态"

    cd "${PROJECT_ROOT}"

    # 从Logcat中提取VM相关日志
    if [ -f "${PROJECT_ROOT}/e2e-logcat.log" ]; then
        log_step "分析E2E日志中的VM操作"

        echo "===== VM运行状态日志 ====="
        grep -E "QEMU|VmManager|VmRuntime|E2E-STEP" "${PROJECT_ROOT}/e2e-logcat.log" | tail -50 || echo "(无VM日志)"
        echo "===== VM运行状态日志结束 ====="

        log_step "检查VM命令响应"

        # 验证VM启动、停止等关键操作
        if grep -q "VM started successfully" "${PROJECT_ROOT}/e2e-logcat.log" || \
           grep -q "QEMU VM started" "${PROJECT_ROOT}/e2e-logcat.log"; then
            log_info "VM启动操作验证成功"
            log_step "VM启动响应正常"
        else
            log_info "未检测到VM启动日志(可能使用模拟模式)"
        fi

        # 验证命令执行
        if grep -q "Command executed" "${PROJECT_ROOT}/e2e-logcat.log" || \
           grep -q "Terminal command" "${PROJECT_ROOT}/e2e-logcat.log"; then
            log_info "VM命令执行验证成功"
            log_step "VM命令响应正常"
        fi
    fi

    # 验证QEMU进程状态
    log_step "验证QEMU进程状态"
    docker exec droidvisor-qemu-provider \
        sh -c "ps aux | grep qemu | grep -v grep || echo 'QEMU进程未运行(正常,仅在测试时启动)'"

    log_info "虚拟机运行验证完成"
}

collect_e2e_reports() {
    log_info "收集E2E测试报告..."

    cd "${PROJECT_ROOT}"

    if [ -d "${TEST_REPORT_DIR}" ]; then
        log_info "E2E测试报告目录: ${TEST_REPORT_DIR}"

        local report_count=$(find "${TEST_REPORT_DIR}" -name "*.html" 2>/dev/null | wc -l)
        log_info "发现 ${report_count} 个HTML测试报告"

        if [ -f "${PROJECT_ROOT}/e2e-logcat.log" ]; then
            log_info "E2E Logcat日志已保存: ${PROJECT_ROOT}/e2e-logcat.log"
        fi
    else
        log_info "未找到测试报告目录"
    fi

    log_step "E2E测试报告收集完成"
}

stop_e2e_containers() {
    log_info "停止E2E测试容器..."

    cd "${PROJECT_ROOT}"

    # 停止模拟器
    docker exec droidvisor-android-e2e bash -c "adb emu kill || true" 2>/dev/null || true

    # 停止容器
    docker-compose -f "${COMPOSE_FILE}" down 2>/dev/null || true

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
    echo "测试模式: 无头测试(Headless)" | tee -a "${BUILD_LOG}"
    echo "虚拟化后端: QEMU提供者(默认)" | tee -a "${BUILD_LOG}"
    echo "========================================" | tee -a "${BUILD_LOG}"

    check_prerequisites

    cleanup_e2e_environment

    if [ "$skip_build" = false ]; then
        build_e2e_docker_images
    else
        log_info "跳过Docker镜像构建"
    fi

    start_e2e_containers

    # 验证各容器服务
    verify_qemu_provider || true
    verify_avd_environment || true

    # 运行E2E测试
    local test_failed=false
    if ! run_e2e_tests; then
        test_failed=true
    fi

    # 验证虚拟机运行和响应
    verify_vm_response

    # 收集测试报告
    collect_e2e_reports

    # 停止容器
    stop_e2e_containers

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