package com.droidvisor.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import com.droidvisor.vm.model.VmTemplateType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.vmStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "vm_state")

class VmStateDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val VM_INSTANCES_KEY = stringPreferencesKey("vm_instances")
        private val SELECTED_VM_ID_KEY = stringPreferencesKey("selected_vm_id")
    }

    val vmInstancesFlow: Flow<List<VmInstance>> = context.vmStateDataStore.data
        .map { preferences ->
            val instancesJson = preferences[VM_INSTANCES_KEY]
            if (instancesJson.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<VmInstanceData>>(instancesJson).map { it.toVmInstance() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    val selectedVmIdFlow: Flow<String?> = context.vmStateDataStore.data
        .map { preferences ->
            preferences[SELECTED_VM_ID_KEY]
        }

    suspend fun saveVmInstances(instances: List<VmInstance>) {
        context.vmStateDataStore.edit { preferences ->
            val dataList = instances.map { VmInstanceData.fromVmInstance(it) }
            preferences[VM_INSTANCES_KEY] = json.encodeToString(dataList)
        }
    }

    suspend fun saveSelectedVmId(vmId: String?) {
        context.vmStateDataStore.edit { preferences ->
            if (vmId != null) {
                preferences[SELECTED_VM_ID_KEY] = vmId
            } else {
                preferences.remove(SELECTED_VM_ID_KEY)
            }
        }
    }

    suspend fun clearState() {
        context.vmStateDataStore.edit { preferences ->
            preferences.remove(VM_INSTANCES_KEY)
            preferences.remove(SELECTED_VM_ID_KEY)
        }
    }
}

@kotlinx.serialization.Serializable
private data class VmInstanceData(
    val id: String,
    val name: String,
    val templateName: String,
    val templatePayloadBinaryName: String,
    val customMemoryBytes: Long?,
    val customCpuCores: Int?,
    val customDiskSizeBytes: Long?,
    val status: VmStatus,
    val createdAt: Long,
    val startedAt: Long?,
    val ipAddress: String?
) {
    fun toVmInstance(): VmInstance {
        val template = VmTemplate(
            type = VmTemplateType.CUSTOM,
            name = templateName,
            payloadBinaryName = templatePayloadBinaryName,
            memoryBytes = 0L,
            cpuCores = 0,
            diskSizeBytes = 0L,
            description = ""
        )
        return VmInstance(
            id = id,
            name = name,
            template = template,
            customMemoryBytes = customMemoryBytes,
            customCpuCores = customCpuCores,
            customDiskSizeBytes = customDiskSizeBytes,
            status = status,
            createdAt = createdAt,
            startedAt = startedAt,
            ipAddress = ipAddress
        )
    }

    companion object {
        fun fromVmInstance(vm: VmInstance): VmInstanceData {
            return VmInstanceData(
                id = vm.id,
                name = vm.name,
                templateName = vm.template.name,
                templatePayloadBinaryName = vm.template.payloadBinaryName,
                customMemoryBytes = vm.customMemoryBytes,
                customCpuCores = vm.customCpuCores,
                customDiskSizeBytes = vm.customDiskSizeBytes,
                status = vm.status,
                createdAt = vm.createdAt,
                startedAt = vm.startedAt,
                ipAddress = vm.ipAddress
            )
        }
    }
}