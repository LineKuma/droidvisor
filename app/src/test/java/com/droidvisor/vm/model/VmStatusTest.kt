package com.droidvisor.vm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VmStatusTest {

    @Test
    fun isRunning_returnsTrue_whenStatusIsRunning() {
        assertTrue(VmStatus.RUNNING.isRunning())
    }

    @Test
    fun isRunning_returnsFalse_whenStatusIsNotRunning() {
        assertFalse(VmStatus.STOPPED.isRunning())
        assertFalse(VmStatus.STARTING.isRunning())
        assertFalse(VmStatus.STOPPING.isRunning())
        assertFalse(VmStatus.ERROR.isRunning())
    }

    @Test
    fun isStopped_returnsTrue_whenStatusIsStopped() {
        assertTrue(VmStatus.STOPPED.isStopped())
    }

    @Test
    fun isStopped_returnsFalse_whenStatusIsNotStopped() {
        assertFalse(VmStatus.RUNNING.isStopped())
        assertFalse(VmStatus.STARTING.isStopped())
        assertFalse(VmStatus.STOPPING.isStopped())
        assertFalse(VmStatus.ERROR.isStopped())
    }

    @Test
    fun canStart_returnsTrue_whenStatusIsStoppedOrError() {
        assertTrue(VmStatus.STOPPED.canStart())
        assertTrue(VmStatus.ERROR.canStart())
    }

    @Test
    fun canStart_returnsFalse_whenStatusIsRunningOrStartingOrStopping() {
        assertFalse(VmStatus.RUNNING.canStart())
        assertFalse(VmStatus.STARTING.canStart())
        assertFalse(VmStatus.STOPPING.canStart())
    }

    @Test
    fun canStop_returnsTrue_whenStatusIsRunning() {
        assertTrue(VmStatus.RUNNING.canStop())
    }

    @Test
    fun canStop_returnsFalse_whenStatusIsNotRunning() {
        assertFalse(VmStatus.STOPPED.canStop())
        assertFalse(VmStatus.STARTING.canStop())
        assertFalse(VmStatus.STOPPING.canStop())
        assertFalse(VmStatus.ERROR.canStop())
    }

    @Test
    fun isError_returnsTrue_whenStatusIsError() {
        assertTrue(VmStatus.ERROR.isError())
    }

    @Test
    fun isError_returnsFalse_whenStatusIsNotError() {
        assertFalse(VmStatus.RUNNING.isError())
        assertFalse(VmStatus.STOPPED.isError())
        assertFalse(VmStatus.STARTING.isError())
        assertFalse(VmStatus.STOPPING.isError())
    }
}