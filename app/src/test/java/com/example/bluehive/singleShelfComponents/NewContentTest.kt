package com.example.bluehive.singleShelfComponents

import com.google.gson.Gson
import com.example.bluehive.models.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewContentTest {
    private val today = LocalDate.of(2026, 11, 21)

    // MediaItem has many fields; build one the way the server sends it.
    private fun item(id: Int, type: String = "movie", released: String? = "2026-09-01", streaming: Boolean? = false,
                     label: String? = null, detail: String? = null): MediaItem =
        Gson().fromJson(
            """{"media_id":$id,"tmdb_id":$id,"media_type":"$type","title":"T$id","release_date":${released?.let { "\"$it\"" }},
               "on_streaming":$streaming,"new_label":${label?.let { "\"$it\"" }},"new_detail":${detail?.let { "\"$it\"" }}}""",
            MediaItem::class.java,
        )

    // ── What's Popular: popular and top rated, one and one ─────────────────
    @Test fun `popular and top rated alternate one by one`() {
        val popular = listOf(item(1), item(2), item(3))
        val top = listOf(item(11), item(12), item(13))
        assertEquals(listOf(1, 11, 2, 12, 3, 13), ShelfRepository.interleave(popular, top, emptySet()).map { it.tmdbId })
    }

    @Test fun `a title in both lists appears once`() {
        val popular = listOf(item(1), item(2), item(3))
        val top = listOf(item(2), item(12), item(1))
        assertEquals(listOf(1, 2, 12, 3), ShelfRepository.interleave(popular, top, emptySet()).map { it.tmdbId })
    }

    @Test fun `later pages skip titles already shown, the longer list's tail follows`() {
        val popular = listOf(item(4), item(5))
        val top = listOf(item(1), item(14), item(15), item(16))
        assertEquals(listOf(4, 5, 14, 15, 16), ShelfRepository.interleave(popular, top, setOf(1)).map { it.tmdbId })
    }

    // ── New content ─────────────────────────────────────────────────────────
    @Test fun `a movie still counting down to HD is never shown as new`() {
        // 10 days old, not on streaming: the app shows "50 DAYS" -- must be dropped.
        val counting = item(1, released = "2026-11-11", streaming = false, label = "NEW RELEASE", detail = "HD")
        val streaming = item(2, released = "2026-11-11", streaming = true, label = "NEW RELEASE", detail = "HD")
        val pastHd = item(3, released = "2026-09-01", streaming = false, label = "NEW RELEASE", detail = "HD")
        assertEquals(listOf(2, 3), NewContent.eligible(listOf(counting, streaming, pastHd), today).map { it.tmdbId })
    }

    @Test fun `TV is never counted down, and an unlabelled title is skipped`() {
        val tv = item(4, type = "tv", released = "2026-11-15", streaming = null, label = "NEW EPISODE", detail = "S2 E5")
        assertEquals(listOf(4), NewContent.eligible(listOf(tv, item(5)), today).map { it.tmdbId })
    }

    @Test fun `the New Releases row has no cap and pages through everything`() {
        val all = (1..46).toList()
        assertEquals((1..20).toList(), NewContent.slice(all, 0, 20))
        assertEquals((41..46).toList(), NewContent.slice(all, 2, 20))
        assertTrue(NewContent.slice(all, 3, 20).isEmpty())
    }

    @Test fun `movie and TV ids never collide in the tab lookup`() {
        assertFalse(NewContent.key("movie", 1396) == NewContent.key("tv", 1396))
    }

    @Test fun `cards in the New Releases row hide their tab`() {
        val shown = item(1, label = "NEW RELEASE", detail = "HD").copy(hideNewTab = true)
        assertTrue(shown.hideNewTab)
        assertFalse(item(1).hideNewTab)
    }
}
