package com.example.bluehive.livetv

/**
 * "Still watching?" for Live TV.
 *
 * A live channel never ends, and playing is a full exemption from IdleWatcher's
 * close, so someone asleep in front of a live channel would hold their TV slot
 * until somebody pressed a button. After [ASK_AFTER_MS] with no remote input the
 * player asks; unanswered for [ANSWER_WITHIN_MS] it stops, and IdleWatcher's
 * ordinary timers take it from there. Pure, so the JVM tests drive the clock.
 */
internal object StillWatching {

    const val ASK_AFTER_MS = 4L * 60 * 60 * 1000
    const val ANSWER_WITHIN_MS = 2L * 60 * 1000

    sealed interface State {
        data object Watching : State
        data class Asking(val secondsLeft: Int) : State
        data object Stop : State
    }

    fun state(now: Long, lastInput: Long): State {
        val idle = now - lastInput
        return when {
            idle < ASK_AFTER_MS -> State.Watching
            idle < ASK_AFTER_MS + ANSWER_WITHIN_MS ->
                State.Asking(((ASK_AFTER_MS + ANSWER_WITHIN_MS - idle + 999) / 1000).toInt())
            else -> State.Stop
        }
    }

    /** "1:48" */
    fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}
