package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle

object AppLabelResolver {
    private const val EXTRA_BUILDER_APPLICATION_INFO = "android.appInfo"
    private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"

    fun label(
        context: Context,
        packageName: String,
        notification: Notification? = null,
        uid: Int? = null,
        sourceDir: String? = null
    ): String {
        return labelOrNull(context, packageName, notification, uid, sourceDir) ?: packageName
    }

    fun labelOrNull(
        context: Context,
        packageName: String,
        notification: Notification? = null,
        uid: Int? = null,
        sourceDir: String? = null
    ): String? {
        notification?.substituteAppName(packageName)?.let { return it }
        notification?.builderApplicationInfo()?.let { appInfo ->
            appInfo.loadLabelSafely(context, packageName)?.let { return it }
        }
        packageManagerLabel(context, packageName)?.let { return it }
        archiveLabel(context, packageName, sourceDir)?.let { return it }
        launcherLabel(context, packageName, uid)?.let { return it }
        return packageContextLabel(context, packageName)
    }

    fun isSystemApp(context: Context, packageName: String): Boolean? {
        val appInfo = packageManagerApplicationInfo(context, packageName)
            ?: packageContextApplicationInfo(context, packageName)
            ?: return null
        return appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
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
        val appInfo = packageManagerApplicationInfo(context, packageName) ?: return null
        return runCatching {
            pm.getApplicationLabel(appInfo).toString().asLabelOrNull(packageName)
        }.getOrNull()
    }

    private fun launcherLabel(context: Context, packageName: String, uid: Int?): String? {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val user = uid?.let(UserHandle::getUserHandleForUid) ?: Process.myUserHandle()
        return runCatching {
            launcherApps.getActivityList(packageName, user)
                .asSequence()
                .firstNotNullOfOrNull { activity ->
                    activity.applicationInfo.loadLabelSafely(context, packageName)
                        ?: activity.label?.toString()?.asLabelOrNull(packageName)
                }
        }.getOrNull()
    }

    private fun archiveLabel(context: Context, packageName: String, sourceDir: String?): String? {
        val cleanSourceDir = sourceDir?.takeIf { it.isNotBlank() } ?: return null
        val appInfo = runCatching {
            context.packageManager
                .getPackageArchiveInfo(cleanSourceDir, PackageManager.PackageInfoFlags.of(0))
                ?.applicationInfo
        }.getOrNull() ?: return null
        appInfo.sourceDir = cleanSourceDir
        appInfo.publicSourceDir = cleanSourceDir
        return appInfo.loadLabelSafely(context, packageName)
    }

    private fun packageManagerApplicationInfo(context: Context, packageName: String): ApplicationInfo? {
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        }.getOrNull()
    }

    private fun packageContextLabel(context: Context, packageName: String): String? {
        val appInfo = packageContextApplicationInfo(context, packageName) ?: return null
        return runCatching {
            appInfo
                .loadLabel(context.createPackageContext(packageName, 0).packageManager)
                .toString()
                .asLabelOrNull(packageName)
        }.getOrNull()
    }

    private fun packageContextApplicationInfo(context: Context, packageName: String): ApplicationInfo? {
        return runCatching {
            context.createPackageContext(packageName, 0).applicationInfo
        }.getOrNull()
    }

    private fun String.asLabelOrNull(packageName: String): String? {
        val normalized = trim()
        return normalized.takeIf { it.isNotEmpty() && it != packageName }
    }
}
