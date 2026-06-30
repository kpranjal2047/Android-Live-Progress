package com.pranjal.liveprogress

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

@SuppressLint("AccessibilityPolicy")
class QuickSettingsAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastScanUptimeMs = 0L
    private var scanPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = SCAN_THROTTLE_MS
        }
        VisibilityState.register(this)
        AppDiagnostics.note(this, "visibility", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (packageName.isNullOrBlank()) return
        if (packageName != SYSTEM_UI_PACKAGE && event.isForegroundWindowEvent()) {
            VisibilityState.setForegroundPackage(this, packageName)
            return
        }
        if (packageName != SYSTEM_UI_PACKAGE) return
        if (eventSuggestsQuickSettings(event)) {
            VisibilityState.setQuickSettingsExpanded(this, true)
            requestQuickSettingsScan()
            return
        }
        requestQuickSettingsScan()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestQuickSettingsScan() {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastScanUptimeMs
        if (elapsed >= SCAN_THROTTLE_MS) {
            scanPending = false
            scanQuickSettings()
            return
        }
        if (scanPending) return
        scanPending = true
        mainHandler.postDelayed(
            {
                scanPending = false
                scanQuickSettings()
            },
            SCAN_THROTTLE_MS - elapsed
        )
    }

    private fun scanQuickSettings() {
        lastScanUptimeMs = SystemClock.uptimeMillis()
        val root = rootInActiveWindow
        val expanded = root?.let { hasQuickSettingsPanel(it) } ?: false
        VisibilityState.setQuickSettingsExpanded(this, expanded)
    }

    override fun onInterrupt() {
        AppDiagnostics.note(this, "visibility", "Accessibility service interrupted")
    }

    private fun eventSuggestsQuickSettings(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString().orEmpty()
        if (className.contains("QuickSettings", ignoreCase = true)) return true
        return event.text.any {
            it?.toString()?.contains("quick settings", ignoreCase = true) == true
        }
    }

    private fun hasQuickSettingsPanel(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val className = node.className?.toString().orEmpty().lowercase()
        val text = node.text?.toString().orEmpty().lowercase()
        val description = node.contentDescription?.toString().orEmpty().lowercase()

        if (node.isVisibleToUser) {
            if ("quick_settings_panel" in viewId || "qs_panel" in viewId) return true
            if ("quicksettings" in className || "quick settings" in text || "quick settings" in description) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasQuickSettingsPanel(child)) return true
        }
        return false
    }

    private fun AccessibilityEvent.isForegroundWindowEvent(): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val SCAN_THROTTLE_MS = 250L
    }
}
