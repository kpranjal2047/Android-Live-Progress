package com.pranjal.liveprogress

data class InstalledNotificationApp(
    val packageName: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val sourceDir: String? = null
)

object InstalledPackageListParser {
    private const val SECTION_ALL = "__ALL__"
    private const val SECTION_SYSTEM = "__SYSTEM__"
    private val packageLine = Regex("""^package:(\S+)\s+uid:(\d+)$""")
    private val packageLineWithSource = Regex("""^package:(.+)=(\S+)\s+uid:(\d+)$""")

    fun parse(shellOutput: String): List<InstalledNotificationApp> {
        val allPackages = linkedMapOf<String, PackageEntry>()
        val systemPackages = mutableSetOf<String>()
        var section: String? = null
        shellOutput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when (line) {
                    SECTION_ALL, SECTION_SYSTEM -> section = line
                    else -> {
                        val entry = parsePackageEntry(line) ?: return@forEach
                        when (section) {
                            SECTION_ALL -> allPackages[entry.packageName] = entry
                            SECTION_SYSTEM -> systemPackages += entry.packageName
                        }
                    }
                }
            }
        return allPackages
            .map { (_, entry) ->
                InstalledNotificationApp(
                    packageName = entry.packageName,
                    uid = entry.uid,
                    isSystemApp = entry.packageName in systemPackages,
                    sourceDir = entry.sourceDir
                )
            }
            .sortedWith(compareBy { it.packageName })
    }

    private fun parsePackageEntry(line: String): PackageEntry? {
        packageLineWithSource.matchEntire(line)?.let { match ->
            return PackageEntry(
                packageName = match.groupValues[2],
                uid = match.groupValues[3].toIntOrNull() ?: return null,
                sourceDir = match.groupValues[1]
            )
        }
        val match = packageLine.matchEntire(line) ?: return null
        return PackageEntry(
            packageName = match.groupValues[1],
            uid = match.groupValues[2].toIntOrNull() ?: return null,
            sourceDir = null
        )
    }

    private data class PackageEntry(
        val packageName: String,
        val uid: Int,
        val sourceDir: String?
    )
}
