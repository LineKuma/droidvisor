package com.droidvisor.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VmStatusTest {

    @Test
    fun statusValues_areComplete() {
        val statuses = VmStatus.values()
        assertEquals(5, statuses.size)
        assertTrue(statuses.contains(VmStatus.STOPPED))
        assertTrue(statuses.contains(VmStatus.STARTING))
        assertTrue(statuses.contains(VmStatus.RUNNING))
        assertTrue(statuses.contains(VmStatus.STOPPING))
        assertTrue(statuses.contains(VmStatus.ERROR))
    }

    @Test
    fun isRunning_returnsTrueOnlyWhenRunning() {
        assertTrue(VmStatus.RUNNING.isRunning())
        assertFalse(VmStatus.STOPPED.isRunning())
        assertFalse(VmStatus.STARTING.isRunning())
        assertFalse(VmStatus.STOPPING.isRunning())
        assertFalse(VmStatus.ERROR.isRunning())
    }

    @Test
    fun isStopped_returnsTrueOnlyWhenStopped() {
        assertTrue(VmStatus.STOPPED.isStopped())
        assertFalse(VmStatus.RUNNING.isStopped())
        assertFalse(VmStatus.STARTING.isStopped())
        assertFalse(VmStatus.STOPPING.isStopped())
        assertFalse(VmStatus.ERROR.isStopped())
    }

    @Test
    fun canStart_returnsTrueWhenStoppedOrError() {
        assertTrue(VmStatus.STOPPED.canStart())
        assertTrue(VmStatus.ERROR.canStart())
        assertFalse(VmStatus.RUNNING.canStart())
        assertFalse(VmStatus.STARTING.canStart())
        assertFalse(VmStatus.STOPPING.canStart())
    }

    @Test
    fun canStop_returnsTrueOnlyWhenRunning() {
        assertTrue(VmStatus.RUNNING.canStop())
        assertFalse(VmStatus.STOPPED.canStop())
        assertFalse(VmStatus.STARTING.canStop())
        assertFalse(VmStatus.STOPPING.canStop())
        assertFalse(VmStatus.ERROR.canStop())
    }

    @Test
    fun isError_returnsTrueOnlyWhenError() {
        assertTrue(VmStatus.ERROR.isError())
        assertFalse(VmStatus.RUNNING.isError())
        assertFalse(VmStatus.STOPPED.isError())
        assertFalse(VmStatus.STARTING.isError())
        assertFalse(VmStatus.STOPPING.isError())
    }

    @Test
    fun statusTransitions_fromStopped() {
        val stopped = VmStatus.STOPPED
        assertTrue(stopped.canStart())
        assertFalse(stopped.canStop())
    }

    @Test
    fun statusTransitions_fromRunning() {
        val running = VmStatus.RUNNING
        assertFalse(running.canStart())
        assertTrue(running.canStop())
        assertTrue(running.isRunning())
    }

    @Test
    fun statusTransitions_fromStarting() {
        val starting = VmStatus.STARTING
        assertFalse(starting.canStart())
        assertFalse(starting.canStop())
        assertFalse(starting.isRunning())
    }

    @Test
    fun statusTransitions_fromStopping() {
        val stopping = VmStatus.STOPPING
        assertFalse(stopping.canStart())
        assertFalse(stopping.canStop())
        assertFalse(stopping.isRunning())
    }

    @Test
    fun statusTransitions_fromError() {
        val error = VmStatus.ERROR
        assertTrue(error.canStart())
        assertFalse(error.canStop())
        assertTrue(error.isError())
        assertFalse(error.isRunning())
    }
}