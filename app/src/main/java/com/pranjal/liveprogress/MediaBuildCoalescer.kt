package com.pranjal.liveprogress

class MediaBuildCoalescer<T> {
    private var running = false
    private var queued: T? = null

    fun submit(request: T, start: (T) -> Unit) {
        if (running) {
            queued = request
            return
        }
        running = true
        start(request)
    }

    fun complete(start: (T) -> Unit) {
        val next = queued
        queued = null
        if (next == null) {
            running = false
            return
        }
        start(next)
    }

    fun cancelQueued() {
        queued = null
    }
}
