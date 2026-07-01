#!/bin/bash
#
# Droidvisor GitHub Actions Workflow Validation Script
# 功能：验证E2E测试workflow配置的完整性和正确性
# 架构：单一容器（无外部提供者），最终验证为 Debian VM SSH 连接
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
echo "架构: 单一容器 + Debian VM SSH 最终验证"
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

# 2. 验证E2E workflow核心配置
log_info "验证E2E workflow核心配置..."

e2e_workflow_checks=(
    "name: E2E Tests"
    "docker compose -f docker-compose-e2e.yml"
    "droidvisor-android-e2e"
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

# 验证无外部提供者
if grep -q "qemu-provider" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
    log_fail "Workflow包含外部 qemu-provider 引用"
else
    log_pass "Workflow无外部 qemu-provider 引用"
fi

if grep -q "avd-setup" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
    log_fail "Workflow包含外部 avd-setup 引用"
else
    log_pass "Workflow无外部 avd-setup 引用"
fi

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

# 4. 验证核心验证步骤
log_info "验证核心验证步骤配置..."

core_verification=(
    "Compile APK in Container"
    "Start Android Emulator"
    "Install APKs on Emulator"
    "Run E2E Tests (US001-US009)"
    "Verify App QemuVmRuntime Logs"
    "Verify Debian VM - SSH into App's Created VM"
    "Check Debian VM Verification Result"
)

for step in "${core_verification[@]}"; do
    if grep -q "$step" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "核心验证步骤存在: $step"
    else
        log_fail "核心验证步骤缺失: $step"
    fi
done

# 5. 验证Docker Compose命令
log_info "验证Docker Compose命令配置..."

compose_commands=(
    "docker compose -f docker-compose-e2e.yml build"
    "docker compose -f docker-compose-e2e.yml up -d"
    "docker compose -f docker-compose-e2e.yml down"
    "docker exec droidvisor-android-e2e"
)

for cmd in "${compose_commands[@]}"; do
    if grep -q "$cmd" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "Docker命令配置包含: $cmd"
    else
        log_fail "Docker命令配置缺失: $cmd"
    fi
done

# 验证无外部容器exec命令
if grep -q "docker exec droidvisor-qemu-provider\|docker exec droidvisor-avd-setup" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
    log_fail "Workflow包含外部容器命令"
else
    log_pass "Workflow无外部容器命令"
fi

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

# 7. 验证 Debian VM SSH 验证步骤
log_info "验证 Debian VM SSH 连接验证配置..."

debian_ssh_checks=(
    "Verify Debian VM - SSH into App's Created VM"
    "verify-debian-ssh.sh"
    "E2E_SSH_VERIFY_OK"
    "Check Debian VM Verification Result"
    "debian-vm-ssh-verification"
)

for check in "${debian_ssh_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "Debian SSH验证包含: $check"
    else
        log_fail "Debian SSH验证缺失: $check"
    fi
done

# 8. 验证 QemuVmRuntime 日志验证
log_info "验证 QemuVmRuntime 日志配置..."

vm_log_checks=(
    "Verify App QemuVmRuntime Logs"
    "QemuVmRuntime"
    "QemuDiskManager"
    "QemuProcessManager"
    "VmManager"
)

for check in "${vm_log_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "QemuVmRuntime日志包含: $check"
    else
        log_fail "QemuVmRuntime日志缺失: $check"
    fi
done

# 9. 验证报告上传配置
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

# 10. 验证清理步骤
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

# 15. 验证 Dockerfile 中的 Debian 镜像准备
log_info "验证 Dockerfile 中的 Debian 镜像准备..."

dockerfile_debian_checks=(
    "debian-12-genericcloud-arm64"
    "e2e_ssh_key"
    "virt-customize"
    "virt-get-kernel"
    "verify-debian-ssh.sh"
)

for check in "${dockerfile_debian_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/Dockerfile.e2e"; then
        log_pass "Dockerfile包含: $check"
    else
        log_fail "Dockerfile缺失: $check"
    fi
done

# 16. 验证 verify-debian-ssh.sh 脚本内容
log_info "验证 verify-debian-ssh.sh 脚本内容..."

ssh_script_checks=(
    "adb forward"
    "ssh -o StrictHostKeyChecking=no"
    "whoami"
    "uname -a"
    "cat /etc/os-release"
    "hostname"
    "df -h"
    "E2E_SSH_VERIFY_OK"
)

for check in "${ssh_script_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/Dockerfile.e2e"; then
        log_pass "SSH验证脚本包含: $check"
    else
        log_fail "SSH验证脚本缺失: $check"
    fi
done

# 17. 验证相关配置文件
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
pass_count=$(grep -c "^\[PASS\]" "${VALIDATION_LOG}" 2>/dev/null)
pass_count=${pass_count:-0}
fail_count=$(grep -c "^\[FAIL\]" "${VALIDATION_LOG}" 2>/dev/null)
fail_count=${fail_count:-0}

echo "验证结果: $pass_count 项通过, $fail_count 项失败"

if [ "$fail_count" -gt 0 ]; then
    echo "⚠️  Workflow配置验证发现问题,请检查失败项"
    exit 1
else
    echo "✅ 所有workflow配置验证通过,可以提交到GitHub运行测试"
    exit 0
fi