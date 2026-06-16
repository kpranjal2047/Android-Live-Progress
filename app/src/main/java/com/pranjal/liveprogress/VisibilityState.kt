package com.pranjal.liveprogress

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

object VisibilityState {
    private val UNLOCK_REFRESH_DELAYS_MS = longArrayOf(250L, 750L, 1_500L)

    @Volatile
    var quickSettingsExpanded: Boolean = false
        private set

    @Volatile
    var locked: Boolean = false
        private set

    @Volatile
    var screenOff: Boolean = false
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var unlockRefreshGeneration = 0

    fun register(context: Context) {
        val appContext = context.applicationContext
        refreshLockState(appContext)
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        appContext.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun setQuickSettingsExpanded(context: Context, expanded: Boolean) {
        if (quickSettingsExpanded == expanded) return
        quickSettingsExpanded = expanded
        AppDiagnostics.note(context, "visibility", "Quick settings expanded=$expanded")
        notifyListeners()
    }

    fun refreshLockState(context: Context) {
        refreshLockState(context, forceNotifyIfUnlocked = false)
    }

    private fun refreshLockState(context: Context, forceNotifyIfUnlocked: Boolean) {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val current = keyguard.isKeyguardLocked
        val changed = locked != current
        locked = current
        if (changed) {
            AppDiagnostics.note(context, "visibility", "Locked=$current")
        }
        if (changed || (forceNotifyIfUnlocked && !locked && !screenOff)) {
            notifyListeners()
        }
    }

    fun shouldShowMirror(
        context: Context,
        hideWhenQuickSettingsExpanded: Boolean = true
    ): Boolean {
        refreshLockState(context)
        return MirrorVisibilityPolicy.shouldShow(
            locked = locked,
            quickSettingsExpanded = quickSettingsExpanded,
            hideWhenQuickSettingsExpanded = hideWhenQuickSettingsExpanded
        )
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }

    private fun scheduleUnlockRefreshes(context: Context) {
        val appContext = context.applicationContext
        val generation = ++unlockRefreshGeneration
        UNLOCK_REFRESH_DELAYS_MS.forEach { delayMs ->
            mainHandler.postDelayed(
                {
                    if (generation != unlockRefreshGeneration) return@postDelayed
                    refreshLockState(appContext, forceNotifyIfUnlocked = true)
                },
                delayMs
            )
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    unlockRefreshGeneration += 1
                    screenOff = true
                    locked = true
                    quickSettingsExpanded = false
                    AppDiagnostics.note(context, "visibility", "Screen off; treating device as locked")
                    notifyListeners()
                }

                Intent.ACTION_USER_PRESENT -> {
                    screenOff = false
                    locked = false
                    AppDiagnostics.note(context, "visibility", "User present; unlocked")
                    notifyListeners()
                    scheduleUnlockRefreshes(context)
                }

                Intent.ACTION_SCREEN_ON -> {
                    val wasScreenOff = screenOff
                    val previousLocked = locked
                    screenOff = false
                    refreshLockState(context)
                    if (wasScreenOff && previousLocked == locked) {
                        AppDiagnostics.note(context, "visibility", "Screen on")
                        notifyListeners()
                    }
                    scheduleUnlockRefreshes(context)
                }
            }
        }
    }
}
