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
    private lateinit var categoryPreferences: NotificationCategoryPreferences
    private lateinit var visibilityPreferences: VisibilityPreferences
    private var progressMirrorActive = false
    private var lastRefreshUptimeMs = 0L
    private val progressPreferenceListener = { onProgressPreferencesChanged() }
    private val additionalPreferenceListener = { onAdditionalPreferencesChanged() }
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
        categoryPreferences = NotificationCategoryPreferences(this)
        visibilityPreferences = VisibilityPreferences(this)
        MirrorNotificationBuilder.ensureChannel(this)
        mediaLiveController = MediaLiveController(this, notificationManager)
        mediaLiveController.initialize()
        ProgressPreferenceEvents.addListener(progressPreferenceListener)
        AdditionalNotificationPreferenceEvents.addListener(additionalPreferenceListener)
        VisibilityPreferenceEvents.addListener(visibilityPreferenceListener)
        VisibilityState.register(this)
        VisibilityState.addListener(visibilityListener)
        AppDiagnostics.verbose(this, "listener", "Notification mirror service created")
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        mediaLiveController.destroy()
        ProgressPreferenceEvents.removeListener(progressPreferenceListener)
        AdditionalNotificationPreferenceEvents.removeListener(additionalPreferenceListener)
        VisibilityPreferenceEvents.removeListener(visibilityPreferenceListener)
        OriginalSuppressionController.restoreAll(this, "notification listener destroyed")
        VisibilityState.removeListener(visibilityListener)
        AppDiagnostics.verbose(this, "listener", "Notification mirror service destroyed")
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
        handlePosted(sbn, rankingMap = null)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {
        handlePosted(sbn, rankingMap)
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
        val rankingMap = currentRankingMap(reason)
        AppDiagnostics.note(
            this,
            "listener",
            "Refreshing ${active.size} active notifications; progress enabled=${progressPreferences.enabled}; reason=$reason"
        )
        val previous = candidates.toMap()
        val refreshed = linkedMapOf<String, MirrorCandidate>()
        active.forEach { sbn ->
            observeNotificationCategory(sbn, rankingMap)
            mediaLiveController.onNotificationPosted(sbn)
            classifyCandidate(sbn)?.let { refreshed[it.key] = it }
        }
        previous.values
            .filter { it.key !in refreshed }
            .forEach { removed ->
                OriginalSuppressionController.onMirrorHidden(this, removed, "source notification no longer eligible")
                notificationManager.cancel(removed.notificationId)
                mirrorVisibilityByKey.remove(removed.key)
                clearProgressSnapshot(removed.key)
                AppDiagnostics.verbose(
                    this,
                    "mirror",
                    "Progress mirror removed during refresh; app=${removed.appLabel}; reason=source notification no longer eligible"
                )
            }
        candidates.clear()
        candidates.putAll(refreshed)
        if (candidates.isEmpty()) {
            publishProgressMirrorActivity()
            mediaLiveController.onVisibilityChanged()
        }
        AppDiagnostics.note(
            this,
            "listener",
            "Tracking ${candidates.size} progress mirrors after refresh"
        )
        reconcileVisibility()
    }

    private fun handlePosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?
    ) {
        observeNotificationCategory(sbn, rankingMap)
        mediaLiveController.onNotificationPosted(sbn)
        val candidate = classifyCandidate(sbn)
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
                AppDiagnostics.verbose(
                    this,
                    "mirror",
                    "Progress mirror removed during visibility reconcile; app=${entry.value.appLabel}; reason=source notification no longer active"
                )
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
        AppDiagnostics.verbose(this, "mirror", "Progress mirror active=$active; count=${candidates.size}")
        mediaLiveController.onProgressMirrorActivityChanged(active)
    }

    private fun onProgressPreferencesChanged() {
        progressPreferences = ProgressPreferences(this)
        progressSnapshotsByKey.clear()
        progressUseSourceIconByKey.clear()
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Progress preferences reloaded; enabled=${progressPreferences.enabled}; aod=${progressPreferences.showOnAod}; lock=${progressPreferences.showOnLockScreen}; hideOriginal=${progressPreferences.suppressOriginalNotification}"
        )
        refreshActiveNotifications("progress preferences changed")
    }

    private fun onAdditionalPreferencesChanged() {
        categoryPreferences = NotificationCategoryPreferences(this)
        progressSnapshotsByKey.clear()
        progressUseSourceIconByKey.clear()
        AppDiagnostics.verbose(this, "mirror", "Additional notification preferences reloaded")
        refreshActiveNotifications("additional notification preferences changed")
        mediaLiveController.onPreferencesChanged()
    }

    private fun onVisibilityPreferencesChanged() {
        visibilityPreferences = VisibilityPreferences(this)
        AppDiagnostics.verbose(
            this,
            "visibility",
            "Visibility preferences reloaded; hideQuickSettings=${visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded}"
        )
        reconcileVisibility()
    }

    private fun applyVisibility(candidate: MirrorCandidate) {
        VisibilityState.refreshLockState(this)
        val displaySettings = candidate.displaySettings
        val sourceAppInForeground = VisibilityState.isSourcePackageInForeground(candidate.packageName)
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Progress visibility evaluated; app=${candidate.appLabel}; source=${displaySettings.source}; locked=${VisibilityState.locked}; screenOff=${VisibilityState.screenOff}; quickSettings=${VisibilityState.quickSettingsExpanded}; hideQuickSettings=${visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded}; sourceForeground=$sourceAppInForeground; showAod=${displaySettings.showOnAod}; showLock=${displaySettings.showOnLockScreen}; hideOriginal=${displaySettings.hideOriginalNotification}"
        )
        if (!MirrorVisibilityPolicy.shouldShow(
                locked = VisibilityState.locked,
                quickSettingsExpanded = VisibilityState.quickSettingsExpanded,
                hideWhenQuickSettingsExpanded = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded,
                sourceAppInForeground = sourceAppInForeground
            )
        ) {
            val hiddenState = if (sourceAppInForeground) "hidden:foreground" else "hidden:qs"
            val hiddenReason = if (sourceAppInForeground) {
                "source app is foreground"
            } else {
                "quick settings is expanded"
            }
            OriginalSuppressionController.restoreAll(this, "mirror hidden while $hiddenReason")
            notificationManager.cancel(candidate.notificationId)
            clearProgressSnapshot(candidate.key)
            noteMirrorVisibility(
                candidate,
                hiddenState,
                "Mirror hidden while $hiddenReason for ${candidate.appLabel}"
            )
            return
        }

        if (VisibilityState.screenOff && !displaySettings.showOnAod) {
            OriginalSuppressionController.onMirrorHidden(
                this,
                candidate,
                "${displaySettings.diagnosticName()} AOD disabled"
            )
            notificationManager.cancel(candidate.notificationId)
            clearProgressSnapshot(candidate.key)
            noteMirrorVisibility(
                candidate,
                "hidden:aod_disabled",
                "Mirror hidden on AOD for ${candidate.appLabel}; ${displaySettings.diagnosticName()} AOD disabled"
            )
            return
        }

        if (
            VisibilityState.locked &&
            !VisibilityState.screenOff &&
            !displaySettings.showOnLockScreen
        ) {
            OriginalSuppressionController.onMirrorHidden(
                this,
                candidate,
                "${displaySettings.diagnosticName()} lock screen mirror disabled"
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
            displaySettings.hideOriginalNotification &&
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
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Progress repost skipped; unchanged snapshot for ${candidate.appLabel}; priority=$priorityMode; suppressOriginal=$shouldSuppressOriginal"
            )
            return ProgressPostResult(notification = null)
        }

        val notification = MirrorNotificationBuilder.build(
            context = this,
            candidate = candidate,
            useSourceSmallIcon = useSourceIcon,
            priorityMode = priorityMode
        )
        return try {
            notificationManager.notify(candidate.notificationId, notification)
            progressSnapshotsByKey[candidate.key] = snapshot
            BatteryDiagnostics.increment(BatteryDiagnostics.Counter.PROGRESS_REPOSTS)
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Progress notification posted; app=${candidate.appLabel}; priority=$priorityMode; sourceIcon=$useSourceIcon; suppressOriginal=$shouldSuppressOriginal"
            )
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
                AppDiagnostics.verbose(
                    this,
                    "mirror",
                    "Progress fallback repost skipped; unchanged snapshot for ${candidate.appLabel}; priority=$priorityMode"
                )
                return ProgressPostResult(notification = null)
            }
            val fallback = MirrorNotificationBuilder.build(
                context = this,
                candidate = candidate,
                useSourceSmallIcon = false,
                priorityMode = priorityMode
            )
            try {
                notificationManager.notify(candidate.notificationId, fallback)
                progressSnapshotsByKey[candidate.key] = fallbackSnapshot
                BatteryDiagnostics.increment(BatteryDiagnostics.Counter.PROGRESS_REPOSTS)
                AppDiagnostics.verbose(
                    this,
                    "mirror",
                    "Progress notification posted with fallback icon; app=${candidate.appLabel}; priority=$priorityMode; suppressOriginal=$shouldSuppressOriginal"
                )
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

    private fun currentRankingMap(reason: String): RankingMap? {
        return try {
            currentRanking
        } catch (error: SecurityException) {
            AppDiagnostics.note(
                this,
                "listener",
                "Unable to read notification ranking; reason=$reason; ${error.shortMessage()}"
            )
            null
        }
    }

    private fun observeNotificationCategory(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?
    ) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        if (NotificationClassifier.isAlreadyLiveProgress(notification)) return
        val channelId = notification.channelId?.takeIf { it.isNotBlank() } ?: return
        val channelName = channelNameFor(sbn.key, rankingMap)
        val systemApp = AppLabelResolver.isSystemApp(this, sbn.packageName)
        val changed = categoryPreferences.observe(
            packageName = sbn.packageName,
            uid = sbn.uid,
            channelId = channelId,
            appLabel = AppLabelResolver.label(this, sbn.packageName, notification),
            channelName = channelName,
            isSystemApp = systemApp
        )
        if (changed) {
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Observed notification category; ${sbn.debugIdentity()}; name=${channelName.orEmpty()}; systemApp=${systemApp ?: false}"
            )
        }
    }

    private fun classifyCandidate(sbn: StatusBarNotification): MirrorCandidate? {
        var classification = "no_result"
        val candidate = NotificationClassifier.toCandidate(
            context = this,
            sbn = sbn,
            progressEnabled = progressPreferences.enabled,
            progressDisplaySettings = progressDisplaySettings(),
            additionalCategorySettings = ::additionalCategorySettings,
            debug = { classification = it }
        )
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Progress classification; ${sbn.debugIdentity()}; $classification"
        )
        return candidate
    }

    private fun channelNameFor(
        key: String,
        rankingMap: RankingMap?
    ): String? {
        val ranking = Ranking()
        val channel = if (rankingMap?.getRanking(key, ranking) == true) {
            ranking.channel
        } else {
            null
        }
        return channel?.name?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun additionalCategorySettings(
        packageName: String,
        uid: Int,
        channelId: String?
    ): NotificationCategorySettings {
        return categoryPreferences.settingsFor(packageName, uid, channelId)
    }

    private fun progressDisplaySettings(): MirrorCandidateDisplaySettings {
        return MirrorCandidateDisplaySettings(
            source = MirrorCandidateSource.PROGRESS,
            showOnAod = progressPreferences.showOnAod,
            showOnLockScreen = progressPreferences.showOnLockScreen,
            hideOriginalNotification = progressPreferences.suppressOriginalNotification
        )
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

    private fun MirrorCandidateDisplaySettings.diagnosticName(): String {
        return when (source) {
            MirrorCandidateSource.PROGRESS -> "progress"
            MirrorCandidateSource.ADDITIONAL -> "additional notification"
        }
    }

    private fun StatusBarNotification.debugIdentity(): String {
        val channel = notification?.channelId.orEmpty()
        val tagText = tag ?: "none"
        return "pkg=$packageName; uid=$uid; id=$id; tag=$tagText; channel=$channel"
    }
}
