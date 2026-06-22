package com.pranjal.liveprogress

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

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
        const val CONTENT_PADDING_DP = 20
    }

    private lateinit var diagnosticsView: TextView
    private lateinit var settingsContainer: LinearLayout

    private enum class ButtonStyle {
        Filled,
        Tonal
    }

    private data class UiPalette(
        val background: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val primary: Int,
        val primaryPressed: Int,
        val onPrimary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val outline: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textDisabled: Int,
        val disabledContainer: Int,
        val ripple: Int
    )

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
        val colors = palette()
        val root = contentRoot()
        val appTitle = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.textPrimary)
            includeFontPadding = false
        }
        val setupTitle = TextView(this).apply {
            text = getString(requirement.titleRes)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.textPrimary)
            includeFontPadding = false
        }
        val reason = TextView(this).apply {
            text = getString(requirement.reasonRes)
            textSize = 15f
            setTextColor(colors.textSecondary)
            setLineSpacing(2.dp().toFloat(), 1f)
            setPadding(0, 12.dp(), 0, 24.dp())
        }

        val setupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 22.dp(), 22.dp(), 22.dp())
            background = rounded(colors.surfaceContainer, 30.dp())
            addView(setupTitle)
            addView(reason)
            addView(button(getString(requirement.actionRes), requirement.action))
            requirement.skipAction?.let { skipAction ->
                addView(
                    button(
                        label = getString(R.string.setup_skip_action),
                        style = ButtonStyle.Tonal,
                        action = skipAction
                    ),
                    blockParams(top = 10.dp())
                )
            }
        }

        root.addView(appTitle, blockParams(bottom = 18.dp()))
        root.addView(setupCard, blockParams(top = 8.dp()))

        return scrollContent(root)
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
        val colors = palette()
        val root = contentRoot()
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.textPrimary)
            includeFontPadding = false
        }
        diagnosticsView = TextView(this).apply {
            textSize = 13f
            setTextColor(colors.textSecondary)
            typeface = Typeface.MONOSPACE
            setLineSpacing(2.dp().toFloat(), 1f)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            background = rounded(colors.surfaceContainer, 24.dp())
        }

        root.addView(title, blockParams(bottom = 18.dp()))
        root.addView(
            button(getString(R.string.post_live_test_notification)) {
                postLiveTestNotification()
            },
            blockParams(top = 6.dp())
        )
        root.addView(
            button(
                label = getString(R.string.cancel_live_test_notification),
                style = ButtonStyle.Tonal
            ) {
                getSystemService(NotificationManager::class.java)
                    .cancel(TEST_LIVE_NOTIFICATION_ID)
                AppDiagnostics.note(this, "mirror", getString(R.string.diagnostic_canceled_live_test))
                refreshStatus()
            },
            blockParams(top = 10.dp())
        )
        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20.dp(), 0, 0)
        }
        root.addView(settingsContainer)
        root.addView(diagnosticsView, blockParams(top = 18.dp()))

        return scrollContent(root)
    }

    private fun applySystemBarPadding(view: View) {
        val contentPadding = CONTENT_PADDING_DP.dp()
        view.setPadding(
            contentPadding,
            contentPadding,
            contentPadding,
            contentPadding
        )
        view.setOnApplyWindowInsetsListener { target, insets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(
                contentPadding + systemBars.left,
                contentPadding + systemBars.top,
                contentPadding + systemBars.right,
                contentPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun button(label: String, action: () -> Unit): Button {
        return button(label = label, enabled = true, action = action)
    }

    private fun button(
        label: String,
        style: ButtonStyle,
        action: () -> Unit
    ): Button {
        return button(label = label, enabled = true, style = style, action = action)
    }

    private fun button(
        label: String,
        enabled: Boolean,
        style: ButtonStyle = ButtonStyle.Filled,
        action: () -> Unit
    ): Button {
        val colors = palette()
        return Button(this).apply {
            text = label
            isEnabled = enabled
            setAllCaps(false)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 56.dp()
            minimumHeight = 56.dp()
            minWidth = 0
            minimumWidth = 0
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(buttonTextColors(style, colors))
            setPadding(20.dp(), 8.dp(), 20.dp(), 8.dp())
            background = buttonBackground(style, enabled, colors)
            setOnClickListener { action() }
        }
    }

    private fun contentRoot(): LinearLayout {
        val colors = palette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            applySystemBarPadding(this)
        }
    }

    private fun scrollContent(root: LinearLayout): ScrollView {
        return ScrollView(this).apply {
            setBackgroundColor(palette().background)
            clipToPadding = false
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun settingRow(enabled: Boolean): LinearLayout {
        val colors = palette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 68.dp()
            setPadding(18.dp(), 12.dp(), 14.dp(), 12.dp())
            background = rowBackground(enabled, colors)
            isClickable = enabled
            isFocusable = enabled
            layoutParams = blockParams(bottom = 10.dp())
        }
    }

    private fun blockParams(
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, top, 0, bottom)
        }
    }

    private fun buttonTextColors(
        style: ButtonStyle,
        colors: UiPalette
    ): ColorStateList {
        val normal = when (style) {
            ButtonStyle.Filled -> colors.onPrimary
            ButtonStyle.Tonal -> colors.onSecondaryContainer
        }
        return ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(colors.textDisabled, normal)
        )
    }

    private fun buttonBackground(
        style: ButtonStyle,
        enabled: Boolean,
        colors: UiPalette
    ): Drawable {
        if (!enabled) {
            return rounded(colors.disabledContainer, 28.dp())
        }
        val normal = when (style) {
            ButtonStyle.Filled -> colors.primary
            ButtonStyle.Tonal -> colors.secondaryContainer
        }
        val pressed = when (style) {
            ButtonStyle.Filled -> colors.primaryPressed
            ButtonStyle.Tonal -> blend(colors.primary, colors.secondaryContainer, 0.10f)
        }
        return RippleDrawable(
            ColorStateList.valueOf(colors.ripple),
            rounded(normal, 28.dp()),
            rounded(pressed, 28.dp())
        )
    }

    private fun rowBackground(
        enabled: Boolean,
        colors: UiPalette
    ): Drawable {
        val surface = if (enabled) colors.surfaceContainer else colors.surface
        if (!enabled) {
            return rounded(surface, 26.dp(), colors.disabledContainer, 1.dp())
        }
        return RippleDrawable(
            ColorStateList.valueOf(colors.ripple),
            rounded(surface, 26.dp(), colors.outline, 1.dp()),
            rounded(surface, 26.dp())
        )
    }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int? = null,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun palette(): UiPalette {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            UiPalette(
                background = systemColor(android.R.color.system_neutral1_900),
                surface = systemColor(android.R.color.system_neutral1_900),
                surfaceContainer = systemColor(android.R.color.system_neutral1_800),
                surfaceContainerHigh = systemColor(android.R.color.system_neutral1_700),
                primary = systemColor(android.R.color.system_accent1_200),
                primaryPressed = systemColor(android.R.color.system_accent1_100),
                onPrimary = systemColor(android.R.color.system_accent1_900),
                secondaryContainer = systemColor(android.R.color.system_accent2_800),
                onSecondaryContainer = systemColor(android.R.color.system_accent2_100),
                outline = systemColor(android.R.color.system_neutral2_700),
                textPrimary = systemColor(android.R.color.system_neutral1_100),
                textSecondary = systemColor(android.R.color.system_neutral2_200),
                textDisabled = systemColor(android.R.color.system_neutral2_500),
                disabledContainer = systemColor(android.R.color.system_neutral1_800),
                ripple = withAlpha(systemColor(android.R.color.system_neutral1_100), 28)
            )
        } else {
            UiPalette(
                background = systemColor(android.R.color.system_neutral1_10),
                surface = systemColor(android.R.color.system_neutral1_10),
                surfaceContainer = systemColor(android.R.color.system_neutral1_50),
                surfaceContainerHigh = systemColor(android.R.color.system_neutral1_100),
                primary = systemColor(android.R.color.system_accent1_600),
                primaryPressed = systemColor(android.R.color.system_accent1_700),
                onPrimary = systemColor(android.R.color.system_neutral1_10),
                secondaryContainer = systemColor(android.R.color.system_accent2_100),
                onSecondaryContainer = systemColor(android.R.color.system_accent2_900),
                outline = systemColor(android.R.color.system_neutral2_200),
                textPrimary = systemColor(android.R.color.system_neutral1_900),
                textSecondary = systemColor(android.R.color.system_neutral2_700),
                textDisabled = systemColor(android.R.color.system_neutral2_400),
                disabledContainer = systemColor(android.R.color.system_neutral1_100),
                ripple = withAlpha(systemColor(android.R.color.system_accent1_600), 34)
            )
        }
    }

    private fun systemColor(colorRes: Int): Int {
        return getColor(colorRes)
    }

    private fun blend(
        foreground: Int,
        background: Int,
        ratio: Float
    ): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color.rgb(
            (Color.red(foreground) * clamped + Color.red(background) * inverse).roundToInt(),
            (Color.green(foreground) * clamped + Color.green(background) * inverse).roundToInt(),
            (Color.blue(foreground) * clamped + Color.blue(background) * inverse).roundToInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun Int.dp(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).roundToInt()
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
        val colors = palette()
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.primary)
            includeFontPadding = false
            setPadding(4.dp(), 24.dp(), 0, 10.dp())
        }
    }

    private fun settingToggle(
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        onChanged: (Boolean) -> Unit
    ): View {
        val colors = palette()
        val row = settingRow(enabled)
        val labelView = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(if (enabled) colors.textPrimary else colors.textDisabled)
            setLineSpacing(2.dp().toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val switch = switchIndicator(checked, enabled, colors).apply {
            contentDescription = label
        }

        row.addView(labelView)
        row.addView(
            switch,
            LinearLayout.LayoutParams(
                58.dp(),
                36.dp()
            ).apply {
                marginStart = 16.dp()
            }
        )
        row.setOnClickListener {
            if (enabled) onChanged(!checked)
        }
        return row
    }

    private fun <T> dropdown(
        label: String,
        values: List<T>,
        selected: T,
        enabled: Boolean = true,
        display: (T) -> String = { it.toString() },
        onChanged: (T) -> Unit
    ): View {
        val colors = palette()
        val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
        val labels = values.map(display)

        val row = settingRow(enabled)
        row.apply {
            addView(TextView(this@MainActivity).apply {
                text = label
                isEnabled = enabled
                textSize = 16f
                setTextColor(if (enabled) colors.textPrimary else colors.textDisabled)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })
            val selectedPill = dropdownPill(
                label = labels.getOrElse(selectedIndex) { "" },
                enabled = enabled,
                colors = colors
            )
            val openMenu = {
                if (enabled) {
                    showDropdownMenu(
                        anchor = selectedPill,
                        labels = labels,
                        values = values,
                        selected = selected,
                        onChanged = onChanged
                    )
                }
            }
            selectedPill.setOnClickListener { openMenu() }
            addView(
                selectedPill,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    48.dp()
                ).apply { marginStart = 16.dp() }
            )
            setOnClickListener { openMenu() }
        }
        return row
    }

    private fun dropdownPill(
        label: String,
        enabled: Boolean,
        colors: UiPalette
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(if (enabled) colors.onSecondaryContainer else colors.textDisabled)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 108.dp()
            maxWidth = 210.dp()
            setPadding(16.dp(), 0, 14.dp(), 0)
            background = pillBackground(enabled, colors)
            compoundDrawablePadding = 8.dp()
            setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
            compoundDrawableTintList = ColorStateList.valueOf(
                if (enabled) colors.onSecondaryContainer else colors.textDisabled
            )
        }
    }

    private fun <T> showDropdownMenu(
        anchor: View,
        labels: List<String>,
        values: List<T>,
        selected: T,
        onChanged: (T) -> Unit
    ) {
        PopupMenu(this, anchor).apply {
            labels.forEachIndexed { index, label ->
                menu.add(0, index, index, label).isChecked = values.getOrNull(index) == selected
            }
            setOnMenuItemClickListener { item ->
                val value = values.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener true
                if (value != selected) {
                    onChanged(value)
                }
                true
            }
            show()
        }
    }

    private fun switchIndicator(
        checked: Boolean,
        enabled: Boolean,
        colors: UiPalette
    ): FrameLayout {
        val trackColor = when {
            !enabled -> colors.disabledContainer
            checked -> colors.primary
            else -> blend(colors.textSecondary, colors.surfaceContainerHigh, 0.40f)
        }
        val trackStroke = when {
            checked -> null
            enabled -> colors.outline
            else -> colors.textDisabled
        }
        val thumbColor = when {
            !enabled -> colors.textDisabled
            checked -> colors.onPrimary
            else -> colors.surface
        }
        return FrameLayout(this).apply {
            isEnabled = enabled
            background = rounded(trackColor, 18.dp(), trackStroke, 1.dp())
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            addView(
                View(this@MainActivity).apply {
                    background = rounded(thumbColor, 14.dp())
                    elevation = if (enabled) 2.dp().toFloat() else 0f
                },
                FrameLayout.LayoutParams(28.dp(), 28.dp()).apply {
                    gravity = if (checked) {
                        Gravity.CENTER_VERTICAL or Gravity.END
                    } else {
                        Gravity.CENTER_VERTICAL or Gravity.START
                    }
                }
            )
        }
    }

    private fun pillBackground(
        enabled: Boolean,
        colors: UiPalette
    ): Drawable {
        if (!enabled) {
            return rounded(colors.disabledContainer, 18.dp(), colors.outline, 1.dp())
        }
        return RippleDrawable(
            ColorStateList.valueOf(colors.ripple),
            rounded(colors.secondaryContainer, 18.dp(), colors.outline, 1.dp()),
            rounded(colors.secondaryContainer, 18.dp())
        )
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
            .setColor(SystemColorPalette.primary(this))
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
