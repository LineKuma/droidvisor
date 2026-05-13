package com.droidvisor.vm

import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

class AvfCapabilityChecker(private val context: Context) {

    private val TAG = "AvfCapabilityChecker"

    data class AvfCapabilities(
        val isAvfSupported: Boolean,
        val isProtectedVmSupported: Boolean,
        val isVsockSupported: Boolean,
        val minimumSdkMet: Boolean
    )

    fun checkCapabilities(): AvfCapabilities {
        return AvfCapabilities(
            isAvfSupported = checkAvfSupport(),
            isProtectedVmSupported = checkProtectedVmSupport(),
            isVsockSupported = checkVsockSupport(),
            minimumSdkMet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
    }

    private fun checkAvfSupport(): Boolean {
        return try {
            val virtualMachineManagerClass = Class.forName("android.os.VirtualMachineManager")
            val getInstanceMethod = virtualMachineManagerClass.getMethod("getInstance", Context::class.java)
            getInstanceMethod.invoke(null, context)
            Log.d(TAG, "AVF is supported")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AVF is not supported", e)
            false
        }
    }

    private fun checkProtectedVmSupport(): Boolean {
        return try {
            val virtualMachineManagerClass = Class.forName("android.os.VirtualMachineManager")
            val getInstanceMethod = virtualMachineManagerClass.getMethod("getInstance", Context::class.java)
            val vmManager = getInstanceMethod.invoke(null, context)

            val getCapabilitiesMethod = virtualMachineManagerClass.getMethod("getCapabilities")
            val capabilities = getCapabilitiesMethod.invoke(vmManager) as Int

            val CAPABILITY_PROTECTED_VM = 1
            val hasProtectedVm = (capabilities and CAPABILITY_PROTECTED_VM) != 0

            Log.d(TAG, "Protected VM support: $hasProtectedVm")
            hasProtectedVm
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Protected VM support", e)
            false
        }
    }

    private fun checkVsockSupport(): Boolean {
        return try {
            Class.forName("android.os.VirtualMachineManager")
            Log.d(TAG, "Vsock is supported (AVF present)")
            true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Vsock is not supported", e)
            false
        }
    }
}