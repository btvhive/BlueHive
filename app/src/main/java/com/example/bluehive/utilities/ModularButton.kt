package com.example.bluehive.utilities

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxScope
import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BlueHiveApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer

private const val TAG = "ModularButton"

@Immutable
data class ModularButtonDimensions(
    val mainWidth: Dp = 162.dp,
    val mainHeight: Dp = 23.5.dp,
    val mainYOffset: Dp = 0.dp,
    val secondWidth: Dp = 162.dp,
    val secondHeight: Dp = 22.dp,
    val secondYOffset: Dp = 7.dp,
    val shadowHeight: Dp = 6.dp,
    val glowWidth: Dp = 203.dp,
    val glowHeight: Dp = 58.5.dp,
    val mainCornerRadius: Float = 8f,
    val secondCornerRadius: Float = 6f,
    val shadowOffset: Dp = 0.dp,
    /** The soft drop shadow under an unfocused button; home's radio tabs go without (v634). */
    val dropShadow: Boolean = true,
)

@Stable
data class ModularButtonColors(
    val mainDefault: Color = Color(0xFF6C6C6C),
    val mainToggled: Color = Color(0xFF535C67),
    val mainFocused: Color = Color(0xFF535C67),
    val secondDefault: Color = Color(0xFF2E2C37),
    val secondFocused: Color = Color(0xFF52505A),
    val secondToggled: Color = Color(0xFF372B67),
    val textFocused: Color = Color.White,
    val textUnfocused: Color = Color(0xFF8A8F97),
    val textUnfocusedToggled: Color = Color(0xFF918181),
    // Used when toggleable & toggled (unfocused)
    val textToggled: Color = Color(0xFF8A8F97),
    // ⭐ NEW: used when toggleable & toggled & focused (glow on it)
    val textToggledFocus: Color = Color(0xFFCDCDCD),
    /**
     * The split face (source buttons, v613): a band of its own colour across
     * the lower [BAND_FRACTION] of the face, and a 1 dp edge around it, each
     * with an idle, focused and pressed colour. Unset draws neither.
     */
    val band: Color = Color.Unspecified,
    val bandFocused: Color = Color.Unspecified,
    val bandToggled: Color = Color.Unspecified,
    val outline: Color = Color.Unspecified,
    val outlineFocused: Color = Color.Unspecified,
    /** The shadow the face casts onto the strip just under it (drawSecond); unset casts none. */
    val faceShadow: Color = Color.Unspecified,
)

// ── The "watch" button palettes ─────────────────────────────────────────────
//
// WHAT EACH LAYER IS, looking at one button:
//
//   main    the face of the button, the part carrying the label
//   second  the thin strip peeking out underneath it
//   shadow  the soft drop under the whole thing
//   text    the label
//
// and each has a Default (idle) and a Focused (D-pad on it) value. Toggled is
// unused by these three — none of them is a toggle.
//
// ALL THREE SHARED ONE BUG. Every call site set mainDefault and none set
// mainFocused, so focusing any of them fell back to this file's neutral default
// (0xFF535C67) and a blue, red or gold button turned GREY under the cursor.
//
// FOCUS NOW BRIGHTENS RATHER THAN DULLING. Each focused value is its own idle
// hue lifted in lightness, so focus reads as the button lighting up rather than
// becoming a different button. Several secondFocused values previously went
// DARKER than their idle counterpart, fighting the glow instead of supporting it.

/**
 * "Watch in English" -- the red anime button, on both details pages and on
 * Default server after English.
 *
 * THE ROYAL SPLIT IN RED (the user's pick, v622, mock-up A2): the source
 * buttons' recipe -- a deep top, a brighter band across the lower part, a
 * 1 dp edge, the face's shadow on the strip -- at hue 0. Each part keeps the
 * royal split's lightness (+4%, since red reads darker than purple at equal
 * lightness) at FULL saturation; royal's own saturation made a brownish red
 * next to the purple. Focus lifts every part toward white (top 18%, band
 * 20%, edge 35%, strip 10%), as on the source buttons. It replaced a flat
 * 0xFFCC0000 face over a 0xFF7A0000 strip.
 */
val AnimeDubButtonColors = ModularButtonColors(
    mainDefault   = Color(0xFFAE0000),
    mainFocused   = Color(0xFFBD2E2E),
    // Pressed: the same split, darker.
    mainToggled   = Color(0xFF7D0000),
    band          = Color(0xFFEA0000),
    bandFocused   = Color(0xFFEE3333),
    bandToggled   = Color(0xFFA80000),
    outline       = Color(0xFFFF4949),
    outlineFocused = Color(0xFFFF8989),
    // The strip under the face.
    secondDefault = Color(0xFF800000),
    secondFocused = Color(0xFF8D1A1A),
    // Selected/pressed strip. Without one every palette here fell back to
    // ModularButtonColors' default 0xFF372B67 -- a PURPLE strip under a red,
    // green or blue button the moment it was toggled.
    secondToggled = Color(0xFF600000),
    faceShadow    = Color(0x99000000),
    textUnfocusedToggled = Color(0xFF9E3939),
    textFocused   = Color.White,
    textUnfocused = Color(0xFFD6D6D6),
)

/**
 * "Watch in Japanese" -- the purple anime button, on both details pages and
 * on Default server after Japanese.
 *
 * THE ROYAL SPLIT IN PURPLE (the user's pick, v622, mock-up A): every part
 * at the royal split's own saturation and lightness, moved to hue 275. It
 * replaced a flat 0xFF8A1FD6 face over a 0xFF50127D strip.
 */
val AnimeSubButtonColors = ModularButtonColors(
    mainDefault   = Color(0xFF551A80),
    mainFocused   = Color(0xFF744397),
    mainToggled   = Color(0xFF3A1158),
    band          = Color(0xFF7628AE),
    bandFocused   = Color(0xFF9153BE),
    bandToggled   = Color(0xFF521A7A),
    outline       = Color(0xFFA45CD8),
    outlineFocused = Color(0xFFC495E6),
    secondDefault = Color(0xFF3C125A),
    secondFocused = Color(0xFF502A6A),
    secondToggled = Color(0xFF2A0C40),
    faceShadow    = Color(0x99000000),
    textUnfocusedToggled = Color(0xFF5F3C78),
    textFocused   = Color.White,
    textUnfocused = Color(0xFFD6D6D6),
)

/**
 * The source buttons, "Play / from Website 1" ... "Website 4".
 *
 * ONE PLACE, because there were three: the same colour block was pasted into
 * MoviesDetailsScreenCompose and twice into TVShowsDetailsScreenCompose, each
 * carrying its own copy of the missing-mainFocused bug. Fixing that in three
 * copies is how two of them drift.
 */
/**
 * The shadow behind the movie page's button labels -- "Watch on ...", Play
 * Trailer and Add to Favorites (the user's calls, 2026-10-26): dark, a little
 * below the text, so the label lifts off the face. In px: 3 down, 5 of blur
 * (1.5 dp and 2.5 dp on the onn box); v440 had 60% black, 2 down, 4 of blur.
 */
val ButtonLabelShadow = Shadow(
    color = Color(0xCC000000),
    offset = androidx.compose.ui.geometry.Offset(0f, 3f),
    blurRadius = 5f,
)

/**
 * The colour of the source buttons' "from Website N" line: butter (the
 * user's pick, v596; silver 0xFFC9CEE8 was tried in v598 and dropped, gold
 * 0xFFF0B44C before both).
 */
val SOURCE_LABEL_COLOR = Color(0xFFFFE58A)

val StreamSourceButtonColors = ModularButtonColors(
    // ROYAL SPLIT (the user's pick, v613, back again at v621, mock-up 3): a
    // deep royal top over a brighter band across the lower part, with a light
    // 1 dp edge. The split face replaced the pure 0xFF0004FF electric blue,
    // which glared on a TV and fought the navy page. Focus lifts every part
    // toward white (top 18%, band 20%, edge 35%, strip 10%), as in the
    // mock-up. Tried and dropped: charcoal + blue band (v617) top 1C1F2A,
    // band 1C2A7C, edge 4658A6, strip 14161E; midnight + teal band (v618) top
    // 10204A, band 155A70, edge 3E92AC, strip 0C1838; halfway between royal
    // and charcoal (v619) top 1B2655, band 223595, edge 5166BF, strip 131B3C;
    // royal with the top 8% darker (v620) top 182876.
    mainDefault   = Color(0xFF1A2C80),
    mainFocused   = Color(0xFF435297),
    // Pressed: the same split, darker.
    mainToggled   = Color(0xFF111D58),
    band          = Color(0xFF2840AE),
    bandFocused   = Color(0xFF5366BE),
    bandToggled   = Color(0xFF1A2C7A),
    outline       = Color(0xFF5C74D8),
    outlineFocused = Color(0xFF95A5E6),
    // The strip under the face.
    secondDefault = Color(0xFF121F5A),
    secondFocused = Color(0xFF2A356B),
    secondToggled = Color(0xFF0C1540),
    // The face's shadow on the strip (v616): black at 60%, fading out.
    faceShadow    = Color(0x99000000),
    textUnfocusedToggled = Color(0xFF3C4A78),
    textFocused   = Color.White,
    textUnfocused = Color(0xFFD6D6D6),
)

/**
 * How much taller the two-line buttons' face is than the other watch buttons'
 * 33 dp: 36 dp (the user's pick from the 33/36/38 mock-ups, v611) -- the
 * source buttons and, since v622, Watch in English / Japanese and the server
 * pair after them. Both details pages add it to the face, the strip's offset,
 * the glow and the row step, so the gaps between buttons do not change.
 */
val SOURCE_BUTTON_EXTRA_HEIGHT = 3.dp

/**
 * The source buttons' label (option B, v609): a rounded play symbol, then a
 * small "Play from" (Inter Bold, 8 sp, the label's colour) over a big
 * "Website N" (Passion One, 15 sp, butter), the group centred on the face.
 * The website is the line people need to read and say, so it is the big one:
 * "from Website N" at 6.5 sp under "Play" was unreadable (v606), and taller
 * buttons for a 10 sp line (option E, v607-v608) were dropped for this.
 * One place for both details pages, like StreamSourceButtonColors.
 */
fun streamSourceLabel(coverName: String, fontSize: Float = 15f) = ModularButtonTextConfig(
    text = coverName,
    fontSize = fontSize,
    shadow = ButtonLabelShadow,
    mainColor = SOURCE_LABEL_COLOR,
    subText = "Play from",
    subFontSize = 8f,
    subAbove = true,
    leadingIconRes = com.example.bluehive.R.drawable.ic_play_rounded,
    leadingIconSize = 16.dp,
)

/**
 * Watch in English / Japanese, in the source buttons' two lines (v622): a
 * small "Watch in" over the language, with the same play icon.
 */
fun animeLanguageLabel(language: String) = streamSourceLabel(language).copy(subText = "Watch in")

/**
 * Play Trailer and the two episode arrows on both detail screens.
 *
 * RED, WHERE THEY USED TO BE GREY. These are the page's playback controls and
 * were sharing one neutral grey with Add to Favorites and Extras -- five
 * identical buttons in a row, none of which looked like it did anything
 * different from its neighbour.
 *
 * They carried the same hole as every other palette here: mainDefault was set,
 * mainFocused was not, so focusing one fell through to this file's neutral
 * default (0xFF535C67) -- very slightly DARKER and bluer than their idle grey.
 * The button dulled under the cursor rather than lighting up, which is why the
 * focus state read as nothing happening at all.
 *
 * Muted rather than vivid on purpose. These sit beside the anime dub button's
 * 0xFFCC0000, and a second saturated red in the same app would read as the same
 * control. This one is desaturated and darker: clearly red, clearly secondary.
 */
val TrailerControlButtonColors = ModularButtonColors(
    // THE SAME RED, NOW SPLIT (v623, mock-up 8g): the idle face is still
    // 0xFF9E2B2B; a brighter band across its lower part, a 1 dp edge and the
    // face's shadow on the strip build it like the Website buttons. Focus
    // lifts every part toward white (top 18%, band 20%, edge 35%, strip 10%).
    mainDefault   = Color(0xFF9E2B2B),
    mainFocused   = Color(0xFFAF5151),
    mainToggled   = Color(0xFF6B1D1D),
    band          = Color(0xFFC23A3A),
    bandFocused   = Color(0xFFCE6161),
    bandToggled   = Color(0xFF842727),
    outline       = Color(0xFFDC7474),
    outlineFocused = Color(0xFFE8A5A5),
    secondDefault = Color(0xFF741F1F),
    secondFocused = Color(0xFF823535),
    secondToggled = Color(0xFF4F1515),
    faceShadow    = Color(0x99000000),
    textUnfocusedToggled = Color(0xFF804444),
    textFocused   = Color.White,
    textUnfocused = Color(0xFFF0F0F0),
)

/**
 * The details pages' Search button (v624): amethyst, the split recipe at a
 * jewel's saturation (hue 278, 55%), next to the azure Favorites and the
 * trailer red. Hot pink (v623) read as candy.
 */
val SearchButtonColors = ModularButtonColors(
    mainDefault   = Color(0xFF582277),
    mainFocused   = Color(0xFF764A8F),
    mainToggled   = Color(0xFF3C1751),
    band          = Color(0xFF7B30A6),
    bandFocused   = Color(0xFF9559B8),
    bandToggled   = Color(0xFF542171),
    outline       = Color(0xFFAB6ECF),
    outlineFocused = Color(0xFFC8A1E0),
    secondDefault = Color(0xFF3D1853),
    secondFocused = Color(0xFF502F64),
    secondToggled = Color(0xFF291038),
    faceShadow    = Color(0x99000000),
    textFocused   = Color.White,
    textUnfocused = Color.White,
)

/**
 * Add to Favorites on both details pages (v624, the user's pick "azure"):
 * a bright sky blue in the split recipe (hue 210, 85%), white words and a
 * filled star. Lime (v623) read as candy; gold and bronze before that.
 */
val FavoriteButtonColors = ModularButtonColors(
    mainDefault   = Color(0xFF1278DE),
    mainFocused   = Color(0xFF388EE3),
    mainToggled   = Color(0xFF0C5297),
    band          = Color(0xFF3491EF),
    bandFocused   = Color(0xFF59A5F2),
    bandToggled   = Color(0xFF2363A3),
    outline       = Color(0xFF73B0EC),
    outlineFocused = Color(0xFFA4CCF3),
    secondDefault = Color(0xFF0D549C),
    secondFocused = Color(0xFF2565A6),
    secondToggled = Color(0xFF09396A),
    faceShadow    = Color(0x99000000),
    textFocused   = Color.White,
    textUnfocused = Color.White,
)

/**
 * Remove from Favorites, once the title is saved (v624, mock-up A): the
 * azure dropped to a deep navy-azure, the words and star in pale ice blue.
 */
val FavoriteSavedButtonColors = ModularButtonColors(
    mainDefault   = Color(0xFF0F488A),
    mainFocused   = Color(0xFF35659D),
    mainToggled   = Color(0xFF0A315E),
    band          = Color(0xFF1461B8),
    bandFocused   = Color(0xFF3E7DC5),
    bandToggled   = Color(0xFF0E427D),
    outline       = Color(0xFF2C7FDD),
    outlineFocused = Color(0xFF76ACE9),
    secondDefault = Color(0xFF082749),
    secondFocused = Color(0xFF213D5B),
    secondToggled = Color(0xFF051B32),
    faceShadow    = Color(0x99000000),
    textFocused   = Color(0xFFA9D3FF),
    textUnfocused = Color(0xFFA9D3FF),
)

@Immutable
data class ModularButtonTextConfig(
    val text: String,
    val toggledText: String? = null,
    val fontSize: Float = 12f,
    val offsetX: Dp = 0.dp,

    /**
     * A shadow behind the label; null draws none, as every button did before.
     * An icon gets it too: a copy in the shadow's colour, offset and blurred
     * (the blur needs API 31; older boxes get a sharp copy).
     */
    val shadow: Shadow? = null,

    /**
     * Draw this drawable instead of [text].
     *
     * Added for symbols no font can be trusted with. A play triangle is a glyph
     * every font has; a pause that reads correctly at 11sp is not -- U+25AE
     * doubled looked like a snout, U+2016 merged into one bar, and spacing them
     * apart needed the button widened around the workaround.
     *
     * It renders in the SAME box as the text, so it inherits the press offset
     * and the focus colour: an icon overlaid on the button from outside would
     * sit still while the button moved under it.
     *
     * TINTED WITH THE LABEL COLOUR, so an icon button and a text button light up
     * identically on focus. Supply a white or single-colour asset.
     */
    val iconRes: Int? = null,
    val iconSize: Dp = 12.dp,

    /**
     * A second, smaller line in Inter Bold, under [text] or above it with
     * [subAbove] ("Play from" over "Website 1"), in [subColor] when set, else
     * the label's focus colour. With it the label is [leadingIconRes] with the
     * two lines beside it, the group centred on the button. Null keeps the one
     * centred line every other button has.
     */
    val subText: String? = null,
    val subFontSize: Float = 10f,
    val subColor: Color = Color.Unspecified,
    val subAbove: Boolean = false,
    /** [text]'s colour whatever the focus, for a stacked label; unset follows the focus colour. */
    val mainColor: Color = Color.Unspecified,
    /** Tinted with the label colour, like [iconRes]. */
    val leadingIconRes: Int? = null,
    val leadingIconSize: Dp = 16.dp,
)

@Immutable
data class ModularButtonGlowConfig(
    val enabled: Boolean = true,
    val defaultRes: Int? = null,
    val toggledRes: Int? = null,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = (-10).dp,

    // The glow PNGs are soft blooms, so this clip only shows on a glow with
    // hard edges; on the current ones it changes nothing visible.
    val cornerRadius: Dp = 0.dp,          // controls glow roundness
    val fadeOutDurationMillis: Int = 80,   // how fast glow fades out on press/toggle
    val fadeInDurationMillis: Int = 150,   // how fast glow fades back in on release
)

@Immutable
data class ModularButtonAnimationConfig(
    val pressOffset: Dp = 4.dp,
    val textOffsetDefault: Dp = 1.dp,
    val textOffsetPressed: Dp = 2.4.dp,
    // How long the button takes to travel, each way.
    val durationMillis: Int = 110,
    // How long it stays down before starting back up. The full press therefore
    // lasts bounceBackDelayMillis + durationMillis, not twice the duration —
    // the downward tween is cut short when the bounce begins.
    val bounceBackDelayMillis: Int = 80,
    /**
     * How long after the press to actually run onClick. 0 = immediately.
     *
     * WHY THIS IS NOT JUST A SLOWER ANIMATION. onClick used to fire on the same
     * frame as the press, so whatever it opened — the server-choice overlay, the
     * player — appeared while the button was still travelling downward. The
     * animation was never seen; it was covered by its own result, which read as
     * the button stuttering rather than pressing.
     *
     * Set this a little longer than bounceBackDelayMillis + durationMillis and
     * the button completes its press, settles, and THEN the overlay arrives.
     *
     * The click SOUND still plays immediately — feedback has to be instant even
     * when the action is not, or the button feels unresponsive rather than
     * deliberate.
     *
     * Defaults to 0, so every existing button behaves exactly as before.
     */
    val clickDelayMillis: Int = 0
)

/**
 * Press timing for every "watch" button — the four stream sources and the two
 * anime dub/sub buttons — on both detail screens.
 *
 * The two screens had drifted to different feels (movies 180/140, TV 110/80)
 * for the same control. This is the single timing; each screen still overrides
 * pressOffset and the text offsets, which are tuned to its own button size.
 *
 * Full press = 150 down-and-hold + 190 travel = 340ms, then a 60ms breath before
 * the overlay. Tune the weight of the press with the first two and how long the
 * pause afterwards feels with clickDelayMillis.
 */
val WatchButtonAnimation = ModularButtonAnimationConfig(
    pressOffset = 2.5.dp,
    textOffsetDefault = 1.dp,
    textOffsetPressed = 2.4.dp,
    durationMillis = 190,
    bounceBackDelayMillis = 150,
    clickDelayMillis = 400,
)

/**
 * ModularButton's default palette, built once. As an inline default
 * (`= ModularButtonColors()`) its constructor and every field's default ran
 * inside ModularButton, which is held to 2,500 units / 100 registers
 * (checkMethodSizes); the split-face fields (v613) took it to 2,490 / 96.
 */
private val DefaultButtonColors = ModularButtonColors()

@Composable
fun ModularButton(
    modifier: Modifier = Modifier,
    textConfig: ModularButtonTextConfig,
    isToggleable: Boolean,
    toggled: Boolean = false,
    isFocusable: Boolean = true,
    onClick: () -> Unit,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    dimensions: ModularButtonDimensions = ModularButtonDimensions(),
    colors: ModularButtonColors = DefaultButtonColors,
    glowConfig: ModularButtonGlowConfig = ModularButtonGlowConfig(),
    animationConfig: ModularButtonAnimationConfig = ModularButtonAnimationConfig(),
    externalToggled: Boolean? = null,       // parent-controlled toggle (for radio-style groups)
    playClickSound: Boolean = true,
    // Fired only when this button GAINS focus, never on a loss and never
    // on a recomposition reporting the same state. Default does nothing,
    // so every existing call site is unaffected.
    onFocusGained: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    var keepGlow by remember { mutableStateOf(false) }
    var internalToggled by remember(isToggleable, toggled) {
        mutableStateOf(if (isToggleable) toggled else false)
    }
    var pressWave by remember { mutableIntStateOf(0) }

    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(newValue = onClick)

    // Drives the deferred onClick — see ModularButtonAnimationConfig.clickDelayMillis.
    // Scoped to the composition, so a button that leaves the screen mid-press
    // drops its pending action rather than firing into a dead tree.
    val scope = rememberCoroutineScope()
    var clickPending by remember { mutableStateOf(false) }

    // ✅ single source-of-truth for toggle state
    val effectiveToggled = externalToggled ?: internalToggled

    val faceYOffset = animateDpAsState(
        targetValue = if ((isToggleable && effectiveToggled) || (!isToggleable && pressWave % 2 == 1))
            animationConfig.pressOffset else 0.dp,
        animationSpec = tween(durationMillis = animationConfig.durationMillis),
        label = "mainYOffset"
    )
    val mainYOffset by faceYOffset

    // Read while placing (offsetWithY), never while composing: a press
    // re-placed the label but recomposed the whole button every frame (v515).
    val textYOffset = animateDpAsState(
        targetValue = if ((isToggleable && effectiveToggled) || (!isToggleable && pressWave % 2 == 1))
            animationConfig.textOffsetPressed else animationConfig.textOffsetDefault,
        animationSpec = tween(durationMillis = animationConfig.durationMillis),
        label = "textYOffset"
    )


    // Read only in the glow's layer (ModularButtonGlow).
    val glowAlpha = animateFloatAsState(
        targetValue = when {
            !isFocusable                                  -> 0f
            !isFocused                                    -> 0f
            !isToggleable && pressWave % 2 == 1           -> 0f
            isToggleable && effectiveToggled && isFocused -> 0.6f
            else                                          -> 1f
        },
        animationSpec = tween(
            durationMillis = if (!isToggleable && pressWave % 2 == 1)
                glowConfig.fadeOutDurationMillis
            else
                glowConfig.fadeInDurationMillis
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(pressWave) {
        if (!isToggleable && pressWave % 2 == 1) {
            delay(animationConfig.bounceBackDelayMillis.toLong())
            pressWave++
        }
    }

    fun handleButtonClick() {
        if (isToggleable && externalToggled == null) {
            // Only manage state locally if parent isn't controlling it
            internalToggled = !internalToggled
        } else if (!isToggleable) {
            pressWave++
        }

        // ALWAYS IMMEDIATE, even when the action is deferred below. A press that
        // is silent until its overlay appears reads as a missed input, and the
        // viewer presses again.
        if (playClickSound) {
            BlueHiveApplication.playClickSound()
        }

        // Unchanged for every button that has not asked for a delay.
        if (animationConfig.clickDelayMillis <= 0) {
            currentOnClick()
            return
        }

        // A second press while one is already in flight still animates and still
        // clicks audibly, but must not run the action twice — that is how one
        // press on a stream button opens two players.
        if (clickPending) return
        clickPending = true
        scope.launch {
            delay(animationConfig.clickDelayMillis.toLong())
            clickPending = false
            currentOnClick()
        }
    }

    val isPressed = !isToggleable && pressWave % 2 == 1

    val mainColor = when {
        isToggleable && effectiveToggled -> colors.mainToggled
        isPressed -> colors.mainToggled
        isFocused -> colors.mainFocused
        else -> colors.mainDefault
    }

    val totalButtonHeight = dimensions.secondYOffset + dimensions.secondHeight + dimensions.shadowHeight





    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier.size(
            width = dimensions.glowWidth,
            height = dimensions.glowHeight
        )
    ) {
        // 🔹 Glow image behind the button
        if (glowConfig.enabled && isFocusable) {

            val glowRes =
                if (isToggleable && effectiveToggled)
                    glowConfig.toggledRes ?: glowConfig.defaultRes
                else
                    glowConfig.defaultRes

            glowRes?.let { ModularButtonGlow(it, dimensions, glowConfig, glowAlpha) }
        }

        // 🔹 Main button body + shadow
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .size(width = dimensions.mainWidth, height = totalButtonHeight)
                .align(Alignment.TopStart)
                .drawBehind {
                    // Shadow stack (only when unfocused)
                    if (!isFocused && dimensions.dropShadow) {
                        val shadowBaseY =
                            dimensions.mainHeight.toPx() + dimensions.shadowOffset.toPx()

                        for (i in 0..2) {
                            val expansion = i * 2f
                            drawRoundRect(
                                color = Color(0xFF040404).copy(alpha = 0.3f - (i * 0.05f)),
                                topLeft = Offset(
                                    -expansion / 2f,
                                    shadowBaseY + i
                                ),
                                size = Size(
                                    dimensions.mainWidth.toPx() + expansion,
                                    dimensions.shadowHeight.toPx()
                                ),
                                cornerRadius = CornerRadius(
                                    dimensions.secondCornerRadius,
                                    dimensions.secondCornerRadius
                                )
                            )
                        }
                        for (i in 3..5) {
                            val expansion = i * 3f
                            drawRoundRect(
                                color = Color(0xFF040404).copy(alpha = 0.15f - (i * 0.02f)),
                                topLeft = Offset(
                                    -expansion / 2f,
                                    shadowBaseY + (i * 1.5f)
                                ),
                                size = Size(
                                    dimensions.mainWidth.toPx() + expansion,
                                    dimensions.shadowHeight.toPx() + (i * 0.8f)
                                ),
                                cornerRadius = CornerRadius(
                                    dimensions.secondCornerRadius + (i * 0.5f),
                                    dimensions.secondCornerRadius + (i * 0.5f)
                                )
                            )
                        }
                        for (i in 6..8) {
                            val expansion = i * 4f
                            drawRoundRect(
                                color = Color(0xFF040404).copy(alpha = 0.08f - (i * 0.01f)),
                                topLeft = Offset(
                                    -expansion / 2f,
                                    shadowBaseY + (i * 2f)
                                ),
                                size = Size(
                                    dimensions.mainWidth.toPx() + expansion,
                                    dimensions.shadowHeight.toPx() + (i * 1.2f)
                                ),
                                cornerRadius = CornerRadius(
                                    dimensions.secondCornerRadius + i,
                                    dimensions.secondCornerRadius + i
                                )
                            )
                        }
                    }

                    // Second (middle) surface
                    drawSecond(colors, isFocused, isToggleable && effectiveToggled, dimensions, mainYOffset)


                    // Main top surface
                    drawRoundRect(
                        color = mainColor,
                        topLeft = Offset(
                            0f,
                            (dimensions.mainYOffset + mainYOffset).toPx()
                        ),
                        size = Size(
                            dimensions.mainWidth.toPx(),
                            dimensions.mainHeight.toPx()
                        ),
                        cornerRadius = CornerRadius(
                            dimensions.mainCornerRadius,
                            dimensions.mainCornerRadius
                        )
                    )
                    drawSplit(colors, isFocused, (isToggleable && effectiveToggled) || isPressed, dimensions, mainYOffset)
                }
        ) {
            val labelText =
                if (isToggleable && effectiveToggled && textConfig.toggledText != null)
                    textConfig.toggledText
                else
                    textConfig.text

            val isGlowActive = isFocusable && (isFocused || keepGlow)

            val labelColor = when {
                // focused + toggled
                isToggleable && effectiveToggled && isGlowActive -> colors.textToggledFocus

                // focused (not toggled)
                isGlowActive -> colors.textFocused

                // ✅ toggled but NOT focused
                isToggleable && effectiveToggled -> colors.textUnfocusedToggled

                // normal unfocused
                else -> colors.textUnfocused
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(
                        width = dimensions.mainWidth,
                        height = dimensions.mainHeight
                    )
                    .align(Alignment.TopCenter)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        val next = if (isFocusable) focusState.isFocused else false
                        // Compared BEFORE assigning: onFocusChanged also fires on
                        // recompositions carrying the same state, so a plain
                        // "if (isFocused)" would re-fire on each of them while the
                        // button simply sat there holding focus.
                        val gained = next && !isFocused
                        isFocused = next
                        Log.d(TAG, "FOCUS STATE (${textConfig.text}): $isFocused")
                        if (gained) onFocusGained()
                    }
                    .focusable(enabled = isFocusable, interactionSource = interactionSource)
                    .onKeyEvent { keyEvent ->
                        if (!isFocusable) return@onKeyEvent false

                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionCenter,
                                Key.Enter,
                                Key.NumPadEnter -> {
                                    Log.d(TAG, "CENTER PRESSED on ${textConfig.text}")
                                    handleButtonClick()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .clickable(
                        enabled = isFocusable,
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        handleButtonClick()
                    }
            ) {
                ModularButtonLabel(
                    textConfig, labelText, labelColor, fontFamily, textYOffset,
                    // The face's own press, from the label's resting offset.
                    stackedY = { animationConfig.textOffsetDefault + faceYOffset.value },
                )
            }
        }
    }
}

/*
 * ModularButton's pieces, each its own restartable composable (v524): Box is
 * inline, so everything drawn inside the button compiled into ModularButton's
 * own method -- 2,926 dex units and 97 registers, close to the 100 at which
 * R8 miscompiled the TV details screen (v371-v375). Every button on every
 * screen runs through it, so checkMethodSizes now holds it to the same budget
 * as the details screens. A child composable is a method of its own that R8
 * does not fold back in.
 */

/** The focus glow behind the button; [alpha] is read only in its layer. */
@Composable
private fun BoxScope.ModularButtonGlow(
    @DrawableRes res: Int,
    dimensions: ModularButtonDimensions,
    glowConfig: ModularButtonGlowConfig,
    alpha: State<Float>,
) {
    Image(
        painter = heldPainter(res),
        contentDescription = null,
        modifier = Modifier
            .size(
                width = dimensions.glowWidth,
                height = dimensions.glowHeight
            )
            .align(Alignment.TopStart)
            .offset(x = glowConfig.offsetX, y = glowConfig.offsetY)
            .graphicsLayer {
                clip = true
                shape = RoundedCornerShape(glowConfig.cornerRadius)
                this.alpha = alpha.value
            },
        contentScale = ContentScale.FillBounds
    )
}

/**
 * The button's label: an icon (with its blurred shadow) or text. [textYOffset]
 * is the press-in, read while placing (offsetWithY). A stacked label rides
 * [stackedY] instead -- see StackedLabel.
 */
@Composable
private fun ModularButtonLabel(
    textConfig: ModularButtonTextConfig,
    labelText: String,
    labelColor: Color,
    fontFamily: FontFamily,
    textYOffset: State<Dp>,
    stackedY: () -> Dp,
) {
    if (textConfig.subText != null) {
        StackedLabel(textConfig, labelText, labelColor, fontFamily, stackedY)
    } else if (textConfig.leadingIconRes != null) {
        IconTextLabel(textConfig, labelText, labelColor, fontFamily, textYOffset)
    } else if (textConfig.iconRes != null) {
        val iconShadow = textConfig.shadow
        if (iconShadow != null) {
            val density = LocalDensity.current
            val dx = with(density) { iconShadow.offset.x.toDp() }
            val dy = with(density) { iconShadow.offset.y.toDp() }
            val blur = with(density) { (iconShadow.blurRadius / 2f).toDp() }
            Image(
                painter = painterResource(id = textConfig.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(iconShadow.color),
                modifier = Modifier
                    .size(textConfig.iconSize)
                    .offsetWithY(textConfig.offsetX + dx) { textYOffset.value + dy }
                    // Blur needs API 31. Below it Modifier.blur draws nothing but
                    // still adds a layer (Unbounded: no clip either) under each
                    // shadowed icon on the Fire TV sticks -- so it is left off there (v516).
                    .then(if (blurDrawsOn(Build.VERSION.SDK_INT)) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier)
            )
        }
        Image(
            painter = painterResource(id = textConfig.iconRes),
            contentDescription = labelText,
            colorFilter = ColorFilter.tint(labelColor),
            modifier = Modifier
                .size(textConfig.iconSize)
                .offsetWithY(textConfig.offsetX) { textYOffset.value }
        )
    } else {
        Text(
            text = labelText,
            color = labelColor,
            fontSize = textConfig.fontSize.sp,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.let { base -> if (textConfig.shadow != null) base.copy(shadow = textConfig.shadow) else base },
            modifier = Modifier
                .offsetWithY(textConfig.offsetX) { textYOffset.value }
                .semantics { contentDescription = labelText }
        )
    }
}

/**
 * One line after a leading icon: Favorites' star (v623). The two ride the
 * label's own press offset together, centred as one group; the icon gets a
 * sharp shadow copy like StackedLabel's.
 */
@Composable
private fun IconTextLabel(
    textConfig: ModularButtonTextConfig,
    labelText: String,
    labelColor: Color,
    fontFamily: FontFamily,
    textYOffset: State<Dp>,
) {
    val shadow = textConfig.shadow
    val res = textConfig.leadingIconRes ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .offsetWithY(textConfig.offsetX) { textYOffset.value }
            .semantics { contentDescription = labelText },
    ) {
        Box {
            if (shadow != null) {
                val density = LocalDensity.current
                Image(
                    painter = painterResource(id = res),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(shadow.color),
                    modifier = Modifier
                        .size(textConfig.leadingIconSize)
                        .offset(
                            x = with(density) { shadow.offset.x.toDp() },
                            y = with(density) { shadow.offset.y.toDp() },
                        ),
                )
            }
            Image(
                painter = painterResource(id = res),
                contentDescription = null,
                colorFilter = ColorFilter.tint(labelColor),
                modifier = Modifier.size(textConfig.leadingIconSize),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = labelText,
            color = labelColor,
            fontSize = textConfig.fontSize.sp,
            fontFamily = fontFamily,
            maxLines = 1,
            style = LocalTextStyle.current.let { base -> if (shadow != null) base.copy(shadow = shadow) else base },
        )
    }
}

/**
 * Two lines beside a leading icon: "Play" over "from Website 1" (see
 * ModularButtonTextConfig.subText). The icon and the lines, as one group, sit
 * dead centre on the button both ways; the lines' leading is trimmed to the
 * glyphs so font padding cannot push the group off centre. The icon and the
 * first line take the focus colour; the second keeps its own.
 *
 * IT RIDES THE FACE'S OWN PRESS ANIMATION, not the label's: the shared label
 * press (WatchButtonAnimation, 1 -> 2.4 dp) travels 1.4 dp while the face
 * drops 2.5 dp, so the words lagged the face on every press (the user's
 * report, v598). One animation, one distance, the same frame.
 * The icon's shadow is a sharp copy, not a blur: a blur on every source
 * button would be four RenderEffect layers on the overlay.
 */
/**
 * Where the stacked label is nudged so the DRAWN group sits dead centre on the
 * face. Layout-centring is not enough: fonts leave uneven empty space above
 * and below their glyphs, and the glyphs' side bearings put the group 1 dp
 * right. Measured from onn box screencaps, glyph pixels against the face:
 * option B (v609, "Play from" over Passion One "Website N") drew 2.5 px high
 * at -0.5 dp -- 8 px of face above, 13 below -- hence +0.75 dp.
 * Re-measure if the fonts, sizes or line order change.
 */
private val STACKED_LABEL_LIFT = 0.75.dp
private val STACKED_LABEL_SHIFT_X = (-1).dp

@Composable
private fun StackedLabel(
    textConfig: ModularButtonTextConfig,
    labelText: String,
    labelColor: Color,
    fontFamily: FontFamily,
    /** The face's press offset plus the label's resting one, read while placing. */
    pressY: () -> Dp,
) {
    val shadow = textConfig.shadow
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .offsetWithY(STACKED_LABEL_SHIFT_X) { pressY() + STACKED_LABEL_LIFT }
            .semantics {
                contentDescription = if (textConfig.subAbove) "${textConfig.subText} $labelText"
                                     else "$labelText ${textConfig.subText}"
            },
    ) {
        textConfig.leadingIconRes?.let { res ->
            Box {
                if (shadow != null) {
                    val density = LocalDensity.current
                    Image(
                        painter = painterResource(id = res),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(shadow.color),
                        modifier = Modifier
                            .size(textConfig.leadingIconSize)
                            .offset(
                                x = with(density) { shadow.offset.x.toDp() },
                                y = with(density) { shadow.offset.y.toDp() },
                            ),
                    )
                }
                Image(
                    painter = painterResource(id = res),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(labelColor),
                    modifier = Modifier.size(textConfig.leadingIconSize),
                )
            }
            Spacer(Modifier.width(7.dp))
        }
        Column {
            val base = LocalTextStyle.current
                .copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))
                .let { if (shadow != null) it.copy(shadow = shadow) else it }
            // The small line in Inter Bold (SemiBold thinned out at this
            // size, v594), 1 dp from the main line on whichever side it sits.
            val sub: @Composable () -> Unit = {
                Text(
                    text = textConfig.subText.orEmpty(),
                    color = textConfig.subColor.takeIf { it.isSpecified } ?: labelColor,
                    fontSize = textConfig.subFontSize.sp,
                    lineHeight = (textConfig.subFontSize * 1.2f).sp,
                    fontFamily = AppTypography.interBold,
                    maxLines = 1,
                    style = base,
                    modifier = Modifier.padding(
                        top = if (textConfig.subAbove) 0.dp else 1.dp,
                        bottom = if (textConfig.subAbove) 1.dp else 0.dp,
                    ),
                )
            }
            if (textConfig.subAbove) sub()
            Text(
                text = labelText,
                color = textConfig.mainColor.takeIf { it.isSpecified } ?: labelColor,
                fontSize = textConfig.fontSize.sp,
                lineHeight = (textConfig.fontSize * 1.1f).sp,
                fontFamily = fontFamily,
                maxLines = 1,
                style = base,
            )
            if (!textConfig.subAbove) sub()
        }
    }
}

/**
 * The strip under the face. With a [ModularButtonColors.faceShadow] the face
 * casts a soft shadow onto it (the user's pick, v616, "option 3"): darkest
 * right under the face's bottom edge, gone [FACE_SHADOW_DEPTH] below, so the
 * face reads as resting on a second layer. It follows the face down on a
 * press. Outside ModularButton for the same method budget as drawSplit.
 */
private fun DrawScope.drawSecond(
    colors: ModularButtonColors,
    focused: Boolean,
    pressed: Boolean,
    dimensions: ModularButtonDimensions,
    pressY: Dp,
) {
    val base = when {
        pressed -> colors.secondToggled
        focused -> colors.secondFocused
        else -> colors.secondDefault
    }
    val top = dimensions.secondYOffset.toPx()
    val radius = CornerRadius(dimensions.secondCornerRadius, dimensions.secondCornerRadius)
    val size = Size(dimensions.secondWidth.toPx(), dimensions.secondHeight.toPx())
    drawRoundRect(color = base, topLeft = Offset(0f, top), size = size, cornerRadius = radius)
    if (!colors.faceShadow.isSpecified) return
    val faceBottom = (dimensions.mainYOffset + pressY + dimensions.mainHeight).toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(colors.faceShadow, Color.Transparent),
            startY = faceBottom,
            endY = faceBottom + FACE_SHADOW_DEPTH.toPx(),
        ),
        topLeft = Offset(0f, top),
        size = size,
        cornerRadius = radius,
    )
}

/** How far below the face its shadow on the strip fades out (6 px in the mock-up, ~4 dp on the TV). */
private val FACE_SHADOW_DEPTH = 4.dp

/** How much of the face, from the bottom, the split's band covers (as in the mock-up). */
private const val BAND_FRACTION = 0.43f

/**
 * The split face's band and edge, drawn over the face: the band across its
 * lower [BAND_FRACTION], clipped to the rounded corners, then a 1 dp edge
 * inset half a stroke. Both move with the press like the face. Nothing for a
 * palette without them.
 *
 * THE COLOUR CHOICE LIVES HERE, not in ModularButton: that method is held to
 * 2,500 units / 100 registers (checkMethodSizes, the R8 register bug of
 * v371-v375), and choosing these inline took it to 2,490 / 96.
 */
private fun DrawScope.drawSplit(
    colors: ModularButtonColors,
    focused: Boolean,
    pressed: Boolean,
    dimensions: ModularButtonDimensions,
    pressY: Dp,
) {
    val band = when {
        pressed -> colors.bandToggled
        focused -> colors.bandFocused
        else -> colors.band
    }
    val outline = if (focused && !pressed) colors.outlineFocused else colors.outline
    if (!band.isSpecified && !outline.isSpecified) return
    val top = (dimensions.mainYOffset + pressY).toPx()
    val w = dimensions.mainWidth.toPx()
    val h = dimensions.mainHeight.toPx()
    val radius = CornerRadius(dimensions.mainCornerRadius, dimensions.mainCornerRadius)
    if (band.isSpecified) {
        val face = Path().apply { addRoundRect(RoundRect(0f, top, w, top + h, radius)) }
        clipPath(face) {
            drawRect(color = band, topLeft = Offset(0f, top + h * (1f - BAND_FRACTION)), size = Size(w, h * BAND_FRACTION))
        }
    }
    if (outline.isSpecified) {
        val stroke = 1.dp.toPx()
        drawRoundRect(
            color = outline,
            topLeft = Offset(stroke / 2, top + stroke / 2),
            size = Size(w - stroke, h - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
    }
}

/** Modifier.blur draws from API 31 (RenderEffect); below it, it only adds an empty layer. Pure, so it is tested. */
internal fun blurDrawsOn(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S
