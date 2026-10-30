package com.example.bluehive.utilities

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix 2 (v515): every OK press recomposed the whole ModularButton for each
 * frame of the label's press-in (~20 frames), on every header and Watch
 * button of both pages. The label's offset is read while placing now, through
 * offsetWithY -- which must land on exactly the pixels offset(x, y) did.
 */
class ButtonLabelPressTest {

    private val button = MainSource.code("utilities/ModularButton.kt")

    @Test
    fun `the label offset is a State read while placing`() {
        assertFalse(Regex("""val\s+textYOffset\s+by\s+animate""").containsMatchIn(button))
        assertFalse(Regex("""\.offset\([^)]*textYOffset""").containsMatchIn(button))
        assertEquals(3, Regex("""\.offsetWithY\([^)]*\)\s*\{\s*textYOffset\.value""").findAll(button).count())
    }

    @Test
    fun `offsetWithY lands on offset's pixels, each axis rounded on its own`() {
        val onn = Density(2f)
        assertEquals(IntOffset(8, 3), offsetPx(onn, 4.dp, 1.5.dp))
        assertEquals(IntOffset(8, -3), offsetPx(onn, 4.dp, (-1.5).dp))
        // Fire TV sticks run at 1.5: halves round up, as roundToPx does.
        val fire = Density(1.5f)
        assertEquals(IntOffset(6, 2), offsetPx(fire, 4.dp, 1.dp))
        assertEquals(IntOffset(with(fire) { 27.75.dp.roundToPx() }, with(fire) { 2.4.dp.roundToPx() }), offsetPx(fire, 27.75.dp, 2.4.dp))
    }

    @Test
    fun `offsetPx is roundToPx on each axis for any value`() {
        for (density in listOf(1f, 1.5f, 2f, 2.75f)) {
            val d = Density(density)
            var v = -20f
            while (v <= 20f) {
                val x = v.dp
                val y = (v * 0.37f).dp
                assertEquals(IntOffset(with(d) { x.roundToPx() }, with(d) { y.roundToPx() }), offsetPx(d, x, y))
                v += 0.25f
            }
        }
    }
}
