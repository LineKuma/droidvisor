#!/bin/bash
#
# Droidvisor GitHub Actions Workflow Validation Script
# 功能：验证E2E测试workflow配置的完整性和正确性
# 使用方法：./scripts/validate-workflow-config.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
WORKFLOW_DIR="${PROJECT_ROOT}/.github/workflows"
VALIDATION_LOG="${PROJECT_ROOT}/workflow-config-validation.log"

log_info() {
    local msg="$1"
    echo "[INFO] $msg"
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $msg" >> "${VALIDATION_LOG}"
}

log_pass() {
    local msg="$1"
    echo "[PASS] ✓ $msg"
    echo "[PASS] $(date '+%Y-%m-%d %H:%M:%S') - $msg" >> "${VALIDATION_LOG}"
}

log_fail() {
    local msg="$1"
    echo "[FAIL] ✗ $msg"
    echo "[FAIL] $(date '+%Y-%m-%d %H:%M:%S') - $msg" >> "${VALIDATION_LOG}"
}

echo "========================================"
echo "GitHub Actions Workflow Configuration Validation"
echo "验证时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

# 1. 验证workflow文件存在
log_info "检查必需workflow文件..."

workflow_files=(
    ".github/workflows/e2e.yml"
    ".github/workflows/ci.yml"
    ".github/workflows/docker-integration.yml"
)

for file in "${workflow_files[@]}"; do
    if [ -f "${PROJECT_ROOT}/$file" ]; then
        log_pass "Workflow文件存在: $file"
    else
        log_fail "Workflow文件缺失: $file"
    fi
done

# 2. 验证E2E workflow配置
log_info "验证E2E workflow配置..."

e2e_workflow_checks=(
    "name: E2E Tests"
    "docker compose -f docker-compose-e2e.yml"
    "droidvisor-qemu-provider"
    "droidvisor-avd-setup"
    "droidvisor-android-e2e"
    "qemu-system-x86_64"
    "assembleDebug assembleDebugAndroidTest"
    "adb shell am instrument"
    "upload-artifact@v4"
    "mikepenz/action-junit-report@v4"
)

for check in "${e2e_workflow_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "E2E workflow包含: $check"
    else
        log_fail "E2E workflow缺失: $check"
    fi
done

# 3. 验证workflow触发条件
log_info "验证workflow触发条件..."

trigger_checks=(
    "push:"
    "pull_request:"
    "workflow_dispatch:"
    "branches: \[main, master, develop"
)

for check in "${trigger_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "触发条件配置包含: $check"
    else
        log_fail "触发条件配置缺失: $check"
    fi
done

# 4. 验证容器启动顺序
log_info "验证容器启动顺序配置..."

container_sequence=(
    "Start QEMU Provider Container"
    "Start AVD Setup Container"
    "Start Android E2E Test Container"
)

sequence_found=true
for i in "${!container_sequence[@]}"; do
    step="${container_sequence[$i]}"
    if grep -n "$step" "${PROJECT_ROOT}/.github/workflows/e2e.yml" > /dev/null; then
        log_pass "容器启动步骤存在: $step"
    else
        log_fail "容器启动步骤缺失: $step"
        sequence_found=false
    fi
done

# 5. 验证Docker Compose命令
log_info "验证Docker Compose命令配置..."

compose_commands=(
    "docker compose -f docker-compose-e2e.yml build"
    "docker compose -f docker-compose-e2e.yml up -d"
    "docker compose -f docker-compose-e2e.yml down"
    "docker exec droidvisor-android-e2e"
    "docker exec droidvisor-qemu-provider"
    "docker exec droidvisor-avd-setup"
)

for cmd in "${compose_commands[@]}"; do
    if grep -q "$cmd" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "Docker命令配置包含: $cmd"
    else
        log_fail "Docker命令配置缺失: $cmd"
    fi
done

# 6. 验证测试执行配置
log_info "验证测试执行配置..."

test_execution=(
    "Run E2E Tests (US001-US009)"
    "-e package com.droidvisor.e2e"
    "-e debug false"
    "com.droidvisor.test/androidx.test.runner.AndroidJUnitRunner"
)

for check in "${test_execution[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "测试执行配置包含: $check"
    else
        log_fail "测试执行配置缺失: $check"
    fi
done

# 7. 验证虚拟机验证步骤
log_info "验证虚拟机运行验证配置..."

vm_verification=(
    "Verify VM Runtime"
    "VM started successfully"
    "QEMU VM started"
    "Command executed"
    "grep -E 'QEMU|VmManager|VmRuntime|E2E-STEP'"
)

for check in "${vm_verification[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "VM验证配置包含: $check"
    else
        log_fail "VM验证配置缺失: $check"
    fi
done

# 8. 验证报告上传配置
log_info "验证测试报告上传配置..."

report_config=(
    "Collect Test Results"
    "Upload E2E Test Results"
    "Publish E2E Test Report"
    "app/build/reports/androidTests/connected/"
    "app/build/outputs/androidTest-results/connected/"
    "e2e-logcat.log"
    "retention-days: 14"
)

for check in "${report_config[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "报告配置包含: $check"
    else
        log_fail "报告配置缺失: $check"
    fi
done

# 9. 验证清理步骤
log_info "验证环境清理配置..."

cleanup_checks=(
    "Cleanup Docker Environment"
    "docker compose -f docker-compose-e2e.yml down -v"
    "docker system prune -f"
)

for check in "${cleanup_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "清理配置包含: $check"
    else
        log_fail "清理配置缺失: $check"
    fi
done

# 10. 验证Docker集成测试job
log_info "验证Docker集成测试job..."

docker_integration=(
    "docker-integration:"
    "name: Docker Integration Test"
    "if: github.event_name == 'workflow_dispatch'"
    "Build and Test Docker Compose Environment"
)

for check in "${docker_integration[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "Docker集成job包含: $check"
    else
        log_fail "Docker集成job缺失: $check"
    fi
done

# 11. 验证超时和并发配置
log_info "验证超时和并发配置..."

timeout_concurrency=(
    "timeout-minutes: 120"
    "concurrency:"
    "group: e2e-"
    "cancel-in-progress: true"
)

for check in "${timeout_concurrency[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "超时并发配置包含: $check"
    else
        log_fail "超时并发配置缺失: $check"
    fi
done

# 12. 验证permissions配置
log_info "验证permissions配置..."

permissions_checks=(
    "permissions:"
    "contents: read"
    "checks: write"
)

for check in "${permissions_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "Permissions配置包含: $check"
    else
        log_fail "Permissions配置缺失: $check"
    fi
done

# 13. 验证KVM支持配置
log_info "验证KVM支持配置..."

kvm_checks=(
    "Enable KVM support"
    "KERNEL==\"kvm\""
    "udevadm control --reload-rules"
    "udevadm trigger --name-match=kvm"
)

for check in "${kvm_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "KVM配置包含: $check"
    else
        log_fail "KVM配置缺失: $check"
    fi
done

# 14. 验证缓存配置
log_info "验证缓存配置..."

cache_checks=(
    "Cache Docker images"
    "actions/cache@v4"
    "docker-e2e-"
)

for check in "${cache_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "缓存配置包含: $check"
    else
        log_fail "缓存配置缺失: $check"
    fi
done

# 15. 验证workflow文档
log_info "验证workflow说明文档..."

workflow_docs=(
    "GITHUB_ACTIONS_E2E_WORKFLOW.md"
)

for doc in "${workflow_docs[@]}"; do
    if [ -f "${PROJECT_ROOT}/$doc" ]; then
        log_pass "文档文件存在: $doc"
        
        # 验证文档关键章节
        doc_sections=(
            "## Workflow概述"
            "## 测试流程"
            "## Docker环境构建"
            "## 启动容器编排组"
            "## 运行E2E测试"
            "## 验证虚拟机运行"
            "## 测试报告查看"
        )
        
        for section in "${doc_sections[@]}"; do
            if grep -q "$section" "${PROJECT_ROOT}/$doc"; then
                log_pass "文档包含章节: $section"
            else
                log_fail "文档缺失章节: $section"
            fi
        done
    else
        log_fail "文档文件缺失: $doc"
    fi
done

# 16. 验证相关配置文件
log_info "验证相关配置文件..."

config_files=(
    "docker-compose-e2e.yml"
    "Dockerfile.e2e"
    "scripts/run-e2e-tests.sh"
)

for file in "${config_files[@]}"; do
    if [ -f "${PROJECT_ROOT}/$file" ]; then
        log_pass "配置文件存在: $file"
    else
        log_fail "配置文件缺失: $file"
    fi
done

# 总结
echo "========================================"
echo "Workflow配置验证完成"
echo "验证日志: ${VALIDATION_LOG}"
echo "========================================"

# 统计通过和失败数量
pass_count=$(grep -c "^\[PASS\]" "${VALIDATION_LOG}" || echo 0)
fail_count=$(grep -c "^\[FAIL\]" "${VALIDATION_LOG}" || echo 0)

echo "验证结果: $pass_count 项通过, $fail_count 项失败"

if [ "$fail_count" -gt 0 ]; then
    echo "⚠️  Workflow配置验证发现问题,请检查失败项"
    exit 1
else
    echo "✅ 所有workflow配置验证通过,可以提交到GitHub运行测试"
    exit 0
fi