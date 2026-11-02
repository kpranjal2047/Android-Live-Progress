package com.pranjal.liveprogress

import android.app.Activity
import android.app.AlertDialog

/** Informational dialogs never request access unless the user explicitly presses Grant. */
class PrerequisiteHelp(private val activity: Activity) {
    enum class Topic { ACCESSIBILITY, SHIZUKU, HOTSPOT }
    private var dialog: AlertDialog? = null
    private var observer: PrerequisiteAccessObserver? = null

    fun close() {
        dialog?.dismiss()
        dialog = null
        observer?.stop()
        observer = null
    }

    fun show(topic: Topic) {
        close()
        val privileged = topic == Topic.SHIZUKU || topic == Topic.HOTSPOT
        val popup = AlertDialog.Builder(activity)
            .setTitle(R.string.help_title)
            .setMessage(message(topic))
            .setNegativeButton(R.string.action_close, null)
            .setPositiveButton(if (privileged) R.string.setup_shizuku_action else R.string.grant_accessibility_permission, null)
            .create()
        dialog = popup
        popup.setOnDismissListener {
            observer?.stop()
            observer = null
            dialog = null
        }
        popup.show()
        popup.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(SettingsUi(activity).palette().surfaceContainerHigh)
            cornerRadius = 28f * activity.resources.displayMetrics.density
        })
        popup.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        fun refresh() {
            popup.setMessage(message(topic))
            popup.getButton(AlertDialog.BUTTON_POSITIVE).visibility =
                if (if (privileged) canOfferShizukuSetup()
                    else !SetupAccessStateReader.accessibilityEnabled(activity)) android.view.View.VISIBLE
                else android.view.View.GONE
        }
        popup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val missing = if (privileged) canOfferShizukuSetup()
                else !SetupAccessStateReader.accessibilityEnabled(activity)
            if (missing) {
                close()
                PermissionSetupNavigation.open(activity, topic)
            } else refresh()
        }
        observer = PrerequisiteAccessObserver(activity, ::refresh).also { it.start() }
        refresh()
    }

    private fun canOfferShizukuSetup(): Boolean {
        val state = PrivilegedAccess.currentState(activity)
        return ShizukuAccessPolicy.canOfferSetup(PrivilegedAccess.isShizukuInstalled(activity),
            state.shizukuAvailable, state.shizukuGranted)
    }

    private fun message(topic: Topic): String = activity.getString(when (topic) {
        Topic.ACCESSIBILITY -> R.string.help_accessibility
        Topic.SHIZUKU -> R.string.help_shizuku_suppression
        Topic.HOTSPOT -> R.string.help_hotspot_counts
    })
}
