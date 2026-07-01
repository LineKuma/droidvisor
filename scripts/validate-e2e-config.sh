#!/bin/bash
#
# Droidvisor E2E Configuration Validation Script
# 功能：验证E2E测试配置文件的完整性和正确性
# 验证架构：单一容器（无外部提供者），所有测试验证项目自身功能
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
echo "架构: 单一容器（无外部提供者）"
echo "验证时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"

# 1. 验证必需文件存在
log_info "检查必需配置文件..."

required_files=(
    "docker-compose-e2e.yml"
    "Dockerfile.e2e"
    "scripts/run-e2e-tests.sh"
    ".github/workflows/e2e.yml"
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

# 2. 验证Docker Compose配置结构 - 确认无外部提供者
log_info "验证Docker Compose配置结构（无外部提供者）..."

if python3 -c "import yaml" 2>/dev/null; then
    python3 -c "
import yaml
try:
    with open('${PROJECT_ROOT}/docker-compose-e2e.yml', 'r') as f:
        config = yaml.safe_load(f)

    services = config.get('services', {})
    service_names = list(services.keys())

    # 验证无外部提供者
    assert 'qemu-provider' not in service_names, '不应存在 qemu-provider 服务'
    assert 'avd-setup' not in service_names, '不应存在 avd-setup 服务'

    # 验证只有 android-e2e-test
    assert 'android-e2e-test' in service_names, '必须存在 android-e2e-test 服务'
    assert len(service_names) == 1, '应该只有 android-e2e-test 一个服务'

    print(f'[PASS] 服务定义正确: {service_names}')
    print('[PASS] 无外部提供者服务')

    # 验证网络配置
    networks = config.get('networks', {})
    if 'droidvisor-e2e-network' in networks:
        print('[PASS] 网络配置存在: droidvisor-e2e-network')

    # 验证没有外部卷
    volumes = config.get('volumes', {})
    if not volumes:
        print('[PASS] 无外部卷定义（干净配置）')

    print('[PASS] Docker Compose配置结构验证成功')
except AssertionError as e:
    print(f'[FAIL] 配置结构错误: {e}')
except Exception as e:
    print(f'[FAIL] Docker Compose配置解析失败: {e}')
" 2>&1 | tee -a "${VALIDATION_LOG}"
else
    # 如果Python yaml模块不可用，使用基础验证
    if grep -q "android-e2e-test:" "${PROJECT_ROOT}/docker-compose-e2e.yml"; then
        log_pass "Docker Compose服务定义存在: android-e2e-test"
    else
        log_fail "Docker Compose服务定义缺失: android-e2e-test"
    fi

    # 验证无外部提供者
    if grep -q "qemu-provider:" "${PROJECT_ROOT}/docker-compose-e2e.yml"; then
        log_fail "配置错误: 存在外部 qemu-provider 服务"
    else
        log_pass "无外部 qemu-provider 服务"
    fi

    if grep -q "avd-setup:" "${PROJECT_ROOT}/docker-compose-e2e.yml"; then
        log_fail "配置错误: 存在外部 avd-setup 服务"
    else
        log_pass "无外部 avd-setup 服务"
    fi
fi

# 3. 验证Dockerfile中项目编译和依赖
log_info "验证Dockerfile.e2e 项目环境配置..."

dockerfile_checks=(
    "openjdk-17-jdk"
    "Android SDK"
    "system-images;android-34"
    "e2e_test_avd"
    "debian-12-genericcloud-arm64"
    "virt-customize"
    "virt-get-kernel"
    "e2e_ssh_key"
)

for check in "${dockerfile_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/Dockerfile.e2e"; then
        log_pass "Dockerfile包含: $check"
    else
        log_fail "Dockerfile缺失: $check"
    fi
done

# 验证Dockerfile中无外部提供者环境变量
if grep -q "QEMU_PROVIDER=default" "${PROJECT_ROOT}/Dockerfile.e2e"; then
    log_fail "Dockerfile包含外部提供者环境变量: QEMU_PROVIDER=default"
else
    log_pass "Dockerfile无外部提供者环境变量"
fi

# 4. 验证Workflow配置
log_info "验证Workflow配置..."

workflow_checks=(
    "name: E2E Tests"
    "android-e2e-test"
    "assembleDebug"
    "am instrument"
    "AndroidJUnitRunner"
    "com.droidvisor.e2e"
    "QemuVmRuntime"
)

for check in "${workflow_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
        log_pass "Workflow包含: $check"
    else
        log_fail "Workflow缺失: $check"
    fi
done

# 验证workflow中无外部提供者验证步骤
if grep -q "Verify QEMU Provider" "${PROJECT_ROOT}/.github/workflows/e2e.yml"; then
    log_fail "Workflow包含外部提供者验证步骤"
else
    log_pass "Workflow无外部提供者验证步骤"
fi

# 5. 验证E2E测试文件
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

# 6. 验证项目QEMU运行时源码（被测对象）
log_info "验证项目QEMU运行时源码（被测对象）..."

if [ -f "${PROJECT_ROOT}/app/src/main/java/com/droidvisor/vm/qemu/QemuVmRuntime.kt" ]; then
    log_pass "QEMU运行时源码存在"
    
    qemu_runtime_checks=(
        "class QemuVmRuntime"
        "VmRuntime.RuntimeType.QEMU"
        "startVm"
        "stopVm"
        "connectVsock"
        "isAvailable"
        "getDiskManager"
        "prepareDisks"
        "launchQemuProcess"
    )
    
    for check in "${qemu_runtime_checks[@]}"; do
        if grep -q "$check" "${PROJECT_ROOT}/app/src/main/java/com/droidvisor/vm/qemu/QemuVmRuntime.kt"; then
            log_pass "QemuVmRuntime包含: $check"
        else
            log_fail "QemuVmRuntime缺失: $check"
        fi
    done
else
    log_fail "QEMU运行时源码缺失"
fi

# 7. 验证E2E测试基类
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
        "createdVms"
        "registerVmForCleanup"
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

# 8. 验证本地测试脚本关键函数
log_info "验证本地测试脚本..."

script_checks=(
    "compile_apk"
    "start_emulator"
    "run_instrumentation_tests"
    "verify_app_qemu_logs"
    "collect_e2e_reports"
    "stop_e2e_container"
)

for check in "${script_checks[@]}"; do
    if grep -q "$check" "${PROJECT_ROOT}/scripts/run-e2e-tests.sh"; then
        log_pass "测试脚本包含函数: $check"
    else
        log_fail "测试脚本缺失函数: $check"
    fi
done

# 验证 Debian SSH 验证函数
if grep -q "verify_debian_vm_ssh" "${PROJECT_ROOT}/scripts/run-e2e-tests.sh"; then
    log_pass "测试脚本包含 Debian SSH 验证函数: verify_debian_vm_ssh"
fi

# 验证脚本中无外部提供者引用
if grep -q "verify_qemu_provider" "${PROJECT_ROOT}/scripts/run-e2e-tests.sh"; then
    log_fail "测试脚本包含外部提供者验证: verify_qemu_provider"
else
    log_pass "测试脚本无外部提供者验证函数"
fi

if grep -q "verify_avd_environment" "${PROJECT_ROOT}/scripts/run-e2e-tests.sh"; then
    log_fail "测试脚本包含外部AVD验证: verify_avd_environment"
else
    log_pass "测试脚本无外部AVD验证函数"
fi

# 9. 验证项目架构完整性
log_info "验证项目架构完整性..."

architecture_checks=(
    "app/src/main/java/com/droidvisor/vm/qemu/QemuVmRuntime.kt"
    "app/src/main/java/com/droidvisor/vm/qemu/QemuVmConfig.kt"
    "app/src/main/java/com/droidvisor/vm/qemu/QemuDiskManager.kt"
    "app/src/main/java/com/droidvisor/vm/qemu/QemuProcessManager.kt"
    "app/src/main/java/com/droidvisor/vm/qemu/QemuVsockChannel.kt"
)

all_arch_found=true
for file in "${architecture_checks[@]}"; do
    if [ -f "${PROJECT_ROOT}/$file" ]; then
        log_pass "项目源码存在: $file"
    else
        log_fail "项目源码缺失: $file"
        all_arch_found=false
    fi
done

# 10. 确保E2E测试依赖项目QEMU运行时（而非外部提供者）
log_info "验证E2E测试依赖关系..."

# 检查US002（VM管理）是否依赖QemuVmRuntime
if grep -q "QemuVmRuntime\|QemuVmConfig\|vm/qemu" "${PROJECT_ROOT}/app/src/androidTest/java/com/droidvisor/e2e/US002_VmManagement.kt" 2>/dev/null; then
    log_pass "US002_VmManagement 引用项目QemuVmRuntime"
else
    log_info "US002_VmManagement 通过UI间接测试QemuVmRuntime（通过Compose UI交互）"
fi

# 总结
echo "========================================"
echo "E2E配置验证完成"
echo "验证日志: ${VALIDATION_LOG}"
echo "========================================"

# 统计通过和失败数量
pass_count=$(grep -c "^\[PASS\]" "${VALIDATION_LOG}" 2>/dev/null)
pass_count=${pass_count:-0}
fail_count=$(grep -c "^\[FAIL\]" "${VALIDATION_LOG}" 2>/dev/null)
fail_count=${fail_count:-0}

echo "验证结果: ${pass_count} 项通过, ${fail_count} 项失败"

if [ "$fail_count" -gt 0 ]; then
    echo "⚠️  配置验证发现问题,请检查失败项"
    exit 1
else
    echo "✅ 所有配置验证通过,可以开始E2E测试"
    exit 0
fi