package com.droidvisor.vm

class VmConfigValidator {

    companion object {
        const val MIN_MEMORY_BYTES = 512L * 1024 * 1024
        const val MAX_MEMORY_BYTES = 32L * 1024 * 1024 * 1024
        const val MIN_CPU_CORES = 1
        const val MAX_CPU_CORES = 16
        const val MIN_DISK_SIZE_BYTES = 512L * 1024 * 1024
        const val MAX_DISK_SIZE_BYTES = 256L * 1024 * 1024 * 1024
    }

    fun validate(config: VmConfig): ValidationResult {
        if (config.memoryBytes < MIN_MEMORY_BYTES) {
            return ValidationResult(false, "Memory must be at least ${MIN_MEMORY_BYTES / (1024 * 1024)} MB")
        }
        if (config.memoryBytes > MAX_MEMORY_BYTES) {
            return ValidationResult(false, "Memory must not exceed ${MAX_MEMORY_BYTES / (1024 * 1024 * 1024)} GB")
        }

        if (config.cpuCores < MIN_CPU_CORES) {
            return ValidationResult(false, "CPU cores must be at least $MIN_CPU_CORES")
        }
        if (config.cpuCores > MAX_CPU_CORES) {
            return ValidationResult(false, "CPU cores must not exceed $MAX_CPU_CORES")
        }

        if (config.diskSizeBytes < 0) {
            return ValidationResult(false, "disk size cannot be negative")
        }
        if (config.diskSizeBytes > 0 && config.diskSizeBytes < MIN_DISK_SIZE_BYTES) {
            return ValidationResult(false, "disk size must be at least ${MIN_DISK_SIZE_BYTES / (1024 * 1024)} MB")
        }
        if (config.diskSizeBytes > MAX_DISK_SIZE_BYTES) {
            return ValidationResult(false, "disk size must not exceed ${MAX_DISK_SIZE_BYTES / (1024 * 1024 * 1024)} GB")
        }

        if (config.payloadBinaryName.isEmpty()) {
            return ValidationResult(false, "Payload binary name cannot be empty")
        }
        if (!config.payloadBinaryName.matches(Regex("^[a-zA-Z0-9_-]+\\.?[a-zA-Z0-9_-]*\$"))) {
            return ValidationResult(false, "Payload binary name contains invalid characters")
        }

        return ValidationResult(true, null)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}