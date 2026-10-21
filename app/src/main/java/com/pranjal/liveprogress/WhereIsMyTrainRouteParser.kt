package com.pranjal.liveprogress

/** Fields from WIMT's notification_expanded route image, not from the train-name wording. */
data class WhereIsMyTrainRouteData(
    val trainNumber: String?,
    val trainName: String?,
    val lastUpdatedTime: String?,
    val sourceStation: String?,
    val destination: String?,
    val currentStatus: String?,
    val distance: String?,
    val eta: String?,
    val scheduleStatus: String?
) {
    val trainTitle: String?
        get() = trainNumber?.let { number ->
            trainName?.let { "$number - $it" } ?: number
        }
}

internal object WhereIsMyTrainRouteParser {
    private val header = Regex("""^(\p{Nd}+)\s*[-–—]\s*(.+)$""")
    private val trailingClock = Regex("""(?i)(?<![\p{L}\p{N}:])(\p{Nd}{1,2})\s*:\s*(\p{Nd}{2})\s*([ap])\s*m\s*$""")
    private val distance = Regex("""(?i)(?<![\p{L}\p{N}.,])(\p{Nd}+(?:[.,]\p{Nd}+)?)\s*km\b""")
    private val whitespace = Regex("""[\s\u00a0\u202f]+""")

    fun parse(layout: NotificationOcrLayout): WhereIsMyTrainRouteData? {
        if (layout.width <= 0 || layout.height <= 0) return null
        val elements = layout.elements.filter {
            it.text.isNotBlank() && it.left >= 0 && it.top >= 0 &&
                it.right > it.left && it.bottom > it.top &&
                it.right <= layout.width && it.bottom <= layout.height
        }
        // These normalized bands describe the recognized 200dp route card, including its header.
        // The progress rail has no text. Never flatten OCR blocks: their order is not row order.
        fun band(from: Double, until: Double) = elements.filter {
            val y = (it.top.toDouble() + it.bottom) / 2 / layout.height
            y >= from && y < until
        }
        fun List<NotificationOcrElement>.joined(): String? =
            sortedBy { it.left }.joinToString(" ") { it.text }
                .replace(whitespace, " ").trim().takeIf { it.isNotEmpty() }
        fun List<NotificationOcrElement>.leftColumn() = filter {
            (it.left.toDouble() + it.right) / 2 < layout.width / 2.0
        }
        fun List<NotificationOcrElement>.rightColumn() = filter {
            (it.left.toDouble() + it.right) / 2 >= layout.width / 2.0
        }

        val headerText = band(0.0, 0.22).joined().orEmpty()
        val updateClock = trailingClock.find(headerText)
        val headerWithoutTime = headerText.substring(0, updateClock?.range?.first ?: headerText.length).trim()
        val train = header.matchEntire(headerWithoutTime)
        val stations = band(0.22, 0.38)
        val arrival = band(0.65, 0.85)
        val arrivalText = arrival.joined().orEmpty()
        val etaClock = trailingClock.find(arrivalText)
        val distanceMatch = distance.findAll(arrivalText).lastOrNull()
        val destinationEnd = distanceMatch?.range?.first ?: etaClock?.range?.first
        val arrivalStation = if (destinationEnd != null) {
            arrivalText.substring(0, destinationEnd).trim().trimEnd('-', '–', '—').trim().takeIf { it.isNotEmpty() }
        } else {
            arrival.leftColumn().joined()
        }
        val destination = preferCompleteStation(stations.rightColumn().joined(), arrivalStation)
        val data = WhereIsMyTrainRouteData(
            trainNumber = train?.groupValues?.get(1),
            trainName = train?.groupValues?.get(2),
            lastUpdatedTime = if (train != null) validClock(updateClock) else null,
            sourceStation = stations.leftColumn().joined(),
            destination = destination,
            currentStatus = band(0.49, 0.65).joined(),
            distance = distanceMatch?.groupValues?.get(1)?.let { "$it km" },
            eta = validClock(etaClock),
            scheduleStatus = band(0.85, 1.0).joined()
        )
        return data.takeIf {
            it.trainNumber != null || it.sourceStation != null || it.destination != null ||
                it.currentStatus != null || it.distance != null || it.eta != null || it.scheduleStatus != null
        }
    }

    private fun validClock(match: MatchResult?): String? {
        match ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 1..12 || minute !in 0..59) return null
        return "${match.groupValues[1]}:${match.groupValues[2]} ${match.groupValues[3].uppercase()}M"
    }

    private fun preferCompleteStation(top: String?, bottom: String?): String? {
        fun String.truncated() = endsWith("…") || endsWith("...")
        return when {
            top == null -> bottom
            bottom != null && top.truncated() && !bottom.truncated() -> bottom
            else -> top
        }
    }
}
