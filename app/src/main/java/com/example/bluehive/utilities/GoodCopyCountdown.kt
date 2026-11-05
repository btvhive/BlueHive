package com.example.bluehive.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.models.MediaItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * "Good copy" countdown on a movie poster (design 5, "big number tile",
 * v643; design E from 2026-10-24 before it).
 *
 * A movie that came out in cinemas less than [WINDOW_DAYS] days ago usually only
 * exists as a cinema recording, so the card says how long until a proper copy is
 * likely: a dark tile in the poster's top-left corner, gold-edged, with the
 * number of days large in gold and "DAYS / UNTIL HD" under it -- always days
 * (the user's call, v643; it counted weeks past two weeks before, and its
 * "WKS / DAYS" line was too small to read). Movies only -- TV, Japanese
 * animation and anything on a streaming service never get it -- and it
 * disappears by itself on day [WINDOW_DAYS], no data needed.
 *
 * Sizes come from the mock-up, drawn on a 150 px poster, converted with the
 * poster's 90.25 dp / 150 px.
 */
object GoodCopyCountdown {

    const val WINDOW_DAYS = 60

    data class Label(val number: String, val unit: String)

    /** The badge for [item] today, or null when it should not show. */
    fun labelFor(item: MediaItem, today: LocalDate = LocalDate.now()): Label? = labelFor(
        mediaType = item.mediaType,
        genres = item.genres?.joinToString(","),
        originalLanguage = item.originalLanguage,
        onStreaming = item.onStreaming,
        releaseDate = item.releaseDate,
        today = today,
    )

    /** The same, from the fields a details page is opened with (its "Not in HD yet" banner, v649). */
    fun labelFor(
        mediaType: String?,
        genres: String?,
        originalLanguage: String?,
        onStreaming: Boolean?,
        releaseDate: String?,
        today: LocalDate = LocalDate.now(),
    ): Label? {
        if (mediaType != "movie") return null
        if (isJapaneseAnimation(genres, originalLanguage)) return null
        // Straight to Netflix/Prime/etc. = online in HD from day one, so there is
        // nothing to wait for. The server sets this from its US streaming list.
        if (onStreaming == true) return null
        val released = releaseDate
            ?.takeIf { it.length >= 10 }
            ?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
            ?: return null
        val age = ChronoUnit.DAYS.between(released, today)
        if (age < 0 || age >= WINDOW_DAYS) return null
        val left = (WINDOW_DAYS - age).toInt()
        return Label(left.toString(), if (left == 1) "DAY" else "DAYS")
    }
}

// Mock-up px -> dp: the mock-up's poster is 150 px, the card's 90.25 dp.
private const val K = 90.25f / 150f
private fun px(v: Float) = (v * K).dp
private fun pxSp(v: Float): TextUnit = (v * K).sp

private val Gold = Color(0xFFE8B23A)
private val Backing = Color(0xFF111114)       // solid near-black (v644; 82 % see-through black before)

/** Draw inside the poster box, aligned to its top-left corner. */
@Composable
fun GoodCopyBadge(label: GoodCopyCountdown.Label, modifier: Modifier = Modifier) {
    val font = AppTypography.interBold
    val shape = RoundedCornerShape(px(9f))
    Column(
        modifier = modifier
            .padding(start = px(9f), top = px(9f))
            .widthIn(min = px(74f))
            .clip(shape)
            .background(Backing)
            .border(px(1.5f), Gold, shape)
            .padding(start = px(6f), end = px(6f), top = px(5f), bottom = px(6f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Bigger all round (v645, mock-up E): the number 28 px, both lines of
        // words 12 px in full white -- at 24 / 9 px with a grey "UNTIL HD"
        // the words were hard to see from the couch.
        Line(label.number, Gold, font, size = 28f, lineHeight = 28f)
        Line(label.unit, Color.White, font, size = 12f, lineHeight = 14f, topGap = 3f)
        // Thicker than Inter Bold can draw (v646, the user's call): the app has
        // no heavier Inter, so the letters get a thin outline in their own
        // colour under the fill.
        Line("UNTIL HD", Color.White, font, size = 12f, lineHeight = 14f, thicken = true)
    }
}

@Composable
private fun Line(
    text: String,
    color: Color,
    font: FontFamily,
    size: Float,
    lineHeight: Float,
    topGap: Float = 0f,
    thicken: Boolean = false,
) {
    val style = TextStyle(fontSize = pxSp(size), lineHeight = pxSp(lineHeight))
    Box(Modifier.padding(top = px(topGap))) {
        if (thicken) {
            val stroke = with(LocalDensity.current) { THICKEN.toPx() }
            Text(
                text = text,
                color = color,
                fontFamily = font,
                maxLines = 1,
                softWrap = false,
                style = style.copy(drawStyle = Stroke(width = stroke, join = StrokeJoin.Round)),
            )
        }
        Text(
            text = text,
            color = color,
            fontFamily = font,
            maxLines = 1,
            softWrap = false,
            style = style,
        )
    }
}

/** The outline that thickens a line's letters: 0.25 dp (v648; 0.7 dp was way too thick, 0.35 a little). */
private val THICKEN = 0.25.dp
