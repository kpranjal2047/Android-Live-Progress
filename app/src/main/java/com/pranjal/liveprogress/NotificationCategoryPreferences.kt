package com.pranjal.liveprogress

import android.content.Context

data class NotificationCategoryKey(
    val packageName: String,
    val uid: Int,
    val channelId: String
) {
    fun encode(): String {
        return listOf(packageName.cleanField(), uid.toString(), channelId.cleanField())
            .joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): NotificationCategoryKey? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 3) return null
            return NotificationCategoryKey(
                packageName = parts[0],
                uid = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            )
        }
    }
}

data class ObservedNotificationCategory(
    val key: NotificationCategoryKey,
    val appLabel: String,
    val channelName: String?,
    val lastSeenMillis: Long,
    val isSystemApp: Boolean = false
) {
    val displayName: String
        get() = channelName?.takeIf { it.isNotBlank() } ?: key.channelId

    fun encode(): String {
        return listOf(
            key.packageName.cleanField(),
            key.uid.toString(),
            key.channelId.cleanField(),
            appLabel.cleanField(),
            channelName.orEmpty().cleanField(),
            lastSeenMillis.toString(),
            isSystemApp.toString()
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): ObservedNotificationCategory? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 6 && parts.size != 7) return null
            val key = NotificationCategoryKey(
                packageName = parts[0],
                uid = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            )
            return ObservedNotificationCategory(
                key = key,
                appLabel = parts[3],
                channelName = parts[4].takeIf { it.isNotBlank() },
                lastSeenMillis = parts[5].toLongOrNull() ?: return null,
                isSystemApp = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: false
            )
        }
    }
}

data class NotificationCategorySettings(
    val enabled: Boolean = false,
    val showOnAod: Boolean = true,
    val showOnLockScreen: Boolean = false,
    val hideOriginalNotification: Boolean = false
) {
    fun encode(key: NotificationCategoryKey): String {
        return listOf(
            key.packageName.cleanField(),
            key.uid.toString(),
            key.channelId.cleanField(),
            enabled.toString(),
            showOnAod.toString(),
            showOnLockScreen.toString(),
            hideOriginalNotification.toString()
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): Pair<NotificationCategoryKey, NotificationCategorySettings>? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 7) return null
            val key = NotificationCategoryKey(
                packageName = parts[0],
                uid = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            )
            return key to NotificationCategorySettings(
                enabled = parts[3].toBooleanStrictOrNull() ?: return null,
                showOnAod = parts[4].toBooleanStrictOrNull() ?: return null,
                showOnLockScreen = parts[5].toBooleanStrictOrNull() ?: return null,
                hideOriginalNotification = parts[6].toBooleanStrictOrNull() ?: return null
            )
        }
    }
}

class NotificationCategoryPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoEnableNewCategories: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLE_NEW_CATEGORIES, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLE_NEW_CATEGORIES, value).apply()

    var showSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, value).apply()

    fun observedCategories(includeSystemApps: Boolean = true): List<ObservedNotificationCategory> {
        return observedByKey().values
            .filter { includeSystemApps || !it.isSystemApp }
            .sortedWith(compareBy<ObservedNotificationCategory> { it.appLabel.lowercase() }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.key.packageName }
                .thenBy { it.key.channelId })
    }

    fun observe(
        packageName: String,
        uid: Int,
        channelId: String?,
        appLabel: String,
        channelName: String?,
        isSystemApp: Boolean? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanChannelId = channelId?.takeIf { it.isNotBlank() } ?: return false
        val key = NotificationCategoryKey(packageName, uid, cleanChannelId)
        val existing = observedByKey()
        val current = existing[key]
        val firstObservation = current == null
        val next = ObservedNotificationCategory(
            key = key,
            appLabel = appLabel,
            channelName = channelName?.takeIf { it.isNotBlank() } ?: current?.channelName,
            isSystemApp = isSystemApp ?: current?.isSystemApp ?: false,
            lastSeenMillis = if (
                current == null ||
                nowMillis - current.lastSeenMillis >= LAST_SEEN_WRITE_INTERVAL_MS
            ) {
                nowMillis
            } else {
                current.lastSeenMillis
            }
        )
        if (current == next) return false
        existing[key] = next
        val editor = prefs.edit()
            .putStringSet(KEY_OBSERVED, existing.values.map { it.encode() }.toSet())
        val existingSettings = settingsByKey()
        if (firstObservation && autoEnableNewCategories && key !in existingSettings) {
            editor.putStringSet(
                KEY_CATEGORY_SETTINGS,
                (existingSettings + (key to NotificationCategorySettings(enabled = true)))
                    .map { (settingsKey, settings) -> settings.encode(settingsKey) }
                    .toSet()
            )
        }
        editor.apply()
        return true
    }

    fun isSelected(key: NotificationCategoryKey): Boolean {
        return settingsFor(key).enabled
    }

    fun settingsFor(
        packageName: String,
        uid: Int,
        channelId: String?
    ): NotificationCategorySettings {
        val cleanChannelId = channelId?.takeIf { it.isNotBlank() } ?: return NotificationCategorySettings()
        return settingsFor(NotificationCategoryKey(packageName, uid, cleanChannelId))
    }

    fun settingsFor(key: NotificationCategoryKey): NotificationCategorySettings {
        return settingsByKey()[key] ?: migratedSelectedSettings(key)
    }

    fun setSelected(key: NotificationCategoryKey, selected: Boolean) {
        updateSettings(key) { it.copy(enabled = selected) }
    }

    fun updateSettings(
        key: NotificationCategoryKey,
        transform: (NotificationCategorySettings) -> NotificationCategorySettings
    ) {
        val next = settingsByKey().toMutableMap()
        next[key] = transform(settingsFor(key))
        saveSettings(next)
    }

    fun setObservedEnabled(
        keys: List<NotificationCategoryKey>,
        enabled: Boolean
    ) {
        setEnabled(keys, enabled)
    }

    fun setAppEnabled(
        packageName: String,
        uid: Int,
        enabled: Boolean
    ) {
        setEnabled(
            observedCategories()
                .filter { it.key.packageName == packageName && it.key.uid == uid }
                .map { it.key },
            enabled
        )
    }

    private fun setEnabled(
        keys: List<NotificationCategoryKey>,
        enabled: Boolean
    ) {
        val next = settingsByKey().toMutableMap()
        keys.forEach { key ->
            next[key] = settingsFor(key).copy(enabled = enabled)
        }
        saveSettings(next)
    }

    private fun observedByKey(): LinkedHashMap<NotificationCategoryKey, ObservedNotificationCategory> {
        val map = linkedMapOf<NotificationCategoryKey, ObservedNotificationCategory>()
        prefs.getStringSet(KEY_OBSERVED, emptySet()).orEmpty()
            .mapNotNull(ObservedNotificationCategory::parse)
            .forEach { map[it.key] = it }
        return map
    }

    private fun settingsByKey(): Map<NotificationCategoryKey, NotificationCategorySettings> {
        val stored = prefs.getStringSet(KEY_CATEGORY_SETTINGS, emptySet()).orEmpty()
            .mapNotNull(NotificationCategorySettings::parse)
            .toMap()
            .toMutableMap()
        selectedKeys().forEach { selected ->
            val key = NotificationCategoryKey.parse(selected) ?: return@forEach
            if (key !in stored) stored[key] = NotificationCategorySettings(enabled = true)
        }
        return stored
    }

    private fun migratedSelectedSettings(key: NotificationCategoryKey): NotificationCategorySettings {
        return if (key.encode() in selectedKeys()) {
            NotificationCategorySettings(enabled = true)
        } else {
            NotificationCategorySettings()
        }
    }

    private fun saveSettings(settings: Map<NotificationCategoryKey, NotificationCategorySettings>) {
        prefs.edit()
            .putStringSet(
                KEY_CATEGORY_SETTINGS,
                settings.map { (key, value) -> value.encode(key) }.toSet()
            )
            .apply()
    }

    private fun selectedKeys(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet()
    }

    companion object {
        private const val PREFS = "live_progress_notification_categories"
        private const val KEY_OBSERVED = "observed_categories"
        private const val KEY_SELECTED = "selected_categories"
        private const val KEY_CATEGORY_SETTINGS = "category_settings"
        private const val KEY_AUTO_ENABLE_NEW_CATEGORIES = "auto_enable_new_categories"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val LAST_SEEN_WRITE_INTERVAL_MS = 60L * 60L * 1000L
    }
}

private const val FIELD_SEPARATOR = "\u001F"

private fun String.cleanField(): String {
    return replace(FIELD_SEPARATOR, " ").trim()
}
