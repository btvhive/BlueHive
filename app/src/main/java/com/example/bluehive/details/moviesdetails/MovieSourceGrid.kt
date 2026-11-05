package com.example.bluehive.details.moviesdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.details.shared.layout.PEEK_RIGHT_COLUMN_X
import com.example.bluehive.details.shared.layout.PEEK_SOURCE_ROW_STEP
import com.example.bluehive.details.shared.playback.StreamSource
import com.example.bluehive.utilities.*

/**
 * The movie screen's play area: Watch in English / Japanese for anime, the
 * "Watch on ..." grid for everything else.
 *
 * Moved out of MovieDetailsScreen on 2026-10-26, with the four button
 * composables below, to keep the screen function well under the 2,500-unit
 * budget (checkMethodSizes); the layout and key handling are the code they
 * replace. What a press DOES stays the screen's: [onAnimePress],
 * [onSourcePress] and [onDownIntoRow].
 */
@Composable
internal fun BoxScope.MovieSourceGrid(
    isAnime: Boolean,
    sources: List<StreamSource>,
    fontFamily: FontFamily,
    /**
     * The first row's top, just under the overview (DetailsPeekState.playTop).
     * Read while placing (v515): it arrives with the overview's first layout,
     * and reading it while composing recomposed every button as the page opened.
     */
    playTop: () -> Dp,
    animeDubFocusRequester: FocusRequester,
    animeSubFocusRequester: FocusRequester,
    /** One per source, in order. */
    streamerFocus: List<FocusRequester>,
    /** Down on a bottom-row button, from that button: true when focus entered the recommended row. */
    onDownIntoRow: (FocusRequester) -> Boolean,
    /** Watch in English (true) or Japanese (false). */
    onAnimePress: (Boolean) -> Unit,
    /** A "Watch on ..." press: the button's requester and its source. */
    onSourcePress: (FocusRequester, StreamSource) -> Unit,
    /** The poster tile's countdown, for the "Not in HD yet" banner; null shows none. */
    notHd: GoodCopyCountdown.Label? = null,
) {
    if (isAnime) {
        // ── Anime: DuB / SuB ────────────────────────────────────
        AnimeDubButton(
            fontFamily = fontFamily,
            focusRequester = animeDubFocusRequester,
            offsetX = 27.75.dp, offsetY = playTop,
            onDown = { onDownIntoRow(animeDubFocusRequester) },
            onClick = { onAnimePress(true) }
        )
        AnimeSubButton(
            fontFamily = fontFamily,
            focusRequester = animeSubFocusRequester,
            offsetX = PEEK_RIGHT_COLUMN_X, offsetY = playTop,
            onDown = { onDownIntoRow(animeSubFocusRequester) },
            onClick = { onAnimePress(false) }
        )
    } else {
        // ── Non-anime: 2-column grid laid out FROM the sources list ──
        // Adding/removing an entry in getStreamingSources() now "just
        // works" — no per-index block to keep in sync (that mismatch is
        // what silently dropped the 6th button). Column X and the first
        // rows keep the legacy spacing, starting just under the overview's
        // last line (peek layout); a 5th+ source continues the grid downward and
        // pushes the recommended row down with it (DetailsPeekState).
        val colX = listOf(27.75.dp, PEEK_RIGHT_COLUMN_X)  // [left column, right column]
        sources.forEachIndexed { i, source ->
            val col = i % 2
            val row = i / 2
            // A trailing odd button (alone on its row) is centered between the two
            // columns instead of hugging the left column.
            val aloneInRow = i % 2 == 0 && i == sources.lastIndex
            MoviesStreamerButton(
                source = source,
                fontFamily = fontFamily,
                focusRequester = streamerFocus[i],
                offsetX = if (aloneInRow) (colX[0] + colX[1]) / 2f else colX[col],
                offsetY = { playTop() + PEEK_SOURCE_ROW_STEP * row },
                disableLeftSound  = aloneInRow || col == 0, // left column / lone centered → mute left edge
                disableRightSound = aloneInRow || col == 1, // right column / lone centered → mute right edge
                disableDownSound  = i + 2 >= sources.size,  // no button directly below → mute down
                // Bottom row: Down enters the recommended row.
                onDownFromBottom  = { onDownIntoRow(streamerFocus[i]) },
                onClick = { onSourcePress(streamerFocus[i], source) }
            )
        }
        // "Not in HD yet" (design 1, v649): beside the last row of buttons,
        // the poster tile's look stretched into a banner.
        if (notHd != null && sources.isNotEmpty()) {
            val lastRow = sources.lastIndex / 2
            NotHdYetBanner(
                label = notHd,
                offsetX = BANNER_X,
                offsetY = { playTop() + PEEK_SOURCE_ROW_STEP * lastRow + 0.7.dp },
            )
        }
    }
}

private val SOURCE_BUTTON_WIDTH = 162.dp
/**
 * The banner's span (the user's marks on a screenshot, v650): it starts a
 * little away from the buttons -- 86 dp past Website 4's right edge -- and
 * ends level with the right side of the recommended row's last poster.
 */
private val BANNER_X = PEEK_RIGHT_COLUMN_X + SOURCE_BUTTON_WIDTH + 86.dp
private val BANNER_RIGHT = 923.dp
private val BANNER_WIDTH = BANNER_RIGHT - BANNER_X
private val BANNER_GOLD = Color(0xFFE8B23A)
private val BANNER_BG = Color(0xFF111114)
private val BANNER_RULE = Color(0xFF3A3A40)
private val BANNER_NOTE = Color(0xFFCFD0D6)
/** The stripes' width at the banner's right end, and one gold + one black stripe across. */
private val HAZARD_WIDTH = 79.dp
private val HAZARD_PERIOD = 14.3.dp

/**
 * The movie page's "Not in HD yet" banner (design 1, v649): the poster's
 * until-HD tile stretched -- near-black, a gold edge, the day count large in
 * gold, "DAYS / UNTIL HD" beside it, a rule, then why the picture may look
 * rough. As tall as the Website buttons' face. Not focusable: it only says.
 */
@Composable
private fun NotHdYetBanner(label: GoodCopyCountdown.Label, offsetX: Dp, offsetY: () -> Dp) {
    val shape = RoundedCornerShape(7.dp)
    val font = AppTypography.interBold
    Box(
        modifier = Modifier
            .offsetWithY(offsetX, offsetY)
            .width(BANNER_WIDTH)
            .height(33.dp + SOURCE_BUTTON_EXTRA_HEIGHT)
            .clip(shape)
            .background(BANNER_BG)
            .border(1.5.dp, BANNER_GOLD, shape),
    ) {
        HazardStripes(Modifier.align(Alignment.CenterEnd).width(HAZARD_WIDTH).fillMaxHeight())
        NotHdYetWords(label, font)
    }
}

/**
 * Caution-tape stripes across the banner's right end (mock-up A3, v652):
 * gold and the banner's black, leaning 60 degrees off upright, fading in from
 * the left so they rise out of the empty space after the words.
 */
@Composable
private fun HazardStripes(modifier: Modifier) {
    Canvas(modifier) {
        // One gold stripe and one black, HAZARD_PERIOD across, measured along
        // a line 30 degrees right of straight up -- so the stripes run 60
        // degrees off upright, as in the mock-up.
        val period = HAZARD_PERIOD.toPx()
        val dir = Offset(0.5f, -0.8660254f)
        drawRect(
            Brush.linearGradient(
                0f to BANNER_GOLD, 0.5f to BANNER_GOLD, 0.5f to BANNER_BG, 1f to BANNER_BG,
                start = Offset.Zero,
                end = dir * period,
                tileMode = TileMode.Repeated,
            )
        )
        drawRect(Brush.horizontalGradient(0f to BANNER_BG, 0.3f to BANNER_BG.copy(alpha = 0f)))
    }
}

@Composable
private fun NotHdYetWords(label: GoodCopyCountdown.Label, font: FontFamily) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label.number, color = BANNER_GOLD, fontFamily = font, maxLines = 1,
            style = TextStyle(fontSize = 20.sp, lineHeight = 20.sp))
        Column {
            Text(label.unit, color = Color.White, fontFamily = font, maxLines = 1,
                style = TextStyle(fontSize = 10.5.sp, lineHeight = 11.5.sp))
            Text("UNTIL HD", color = Color.White, fontFamily = font, maxLines = 1,
                style = TextStyle(fontSize = 10.5.sp, lineHeight = 11.5.sp))
        }
        Box(Modifier.size(width = 1.dp, height = 20.dp).background(BANNER_RULE))
        Text("What's playing now is a cinema recording", color = BANNER_NOTE,
            fontFamily = AppTypography.interSemiBold, maxLines = 1,
            style = TextStyle(fontSize = 10.5.sp, lineHeight = 12.sp))
    }
}


@Composable
fun MoviesStreamerButton(
    source: StreamSource,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp,
    /** Read while placing -- see MovieSourceGrid's playTop. */
    offsetY: () -> Dp,
    disableLeftSound: Boolean = false,
    disableRightSound: Boolean = false,
    disableDownSound: Boolean = false,
    /** Down on a bottom-row button; true when it moved focus (into the recommended row). */
    onDownFromBottom: () -> Boolean,
    onClick: () -> Unit
) {
    ModularButton(
        // "Play / from Website 1" -- see streamSourceLabel. The viewer's name
        // for the source, never its routing name.
        textConfig = if (source.name == "M Extract") {
            ModularButtonTextConfig(text = "M Extract", fontSize = 14f, shadow = ButtonLabelShadow)
        } else {
            streamSourceLabel(source.coverName)
        },
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offsetWithY(offsetX, offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (disableLeftSound) {
                                // edge: no sound, no navigation
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false   // let focus move normally
                            }
                        }
                        Key.DirectionRight -> {
                            if (disableRightSound) {
                                // Right edge: a plain dead end, no sound, no move.
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            BlueHiveApplication.playHoverSound()
                            false
                        }
                        Key.DirectionDown -> {
                            if (disableDownSound) {
                                // Bottom row: into the recommended row, with a
                                // sound only when it moved; without a row,
                                // nothing below and no sound.
                                if (onDownFromBottom()) BlueHiveApplication.playHoverSound()
                                true
                            } else {
                                BlueHiveApplication.playHoverSound()
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        // 3 dp taller than the anime buttons (SOURCE_BUTTON_EXTRA_HEIGHT, v611):
        // face, strip offset and glow all grow by it.
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp,
            mainHeight = 33.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            mainYOffset = 0.7.dp,
            secondWidth = 162.dp,
            secondHeight = 22.dp,
            secondYOffset = 18.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            shadowHeight = (12).dp,
            glowWidth = 185.8.dp,
            glowHeight = 55.8.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared with the TV screen — see StreamSourceButtonColors in
        // utilities/ModularButton.kt. Change the blues there, not here.
        colors = StreamSourceButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp,
            offsetY = (-2.5).dp
        ),
        // Timing shared with the TV screen — see WatchButtonAnimation in
        // utilities/ModularButton.kt. Change the press feel there, not here.
        animationConfig = WatchButtonAnimation,
        // No playClickSound() here: ModularButton already plays it, immediately,
        // and this second call fired a duplicate. Harmless while the two landed
        // on the same frame — audible as a real double-click once onClick became
        // deferred by clickDelayMillis.
        onClick = onClick
    )
}


/** Down on Watch in English / Japanese: into the recommended row, sounding only when it moved. */
private fun animeDown(onDown: () -> Boolean): Boolean {
    if (onDown()) BlueHiveApplication.playHoverSound()
    return true
}

@Composable
fun AnimeDubButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp,
    /** Read while placing -- see MovieSourceGrid's playTop. */
    offsetY: () -> Dp,
    /** Down; true when it moved focus (into the recommended row). */
    onDown: () -> Boolean,
    onClick: () -> Unit
) {
    ModularButton(
        // "English" / "Japanese" rather than "DuB" / "SuB". Dub and sub are
        // fan shorthand; the language is what a viewer is actually choosing.
        // The same label shadow as the Watch on ... buttons -- see ButtonLabelShadow.
        // Two lines like the source buttons, "Watch in" over the language (v622).
        textConfig = animeLanguageLabel("English"),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offsetWithY(offsetX, offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> true   // left edge — block + no sound
                        Key.DirectionDown -> animeDown(onDown)
                        Key.DirectionRight, Key.DirectionUp -> {
                            BlueHiveApplication.playHoverSound(); false
                        }
                        else -> false
                    }
                } else false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp, mainHeight = 33.dp + SOURCE_BUTTON_EXTRA_HEIGHT, mainYOffset = 0.7.dp,
            secondWidth = 162.dp, secondHeight = 22.dp, secondYOffset = 18.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            shadowHeight = 12.dp, glowWidth = 185.8.dp, glowHeight = 55.8.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        // Palette and press feel are shared with the TV screen — see
        // AnimeDubButtonColors / WatchButtonAnimation in utilities/ModularButton.kt.
        colors = AnimeDubButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp, offsetY = (-2.5).dp
        ),
        animationConfig = WatchButtonAnimation,
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}

@Composable
fun AnimeSubButton(
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    offsetX: Dp,
    /** Read while placing -- see MovieSourceGrid's playTop. */
    offsetY: () -> Dp,
    /** Down; true when it moved focus (into the recommended row). */
    onDown: () -> Boolean,
    onClick: () -> Unit
) {
    ModularButton(
        textConfig = animeLanguageLabel("Japanese"),
        isToggleable = false,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = Modifier
            .offsetWithY(offsetX, offsetY)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> true  // right edge — block + no sound
                        Key.DirectionDown -> animeDown(onDown)
                        Key.DirectionLeft, Key.DirectionUp -> {
                            BlueHiveApplication.playHoverSound(); false
                        }
                        else -> false
                    }
                } else false
            },
        dimensions = ModularButtonDimensions(
            mainWidth = 162.dp, mainHeight = 33.dp + SOURCE_BUTTON_EXTRA_HEIGHT, mainYOffset = 0.7.dp,
            secondWidth = 162.dp, secondHeight = 22.dp, secondYOffset = 18.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            shadowHeight = 12.dp, glowWidth = 185.8.dp, glowHeight = 55.8.dp + SOURCE_BUTTON_EXTRA_HEIGHT,
            mainCornerRadius = 7f, secondCornerRadius = 8f,
        ),
        // Palette and press feel are shared with the TV screen — see
        // AnimeSubButtonColors / WatchButtonAnimation in utilities/ModularButton.kt.
        colors = AnimeSubButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-11.8).dp, offsetY = (-2.5).dp
        ),
        animationConfig = WatchButtonAnimation,
        // ModularButton already plays the click, immediately. This second call
        // was a duplicate, inaudible only because both landed on one frame.
        onClick = onClick
    )
}
