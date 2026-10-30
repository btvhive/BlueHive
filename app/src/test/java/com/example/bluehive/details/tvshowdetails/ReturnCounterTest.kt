package com.example.bluehive.details.tvshowdetails

import androidx.lifecycle.Lifecycle.Event
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 6 (v515): Lifecycle replays ON_CREATE / ON_START / ON_RESUME to an
 * observer added to a screen that is already resumed -- and the TV page adds
 * its observers after its first frame. Counting that replay as a return
 * restarted the season check (cancelling the first, in flight), recomposed
 * the screen, and fetched the watched marks a second time. Only a resume
 * after the screen went away counts now.
 */
class ReturnCounterTest {

    private fun ReturnCounter.feed(vararg events: Event) = events.forEach { onEvent(it) }

    @Test
    fun `the replay on opening counts nothing`() {
        val returns = ReturnCounter()
        returns.feed(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)
        assertEquals(0, returns.count)
    }

    @Test
    fun `back from the player counts once`() {
        val returns = ReturnCounter()
        returns.feed(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)
        returns.feed(Event.ON_PAUSE, Event.ON_STOP, Event.ON_START, Event.ON_RESUME)
        assertEquals(1, returns.count)
    }

    @Test
    fun `a pause and resume without stopping (a dialog, the screensaver) counts`() {
        val returns = ReturnCounter()
        returns.feed(Event.ON_RESUME, Event.ON_PAUSE, Event.ON_RESUME)
        assertEquals(1, returns.count)
    }

    @Test
    fun `each return counts, and a stray resume does not`() {
        val returns = ReturnCounter()
        returns.feed(Event.ON_RESUME, Event.ON_PAUSE, Event.ON_RESUME, Event.ON_RESUME)
        returns.feed(Event.ON_PAUSE, Event.ON_STOP, Event.ON_START, Event.ON_RESUME)
        assertEquals(2, returns.count)
    }

    @Test
    fun `the watched marks are read by one effect, keyed on the returns`() {
        val code = MainSource.code("details/tvshowdetails/EpisodesState.kt")
        val effects = code.substringAfter("internal fun WatchedEpisodesEffects(").substringBefore("\n}\n")
        assertEquals(1, Regex("""refreshWatchedEpisodes""").findAll(effects).count())
        assertTrue(effects.contains("LaunchedEffect(tmdbId, profileId, returns)"))
        assertFalse(effects.contains("LifecycleEventObserver"))
    }
}
