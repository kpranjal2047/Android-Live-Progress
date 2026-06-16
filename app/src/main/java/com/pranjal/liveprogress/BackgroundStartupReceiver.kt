package com.pranjal.liveprogress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BackgroundStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "Device boot completed"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "App package replaced"
            else -> return
        }
        BackgroundRuntime.initialize(context, reason)
    }
}
