package com.example.bluehive.details.tvshowdetails

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 1 (v515): Left / Right in the episode row recomposed the two cards it
 * touched on every frame of the title's bounce, because the animated offset
 * was read while composing. It is read while placing now (offsetWithY), and
 * the air date is formatted once per date, not on every recomposition.
 */
class EpisodeTitleBounceTest {

    private val card = MainSource.code("details/tvshowdetails/EpisodeCard.kt")

    @Test
    fun `the bounce is kept as a State, not delegated into a composition read`() {
        assertFalse(Regex("""val\s+titleYOffset\s+by\s+animate""").containsMatchIn(card))
        assertTrue(Regex("""val\s+titleYOffset\s*=\s*animateDpAsState""").containsMatchIn(card))
    }

    @Test
    fun `the title reads the bounce while placing`() {
        assertTrue(card.contains(".offsetWithY(4.dp) { titleYOffset.value }"))
        assertFalse(Regex("""\.offset\([^)]*titleYOffset""").containsMatchIn(card))
    }

    @Test
    fun `no animated value of the card is delegated with by`() {
        assertFalse(Regex("""by\s+animate\w+AsState""").containsMatchIn(card))
    }

    @Test
    fun `the air date is formatted once per date`() {
        assertTrue(card.contains("remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }"))
    }

    @Test
    fun `the remembered date is the same text as before`() {
        // What the card shows did not change, only how often it is worked out.
        assertTrue(formatEpisodeAirDate("2022-02-03") == "02/03/2022")
        assertTrue(formatEpisodeAirDate(null).isEmpty())
    }
}
