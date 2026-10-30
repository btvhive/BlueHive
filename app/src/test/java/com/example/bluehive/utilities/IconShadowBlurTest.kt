package com.example.bluehive.utilities

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v516: below API 31 Modifier.blur draws nothing, but it still added a layer
 * under every shadowed icon (Rewind, Forward, Search) on the Fire TV sticks.
 * The icon shadow is blurred only where blur draws.
 */
class IconShadowBlurTest {

    @Test
    fun `blur draws from API 31`() {
        assertFalse(blurDrawsOn(25))   // Fire TV, Android 7
        assertFalse(blurDrawsOn(28))   // Fire TV, Android 9
        assertFalse(blurDrawsOn(30))
        assertTrue(blurDrawsOn(31))    // the onn box
        assertTrue(blurDrawsOn(34))
    }

    @Test
    fun `the button's only blur is behind the API check`() {
        val code = MainSource.code("utilities/ModularButton.kt")
        assertEquals(1, Regex("""\.blur\(""").findAll(code).count())
        assertTrue(code.contains("if (blurDrawsOn(Build.VERSION.SDK_INT)) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier"))
    }
}
