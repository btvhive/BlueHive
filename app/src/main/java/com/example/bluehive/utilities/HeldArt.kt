package com.example.bluehive.utilities

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.example.bluehive.R

/**
 * The details pages' built-in raster art (and the button glows every page
 * shares), decoded once off the main thread and kept for the app's life.
 *
 * WHY (traced on the onn box, 2026-10-30): painterResource decodes through
 * the Resources drawable cache, which keeps nothing once a page has closed
 * and a garbage collection has run. Every other TV-page open then decoded all
 * of this again on the main thread inside its first frame -- backdrop_details
 * (1920x1080) 75 ms, backdrop_details_gradient (1280x815) 90 ms, the caps,
 * glows and title-card frames the rest: ~230 ms, the whole gap between the
 * ~350 ms and ~650 ms opens. Warming that cache from the application did not
 * help (still decoded every other open), so the bitmaps are held here and
 * drawn straight from it ([heldPainter]).
 *
 * About 14 MB of bitmap memory for the app's life, almost all of it the two
 * backdrops. [warm] runs on a background thread at start-up (AppWarmup).
 */
object HeldArt {

    private const val TAG = "HeldArt"

    /** Every raster drawable the details pages draw (vectors have their own cache). Checked by HeldArtTest. */
    internal val RASTER: IntArray = intArrayOf(
        R.drawable.backdrop_details,
        R.drawable.backdrop_details_gradient,
        R.drawable.button_focus_wide_glow,
        R.drawable.button_focus_wide_nonglow,
        R.drawable.button_focus_narrow_glow,
        R.drawable.button_focus_narrow_nonglow,
        R.drawable.extended_mini_left_upper_body,
        R.drawable.extended_mini_left_lower_body,
        R.drawable.mini_right_upper_body,
        R.drawable.mini_right_lower_body,
        R.drawable.left_upper_body,
        R.drawable.left_lower_body,
        R.drawable.right_upper_body,
        R.drawable.right_lower_body,
        R.drawable.titlecard_base,
        R.drawable.titlecard_focused,
        R.drawable.titlecard_shadow,
        R.drawable.episode_focus_glow,
        R.drawable.star,
    )

    @Volatile
    private var held: Map<Int, ImageBitmap> = emptyMap()

    /**
     * Decodes the art, as painterResource would (the app's density, no
     * theme), and keeps it. Safe off the main thread; a second call does
     * nothing.
     */
    fun warm(context: Context) {
        if (held.isNotEmpty()) return
        val resources = context.applicationContext.resources
        val decoded = HashMap<Int, ImageBitmap>(RASTER.size)
        for (id in RASTER) {
            try {
                val bitmap = (resources.getDrawable(id, null) as? BitmapDrawable)?.bitmap ?: continue
                decoded[id] = bitmap.asImageBitmap()
            } catch (e: Exception) {
                Log.w(TAG, "could not decode drawable $id", e)
            }
        }
        held = decoded
    }

    /** The held bitmap for [id], or null before [warm] has run (or for art it does not hold). */
    internal fun bitmap(@DrawableRes id: Int): ImageBitmap? = held[id]
}

/**
 * painterResource for the art [HeldArt] keeps: drawn from the held bitmap,
 * so a page never decodes it in its first frame. Before the warm-up has
 * finished, and for anything else, it is painterResource itself.
 */
@Composable
fun heldPainter(@DrawableRes id: Int): Painter {
    val bitmap = HeldArt.bitmap(id) ?: return painterResource(id)
    return remember(bitmap) { BitmapPainter(bitmap) }
}
