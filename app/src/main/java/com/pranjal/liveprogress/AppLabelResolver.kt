package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppLabelResolver {
    private const val EXTRA_BUILDER_APPLICATION_INFO = "android.appInfo"
    private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"

    fun label(context: Context, packageName: String, notification: Notification? = null): String {
        return labelOrNull(context, packageName, notification) ?: packageName
    }

    fun labelOrNull(context: Context, packageName: String, notification: Notification? = null): String? {
        notification?.substituteAppName(packageName)?.let { return it }
        notification?.builderApplicationInfo()?.let { appInfo ->
            appInfo.loadLabelSafely(context, packageName)?.let { return it }
        }
        packageManagerLabel(context, packageName)?.let { return it }
        return packageContextLabel(context, packageName)
    }

    private fun Notification.substituteAppName(packageName: String): String? {
        return extras
            ?.getCharSequence(EXTRA_SUBSTITUTE_APP_NAME)
            ?.toString()
            ?.asLabelOrNull(packageName)
    }

    private fun Notification.builderApplicationInfo(): ApplicationInfo? {
        return extras?.getParcelable(EXTRA_BUILDER_APPLICATION_INFO, ApplicationInfo::class.java)
    }

    private fun ApplicationInfo.loadLabelSafely(context: Context, packageName: String): String? {
        return runCatching {
            loadLabel(context.packageManager).toString().asLabelOrNull(packageName)
        }.getOrNull()
    }

    private fun packageManagerLabel(context: Context, packageName: String): String? {
        val pm = context.packageManager
        return runCatching {
            val appInfo = pm.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
            pm.getApplicationLabel(appInfo).toString().asLabelOrNull(packageName)
        }.getOrNull()
    }

    private fun packageContextLabel(context: Context, packageName: String): String? {
        return runCatching {
            val packageContext = context.createPackageContext(packageName, 0)
            packageContext.applicationInfo
                .loadLabel(packageContext.packageManager)
                .toString()
                .asLabelOrNull(packageName)
        }.getOrNull()
    }

    private fun String.asLabelOrNull(packageName: String): String? {
        val normalized = trim()
        return normalized.takeIf { it.isNotEmpty() && it != packageName }
    }
}
