#!/bin/bash
#
# Droidvisor Docker Testing Script
# 功能：构建Docker镜像、启动测试容器、运行单元测试和集成测试、收集测试报告、清理测试环境
# 使用方法：./scripts/test-docker.sh [options]
# 选项：--unit-only 仅运行单元测试 | --integration-only 仅运行集成测试 | --help 显示帮助
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
BUILD_LOG="${PROJECT_ROOT}/build.log"
TEST_REPORT_DIR="${PROJECT_ROOT}/app/build/reports/tests"
TEST_RESULT_DIR="${PROJECT_ROOT}/app/build/test-results"

UNIT_TEST_PATTERN="**/test/**/*Test.class"
INTEGRATION_TEST_PATTERN="**/test/**/*IntegrationTest.class"

show_help() {
    cat << EOF
Droidvisor Docker Testing Script

用法: $(basename "$0") [选项]

选项:
  --unit-only         仅运行单元测试
  --integration-only  仅运行集成测试
  --help              显示此帮助信息

示例:
  $(basename "$0")              # 运行所有测试
  $(basename "$0") --unit-only   # 仅运行单元测试
  $(basename "$0") --integration-only  # 仅运行集成测试

环境要求:
  - Docker 和 Docker Compose 已安装
  - docker-compose.yml 存在于项目根目录

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

check_prerequisites() {
    log_info "检查先决条件..."

    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装或不在 PATH 中"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose 未安装或不在 PATH 中"
        exit 1
    fi

    if [ ! -f "${COMPOSE_FILE}" ]; then
        log_error "docker-compose.yml 不存在于: ${COMPOSE_FILE}"
        exit 1
    fi

    log_info "先决条件检查通过"
}

cleanup_test_environment() {
    log_info "清理旧测试环境..."

    cd "${PROJECT_ROOT}"

    docker-compose -f "${COMPOSE_FILE}" down -v --remove-orphans 2>/dev/null || true

    docker stop droidvisor-android-test 2>/dev/null || true
    docker rm droidvisor-android-test 2>/dev/null || true

    docker stop droidvisor-dind 2>/dev/null || true
    docker rm droidvisor-dind 2>/dev/null || true

    docker image prune -f 2>/dev/null || true

    log_info "旧测试环境清理完成"
}

build_docker_image() {
    log_info "构建 Docker 测试镜像..."

    cd "${PROJECT_ROOT}"

    docker build -t droidvisor-test:latest \
        --build-arg BUILDKIT_INLINE_CACHE=1 \
        . 2>&1 | tee -a "${BUILD_LOG}"

    if [ $? -eq 0 ]; then
        log_info "Docker 镜像构建成功"
    else
        log_error "Docker 镜像构建失败"
        exit 1
    fi
}

start_test_containers() {
    log_info "启动测试容器..."

    cd "${PROJECT_ROOT}"

    docker-compose -f "${COMPOSE_FILE}" up -d

    log_info "等待容器启动..."

    local max_wait=120
    local wait_count=0

    while [ $wait_count -lt $max_wait ]; do
        local android_test_status=$(docker inspect -f '{{.State.Running}}' droidvisor-android-test 2>/dev/null || echo "false")
        local dind_status=$(docker inspect -f '{{.State.Running}}' droidvisor-dind 2>/dev/null || echo "false")

        if [ "$android_test_status" = "true" ] && [ "$dind_status" = "true" ]; then
            log_info "所有容器已启动"
            return 0
        fi

        sleep 2
        wait_count=$((wait_count + 2))
    done

    log_error "容器启动超时"
    docker-compose -f "${COMPOSE_FILE}" logs
    exit 1
}

wait_for_dind_ready() {
    log_info "等待 Docker-in-Docker 服务就绪..."

    local max_wait=60
    local wait_count=0

    while [ $wait_count -lt $max_wait ]; do
        if docker exec droidvisor-dind docker info &> /dev/null; then
            log_info "Docker-in-Docker 服务已就绪"
            return 0
        fi

        sleep 2
        wait_count=$((wait_count + 2))
    done

    log_error "Docker-in-Docker 服务启动超时"
    exit 1
}

run_unit_tests() {
    log_info "运行单元测试..."

    cd "${PROJECT_ROOT}"

    docker exec droidvisor-android-test \
        gradlew testDebugUnitTest \
        --no-daemon \
        --info 2>&1 | tee -a "${BUILD_LOG}"

    local exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
        log_info "单元测试执行成功"
    else
        log_error "单元测试执行失败 (退出码: ${exit_code})"
        return 1
    fi
}

run_integration_tests() {
    log_info "运行集成测试..."

    wait_for_dind_ready

    cd "${PROJECT_ROOT}"

    docker exec droidvisor-android-test \
        gradlew testDebugUnitTest \
        --tests "*IntegrationTest" \
        --no-daemon \
        --info 2>&1 | tee -a "${BUILD_LOG}"

    local exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
        log_info "集成测试执行成功"
    else
        log_error "集成测试执行失败 (退出码: ${exit_code})"
        return 1
    fi
}

collect_test_reports() {
    log_info "收集测试报告..."

    cd "${PROJECT_ROOT}"

    if [ -d "${TEST_REPORT_DIR}" ]; then
        log_info "测试报告目录: ${TEST_REPORT_DIR}"

        local report_count=$(find "${TEST_REPORT_DIR}" -name "*.html" 2>/dev/null | wc -l)
        log_info "发现 ${report_count} 个 HTML 测试报告"

        local test_results_xml="${TEST_RESULT_DIR}/test-results/*.xml"
        if ls ${test_results_xml} 2>/dev/null | head -1 > /dev/null; then
            log_info "发现 XML 测试结果文件"
        fi
    else
        log_info "未找到测试报告目录 (可能测试未执行)"
    fi

    log_info "测试报告收集完成"
}

stop_test_containers() {
    log_info "停止测试容器..."

    cd "${PROJECT_ROOT}"

    docker-compose -f "${COMPOSE_FILE}" down 2>/dev/null || true

    log_info "测试容器已停止"
}

main() {
    local run_unit=true
    local run_integration=true

    for arg in "$@"; do
        case $arg in
            --unit-only)
                run_integration=false
                ;;
            --integration-only)
                run_unit=false
                ;;
            --help)
                show_help
                exit 0
                ;;
        esac
    done

    echo "========================================" | tee -a "${BUILD_LOG}"
    echo "Droidvisor Docker Testing" | tee -a "${BUILD_LOG}"
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "${BUILD_LOG}"
    echo "========================================" | tee -a "${BUILD_LOG}"

    check_prerequisites

    cleanup_test_environment

    build_docker_image

    start_test_containers

    local test_failed=false

    if [ "$run_unit" = true ]; then
        if ! run_unit_tests; then
            test_failed=true
        fi
    fi

    if [ "$run_integration" = true ]; then
        if ! run_integration_tests; then
            test_failed=true
        fi
    fi

    collect_test_reports

    stop_test_containers

    echo "========================================" | tee -a "${BUILD_LOG}"
    echo "测试完成时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "${BUILD_LOG}"
    echo "========================================" | tee -a "${BUILD_LOG}"

    if [ "$test_failed" = true ]; then
        log_error "测试执行失败"
        exit 1
    fi

    log_info "所有测试执行成功"
    exit 0
}

main "$@"