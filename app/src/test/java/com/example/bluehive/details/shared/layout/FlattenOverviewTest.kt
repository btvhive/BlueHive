package com.example.bluehive.details.shared.layout

import org.junit.Assert.assertEquals
import org.junit.Test

/** An overview in paragraphs is shown as one. */
class FlattenOverviewTest {

    @Test
    fun `paragraphs join with one space`() {
        assertEquals(
            "MegaTokyo 2033: Tokyo was left flattened. A new city, MegaTokyo, was then recreated.",
            flattenOverview("MegaTokyo 2033: Tokyo was left flattened.\n\nA new city, MegaTokyo, was then recreated."),
        )
    }

    @Test
    fun `windows line breaks and stray spaces around them go too`() {
        assertEquals("One. Two. Three.", flattenOverview("One. \r\n\r\n  Two.\rThree.\n"))
    }

    @Test
    fun `an overview in one paragraph is unchanged`() {
        val text = "A magical, singing orange unicorn holds his cast hostage."
        assertEquals(text, flattenOverview(text))
    }
}
