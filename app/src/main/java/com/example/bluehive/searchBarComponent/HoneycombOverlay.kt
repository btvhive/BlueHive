package com.example.bluehive.searchBarComponent

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.SearchButtonColors
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
//  HONEYCOMB KEYBOARD — BlueHive's own keyboard
// ══════════════════════════════════════════════════════════════════════════════
//
// One card, everywhere BlueHive takes typing; the system keyboard is not used.
//
// SEARCH (Voice ... Search):
//   * over HOME, from the sidebar's Search Bar, and over the MEDIA CATALOG, from
//     its search bar (HoneycombKeyboardPopup) -- the screen behind blurs;
//   * over the SEARCH PAGE, from its bar, to refine (HoneycombKeyboardOverlay).
//   Search -- or a finished Voice search -- hands the text on and the search
//   page shows the results.
//   * on LIVE TV's search page, where the page places it (HoneycombKeyboardInline):
//   every key filters the page's tiles as it is typed, and Search moves to them.
//
// NAME (Shift ... Done), for a profile name on the create and manage screens
// (HoneycombNamePopup). Shift capitalises the next letter only; Done hands the
// name back. Names keep their capitals, searches are typed in lower case.
//
// BACK belongs to whoever hosts the card.
//
// NO SYSTEM KEYBOARD. Gboard's voice typing goes deaf after days of uptime;
// Voice here is BlueHive's own recognizer (VoiceSearch.kt): press OK and talk
// into the remote, no button to hold. It sits just over the letters, one UP
// from where the focus starts ('a').
//
// ONE FOCUS TARGET. The card is a single focusable that moves its own
// highlight, because the honeycomb needs its own UP/DOWN rule (HiveKeyboard.kt);
// Compose's geometric focus search drifts sideways on the offset rows.

private const val MAX_QUERY = 60

/** What the keyboard is typing. */
internal enum class HiveMode { SEARCH, NAME }

// ── Look ──────────────────────────────────────────────────────────────────────
private val PAGE_SCRIM   = Color(0x73000000)       // over the (blurred) search page
private val CARD_SHAPE   = RoundedCornerShape(18.dp)
// The sidebar's own panel colours (SidebarDisplay.kt, the expanded panel), so
// the keyboard and the sidebar beside it read as one surface.
private val CARD_BRUSH   = Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D)))
private val CARD_EDGE    = Color(0xFF333333)
private val FIELD_BG     = Color(0xFF0E0F14)
private val FIELD_BORDER = Color(0xFF2A2D37)
private val KEY_BG       = Color(0xFF262833)
private val KEY_TEXT     = Color(0xFFD9DCE3)
// The focused key: white, with dark text on it (the user's pick; it was gold,
// which now marks Search).
private val FOCUS        = Color.White
private val FOCUS_INK    = Color(0xFF111111)
// A Shift that is on reads gold.
private val GOLD         = Color(0xFFD19814)
// Search and Done, the keys that hand the text on: the details pages' Search
// button's amethyst, read from it so the two cannot drift, with white on it
// (the user's calls: Search 2026-10-28, Done -- the profile name keyboard --
// 2026-10-29, both gold with dark text before; forest green until v625). A
// flat key, so it takes the button's band -- the brighter purple the button
// reads as -- not its darker top.
private val FINISH_PURPLE = SearchButtonColors.band
private val VOICE_BLUE   = Color(0xFF4C63AC)
private val VOICE_LIT    = Color(0xFF7C93DC)
private val PLACEHOLDER  = Color(0x806F7482)       // the hint in the empty box, at half opacity
private val COUNT_TEXT   = Color(0xFF6F7482)
private val LISTEN_BLUE  = Color(0xFF9DB2F0)
private val ERROR_SOFT   = Color(0xFFFFB4A8)
private val STATUS_TEXT  = Color(0xFF9AA1B2)       // a host's own line (Live TV: "14 channels · 13 live")

// ── Size ──────────────────────────────────────────────────────────────────────
private val KEY_W       = 43.dp
private val KEY_H       = 50.dp
private val KEY_GAP     = 6.dp
private val PITCH       = KEY_W + KEY_GAP
private val ROW_STEP    = 43.dp                     // 3/4 of a hexagon plus a gap: rows interlock
private val COMB_W      = KEY_W * HiveKeyboard.LONG_ROW + KEY_GAP * (HiveKeyboard.LONG_ROW - 1)
private val COMB_H      = ROW_STEP * 3 + KEY_H
private val PILL_H      = 35.dp
private val PILL_GAP    = 8.dp
private val PILL_SHAPE  = RoundedCornerShape(PILL_H / 2)
// Each action key is three hexagons wide; the two sit side by side in the middle.
private val ACTION_W    = KEY_W * HiveKeyboard.ACTION_KEYS + KEY_GAP * (HiveKeyboard.ACTION_KEYS - 1)
private val FIELD_H     = 42.dp
private val FIELD_SHAPE = RoundedCornerShape(10.dp)
private val SHADOW_ROOM = 32.dp                     // popup window margin, so the shadow is not clipped

/** Pointy-top hexagon. */
private val HexShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w / 2f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w / 2f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

/**
 * Blurs whatever this is applied to while [active] -- for the screen behind
 * the keyboard. The same GPU blur home puts behind its sidebar (API 31+;
 * older boxes go without, and get only the screen's own dim).
 */
fun Modifier.hiveBackdropBlur(active: Boolean): Modifier =
    if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(10f, 10f, android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else this

/**
 * Search, over home, the media catalog and the details pages. A pop-up
 * window, so the screen underneath keeps its own state while this has the
 * focus; BACK closes it. Call it always, with [visible] saying whether it
 * shows.
 *
 * KEPT WARM (v627). Building the card took ~170 ms of the ~300 ms from the
 * press to its first frame, and the window ~50 ms more (measured on the onn
 * box, 2026-11-02). So it is built once, hidden and not focusable,
 * [KEYBOARD_WARM_DELAY_MS] after the screen opens -- out of the way of the
 * screen's own first frames -- and a press only shows it. A press before
 * then builds it on the spot, as it always did.
 */
@Composable
fun HoneycombKeyboardPopup(
    visible: Boolean,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(KEYBOARD_WARM_DELAY_MS)
        warm = true
    }
    if (!warm && !visible) return
    CardPopup(onDismiss, focusable = visible) {
        HoneycombKeyboardCard(initialText = "", onSubmit = onSearch, shown = visible)
    }
}

/** How long after a screen opens its search keyboard is built, hidden. */
private const val KEYBOARD_WARM_DELAY_MS = 2_000L

/**
 * A profile name. [onChange] follows every key, so a screen that shows the
 * name behind the keyboard keeps it current; [onDone] is the Done key. BACK
 * closes it -- what that means for the name is the screen's call.
 */
@Composable
fun HoneycombNamePopup(
    initialText: String,
    maxLength: Int,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    onChange: (String) -> Unit = {},
) {
    CardPopup(onDismiss) {
        HoneycombKeyboardCard(
            initialText   = initialText,
            onSubmit      = onDone,
            mode          = HiveMode.NAME,
            maxLength     = maxLength,
            onDraftChange = onChange,
        )
    }
}

/**
 * Search, over the search page, from its bar. [initialText] is what the bar
 * shows, so opening it again refines the last search instead of starting over.
 * The page blurs itself behind this and handles BACK.
 */
@Composable
internal fun HoneycombKeyboardOverlay(
    initialText: String,
    onSearch: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PAGE_SCRIM),
        contentAlignment = Alignment.Center,
    ) {
        HoneycombKeyboardCard(initialText = initialText, onSubmit = onSearch)
    }
}

/**
 * What a page that places the card itself tells it (Live TV's search page).
 * [text] is the page's copy of the query; the box takes it whenever [textSets]
 * goes up -- the page set it itself, as a quick pick does (typing never bumps
 * it, so a fast typist cannot be overwritten by a stale copy). [status] is
 * the page's line under the box; [showFocus] is false while the page has the
 * focus elsewhere, so no key looks focused; [onEdge] hears a press past the
 * card's edge, so the page can take the focus.
 */
internal class HiveInline(
    val text: String,
    val textSets: Int,
    val placeholder: String,
    val status: String?,
    val showFocus: Boolean,
    val focus: FocusRequester,
    val onEdge: (HiveDir) -> Unit,
)

/**
 * Search on a page that lists what it finds while the viewer types (Live TV):
 * the card where the page puts it, no pop-up and no scrim. [onTextChange]
 * follows every key; [onSearch] is the Search key, or a finished Voice search.
 * BACK goes on to the page.
 */
@Composable
internal fun HoneycombKeyboardInline(
    inline: HiveInline,
    onTextChange: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    HoneycombKeyboardCard(
        initialText   = inline.text,
        onSubmit      = onSearch,
        onDraftChange = onTextChange,
        inline        = inline,
    )
}

@Composable
private fun CardPopup(onDismiss: () -> Unit, focusable: Boolean = true, card: @Composable () -> Unit) {
    Popup(
        popupPositionProvider = CenteredInWindow,
        onDismissRequest = {
            BlueHiveApplication.playBackOutSound()
            onDismiss()
        },
        properties = PopupProperties(
            // False while a warm keyboard is hidden: the screen keeps the
            // focus and the keys.
            focusable = focusable,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.padding(SHADOW_ROOM)) { card() }
    }
}

/** Centre of the whole window, whatever the pop-up is anchored to. */
private object CenteredInWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (windowSize.width - popupContentSize.width) / 2,
        y = (windowSize.height - popupContentSize.height) / 2,
    )
}

@Composable
private fun HoneycombKeyboardCard(
    initialText: String,
    onSubmit: (String) -> Unit,
    mode: HiveMode = HiveMode.SEARCH,
    maxLength: Int = MAX_QUERY,
    onDraftChange: (String) -> Unit = {},
    inline: HiveInline? = null,
    /** False while a warm keyboard is hidden -- see HoneycombKeyboardPopup. */
    shown: Boolean = true,
) {
    val forNames  = mode == HiveMode.NAME
    var draft     by remember { mutableStateOf(initialText.take(maxLength)) }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    var shift     by remember { mutableStateOf(false) }
    val ownFR     = remember { FocusRequester() }
    val hostFR    = inline?.focus ?: ownFR

    fun setDraft(text: String) {
        draft = text
        onDraftChange(text)
    }

    // A page that places the card may set the query itself (a quick pick).
    LaunchedEffect(inline?.textSets) {
        if (inline != null && inline.textSets > 0) draft = inline.text.take(maxLength)
    }

    val voice = rememberVoiceSearch(
        onStart = {
            voiceHint = null
            setDraft("")
        },
        onPartial = { setDraft(cleanSpoken(it).take(maxLength)) },
        // Said in full: search it straight away, as pressing Search would.
        onFinal = { text ->
            val q = cleanSpoken(text).take(maxLength)
            setDraft(q)
            if (q.isNotBlank()) onSubmit(q)
        },
        onFailed = { failure ->
            BlueHiveApplication.playErrorSound()
            voiceHint = failure.message
        },
    )

    var numbers  by remember { mutableStateOf(false) }
    val keyboard = remember(numbers, voice.available, shift) {
        HiveKeyboard(
            numbers  = numbers,
            hasVoice = !forNames && voice.available,
            forNames = forNames,
            upper    = forNames && shift,
        )
    }
    // Every opening starts on 'a', the first letter -- search included (the
    // user's call; it used to start on Voice).
    var spot     by remember { mutableStateOf(keyboard.firstKey) }
    var stickyX  by remember { mutableFloatStateOf(keyboard.centre(spot)) }

    // Arrives with a short fade and settle rather than popping in -- each
    // time it is shown. A warm keyboard stays built while hidden, so every
    // opening after its first starts over: an empty box, letters, on 'a'.
    val appear = remember { Animatable(0f) }
    var openings by remember { mutableIntStateOf(0) }
    LaunchedEffect(shown) {
        if (!shown) {
            if (voice.listening) voice.cancel()
            appear.snapTo(0f)
            return@LaunchedEffect
        }
        if (openings++ > 0) {
            draft = initialText.take(maxLength)
            numbers = false
            shift = false
            voiceHint = null
            val letters = HiveKeyboard(
                numbers  = false,
                hasVoice = !forNames && voice.available,
                forNames = forNames,
                upper    = false,
            )
            spot = letters.firstKey
            stickyX = letters.centre(spot)
        }
        launch { appear.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        awaitFrame()
        runCatching { hostFR.requestFocus() }
    }

    LaunchedEffect(voiceHint) {
        if (voiceHint != null) {
            delay(5_000)
            voiceHint = null
        }
    }

    fun type(c: Char) {
        if (voice.listening) voice.cancel()
        if (draft.length < maxLength) setDraft(draft + c)
        // Shift covers the next letter only, as on a phone keyboard.
        if (shift && c.isLetter()) shift = false
    }

    fun submit() {
        if (voice.listening) voice.cancel()
        if (!forNames) {
            // An empty search has nothing to show; an empty name is the
            // screen's to refuse, as it always was.
            val q = draft.trim()
            if (q.isEmpty()) {
                BlueHiveApplication.playErrorSound()
                return
            }
            BlueHiveApplication.playClickSound()
            onSubmit(q)
        } else {
            BlueHiveApplication.playClickSound()
            onSubmit(draft)
        }
    }

    fun press(key: HiveKey, repeat: Boolean) {
        when (key.action) {
            HiveAction.CHAR -> if (!repeat) {
                BlueHiveApplication.playClickSound()
                type(key.char)
            }
            HiveAction.SPACE -> if (!repeat && draft.isNotEmpty() && !draft.endsWith(' ')) {
                BlueHiveApplication.playClickSound()
                type(' ')
            }
            // Held down, delete keeps going -- the quick way to clear the box.
            HiveAction.DELETE -> if (draft.isNotEmpty()) {
                if (!repeat) BlueHiveApplication.playClickSound()
                if (voice.listening) voice.cancel()
                setDraft(draft.dropLast(1))
            }
            HiveAction.MODE -> if (!repeat) {
                BlueHiveApplication.playClickSound()
                numbers = !numbers
            }
            HiveAction.SHIFT -> if (!repeat) {
                BlueHiveApplication.playClickSound()
                shift = !shift
            }
            HiveAction.VOICE -> if (!repeat) {
                BlueHiveApplication.playClickSound()
                voiceHint = null
                voice.toggle()
            }
            HiveAction.SEARCH, HiveAction.DONE -> if (!repeat) submit()
        }
    }

    // Every D-pad press belongs to the keyboard; an edge is a silent dead end,
    // unless the page placing the card listens (Live TV moves to its tiles).
    fun navigate(dir: HiveDir) {
        val next = keyboard.move(spot, dir, stickyX) ?: run {
            inline?.onEdge?.invoke(dir)
            return
        }
        spot = next
        if (dir == HiveDir.LEFT || dir == HiveDir.RIGHT) stickyX = keyboard.centre(next)
        BlueHiveApplication.playHoverSound()
    }

    Column(
        modifier = Modifier
            .graphicsLayer {
                alpha = appear.value
                val s = 0.94f + 0.06f * appear.value
                scaleX = s
                scaleY = s
            }
            .shadow(28.dp, CARD_SHAPE, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CARD_SHAPE)
            .background(CARD_BRUSH)
            .border(1.dp, CARD_EDGE, CARD_SHAPE)
            .focusRequester(hostFR)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = event.nativeKeyEvent.repeatCount > 0
                when (event.key) {
                    Key.DirectionLeft  -> { navigate(HiveDir.LEFT); true }
                    Key.DirectionRight -> { navigate(HiveDir.RIGHT); true }
                    Key.DirectionUp    -> { navigate(HiveDir.UP); true }
                    Key.DirectionDown  -> { navigate(HiveDir.DOWN); true }
                    Key.DirectionCenter -> { press(keyboard.key(spot), repeat); true }
                    // A real keyboard (USB, or a phone remote app) types straight in.
                    Key.Enter, Key.NumPadEnter -> {
                        if (!repeat) submit()
                        true
                    }
                    Key.Backspace -> {
                        if (draft.isNotEmpty()) setDraft(draft.dropLast(1))
                        true
                    }
                    else -> {
                        val ch = event.nativeKeyEvent.unicodeChar
                        if (ch > 0 && !Character.isISOControl(ch)) {
                            val c = ch.toChar()
                            type(if (forNames) c else c.lowercaseChar())
                            true
                        } else false            // BACK goes on to the host
                    }
                }
            }
            .focusable()
            .padding(horizontal = 21.dp, vertical = 18.dp),
    ) {
        DraftField(
            active      = shown,
            draft       = draft,
            listening   = voice.listening,
            placeholder = inline?.placeholder ?: if (forNames) "Enter a name..." else "Search movies, shows and anime",
            count       = if (forNames) "${draft.length}/$maxLength" else null,
            searchIcon  = !forNames,
        )

        // Reserved, so a message appearing never shifts the keyboard.
        Box(
            modifier = Modifier
                .width(COMB_W)
                .height(22.dp)
                .padding(start = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val line = if (voice.listening) "Listening… talk into the remote" else voiceHint ?: inline?.status
            if (line != null) {
                Text(
                    text       = line,
                    color      = when {
                        voice.listening -> LISTEN_BLUE
                        voiceHint != null -> ERROR_SOFT
                        else -> STATUS_TEXT
                    },
                    fontSize   = 11.sp,
                    fontFamily = AppTypography.interSemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }

        // Voice and Search (Shift and Done) sit with the keys, nearer them than
        // the box -- the same distance over the comb as the pills are under it.
        // No key looks focused while the page placing the card has the focus.
        val shown = if (inline?.showFocus == false) NO_SPOT else spot
        Spacer(Modifier.height(4.dp))
        ActionRow(keyboard, focused = shown, voice = voice, shiftOn = shift)
        Spacer(Modifier.height(14.dp))
        Honeycomb(keyboard, focused = shown)
        Spacer(Modifier.height(14.dp))
        PillRow(keyboard, focused = shown)
    }
}

private val NO_SPOT = HiveSpot(-1, -1)

@Composable
private fun DraftField(
    /** False while hidden: the cursor holds still (no frames for an unseen card). */
    active: Boolean,
    draft: String,
    listening: Boolean,
    placeholder: String,
    count: String?,
    searchIcon: Boolean,
) {
    val blink = if (active) {
        rememberInfiniteTransition(label = "cursor").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
            label = "cursorAlpha",
        ).value
    } else 1f
    val cursor = @Composable {
        Box(
            Modifier
                .width(2.dp)
                .height(18.dp)
                .graphicsLayer(alpha = blink)
                .background(Color.White)
        )
    }
    Row(
        modifier = Modifier
            .width(COMB_W)
            .height(FIELD_H)
            .clip(FIELD_SHAPE)
            .background(FIELD_BG)
            .border(1.dp, if (listening) VOICE_BLUE else FIELD_BORDER, FIELD_SHAPE)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A name field is not a search box, so it goes without the magnifier.
        if (searchIcon) {
            Image(
                painter = painterResource(R.drawable.magnifying_glass_raw_image),
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer(alpha = 0.55f),
            )
            Spacer(Modifier.width(10.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draft.isEmpty()) {
                // Empty: the cursor waits at the start, ahead of the hint.
                cursor()
                Spacer(Modifier.width(5.dp))
                Text(
                    text       = placeholder,
                    color      = PLACEHOLDER,
                    fontSize   = 14.sp,
                    fontFamily = AppTypography.interSemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            } else {
                // A long entry keeps its END in view -- the part being typed.
                Text(
                    text       = draft,
                    color      = Color.White,
                    fontSize   = 15.sp,
                    fontFamily = AppTypography.interSemiBold,
                    maxLines   = 1,
                    softWrap   = false,
                    overflow   = TextOverflow.StartEllipsis,
                    modifier   = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(2.dp))
                cursor()
            }
        }
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text       = count,
                color      = COUNT_TEXT,
                fontSize   = 11.sp,
                fontFamily = AppTypography.interSemiBold,
            )
        }
    }
}

@Composable
private fun Honeycomb(keyboard: HiveKeyboard, focused: HiveSpot) {
    Box(Modifier.size(COMB_W, COMB_H)) {
        for (r in keyboard.combRows) {
            keyboard.rows[r].forEachIndexed { c, key ->
                val isFocused = focused == HiveSpot(r, c)
                HexKey(
                    label   = key.label,
                    focused = isFocused,
                    modifier = Modifier
                        .offset(
                            x = PITCH * (keyboard.centres[r][c] - 0.5f),
                            y = ROW_STEP * (r - keyboard.combRows.first),
                        )
                        .zIndex(if (isFocused) 1f else 0f),
                )
            }
        }
    }
}

@Composable
private fun HexKey(label: String, focused: Boolean, modifier: Modifier) {
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(110), label = "hexScale")
    Box(
        modifier = modifier
            .size(KEY_W, KEY_H)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(HexShape)
            .background(if (focused) FOCUS else KEY_BG),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = label,
            color      = if (focused) FOCUS_INK else KEY_TEXT,
            fontSize   = 18.sp,
            fontFamily = AppTypography.interSemiBold,
        )
    }
}

/**
 * Voice and Search -- Shift and Done for a name -- side by side in the middle,
 * over the comb: the way in on the left, the way out on the right, each three
 * keys wide, with the text box's own corners.
 */
@Composable
private fun ActionRow(
    keyboard: HiveKeyboard,
    focused: HiveSpot,
    voice: VoiceSearchState,
    shiftOn: Boolean,
) {
    // Runs only while listening: an idle card -- shown or warm and hidden --
    // asks for no frames.
    val pulse = if (voice.listening) {
        rememberInfiniteTransition(label = "voicePulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "voicePulseLevel",
        )
    } else null
    Row(
        modifier = Modifier.width(COMB_W),
        horizontalArrangement = Arrangement.spacedBy(PILL_GAP, Alignment.CenterHorizontally),
    ) {
        val row = keyboard.actionRow
        keyboard.rows[row].forEachIndexed { c, key ->
            FlatKey(
                key       = key,
                focused   = focused == HiveSpot(row, c),
                shape     = FIELD_SHAPE,
                // Read only while listening, so the pulse never recomposes an idle card.
                accent    = when {
                    HiveKeyboard.isFinish(key.action) -> FINISH_PURPLE
                    key.action == HiveAction.VOICE -> lerp(
                        VOICE_BLUE, VOICE_LIT,
                        if (voice.listening) maxOf(pulse?.value ?: 0f, voice.level) else 0f,
                    )
                    else -> null
                },
                accentInk = Color.White,
                lit       = key.action == HiveAction.SHIFT && shiftOn,
                listening = voice.listening,
                modifier  = Modifier.width(ACTION_W),
            )
        }
    }
}

/** 123, space and delete, across the comb's full width. */
@Composable
private fun PillRow(keyboard: HiveKeyboard, focused: HiveSpot) {
    Row(
        modifier = Modifier.width(COMB_W),
        horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
    ) {
        val row = keyboard.pillRow
        keyboard.rows[row].forEachIndexed { c, key ->
            FlatKey(
                key      = key,
                focused  = focused == HiveSpot(row, c),
                shape    = PILL_SHAPE,
                modifier = Modifier.weight(keyboard.pillWeights[c]),
            )
        }
    }
}

/**
 * Any key that is not a hexagon. Focus is white; [accent] colours Voice (blue)
 * and Search/Done (amethyst), with [accentInk] on it; [lit] is a Shift that is on,
 * which reads gold even when the focus is elsewhere.
 */
@Composable
private fun FlatKey(
    key: HiveKey,
    focused: Boolean,
    shape: Shape,
    modifier: Modifier,
    accent: Color? = null,
    accentInk: Color = Color.White,
    lit: Boolean = false,
    listening: Boolean = false,
) {
    val bg = when {
        focused -> FOCUS
        accent != null -> accent
        else -> KEY_BG
    }
    val fg = when {
        focused -> FOCUS_INK
        lit -> GOLD
        accent != null -> accentInk
        else -> KEY_TEXT
    }
    Row(
        modifier = modifier
            .height(PILL_H)
            .clip(shape)
            .background(bg)
            .then(if (lit && !focused) Modifier.border(1.5.dp, GOLD, shape) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (key.action) {
            HiveAction.VOICE -> {
                PillIcon(R.drawable.ic_voice_mic, fg)
                Spacer(Modifier.width(5.dp))
                PillLabel(if (listening) "Listening" else key.label, fg)
            }
            HiveAction.SEARCH -> {
                PillIcon(R.drawable.magnifying_glass_raw_image, fg)
                Spacer(Modifier.width(5.dp))
                PillLabel(key.label, fg)
            }
            HiveAction.DONE -> {
                PillIcon(R.drawable.ic_check, fg, size = 16)
                Spacer(Modifier.width(4.dp))
                PillLabel(key.label, fg)
            }
            HiveAction.DELETE -> PillIcon(R.drawable.ic_backspace, fg, size = 16)
            else -> PillLabel(key.label, fg)
        }
    }
}

@Composable
private fun PillIcon(res: Int, tint: Color, size: Int = 14) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size.dp),
    )
}

@Composable
private fun PillLabel(text: String, color: Color) {
    Text(
        text       = text,
        color      = color,
        fontSize   = 12.sp,
        fontFamily = AppTypography.interSemiBold,
        maxLines   = 1,
    )
}
