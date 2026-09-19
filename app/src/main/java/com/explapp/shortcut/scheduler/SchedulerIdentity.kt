package com.explapp.shortcut.scheduler

object SchedulerIdentity {
    fun requestCode(id: String): Int = id.hashCode()
}
