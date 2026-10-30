package com.example.bluehive.details.shared.layout

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v528: the details pages' backdrop fade is backdrop_details_fade -- the
 * original backdrop_details_gradient rebuilt without its grain and stepping
 * (tools/details-art/make_backdrop_fade.py). The original stays as its source.
 */
class BackdropFadeTest {

    private val res = File("src/main/res/drawable-xhdpi")

    @Test
    fun `the new fade is a lossless image the original's size`() {
        val b = File(res, "backdrop_details_fade.webp").readBytes()
        assertEquals("VP8L", String(b, 12, 4))
        val bits = (b[21].toInt() and 0xFF) or ((b[22].toInt() and 0xFF) shl 8) or
            ((b[23].toInt() and 0xFF) shl 16) or ((b[24].toInt() and 0xFF) shl 24)
        assertEquals(1280, (bits and 0x3FFF) + 1)
        assertEquals(815, ((bits shr 14) and 0x3FFF) + 1)
    }

    @Test
    fun `the pages draw the new fade, and the original is kept`() {
        val backdrop = MainSource.code("details/shared/layout/DetailsBackdrop.kt")
        assertTrue(backdrop.contains("heldPainter(R.drawable.backdrop_details_fade)"))
        assertTrue(!backdrop.contains("R.drawable.backdrop_details_gradient"))
        assertTrue(File(res, "backdrop_details_gradient.webp").exists())
    }
}
