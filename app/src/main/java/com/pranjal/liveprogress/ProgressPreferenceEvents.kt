package com.pranjal.liveprogress

import java.util.concurrent.CopyOnWriteArraySet

object ProgressPreferenceEvents {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }
}
