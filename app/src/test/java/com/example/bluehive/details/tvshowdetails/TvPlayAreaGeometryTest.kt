package com.example.bluehive.details.tvshowdetails

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The right caps line up with the recommended row's; a wrong declaration order would make them 0. */
class TvPlayAreaGeometryTest {

    @Test
    fun `the mini caps' right edge meets the recommended row's right cap`() {
        assertEquals(894.9f, RIGHT_CAP_X.value, 0.001f)
        assertEquals(925.4f, (RIGHT_CAP_X + 30.5.dp).value, 0.001f)
    }

    @Test
    fun `the episode row still ends 17 dp under the caps`() {
        assertEquals((RIGHT_CAP_X + 17.dp).value, EPISODE_ROW_END.value, 0.001f)
    }
}
