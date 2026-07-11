package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledPackageListParserTest {
    @Test
    fun parsesAllPackagesAndMarksSystemPackages() {
        val output = """
            __ALL__
            package:com.example.user uid:10123
            package:android uid:1000
            package:com.android.systemui uid:10140
            __SYSTEM__
            package:android uid:1000
            package:com.android.systemui uid:10140
        """.trimIndent()

        assertEquals(
            listOf(
                InstalledNotificationApp("android", 1000, true),
                InstalledNotificationApp("com.android.systemui", 10140, true),
                InstalledNotificationApp("com.example.user", 10123, false)
            ),
            InstalledPackageListParser.parse(output)
        )
    }

    @Test
    fun ignoresMalformedPackageLines() {
        val output = """
            __ALL__
            package:com.example.valid uid:10123
            package:com.example.missing_uid
            package:com.example.invalid uid:not-a-number
            __SYSTEM__
            package:com.example.valid uid:10123
        """.trimIndent()

        assertEquals(
            listOf(InstalledNotificationApp("com.example.valid", 10123, true)),
            InstalledPackageListParser.parse(output)
        )
    }

    @Test
    fun parsesPackageSourcePathsWhenPresent() {
        val output = """
            __ALL__
            package:/data/app/~~abc/base.apk=com.example.user uid:10123
            package:/system_ext/priv-app/SystemUi/SystemUi.apk=com.android.systemui uid:10140
            __SYSTEM__
            package:/system_ext/priv-app/SystemUi/SystemUi.apk=com.android.systemui uid:10140
        """.trimIndent()

        assertEquals(
            listOf(
                InstalledNotificationApp(
                    packageName = "com.android.systemui",
                    uid = 10140,
                    isSystemApp = true,
                    sourceDir = "/system_ext/priv-app/SystemUi/SystemUi.apk"
                ),
                InstalledNotificationApp(
                    packageName = "com.example.user",
                    uid = 10123,
                    isSystemApp = false,
                    sourceDir = "/data/app/~~abc/base.apk"
                )
            ),
            InstalledPackageListParser.parse(output)
        )
    }
}
