package com.pranjal.liveprogress

import org.junit.Assert.*
import org.junit.Test

class EnabledServiceComponentsTest {
    @Test fun acceptsFullAndAbbreviatedNamesWithinAList() {
        val name = "com.example.Listener"
        assertTrue(EnabledServiceComponents.contains("com.example/com.example.Listener", "com.example", name))
        assertTrue(EnabledServiceComponents.contains("other/.Service:com.example/.Listener", "com.example", name))
    }
    @Test fun doesNotConfuseOtherServicesOrMalformedRecords() {
        for (value in listOf(null, "", "null", "com.example/", "com.example/.Other", "other/com.example.Listener", "com.example/.Listener/extra")) {
            assertFalse(EnabledServiceComponents.contains(value, "com.example", "com.example.Listener"))
        }
    }
}
