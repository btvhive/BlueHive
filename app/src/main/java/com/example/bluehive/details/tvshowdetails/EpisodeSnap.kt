package com.example.bluehive.details.tvshowdetails

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.bluehive.details.shared.playback.PlaybackSession
import com.exjunk.lib.thanos.vanish.VanishGLSurfaceView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long the dust takes to blow away. Playback starts only after it. */
internal const val SNAP_DURATION_MS = 1000L   // the user's call, 2026-10-27 (was 2 s)

/** The dust's first frames overlap the card before the card hides, so nothing blinks. */
private const val SNAP_HIDE_DELAY_MS = 120L

/** A capture that takes longer than this is abandoned: the press goes ahead without dust. */
private const val SNAP_CAPTURE_TIMEOUT_MS = 600L

/** After the press's action, how long to wait for the extraction to begin before giving the card back. */
private const val SNAP_START_WAIT_MS = 3000L

private const val TAG = "EpisodeSnap"

/**
 * The episode overlay's snap: a Watch on ... press turns the card to dust
 * (ThanosEffect's particle renderer) and the press's action -- starting the
 * source chain -- runs only once the dust has finished.
 *
 * ONE PATH, THREE PHASES, and every way out of each is handled:
 *
 *   Idle     -> the card is shown and takes presses.
 *   Snapping -> the card is captured and handed to the particle renderer;
 *               the card hides once the dust covers it; keys are swallowed.
 *               After [SNAP_DURATION_MS] the action runs, exactly once.
 *   Playing  -> the card stays hidden while the extraction runs; when it ends
 *               (the player opening, an error, a cancel) the renderer is reset
 *               and the card comes back, focus still on the pressed button.
 *
 * What it guards against:
 *   - a second press while snapping or playing: ignored (phase check);
 *   - no renderer yet, a capture that fails or hangs: no dust, the card hides
 *     and the action runs at once -- the snap can delay playback, never block it;
 *   - the action throwing: the card comes back and the phase returns to Idle;
 *   - the screen closing mid-snap: the scope is cancelled with the screen, so
 *     the action never runs for a page that is gone;
 *   - an extraction that never begins: the card returns after [SNAP_START_WAIT_MS].
 *
 * WHY IT CAPTURES THE CARD ITSELF: the library's own capture (VanishEffect's
 * vanishable) replays the card's drawing into an android.graphics.Picture,
 * and a Picture cannot hold layered content -- the still and the focus glow
 * (both drawn on graphics layers) vanished from the card (v463). Recording
 * the card into a Compose GraphicsLayer and taking its bitmap keeps all of
 * it, and the card draws normally the rest of the time.
 */
@Stable
internal class EpisodeSnapState(
    private val scope: CoroutineScope,
    /** Starts the dust; false when there is nothing to animate with. Swapped in tests. */
    private val startDust: suspend (EpisodeSnapState) -> Boolean = { it.startRendererDust() },
    /** android.util.Log outside tests, where it does not exist. */
    private val warn: (String, Throwable) -> Unit = { message, t -> Log.w(TAG, message, t) },
) {

    enum class Phase { Idle, Snapping, Playing }

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** False while the dust (or the loading screen) has the card's place. */
    var cardVisible by mutableStateOf(true)
        private set

    /** The particle renderer; null until its surface exists. */
    var renderer: VanishGLSurfaceView? = null

    internal var layer: GraphicsLayer? = null
    internal var bounds: Rect? = null

    /**
     * Snap the card, then run [action]. A press while a snap or its playback
     * is in progress does nothing.
     */
    fun snapThen(action: () -> Unit) {
        if (phase != Phase.Idle) return
        phase = Phase.Snapping
        scope.launch {
            val dust = try {
                withTimeoutOrNull(SNAP_CAPTURE_TIMEOUT_MS) { startDust(this@EpisodeSnapState) } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warn("capture failed; playing without the snap", t)
                false
            }
            if (dust) {
                delay(SNAP_HIDE_DELAY_MS)
                cardVisible = false
                delay(SNAP_DURATION_MS - SNAP_HIDE_DELAY_MS)
            } else {
                cardVisible = false
            }
            phase = Phase.Playing
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warn("the press's action failed", t)
                finish()
            }
        }
    }

    /**
     * Playing: wait for the extraction to begin (or fail at once), then to
     * end, and give the card back.
     */
    suspend fun awaitPlaybackEnd(session: PlaybackSession) {
        withTimeoutOrNull(SNAP_START_WAIT_MS) {
            snapshotFlow { session.isExtracting || session.extractionError != null }.first { it }
        }
        snapshotFlow { session.isExtracting }.first { !it }
        finish()
    }

    private fun finish() {
        try {
            renderer?.resetAnimation()
        } catch (t: Throwable) {
            warn("renderer reset failed", t)
        }
        cardVisible = true
        phase = Phase.Idle
    }

    /** Hands the card's picture to the renderer; false when there is nothing to animate with. */
    internal suspend fun startRendererDust(): Boolean {
        val gl = renderer ?: return false
        val layer = layer ?: return false
        val box = bounds ?: return false
        if (box.width < 1f || box.height < 1f) return false
        var bitmap = layer.toImageBitmap().asAndroidBitmap()
        // The renderer uploads it as a GL texture, which a HARDWARE bitmap cannot be.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        }
        gl.startAnimationWithBitmap(
            bitmap,
            android.graphics.Rect(
                box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()
            ),
        )
        return true
    }
}

/** The overlay's snap state, and the wait for the playback it started. */
@Composable
internal fun rememberEpisodeSnap(session: PlaybackSession): EpisodeSnapState {
    val scope = rememberCoroutineScope()
    val snap = remember { EpisodeSnapState(scope) }
    snap.layer = rememberGraphicsLayer()
    LaunchedEffect(snap.phase == EpisodeSnapState.Phase.Playing) {
        if (snap.phase == EpisodeSnapState.Phase.Playing) snap.awaitPlaybackEnd(session)
    }
    return snap
}

/**
 * On the card: records its drawing into the snap's layer (and draws that,
 * so the card looks exactly as before), tracks its bounds for the renderer,
 * hides it while the dust has its place, and swallows keys while snapping.
 */
internal fun Modifier.episodeSnapCard(snap: EpisodeSnapState): Modifier = this
    .onPreviewKeyEvent { snap.phase == EpisodeSnapState.Phase.Snapping }
    .graphicsLayer { alpha = if (snap.cardVisible) 1f else 0f }
    .onGloballyPositioned { snap.bounds = it.boundsInRoot() }
    .drawWithContent {
        val layer = snap.layer
        if (layer == null) {
            drawContent()
        } else {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }
