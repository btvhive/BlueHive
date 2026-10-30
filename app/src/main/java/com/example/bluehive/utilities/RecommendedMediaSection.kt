// utilities/RecommendedMediaSection.kt
package com.example.bluehive.utilities

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.models.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ⬅️➡️ Hold-to-repeat navigation speed for the Recommended Media row: the minimum time
// (ms) between focus moves while LEFT/RIGHT is HELD DOWN. HIGHER = SLOWER.
private const val RECOMMENDED_NAV_REPEAT_MS = 105L

// ⏱️ Title reveal timing — matched to the front-page TvWideLazyRow shelves. A card must hold
// focus for TITLE_REVEAL_DELAY_MS, then its title fades in over TITLE_FADE_MS. Fast swiping
// keeps the title at alpha 0 (no flicker); settling triggers the same relaxed fade as home.
private const val TITLE_REVEAL_DELAY_MS = 800L
private const val TITLE_FADE_MS = 700

/** The "Recommended Media" heading while the row lacks focus, in the movie screen's peek layout. */
private val DIM_TITLE_COLOR = Color(0xFFA6A6A6)

/**
 * Lets a screen move focus into the row from outside it: the movie screen's
 * play buttons, whose Down enters the row below them.
 */
class RecommendedRowEntry {
    internal var enter: (() -> Unit)? = null

    /** Focus the row's last focused card (the first, at the start); false while the row has no titles. */
    fun focus(): Boolean {
        val e = enter ?: return false
        e()
        return true
    }
}

/**
 * "Recommended Media", grey while [dim] and white otherwise, fading over
 * 350 ms.
 *
 * ITS OWN COMPOSABLE, AND THE COLOUR IS READ ONLY WHILE DRAWING. It used to be
 * animated in RecommendedMediaSection's body, so every frame of the fade
 * rebuilt the whole section -- the row and every visible card. A trace of the
 * movie page's slide on the onn box (2026-10-27) put 20-33 ms of
 * recomposition into each of its slow frames, about nine recompositions a
 * frame. [dim] is a lambda for the same reason: a focus change re-runs only
 * this. The style is what Material's Text would build, so nothing on screen
 * changes.
 */
@Composable
private fun RecommendedHeading(
    title: String,
    dim: () -> Boolean,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    modifier: Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (dim()) DIM_TITLE_COLOR else Color.White,
        animationSpec = tween(durationMillis = 350),
        label = "recHeadingColor"
    )
    BasicText(
        text = title,
        modifier = modifier,
        style = LocalTextStyle.current.merge(
            TextStyle(fontSize = fontSize, fontFamily = fontFamily)
        ),
        color = { color },
    )
}

/**
 * The recommended row, under the details pages' play area (the peek layout,
 * DetailsPeekRecommendations): placed by [modifier], its heading smaller and
 * dimmed until the row has focus, entered through [entry], and sliding the
 * page on [onRowFocusChanged]. (The Extras row's defaults went in v515.)
 */
@Composable
fun RecommendedMediaSection(
    recommendations: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    titleFontSize: TextUnit = 24.sp,
    /** The heading's font: Passion One, as it has always been, unless a screen asks for another. */
    titleFontFamily: FontFamily = AppTypography.passionRegular,
    /** The heading's offset above the cards. */
    titleOffsetY: Dp,
    dimTitleUntilFocused: Boolean = false,
    autoFocusFirst: Boolean = true,
    entry: RecommendedRowEntry? = null,
    onRowFocusChanged: (Boolean) -> Unit = {},
    /** The heading's words; the details pages name the kind of title (recommendedRowTitle). */
    title: String = "Recommended Media",
) {
    val backgroundPainter = heldPainter(R.drawable.titlecard_base)
    val focusedPainter = heldPainter(R.drawable.titlecard_focused)
    val shadowPainter = heldPainter(R.drawable.titlecard_shadow)

    val baseCardModifier = remember {
        Modifier
            .wrapContentSize(unbounded = true)
            .focusable()
    }

    val listState = rememberLazyListState()
    // Hold-to-repeat throttle timestamp for the row (see RECOMMENDED_NAV_REPEAT_MS).
    // Plain 1-slot array (NOT observable state) so ticking it never triggers recomposition.
    val recNavGate = remember { longArrayOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var hasRowFocus by remember { mutableStateOf(false) }

    val activity = LocalActivity.current as? ComponentActivity

    val focusRequesters = remember(recommendations.size) {
        List(recommendations.size) { FocusRequester() }
    }

    if (entry != null) {
        DisposableEffect(entry, focusRequesters) {
            entry.enter = {
                if (focusRequesters.isNotEmpty()) {
                    focusRequesters[focusedIndex.coerceIn(0, focusRequesters.lastIndex)].requestFocus()
                }
            }
            onDispose { entry.enter = null }
        }
    }

    Column(
        modifier = modifier
            .offset(x = 60.dp)
            .height(250.dp)
    ) {
        RecommendedHeading(
            title = title,
            dim = { dimTitleUntilFocused && !hasRowFocus },
            fontSize = titleFontSize,
            fontFamily = titleFontFamily,
            modifier = Modifier.offset(x = (-29).dp, y = titleOffsetY)
        )

        Box {
            Image(
                painter = heldPainter(R.drawable.left_lower_body),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(2.25.dp)
                    .height(167.dp)
                    .offset(x = (-7.9).dp, y = (-32.5).dp),
                contentScale = ContentScale.Fit
            )

            Image(
                painter = heldPainter(R.drawable.right_lower_body),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(2.25.dp)
                    .height(167.dp)
                    .offset(x = (839.5).dp, y = (-32.5).dp),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .width(870.dp)
                    .height(208.dp)
                    .offset(y = (-40).dp)
                    .offset(x = (-20).dp)
            ) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(start = 18.dp, end = 15.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { focusState ->
                            hasRowFocus = focusState.hasFocus
                            onRowFocusChanged(focusState.hasFocus)

                            if (focusState.hasFocus) {
                                if (recommendations.isNotEmpty()) {
                                    if (focusedIndex !in recommendations.indices) {
                                        focusedIndex = 0
                                    }

                                    val targetIndex = focusedIndex.coerceIn(
                                        0,
                                        focusRequesters.lastIndex
                                    )

                                    // Only restore focus – scrolling handled in LaunchedEffect
                                    focusRequesters[targetIndex].requestFocus()
                                }
                            } else {
                                Log.d(
                                    "RecommendedMediaSection",
                                    "LazyRow lost focus (keeping focusedIndex=$focusedIndex)"
                                )
                            }
                        },
                    // 👇 Let focus logic drive scroll; user scroll via D-pad is handled by us
                    userScrollEnabled = false,
                    reverseLayout = false
                ) {
                    itemsIndexed(
                        items = recommendations,
                        key = { _, item -> item.tmdbId }
                    ) { index, mediaItem ->
                        // Per-item focus state (LOCAL) so a nav step recomposes only the two
                        // cards whose focus actually changed — not every visible card. Reading
                        // the shared focusedIndex here made ALL cards (each an AsyncImage)
                        // recompose on every keypress, which is the navigation lag.
                        var isItemFocused by remember { mutableStateOf(false) }

                        // Title reveal debounce + fade, matched to the front-page TvWideLazyRow
                        // shelves: the title Box is always composed while focused but sits at
                        // alpha 0 until the card has HELD focus for TITLE_REVEAL_DELAY_MS, then
                        // fades in over TITLE_FADE_MS. Fast swiping never lets titleVisible reach
                        // true, so alpha stays 0 — no flicker; settling triggers the relaxed fade.
                        var titleVisible by remember { mutableStateOf(false) }
                        val titleAlpha by animateFloatAsState(
                            targetValue = if (titleVisible) 1f else 0f,
                            animationSpec = tween(durationMillis = TITLE_FADE_MS),
                            label = "recTitleAlpha"
                        )
                        LaunchedEffect(isItemFocused) {
                            if (isItemFocused) {
                                titleVisible = false
                                delay(TITLE_REVEAL_DELAY_MS)
                                titleVisible = true
                            } else {
                                titleVisible = false
                            }
                        }

                        val releaseDateText = remember(mediaItem.releaseDate) {
                            mediaItem.releaseDate
                                ?.takeIf { it.isNotBlank() && it != "N/A" }
                                ?.let { raw ->
                                    val parts = raw.split("-")
                                    if (parts.size == 3) "${parts[1]}/${parts[2]}/${parts[0]}" else raw
                                }
                        }

                        // WAS runtime-only — see TitleCardFormat. TV shows in the
                        // recommendations now show their season count too.
                        val runTimeText = remember(
                            mediaItem.runtime, mediaItem.numberOfSeasons, mediaItem.mediaType
                        ) {
                            TitleCardFormat.meta(
                                mediaItem.mediaType, mediaItem.runtime, mediaItem.numberOfSeasons
                            )
                        }

                        val rating = remember(mediaItem.voteAverage) {
                            mediaItem.voteAverage?.takeIf { it > 0.0 }
                        }

                        // THIS ROW HAD NO PRESS ANIMATION AT ALL. Every other card
                        // list dipped on press; recommendations just jumped straight
                        // to the details screen, which made them feel unlike the rest
                        // of the app. Shared timing — see TitleCardPress.
                        val isPressed = remember { mutableStateOf(false) }
                        val pressBusy = remember { mutableStateOf(false) }
                        val cardScale by animateFloatAsState(
                            targetValue   = if (isPressed.value) TitleCardPress.SCALE else 1f,
                            animationSpec = tween(
                                if (isPressed.value) TitleCardPress.SHRINK_MS else TitleCardPress.REGROW_MS
                            ),
                            label = "cardScale",
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(112.dp)
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }
                        ) {
                            MediaCard(
                                mediaItem = mediaItem,
                                backgroundPainter = backgroundPainter,
                                focusedPainter = focusedPainter,
                                shadowPainter = shadowPainter,
                                isFocused = isItemFocused,
                                releaseDateText = releaseDateText,
                                runTimeText = runTimeText,
                                rating = rating,
                                modifier = baseCardModifier
                                    .focusRequester(focusRequesters[index])
                                    .onFocusChanged { focusState ->
                                        // Drive the LOCAL focus flag (glow/size/title) from this
                                        // card's own focus — this is what was missing and made the
                                        // glow layer disappear (isItemFocused never went true).
                                        isItemFocused = focusState.isFocused
                                        if (focusState.isFocused) {
                                            // 🔹 Only update state, no scroll here
                                            focusedIndex = index
                                        }
                                    }
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionLeft -> {
                                                    // Throttle hold-to-repeat (see RECOMMENDED_NAV_REPEAT_MS); the
                                                    // boundary guard keeps the sound off at the first card.
                                                    val now = SystemClock.uptimeMillis()
                                                    if (focusedIndex > 0 && now - recNavGate[0] >= RECOMMENDED_NAV_REPEAT_MS) {
                                                        recNavGate[0] = now
                                                        focusRequesters[focusedIndex - 1].requestFocus()
                                                        BlueHiveApplication.playTitleCardNavigation()
                                                    }
                                                    true
                                                }

                                                Key.DirectionRight -> {
                                                    val now = SystemClock.uptimeMillis()
                                                    if (focusedIndex < recommendations.size - 1 && now - recNavGate[0] >= RECOMMENDED_NAV_REPEAT_MS) {
                                                        recNavGate[0] = now
                                                        focusRequesters[focusedIndex + 1].requestFocus()
                                                        BlueHiveApplication.playTitleCardNavigation()
                                                    }
                                                    true
                                                }

                                                Key.DirectionUp -> {
                                                    onNavigateUp()
                                                    true
                                                }

                                                Key.DirectionDown -> true

                                                Key.Back -> {
                                                    activity?.onBackPressedDispatcher?.onBackPressed()
                                                    true
                                                }

                                                Key.Enter,
                                                Key.NumPadEnter,
                                                Key.DirectionCenter -> {
                                                    // Sound now, navigation after the dip.
                                                    BlueHiveApplication.playClickSound()
                                                    coroutineScope.runTitleCardPress(isPressed, pressBusy) {
                                                        onMediaClick(mediaItem)
                                                    }
                                                    true
                                                }

                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                            )

                            // Composed once the title is due (v515): a card passed over
                            // while scrolling lays out no title it would never show.
                            if (isItemFocused && titleVisible) {
                                var fontSize by remember(mediaItem.title) { mutableStateOf(16.sp) }

                                Box(
                                    modifier = Modifier
                                        .width(145.dp)
                                        .height(50.dp)
                                        .offset(y = (-2).dp)
                                        .graphicsLayer { alpha = titleAlpha },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Text(
                                        text = mediaItem.title,
                                        color = Color.White,
                                        fontSize = fontSize,
                                        fontFamily = AppTypography.passionRegular,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                        onTextLayout = { layoutResult ->
                                            if (layoutResult.lineCount > 1 && fontSize != 13.sp) {
                                                fontSize = 13.sp
                                            }
                                        }
                                    )
                                }
                            }

                        }
                    }
                }
            }

            Image(
                painter = heldPainter(R.drawable.left_upper_body),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(25.4.dp)
                    .height(193.dp)
                    .offset(x = (-31).dp, y = (-46).dp),
                contentScale = ContentScale.Fit
            )

            Image(
                painter = heldPainter(R.drawable.right_upper_body),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(25.4.dp)
                    .height(193.dp)
                    .offset(x = 840.dp, y = (-46).dp),
                contentScale = ContentScale.Fit
            )
        }
    }

    // 🔹 Centralized, debounced scroll logic. Follows focusedIndex in a flow,
    // not as an effect key: a key is read while composing, so every focus
    // move recomposed the whole section (v515).
    LaunchedEffect(listState, recommendations) {
        if (recommendations.isEmpty()) return@LaunchedEffect
        snapshotFlow { focusedIndex }.collect { index ->
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (index < 0 || visibleItems.isEmpty()) return@collect
            val target = recommendedRowNudge(index, visibleItems.first().index, visibleItems.last().index)
                ?: return@collect
            coroutineScope.launch {
                listState.scrollToItem(target)
            }
        }
    }

    // Auto-focus first item when section appears
    LaunchedEffect(recommendations.size) {
        if (autoFocusFirst && recommendations.isNotEmpty() && focusedIndex == -1) {
            focusedIndex = 0
            focusRequesters[0].requestFocus()
        }
    }
}

/**
 * Where the row scrolls to when [focused] lands at or past either end of the
 * visible cards ([firstVisible]..[lastVisible]): one card in from the edge.
 * Null while it is safely inside. Pure, so it is tested.
 */
internal fun recommendedRowNudge(focused: Int, firstVisible: Int, lastVisible: Int): Int? =
    if (focused in (firstVisible + 1)..(lastVisible - 1)) null else maxOf(focused - 1, 0)
