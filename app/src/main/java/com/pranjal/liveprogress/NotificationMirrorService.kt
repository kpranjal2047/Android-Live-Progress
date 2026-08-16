package com.pranjal.liveprogress

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

class NotificationMirrorService : NotificationListenerService() {
    private val candidates = linkedMapOf<String, MirrorCandidate>()
    private val dismissedProgressKeys = mutableSetOf<String>()
    private val retainedAfterSourceRemovedKeys = mutableSetOf<String>()
    private val mirrorVisibilityByKey = mutableMapOf<String, String>()
    private val progressSnapshotsByKey = mutableMapOf<String, ProgressMirrorSnapshot>()
    private val progressUseSourceIconByKey = mutableMapOf<String, Boolean>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val uberExtractionExecutor = Executors.newSingleThreadExecutor()
    private val uberExtractionVersions = mutableMapOf<String, Int>()
    private val visibilityListener = { reconcileVisibility() }
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaLiveController: MediaLiveController
    private lateinit var progressPreferences: ProgressPreferences
    private lateinit var categoryPreferences: NotificationCategoryPreferences
    private lateinit var visibilityPreferences: VisibilityPreferences
    private var progressMirrorActive = false
    private var lastRefreshUptimeMs = 0L
    private var nextUberExtractionVersion = 0
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

        fun dismissProgressMirror(
            context: Context,
            key: String,
            notificationId: Int
        ) {
            val service = activeService
            if (service != null) {
                service.dismissProgressMirror(key, notificationId)
                return
            }

            context.getSystemService(NotificationManager::class.java).cancel(notificationId)
            AppDiagnostics.note(context, "mirror", "Progress mirror dismissed by user")
        }

        fun dismissMediaMirror(context: Context) {
            val service = activeService
            if (service != null) {
                service.mediaLiveController.dismissByUser()
                return
            }

            context.getSystemService(NotificationManager::class.java)
                .cancel(MediaLiveNotificationBuilder.NOTIFICATION_ID)
            AppDiagnostics.note(context, "media", "Media live notification dismissed by user")
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
        uberExtractionVersions.clear()
        mainHandler.removeCallbacksAndMessages(null)
        uberExtractionExecutor.shutdownNow()
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
            if (shouldExtractUber(sbn)) {
                previous[sbn.key]?.let { refreshed[sbn.key] = it }
                scheduleUberExtraction(sbn, "active notification refresh")
            } else {
                mediaLiveController.onNotificationPosted(sbn)
                classifyCandidate(sbn)
                    ?.takeUnless { it.key in dismissedProgressKeys }
                    ?.let { candidate ->
                        removeRetainedMirrorsForReplacement(candidate)
                        retainedAfterSourceRemovedKeys.remove(candidate.key)
                        refreshed[candidate.key] = candidate
                    }
            }
        }
        previous.values
            .filter { it.key !in refreshed }
            .forEach { removed ->
                val retained = retainMirrorAfterSourceRemoved(
                    candidate = removed,
                    reason = "source notification no longer eligible",
                    allowNewRetention = false
                )
                if (retained != null) {
                    refreshed[retained.key] = retained
                } else {
                    OriginalSuppressionController.onMirrorHidden(this, removed, "source notification no longer eligible")
                    notificationManager.cancel(removed.notificationId)
                    retainedAfterSourceRemovedKeys.remove(removed.key)
                    mirrorVisibilityByKey.remove(removed.key)
                    clearProgressSnapshot(removed.key)
                    AppDiagnostics.verbose(
                        this,
                        "mirror",
                        "Progress mirror removed during refresh; app=${removed.appLabel}; reason=source notification no longer eligible"
                    )
                }
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
        if (shouldExtractUber(sbn)) {
            scheduleUberExtraction(sbn, "notification posted")
            return
        }
        mediaLiveController.onNotificationPosted(sbn)
        applyClassifiedCandidate(sbn, classifyCandidate(sbn))
    }

    private fun applyClassifiedCandidate(
        sbn: StatusBarNotification,
        candidate: MirrorCandidate?
    ) {
        if (candidate == null) {
            removeMirrorFor(sbn)
            return
        }
        if (candidate.key in dismissedProgressKeys) {
            removeVisibleMirrorForDismissedCandidate(candidate)
            return
        }

        removeRetainedMirrorsForReplacement(candidate)
        retainedAfterSourceRemovedKeys.remove(candidate.key)
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
        uberExtractionVersions.remove(sbn.key)
        dismissedProgressKeys.remove(sbn.key)
        val removed = candidates[sbn.key] ?: return
        val retained = retainMirrorAfterSourceRemoved(
            candidate = removed,
            reason = "source notification removed",
            allowNewRetention = true
        )
        if (retained != null) {
            candidates[sbn.key] = retained
            AppDiagnostics.note(this, "mirror", "Retained mirror for ${retained.appLabel} after original notification was dismissed")
            publishProgressMirrorActivity()
            applyVisibility(retained)
            return
        }

        candidates.remove(sbn.key)
        OriginalSuppressionController.onMirrorHidden(this, removed, "source notification removed")
        notificationManager.cancel(removed.notificationId)
        retainedAfterSourceRemovedKeys.remove(removed.key)
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
                val retained = retainMirrorAfterSourceRemoved(
                    candidate = entry.value,
                    reason = "source notification no longer active",
                    allowNewRetention = true
                )
                if (retained != null) {
                    entry.setValue(retained)
                    applyVisibility(retained)
                } else {
                    OriginalSuppressionController.onMirrorHidden(
                        this,
                        entry.value,
                        "source notification no longer active"
                    )
                    notificationManager.cancel(entry.value.notificationId)
                    retainedAfterSourceRemovedKeys.remove(entry.key)
                    mirrorVisibilityByKey.remove(entry.key)
                    clearProgressSnapshot(entry.key)
                    AppDiagnostics.verbose(
                        this,
                        "mirror",
                        "Progress mirror removed during visibility reconcile; app=${entry.value.appLabel}; reason=source notification no longer active"
                    )
                    iterator.remove()
                }
            } else {
                applyVisibility(entry.value)
            }
        }
        publishProgressMirrorActivity()
        mediaLiveController.onVisibilityChanged()
    }

    private fun dismissProgressMirror(
        key: String,
        notificationId: Int
    ) {
        dismissedProgressKeys.add(key)
        retainedAfterSourceRemovedKeys.remove(key)
        val removed = candidates.remove(key)
        if (removed != null) {
            OriginalSuppressionController.onMirrorHidden(this, removed, "progress mirror dismissed by user")
            notificationManager.cancel(removed.notificationId)
            mirrorVisibilityByKey.remove(key)
            clearProgressSnapshot(key)
        } else {
            OriginalSuppressionController.restoreAll(this, "progress mirror dismissed by user")
            notificationManager.cancel(notificationId)
        }
        AppDiagnostics.note(this, "mirror", "Progress mirror dismissed by user")
        publishProgressMirrorActivity()
        mediaLiveController.onVisibilityChanged()
    }

    private fun removeVisibleMirrorForDismissedCandidate(candidate: MirrorCandidate) {
        val removed = candidates.remove(candidate.key)
        OriginalSuppressionController.onMirrorHidden(
            this,
            removed ?: candidate,
            "progress mirror dismissed by user"
        )
        notificationManager.cancel(candidate.notificationId)
        retainedAfterSourceRemovedKeys.remove(candidate.key)
        mirrorVisibilityByKey.remove(candidate.key)
        clearProgressSnapshot(candidate.key)
        publishProgressMirrorActivity()
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Progress mirror remains dismissed; app=${candidate.appLabel}"
        )
    }

    private fun retainMirrorAfterSourceRemoved(
        candidate: MirrorCandidate,
        reason: String,
        allowNewRetention: Boolean
    ): MirrorCandidate? {
        if (!allowNewRetention && candidate.key !in retainedAfterSourceRemovedKeys) return null
        val retained = retainedCandidateOrNull(candidate) ?: run {
            retainedAfterSourceRemovedKeys.remove(candidate.key)
            return null
        }
        val wasRetained = retained.key in retainedAfterSourceRemovedKeys
        val settingsChanged = retained.displaySettings != candidate.displaySettings
        retainedAfterSourceRemovedKeys.add(retained.key)
        if (!wasRetained || settingsChanged) {
            OriginalSuppressionController.onMirrorHidden(this, retained, "$reason; retaining mirror")
            clearProgressSnapshot(retained.key)
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Progress mirror retained after source removal; app=${retained.appLabel}; reason=$reason"
            )
        }
        return retained
    }

    private fun retainedCandidateOrNull(candidate: MirrorCandidate): MirrorCandidate? {
        if (candidate.displaySettings.source != MirrorCandidateSource.ADDITIONAL) return null
        val settings = additionalCategorySettings(
            packageName = candidate.packageName,
            uid = candidate.sourceUid,
            channelId = candidate.channelId
        )
        if (!settings.enabled || !settings.keepAfterOriginalDismissed) return null
        return candidate.copy(
            displaySettings = MirrorCandidateDisplaySettings(
                source = MirrorCandidateSource.ADDITIONAL,
                showOnAod = settings.showOnAod,
                showOnLockScreen = settings.showOnLockScreen,
                hideOriginalNotification = settings.hideOriginalNotification,
                keepAfterOriginalDismissed = settings.keepAfterOriginalDismissed
            )
        )
    }

    private fun removeRetainedMirrorsForReplacement(candidate: MirrorCandidate) {
        val removedKeys = candidates
            .filter { (key, retained) ->
                key in retainedAfterSourceRemovedKeys &&
                    key != candidate.key &&
                    retained.sameSourceCategory(candidate)
            }
            .keys
            .toList()
        removedKeys.forEach { key ->
            val removed = candidates.remove(key) ?: return@forEach
            retainedAfterSourceRemovedKeys.remove(key)
            OriginalSuppressionController.onMirrorHidden(
                this,
                removed,
                "new notification replaced retained mirror"
            )
            notificationManager.cancel(removed.notificationId)
            mirrorVisibilityByKey.remove(key)
            clearProgressSnapshot(key)
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Retained mirror replaced; old=${removed.appLabel}; new=${candidate.appLabel}"
            )
        }
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
        dismissedProgressKeys.clear()
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
        dismissedProgressKeys.clear()
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
            "Visibility preferences reloaded; hideQuickSettings=${visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded}; hideForegroundPill=${visibilityPreferences.hideStatusBarPillWhenSourceAppForeground}"
        )
        reconcileVisibility()
    }

    private fun applyVisibility(candidate: MirrorCandidate) {
        VisibilityState.refreshLockState(this)
        val displaySettings = candidate.displaySettings
        val retainedAfterSourceRemoval = candidate.key in retainedAfterSourceRemovedKeys
        val sourceAppInForeground = VisibilityState.isSourcePackageInForeground(candidate.packageName)
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Progress visibility evaluated; app=${candidate.appLabel}; source=${displaySettings.source}; retained=$retainedAfterSourceRemoval; locked=${VisibilityState.locked}; screenOff=${VisibilityState.screenOff}; quickSettings=${VisibilityState.quickSettingsExpanded}; hideQuickSettings=${visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded}; hideForegroundPill=${visibilityPreferences.hideStatusBarPillWhenSourceAppForeground}; sourceForeground=$sourceAppInForeground; showAod=${displaySettings.showOnAod}; showLock=${displaySettings.showOnLockScreen}; hideOriginal=${displaySettings.hideOriginalNotification}; keepAfterDismiss=${displaySettings.keepAfterOriginalDismissed}"
        )
        if (!MirrorVisibilityPolicy.shouldShow(
                locked = VisibilityState.locked,
                quickSettingsExpanded = VisibilityState.quickSettingsExpanded,
                hideWhenQuickSettingsExpanded = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded,
                hideWhenSourceAppInForeground = visibilityPreferences.hideStatusBarPillWhenSourceAppForeground,
                sourceAppInForeground = sourceAppInForeground
            )
        ) {
            val hiddenForForeground = sourceAppInForeground &&
                visibilityPreferences.hideStatusBarPillWhenSourceAppForeground
            val hiddenState = if (hiddenForForeground) "hidden:foreground" else "hidden:qs"
            val hiddenReason = if (hiddenForForeground) {
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
            MirrorRetentionPolicy.shouldUseOriginalSuppression(
                candidate = candidate,
                retainedAfterSourceRemoval = retainedAfterSourceRemoval
            ) &&
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
        val shownState = "shown:${VisibilityState.locked}:${VisibilityState.screenOff}:$shouldSuppressOriginal:$retainedAfterSourceRemoval"
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

    private fun shouldExtractUber(sbn: StatusBarNotification): Boolean {
        return progressPreferences.enabled && UberNotificationSupport.isUber(sbn)
    }

    private fun scheduleUberExtraction(
        sbn: StatusBarNotification,
        reason: String
    ) {
        val version = ++nextUberExtractionVersion
        uberExtractionVersions[sbn.key] = version
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Uber extraction queued; ${sbn.debugIdentity()}; version=$version; reason=$reason"
        )
        uberExtractionExecutor.execute {
            val result = UberNotificationSupport.extract(this, sbn)
            mainHandler.post {
                handleUberExtractionResult(sbn, version, result)
            }
        }
    }

    private fun handleUberExtractionResult(
        source: StatusBarNotification,
        version: Int,
        extraction: UberExtractionResult
    ) {
        if (uberExtractionVersions[source.key] != version) return
        val active = activeNotificationsSnapshot("Uber extraction result")
            .firstOrNull { it.key == source.key }
            ?: run {
                uberExtractionVersions.remove(source.key)
                return
            }
        if (!progressPreferences.enabled || !UberNotificationSupport.isUber(active)) {
            uberExtractionVersions.remove(source.key)
            return
        }
        uberExtractionVersions.remove(source.key)
        val additionalSettings = additionalCategorySettings(
            packageName = active.packageName,
            uid = active.uid,
            channelId = active.notification.channelId
        )
        val route = UberNotificationRouting.decide(
            progressEnabled = progressPreferences.enabled,
            hasCustomCandidate = extraction is UberExtractionResult.Extracted,
            allowNativeProgress = extraction is UberExtractionResult.NotUberRichNotification,
            hasNativeProgress = NotificationClassifier.standardProgressInfo(active.notification) != null,
            additionalEnabled = additionalSettings.enabled
        )
        val candidate = when (route) {
            UberMirrorRoute.CUSTOM_PROGRESS -> {
                val data = (extraction as UberExtractionResult.Extracted).data
                NotificationClassifier.toUberCandidate(
                    context = this,
                    sbn = active,
                    data = data,
                    progressDisplaySettings = progressDisplaySettings()
                )
            }

            UberMirrorRoute.NATIVE_PROGRESS -> {
                classifyCandidate(active, allowStandardProgress = true)
            }

            UberMirrorRoute.ADDITIONAL -> {
                classifyCandidate(active, allowStandardProgress = false)
            }

            UberMirrorRoute.NONE -> null
        }
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Uber extraction completed; ${active.debugIdentity()}; result=${extraction.javaClass.simpleName}; route=$route; candidate=${candidate?.displaySettings?.source ?: "none"}"
        )
        applyClassifiedCandidate(active, candidate)
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

    private fun classifyCandidate(
        sbn: StatusBarNotification,
        allowStandardProgress: Boolean = true
    ): MirrorCandidate? {
        var classification = "no_result"
        val candidate = NotificationClassifier.toCandidate(
            context = this,
            sbn = sbn,
            progressEnabled = progressPreferences.enabled,
            progressDisplaySettings = progressDisplaySettings(),
            allowStandardProgress = allowStandardProgress,
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

    private fun MirrorCandidate.sameSourceCategory(other: MirrorCandidate): Boolean {
        return packageName == other.packageName &&
            sourceUid == other.sourceUid &&
            channelId == other.channelId
    }

    private fun StatusBarNotification.debugIdentity(): String {
        val channel = notification?.channelId.orEmpty()
        val tagText = tag ?: "none"
        return "pkg=$packageName; uid=$uid; id=$id; tag=$tagText; channel=$channel"
    }
}
