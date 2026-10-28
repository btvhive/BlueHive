package com.example.bluehive.details.shared.layout

import org.junit.Assert.assertEquals
import org.junit.Test

/** The recommended row is named for what the page is. */
class RecommendedRowTitleTest {

    @Test
    fun `films and shows`() {
        assertEquals("Movies You May Also Like", recommendedRowTitle("movie", "Adventure, Animation", "en"))
        assertEquals("TV Shows You May Also Like", recommendedRowTitle("tv", "Animation, Comedy", "en"))
    }

    @Test
    fun `japanese animation is anime`() {
        assertEquals("Anime Movies You May Also Like", recommendedRowTitle("movie", "Animation, Fantasy", "ja"))
        assertEquals("Anime Series You May Also Like", recommendedRowTitle("tv", "Animation, Action & Adventure", "ja"))
    }

    @Test
    fun `japanese live action is not anime`() {
        assertEquals("TV Shows You May Also Like", recommendedRowTitle("tv", "Drama", "ja"))
    }

    @Test
    fun `missing details fall back to the page type`() {
        assertEquals("Movies You May Also Like", recommendedRowTitle("movie", null, null))
        assertEquals("TV Shows You May Also Like", recommendedRowTitle("TV", null, null))
    }
}
