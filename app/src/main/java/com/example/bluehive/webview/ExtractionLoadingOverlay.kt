package com.example.bluehive.webview

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.R
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.*




/**
 * Process-wide cache for the loading-overlay bee logo.
 *
 * blue_bee.png is decoded through BitmapFactory. On 2 GB hardware the overlay
 * appears at a memory-pressure peak (heavy details screen + trailer teardown +
 * extractor spinning up), where the decode can return null → Compose casts null
 * to BitmapDrawable → NPE → the whole app crashes back to the home screen.
 *
 * prime() is called from the details screens as they first compose (memory is
 * comparatively free then) and decodes the bitmap ONCE, off the main thread. It
 * only caches on success, so a failed attempt simply retries on the next entry.
 */
object BeeLogo {
    @Volatile
    var bitmap: ImageBitmap? = null
        private set

    fun prime(context: Context) {
        if (bitmap != null) return
        bitmap = try {
            ImageBitmap.imageResource(context.resources, R.drawable.blue_bee)
        } catch (t: Throwable) {
            Log.w("scraper", "BeeLogo prime failed (low memory): ${t.message}")
            null
        }
    }
}

/**
 * @param status the live, per-milestone line ("Finding the best server…").
 * @param note   a persistent second line for something the user should know that
 *               ISN'T a milestone — currently the retry banner. It has to be its
 *               own parameter rather than part of [status]: the extractor
 *               broadcasts a new status every few seconds, so a retry message
 *               folded into that string would be overwritten and vanish a moment
 *               after appearing. Rendered in amber so it reads as a warning
 *               rather than progress.
 */
/**
 * Hold focus while an extraction runs, and let only BACK out.
 *
 * WHY IT IS NEEDED: ExtractionLoadingOverlay paints a full-screen scrim and
 * nothing else. It takes no focus, so the control underneath keeps it, and on a
 * d-pad every key still reaches the screen behind the scrim — including BACK,
 * which never gets as far as the BackHandler that would cancel the extraction.
 *
 * WHAT THAT COST, measured on the TV detail screen: cancelling took THREE
 * presses. The first went to the focused episode tile, whose own Key.Back
 * branch closed the episode overlay and consumed the key. The second went to
 * the same tile, took its next branch and moved focus to the season column,
 * consuming again. Only the third landed on a control with no Back handler of
 * its own, fell through to the dispatcher, and stopped the extraction. From the
 * outside that reads as the overlay losing focus and wandering off on its own.
 *
 * BACK AND ESCAPE PASS THROUGH ON PURPOSE — `false` means not consumed. They
 * are the only way out, and swallowing them would turn this into a trap with no
 * exit on a remote that has no other keys. Everything else is swallowed so the
 * screen underneath cannot be navigated while it is covered.
 *
 * The focus request is a frame late because the Box has to be laid out before
 * it can take focus; a requestFocus() in the same frame is simply dropped.
 */
@Composable
fun ExtractionFocusTrap(content: @Composable () -> Unit) {
    val trap = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        try { trap.requestFocus() } catch (_: Exception) {}
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(trap)
            .focusable()
            .onPreviewKeyEvent { ev ->
                when (ev.key) {
                    Key.Back, Key.Escape -> false
                    else -> true
                }
            }
    ) {
        content()
    }
}

/**
 * @param chain  (attempt, planned) while the backend works down the source
 *               chain -- shown as the headline "Browsing source 2 of 4: Royal
 *               Jelly", with [status] beneath it as the detail. null for waits
 *               that are not a chain (anime, server check).
 * @param source the source being tried, by its button label, for the headline.
 *
 * THE SAME LAYOUT ON EVERY TV (v576). Every text line has a fixed slot -- the
 * headline, a two-line detail and a two-line warning -- whether or not it is
 * showing, so the block is always the same height and sits exactly centred:
 * nothing jumps when a warning appears or a line wraps. Sizes are in dp and
 * sp, which scale with the screen, so the proportions match on any set.
 */
@Composable
fun ExtractionLoadingOverlay(
    status: String,
    stage: Int,
    note: String? = null,
    chain: Pair<Int, Int>? = null,
    source: String? = null,
) {
    val inf = rememberInfiniteTransition(label = "loader")

    // Read ONLY inside graphicsLayer{} below (the stage dots' pulse) -- the
    // draw/render phase -- so it NEVER triggers recomposition.
    val glow by inf.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    // progress/stage/status only change at stage transitions, not per-frame — fine to read in composition.
    val progress by animateFloatAsState(
        targetValue = stage / 5f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBF000000)),
        contentAlignment = Alignment.Center
    ) {
        HoneycombBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "B L U E H I V E",
                color = Color(0xFF4477FF).copy(alpha = 0.65f),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                // 18 px higher on the TV (9 dp; 8 px in v576, 10 more in v577),
                // the owner's call; an offset, so nothing else moves.
                modifier = Modifier.offset(y = TITLE_RAISE),
            )

            Spacer(Modifier.height(30.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {

                // The globe (GlobeLoader.kt, v575): a wireframe globe turning,
                // the bee riding on it, beams flying out to servers -- the web
                // behind the wait. It replaced the spinning hive.
                // Use the cache primed on details-screen entry. If it somehow
                // isn't primed yet, fall back to a guarded live decode so the
                // overlay can never crash — worst case the logo is briefly absent.
                val ctx = LocalContext.current
                val beeBitmap = remember {
                    BeeLogo.bitmap ?: runCatching {
                        ImageBitmap.imageResource(ctx.resources, R.drawable.blue_bee)
                    }.getOrNull()
                }
                GlobeLoader(beeBitmap)
            }

            Spacer(Modifier.height(38.dp))

            // WHICH SOURCE, OF HOW MANY, AND WHICH. A failover used to restart
            // the same stage words with nothing to say a source had died and
            // another had taken over, so a long chain read as one stuck load.
            // "of N" is the attempts this chain can actually make
            // (SourceCatalog.plannedAttempts); a chain of one names the source.
            Box(Modifier.height(HEADLINE_SLOT), contentAlignment = Alignment.Center) {
                if (chain != null) {
                    val (attempt, planned) = chain
                    AnimatedContent(
                        targetState = chainHeadline(attempt, planned, source),
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        label = "chain"
                    ) { h ->
                        Text(
                            h,
                            color         = Color.White.copy(0.95f),
                            fontSize      = 19.sp,
                            fontWeight    = FontWeight.Normal,
                            textAlign     = TextAlign.Center,
                            letterSpacing = 0.3.sp,
                            maxLines      = 1,
                            modifier      = Modifier.widthIn(max = 520.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Box(Modifier.height(STATUS_SLOT), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "status"
                ) { s ->
                    Text(
                        s,
                        // Under a headline the stage words are the detail, so they
                        // step back; alone they are the message, as before.
                        color         = Color.White.copy(if (chain != null) 0.6f else 0.88f),
                        fontSize      = if (chain != null) 14.sp else 17.sp,
                        fontWeight    = FontWeight.Light,
                        textAlign     = TextAlign.Center,
                        letterSpacing = 0.3.sp,
                        maxLines      = 2,
                        modifier      = Modifier.widthIn(max = 340.dp)
                    )
                }
            }

            // Persistent warning line (a source failed). Fades in and then
            // STAYS, independent of the status line churning above it; its
            // slot is kept even when empty, so it never pushes anything.
            Box(Modifier.height(NOTE_SLOT), contentAlignment = Alignment.TopCenter) {
                if (note != null) {
                    AnimatedContent(
                        targetState = note,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        label = "note"
                    ) { n ->
                        Text(
                            n,
                            color         = Color(0xFFFFB74D).copy(0.95f),
                            fontSize      = 13.sp,
                            fontWeight    = FontWeight.Normal,
                            textAlign     = TextAlign.Center,
                            letterSpacing = 0.3.sp,
                            maxLines      = 2,
                            modifier      = Modifier.widthIn(max = 340.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                Modifier
                    .width(220.dp)
                    .height(2.dp)
                    .background(Color.White.copy(0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0044FF), Color(0xFFAA33FF))
                            )
                        )
                )
            }

            Spacer(Modifier.height(18.dp))

            // Stage dots — colors come from `stage` (not animated). The active dot
            // pulses via graphicsLayer.alpha = glow, so glow is NEVER read in
            // composition. THIS is what was forcing 60fps recomposition before.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { i ->
                    val active = i == stage - 1
                    val filled = i < stage
                    val dotColor = when {
                        active -> Color(0xFF88BBFF)
                        filled -> Color(0xFF4466FF)
                        else   -> Color.White.copy(0.12f)
                    }
                    Box(
                        Modifier
                            .size(if (active) 7.dp else 5.dp)
                            .graphicsLayer { if (active) alpha = glow }
                            .background(dotColor, CircleShape)
                    )
                }
            }
        }
    }
}

/** "Browsing source 2 of 4: Royal Jelly"; a chain of one, "Browsing Royal Jelly". */
internal fun chainHeadline(attempt: Int, planned: Int, source: String?): String {
    val of = maxOf(planned, attempt)
    val name = source?.takeIf { it.isNotBlank() }
    return when {
        of > 1 && name != null -> "Browsing source $attempt of $of: $name"
        of > 1                 -> "Browsing source $attempt of $of"
        name != null           -> "Browsing $name"
        else                   -> "Browsing for your stream"
    }
}

/** How far the title is lifted: 18 px on the 2 px-per-dp TV. */
internal val TITLE_RAISE = (-9).dp

/** The fixed text slots that keep the block one height (see the overlay's doc). */
internal val HEADLINE_SLOT = 28.dp
internal val STATUS_SLOT = 44.dp
internal val NOTE_SLOT = 36.dp

@Composable
private fun HoneycombBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val hexSize = 34.dp.toPx()
        val hexW    = hexSize * sqrt(3f)
        val hexH    = hexSize * 2f
        val cols    = (size.width  / hexW).toInt() + 2
        val rows    = (size.height / (hexH * 0.75f)).toInt() + 2

        for (row in -1..rows) {
            for (col in -1..cols) {
                val xOff = if (row % 2 == 0) 0f else hexW / 2f
                val cx   = col * hexW + xOff
                val cy   = row * hexH * 0.75f

                val path = Path().apply {
                    for (i in 0 until 6) {
                        val a = Math.toRadians((60.0 * i) - 30.0)
                        val x = cx + hexSize * cos(a).toFloat()
                        val y = cy + hexSize * sin(a).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(
                    path,
                    Color(0xFF3355FF).copy(alpha = 0.045f),
                    style = Stroke(0.6.dp.toPx())
                )
            }
        }
    }
}