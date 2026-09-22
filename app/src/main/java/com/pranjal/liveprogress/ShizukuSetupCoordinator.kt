package com.pranjal.liveprogress

import android.content.Context
import android.os.Handler
import android.os.Looper

sealed interface ShizukuSetupState {
    data object Idle : ShizukuSetupState

    data class Running(
        val items: List<AutomaticSetupItem>
    ) : ShizukuSetupState

    data class Finished(
        val outcome: ShizukuSetupOutcome
    ) : ShizukuSetupState
}

data class ShizukuSetupOutcome(
    val requestedItems: List<AutomaticSetupItem>,
    val completedItems: Set<AutomaticSetupItem>,
    val failedItems: Set<AutomaticSetupItem>
)

object ShizukuSetupCoordinator {
    private const val VERIFY_RETRY_DELAY_MS = 500L
    private const val MAX_VERIFY_ATTEMPTS = 6

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state: ShizukuSetupState = ShizukuSetupState.Idle
    private var observer: ((ShizukuSetupState) -> Unit)? = null

    fun currentState(): ShizukuSetupState = synchronized(lock) { state }

    fun addObserver(callback: (ShizukuSetupState) -> Unit) {
        val snapshot = synchronized(lock) {
            observer = callback
            state
        }
        mainHandler.post { callback(snapshot) }
    }

    fun removeObserver(callback: (ShizukuSetupState) -> Unit) {
        synchronized(lock) {
            if (observer === callback) observer = null
        }
    }

    fun start(context: Context): Boolean {
        val appContext = context.applicationContext
        val before = SetupAccessStateReader.read(appContext)
        val items = SetupFlowPolicy.automaticItemsMissing(before)
        if (items.isEmpty() || !before.shizukuGranted) return false
        synchronized(lock) {
            if (state is ShizukuSetupState.Running) return false
        }
        setState(ShizukuSetupState.Running(items))
        AppDiagnostics.note(
            appContext,
            "privileged_setup",
            "Automatic setup started; items=${items.joinToString()}"
        )
        PrivilegedAccess.grantSetupAccessAsync(appContext, items) {
            verifySetupAccess(appContext, items, attempt = 1)
        }
        return true
    }

    fun acknowledgeFinished(outcome: ShizukuSetupOutcome) {
        synchronized(lock) {
            if ((state as? ShizukuSetupState.Finished)?.outcome == outcome) {
                state = ShizukuSetupState.Idle
            }
        }
    }

    private fun AutomaticSetupItem.isReady(state: SetupAccessState): Boolean {
        return when (this) {
            AutomaticSetupItem.NOTIFICATIONS -> state.notificationsReady
            AutomaticSetupItem.PROMOTED_NOTIFICATIONS -> state.promotedNotificationsReady
            AutomaticSetupItem.NOTIFICATION_LISTENER -> state.notificationListenerReady
            AutomaticSetupItem.ACCESSIBILITY -> state.accessibilityEnabled
        }
    }

    private fun verifySetupAccess(
        context: Context,
        items: List<AutomaticSetupItem>,
        attempt: Int
    ) {
        val after = SetupAccessStateReader.read(context)
        val completed = items.filterTo(linkedSetOf()) { item -> item.isReady(after) }
        val failed = items.toSet() - completed
        if (failed.isNotEmpty() && attempt < MAX_VERIFY_ATTEMPTS) {
            mainHandler.postDelayed(
                { verifySetupAccess(context, items, attempt + 1) },
                VERIFY_RETRY_DELAY_MS
            )
            return
        }
        val outcome = ShizukuSetupOutcome(items, completed, failed)
        AppDiagnostics.note(
            context,
            "privileged_setup",
            "Automatic setup finished; completed=${completed.joinToString()}; failed=${failed.joinToString()}; attempts=$attempt"
        )
        setState(ShizukuSetupState.Finished(outcome))
    }

    private fun setState(next: ShizukuSetupState) {
        val callback = synchronized(lock) {
            state = next
            observer
        }
        callback?.let { target -> mainHandler.post { target(next) } }
    }
}
