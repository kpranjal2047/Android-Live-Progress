package com.pranjal.liveprogress

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(
    val tag: String,
    private val nativeLabel: String? = null
) {
    AUTOMATIC(""),
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ARABIC("ar", "العربية"),
    BENGALI("bn", "বাংলা"),
    PORTUGUESE("pt", "Português"),
    RUSSIAN("ru", "Русский"),
    URDU("ur", "اردو"),
    JAPANESE("ja", "日本語"),
    GERMAN("de", "Deutsch"),
    ITALIAN("it", "Italiano"),
    INDONESIAN("id", "Bahasa Indonesia"),
    TURKISH("tr", "Türkçe"),
    VIETNAMESE("vi", "Tiếng Việt"),
    KOREAN("ko", "한국어"),
    THAI("th", "ไทย"),
    PERSIAN("fa", "فارسی"),
    MALAY("ms", "Bahasa Melayu"),
    TAMIL("ta", "தமிழ்");

    fun displayName(context: Context): String {
        return nativeLabel ?: context.getString(R.string.language_automatic)
    }

    companion object {
        fun selected(context: Context): AppLanguage {
            val manager = context.getSystemService(LocaleManager::class.java)
            val tags = manager.applicationLocales.toLanguageTags()
            if (tags.isBlank()) return AUTOMATIC

            val firstTag = tags.substringBefore(',')
            val exact = entries.firstOrNull { it.tag == firstTag }
            if (exact != null) return exact

            val language = Locale.forLanguageTag(firstTag).language
            return entries.firstOrNull { it.tag == language } ?: AUTOMATIC
        }

        fun apply(context: Context, language: AppLanguage) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager.applicationLocales = if (language.tag.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
        }
    }
}
