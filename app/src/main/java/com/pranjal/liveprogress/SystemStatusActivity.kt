package com.pranjal.liveprogress

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout

class SystemStatusActivity : Activity() {
    private val prerequisiteHelp by lazy { PrerequisiteHelp(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = SystemStatusPreferences(this)
        val ui = SettingsUi(this)
        val root = ui.contentRoot()
        root.addView(ui.title(getString(R.string.system_status_title)))
        for (kind in SystemStatusKind.entries) {
            val visibilityContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun renderVisibility() {
                visibilityContainer.removeAllViews()
                visibilityContainer.addView(ui.settingToggle(
                    label = getString(R.string.setting_show_progress_lock_screen),
                    checked = prefs.showOnLockScreen(kind),
                    enabled = prefs.enabled(kind)
                ) { prefs.setShowOnLockScreen(kind, it) })
            }
            root.addView(ui.sectionTitle(getString(kind.titleRes)))
            root.addView(ui.settingToggle(
                label = getString(R.string.system_enable_live_notification), checked = prefs.enabled(kind),
                onInfo = if (kind == SystemStatusKind.HOTSPOT) {
                    { prerequisiteHelp.show(PrerequisiteHelp.Topic.HOTSPOT) }
                } else null
            ) {
                prefs.setEnabled(kind, it)
                renderVisibility()
            })
            renderVisibility()
            root.addView(visibilityContainer)
        }
        setContentView(ui.scrollContent(root))
    }

    override fun onStart() {
        super.onStart()
        AppUiLifecycleTracker.onActivityStarted()
    }

    override fun onStop() {
        prerequisiteHelp.close()
        AppUiLifecycleTracker.onActivityStopped(this)
        super.onStop()
    }
}
