package com.example.bluehive.webview.hls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bluehive.utilities.AppTypography

// ── Up Next (v580) ──────────────────────────────────────────────────────────
//
// The last ten seconds of an episode show the next one -- its still, its
// number and title -- over the player while the credits keep playing, and the
// details screen opens it through the usual backend extraction when this one
// ends. Every third episode in a row nobody touches the remote for, a "Still
// watching?" prompt comes up thirty seconds before the end; unanswered, the
// card still shows, but playback stops when the episode does.
//
// The clock is the STREAM's, never the wall's: the countdown is the position
// against the duration, so a pause or a stall holds it, and a seek moves it.

/** The card shows over the episode's last this-many ms. */
internal const val UP_NEXT_WINDOW_MS = 10_000L

/** The Still Watching prompt shows this long before the end, so for 20 s before the card. */
internal const val STILL_WATCHING_LEAD_MS = 30_000L

/** The episode, counted in a row without a remote press, that asks "Still watching?". */
internal const val UNATTENDED_LIMIT = 3

/** Shorter than this is no episode (a broken or trailer-length stream): no card. */
internal const val UP_NEXT_MIN_DURATION_MS = 5L * 60_000L

/** How often the player's position is read. */
internal const val UP_NEXT_TICK_MS = 250L

internal enum class UpNextPhase { Hidden, StillWatching, Card }

/** The next episode, from the player's intent: none at a season's end. */
internal data class NextEpisode(
    val season: Int,
    val episode: Int,
    val title: String?,
    /** TMDB's w300 still as the episodes API sends it: small on purpose. */
    val stillUrl: String?,
)

/**
 * What the overlay shows with [remainingMs] of the stream left. Pure, so the
 * timing is tested without a player.
 */
internal fun upNextPhaseFor(
    remainingMs: Long,
    durationMs: Long,
    hasNext: Boolean,
    cancelled: Boolean,
    askStillWatching: Boolean,
    promptDismissed: Boolean,
): UpNextPhase = when {
    !hasNext || durationMs < UP_NEXT_MIN_DURATION_MS || remainingMs < 0 -> UpNextPhase.Hidden
    remainingMs <= UP_NEXT_WINDOW_MS -> if (cancelled) UpNextPhase.Hidden else UpNextPhase.Card
    remainingMs <= STILL_WATCHING_LEAD_MS && askStillWatching && !promptDismissed -> UpNextPhase.StillWatching
    else -> UpNextPhase.Hidden
}

/** Whether this episode asks: the [UNATTENDED_LIMIT]th in a row with no press. */
internal fun shouldAskStillWatching(unattendedBefore: Int, touchedThisEpisode: Boolean): Boolean =
    !touchedThisEpisode && unattendedBefore + 1 >= UNATTENDED_LIMIT

/** The count the next episode starts from: a press anywhere in this one resets it. */
internal fun unattendedAfter(unattendedBefore: Int, touchedThisEpisode: Boolean): Int =
    if (touchedThisEpisode) 0 else unattendedBefore + 1

/** The whole seconds the card counts down: 10 at the window's start, never 0. */
internal fun upNextSeconds(remainingMs: Long): Int =
    ((remainingMs + 999) / 1000).toInt().coerceIn(1, (UP_NEXT_WINDOW_MS / 1000).toInt())

/** "S1 E2 · Compulsion", or "S1 E2" without a title. */
internal fun upNextHeading(next: NextEpisode): String {
    val number = "S${next.season} E${next.episode}"
    return next.title?.takeIf { it.isNotBlank() }?.let { "$number  ·  $it" } ?: number
}

// ── The look: the approved mock-up (B mirrored, 2026-10-31) ─────────────────

internal val UP_NEXT_BLUE  = Color(0xFF2563EB)
private val UP_NEXT_LABEL  = Color(0xFF96AAE6)
private val UP_NEXT_DIM    = Color(0xFFC8CDDC)
internal val UP_NEXT_STILL_W = 220.dp
internal val UP_NEXT_STILL_H = 124.dp

private fun Modifier.bottomScrim(fromFraction: Float, strength: Float) = background(
    Brush.verticalGradient(
        fromFraction to Color.Transparent,
        1f to Color.Black.copy(alpha = strength),
    )
)

/**
 * The Up Next card: the still on the right with the countdown along its
 * bottom, the number and title right-aligned beside it, then "Watch next
 * episode" and Cancel. [focus] 0 is Watch, 1 is Cancel. [counting] false is
 * the card after an unanswered Still Watching: no number, no bar, nothing
 * plays by itself.
 */
@Composable
internal fun BoxScope.UpNextCard(
    next: NextEpisode,
    remainingMs: Long,
    focus: Int,
    counting: Boolean,
) {
    Box(Modifier.matchParentSize().bottomScrim(0.48f, 0.92f))
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 40.dp, bottom = 46.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.height(UP_NEXT_STILL_H),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = if (counting) "Next episode in ${upNextSeconds(remainingMs)}" else "Up next",
                color = UP_NEXT_LABEL,
                fontSize = 13.sp,
                fontFamily = AppTypography.interSemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = upNextHeading(next),
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = AppTypography.interBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(560.dp),
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                UpNextButton("Watch next episode", focused = focus == 0, playIcon = true)
                UpNextButton("Cancel", focused = focus == 1)
            }
        }
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .size(UP_NEXT_STILL_W, UP_NEXT_STILL_H)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14161E))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(next.stillUrl)
                    .size(300, 169)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (counting) {
                // Fills from the right toward the words, as the seconds go.
                val elapsed = (1f - remainingMs.toFloat() / UP_NEXT_WINDOW_MS).coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp)
                        .background(Color.White.copy(alpha = 0.24f))
                ) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            .fillMaxWidth(elapsed).background(UP_NEXT_BLUE)
                    )
                }
            }
        }
    }
}

/**
 * "Still watching {show}?" with Keep watching and I'm done. [focus] 0 is Keep
 * watching. [progress] runs 0..1 over the 20 s before the card.
 */
@Composable
internal fun BoxScope.StillWatchingPrompt(showTitle: String?, focus: Int, progress: Float) {
    Box(Modifier.matchParentSize().bottomScrim(0.35f, 0.92f))
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 62.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = showTitle?.takeIf { it.isNotBlank() }?.let { "Still watching $it?" } ?: "Still watching?",
            color = Color.White,
            fontSize = 26.sp,
            fontFamily = AppTypography.interBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Press any button to keep going. Otherwise playback stops after this episode.",
            color = UP_NEXT_DIM,
            fontSize = 13.sp,
            fontFamily = AppTypography.interSemiBold,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            UpNextButton("Keep watching", focused = focus == 0, width = 190.dp)
            UpNextButton("I’m done", focused = focus == 1, width = 190.dp)
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.width(300.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(UP_NEXT_BLUE))
        }
    }
}

/** The mock-up's pill: white with a blue ring when focused, frosted otherwise. */
@Composable
private fun UpNextButton(
    label: String,
    focused: Boolean,
    playIcon: Boolean = false,
    width: Dp? = null,
) {
    val text = if (focused) Color(0xFF0C0C10) else Color(0xFFEBEEF5)
    // The ring sits 3 dp outside the pill, so both states take the same room.
    Box(
        Modifier
            .border(2.dp, if (focused) UP_NEXT_BLUE else Color.Transparent, RoundedCornerShape(15.dp))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.15f))
                .padding(horizontal = if (playIcon) 26.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (playIcon) {
                Canvas(Modifier.size(11.dp, 14.dp)) {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close()
                        },
                        color = text,
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(label, color = text, fontSize = 15.sp, fontFamily = AppTypography.interBold)
        }
    }
}
