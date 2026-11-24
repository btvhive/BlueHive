package com.example.bluehive.singleShelfComponents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FreshPicksTest {
    private val row = (1..40).toList()

    @Test fun `the row's two pages of 20 come from one fetch`() {
        assertEquals((1..20).toList(), FreshPicks.slice(row, 0, 20))
        assertEquals((21..40).toList(), FreshPicks.slice(row, 1, 20))
        assertTrue(FreshPicks.slice(row, 2, 20).isEmpty())          // the row ends at 40
    }

    @Test fun `never more than 40 even if the server sends more`() {
        val big = (1..55).toList()
        assertEquals(40, FreshPicks.slice(big, 0, 20).size + FreshPicks.slice(big, 1, 20).size)
        assertTrue(FreshPicks.slice(big, 2, 20).isEmpty())
    }

    @Test fun `a short row fills what it has`() {
        val short = (1..27).toList()
        assertEquals((21..27).toList(), FreshPicks.slice(short, 1, 20))
        assertTrue(FreshPicks.slice(short, -1, 20).isEmpty())
    }

    @Test fun `window end parsed from the server's ISO time`() {
        assertEquals(Instant.parse("2026-11-23T08:00:00Z"), FreshPicks.parseInstant("2026-11-23T08:00:00+00:00"))
        assertNull(FreshPicks.parseInstant(null))
        assertNull(FreshPicks.parseInstant("not a time"))
    }
}
