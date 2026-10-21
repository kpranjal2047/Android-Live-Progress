package com.pranjal.liveprogress

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLocalizationTest {
    @Test
    fun everySupportedLanguageHasAllStringsAndMatchingFormatArguments() {
        val base = TestNotificationText.read("values")
        val locales = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/locales_config.xml")).getElementsByTagName("locale")
        val format = Regex("""%(\d+\$)?[dsf]""")
        for (index in 0 until locales.length) {
            val language = locales.item(index).attributes.getNamedItem("android:name").nodeValue
            val directory = when (language) {
                "en" -> "values"
                "id", "in" -> "values-in"
                else -> "values-$language"
            }
            val translated = TestNotificationText.read(directory)
            assertEquals("Resource keys for $language", base.keys, translated.keys)
            base.forEach { (name, value) ->
                assertTrue("Empty $language/$name", translated.getValue(name).isNotBlank())
                assertEquals("Format arguments for $language/$name",
                    format.findAll(value).map { it.value }.sorted().toList(),
                    format.findAll(translated.getValue(name)).map { it.value }.sorted().toList())
            }
        }
    }

    @Test
    fun trainAndDeliveryCountdownsUseSelectedLanguageAtEveryRender() {
        val pill = TrainArrivalPill(72 * 60_000, 30 * 60_000, false)
        val japanese = TestNotificationText.forLanguage("ja")
        assertEquals("1時間12分", pill.text(0, japanese))
        assertEquals("1時間12分", pill.text(1, japanese))
        assertEquals("18分", DominosDeliveryTime.remainingText(18 * 60_000, 0, japanese))
        assertEquals("1分未満", TrainArrivalPill(59_000, 60_000, false).text(0, japanese))
        assertEquals("到着", TrainArrivalPill(null, 60_000, true).text(0, japanese))
        // The same retained countdown uses the new language; no English text is cached in it.
        assertEquals("1h 12m", pill.text(0, TestNotificationText.english))
        assertEquals("", pill.text(30 * 60_000, japanese))
    }

    @Test
    fun translatedDistanceAndVariancePreserveSourceStationStatus() {
        val hindi = TestNotificationText.forLanguage("hi")
        assertEquals("Departed Central · 20 किमी बाकी · 5 मि. की देरी",
            WhereIsMyTrainText.additionalText(hindi, "Departed Central", "20 km", "Behind by 5 minutes"))
        assertEquals("आगे का स्टेशन · 20 किमी बाकी · 5 मि. आगे",
            WhereIsMyTrainText.additionalText(hindi, "आगे का स्टेशन", "20 km", "Ahead by 5 minutes"))
        assertEquals("未知状态", WhereIsMyTrainText.additionalText(hindi, "未知状态", null, null))
        assertEquals("5 मि. पहले", WhereIsMyTrainText.compactScheduleStatus("5 minutes early", hindi))
    }

    @Test
    fun rtlCountdownUsesLocalizedDigitsAndWords() {
        val arabic = TestNotificationText.forLanguage("ar")
        assertEquals("١٨ د", TrainArrivalPill(18 * 60_000, 30 * 60_000, false).text(0, arabic))
        assertEquals("وصل", TrainArrivalPill(null, 60_000, true).text(0, arabic))
        assertEquals("أقل من دقيقة", TrainArrivalPill(30_000, 60_000, false).text(0, arabic))
        assertEquals("المتبقي 20 كم", WhereIsMyTrainText.additionalText(arabic, null, "20 km", "On time"))
    }

    @Test
    fun missingMediaTitleUsesProvidedTranslation() {
        val fallback = TestNotificationText.forLanguage("ja").get(R.string.media_unknown_title)
        assertEquals("不明なタイトル", MediaTextFormatter.shortCriticalText(
            "", 0, 0, false, MediaPillMode.TITLE, fallback))
    }
}
