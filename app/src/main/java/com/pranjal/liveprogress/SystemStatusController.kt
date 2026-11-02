package com.pranjal.liveprogress

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors

class SystemStatusController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val tethering = context.getSystemService(TetheringManager::class.java)
    private val prefs = SystemStatusPreferences(context)
    private val states = SystemStatusKind.entries.associateWith { SystemStatusSession() }
    private val torchIds = mutableSetOf<String>()
    private val snapshots = mutableMapOf<SystemStatusKind, String>()
    private val scroller = PillTextScroller()
    private val polling = HotspotPollSession()
    private var channelLocale: String? = null
    private var destroyed = false
    private val refreshTask = Runnable { refresh() }
    private val changed: () -> Unit = { handler.removeCallbacks(refreshTask); handler.post(refreshTask) }
    private val scroll = Runnable { render() }
    private val poll = Runnable { readCount() }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (enabled) torchIds.add(cameraId) else torchIds.remove(cameraId)
            setActive(SystemStatusKind.TORCH, torchIds.isNotEmpty())
        }
        override fun onTorchModeUnavailable(cameraId: String) = onTorchModeChanged(cameraId, false)
    }
    private val tetherCallback = object : TetheringManager.TetheringEventCallback {
        override fun onTetheredInterfacesChanged(interfaces: MutableSet<TetheringInterface>) {
            setActive(SystemStatusKind.HOTSPOT, interfaces.any { it.type == TetheringManager.TETHERING_WIFI })
        }
    }

    fun start() {
        currentRef = java.lang.ref.WeakReference(this)
        ensureChannel()
        VisibilityState.addListener(changed)
        VisibilityPreferenceEvents.addListener(changed)
        AdditionalNotificationPreferenceEvents.addListener(changed)
        runCatching { cameras.registerTorchCallback(torchCallback, handler) }
        runCatching { tethering.registerTetheringEventCallback(context.mainExecutor, tetherCallback) }
        refresh()
    }

    private fun ensureChannel() {
        val locale = context.resources.configuration.locales.toLanguageTags()
        if (channelLocale == locale) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.system_status_title), NotificationManager.IMPORTANCE_DEFAULT)
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
        channelLocale = locale
    }

    private fun setActive(kind: SystemStatusKind, enabled: Boolean) {
        if (destroyed) return
        val state = states.getValue(kind)
        if (state.active == enabled) return
        state.update(enabled)
        refresh()
    }

    fun refresh() {
        if (destroyed) return
        ensureChannel()
        if (polling.updateEligibility(canReadCount())) handler.removeCallbacks(poll)
        if (polling.eligible && !polling.reading && !handler.hasCallbacks(poll)) readCount()
        render()
    }

    private fun canReadCount(): Boolean {
        if (destroyed || !isVisible(SystemStatusKind.HOTSPOT)) return false
        val access = PrivilegedAccess.currentState(context)
        return access.shizukuAvailable && access.shizukuGranted
    }

    private fun isVisible(kind: SystemStatusKind): Boolean = states.getValue(kind).visible(
        prefs.enabled(kind), prefs.showOnLockScreen(kind), VisibilityState.locked, VisibilityState.screenOff)

    private fun readCount() {
        if (!canReadCount()) {
            polling.updateEligibility(false)
            render()
            return
        }
        val requestedGeneration = polling.beginRead() ?: return
        worker.execute {
            val result = PrivilegedAccess.hotspotClientCount()
            handler.post {
                if (destroyed) return@post
                polling.updateEligibility(canReadCount())
                val accepted = polling.complete(requestedGeneration, result)
                render()
                if (polling.eligible) {
                    handler.removeCallbacks(poll)
                    handler.postDelayed(poll, if (accepted) 5_000L else 0L)
                }
            }
        }
    }

    private fun render() {
        if (destroyed) return
        handler.removeCallbacks(scroll)
        var scrolling = false
        val locked = VisibilityState.locked || VisibilityState.screenOff
        for (kind in SystemStatusKind.entries) {
            if (!isVisible(kind)) {
                if (snapshots.remove(kind) != null) manager.cancel(kind.notificationId)
                continue
            }
            val text = when (kind) {
                SystemStatusKind.TORCH -> context.getString(R.string.system_torch_on)
                SystemStatusKind.HOTSPOT -> polling.count?.takeIf { it > 0 }?.let {
                    context.resources.getQuantityString(R.plurals.system_hotspot_devices, it, it)
                } ?: context.getString(R.string.system_hotspot_on)
            }
            val compact = if (kind == SystemStatusKind.HOTSPOT) {
                scrolling = PillTextScroller.needsScrolling(text)
                scroller.render(text, text, SystemClock.elapsedRealtime())
            } else text
            val snapshot = "$text|$compact|$locked|${context.resources.configuration.locales.toLanguageTags()}"
            if (snapshots[kind] == snapshot) continue
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = if (kind == SystemStatusKind.HOTSPOT) PendingIntent.getActivity(context, kind.notificationId,
                Intent(context, HotspotActionActivity::class.java), flags) else null
            val turnOff = if (kind == SystemStatusKind.HOTSPOT) PendingIntent.getActivity(context, kind.notificationId,
                Intent(context, HotspotActionActivity::class.java).setAction(HotspotActionActivity.ACTION_TURN_OFF), flags)
            else PendingIntent.getBroadcast(context, kind.notificationId,
                Intent(context, TorchOffReceiver::class.java), flags)
            val delete = PendingIntent.getBroadcast(context, kind.notificationId,
                Intent(context, SystemStatusDismissReceiver::class.java).putExtra("kind", kind.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(kind.iconRes)
                .setContentTitle(context.getString(kind.titleRes))
                .setContentText(text)
                .setShortCriticalText(compact)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_system_turn_off),
                    context.getString(R.string.system_turn_off), turnOff)
                    .setAuthenticationRequired(kind == SystemStatusKind.HOTSPOT).build())
                .setDeleteIntent(delete)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setLocalOnly(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(Notification.BigTextStyle().bigText(text))
            runCatching { manager.notify(kind.notificationId, PromotedOngoingCompat.request(builder).build()) }
                .onSuccess { snapshots[kind] = snapshot }
        }
        if (scrolling) handler.postDelayed(scroll, PillTextScroller.UPDATE_MS)
    }

    fun activeTorchCameraIds(): List<String> = torchIds.toList()

    fun dismiss(kind: SystemStatusKind) {
        states.getValue(kind).dismiss()
        refresh()
    }

    fun destroy() {
        destroyed = true
        if (current === this) currentRef.clear()
        polling.updateEligibility(false)
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        runCatching { cameras.unregisterTorchCallback(torchCallback) }
        runCatching { tethering.unregisterTetheringEventCallback(tetherCallback) }
        VisibilityState.removeListener(changed)
        VisibilityPreferenceEvents.removeListener(changed)
        AdditionalNotificationPreferenceEvents.removeListener(changed)
        SystemStatusKind.entries.forEach { manager.cancel(it.notificationId) }
    }

    companion object {
        private const val CHANNEL = "system_status_live"
        private var currentRef = java.lang.ref.WeakReference<SystemStatusController>(null)
        val current: SystemStatusController? get() = currentRef.get()
    }
}

class SystemStatusDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = SystemStatusKind.entries.find { it.name == intent.getStringExtra("kind") } ?: return
        SystemStatusController.current?.dismiss(kind)
        context.getSystemService(NotificationManager::class.java).cancel(kind.notificationId)
    }
}
