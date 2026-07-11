package com.pranjal.liveprogress

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.lang.RuntimeException

object OriginalSuppressionController {
    private const val PREFS = "android_live_progress_suppression_state"
    private const val KEY_SUPPRESSED_CHANNELS = "suppressed_channels"
    private const val ASSISTANT_BIND_DELAY_MS = 500L
    private const val SUPPRESSION_RETRY_MS = 700L
    private const val MAX_SUPPRESSION_ATTEMPTS = 5

    private data class ChannelKey(
        val packageName: String,
        val uid: Int,
        val channelId: String
    )

    private val candidateChannels = linkedMapOf<String, ChannelKey>()
    private val originalVisibilityByChannel = linkedMapOf<ChannelKey, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onLockedMirrorShown(
        context: Context,
        candidate: MirrorCandidate
    ) {
        onLockedSourceShown(
            context = context,
            source = candidate.originalSource(),
            reason = "lock-screen suppression for ${candidate.appLabel}"
        )
    }

    fun onLockedSourceShown(
        context: Context,
        source: OriginalNotificationSource,
        reason: String
    ) {
        val channelKey = source.channelKeyOrNull()
        if (channelKey == null) {
            AppDiagnostics.verbose(
                context,
                "suppression",
                "Suppression skipped; missing channel id; source=${source.appLabel}; reason=$reason"
            )
            AppDiagnostics.note(
                context,
                "suppression",
                "Cannot suppress original for ${source.appLabel}; source notification category id is unavailable"
            )
            return
        }

        val pendingOrSuppressed = synchronized(this) {
            val alreadyPending = candidateChannels[source.key] == channelKey
            candidateChannels[source.key] = channelKey
            alreadyPending || channelKey in originalVisibilityByChannel
        }
        if (pendingOrSuppressed) {
            AppDiagnostics.verbose(
                context,
                "suppression",
                "Suppression request already pending or active; source=${source.appLabel}; channel=${channelKey.channelId}; reason=$reason"
            )
            AppDiagnostics.note(
                context,
                "suppression",
                "Original notification category suppression already pending for ${source.appLabel}"
            )
            return
        }

        PrivilegedAccess.ensureTemporaryAssistantAsync(
            context,
            reason
        ) { ready, message ->
            AppDiagnostics.verbose(
                context,
                "suppression",
                "Temporary assistant result for suppression; source=${source.appLabel}; ready=$ready; message=$message"
            )
            if (!ready) {
                synchronized(this) {
                    candidateChannels.remove(source.key)
                }
                AppDiagnostics.note(
                    context,
                    "suppression",
                    "Cannot suppress original for ${source.appLabel}; $message"
                )
                releaseIfIdle(context, "temporary assistant failed for ${source.appLabel}")
                return@ensureTemporaryAssistantAsync
            }
            scheduleSuppression(context, source, channelKey, attempt = 1)
        }
    }

    fun onMirrorHidden(
        context: Context,
        candidate: MirrorCandidate,
        reason: String
    ) {
        onSourceHidden(context, candidate.originalSource(), reason)
    }

    fun onSourceHidden(
        context: Context,
        source: OriginalNotificationSource,
        reason: String
    ) {
        val channelKey = synchronized(this) {
            candidateChannels.remove(source.key) ?: source.channelKeyOrNull()
        }
        if (channelKey == null) {
            AppDiagnostics.verbose(
                context,
                "suppression",
                "Suppression restore skipped; no channel key; source=${source.appLabel}; reason=$reason"
            )
            releaseIfIdle(context, reason)
            return
        }
        val stillUsed = synchronized(this) { candidateChannels.containsValue(channelKey) }
        AppDiagnostics.verbose(
            context,
            "suppression",
            "Suppression source hidden; source=${source.appLabel}; channel=${channelKey.channelId}; stillUsed=$stillUsed; reason=$reason"
        )
        if (!stillUsed) restoreChannel(context, source, channelKey, reason)
        releaseIfIdle(context, reason)
    }

    fun restoreAll(context: Context, reason: String) {
        loadPersistedSuppressions(context)
        val channels = synchronized(this) {
            val snapshot = originalVisibilityByChannel.keys.toList()
            candidateChannels.clear()
            snapshot
        }

        if (channels.isEmpty()) {
            AppDiagnostics.verbose(context, "suppression", "No suppressed notification categories to restore; reason=$reason")
            releaseIfIdle(context, reason)
            return
        }
        AppDiagnostics.verbose(
            context,
            "suppression",
            "Restoring all suppressed notification categories; count=${channels.size}; reason=$reason"
        )
        channels.forEach { channelKey ->
            restoreChannel(context, null, channelKey, reason)
        }
        releaseIfIdle(context, reason)
    }

    private fun scheduleSuppression(
        context: Context,
        source: OriginalNotificationSource,
        channelKey: ChannelKey,
        attempt: Int
    ) {
        val delay = if (attempt == 1) ASSISTANT_BIND_DELAY_MS else SUPPRESSION_RETRY_MS
        AppDiagnostics.verbose(
            context,
            "suppression",
            "Scheduling suppression attempt; source=${source.appLabel}; channel=${channelKey.channelId}; attempt=$attempt; delayMs=$delay"
        )
        mainHandler.postDelayed(
            {
                when (trySuppressChannel(context, source, channelKey, attempt)) {
                    SuppressionAttempt.RETRY -> scheduleSuppression(
                        context,
                        source,
                        channelKey,
                        attempt + 1
                    )

                    SuppressionAttempt.DONE -> releaseIfIdle(
                        context,
                        "suppression attempt finished for ${source.appLabel}"
                    )
                }
            },
            delay
        )
    }

    private fun trySuppressChannel(
        context: Context,
        source: OriginalNotificationSource,
        channelKey: ChannelKey,
        attempt: Int
    ): SuppressionAttempt {
        val shouldContinue = synchronized(this) {
            candidateChannels[source.key] == channelKey &&
                channelKey !in originalVisibilityByChannel
        }
        if (!shouldContinue) {
            AppDiagnostics.verbose(
                context,
                "suppression",
                "Suppression attempt stopped; source=${source.appLabel}; channel=${channelKey.channelId}; attempt=$attempt"
            )
            return SuppressionAttempt.DONE
        }
        AppDiagnostics.verbose(
            context,
            "suppression",
            "Suppression attempt running; source=${source.appLabel}; channel=${channelKey.channelId}; attempt=$attempt"
        )

        val channelResult = sourceChannel(source, channelKey)
        if (channelResult.isFailure) {
            val error = channelResult.exceptionOrNull()
            if (error.isRetryableBridgeError() && attempt < MAX_SUPPRESSION_ATTEMPTS) {
                AppDiagnostics.note(
                    context,
                    "suppression",
                    "Waiting for assistant bridge before suppressing ${source.appLabel}; attempt=$attempt"
                )
                return SuppressionAttempt.RETRY
            }
            synchronized(this) {
                candidateChannels.remove(source.key)
            }
            AppDiagnostics.note(
                context,
                "suppression",
                "Unable to inspect original notification category for ${source.appLabel}; ${
                    error.describeForUser()
                }"
            )
            return SuppressionAttempt.DONE
        }

        val channel = channelResult.getOrNull()
        if (channel == null) {
            synchronized(this) {
                candidateChannels.remove(source.key)
            }
            AppDiagnostics.note(
                context,
                "suppression",
                "Cannot suppress original for ${source.appLabel}; source notification category ${channelKey.channelId} was not found"
            )
            return SuppressionAttempt.DONE
        }

        val originalVisibility = channel.lockscreenVisibility
        val result = runCatching {
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            NotificationAssistantBridgeService.updateSourceChannel(
                source.packageName,
                source.sourceUser,
                channel
            ).getOrThrow()
        }

        if (result.isSuccess) {
            synchronized(this) {
                if (candidateChannels[source.key] == channelKey) {
                    originalVisibilityByChannel[channelKey] = originalVisibility
                    persistSuppressions(context)
                }
            }
            AppDiagnostics.note(
                context,
                "suppression",
                "Temporarily hid original lock-screen notification category for ${source.appLabel}; original visibility=${visibilityName(originalVisibility)}"
            )
        } else {
            val error = result.exceptionOrNull()
            if (error.isRetryableBridgeError() && attempt < MAX_SUPPRESSION_ATTEMPTS) {
                AppDiagnostics.note(
                    context,
                    "suppression",
                    "Waiting for assistant bridge before updating ${source.appLabel}; attempt=$attempt"
                )
                return SuppressionAttempt.RETRY
            }
            synchronized(this) {
                candidateChannels.remove(source.key)
            }
            AppDiagnostics.note(
                context,
                "suppression",
                "Unable to hide original for ${source.appLabel}; ${
                    error.describeForUser()
                }"
            )
        }
        return SuppressionAttempt.DONE
    }

    private fun restoreChannel(
        context: Context,
        source: OriginalNotificationSource?,
        channelKey: ChannelKey,
        reason: String
    ) {
        val originalVisibility = synchronized(this) {
            originalVisibilityByChannel[channelKey]
        } ?: run {
            AppDiagnostics.verbose(
                context,
                "suppression",
                "Restore skipped; original visibility not tracked; package=${channelKey.packageName}; channel=${channelKey.channelId}; reason=$reason"
            )
            return
        }
        AppDiagnostics.verbose(
            context,
            "suppression",
            "Restore attempt running; package=${channelKey.packageName}; channel=${channelKey.channelId}; original=${visibilityName(originalVisibility)}; reason=$reason"
        )
        val channelResult = sourceChannel(source, channelKey)
        if (channelResult.isFailure) {
            AppDiagnostics.note(
                context,
                "suppression",
                "Unable to inspect original notification category for restore ${channelKey.packageName}; ${
                    channelResult.exceptionOrNull().describeForUser()
                }"
            )
            return
        }

        val channel = channelResult.getOrNull()
        if (channel == null) {
            synchronized(this) {
                originalVisibilityByChannel.remove(channelKey)
                persistSuppressions(context)
            }
            AppDiagnostics.note(
                context,
                "suppression",
                "Suppression restore skipped for ${channelKey.packageName}/${channelKey.channelId}; notification category no longer exists"
            )
            return
        }

        val result = runCatching {
            channel.lockscreenVisibility = originalVisibility
            val user = source?.sourceUser ?: channelKey.userHandle()
            NotificationAssistantBridgeService.updateSourceChannel(
                channelKey.packageName,
                user,
                channel
            ).getOrThrow()
        }

        if (result.isSuccess) {
            synchronized(this) {
                originalVisibilityByChannel.remove(channelKey)
                persistSuppressions(context)
            }
            AppDiagnostics.note(
                context,
                "suppression",
                "Restored original lock-screen notification category for ${channelKey.packageName}; reason=$reason"
            )
        } else {
            AppDiagnostics.note(
                context,
                "suppression",
                "Unable to restore original notification category for ${channelKey.packageName}; ${
                    result.exceptionOrNull().describeForUser()
                }"
            )
        }
    }

    private fun sourceChannel(
        source: OriginalNotificationSource?,
        channelKey: ChannelKey
    ): Result<NotificationChannel?> {
        val user = source?.sourceUser ?: channelKey.userHandle()
        return NotificationAssistantBridgeService.getSourceChannel(
            channelKey.packageName,
            user,
            channelKey.channelId
        )
    }

    private fun releaseIfIdle(context: Context, reason: String) {
        val idle = synchronized(this) {
            candidateChannels.isEmpty() && originalVisibilityByChannel.isEmpty()
        }
        if (idle) {
            AppDiagnostics.verbose(context, "suppression", "Releasing temporary assistant; reason=$reason")
            PrivilegedAccess.releaseTemporaryAssistantAsync(context, reason)
        } else {
            AppDiagnostics.verbose(context, "suppression", "Keeping temporary assistant active; reason=$reason")
        }
    }

    private fun loadPersistedSuppressions(context: Context) {
        val persisted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SUPPRESSED_CHANNELS, emptySet())
            .orEmpty()
        if (persisted.isEmpty()) return
        synchronized(this) {
            persisted.forEach { encoded ->
                val restored = decodeSuppression(encoded) ?: return@forEach
                originalVisibilityByChannel.putIfAbsent(restored.first, restored.second)
            }
        }
        AppDiagnostics.verbose(
            context,
            "suppression",
            "Loaded persisted suppressions; count=${originalVisibilityByChannel.size}"
        )
    }

    private fun persistSuppressions(context: Context) {
        val encoded = originalVisibilityByChannel
            .map { (channelKey, visibility) -> encodeSuppression(channelKey, visibility) }
            .toSet()
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (encoded.isEmpty()) {
            editor.remove(KEY_SUPPRESSED_CHANNELS)
        } else {
            editor.putStringSet(KEY_SUPPRESSED_CHANNELS, encoded)
        }
        editor.apply()
    }

    private fun encodeSuppression(channelKey: ChannelKey, visibility: Int): String {
        return listOf(
            Uri.encode(channelKey.packageName),
            channelKey.uid.toString(),
            Uri.encode(channelKey.channelId),
            visibility.toString()
        ).joinToString("|")
    }

    private fun decodeSuppression(encoded: String): Pair<ChannelKey, Int>? {
        val parts = encoded.split('|')
        if (parts.size != 4 && parts.size != 5) return null
        val uidIndex = if (parts.size == 5) 2 else 1
        val channelIndex = if (parts.size == 5) 3 else 2
        val visibilityIndex = if (parts.size == 5) 4 else 3
        val uid = parts[uidIndex].toIntOrNull() ?: return null
        val visibility = parts[visibilityIndex].toIntOrNull() ?: return null
        return ChannelKey(
            packageName = Uri.decode(parts[0]),
            uid = uid,
            channelId = Uri.decode(parts[channelIndex])
        ) to visibility
    }

    private fun OriginalNotificationSource.channelKeyOrNull(): ChannelKey? {
        val id = channelId?.takeIf { it.isNotBlank() } ?: return null
        return ChannelKey(
            packageName = packageName,
            uid = sourceUid,
            channelId = id
        )
    }

    private fun ChannelKey.userHandle() = android.os.UserHandle.getUserHandleForUid(uid)

    private fun visibilityName(visibility: Int): String {
        return when (visibility) {
            Notification.VISIBILITY_PUBLIC -> "PUBLIC"
            Notification.VISIBILITY_PRIVATE -> "PRIVATE"
            Notification.VISIBILITY_SECRET -> "SECRET"
            else -> visibility.toString()
        }
    }

    private fun Throwable?.isRetryableBridgeError(): Boolean {
        val message = this?.message.orEmpty()
        return this is IllegalStateException &&
            message.contains("bridge is not connected", ignoreCase = true)
    }

    private fun Throwable?.describeForUser(): String {
        if (this == null) return "unknown error"
        val cause: Throwable = if (this is RuntimeException && this.cause != null) {
            this.cause ?: this
        } else {
            this
        }
        val message = cause.message?.takeIf { it.isNotBlank() }
        return listOfNotNull(cause.javaClass.simpleName, message).joinToString(": ")
    }

    private enum class SuppressionAttempt {
        RETRY,
        DONE
    }
}
