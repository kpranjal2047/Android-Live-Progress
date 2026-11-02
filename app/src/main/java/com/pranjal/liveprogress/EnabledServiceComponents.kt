package com.pranjal.liveprogress

/** Secure settings may store either package/.Service or package/package.Service. */
object EnabledServiceComponents {
    fun contains(enabled: String?, packageName: String, className: String): Boolean {
        return enabled.orEmpty().split(':').any { flattened ->
            val parts = flattened.split('/')
            if (parts.size != 2 || parts[0] != packageName) return@any false
            val fullName = if (parts[1].startsWith('.')) parts[0] + parts[1] else parts[1]
            fullName == className
        }
    }
}
