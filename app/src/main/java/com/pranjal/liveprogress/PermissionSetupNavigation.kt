package com.pranjal.liveprogress

import android.app.Activity
import android.content.Intent

/** Settings entry points navigate to setup; they never request permission themselves. */
object PermissionSetupNavigation {
    const val EXTRA_SETUP_TOPIC = "prerequisite_setup_topic"

    fun open(activity: Activity, topic: PrerequisiteHelp.Topic): Boolean {
        val accessibility = topic == PrerequisiteHelp.Topic.ACCESSIBILITY
        if (!accessibility && !PrivilegedAccess.isShizukuInstalled(activity)) return false
        activity.startActivity(Intent(activity, MainActivity::class.java)
            .putExtra(EXTRA_SETUP_TOPIC, if (accessibility) PrerequisiteHelp.Topic.ACCESSIBILITY.name
                else PrerequisiteHelp.Topic.SHIZUKU.name))
        return true
    }
}
