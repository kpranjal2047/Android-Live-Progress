package com.pranjal.liveprogress

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Observe access changes only for the lifetime of the owning screen or service. */
class PrerequisiteAccessObserver(context: Context, private val onChanged: () -> Unit) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val shizuku = ShizukuAccessObserver(context, onChanged)
    private var started = false
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (started && intent.data?.schemeSpecificPart == PrivilegedAccess.SHIZUKU_PACKAGE) onChanged()
        }
    }
    private val accessibility = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { if (started) onChanged() }
    }
    fun start() {
        if (started) return
        started = true
        listOf(Settings.Secure.ACCESSIBILITY_ENABLED, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).forEach {
            context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, accessibility)
        }
        context.registerReceiver(packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        shizuku.start()
    }
    fun stop() {
        if (!started) return
        started = false
        shizuku.stop()
        context.unregisterReceiver(packages)
        context.contentResolver.unregisterContentObserver(accessibility)
        handler.removeCallbacksAndMessages(null)
    }
}
