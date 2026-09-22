package com.pranjal.liveprogress

import android.content.Context

data class NotificationCategoryKey(
    val packageName: String,
    val userId: Int,
    val channelId: String
) {
    fun encode(): String {
        return listOf(packageName.cleanField(), userId.toString(), channelId.cleanField())
            .joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): NotificationCategoryKey? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 3) return null
            return fromStoredValues(
                packageName = parts[0],
                userId = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            )
        }

        internal fun fromRuntimeUid(
            packageName: String,
            uid: Int,
            channelId: String
        ): NotificationCategoryKey {
            return NotificationCategoryKey(
                packageName = packageName,
                userId = uid / ANDROID_UIDS_PER_USER,
                channelId = channelId
            )
        }

        internal fun fromStoredValues(
            packageName: String,
            userId: Int,
            channelId: String
        ): NotificationCategoryKey? {
            if (packageName.isBlank() || userId < 0 || channelId.isBlank()) return null
            return NotificationCategoryKey(packageName, userId, channelId)
        }

    }
}

data class ObservedNotificationCategory(
    val key: NotificationCategoryKey,
    val appLabel: String,
    val channelName: String?,
    val lastSeenMillis: Long,
    val isSystemApp: Boolean = false,
    val sourceDir: String? = null
) {
    val displayName: String
        get() = channelName?.takeIf { it.isNotBlank() } ?: key.channelId

    fun encode(): String {
        return listOf(
            key.packageName.cleanField(),
            key.userId.toString(),
            key.channelId.cleanField(),
            appLabel.cleanField(),
            channelName.orEmpty().cleanField(),
            lastSeenMillis.toString(),
            isSystemApp.toString(),
            sourceDir.orEmpty().cleanField()
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): ObservedNotificationCategory? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 8) return null
            val key = NotificationCategoryKey.fromStoredValues(
                packageName = parts[0],
                userId = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            ) ?: return null
            return ObservedNotificationCategory(
                key = key,
                appLabel = parts[3],
                channelName = parts[4].takeIf { it.isNotBlank() },
                lastSeenMillis = parts[5].toLongOrNull() ?: return null,
                isSystemApp = parts[6].toBooleanStrictOrNull() ?: return null,
                sourceDir = parts[7].takeIf { it.isNotBlank() }
            )
        }
    }
}

data class NotificationCategorySnapshot(
    val categories: List<ObservedNotificationCategory>,
    val settingsByKey: Map<NotificationCategoryKey, NotificationCategorySettings>
)

data class NotificationCategorySettings(
    val enabled: Boolean = false,
    val showOnAod: Boolean = true,
    val showOnLockScreen: Boolean = false,
    val hideOriginalNotification: Boolean = false,
    val keepAfterOriginalDismissed: Boolean = false
) {
    fun encode(key: NotificationCategoryKey): String {
        return listOf(
            key.packageName.cleanField(),
            key.userId.toString(),
            key.channelId.cleanField(),
            enabled.toString(),
            showOnAod.toString(),
            showOnLockScreen.toString(),
            hideOriginalNotification.toString(),
            keepAfterOriginalDismissed.toString()
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): Pair<NotificationCategoryKey, NotificationCategorySettings>? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 8) return null
            val key = NotificationCategoryKey.fromStoredValues(
                packageName = parts[0],
                userId = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            ) ?: return null
            return key to NotificationCategorySettings(
                enabled = parts[3].toBooleanStrictOrNull() ?: return null,
                showOnAod = parts[4].toBooleanStrictOrNull() ?: return null,
                showOnLockScreen = parts[5].toBooleanStrictOrNull() ?: return null,
                hideOriginalNotification = parts[6].toBooleanStrictOrNull() ?: return null,
                keepAfterOriginalDismissed = parts[7].toBooleanStrictOrNull() ?: return null
            )
        }

        fun enabledWithProgressDefaults(
            showOnAod: Boolean,
            showOnLockScreen: Boolean,
            hideOriginalNotification: Boolean
        ): NotificationCategorySettings {
            return NotificationCategorySettings(
                enabled = true,
                showOnAod = showOnAod,
                showOnLockScreen = showOnLockScreen,
                hideOriginalNotification = hideOriginalNotification,
                keepAfterOriginalDismissed = false
            )
        }
    }
}

class NotificationCategoryPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val observedPrefs = appContext.getSharedPreferences(OBSERVED_PREFS, Context.MODE_PRIVATE)
    private val selectedPrefs = appContext.getSharedPreferences(SELECTED_PREFS, Context.MODE_PRIVATE)

    var autoEnableNewCategories: Boolean
        get() = selectedPrefs.getBoolean(KEY_AUTO_ENABLE_NEW_CATEGORIES, false)
        set(value) = selectedPrefs.edit().putBoolean(KEY_AUTO_ENABLE_NEW_CATEGORIES, value).apply()

    var showSystemApps: Boolean
        get() = observedPrefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        set(value) = observedPrefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, value).apply()

    fun observedCategories(includeSystemApps: Boolean = true): List<ObservedNotificationCategory> {
        return allCategoriesByKey().values
            .filter { includeSystemApps || !it.isSystemApp }
            .sortedWith(compareBy<ObservedNotificationCategory> { it.appLabel.lowercase() }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.key.packageName }
                .thenBy { it.key.channelId })
    }

    fun snapshot(): NotificationCategorySnapshot {
        return NotificationCategorySnapshot(
            categories = observedCategories(),
            settingsByKey = settingsByKey()
        )
    }

    fun observe(
        packageName: String,
        uid: Int,
        channelId: String?,
        appLabel: String,
        channelName: String?,
        isSystemApp: Boolean? = null,
        sourceDir: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanChannelId = channelId?.takeIf { it.isNotBlank() } ?: return false
        val key = NotificationCategoryKey.fromRuntimeUid(packageName, uid, cleanChannelId)
        val observed = observedByKey()
        val selectedMetadata = selectedCategoriesByKey()
        val current = observed[key] ?: selectedMetadata[key]
        val firstObservation = key !in observed
        val next = ObservedNotificationCategory(
            key = key,
            appLabel = appLabel,
            channelName = channelName?.takeIf { it.isNotBlank() } ?: current?.channelName,
            isSystemApp = isSystemApp ?: current?.isSystemApp ?: false,
            sourceDir = sourceDir?.takeIf { it.isNotBlank() } ?: current?.sourceDir,
            lastSeenMillis = if (
                current == null ||
                nowMillis - current.lastSeenMillis >= LAST_SEEN_WRITE_INTERVAL_MS
            ) {
                nowMillis
            } else {
                current.lastSeenMillis
            }
        )
        if (observed[key] == next) return false
        observed[key] = next
        observedPrefs.edit()
            .putStringSet(KEY_OBSERVED, observed.values.map { it.encode() }.toSet())
            .apply()
        val existingSettings = settingsByKey()
        if (firstObservation && autoEnableNewCategories && key !in existingSettings) {
            saveSelectedCategories(
                existingSettings + (key to progressDefaultEnabledSettings())
            )
        }
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
        return settingsFor(NotificationCategoryKey.fromRuntimeUid(packageName, uid, cleanChannelId))
    }

    fun settingsFor(key: NotificationCategoryKey): NotificationCategorySettings {
        return settingsByKey()[key] ?: NotificationCategorySettings()
    }

    fun setSelected(key: NotificationCategoryKey, selected: Boolean) {
        setEnabled(listOf(key), selected)
    }

    fun updateSettings(
        key: NotificationCategoryKey,
        transform: (NotificationCategorySettings) -> NotificationCategorySettings
    ) {
        val next = settingsByKey().toMutableMap()
        next[key] = transform(next[key] ?: NotificationCategorySettings())
        saveSelectedCategories(next)
    }

    fun setObservedEnabled(
        keys: List<NotificationCategoryKey>,
        enabled: Boolean
    ) {
        setEnabled(keys, enabled)
    }

    fun setAppEnabled(
        packageName: String,
        userId: Int,
        enabled: Boolean
    ) {
        setEnabled(
            observedCategories()
                .filter { it.key.packageName == packageName && it.key.userId == userId }
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
            val current = next[key] ?: NotificationCategorySettings()
            if (enabled) {
                next[key] = if (current.enabled) current else progressDefaultEnabledSettings()
            } else {
                next.remove(key)
            }
        }
        saveSelectedCategories(next)
    }

    private fun observedByKey(): LinkedHashMap<NotificationCategoryKey, ObservedNotificationCategory> {
        val map = linkedMapOf<NotificationCategoryKey, ObservedNotificationCategory>()
        observedPrefs.getStringSet(KEY_OBSERVED, emptySet()).orEmpty()
            .mapNotNull(ObservedNotificationCategory::parse)
            .forEach { observed ->
                val current = map[observed.key]
                map[observed.key] = if (current == null) {
                    observed
                } else {
                    mergeObservedCategories(current, observed)
                }
            }
        return map
    }

    private fun allCategoriesByKey(): LinkedHashMap<NotificationCategoryKey, ObservedNotificationCategory> {
        val categories = selectedCategoriesByKey()
        observedByKey().forEach { (key, observed) ->
            categories[key] = categories[key]?.let { mergeObservedCategories(it, observed) } ?: observed
        }
        return categories
    }

    private fun selectedCategoriesByKey(): LinkedHashMap<NotificationCategoryKey, ObservedNotificationCategory> {
        val map = linkedMapOf<NotificationCategoryKey, ObservedNotificationCategory>()
        selectedPrefs.getStringSet(KEY_SELECTED_CATEGORY_METADATA, emptySet()).orEmpty()
            .mapNotNull(ObservedNotificationCategory::parse)
            .forEach { category -> map[category.key] = category }
        return map
    }

    private fun settingsByKey(): Map<NotificationCategoryKey, NotificationCategorySettings> {
        return selectedPrefs.getStringSet(KEY_SELECTED_CATEGORY_SETTINGS, emptySet()).orEmpty()
            .mapNotNull(NotificationCategorySettings::parse)
            .fold(linkedMapOf()) { settings, (key, value) ->
                val merged = settings[key]?.let { mergeSettings(it, value) } ?: value
                if (merged.enabled) settings[key] = merged
                settings
            }
    }

    private fun progressDefaultEnabledSettings(): NotificationCategorySettings {
        val progressPreferences = ProgressPreferences(appContext)
        return NotificationCategorySettings.enabledWithProgressDefaults(
            showOnAod = progressPreferences.showOnAod,
            showOnLockScreen = progressPreferences.showOnLockScreen,
            hideOriginalNotification = progressPreferences.suppressOriginalNotification
        )
    }

    private fun saveSelectedCategories(settings: Map<NotificationCategoryKey, NotificationCategorySettings>) {
        val enabledSettings = settings.filterValues(NotificationCategorySettings::enabled)
        val knownCategories = allCategoriesByKey()
        val existingMetadata = selectedCategoriesByKey()
        val selectedMetadata = enabledSettings.keys.map { key ->
            knownCategories[key] ?: existingMetadata[key] ?: ObservedNotificationCategory(
                key = key,
                appLabel = key.packageName,
                channelName = null,
                lastSeenMillis = 0L
            )
        }
        selectedPrefs.edit()
            .putStringSet(
                KEY_SELECTED_CATEGORY_SETTINGS,
                enabledSettings.map { (key, value) -> value.encode(key) }.toSet()
            )
            .putStringSet(
                KEY_SELECTED_CATEGORY_METADATA,
                selectedMetadata.map(ObservedNotificationCategory::encode).toSet()
            )
            .apply()
    }

    private fun mergeObservedCategories(
        first: ObservedNotificationCategory,
        second: ObservedNotificationCategory
    ): ObservedNotificationCategory {
        val newer = if (second.lastSeenMillis >= first.lastSeenMillis) second else first
        val older = if (newer === second) first else second
        return newer.copy(
            appLabel = newer.appLabel.ifBlank { older.appLabel },
            channelName = newer.channelName ?: older.channelName,
            isSystemApp = newer.isSystemApp || older.isSystemApp,
            sourceDir = newer.sourceDir ?: older.sourceDir
        )
    }

    private fun mergeSettings(
        first: NotificationCategorySettings,
        second: NotificationCategorySettings
    ): NotificationCategorySettings {
        return when {
            first.enabled && !second.enabled -> first
            second.enabled && !first.enabled -> second
            else -> second
        }
    }

    companion object {
        private const val OBSERVED_PREFS = "live_progress_notification_categories"
        private const val SELECTED_PREFS = "live_progress_selected_notification_categories"
        private const val KEY_OBSERVED = "observed_categories"
        private const val KEY_SELECTED_CATEGORY_SETTINGS = "selected_category_settings"
        private const val KEY_SELECTED_CATEGORY_METADATA = "selected_category_metadata"
        private const val KEY_AUTO_ENABLE_NEW_CATEGORIES = "auto_enable_new_categories"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val LAST_SEEN_WRITE_INTERVAL_MS = 60L * 60L * 1000L
    }
}

private const val FIELD_SEPARATOR = "\u001F"
private const val ANDROID_UIDS_PER_USER = 100_000

private fun String.cleanField(): String {
    return replace(FIELD_SEPARATOR, " ").trim()
}
