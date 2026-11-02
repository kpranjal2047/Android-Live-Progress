package com.pranjal.liveprogress

/** Only current tethered SoftApManager fields are accepted; event histories are ignored. */
object HotspotClientCountParser {
    fun parse(dump: String): Int? {
        val sections = dump.split(Regex("(?m)^\\s*(?:--)?Dump of SoftApManager[^\\n]*\\n")).drop(1)
        val counts = sections.mapNotNull { section ->
            val lines = section.lineSequence().takeWhile {
                !it.trim().startsWith("mTimeoutEnabled:") &&
                    !it.trim().startsWith("rec[") && !it.trim().startsWith("Dump of ")
            }.map(String::trim).toList()
            if (lines.none { it == "mIfaceIsUp: true" }) return@mapNotNull null
            if (lines.none { it == "mSpecifiedModeConfiguration.targetMode: 1" ||
                        it == "mApConfig.targetMode: 1" }) return@mapNotNull null
            // Some builds expose the current per-band client map as well as a total.
            // Count client MACs, not AP identifiers; discard the addresses immediately.
            val clientMap = lines.singleOrNull { it.startsWith("mConnectedClientWithApInfoMap: ") }
            if (clientMap != null) {
                val clients = Regex("(?i)macAddress[= :]\\s*((?:[0-9a-f]{2}:){5}[0-9a-f]{2})")
                    .findAll(clientMap).map { it.groupValues[1].lowercase() }.toSet()
                if (clients.isNotEmpty()) return@mapNotNull clients.size
                if (clientMap.substringAfter(": ").trim() == "{}") return@mapNotNull 0
                return@mapNotNull null
            }
            val fields = lines.mapNotNull {
                Regex("(?:getConnectedClientList\\(\\)\\.size\\(\\)|mConnectedClients\\.size\\(\\)): (\\d+)")
                    .matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
            }
            fields.singleOrNull()
        }
        // Android aggregates the bridged AP's clients in one manager. Multiple managers
        // cannot be deduplicated from counts alone: prefer unknown over an inflated count.
        return counts.singleOrNull()
    }
}
