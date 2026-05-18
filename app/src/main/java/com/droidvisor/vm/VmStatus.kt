package com.droidvisor.vm

enum class VmStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

fun VmStatus.isRunning(): Boolean = this == VmStatus.RUNNING

fun VmStatus.isStopped(): Boolean = this == VmStatus.STOPPED

fun VmStatus.canStart(): Boolean = this == VmStatus.STOPPED || this == VmStatus.ERROR

fun VmStatus.canStop(): Boolean = this == VmStatus.RUNNING

fun VmStatus.isError(): Boolean = this == VmStatus.ERROR
