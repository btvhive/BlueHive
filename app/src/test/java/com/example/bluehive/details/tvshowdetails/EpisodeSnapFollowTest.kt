package com.example.bluehive.details.tvshowdetails

import androidx.compose.runtime.snapshots.Snapshot
import com.example.bluehive.testutil.MainSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 7 (v515): a Watch press recomposed the whole TV screen three times
 * (Idle -> Snapping -> Playing -> Idle), with the page blurred, because the
 * screen function read the snap's phase to key the wait for the playback's
 * end. The phase is followed in a flow now (followPlayback); this is what it
 * must still do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeSnapFollowTest {

    private fun TestScope.snap() = EpisodeSnapState(scope = this, startDust = { false }, warn = { _, _ -> })

    /** Let the coroutines run and the state writes reach snapshotFlow. */
    private fun TestScope.settle() {
        repeat(3) {
            runCurrent()
            Snapshot.sendApplyNotifications()
        }
        runCurrent()
    }

    @Test
    fun `the wait runs once each time a press reaches Playing`() = runTest {
        val s = snap()
        var waits = 0
        val follower = launch { s.followPlayback { waits++; s.finish() } }
        settle()
        assertEquals(0, waits)

        s.snapThen { }
        settle()
        assertEquals(1, waits)
        assertEquals(EpisodeSnapState.Phase.Idle, s.phase)

        s.snapThen { }
        settle()
        assertEquals(2, waits)
        follower.cancel()
    }

    @Test
    fun `leaving Playing some other way cancels the wait`() = runTest {
        val s = snap()
        var cancelled = false
        val follower = launch {
            s.followPlayback {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        settle()
        s.snapThen { }
        settle()
        assertFalse(cancelled)
        assertEquals(EpisodeSnapState.Phase.Playing, s.phase)

        s.finish()
        settle()
        assertTrue(cancelled)
        follower.cancel()
    }

    @Test
    fun `the screen's snap holder reads no phase while composing`() {
        val code = MainSource.code("details/tvshowdetails/EpisodeSnap.kt")
        val holder = code.substringAfter("internal fun rememberEpisodeSnap(").substringBefore("\n}\n")
        assertFalse(holder.contains("snap.phase"))
        assertTrue(holder.contains("followPlayback"))
    }

    @Test
    fun `the card's read-back runs off the main thread`() {
        val code = MainSource.code("details/tvshowdetails/EpisodeSnap.kt")
        assertTrue(Regex("""withContext\(Dispatchers\.Default\)\s*\{\s*hardware\.copy""").containsMatchIn(code))
    }

    @Test
    fun `the overlay leaves the renderer and its fade to their own scopes`() {
        val code = MainSource.code("details/tvshowdetails/EpisodeSourceOverlay.kt")
        val overlay = code.substringAfter("internal fun BoxScope.EpisodeSourceOverlay(").substringBefore("\n}\n")
        assertFalse(overlay.contains("if (snap.rendererWanted)"))
        assertFalse(Regex("""val\s+dim\s+by\s+animate""").containsMatchIn(overlay))
        assertTrue(overlay.contains("graphicsLayer { alpha = dim.value }"))
    }
}
