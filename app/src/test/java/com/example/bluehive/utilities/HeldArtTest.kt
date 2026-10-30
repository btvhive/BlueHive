package com.example.bluehive.utilities

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v517: every other TV-page open decoded the page's built-in art inside its
 * first frame (~230 ms on the onn box) because the Resources cache had let it
 * go. HeldArt keeps it decoded and the pages draw it through heldPainter.
 * This holds the list to every raster drawable the pages draw, keeps them
 * off painterResource, and checks the warm-up starts it.
 */
class HeldArtTest {

    private val drawableRef = Regex("""R\.drawable\.(\w+)""")

    /** Drawable names with a bitmap file (webp / png / jpg) in any density folder. */
    private fun isRaster(name: String): Boolean =
        File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }!!
            .any { dir -> listOf("webp", "png", "jpg").any { File(dir, "$name.$it").exists() } }

    private val pageSources: Map<String, String> =
        MainSource.all().filterKeys { it.startsWith("details/") || it == "utilities/RecommendedMediaSection.kt" }

    private fun usedByTheDetailsPages(): Set<String> =
        pageSources.values.flatMap { code -> drawableRef.findAll(code).map { it.groupValues[1] }.toList() }.toSet()

    private val held: Set<String> =
        drawableRef.findAll(MainSource.code("utilities/HeldArt.kt")).map { it.groupValues[1] }.toSet()

    @Test
    fun `every raster drawable the details pages draw is held`() {
        val raster = usedByTheDetailsPages().filter(::isRaster).toSet()
        assertTrue("found no raster art -- the scan is broken", raster.contains("backdrop_details"))
        assertEquals(emptySet<String>(), raster - held)
    }

    @Test
    fun `it holds only raster art the pages use`() {
        assertEquals(emptySet<String>(), held.filterNot(::isRaster).toSet())
        assertEquals(emptySet<String>(), held - usedByTheDetailsPages())
    }

    @Test
    fun `the pages draw held art through heldPainter, never painterResource`() {
        val raw = Regex("""painterResource\(\s*(?:id\s*=\s*)?R\.drawable\.(\w+)""")
        val offenders = pageSources.flatMap { (path, code) ->
            raw.findAll(code).map { it.groupValues[1] }.filter { it in held }.map { "$path: $it" }.toList()
        }
        assertEquals(emptyList<String>(), offenders)
        // The buttons' glows are held too, and drawn the same way.
        assertTrue(MainSource.code("utilities/ModularButton.kt").contains("painter = heldPainter(it),"))
    }

    @Test
    fun `the warm-up starts it on the app-wide scope`() {
        val warmup = MainSource.code("Appwarmup.kt")
        assertTrue(warmup.contains("imagePrefetchScope.launch { com.example.bluehive.utilities.HeldArt.warm(app) }"))
    }
}
