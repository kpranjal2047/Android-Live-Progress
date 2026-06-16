package com.pranjal.liveprogress

class DiagnosticMessageDeduper {
    private val lastValueByKey = mutableMapOf<String, String>()

    @Synchronized
    fun shouldWrite(key: String, value: String): Boolean {
        if (lastValueByKey[key] == value) return false
        lastValueByKey[key] = value
        return true
    }

    @Synchronized
    fun clear() {
        lastValueByKey.clear()
    }
}
