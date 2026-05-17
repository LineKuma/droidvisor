package com.droidvisor.vm

enum class VmStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
}

fun VmStatus.isRunning(): Boolean = this == VmStatus.RUNNING

fun VmStatus.isStopped(): Boolean = this == VmStatus.STOPPED

fun VmStatus.canStart(): Boolean = this == VmStatus.STOPPED

fun VmStatus.canStop(): Boolean = this == VmStatus.RUNNING