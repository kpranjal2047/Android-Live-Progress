package com.pranjal.liveprogress

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationMirrorService : NotificationListenerService() {
    private val candidates = linkedMapOf<String, MirrorCandidate>()
    private val mirrorVisibilityByKey = mutableMapOf<String, String>()
    private val progressSnapshotsByKey = mutableMapOf<String, ProgressMirrorSnapshot>()
    private val progressUseSourceIconByKey = mutableMapOf<String, Boolean>()
    private val visibilityListener = { reconcileVisibility() }
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaLiveController: MediaLiveController
    private lateinit var progressPreferences: ProgressPreferences
    private lateinit var visibilityPreferences: VisibilityPreferences
    private var progressMirrorActive = false
    private var lastRefreshUptimeMs = 0L
    private val progressPreferenceListener = { onProgressPreferencesChanged() }
    private val visibilityPreferenceListener = { onVisibilityPreferencesChanged() }

    companion object {
        @Volatile
        private var activeService: NotificationMirrorService? = null

        fun requestRefresh(context: Context, reason: String) {
            val service = activeService
            val shouldRefresh = StartupRefreshThrottle.shouldRefresh(
                listenerConnected = service != null,
                nowUptimeMs = SystemClock.uptimeMillis(),
                lastRefreshUptimeMs = service?.lastRefreshUptimeMs ?: 0L
            )
            if (!shouldRefresh || service == null) {
                BatteryDiagnostics.increment(BatteryDiagnostics.Counter.STARTUP_REFRESH_SKIPS)
                AppDiagnostics.note(
                    context,
                    "listener",
                    "Progress startup refresh skipped; connected=${service != null}; reason=$reason"
                )
                return
            }
            service.refreshActiveNotifications(reason)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        progressPreferences = ProgressPreferences(this)
        visibilityPreferences = VisibilityPreferences(this)
        MirrorNotificationBuilder.ensureChannel(this)
        mediaLiveController = MediaLiveController(this, notificationManager)
        mediaLiveController.initialize()
        ProgressPreferenceEvents.addListener(progressPreferenceListener)
        VisibilityPreferenceEvents.addListener(visibilityPreferenceListener)
        VisibilityState.register(this)
        VisibilityState.addListener(visibilityListener)
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        mediaLiveController.destroy()
        ProgressPreferenceEvents.removeListener(progressPreferenceListener)
        VisibilityPreferenceEvents.removeListener(visibilityPreferenceListener)
        OriginalSuppressionController.restoreAll(this, "notification listener destroyed")
        VisibilityState.removeListener(visibilityListener)
        super.onDestroy()
    }

    override fun onListenerConnected() {
        activeService = this
        AppDiagnostics.note(this, "listener", "Notification listener connected")
        refreshActiveNotifications("listener connected")
    }

    override fun onListenerDisconnected() {
        if (activeService === this) activeService = null
        AppDiagnostics.note(this, "listener", "Notification listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handlePosted(sbn)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {
        handlePosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        mediaLiveController.onNotificationRemoved(sbn, reason = -1)
        removeMirrorFor(sbn)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {
        mediaLiveController.onNotificationRemoved(sbn, reason = -1)
        removeMirrorFor(sbn)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        mediaLiveController.onNotificationRemoved(sbn, reason)
        removeMirrorFor(sbn)
    }

    private fun refreshActiveNotifications(reason: String) {
        lastRefreshUptimeMs = SystemClock.uptimeMillis()
        val active = activeNotificationsSnapshot(reason)
        AppDiagnostics.note(
            this,
            "listener",
            "Refreshing ${active.size} active notifications; progress enabled=${progressPreferences.enabled}; reason=$reason"
        )
        if (!progressPreferences.enabled) {
            clearProgressMirrors("progress live updates disabled")
            active.forEach { sbn ->
                mediaLiveController.onNotificationPosted(sbn)
            }
            publishProgressMirrorActivity()
            mediaLiveController.onVisibilityChanged()
            return
        }
        candidates.clear()
        active.forEach { sbn ->
            mediaLiveController.onNotificationPosted(sbn)
            NotificationClassifier.toCandidate(this, sbn)?.let { candidates[it.key] = it }
        }
        AppDiagnostics.note(
            this,
            "listener",
            "Tracking ${candidates.size} progress mirrors after refresh"
        )
        reconcileVisibility()
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        mediaLiveController.onNotificationPosted(sbn)
        if (!progressPreferences.enabled) {
            removeMirrorFor(sbn)
            return
        }
        val candidate = NotificationClassifier.toCandidate(this, sbn)
        if (candidate == null) {
            removeMirrorFor(sbn)
            return
        }

        val isNewCandidate = candidate.key !in candidates
        candidates[candidate.key] = candidate
        if (isNewCandidate) {
            AppDiagnostics.note(
                this,
                "listener",
                "Tracking ${candidate.appLabel}${candidate.progress.diagnosticSuffix()}"
            )
        }
        publishProgressMirrorActivity()
        applyVisibility(candidate)
    }

    private fun removeMirrorFor(sbn: StatusBarNotification) {
        val removed = candidates.remove(sbn.key) ?: return
        OriginalSuppressionController.onMirrorHidden(this, removed, "source notification removed")
        notificationManager.cancel(removed.notificationId)
        mirrorVisibilityByKey.remove(removed.key)
        clearProgressSnapshot(removed.key)
        AppDiagnostics.note(this, "mirror", "Removed mirror for ${removed.appLabel}")
        publishProgressMirrorActivity()
    }

    private fun clearProgressMirrors(reason: String) {
        if (candidates.isEmpty()) return
        candidates.values.forEach { candidate ->
            OriginalSuppressionController.onMirrorHidden(this, candidate, reason)
            notificationManager.cancel(candidate.notificationId)
            mirrorVisibilityByKey.remove(candidate.key)
            clearProgressSnapshot(candidate.key)
        }
        candidates.clear()
        publishProgressMirrorActivity()
    }

    private fun reconcileVisibility() {
        VisibilityState.refreshLockState(this)
        val activeKeys = activeNotificationsSnapshot("visibility reconcile")
            .filter { it.packageName != packageName }
            .map { it.key }
            .toSet()

        val iterator = candidates.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeKeys) {
                OriginalSuppressionController.onMirrorHidden(
                    this,
                    entry.value,
                    "source notification no longer active"
                )
                notificationManager.cancel(entry.value.notificationId)
                mirrorVisibilityByKey.remove(entry.key)
                clearProgressSnapshot(entry.key)
                iterator.remove()
            } else {
                applyVisibility(entry.value)
            }
        }
        publishProgressMirrorActivity()
        mediaLiveController.onVisibilityChanged()
    }

    private fun publishProgressMirrorActivity() {
        val active = candidates.isNotEmpty()
        if (active == progressMirrorActive) return
        progressMirrorActive = active
        mediaLiveController.onProgressMirrorActivityChanged(active)
    }

    private fun onProgressPreferencesChanged() {
        progressPreferences = ProgressPreferences(this)
        progressSnapshotsByKey.clear()
        progressUseSourceIconByKey.clear()
        if (!progressPreferences.enabled) {
            clearProgressMirrors("progress live updates disabled")
            mediaLiveController.onProgressMirrorActivityChanged(false)
            return
        }
        refreshActiveNotifications("progress preferences changed")
    }

    private fun onVisibilityPreferencesChanged() {
        visibilityPreferences = VisibilityPreferences(this)
        reconcileVisibility()
    }

    private fun applyVisibility(candidate: MirrorCandidate) {
        if (!VisibilityState.shouldShowMirror(
                this,
                visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded
            )
        ) {
            OriginalSuppressionController.restoreAll(this, "mirror hidden while quick settings is expanded")
            notificationManager.cancel(candidate.notificationId)
            clearProgressSnapshot(candidate.key)
            noteMirrorVisibility(
                candidate,
                "hidden:qs",
                "Mirror hidden while quick settings is expanded for ${candidate.appLabel}"
            )
            return
        }

        if (VisibilityState.screenOff && !progressPreferences.showOnAod) {
            OriginalSuppressionController.onMirrorHidden(
                this,
                candidate,
                "progress AOD disabled"
            )
            notificationManager.cancel(candidate.notificationId)
            clearProgressSnapshot(candidate.key)
            noteMirrorVisibility(
                candidate,
                "hidden:aod_disabled",
                "Mirror hidden on AOD for ${candidate.appLabel}; progress AOD disabled"
            )
            return
        }

        if (
            VisibilityState.locked &&
            !VisibilityState.screenOff &&
            !progressPreferences.showOnLockScreen
        ) {
            OriginalSuppressionController.onMirrorHidden(
                this,
                candidate,
                "progress lock screen mirror disabled"
            )
            notificationManager.cancel(candidate.notificationId)
            clearProgressSnapshot(candidate.key)
            noteMirrorVisibility(
                candidate,
                "hidden:lock_original",
                "Mirror hidden on lock screen for ${candidate.appLabel}; original selected"
            )
            return
        }

        val shouldSuppressOriginal = VisibilityState.locked &&
            progressPreferences.suppressOriginalNotification &&
            PrivilegedAccess.canUseOriginalNotificationSuppression(this)
        val priorityMode = MirrorPriorityPolicy.forSurface(
            locked = VisibilityState.locked,
            screenOff = VisibilityState.screenOff
        )
        val postResult = postMirrorNotification(
            candidate = candidate,
            shouldSuppressOriginal = shouldSuppressOriginal,
            priorityMode = priorityMode
        ) ?: return
        val promotedStatus = if (postResult.notification != null) {
            promotedStatus(postResult.notification)
        } else {
            "Unchanged; repost skipped."
        }
        val shownState = "shown:${VisibilityState.locked}:${VisibilityState.screenOff}:$shouldSuppressOriginal"
        val visibilityChanged = noteMirrorVisibility(
            candidate,
            shownState,
            "Mirror shown for ${candidate.appLabel}${candidate.progress.diagnosticSuffix()}. $promotedStatus"
        )

        if (shouldSuppressOriginal) {
            OriginalSuppressionController.onLockedMirrorShown(this, candidate)
        } else if (visibilityChanged) {
            OriginalSuppressionController.restoreAll(this, "progress original should remain visible")
        }
    }

    private fun postMirrorNotification(
        candidate: MirrorCandidate,
        shouldSuppressOriginal: Boolean,
        priorityMode: MirrorPriorityMode
    ): ProgressPostResult? {
        val useSourceIcon = progressUseSourceIconByKey[candidate.key] ?: true
        val snapshot = ProgressMirrorSnapshot.from(
            candidate = candidate,
            locked = VisibilityState.locked,
            screenOff = VisibilityState.screenOff,
            shouldSuppressOriginal = shouldSuppressOriginal,
            useSourceSmallIcon = useSourceIcon,
            priorityMode = priorityMode
        )
        if (progressSnapshotsByKey[candidate.key] == snapshot) {
            BatteryDiagnostics.increment(BatteryDiagnostics.Counter.PROGRESS_SKIPPED_REPOSTS)
            return ProgressPostResult(notification = null)
        }

        val notification = MirrorNotificationBuilder.build(
            context = this,
            candidate = candidate,
            aodVisible = VisibilityState.screenOff,
            useSourceSmallIcon = useSourceIcon,
            priorityMode = priorityMode
        )
        return try {
            notificationManager.notify(candidate.notificationId, notification)
            progressSnapshotsByKey[candidate.key] = snapshot
            BatteryDiagnostics.increment(BatteryDiagnostics.Counter.PROGRESS_REPOSTS)
            ProgressPostResult(notification = notification)
        } catch (error: RuntimeException) {
            if (!useSourceIcon) {
                AppDiagnostics.note(
                    this,
                    "mirror",
                    "Mirror post failed for ${candidate.appLabel}: ${error.shortMessage()}"
                )
                return null
            }
            AppDiagnostics.note(
                this,
                "mirror",
                "Mirror post failed for ${candidate.appLabel}; retrying without source icon: ${error.shortMessage()}"
            )
            progressUseSourceIconByKey[candidate.key] = false
            val fallbackSnapshot = ProgressMirrorSnapshot.from(
                candidate = candidate,
                locked = VisibilityState.locked,
                screenOff = VisibilityState.screenOff,
                shouldSuppressOriginal = shouldSuppressOriginal,
                useSourceSmallIcon = false,
                priorityMode = priorityMode
            )
            if (progressSnapshotsByKey[candidate.key] == fallbackSnapshot) {
                BatteryDiagnostics.increment(BatteryDiagnostics.Counter.PROGRESS_SKIPPED_REPOSTS)
                return ProgressPostResult(notification = null)
            }
            val fallback = MirrorNotificationBuilder.build(
                context = this,
                candidate = candidate,
                aodVisible = VisibilityState.screenOff,
                useSourceSmallIcon = false,
                priorityMode = priorityMode
            )
            try {
                notificationManager.notify(candidate.notificationId, fallback)
                progressSnapshotsByKey[candidate.key] = fallbackSnapshot
                BatteryDiagnostics.increment(BatteryDiagnostics.Counter.PROGRESS_REPOSTS)
                ProgressPostResult(notification = fallback)
            } catch (fallbackError: RuntimeException) {
                AppDiagnostics.note(
                    this,
                    "mirror",
                    "Mirror post failed for ${candidate.appLabel}: ${fallbackError.shortMessage()}"
                )
                null
            }
        }
    }

    private fun clearProgressSnapshot(key: String) {
        progressSnapshotsByKey.remove(key)
        progressUseSourceIconByKey.remove(key)
    }

    private fun noteMirrorVisibility(
        candidate: MirrorCandidate,
        state: String,
        message: String
    ): Boolean {
        if (mirrorVisibilityByKey[candidate.key] == state) return false
        mirrorVisibilityByKey[candidate.key] = state
        AppDiagnostics.note(this, "mirror", message)
        return true
    }

    private fun promotedStatus(notification: android.app.Notification): String {
        return when {
            !notificationManager.canPostPromotedNotifications() ->
                "Promoted permission disabled; open promoted notification settings."

            !notification.hasPromotableCharacteristics() ->
                "Posted, but Android says it is not promotable."

            else -> "Promoted ongoing requested and eligible."
        }
    }

    private fun RuntimeException.shortMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    private fun activeNotificationsSnapshot(reason: String): Array<StatusBarNotification> {
        return try {
            activeNotifications ?: emptyArray()
        } catch (error: SecurityException) {
            AppDiagnostics.note(
                this,
                "listener",
                "Unable to read active notifications; reason=$reason; ${error.shortMessage()}"
            )
            emptyArray()
        }
    }

    private fun SecurityException.shortMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    private data class ProgressPostResult(
        val notification: android.app.Notification?
    )

    private fun ProgressInfo.diagnosticSuffix(): String {
        return shortText.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    }
}
