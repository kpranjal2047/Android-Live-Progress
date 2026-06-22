package com.pranjal.liveprogress

import java.util.Locale

object AppProgressNotificationSupport {
    private val supportedPackages = setOf(
        "com.amazon.mShop.android.shopping",
        "com.application.zomato",
        "com.dd.doordash",
        "com.deliveroo.orderapp",
        "com.flipkart.android",
        "com.gojek.app",
        "com.grabtaxi.passenger",
        "com.grofers.customerapp",
        "com.grubhub.android",
        "com.google.android.apps.maps",
        "com.instacart.client",
        "com.olacabs.customer",
        "com.rapido.passenger",
        "com.ubercab",
        "com.ubercab.driver",
        "com.ubercab.eats",
        "com.ubercab.lite",
        "com.waze",
        "com.zeptoconsumerapp",
        "in.swiggy.android",
        "me.lyft.android"
    )

    private val activeStatusTerms = listOf(
        "accepted",
        "almost there",
        "arriving",
        "arrived",
        "arrival",
        "assigned",
        "booking",
        "confirmed",
        "continue",
        "courier",
        "dasher",
        "delivering",
        "delivery",
        "destination",
        "driver",
        "drop off",
        "dropoff",
        "en route",
        "eta",
        "exit",
        "finding",
        "head ",
        "heading",
        "looking for",
        "matching",
        "meet your",
        "nearby",
        "navigation",
        "on the way",
        "order is on",
        "out for delivery",
        "packed",
        "picked up",
        "pickup",
        "pick up",
        "preparing",
        "ready for pickup",
        "ride",
        "rider",
        "route",
        "searching",
        "shopper",
        "shopping",
        "traffic",
        "trip",
        "turn"
    )

    private val blockedTerms = listOf(
        "% off",
        "cancelled",
        "canceled",
        "cashback",
        "coupon",
        "deal",
        "delivered",
        "discount",
        "flat ",
        "free delivery",
        "invite",
        "login",
        "offer",
        "order again",
        "otp",
        "payment failed",
        "payment received",
        "promo",
        "rate your",
        "rating",
        "receipt",
        "refund",
        "reward",
        "sale",
        "save ",
        "subscription",
        "tip your",
        "verification",
        "wallet"
    )

    private val stageProgress = listOf(
        90 to listOf("almost there", "nearby", "arriving", "arrives soon", "arrive soon"),
        80 to listOf(
            "out for delivery",
            "on the way",
            "en route",
            "heading to you",
            "coming to you",
            "driver is coming",
            "courier is coming",
            "dasher is on"
        ),
        70 to listOf("picked up", "picked-up", "collected", "left the store", "left the restaurant"),
        55 to listOf("ready for pickup", "ready to pick", "packed", "packed up"),
        35 to listOf(
            "preparing",
            "being prepared",
            "restaurant is preparing",
            "shopping",
            "shopper is shopping",
            "picking your items",
            "packing"
        ),
        20 to listOf("confirmed", "accepted", "assigned", "driver assigned", "dasher assigned", "courier assigned", "rider assigned", "order placed"),
        10 to listOf("looking for", "finding", "searching", "matching you", "booking", "processing")
    )

    private val percentPattern = Regex("""(?<!\d)(\d{1,3})\s*%""")

    fun isSupportedPackage(packageName: String): Boolean {
        return packageName in supportedPackages
    }

    fun fallbackProgressInfo(
        packageName: String,
        textFields: List<String>,
        ongoing: Boolean
    ): ProgressInfo? {
        if (!isSupportedPackage(packageName)) return null
        val normalized = textFields
            .joinToString(" ")
            .lowercase(Locale.ROOT)
            .trim()
        if (normalized.isBlank() || containsBlockedTerm(normalized)) return null

        val activeStatus = containsActiveStatusTerm(normalized)
        val percent = percentPattern.find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(0, 100)
        if (percent != null && (ongoing || activeStatus)) {
            return ProgressInfo(progress = percent, max = 100, indeterminate = false)
        }

        if (!ongoing || !activeStatus) return null
        stagePercent(normalized)?.let { percentProgress ->
            return ProgressInfo(progress = percentProgress, max = 100, indeterminate = false)
        }
        return ProgressInfo(progress = 0, max = 0, indeterminate = true)
    }

    private fun stagePercent(text: String): Int? {
        return stageProgress.firstOrNull { (_, terms) ->
            terms.any(text::contains)
        }?.first
    }

    private fun containsActiveStatusTerm(text: String): Boolean {
        return activeStatusTerms.any(text::contains)
    }

    private fun containsBlockedTerm(text: String): Boolean {
        return blockedTerms.any(text::contains)
    }
}
