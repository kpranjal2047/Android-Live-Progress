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
    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            cachedControllers = controllers.orEmpty()
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

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateFromController()
        }
    }

    fun initialize() {
        MediaLiveNotificationBuilder.ensureChannel(service)
        MediaPreferenceEvents.addListener(preferenceListener)
        registerActiveSessionsListener()
        refreshController(
            explicitRefresh = true,
            reason = "media controller initialized"
        )
    }

    fun destroy() {
        mainHandler.removeCallbacks(updateRunnable)
        MediaPreferenceEvents.removeListener(preferenceListener)
        unregisterActiveSessionsListener()
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
        buildCoalescer.cancelQueued()
        releaseSuppressedSource("media controller destroyed")
        buildExecutor.shutdownNow()
    }

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = MediaNotificationSourceFactory.from(service, sbn) ?: return
        val sourcePackageChanged = activeSource?.original?.packageName != source.original.packageName
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
        updateFromController()
    }

    fun onPreferencesChanged() {
        notificationDismissed = false
        updateFromController()
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
        if (shouldQuery) {
            cachedControllers = queryActiveSessions(reason)
        }
        selectController(preferredPackage)
    }

    private fun queryActiveSessions(reason: String): List<MediaController> {
        BatteryDiagnostics.increment(BatteryDiagnostics.Counter.MEDIA_SESSION_SCANS)
        return runCatching {
            mediaSessionManager.getActiveSessions(mediaComponent)
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
            cancelMedia("no active media")
            return
        }

        if (state.isPlaying) {
            notificationDismissed = false
        }
        if (notificationDismissed) {
            cancelMedia("media live notification dismissed")
            return
        }

        if (state.packageName != activeSource?.original?.packageName) {
            releaseSuppressedSource("media source package changed")
            activeSource = null
        }

        if (state.title != lastTitle) {
            lastTitle = state.title
            titleStartTime = System.currentTimeMillis()
        }

        activeState = state
        applyVisibility(state)
    }

    private fun applyVisibility(state: MediaState) {
        VisibilityState.refreshLockState(service)
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = preferences.enabled,
            hasActiveMedia = true,
            locked = VisibilityState.locked,
            screenOff = VisibilityState.screenOff,
            quickSettingsExpanded = VisibilityState.quickSettingsExpanded,
            hideWhenQuickSettingsExpanded = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded,
            sourceAppInForeground = VisibilityState.isSourcePackageInForeground(state.packageName),
            progressMirrorActive = progressMirrorActive,
            showOnAod = preferences.showOnAod,
            showOnLockScreen = preferences.showOnLockScreen
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
                        } else {
                            val posted = runCatching {
                                notificationManager.notify(
                                    MediaLiveNotificationBuilder.NOTIFICATION_ID,
                                    notification
                                )
                            }
                            if (posted.isSuccess) {
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
        if (alreadyHiddenForReason) return
        if (mediaMirrorPosted) {
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
