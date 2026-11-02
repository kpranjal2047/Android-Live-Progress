package com.pranjal.liveprogress

/** Android callbacks and the watchdog may race; only the first result may navigate. */
internal class HotspotStopSession {
    enum class Result { SUCCEEDED, FAILED, TIMED_OUT, OPEN_SETTINGS }
    enum class Effect { FINISH, OPEN_SETTINGS, NONE }
    private var completed = false

    fun accept(result: Result): Effect {
        if (completed) return Effect.NONE
        completed = true
        return if (result == Result.SUCCEEDED) Effect.FINISH else Effect.OPEN_SETTINGS
    }

    fun cancel() { completed = true }
}
