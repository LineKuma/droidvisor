#!/bin/bash
#
# Droidvisor E2E Configuration Validation Script
# 功能：验证E2E测试配置文件的完整性和正确性
# 使用方法：./scripts/validate-e2e-config.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VALIDATION_LOG="${PROJECT_ROOT}/e2e-config-validation.log"

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
echo "Droidvisor E2E Configuration Validation"
echo "验证时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

# 1. 验证必需文件存在
log_info "检查必需配置文件..."

required_files=(
    "docker-compose-e2e.yml"
    "Dockerfile.e2e"
    "scripts/run-e2e-tests.sh"
    "E2E_TESTING_README.md"
    "app/src/androidTest/java/com/droidvisor/e2e/E2ETestBase.kt"
)

all_files_found=true
for file in "${required_files[@]}"; do
    if [ -f "${PROJECT_ROOT}/$file" ]; then
        log_pass "文件存在: $file"
    else
        log_fail "文件缺失: $file"
        all_files_found=false
    fi
done

# 2. 验证Docker Compose配置结构
log_info "验证Docker Compose配置结构..."

if command -v python3 &> /dev/null; then
    # 使用Python解析YAML(如果可用)
    python3 -c "
import yaml
try:
    with open('${PROJECT_ROOT}/docker-compose-e2e.yml', 'r') as f:
        config = yaml.safe_load(f)
    
    services = config.get('services', {})
    required_services = ['android-e2e-test', 'qemu-provider', 'avd-setup']
    
    for service in required_services:
        if service in services:
            print(f'[PASS] 服务定义存在: {service}')
        else:
            print(f'[FAIL] 服务定义缺失: {service}')
    
    networks = config.get('networks', {})
    if 'droidvisor-e2e-network' in networks:
        print('[PASS] 网络配置存在: droidvisor-e2e-network')
    else:
        print('[FAIL] 网络配置缺失')
    
    volumes = config.get('volumes', {})
    required_volumes = ['kvm-volume', 'qemu-sockets', 'avd-cache']
    for vol in required_volumes:
        if vol in volumes:
            print(f'[PASS] Volume定义存在: {vol}')
        else:
            print(f'[FAIL] Volume定义缺失: {vol}')
    
    print('[PASS] Docker Compose配置结构验证成功')
except Exception as e:
    print(f'[FAIL] Docker Compose配置解析失败: {e}')
" 2>&1 | tee -a "${VALIDATION_LOG}"
else
    # 如果Python不可用,使用基础验证
    if grep -q "android-e2e-test:" "${PROJECT_ROOT}/docker-compose-e2e.yml" &&
       grep -q "qemu-provider:" "${PROJECT_ROOT}/docker-compose-e2e.yml" &&
       grep -q "avd-setup:" "${PROJECT_ROOT}/docker-compose-e2e.yml"; then
        log_pass "Docker Compose服务定义存在"
    else
        log_fail "Docker Compose服务定义缺失"
    fi
fi

# 3. 验证QEMU配置关键参数
log_info "验证QEMU提供者配置..."

qemu_checks=(
    "qemu-system-x86_64"
    "qemu-img create"
    "/tmp/qemu-e2e-disks/e2e-test-disk.qcow2"
    "privileged: true"
)

for check in "${qemu_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/docker-compose-e2e.yml"; then
        log_pass "QEMU配置包含: $check"
    else
        log_fail "QEMU配置缺失: $check"
    fi
done

# 4. 验证AVD配置
log_info "验证AVD环境配置..."

avd_checks=(
    "ANDROID_HOME"
    "system-images;android-34"
    "e2e_test_avd"
    "emulator"
)

for check in "${avd_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/Dockerfile.e2e"; then
        log_pass "AVD配置包含: $check"
    else
        log_fail "AVD配置缺失: $check"
    fi
done

# 5. 验证E2E测试脚本
log_info "验证E2E测试脚本..."

if [ -x "${PROJECT_ROOT}/scripts/run-e2e-tests.sh" ]; then
    log_pass "测试脚本可执行权限正确"
else
    log_fail "测试脚本缺少可执行权限"
fi

script_checks=(
    "docker compose"
    "run_e2e_tests"
    "verify_qemu_provider"
    "verify_vm_response"
    "collect_e2e_reports"
)

for check in "${script_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/scripts/run-e2e-tests.sh"; then
        log_pass "测试脚本包含函数: $check"
    else
        log_fail "测试脚本缺失函数: $check"
    fi
done

# 6. 验证E2E测试文件
log_info "验证E2E测试文件..."

e2e_tests=(
    "US001_FirstLaunchAndNavigation.kt"
    "US002_VmManagement.kt"
    "US003_NetworkConfig.kt"
    "US004_TerminalVsock.kt"
    "US005_DockerWorkflow.kt"
    "US006_BackupRestore.kt"
    "US007_Settings.kt"
    "US008_InputValidation.kt"
    "US009_CrossFeatureIntegration.kt"
)

all_tests_found=true
for test in "${e2e_tests[@]}"; do
    if [ -f "${PROJECT_ROOT}/app/src/androidTest/java/com/droidvisor/e2e/$test" ]; then
        log_pass "测试文件存在: $test"
    else
        log_fail "测试文件缺失: $test"
        all_tests_found=false
    fi
done

# 7. 验证QEMU源码配置
log_info "验证QEMU运行时源码配置..."

if [ -f "${PROJECT_ROOT}/app/src/main/java/com/droidvisor/vm/qemu/QemuVmRuntime.kt" ]; then
    log_pass "QEMU运行时源码存在"
    
    # 验证关键配置
    qemu_runtime_checks=(
        "class QemuVmRuntime"
        "VmRuntime.RuntimeType.QEMU"
        "qcow2"
        "startVm"
        "stopVm"
        "connectVsock"
    )
    
    for check in "${qemu_runtime_checks[@]}"; do
        if grep -q "$check" "${PROJECT_ROOT}/app/src/main/java/com/droidvisor/vm/qemu/QemuVmRuntime.kt"; then
            log_pass "QEMU运行时包含: $check"
        else
            log_fail "QEMU运行时缺失: $check"
        fi
    done
else
    log_fail "QEMU运行时源码缺失"
fi

# 8. 验证E2E测试基类
log_info "验证E2E测试基类..."

if [ -f "${PROJECT_ROOT}/app/src/androidTest/java/com/droidvisor/e2e/E2ETestBase.kt" ]; then
    log_pass "E2E测试基类存在"
    
    e2e_base_checks=(
        "abstract class E2ETestBase"
        "composeTestRule"
        "step"
        "retry"
        "@After"
        "baseCleanup"
    )
    
    for check in "${e2e_base_checks[@]}"; do
        if grep -q "$check" "${PROJECT_ROOT}/app/src/androidTest/java/com/droidvisor/e2e/E2ETestBase.kt"; then
            log_pass "E2E基类包含: $check"
        else
            log_fail "E2E基类缺失: $check"
        fi
    done
else
    log_fail "E2E测试基类缺失"
fi

# 9. 验证网络和端口配置
log_info "验证网络和端口配置..."

network_checks=(
    "172.29.0.0/16"
    "droidvisor-e2e-network"
    "DISPLAY=:0"
    "E2E_TEST_MODE=headless"
)

for check in "${network_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/docker-compose-e2e.yml" || \
       grep -q "$check" "${PROJECT_ROOT}/Dockerfile.e2e"; then
        log_pass "网络配置包含: $check"
    else
        log_fail "网络配置缺失: $check"
    fi
done

# 10. 验证文档完整性
log_info "验证文档完整性..."

doc_sections=(
    "## 测试环境架构"
    "## QEMU提供者配置"
    "## E2E测试用户故事"
    "## 快速开始"
    "## 验证虚拟机运行"
    "## 测试命令响应验证"
)

for section in "${doc_sections[@]}"; do
    if grep -q "$section" "${PROJECT_ROOT}/E2E_TESTING_README.md"; then
        log_pass "文档包含章节: $section"
    else
        log_fail "文档缺失章节: $section"
    fi
done

# 总结
echo "========================================"
echo "E2E配置验证完成"
echo "验证日志: ${VALIDATION_LOG}"
echo "========================================"

# 统计通过和失败数量
pass_count=$(grep -c "^\[PASS\]" "${VALIDATION_LOG}" || echo 0)
fail_count=$(grep -c "^\[FAIL\]" "${VALIDATION_LOG}" || echo 0)

echo "验证结果: $pass_count 项通过, $fail_count 项失败"

if [ "$fail_count" -gt 0 ]; then
    echo "⚠️  配置验证发现问题,请检查失败项"
    exit 1
else
    echo "✅ 所有配置验证通过,可以开始E2E测试"
    exit 0
fi