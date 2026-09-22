package com.pranjal.liveprogress

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import java.util.concurrent.Executors

enum class CategoryRefreshAction {
    HIDDEN,
    REQUEST_SHIZUKU_PERMISSION,
    CONFIRM_REFRESH
}

object CategoryRefreshPolicy {
    fun actionFor(
        shizukuAvailable: Boolean,
        shizukuGranted: Boolean
    ): CategoryRefreshAction {
        return when {
            !shizukuAvailable -> CategoryRefreshAction.HIDDEN
            !shizukuGranted -> CategoryRefreshAction.REQUEST_SHIZUKU_PERMISSION
            else -> CategoryRefreshAction.CONFIRM_REFRESH
        }
    }
}

object CategoryRefreshAssistantReleasePolicy {
    fun shouldRelease(temporaryAssistantWasActive: Boolean): Boolean {
        return !temporaryAssistantWasActive
    }
}

data class CategoryRefreshProgress(
    val completedApps: Int,
    val totalApps: Int
) {
    val percentage: Int
        get() = if (totalApps <= 0) 100 else (completedApps * 100 / totalApps).coerceIn(0, 100)
}

sealed interface CategoryRefreshState {
    data object Idle : CategoryRefreshState

    data object Preparing : CategoryRefreshState

    data class Scanning(
        val progress: CategoryRefreshProgress
    ) : CategoryRefreshState

    data class Finished(
        val result: CategoryRefreshResult
    ) : CategoryRefreshState
}

fun CategoryRefreshState.isRefreshInProgress(): Boolean {
    return this is CategoryRefreshState.Preparing || this is CategoryRefreshState.Scanning
}

sealed interface CategoryRefreshResult {
    data class Completed(
        val changed: Boolean,
        val newCategoryCount: Int,
        val failedAppCount: Int
    ) : CategoryRefreshResult

    data object NoAppsFound : CategoryRefreshResult

    data class Failed(
        val detail: String
    ) : CategoryRefreshResult
}

object CategoryRefreshCoordinator {
    private const val ASSISTANT_BIND_DELAY_MS = 500L
    private const val ASSISTANT_RETRY_DELAY_MS = 700L
    private const val MAX_ASSISTANT_REFRESH_ATTEMPTS = 5

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var state: CategoryRefreshState = CategoryRefreshState.Idle
    private var observer: ((CategoryRefreshState) -> Unit)? = null

    fun currentState(): CategoryRefreshState = synchronized(lock) { state }

    fun addObserver(callback: (CategoryRefreshState) -> Unit) {
        val snapshot = synchronized(lock) {
            observer = callback
            state
        }
        mainHandler.post { callback(snapshot) }
    }

    fun removeObserver(callback: (CategoryRefreshState) -> Unit) {
        synchronized(lock) {
            if (observer === callback) observer = null
        }
    }

    fun start(context: Context): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (state is CategoryRefreshState.Preparing || state is CategoryRefreshState.Scanning) {
                return false
            }
        }
        setState(CategoryRefreshState.Preparing)

        val privilegedState = PrivilegedAccess.currentState(appContext)
        if (!privilegedState.shizukuAvailable) {
            finish(appContext, CategoryRefreshResult.Failed("Shizuku is not running"), true)
            return true
        }
        if (!privilegedState.shizukuGranted) {
            finish(appContext, CategoryRefreshResult.Failed("Shizuku permission is not granted"), true)
            return true
        }

        AppDiagnostics.verbose(
            appContext,
            "mirror",
            "Notification category Shizuku refresh started; temporaryAssistant=${privilegedState.temporaryAssistantActive}"
        )
        PrivilegedAccess.listInstalledNotificationAppsAsync(appContext) { result ->
            val apps = result.getOrElse {
                finish(appContext, CategoryRefreshResult.Failed(it.describeForUser()), true)
                return@listInstalledNotificationAppsAsync
            }.filterNot { it.packageName == appContext.packageName }
            if (apps.isEmpty()) {
                finish(appContext, CategoryRefreshResult.NoAppsFound, true)
                return@listInstalledNotificationAppsAsync
            }
            AppDiagnostics.verbose(
                appContext,
                "mirror",
                "Notification category Shizuku refresh app list loaded; apps=${apps.size}"
            )
            ensureAssistantAndScan(
                appContext = appContext,
                apps = apps,
                temporaryAssistantWasActive = privilegedState.temporaryAssistantActive
            )
        }
        return true
    }

    fun acknowledgeFinished(result: CategoryRefreshResult) {
        synchronized(lock) {
            if ((state as? CategoryRefreshState.Finished)?.result != result) return
            state = CategoryRefreshState.Idle
        }
    }

    private fun ensureAssistantAndScan(
        appContext: Context,
        apps: List<InstalledNotificationApp>,
        temporaryAssistantWasActive: Boolean
    ) {
        PrivilegedAccess.ensureTemporaryAssistantAsync(
            appContext,
            "refresh notification categories with Shizuku"
        ) { ready, message ->
            if (!ready) {
                finish(
                    appContext,
                    CategoryRefreshResult.Failed(message),
                    temporaryAssistantWasActive
                )
                return@ensureTemporaryAssistantAsync
            }
            waitForAssistantAndScan(
                appContext = appContext,
                apps = apps,
                temporaryAssistantWasActive = temporaryAssistantWasActive,
                attempt = 1
            )
        }
    }

    private fun waitForAssistantAndScan(
        appContext: Context,
        apps: List<InstalledNotificationApp>,
        temporaryAssistantWasActive: Boolean,
        attempt: Int
    ) {
        val delay = if (attempt == 1) ASSISTANT_BIND_DELAY_MS else ASSISTANT_RETRY_DELAY_MS
        mainHandler.postDelayed(
            {
                if (!NotificationAssistantBridgeService.isConnected()) {
                    if (attempt < MAX_ASSISTANT_REFRESH_ATTEMPTS) {
                        waitForAssistantAndScan(
                            appContext,
                            apps,
                            temporaryAssistantWasActive,
                            attempt + 1
                        )
                    } else {
                        finish(
                            appContext,
                            CategoryRefreshResult.Failed("notification assistant bridge is not connected"),
                            temporaryAssistantWasActive
                        )
                    }
                    return@postDelayed
                }

                executor.execute {
                    val result = scanInstalledAppCategories(appContext, apps)
                    finish(appContext, result, temporaryAssistantWasActive)
                }
            },
            delay
        )
    }

    private fun scanInstalledAppCategories(
        appContext: Context,
        apps: List<InstalledNotificationApp>
    ): CategoryRefreshResult.Completed {
        val preferences = NotificationCategoryPreferences(appContext)
        val beforeKeys = preferences.observedCategories().map { it.key }.toSet()
        var changed = false
        var failedApps = 0
        val now = System.currentTimeMillis()
        setState(CategoryRefreshState.Scanning(CategoryRefreshProgress(0, apps.size)))

        apps.forEachIndexed { index, app ->
            val channels = NotificationAssistantBridgeService.getSourceChannels(
                packageName = app.packageName,
                user = UserHandle.getUserHandleForUid(app.uid)
            ).getOrElse {
                failedApps += 1
                AppDiagnostics.note(
                    appContext,
                    "mirror",
                    "Notification category scan failed for ${app.packageName}: ${it.describeForUser()}"
                )
                emptyList()
            }
            AppDiagnostics.verbose(
                appContext,
                "mirror",
                "Scanned notification categories for ${app.packageName}; count=${channels.size}; system=${app.isSystemApp}"
            )
            val appLabel = AppLabelResolver.label(
                appContext,
                app.packageName,
                uid = app.uid,
                sourceDir = app.sourceDir
            )
            channels.forEach { channel ->
                val channelId = channel.id.takeIf { it.isNotBlank() } ?: return@forEach
                changed = preferences.observe(
                    packageName = app.packageName,
                    uid = app.uid,
                    channelId = channelId,
                    appLabel = appLabel,
                    channelName = channel.name?.toString()?.takeIf { it.isNotBlank() },
                    isSystemApp = app.isSystemApp,
                    sourceDir = app.sourceDir,
                    nowMillis = now
                ) || changed
            }
            setState(
                CategoryRefreshState.Scanning(
                    CategoryRefreshProgress(index + 1, apps.size)
                )
            )
        }
        val afterKeys = preferences.observedCategories().map { it.key }.toSet()
        return CategoryRefreshResult.Completed(
            changed = changed,
            newCategoryCount = (afterKeys - beforeKeys).size,
            failedAppCount = failedApps
        )
    }

    private fun finish(
        appContext: Context,
        result: CategoryRefreshResult,
        temporaryAssistantWasActive: Boolean
    ) {
        val callback = synchronized(lock) {
            if (!state.isRefreshInProgress()) return
            state = CategoryRefreshState.Finished(result)
            observer
        }
        if (CategoryRefreshAssistantReleasePolicy.shouldRelease(temporaryAssistantWasActive)) {
            PrivilegedAccess.releaseTemporaryAssistantAsync(
                appContext,
                "notification category Shizuku refresh finished"
            )
        }
        if ((result as? CategoryRefreshResult.Completed)?.changed == true) {
            AdditionalNotificationPreferenceEvents.notifyChanged()
        }
        AppDiagnostics.note(
            appContext,
            "mirror",
            "Notification category Shizuku refresh finished; result=${result.javaClass.simpleName}"
        )
        if (callback != null) {
            mainHandler.post { callback(CategoryRefreshState.Finished(result)) }
        }
    }

    private fun setState(next: CategoryRefreshState) {
        val callback = synchronized(lock) {
            state = next
            observer
        }
        if (callback != null) {
            mainHandler.post { callback(next) }
        }
    }

    private fun Throwable.describeForUser(): String {
        return listOfNotNull(
            javaClass.simpleName,
            message?.takeIf { it.isNotBlank() }
        ).joinToString(": ")
    }
}
