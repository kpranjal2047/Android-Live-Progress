package com.pranjal.liveprogress

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private companion object {
        const val POST_NOTIFICATIONS_REQUEST = 10
        const val TEST_LIVE_NOTIFICATION_ID = 1001
        const val ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS_VALUE =
            "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
        const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS_VALUE =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
        const val POST_PROMOTED_NOTIFICATIONS_PERMISSION =
            "android.permission.POST_PROMOTED_NOTIFICATIONS"
        const val CONTENT_PADDING = 32
    }

    private lateinit var diagnosticsView: TextView
    private lateinit var settingsContainer: LinearLayout

    private data class SetupRequirement(
        val titleRes: Int,
        val reasonRes: Int,
        val actionRes: Int,
        val action: () -> Unit,
        val skipAction: (() -> Unit)? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDiagnostics.clear(this)
        BackgroundRuntime.initialize(this, getString(R.string.diagnostic_main_activity_started))
        renderCurrentScreen()
    }

    override fun onResume() {
        super.onResume()
        renderCurrentScreen()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == POST_NOTIFICATIONS_REQUEST) {
            renderCurrentScreen()
        }
    }

    private fun renderCurrentScreen() {
        val missingRequirement = firstMissingSetupRequirement()
        if (missingRequirement != null) {
            setContentView(buildSetupContent(missingRequirement))
            return
        }

        setContentView(buildContent())
        refreshStatus()
    }

    private fun firstMissingSetupRequirement(): SetupRequirement? {
        val manager = getSystemService(NotificationManager::class.java)
        val progressPreferences = ProgressPreferences(this)
        val visibilityPreferences = VisibilityPreferences(this)
        val privileged = PrivilegedAccess.currentState(this)
        val requirement = SetupFlowPolicy.firstMissingRequirement(
            notificationsReady = hasPostPermission() && manager.areNotificationsEnabled(),
            promotedNotificationsReady = manager.canPostPromotedNotifications(),
            notificationListenerReady = isNotificationListenerEnabled(),
            progressEnabled = progressPreferences.enabled,
            hideWhenQuickSettingsExpanded =
                visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded,
            accessibilityEnabled = isAccessibilityEnabled(),
            suppressOriginalNotification = progressPreferences.suppressOriginalNotification,
            shizukuAvailable = privileged.shizukuAvailable,
            shizukuGranted = privileged.shizukuGranted
        ) ?: return null

        return when (requirement) {
            SetupRequirementKind.NOTIFICATIONS -> SetupRequirement(
                titleRes = R.string.setup_notifications_title,
                reasonRes = R.string.setup_notifications_reason,
                actionRes = R.string.setup_notifications_action,
                action = ::requestNotificationPostingOrOpenSettings
            )
            SetupRequirementKind.PROMOTED_NOTIFICATIONS -> SetupRequirement(
                titleRes = R.string.setup_promoted_title,
                reasonRes = R.string.setup_promoted_reason,
                actionRes = R.string.setup_promoted_action,
                action = ::openPromotedNotificationSettings
            )
            SetupRequirementKind.NOTIFICATION_LISTENER -> SetupRequirement(
                titleRes = R.string.setup_listener_title,
                reasonRes = R.string.setup_listener_reason,
                actionRes = R.string.setup_listener_action,
                action = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )
            SetupRequirementKind.ACCESSIBILITY -> SetupRequirement(
                titleRes = R.string.setup_accessibility_title,
                reasonRes = R.string.setup_accessibility_reason,
                actionRes = R.string.setup_accessibility_action,
                action = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                skipAction = ::skipAccessibilitySetup
            )
            SetupRequirementKind.SHIZUKU -> SetupRequirement(
                titleRes = R.string.setup_shizuku_title,
                reasonRes = R.string.setup_shizuku_reason,
                actionRes = R.string.setup_shizuku_action,
                action = ::requestShizukuPermission,
                skipAction = ::skipShizukuSetup
            )
        }
    }

    private fun buildSetupContent(requirement: SetupRequirement): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            applySystemBarPadding(this)
        }
        val appTitle = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
        }
        val setupTitle = TextView(this).apply {
            text = getString(requirement.titleRes)
            textSize = 20f
            setPadding(0, 32, 0, 12)
        }
        val reason = TextView(this).apply {
            text = getString(requirement.reasonRes)
            textSize = 15f
            setPadding(0, 0, 0, 24)
        }

        root.addView(appTitle)
        root.addView(setupTitle)
        root.addView(reason)
        root.addView(button(getString(requirement.actionRes), requirement.action))
        requirement.skipAction?.let { skipAction ->
            root.addView(button(getString(R.string.setup_skip_action), skipAction))
        }

        return ScrollView(this).apply { addView(root) }
    }

    private fun requestShizukuPermission() {
        val message = PrivilegedAccess.requestShizukuPermission(this)
        AppDiagnostics.note(this, "privileged_setup", message)
        renderCurrentScreen()
    }

    private fun skipAccessibilitySetup() {
        VisibilityPreferences(this).hideMirrorsWhenQuickSettingsExpanded = false
        AppDiagnostics.note(this, "visibility", getString(R.string.diagnostic_skipped_accessibility_setup))
        VisibilityPreferenceEvents.notifyChanged()
        renderCurrentScreen()
    }

    private fun skipShizukuSetup() {
        ProgressPreferences(this).suppressOriginalNotification = false
        AppDiagnostics.note(this, "privileged_setup", getString(R.string.diagnostic_skipped_shizuku_setup))
        ProgressPreferenceEvents.notifyChanged()
        renderCurrentScreen()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            applySystemBarPadding(this)
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
        }
        diagnosticsView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(button(getString(R.string.post_live_test_notification)) {
            postLiveTestNotification()
        })
        root.addView(button(getString(R.string.cancel_live_test_notification)) {
            getSystemService(NotificationManager::class.java)
                .cancel(TEST_LIVE_NOTIFICATION_ID)
            AppDiagnostics.note(this, "mirror", getString(R.string.diagnostic_canceled_live_test))
            refreshStatus()
        })
        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }
        root.addView(settingsContainer)
        root.addView(diagnosticsView)

        return ScrollView(this).apply { addView(root) }
    }

    private fun applySystemBarPadding(view: View) {
        view.setPadding(
            CONTENT_PADDING,
            CONTENT_PADDING,
            CONTENT_PADDING,
            CONTENT_PADDING
        )
        view.setOnApplyWindowInsetsListener { target, insets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(
                CONTENT_PADDING + systemBars.left,
                CONTENT_PADDING + systemBars.top,
                CONTENT_PADDING + systemBars.right,
                CONTENT_PADDING + systemBars.bottom
            )
            insets
        }
    }

    private fun button(label: String, action: () -> Unit): Button {
        return button(label = label, enabled = true, action = action)
    }

    private fun button(
        label: String,
        enabled: Boolean,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isEnabled = enabled
            setAllCaps(false)
            setOnClickListener { action() }
        }
    }

    private fun refreshStatus() {
        val progressPreferences = ProgressPreferences(this)
        val mediaPreferences = MediaPreferences(this)
        val visibilityPreferences = VisibilityPreferences(this)
        rebuildSettings(progressPreferences, mediaPreferences, visibilityPreferences)
        diagnosticsView.text = AppDiagnostics.snapshot(this)
            .joinToString(separator = "\n") { (key, value) -> "$key: $value" }
    }

    private fun rebuildSettings(
        progressPreferences: ProgressPreferences,
        mediaPreferences: MediaPreferences,
        visibilityPreferences: VisibilityPreferences
    ) {
        if (!::settingsContainer.isInitialized) return
        val privileged = PrivilegedAccess.currentState(this)
        val progressSuppressionToggle = SetupFlowPolicy.progressSuppressionToggleState(
            progressEnabled = progressPreferences.enabled,
            suppressOriginalNotification = progressPreferences.suppressOriginalNotification,
            shizukuAvailable = privileged.shizukuAvailable,
            shizukuGranted = privileged.shizukuGranted
        )
        settingsContainer.removeAllViews()
        settingsContainer.addView(sectionTitle(getString(R.string.section_general)))
        settingsContainer.addView(dropdown(
            label = getString(R.string.setting_language),
            values = AppLanguage.entries,
            selected = AppLanguage.selected(this),
            display = { it.displayName(this) }
        ) { language ->
            AppDiagnostics.note(this, "visibility", getString(R.string.diagnostic_language_setting_changed))
            AppLanguage.apply(this, language)
        })
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_hide_mirrors_qs),
            checked = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded
        ) {
            visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded = it
            AppDiagnostics.note(this, "visibility", getString(R.string.diagnostic_qs_setting_changed))
            VisibilityPreferenceEvents.notifyChanged()
            if (it && !isAccessibilityEnabled()) {
                renderCurrentScreen()
            } else {
                refreshStatus()
            }
        })

        settingsContainer.addView(sectionTitle(getString(R.string.section_progress_live_updates)))
        settingsContainer.addView(settingToggle(getString(R.string.setting_enable_progress_live_updates), progressPreferences.enabled) {
            progressPreferences.enabled = it
            AppDiagnostics.note(this, "mirror", getString(R.string.diagnostic_progress_enabled_changed))
            progressChanged()
        })
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_show_progress_aod),
            checked = progressPreferences.showOnAod,
            enabled = progressPreferences.enabled
        ) {
            progressPreferences.showOnAod = it
            AppDiagnostics.note(this, "mirror", getString(R.string.diagnostic_progress_aod_changed))
            progressChanged()
        })
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_show_progress_lock_screen),
            checked = progressPreferences.showOnLockScreen,
            enabled = progressPreferences.enabled
        ) {
            progressPreferences.showOnLockScreen = it
            AppDiagnostics.note(this, "mirror", getString(R.string.diagnostic_progress_lock_changed))
            progressChanged()
        })
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_hide_original_lock_screen),
            checked = progressSuppressionToggle.checked,
            enabled = progressSuppressionToggle.enabled
        ) {
            progressPreferences.suppressOriginalNotification = it
            AppDiagnostics.note(this, "mirror", getString(R.string.diagnostic_progress_suppression_changed))
            ProgressPreferenceEvents.notifyChanged()
            if (it && privileged.shizukuAvailable && !privileged.shizukuGranted) {
                renderCurrentScreen()
            } else {
                refreshStatus()
            }
        })

        settingsContainer.addView(sectionTitle(getString(R.string.section_media_live_updates)))
        settingsContainer.addView(settingToggle(getString(R.string.setting_enable_media_live_updates), mediaPreferences.enabled) {
            mediaPreferences.enabled = it
            mediaChanged()
        })
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_show_media_aod),
            checked = mediaPreferences.showOnAod,
            enabled = mediaPreferences.enabled
        ) {
            mediaPreferences.showOnAod = it
            mediaChanged()
        })
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_show_media_lock_screen),
            checked = mediaPreferences.showOnLockScreen,
            enabled = mediaPreferences.enabled
        ) {
            mediaPreferences.showOnLockScreen = it
            mediaChanged()
        })
        settingsContainer.addView(dropdown(
            label = getString(R.string.setting_status_bar_text),
            values = MediaPillMode.entries,
            selected = mediaPreferences.pillMode,
            enabled = mediaPreferences.enabled,
            display = { mediaPillModeLabel(it) }
        ) {
            mediaPreferences.pillMode = it
            mediaChanged()
        })
        val titlePillSelected = mediaPreferences.pillMode == MediaPillMode.TITLE
        settingsContainer.addView(settingToggle(
            label = getString(R.string.setting_scroll_title_status_bar),
            checked = mediaPreferences.scrollTitle && titlePillSelected,
            enabled = mediaPreferences.enabled && titlePillSelected
        ) {
            mediaPreferences.scrollTitle = it
            mediaChanged()
        })
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 18f
            setPadding(0, 16, 0, 8)
        }
    }

    private fun settingToggle(
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        onChanged: (Boolean) -> Unit
    ): CheckBox {
        return CheckBox(this).apply {
            text = label
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }
    }

    private fun <T> dropdown(
        label: String,
        values: List<T>,
        selected: T,
        enabled: Boolean = true,
        display: (T) -> String = { it.toString() },
        onChanged: (T) -> Unit
    ): LinearLayout {
        val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            values.map(display)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 8)
            addView(TextView(this@MainActivity).apply {
                text = label
                isEnabled = enabled
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })
            addView(Spinner(this@MainActivity).apply {
                this.adapter = adapter
                isEnabled = enabled
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setSelection(selectedIndex, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val value = values.getOrNull(position) ?: return
                        if (value != selected) {
                            onChanged(value)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            })
        }
    }

    private fun mediaChanged() {
        AppDiagnostics.note(this, "media", getString(R.string.diagnostic_media_settings_changed))
        MediaPreferenceEvents.notifyChanged()
        refreshStatus()
    }

    private fun progressChanged() {
        ProgressPreferenceEvents.notifyChanged()
        refreshStatus()
    }

    private fun mediaPillModeLabel(mode: MediaPillMode): String {
        return when (mode) {
            MediaPillMode.TITLE -> getString(R.string.media_pill_title)
            MediaPillMode.ELAPSED -> getString(R.string.media_pill_elapsed)
            MediaPillMode.REMAINING -> getString(R.string.media_pill_remaining)
        }
    }

    private fun postLiveTestNotification() {
        if (!hasPostPermission()) {
            AppDiagnostics.note(
                this,
                "mirror",
                getString(R.string.diagnostic_notification_permission_required)
            )
            renderCurrentScreen()
            return
        }

        MirrorNotificationBuilder.ensureChannel(this)
        val style = Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
            .setProgress(42)
            .setStyledByProgress(true)

        val builder = Notification.Builder(this, MirrorNotificationBuilder.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_progress)
            .setContentTitle(getString(R.string.live_test_notification_title))
            .setContentText(getString(R.string.live_test_notification_text))
            .setSubText(getString(R.string.app_name))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(Color.rgb(11, 110, 79))
            .setShortCriticalText("42%")
            .setStyle(style)

        val notification = PromotedOngoingCompat.request(builder).build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TEST_LIVE_NOTIFICATION_ID, notification)

        AppDiagnostics.note(
            this,
            "mirror",
            getString(R.string.diagnostic_post_live_test_result, promotedStatus(manager, notification))
        )
        refreshStatus()
    }

    private fun promotedStatus(
        manager: NotificationManager,
        notification: Notification
    ): String {
        return when {
            !manager.canPostPromotedNotifications() ->
                getString(R.string.diagnostic_promoted_disabled)

            !notification.hasPromotableCharacteristics() ->
                getString(R.string.diagnostic_test_not_promotable)

            else ->
                getString(R.string.diagnostic_test_promotable)
        }
    }

    private fun openPromotedNotificationSettings() {
        for (intent in promotedSettingsIntents()) {
            try {
                startActivity(intent)
                AppDiagnostics.note(
                    this,
                    "privileged_setup",
                    getString(R.string.diagnostic_opened_promoted_settings)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next known shape of this system settings intent.
            }
        }

        AppDiagnostics.note(
            this,
            "privileged_setup",
            getString(R.string.diagnostic_promoted_settings_unavailable)
        )
        startActivity(appNotificationSettingsIntent())
    }

    private fun promotedSettingsIntents(): List<Intent> {
        return listOf(
            Intent(runtimeSettingsAction(
                "ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS",
                ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS_VALUE
            ))
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS_VALUE)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS_VALUE)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS_VALUE)
                .setData(Uri.parse("package:$packageName"))
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun runtimeSettingsAction(fieldName: String, fallback: String): String {
        return runCatching {
            Settings::class.java.getField(fieldName).get(null) as String
        }.getOrDefault(fallback)
    }

    private fun appNotificationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .setData(Uri.parse("package:$packageName"))
    }

    private fun requestNotificationPostingOrOpenSettings() {
        if (!hasPostPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                POST_NOTIFICATIONS_REQUEST
            )
            return
        }
        startActivity(appNotificationSettingsIntent())
    }

    private fun hasPostPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        val component = ComponentName(this, NotificationMirrorService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_accessibility_services")
            ?: return false
        val component = ComponentName(this, QuickSettingsAccessibilityService::class.java)
            .flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
