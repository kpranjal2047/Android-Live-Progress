package com.pranjal.liveprogress

import android.content.Context
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

/** Refresh visible permission UI when the service starts/stops or a permission dialog returns. */
class ShizukuAccessObserver(context: Context, private val onChanged: () -> Unit) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var previous: ShizukuAccessStatus? = null
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val died = Shizuku.OnBinderDeadListener { refresh() }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun start() {
        if (registered) return
        registered = true
        previous = currentStatus()
        Shizuku.addBinderReceivedListenerSticky(received, handler)
        Shizuku.addBinderDeadListener(died, handler)
        Shizuku.addRequestPermissionResultListener(permission, handler)
    }

    fun stop() {
        registered = false
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(died)
        Shizuku.removeRequestPermissionResultListener(permission)
        handler.removeCallbacksAndMessages(null)
    }

    private fun currentStatus(): ShizukuAccessStatus {
        val state = PrivilegedAccess.currentState(appContext)
        return ShizukuAccessPolicy.status(state.shizukuAvailable, state.shizukuGranted)
    }

    private fun refresh() {
        if (!registered) return
        val status = currentStatus()
        if (status == previous) return
        previous = status
        onChanged()
    }
}
