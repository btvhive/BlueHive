package com.example.bluehive

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile picker shows the app version bottom-left, two lines: "BlueHive"
 * over "Version <name> · <build date>" (the owner's design 10, 2026-10-31),
 * so the build can be read off the TV without opening Settings.
 */
class ProfileVersionLabelTest {

    @Test
    fun `the second line reads version then build date`() {
        assertEquals("Version 556  ·  Oct 31, 2026", versionLine("556", "Oct 31, 2026"))
    }

    @Test
    fun `the build stamps its date and the label reads the build's own version`() {
        assertTrue(Regex("""[A-Z][a-z]{2} \d{1,2}, \d{4}""").matches(BuildConfig.BUILD_DATE))
        val code = MainSource.code("ProfileScreenActivity.kt")
        assertTrue(code.contains("text       = versionLine(BuildConfig.VERSION_NAME, BuildConfig.BUILD_DATE),"))
        assertTrue(code.contains("text       = \"BlueHive\","))
        // Level with the Sign Out / Manage strip, at the far left.
        assertTrue(code.contains("val VERSION_LABEL_Y: Dp = BOTTOM_STRIP_Y"))
    }
}
