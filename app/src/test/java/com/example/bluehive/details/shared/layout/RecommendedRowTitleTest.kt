package com.example.bluehive.details.shared.layout

import org.junit.Assert.assertEquals
import org.junit.Test

/** The peek row's heading names the kind of title the page is about. */
class RecommendedRowTitleTest {

    @Test
    fun `movies and shows`() {
        assertEquals("More movies you may like", recommendedRowTitle("movie", isAnime = false))
        assertEquals("More shows you may like", recommendedRowTitle("tv", isAnime = false))
    }

    @Test
    fun `anime movies and anime series`() {
        assertEquals("More anime movies you may like", recommendedRowTitle("movie", isAnime = true))
        assertEquals("More anime series you may like", recommendedRowTitle("tv", isAnime = true))
    }

    @Test
    fun `the type is matched without regard to case`() {
        assertEquals("More movies you may like", recommendedRowTitle("Movie", isAnime = false))
    }
}
