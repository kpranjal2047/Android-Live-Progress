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
    val lastSeenMillis: Long
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
            lastSeenMillis.toString()
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {
        fun parse(value: String): ObservedNotificationCategory? {
            val parts = value.split(FIELD_SEPARATOR)
            if (parts.size != 6) return null
            val key = NotificationCategoryKey(
                packageName = parts[0],
                uid = parts[1].toIntOrNull() ?: return null,
                channelId = parts[2]
            )
            return ObservedNotificationCategory(
                key = key,
                appLabel = parts[3],
                channelName = parts[4].takeIf { it.isNotBlank() },
                lastSeenMillis = parts[5].toLongOrNull() ?: return null
            )
        }
    }
}

class NotificationCategoryPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun observedCategories(): List<ObservedNotificationCategory> {
        return observedByKey().values
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
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanChannelId = channelId?.takeIf { it.isNotBlank() } ?: return false
        val key = NotificationCategoryKey(packageName, uid, cleanChannelId)
        val existing = observedByKey()
        val current = existing[key]
        val next = ObservedNotificationCategory(
            key = key,
            appLabel = appLabel,
            channelName = channelName?.takeIf { it.isNotBlank() } ?: current?.channelName,
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
        prefs.edit()
            .putStringSet(KEY_OBSERVED, existing.values.map { it.encode() }.toSet())
            .apply()
        return true
    }

    fun isSelected(packageName: String, uid: Int, channelId: String?): Boolean {
        val cleanChannelId = channelId?.takeIf { it.isNotBlank() } ?: return false
        return NotificationCategoryKey(packageName, uid, cleanChannelId).encode() in selectedKeys()
    }

    fun isSelected(key: NotificationCategoryKey): Boolean {
        return key.encode() in selectedKeys()
    }

    fun setSelected(key: NotificationCategoryKey, selected: Boolean) {
        val next = selectedKeys().toMutableSet()
        if (selected) {
            next.add(key.encode())
        } else {
            next.remove(key.encode())
        }
        prefs.edit().putStringSet(KEY_SELECTED, next).apply()
    }

    private fun observedByKey(): LinkedHashMap<NotificationCategoryKey, ObservedNotificationCategory> {
        val map = linkedMapOf<NotificationCategoryKey, ObservedNotificationCategory>()
        prefs.getStringSet(KEY_OBSERVED, emptySet()).orEmpty()
            .mapNotNull(ObservedNotificationCategory::parse)
            .forEach { map[it.key] = it }
        return map
    }

    private fun selectedKeys(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet()
    }

    companion object {
        private const val PREFS = "live_progress_notification_categories"
        private const val KEY_OBSERVED = "observed_categories"
        private const val KEY_SELECTED = "selected_categories"
        private const val LAST_SEEN_WRITE_INTERVAL_MS = 60L * 60L * 1000L
    }
}

private const val FIELD_SEPARATOR = "\u001F"

private fun String.cleanField(): String {
    return replace(FIELD_SEPARATOR, " ").trim()
}
