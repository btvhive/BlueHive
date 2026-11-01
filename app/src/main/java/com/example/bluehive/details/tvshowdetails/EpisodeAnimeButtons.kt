package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame

// ====================================================================================================================>  Anime episode overlay (Watch in English / Japanese)
@Composable
internal fun AnimeEpisodeProviderButtons(
    passionFont: FontFamily,
    miruroUrl: String?,                                            // null while resolving OR if unmapped
    /**
     * The source list has not arrived yet.
     *
     * THIS IS WHAT SEPARATES "not yet" FROM "not there", and without it the two
     * were the same thing: miruroUrl is null in both cases, so a press made in
     * the second before the AniList resolve landed reported the episode as
     * unavailable — and the identical press a moment later worked. The list is
     * empty only while loading (it always carries VidFast once built), so its
     * emptiness is the signal.
     */
    resolving: Boolean,
    focusRequesters: List<FocusRequester>,                         // [0]=DuB, [1]=SuB
    onDub: (String) -> Unit,
    onSub: (String) -> Unit,
    onUnavailable: () -> Unit,                                     // genuinely has no mapping
    onBack: () -> Unit,
    /** Which button takes focus when the row appears: 1 = Japanese, after BACK from its server choice. */
    startOn: Int = 0,
    /** Focus is on the row: the card's step change is over (EpisodeSnapState.stepFocused). */
    onFocusTaken: () -> Unit = {},
) {
    /** A press made too early. true = dub, false = sub, null = none waiting. */
    var queued by remember { mutableStateOf<Boolean?>(null) }

    // REMEMBERED, NOT REFUSED. The viewer pressed a button; the right answer is
    // to honour it when the url shows up, not to tell them the episode does not
    // exist because the network was a beat behind their thumb.
    //
    // Re-runs whenever the url or the resolving flag changes, which cancels the
    // delay below — so the bounded wait only expires if nothing ever arrived.
    LaunchedEffect(queued, miruroUrl, resolving) {
        val want = queued ?: return@LaunchedEffect
        if (miruroUrl != null) {
            queued = null
            if (want) onDub(miruroUrl) else onSub(miruroUrl)
            return@LaunchedEffect
        }
        if (!resolving) {           // list arrived and carries no Miruro: real
            queued = null
            onUnavailable()
            return@LaunchedEffect
        }
        // Still resolving. Wait, but not forever — a resolve that never
        // finishes must not leave the button looking dead.
        delay(8_000)
        if (queued != null) {
            queued = null
            onUnavailable()
        }
    }
    // Auto-focus English when the overlay opens (matches EpisodeSourceButtons),
    // or the language BACK came from.
    LaunchedEffect(Unit) {
        delay(50)
        focusRequesters.getOrNull(startOn)?.requestFocus()
        onFocusTaken()
    }

    // Side by side in the episode overlay, like its Watch on ... buttons:
    // Left / Right move, every edge goes nowhere -- see OverlayWatchButton.
    OverlayWatchButton(
        text = "Watch in English",
        colors = AnimeDubButtonColors,
        passionFont = passionFont,
        focusRequester = focusRequesters[0],
        index = 0,
        focusRequesters = focusRequesters.subList(0, 2),
        onBack = onBack,
        onClick = {
            when {
                miruroUrl != null -> onDub(miruroUrl)
                resolving -> queued = true      // honour it when it lands
                else -> onUnavailable()
            }
        }
    )
    OverlayWatchButton(
        text = "Watch in Japanese",
        colors = AnimeSubButtonColors,
        passionFont = passionFont,
        focusRequester = focusRequesters[1],
        index = 1,
        focusRequesters = focusRequesters.subList(0, 2),
        onBack = onBack,
        onClick = {
            when {
                miruroUrl != null -> onSub(miruroUrl)
                resolving -> queued = false     // honour it when it lands
                else -> onUnavailable()
            }
        }
    )
}


// ====================================================================================================================>  The server choice, inside the card (v585)
//
// English or Japanese no longer opens a second overlay. The row turns to dust
// (RowSnapState) and these two fade in in its place -- the same buttons, the
// same size, the same focus glow -- with "Default server" in the language's
// own colour and "Choose a server" a darker shade of it. Choose a server opens
// the list in the same card (AnimeInlineServerList). BACK walks back a step.

/** "Choose a server" after English: the dub red, darker; focus still lights it. */
internal val AnimeDubChooseColors = AnimeDubButtonColors.copy(
    mainDefault   = Color(0xFF4A1212),
    mainFocused   = Color(0xFF9E1C1C),
    secondDefault = Color(0xFF2B0909),
    secondFocused = Color(0xFF5E1010),
)

/** "Choose a server" after Japanese: the sub purple, darker. */
internal val AnimeSubChooseColors = AnimeSubButtonColors.copy(
    mainDefault   = Color(0xFF3A1659),
    mainFocused   = Color(0xFF6E2BA6),
    secondDefault = Color(0xFF210C33),
    secondFocused = Color(0xFF461A6B),
)

/** The language's dot: the dub red or the sub purple. */
internal fun animeAccent(dub: Boolean): Color = if (dub) Color(0xFFFF2020) else Color(0xFFAE50F1)

/**
 * Default server / Choose a server, where English / Japanese were. Fades in
 * over SERVER_PAIR_FADE_MS, then takes focus -- on [startOn], so coming back
 * from the list lands on Choose a server again.
 */
@Composable
internal fun AnimeServerChoiceButtons(
    dub: Boolean,
    passionFont: FontFamily,
    focusRequesters: List<FocusRequester>,
    startOn: Int,
    onDefault: () -> Unit,
    onChoose: () -> Unit,
    onBack: () -> Unit,
    onFocusTaken: () -> Unit = {},
) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(SERVER_PAIR_FADE_MS))
    }
    LaunchedEffect(Unit) {
        delay(50)
        try { focusRequesters[startOn.coerceIn(0, 1)].requestFocus() } catch (_: Exception) {}
        onFocusTaken()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
        OverlayWatchButton(
            text = "Default server",
            colors = if (dub) AnimeDubButtonColors else AnimeSubButtonColors,
            passionFont = passionFont,
            focusRequester = focusRequesters[0],
            index = 0,
            focusRequesters = focusRequesters.subList(0, 2),
            onBack = onBack,
            onClick = onDefault,
        )
        OverlayWatchButton(
            text = "Choose a server",
            colors = if (dub) AnimeDubChooseColors else AnimeSubChooseColors,
            passionFont = passionFont,
            focusRequester = focusRequesters[1],
            index = 1,
            focusRequesters = focusRequesters.subList(0, 2),
            onBack = onBack,
            onClick = onChoose,
        )
    }
}

/** "● English  ·  which server?" -- the step's question, over the pair or the list. */
@Composable
internal fun AnimeServerStepLabel(dub: Boolean, text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(Modifier.size(6.dp).background(animeAccent(dub), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (dub) "English" else "Japanese",
            color = Color.White,
            fontFamily = AppTypography.interBold,
            fontSize = 9.5.sp,
        )
        Text(
            text = "  ·  $text",
            color = Color(0xFFBEC0CD),
            fontFamily = AppTypography.interSemiBold,
            fontSize = 9.5.sp,
        )
    }
}

/** The list's rows: this many show before it scrolls. */
private const val INLINE_SERVER_ROWS_SHOWN = 4
private val INLINE_SERVER_ROW_HEIGHT = 26.dp
private val INLINE_SERVER_ROW_GAP = 6.dp

/**
 * The server list, in the card where the overview and the buttons were. The
 * language's colour lights the focused row; the site's default is tagged.
 * Up / Down move (no wrapping, no wandering off the card), OK picks, BACK
 * returns to Default server / Choose a server.
 */
@Composable
internal fun AnimeInlineServerList(
    servers: List<ServerOption>,
    defaultIndex: Int,
    dub: Boolean,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusTaken: () -> Unit = {},
) {
    val focusRequesters = remember(servers) { List(servers.size) { FocusRequester() } }
    LaunchedEffect(servers) {
        if (servers.isNotEmpty()) {
            awaitFrame()
            awaitFrame()   // let the rows lay out before grabbing focus
            try { focusRequesters[0].requestFocus() } catch (_: Exception) {}
            onFocusTaken()
        }
    }
    Column(modifier) {
        AnimeServerStepLabel(dub, "pick a server")
        Spacer(Modifier.height(8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(INLINE_SERVER_ROW_GAP),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = INLINE_SERVER_ROW_HEIGHT * INLINE_SERVER_ROWS_SHOWN +
                                INLINE_SERVER_ROW_GAP * (INLINE_SERVER_ROWS_SHOWN - 1))
                .verticalScroll(rememberScrollState()),
        ) {
            servers.forEachIndexed { i, server ->
                InlineServerRow(
                    name = server.name,
                    tag = if (i == defaultIndex) "Default" else null,
                    accent = animeAccent(dub),
                    focusRequester = focusRequesters[i],
                    up = focusRequesters.getOrNull(i - 1),
                    down = focusRequesters.getOrNull(i + 1),
                    onSelect = { onSelect(server.name) },
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun InlineServerRow(
    name: String,
    tag: String?,
    accent: Color,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    onSelect: () -> Unit,
    onBack: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(INLINE_SERVER_ROW_HEIGHT)
            .background(if (focused) accent else Color(0x12FFFFFF), shape)
            .border(1.5.dp, if (focused) Color.White else Color.Transparent, shape)
            .padding(horizontal = 12.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val target = if (event.key == Key.DirectionUp) up else down
                        target?.let {
                            it.requestFocus()
                            BlueHiveApplication.playHoverSound()
                        }
                        true    // an edge goes nowhere, like the card's buttons
                    }
                    Key.DirectionLeft, Key.DirectionRight -> true
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        BlueHiveApplication.playClickSound()
                        onSelect()
                        true
                    }
                    Key.Back -> { onBack(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Text(
            text = name,
            color = if (focused) Color.White else Color(0xFFD6D6D6),
            fontFamily = AppTypography.interBold,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tag != null) {
            Text(
                text = tag,
                color = if (focused) Color(0xFFFFE4E4) else Color(0xFF8B8B8B),
                fontFamily = AppTypography.interSemiBold,
                fontSize = 8.5.sp,
            )
        }
    }
}



//0x33000000 = 20% opacity (very light)
//0x50000000 = 31% opacity (your current - too light)
//0x80000000 = 50% opacity (medium)
//0xCC000000 = 80% opacity (recommended)
//0xE6000000 = 90% opacity (strong)
//0xFF000000 = 100% opacity (full black)
