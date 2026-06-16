package com.pranjal.liveprogress

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.Executors

data class PrivilegedState(
    val shizukuAvailable: Boolean,
    val shizukuGranted: Boolean,
    val shizukuUid: Int?,
    val temporaryAssistantActive: Boolean
) {
    fun summary(): String {
        val shizuku = when {
            shizukuGranted -> "Shizuku granted uid=$shizukuUid"
            shizukuAvailable -> "Shizuku available, permission not granted"
            else -> "Shizuku unavailable"
        }
        val temporary = if (temporaryAssistantActive) {
            "temporary assistant active"
        } else {
            "temporary assistant inactive"
        }
        return "$shizuku; $temporary"
    }
}

object PrivilegedAccess {
    private const val SHIZUKU_REQUEST_CODE = 7001
    private const val PREFS = "android_live_progress_privileged_state"
    private const val KEY_TEMPORARY_ACTIVE = "temporary_assistant_active"
    private const val KEY_TEMPORARY_CHANGED = "temporary_assistant_changed"
    private const val KEY_PREVIOUS_ASSISTANT = "previous_assistant"
    private const val KEY_TEMPORARY_USER = "temporary_assistant_user"
    private const val NULL_ASSISTANT = "null"

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val pendingAcquireCallbacks = mutableListOf<(Boolean, String) -> Unit>()
    private var acquiringTemporaryAssistant = false
    private var releaseRequestedAfterAcquire = false

    fun currentState(context: Context): PrivilegedState {
        val shizukuAvailable = shizukuPing()
        val shizukuGranted = shizukuAvailable && shizukuPermissionGranted()
        val shizukuUid = if (shizukuAvailable) shizukuUid() else null
        return PrivilegedState(
            shizukuAvailable = shizukuAvailable,
            shizukuGranted = shizukuGranted,
            shizukuUid = shizukuUid,
            temporaryAssistantActive = hasTemporaryAssistantSession(context)
        )
    }

    fun canUseOriginalNotificationSuppression(context: Context): Boolean {
        return hasTemporaryAssistantSession(context) || (shizukuPing() && shizukuPermissionGranted())
    }

    fun requestShizukuPermission(activity: Activity): String {
        if (!shizukuPing()) return "Shizuku is not running"
        if (shizukuPermissionGranted()) return "Shizuku permission already granted"
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val isPreV11 = cls.getMethod("isPreV11").invoke(null) as Boolean
            if (isPreV11) return "Shizuku pre-v11 is unsupported"
            val shouldShowRationale = cls.getMethod("shouldShowRequestPermissionRationale")
                .invoke(null) as Boolean
            if (shouldShowRationale) return "Shizuku permission was denied with no prompt available"
            cls.getMethod("requestPermission", Int::class.javaPrimitiveType)
                .invoke(null, SHIZUKU_REQUEST_CODE)
            "Requested Shizuku permission"
        } catch (error: ReflectiveOperationException) {
            "Unable to request Shizuku permission: ${error.javaClass.simpleName}"
        }
    }

    fun runSetupAsync(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val assistant = ComponentName(appContext, NotificationAssistantBridgeService::class.java)
                .flattenToString()
            val result = runPrivileged(capabilityCheckCommand(assistant))
            val detail = result.stdout
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(3)
                .joinToString("; ")
            val message = if (result.exitCode == 0) {
                if (detail.isBlank()) {
                    "Elevated access check completed"
                } else {
                    "Elevated access check completed: $detail"
                }
            } else {
                "Elevated access check failed: ${
                    result.stderr.ifBlank { result.stdout }.ifBlank { "exit ${result.exitCode}" }
                }"
            }
            AppDiagnostics.note(appContext, "privileged_setup", message)
            main.post { callback(message) }
        }
    }

    fun ensureTemporaryAssistantAsync(
        context: Context,
        reason: String,
        callback: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        synchronized(stateLock) {
            if (hasTemporaryAssistantSession(appContext)) {
                requestAssistantRebind(appContext, reason)
                main.post { callback(true, "Temporary assistant already active") }
                return
            }
            pendingAcquireCallbacks.add(callback)
            if (acquiringTemporaryAssistant) return
            acquiringTemporaryAssistant = true
            releaseRequestedAfterAcquire = false
        }

        executor.execute {
            val assistant = ComponentName(appContext, NotificationAssistantBridgeService::class.java)
                .flattenToString()
            val result = runPrivileged(
                beginTemporaryAssistantCommand(
                    packageName = appContext.packageName,
                    assistant = assistant
                )
            )
            val parsed = parseTemporaryAssistantResult(result.stdout)
            val success = result.exitCode == 0
            val message = if (success) {
                if (parsed.changed) {
                    saveTemporaryAssistantSession(appContext, parsed)
                    requestAssistantRebind(appContext, reason)
                    "Temporary assistant enabled; previous=${parsed.previousAssistant}"
                } else {
                    requestAssistantRebind(appContext, reason)
                    "Notification assistant was already approved"
                }
            } else {
                "Temporary assistant failed: ${
                    result.stderr.ifBlank { result.stdout }.ifBlank { "exit ${result.exitCode}" }
                }"
            }

            AppDiagnostics.note(appContext, "privileged_setup", "$message; reason=$reason")
            val callbacks: List<(Boolean, String) -> Unit>
            val releaseAfterAcquire: Boolean
            synchronized(stateLock) {
                acquiringTemporaryAssistant = false
                callbacks = pendingAcquireCallbacks.toList()
                pendingAcquireCallbacks.clear()
                releaseAfterAcquire = releaseRequestedAfterAcquire
                releaseRequestedAfterAcquire = false
            }
            main.post {
                callbacks.forEach { it(success, message) }
            }
            if (releaseAfterAcquire) {
                releaseTemporaryAssistantAsync(appContext, "release requested while acquiring")
            }
        }
    }

    fun releaseTemporaryAssistantAsync(
        context: Context,
        reason: String,
        callback: ((String) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        synchronized(stateLock) {
            if (acquiringTemporaryAssistant) {
                releaseRequestedAfterAcquire = true
                val message = "Temporary assistant release queued while acquire is running"
                AppDiagnostics.note(appContext, "privileged_setup", "$message; reason=$reason")
                if (callback != null) main.post { callback(message) }
                return
            }
        }

        val session = temporaryAssistantSession(appContext)
        if (session == null) {
            val message = "No temporary assistant state to release"
            if (callback != null) main.post { callback(message) }
            return
        }

        executor.execute {
            val assistant = ComponentName(appContext, NotificationAssistantBridgeService::class.java)
                .flattenToString()
            val result = runPrivileged(
                releaseTemporaryAssistantCommand(
                    packageName = appContext.packageName,
                    assistant = assistant,
                    previousAssistant = session.previousAssistant,
                    user = session.user
                )
            )
            val message = if (result.exitCode == 0) {
                clearTemporaryAssistantSession(appContext)
                "Temporary assistant released: ${
                    result.stdout.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("; ")
                        .ifBlank { "restored previous assistant" }
                }"
            } else {
                "Temporary assistant release failed: ${
                    result.stderr.ifBlank { result.stdout }.ifBlank { "exit ${result.exitCode}" }
                }"
            }
            AppDiagnostics.note(appContext, "privileged_setup", "$message; reason=$reason")
            if (callback != null) main.post { callback(message) }
        }
    }

    fun hasTemporaryAssistantSession(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TEMPORARY_ACTIVE, false)
    }

    private fun capabilityCheckCommand(assistant: String): String {
        return """
            set -e
            assistant='$assistant'
            user=${'$'}(am get-current-user 2>/dev/null || echo 0)
            approved=${'$'}(cmd notification get_approved_assistant "${'$'}user" | tr -d '\r')
            echo "approved assistant: ${'$'}approved"
            echo "expected assistant: ${'$'}assistant"
        """.trimIndent()
    }

    private fun beginTemporaryAssistantCommand(
        packageName: String,
        assistant: String
    ): String {
        return """
            set -e
            pkg='$packageName'
            assistant='$assistant'
            user=${'$'}(am get-current-user 2>/dev/null || echo 0)
            role='android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE'
            success=0
            previous=''
            bypass_touched=0
            cleanup_on_exit() {
              if [ "${'$'}success" != "1" ]; then
                if [ -n "${'$'}previous" ] && [ "${'$'}previous" != "null" ] && [ "${'$'}previous" != "${'$'}assistant" ]; then
                  cmd notification allow_assistant "${'$'}previous" "${'$'}user" >/dev/null 2>&1 || true
                fi
                pm revoke "${'$'}pkg" android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE >/dev/null 2>&1 || true
                cmd role remove-role-holder --user "${'$'}user" "${'$'}role" "${'$'}pkg" >/dev/null 2>&1 || true
              fi
              if [ "${'$'}bypass_touched" = "1" ]; then
                cmd role set-bypassing-role-qualification false >/dev/null 2>&1 || true
              fi
            }
            trap cleanup_on_exit EXIT
            previous=${'$'}(cmd notification get_approved_assistant "${'$'}user" | tr -d '\r')
            if [ "${'$'}previous" = "${'$'}assistant" ]; then
              success=1
              echo "changed=1"
              echo "previous_assistant=null"
              echo "approved_assistant=${'$'}previous"
              echo "user=${'$'}user"
              exit 0
            fi
            bypass_touched=1
            cmd role set-bypassing-role-qualification true >/dev/null 2>&1 || true
            cmd role add-role-holder --user "${'$'}user" "${'$'}role" "${'$'}pkg" >/dev/null 2>&1 || true
            pm grant "${'$'}pkg" android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE >/dev/null 2>&1 || true
            if [ -n "${'$'}previous" ] && [ "${'$'}previous" != "null" ] && [ "${'$'}previous" != "${'$'}assistant" ]; then
              cmd notification disallow_assistant "${'$'}previous" "${'$'}user" >/dev/null 2>&1 ||
                cmd notification remove_assistant "${'$'}previous" "${'$'}user" >/dev/null 2>&1 ||
                true
            fi
            cmd notification allow_assistant "${'$'}assistant" "${'$'}user"
            approved=${'$'}(cmd notification get_approved_assistant "${'$'}user" | tr -d '\r')
            if [ "${'$'}approved" != "${'$'}assistant" ]; then
              echo "notification assistant grant did not stick; approved=${'$'}approved expected=${'$'}assistant previous=${'$'}previous role=${'$'}role user=${'$'}user" >&2
              exit 20
            fi
            success=1
            echo "changed=1"
            echo "previous_assistant=${'$'}previous"
            echo "approved_assistant=${'$'}approved"
            echo "user=${'$'}user"
        """.trimIndent()
    }

    private fun releaseTemporaryAssistantCommand(
        packageName: String,
        assistant: String,
        previousAssistant: String,
        user: String
    ): String {
        return """
            set -e
            pkg='$packageName'
            assistant='$assistant'
            previous='$previousAssistant'
            user='$user'
            role='android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE'
            current=${'$'}(cmd notification get_approved_assistant "${'$'}user" | tr -d '\r')
            if [ "${'$'}current" = "${'$'}assistant" ]; then
              cmd notification disallow_assistant "${'$'}assistant" "${'$'}user" >/dev/null 2>&1 ||
                cmd notification remove_assistant "${'$'}assistant" "${'$'}user" >/dev/null 2>&1 ||
                true
            fi
            if [ -n "${'$'}previous" ] && [ "${'$'}previous" != "null" ] && [ "${'$'}previous" != "${'$'}assistant" ]; then
              cmd notification allow_assistant "${'$'}previous" "${'$'}user" >/dev/null 2>&1 || true
            fi
            pm revoke "${'$'}pkg" android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE >/dev/null 2>&1 || true
            cmd role remove-role-holder --user "${'$'}user" "${'$'}role" "${'$'}pkg" >/dev/null 2>&1 || true
            approved=${'$'}(cmd notification get_approved_assistant "${'$'}user" | tr -d '\r')
            echo "restored_assistant=${'$'}approved"
            echo "previous_assistant=${'$'}previous"
        """.trimIndent()
    }

    fun cleanupUnexpectedElevatedStateAsync(context: Context, reason: String) {
        val appContext = context.applicationContext
        if (hasTemporaryAssistantSession(appContext)) return
        executor.execute {
            if (hasTemporaryAssistantSession(appContext)) return@execute
            val assistant = ComponentName(appContext, NotificationAssistantBridgeService::class.java)
                .flattenToString()
            val result = runPrivileged(
                cleanupUnexpectedElevatedStateCommand(
                    packageName = appContext.packageName,
                    assistant = assistant
                )
            )
            val message = if (result.exitCode == 0) {
                "Unexpected elevated state cleanup: ${
                    result.stdout.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("; ")
                        .ifBlank { "completed" }
                }"
            } else {
                "Unexpected elevated state cleanup failed: ${
                    result.stderr.ifBlank { result.stdout }.ifBlank { "exit ${result.exitCode}" }
                }"
            }
            AppDiagnostics.note(appContext, "privileged_setup", "$message; reason=$reason")
        }
    }

    private fun cleanupUnexpectedElevatedStateCommand(
        packageName: String,
        assistant: String
    ): String {
        return """
            set -e
            pkg='$packageName'
            assistant='$assistant'
            user=${'$'}(am get-current-user 2>/dev/null || echo 0)
            role='android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE'
            current=${'$'}(cmd notification get_approved_assistant "${'$'}user" | tr -d '\r')
            if [ "${'$'}current" = "${'$'}assistant" ]; then
              cmd notification disallow_assistant "${'$'}assistant" "${'$'}user" >/dev/null 2>&1 ||
                cmd notification remove_assistant "${'$'}assistant" "${'$'}user" >/dev/null 2>&1 ||
                true
              echo "removed_assistant=${'$'}assistant"
            else
              echo "approved_assistant=${'$'}current"
            fi
            pm revoke "${'$'}pkg" android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE >/dev/null 2>&1 || true
            cmd role remove-role-holder --user "${'$'}user" "${'$'}role" "${'$'}pkg" >/dev/null 2>&1 || true
        """.trimIndent()
    }

    private fun runPrivileged(command: String): ShellResult {
        if (!shizukuPing()) return ShellResult(1, "", "Shizuku is not running")
        if (!shizukuPermissionGranted()) return ShellResult(1, "", "Shizuku permission is required")
        return runShizuku(command)
    }

    private fun requestAssistantRebind(context: Context, reason: String) {
        val component = ComponentName(context, NotificationAssistantBridgeService::class.java)
        val requested = runCatching {
            NotificationListenerService.requestRebind(component)
        }.isSuccess
        AppDiagnostics.note(
            context,
            "privileged_setup",
            "Requested assistant rebind=$requested; reason=$reason"
        )
    }

    private fun parseTemporaryAssistantResult(stdout: String): TemporaryAssistantCommandResult {
        val values = stdout.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        return TemporaryAssistantCommandResult(
            changed = values["changed"] == "1",
            previousAssistant = values["previous_assistant"].orEmpty().ifBlank { NULL_ASSISTANT },
            user = values["user"].orEmpty().ifBlank { "0" }
        )
    }

    private fun saveTemporaryAssistantSession(
        context: Context,
        result: TemporaryAssistantCommandResult
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TEMPORARY_ACTIVE, true)
            .putBoolean(KEY_TEMPORARY_CHANGED, result.changed)
            .putString(KEY_PREVIOUS_ASSISTANT, result.previousAssistant)
            .putString(KEY_TEMPORARY_USER, result.user)
            .apply()
    }

    private fun clearTemporaryAssistantSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TEMPORARY_ACTIVE)
            .remove(KEY_TEMPORARY_CHANGED)
            .remove(KEY_PREVIOUS_ASSISTANT)
            .remove(KEY_TEMPORARY_USER)
            .apply()
    }

    private fun temporaryAssistantSession(context: Context): TemporaryAssistantSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_TEMPORARY_ACTIVE, false)) return null
        return TemporaryAssistantSession(
            previousAssistant = prefs.getString(KEY_PREVIOUS_ASSISTANT, NULL_ASSISTANT)
                ?: NULL_ASSISTANT,
            user = prefs.getString(KEY_TEMPORARY_USER, "0") ?: "0"
        )
    }

    private fun shizukuPing(): Boolean {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            cls.getMethod("pingBinder").invoke(null) as Boolean
        } catch (_: Throwable) {
            false
        }
    }

    private fun shizukuPermissionGranted(): Boolean {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val result = cls.getMethod("checkSelfPermission").invoke(null) as Int
            result == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun shizukuUid(): Int? {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            cls.getMethod("getUid").invoke(null) as Int
        } catch (_: Throwable) {
            null
        }
    }

    private fun runShizuku(command: String): ShellResult {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val process = findNewProcessMethod(cls)
                .invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            readProcess(process)
        } catch (error: Throwable) {
            ShellResult(1, "", "Shizuku shell failed: ${error.describeForUser()}")
        }
    }

    private fun findNewProcessMethod(cls: Class<*>): Method {
        val parameterTypes = arrayOf(
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        return runCatching {
            cls.getMethod("newProcess", *parameterTypes)
        }.getOrElse {
            cls.getDeclaredMethod("newProcess", *parameterTypes).apply {
                isAccessible = true
            }
        }
    }

    private fun readProcess(process: Process): ShellResult {
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        return ShellResult(process.waitFor(), stdout.trim(), stderr.trim())
    }

    private fun Throwable.describeForUser(): String {
        val cause = if (this is InvocationTargetException) {
            targetException ?: cause ?: this
        } else {
            this
        }
        val detail = cause.message?.takeIf { it.isNotBlank() }
        return listOfNotNull(cause.javaClass.simpleName, detail)
            .joinToString(": ")
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

private data class TemporaryAssistantCommandResult(
    val changed: Boolean,
    val previousAssistant: String,
    val user: String
)

private data class TemporaryAssistantSession(
    val previousAssistant: String,
    val user: String
)
