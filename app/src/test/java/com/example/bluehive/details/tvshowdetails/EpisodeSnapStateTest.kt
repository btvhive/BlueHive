package com.example.bluehive.details.tvshowdetails

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EpisodeSnapState's promise: the press's action (starting the source chain)
 * runs exactly once, and only after the dust has finished -- or at once when
 * there is no dust -- and never after the screen has gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeSnapStateTest {

    private fun TestScope.snap(dust: suspend (EpisodeSnapState) -> Boolean) =
        EpisodeSnapState(scope = this, startDust = dust, warn = { _, _ -> })

    @Test
    fun actionWaitsForTheWholeDust() = runTest {
        var runs = 0
        val s = snap { true }
        s.snapThen { runs++ }
        runCurrent()
        assertEquals(EpisodeSnapState.Phase.Snapping, s.phase)

        advanceTimeBy(SNAP_DURATION_MS - 1)
        runCurrent()
        assertEquals("must not start before the dust is gone", 0, runs)
        assertFalse("the card hides once the dust covers it", s.cardVisible)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, runs)
        assertEquals(EpisodeSnapState.Phase.Playing, s.phase)
    }

    @Test
    fun secondPressIsIgnored() = runTest {
        var runs = 0
        val s = snap { true }
        s.snapThen { runs++ }
        s.snapThen { runs++ }
        advanceTimeBy(SNAP_DURATION_MS + 10)
        runCurrent()
        s.snapThen { runs++ }        // still Playing: ignored too
        runCurrent()
        assertEquals(1, runs)
    }

    @Test
    fun noDustMeansNoWait() = runTest {
        var runs = 0
        val s = snap { false }
        s.snapThen { runs++ }
        runCurrent()
        assertEquals(1, runs)
        assertFalse(s.cardVisible)
    }

    @Test
    fun captureThatThrowsStillPlays() = runTest {
        var runs = 0
        val s = snap { error("no surface") }
        s.snapThen { runs++ }
        runCurrent()
        assertEquals(1, runs)
    }

    @Test
    fun captureThatHangsIsAbandoned() = runTest {
        var runs = 0
        val s = snap { awaitCancellation() }
        s.snapThen { runs++ }
        advanceTimeBy(599)
        runCurrent()
        assertEquals(0, runs)
        advanceTimeBy(2)
        runCurrent()
        assertEquals("a hung capture must not block playback", 1, runs)
    }

    @Test
    fun actionThatThrowsGivesTheCardBack() = runTest {
        val s = snap { false }
        s.snapThen { error("source chain refused") }
        runCurrent()
        assertTrue(s.cardVisible)
        assertEquals(EpisodeSnapState.Phase.Idle, s.phase)
        var runs = 0
        s.snapThen { runs++ }        // and the next press works
        runCurrent()
        assertEquals(1, runs)
    }

    @Test
    fun animeServerChoiceSnapsWhenIdle() {
        runTest {
            var runs = 0
            val s = snap { true }
            s.snapThenOrNow { runs++ }
            advanceTimeBy(SNAP_DURATION_MS - 1)
            runCurrent()
            assertEquals("waits for the dust like a Watch on press", 0, runs)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, runs)
        }
    }

    @Test
    fun animeServerChoiceIsNeverDroppedMidSnap() {
        runTest {
            var runs = 0
            val s = snap { true }
            s.snapThen { }
            runCurrent()
            s.snapThenOrNow { runs++ }       // a busy snap must not swallow it
            assertEquals(1, runs)
        }
    }

    @Test
    fun screenClosingMidSnapNeverStartsPlayback() {
        val scope = TestScope(StandardTestDispatcher())
        var runs = 0
        val s = EpisodeSnapState(scope = scope, startDust = { true }, warn = { _, _ -> })
        s.snapThen { runs++ }
        scope.advanceTimeBy(SNAP_DURATION_MS / 2)
        scope.runCurrent()
        scope.cancel()               // the page went away
        scope.advanceTimeBy(SNAP_DURATION_MS * 2)
        scope.runCurrent()
        assertEquals(0, runs)
    }
}
