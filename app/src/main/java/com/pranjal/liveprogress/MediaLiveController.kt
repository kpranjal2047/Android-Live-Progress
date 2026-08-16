package com.pranjal.liveprogress

import android.app.NotificationManager
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

class MediaLiveController(
    private val service: NotificationMirrorService,
    private val notificationManager: NotificationManager
) {
    private val preferences = MediaPreferences(service)
    private val categoryPreferences = NotificationCategoryPreferences(service)
    private val visibilityPreferences = VisibilityPreferences(service)
    private val mediaSessionManager = service.getSystemService(MediaSessionManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val buildExecutor = Executors.newSingleThreadExecutor()
    private val buildCoalescer = MediaBuildCoalescer<MediaBuildRequest>()
    private val mediaComponent = ComponentName(service, NotificationMirrorService::class.java)

    private var activeController: MediaController? = null
    private var cachedControllers: List<MediaController> = emptyList()
    private var activeState: MediaState? = null
    private var activeSource: MediaNotificationSource? = null
    private var suppressedSource: OriginalNotificationSource? = null
    private var notificationDismissed = false
    private var programmaticCancelPending = false
    private var progressMirrorActive = false
    private var titleStartTime = 0L
    private var lastTitle: String? = null
    private var buildVersion = 0
    private var mediaMirrorPosted = false
    private var lastHiddenReason: String? = null
    private var lastPostedSnapshot: MediaNotificationSnapshot? = null
    private var pendingSnapshot: MediaNotificationSnapshot? = null
    private var lastMissingSuppressionLogKey: String? = null
    private var activeSessionsListenerRegistered = false
    private val observedAppLabels = mutableMapOf<String, String>()
    private val preferenceListener = { onPreferencesChanged() }
    private val additionalPreferenceListener = { onPreferencesChanged() }
    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            cachedControllers = controllers.orEmpty()
            AppDiagnostics.verbose(
                service,
                "media",
                "Active media sessions changed; count=${cachedControllers.size}"
            )
            selectController(activeSource?.original?.packageName)
            updateFromController()
        }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            updateFromController()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateFromController()
        }
    }

    private val updateRunnable = Runnable { updateFromController() }

    fun initialize() {
        MediaLiveNotificationBuilder.ensureChannel(service)
        MediaPreferenceEvents.addListener(preferenceListener)
        AdditionalNotificationPreferenceEvents.addListener(additionalPreferenceListener)
        registerActiveSessionsListener()
        refreshController(
            explicitRefresh = true,
            reason = "media controller initialized"
        )
        AppDiagnostics.verbose(service, "media", "Media live controller initialized")
    }

    fun destroy() {
        mainHandler.removeCallbacks(updateRunnable)
        MediaPreferenceEvents.removeListener(preferenceListener)
        AdditionalNotificationPreferenceEvents.removeListener(additionalPreferenceListener)
        unregisterActiveSessionsListener()
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
        buildCoalescer.cancelQueued()
        releaseSuppressedSource("media controller destroyed")
        buildExecutor.shutdownNow()
        AppDiagnostics.verbose(service, "media", "Media live controller destroyed")
    }

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = MediaNotificationSourceFactory.from(service, sbn) ?: return
        val sourcePackageChanged = activeSource?.original?.packageName != source.original.packageName
        if (sourcePackageChanged) {
            notificationDismissed = false
        }
        activeSource = source
        observedAppLabels[source.original.packageName] = source.original.appLabel
        AppDiagnostics.note(service, "media", "Media notification detected from ${source.original.appLabel}")
        refreshController(
            preferredPackage = sbn.packageName,
            sourcePackageChanged = sourcePackageChanged,
            reason = "media notification posted"
        )
        updateFromController()
    }

    fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int) {
        if (sbn.packageName == service.packageName && sbn.id == MediaLiveNotificationBuilder.NOTIFICATION_ID) {
            if (programmaticCancelPending) {
                programmaticCancelPending = false
                return
            }
            if (reason == REASON_CANCEL || reason == REASON_CANCEL_ALL) {
                notificationDismissed = true
                AppDiagnostics.note(service, "media", "Media live notification dismissed by user")
                releaseSuppressedSource("media live notification dismissed")
            }
            return
        }

        if (activeSource?.original?.key == sbn.key) {
            releaseSuppressedSource("source media notification removed")
            activeSource = null
            notificationDismissed = false
            refreshController(
                preferredPackage = null,
                currentControllerInvalid = true,
                reason = "source media notification removed"
            )
            updateFromController()
        }
    }

    fun onVisibilityChanged() {
        updateFromController()
    }

    fun onProgressMirrorActivityChanged(active: Boolean) {
        if (progressMirrorActive == active) return
        progressMirrorActive = active
        AppDiagnostics.verbose(service, "media", "Progress mirror activity changed for media; active=$active")
        updateFromController()
    }

    fun onPreferencesChanged() {
        notificationDismissed = false
        AppDiagnostics.verbose(
            service,
            "media",
            "Media preferences changed; enabled=${preferences.enabled}; aod=${preferences.showOnAod}; lock=${preferences.showOnLockScreen}; pill=${preferences.pillMode}; scroll=${preferences.scrollTitle}"
        )
        updateFromController()
    }

    fun dismissByUser() {
        notificationDismissed = true
        AppDiagnostics.note(service, "media", "Media live notification dismissed by user")
        cancelMedia("media live notification dismissed by user")
    }

    private fun refreshController(
        preferredPackage: String? = activeSource?.original?.packageName,
        sourcePackageChanged: Boolean = false,
        currentControllerInvalid: Boolean = false,
        explicitRefresh: Boolean = false,
        reason: String
    ) {
        val shouldQuery = MediaSessionRefreshPolicy.shouldQuerySessions(
            hasCachedControllers = cachedControllers.isNotEmpty(),
            hasActiveController = activeController != null,
            sourcePackageChanged = sourcePackageChanged,
            currentControllerInvalid = currentControllerInvalid,
            explicitRefresh = explicitRefresh
        )
        AppDiagnostics.verbose(
            service,
            "media",
            "Media session refresh decision; reason=$reason; shouldQuery=$shouldQuery; cached=${cachedControllers.size}; activeController=${activeController?.packageName.orEmpty()}; preferred=${preferredPackage.orEmpty()}; sourceChanged=$sourcePackageChanged; invalid=$currentControllerInvalid; explicit=$explicitRefresh"
        )
        if (shouldQuery) {
            cachedControllers = queryActiveSessions(reason)
        }
        selectController(preferredPackage)
    }

    private fun queryActiveSessions(reason: String): List<MediaController> {
        BatteryDiagnostics.increment(BatteryDiagnostics.Counter.MEDIA_SESSION_SCANS)
        return runCatching {
            mediaSessionManager.getActiveSessions(mediaComponent)
        }.onSuccess {
            AppDiagnostics.verbose(
                service,
                "media",
                "Queried active media sessions; reason=$reason; count=${it.size}"
            )
        }.onFailure {
            AppDiagnostics.note(
                service,
                "media",
                "Unable to query active media sessions for $reason: ${it.shortMessage()}"
            )
        }.getOrDefault(emptyList())
    }

    private fun selectController(preferredPackage: String? = activeSource?.original?.packageName) {
        val newController = cachedControllers.firstOrNull {
            it.packageName == preferredPackage && MediaState.from(
                it.packageName,
                it.metadata,
                it.playbackState
            ) != null
        } ?: cachedControllers.firstOrNull {
            MediaState.from(it.packageName, it.metadata, it.playbackState) != null
        }

        if (newController !== activeController) {
            activeController?.unregisterCallback(mediaCallback)
            activeController = newController
            activeController?.registerCallback(mediaCallback)
            AppDiagnostics.verbose(
                service,
                "media",
                "Active media controller changed; package=${newController?.packageName.orEmpty()}; preferred=${preferredPackage.orEmpty()}"
            )
        }
    }

    private fun updateFromController() {
        if (activeController == null) {
            refreshController(reason = "active controller missing")
        }
        var controller = activeController
        var state = controller?.let {
            MediaState.from(it.packageName, it.metadata, it.playbackState)
        }
        if (state == null && controller != null) {
            refreshController(
                preferredPackage = null,
                currentControllerInvalid = true,
                reason = "active controller invalid"
            )
            controller = activeController
            state = controller?.let {
                MediaState.from(it.packageName, it.metadata, it.playbackState)
            }
        }

        if (state == null) {
            activeState = null
            AppDiagnostics.verbose(service, "media", "No valid media state after controller refresh")
            cancelMedia("no active media")
            return
        }

        if (state.packageName != activeSource?.original?.packageName) {
            AppDiagnostics.verbose(
                service,
                "media",
                "Media source package changed; state=${state.packageName}; source=${activeSource?.original?.packageName.orEmpty()}"
            )
            releaseSuppressedSource("media source package changed")
            activeSource = null
            notificationDismissed = false
        }

        if (state.title != lastTitle) {
            lastTitle = state.title
            titleStartTime = System.currentTimeMillis()
            notificationDismissed = false
            AppDiagnostics.verbose(service, "media", "Media title changed; title=${state.title}; package=${state.packageName}")
        }

        if (notificationDismissed) {
            AppDiagnostics.verbose(service, "media", "Media update ignored because mirrored notification was dismissed")
            cancelMedia("media live notification dismissed")
            return
        }

        activeState = state
        applyVisibility(state)
    }

    private fun applyVisibility(state: MediaState) {
        VisibilityState.refreshLockState(service)
        val additionalSettings = activeSource?.original?.let { source ->
            categoryPreferences.settingsFor(
                packageName = source.packageName,
                uid = source.sourceUid,
                channelId = source.channelId
            )
        } ?: NotificationCategorySettings()
        val additionalForced = !preferences.enabled && additionalSettings.enabled
        val showOnAod = if (additionalForced) additionalSettings.showOnAod else preferences.showOnAod
        val showOnLockScreen = if (additionalForced) {
            additionalSettings.showOnLockScreen
        } else {
            preferences.showOnLockScreen
        }
        val hideOriginal = additionalForced &&
            additionalSettings.hideOriginalNotification &&
            PrivilegedAccess.canUseOriginalNotificationSuppression(service)
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = preferences.enabled || additionalForced,
            hasActiveMedia = true,
            locked = VisibilityState.locked,
            screenOff = VisibilityState.screenOff,
            quickSettingsExpanded = VisibilityState.quickSettingsExpanded,
            hideWhenQuickSettingsExpanded = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded,
            hideWhenSourceAppInForeground =
                visibilityPreferences.hideStatusBarPillWhenSourceAppForeground,
            sourceAppInForeground = VisibilityState.isSourcePackageInForeground(state.packageName),
            progressMirrorActive = progressMirrorActive,
            showOnAod = showOnAod,
            showOnLockScreen = showOnLockScreen,
            hideOriginalNotification = hideOriginal
        )
        AppDiagnostics.verbose(
            service,
            "media",
            "Media visibility evaluated; package=${state.packageName}; enabled=${preferences.enabled}; additionalForced=$additionalForced; locked=${VisibilityState.locked}; screenOff=${VisibilityState.screenOff}; quickSettings=${VisibilityState.quickSettingsExpanded}; hideQuickSettings=${visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded}; hideForegroundPill=${visibilityPreferences.hideStatusBarPillWhenSourceAppForeground}; sourceForeground=${VisibilityState.isSourcePackageInForeground(state.packageName)}; progressActive=$progressMirrorActive; showAod=$showOnAod; showLock=$showOnLockScreen; hideOriginal=$hideOriginal; showMirror=${decision.showMirror}; aod=${decision.aodVisible}; critical=${decision.showShortCriticalText}; suppress=${decision.suppressOriginal}; reason=${decision.reason}"
        )

        if (!decision.showMirror) {
            cancelMedia(decision.reason)
            return
        }

        val source = activeSource
        val appLabel = appLabelFor(state, source)
        val titleElapsedMs = System.currentTimeMillis() - titleStartTime
        val priorityMode = MirrorPriorityPolicy.forSurface(
            locked = VisibilityState.locked,
            screenOff = VisibilityState.screenOff
        )
        val snapshot = MediaNotificationSnapshot.from(
            state = state,
            source = source,
            preferences = preferences,
            appLabel = appLabel,
            titleElapsedMs = titleElapsedMs,
            aodVisible = decision.aodVisible,
            showShortCriticalText = decision.showShortCriticalText,
            priorityMode = priorityMode
        )
        updateOriginalSuppression(decision, source, state)
        if (snapshot == lastPostedSnapshot || snapshot == pendingSnapshot) {
            BatteryDiagnostics.increment(BatteryDiagnostics.Counter.MEDIA_SKIPPED_REPOSTS)
            AppDiagnostics.verbose(
                service,
                "media",
                "Media repost skipped; unchanged snapshot; title=${state.title}; position=${state.positionMs}; duration=${state.durationMs}; reason=${decision.reason}"
            )
            scheduleNext(state, decision)
            return
        }

        val version = nextBuildVersion()
        pendingSnapshot = snapshot
        val request = MediaBuildRequest(
            version = version,
            snapshot = snapshot,
            state = state,
            source = source,
            titleElapsedMs = titleElapsedMs,
            aodVisible = decision.aodVisible,
            showShortCriticalText = decision.showShortCriticalText,
            appLabel = appLabel,
            reason = decision.reason,
            priorityMode = priorityMode
        )
        AppDiagnostics.verbose(
            service,
            "media",
            "Media build queued; version=$version; title=${state.title}; package=${state.packageName}; aod=${decision.aodVisible}; critical=${decision.showShortCriticalText}; priority=$priorityMode; reason=${decision.reason}"
        )
        buildCoalescer.submit(request, ::startMediaBuild)

        scheduleNext(state, decision)
    }

    private fun startMediaBuild(request: MediaBuildRequest) {
        buildExecutor.execute {
            val result = runCatching {
                MediaLiveNotificationBuilder.build(
                    context = service,
                    state = request.state,
                    source = request.source,
                    preferences = preferences,
                    titleElapsedMs = request.titleElapsedMs,
                    aodVisible = request.aodVisible,
                    showShortCriticalText = request.showShortCriticalText,
                    appLabelOverride = request.appLabel,
                    priorityMode = request.priorityMode
                )
            }
            mainHandler.post {
                result
                    .onSuccess { notification ->
                        if (request.version != buildVersion) {
                            if (pendingSnapshot == request.snapshot) pendingSnapshot = null
                            AppDiagnostics.verbose(
                                service,
                                "media",
                                "Media build discarded as stale; request=${request.version}; current=$buildVersion; title=${request.state.title}"
                            )
                        } else {
                            val posted = runCatching {
                                notificationManager.notify(
                                    MediaLiveNotificationBuilder.NOTIFICATION_ID,
                                    notification
                                )
                            }
                            if (posted.isSuccess) {
                                programmaticCancelPending = false
                                pendingSnapshot = null
                                lastPostedSnapshot = request.snapshot
                                mediaMirrorPosted = true
                                lastHiddenReason = null
                                BatteryDiagnostics.increment(BatteryDiagnostics.Counter.MEDIA_REPOSTS)
                                val promotedStatus = promotedStatus(notification)
                                AppDiagnostics.note(
                                    service,
                                    "media",
                                    "Media mirror shown for ${request.state.title}; ${request.reason}. $promotedStatus"
                                )
                                AppDiagnostics.verbose(
                                    service,
                                    "media",
                                    "Media notification posted; title=${request.state.title}; package=${request.state.packageName}; position=${request.state.positionMs}; duration=${request.state.durationMs}; aod=${request.aodVisible}; critical=${request.showShortCriticalText}; priority=${request.priorityMode}"
                                )
                            } else {
                                if (pendingSnapshot == request.snapshot) pendingSnapshot = null
                                AppDiagnostics.note(
                                    service,
                                    "media",
                                    "Media mirror post failed for ${request.state.title}: ${
                                        posted.exceptionOrNull()?.shortMessage().orEmpty()
                                    }"
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        if (pendingSnapshot == request.snapshot) pendingSnapshot = null
                        AppDiagnostics.note(
                            service,
                            "media",
                            "Media mirror build failed for ${request.state.title}: ${error.shortMessage()}"
                        )
                    }
                buildCoalescer.complete(::startMediaBuild)
            }
        }
    }

    private fun registerActiveSessionsListener() {
        if (activeSessionsListenerRegistered) return
        val registered = runCatching {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                mediaComponent,
                mainHandler
            )
        }.isSuccess
        activeSessionsListenerRegistered = registered
        AppDiagnostics.verbose(service, "media", "Active media session listener registered=$registered")
        if (!registered) {
            AppDiagnostics.note(
                service,
                "media",
                "Active media session listener unavailable; using targeted session queries"
            )
        }
    }

    private fun unregisterActiveSessionsListener() {
        if (!activeSessionsListenerRegistered) return
        runCatching {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        }
        activeSessionsListenerRegistered = false
        AppDiagnostics.verbose(service, "media", "Active media session listener unregistered")
    }

    private fun Throwable.shortMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    private data class MediaBuildRequest(
        val version: Int,
        val snapshot: MediaNotificationSnapshot,
        val state: MediaState,
        val source: MediaNotificationSource?,
        val titleElapsedMs: Long,
        val aodVisible: Boolean,
        val showShortCriticalText: Boolean,
        val appLabel: String,
        val reason: String,
        val priorityMode: MirrorPriorityMode
    )

    private fun updateOriginalSuppression(
        decision: MediaVisibilityDecision,
        source: MediaNotificationSource?,
        state: MediaState
    ) {
        if (decision.suppressOriginal) {
            if (source != null) {
                if (suppressedSource?.key != source.original.key) {
                    releaseSuppressedSource("media suppression source changed")
                    OriginalSuppressionController.onLockedSourceShown(
                        service,
                        source.original,
                        "media ${decision.reason}"
                    )
                    suppressedSource = source.original
                }
                lastMissingSuppressionLogKey = null
            } else {
                val logKey = state.packageName
                if (lastMissingSuppressionLogKey != logKey) {
                    AppDiagnostics.note(
                        service,
                        "media",
                        "Media original suppression unavailable; source notification not observed"
                    )
                    lastMissingSuppressionLogKey = logKey
                }
            }
        } else {
            AppDiagnostics.verbose(
                service,
                "media",
                "Media original suppression not needed; reason=${decision.reason}; suppress=${decision.suppressOriginal}"
            )
            releaseSuppressedSource("media original should remain visible")
        }
    }

    private fun appLabelFor(state: MediaState, source: MediaNotificationSource?): String {
        source
            ?.takeIf { it.original.packageName == state.packageName }
            ?.original
            ?.appLabel
            ?.takeIf { it.isUsefulLabel(state.packageName) }
            ?.let { return it }

        observedAppLabels[state.packageName]
            ?.takeIf { it.isUsefulLabel(state.packageName) }
            ?.let { return it }

        return AppLabelResolver.labelOrNull(service, state.packageName)
            ?.takeIf { it.isUsefulLabel(state.packageName) }
            ?: state.packageName
    }

    private fun scheduleNext(state: MediaState, decision: MediaVisibilityDecision) {
        mainHandler.removeCallbacks(updateRunnable)
        val delay = MediaUpdateScheduler.nextDelayMs(
            showMirror = decision.showMirror,
            isPlaying = state.isPlaying,
            title = state.title,
            pillMode = preferences.pillMode,
            scrollTitle = preferences.scrollTitle,
            aodVisible = decision.aodVisible,
            showShortCriticalText = decision.showShortCriticalText,
            expandedTimelineVisible = state.durationMs > 0L
        )
        AppDiagnostics.verbose(
            service,
            "media",
            "Media next update delay=${delay?.toString() ?: "none"}; title=${state.title}; playing=${state.isPlaying}; aod=${decision.aodVisible}; critical=${decision.showShortCriticalText}; pill=${preferences.pillMode}; scroll=${preferences.scrollTitle}; expandedTimeline=${state.durationMs > 0L}"
        )
        if (delay != null) {
            mainHandler.postDelayed(updateRunnable, delay)
        }
    }

    private fun cancelMedia(reason: String) {
        val alreadyHiddenForReason = !mediaMirrorPosted &&
            pendingSnapshot == null &&
            lastPostedSnapshot == null &&
            lastHiddenReason == reason
        nextBuildVersion()
        mainHandler.removeCallbacks(updateRunnable)
        buildCoalescer.cancelQueued()
        pendingSnapshot = null
        lastPostedSnapshot = null
        releaseSuppressedSource("media hidden: $reason")
        if (alreadyHiddenForReason) {
            AppDiagnostics.verbose(service, "media", "Media mirror already hidden; reason=$reason")
            return
        }
        if (mediaMirrorPosted) {
            programmaticCancelPending = true
            notificationManager.cancel(MediaLiveNotificationBuilder.NOTIFICATION_ID)
        }
        mediaMirrorPosted = false
        lastHiddenReason = reason
        lastMissingSuppressionLogKey = null
        AppDiagnostics.note(service, "media", "Media mirror hidden: $reason")
    }

    private fun releaseSuppressedSource(reason: String) {
        val source = suppressedSource ?: return
        OriginalSuppressionController.onSourceHidden(service, source, reason)
        suppressedSource = null
    }

    private fun nextBuildVersion(): Int {
        buildVersion += 1
        return buildVersion
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

    private fun String.isUsefulLabel(packageName: String): Boolean {
        val normalized = trim()
        return normalized.isNotEmpty() && normalized != packageName
    }

    private companion object {
        const val REASON_CANCEL = 2
        const val REASON_CANCEL_ALL = 3
    }
}
