package com.example.bluehive.details.shared.layout

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The page's blur behind the search keyboard and the episode overlay -- the
 * same 10 px blur as hiveBackdropBlur, on API 31+ (older boxes, Fire TV,
 * keep their scrims and get no layer at all).
 *
 * [active] is read in the layer, not while composing (v515): opening or
 * closing redraws the page instead of recomposing the whole screen function,
 * and the blur is made once, not on every recomposition while it is on.
 */
fun Modifier.detailsBackdropBlur(active: () -> Boolean): Modifier =
    if (blurSupported(Build.VERSION.SDK_INT)) {
        graphicsLayer { renderEffect = if (active()) PageBlur else null }
    } else this

/** RenderEffect arrived in API 31. Pure, so it is tested. */
internal fun blurSupported(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

private val PageBlur: RenderEffect by lazy {
    android.graphics.RenderEffect
        .createBlurEffect(10f, 10f, android.graphics.Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
}
