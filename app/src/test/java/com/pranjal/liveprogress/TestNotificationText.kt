package com.pranjal.liveprogress

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** JVM tests resolve the real resource templates, without an Android Context stub. */
internal object TestNotificationText {
    private val namesById = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    val english = forLanguage("en")

    fun read(directory: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$directory/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    fun forLanguage(language: String): NotificationText {
        val directory = when (language) {
            "en" -> "values"
            "id" -> "values-in"
            else -> "values-$language"
        }
        val values = read(directory)
        val locale = Locale.forLanguageTag(language)
        return NotificationText { id, arguments ->
            String.format(locale, values.getValue(namesById.getValue(id)), *arguments)
        }
    }
}
