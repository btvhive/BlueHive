package com.example.bluehive.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.utilities.SoundPrefs

/**
 * Settings > Sounds: interface sounds for the profile in use.
 *
 * One master switch, a volume in 10% steps, and one switch per kind of sound
 * (navigation ticks, clicks and back-outs, the error sound). Everything goes
 * through SoundPrefs, which applies it at once and saves it to the profile on
 * the server, so it follows the profile to other TVs and survives delete +
 * recover. Media playback is not affected -- it never plays through the
 * interface sound pool.
 *
 * Same row style and the same key rules as the Settings list (SettingsEntry):
 * ticks come from the key press, not from focus, and the ends of the list are
 * silent.
 */
class SoundsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SoundsScreen() }
    }

    /** Send any change still waiting out its save delay. */
    override fun onStop() {
        SoundPrefs.flush(this)
        super.onStop()
    }

    /** Leaving is a back-out, as on Settings. */
    override fun finish() {
        BlueHiveApplication.playBackOutSound()
        super.finish()
    }
}

private val PageBg    = Color(0xFF0A0A0A)
private val PanelBg   = Color(0xFF141418)
private val FocusBg   = Color(0xFF1D2540)
private val Hairline  = Color(0xFF26262C)
private val TextHi    = Color.White
private val TextLo    = Color(0xFF9A9AA3)
private val On        = Color(0xFF2644A6)
private val OffKnob   = Color(0xFF868686)

private const val STEP = 10

/** Text for the Settings row: "On, 70%" or "Off". */
internal fun soundsSummary(s: SoundPrefs.Settings): String =
    if (s.muted) "Off" else "On, ${s.volume}%"

@Composable
private fun SoundsScreen() {
    val context = LocalContext.current
    var s by remember { mutableStateOf(SoundPrefs.current) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    fun save(next: SoundPrefs.Settings) {
        s = next
        SoundPrefs.update(context, next)
    }

    val name = SoundPrefs.currentProfileName
    val dim = if (s.muted) 0.45f else 1f

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp)) {
            Text("Sounds", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                (if (name.isNotBlank()) "For $name. " else "") +
                    "Movies, shows and trailers aren't affected.",
                color = TextLo, fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))

            Column(Modifier.widthIn(max = 640.dp)) {
                SwitchRow(
                    title = "Interface sounds",
                    checked = !s.muted,
                    focusRequester = first,
                    hasAbove = false,
                    onToggle = {
                        save(s.copy(muted = !s.muted))
                        // After the change, so turning sounds ON is heard.
                        BlueHiveApplication.playClickSound()
                    },
                )
                VolumeRow(
                    percent = s.volume,
                    enabled = !s.muted,
                    modifier = Modifier.alpha(dim),
                    onChange = { p ->
                        save(s.copy(volume = p))
                        BlueHiveApplication.playVolumePreview(p)
                    },
                )
                SwitchRow(
                    title = "Navigation ticks",
                    checked = s.nav,
                    modifier = Modifier.alpha(dim),
                    onToggle = { save(s.copy(nav = !s.nav)); BlueHiveApplication.playClickSound() },
                )
                SwitchRow(
                    title = "Clicks and back",
                    checked = s.click,
                    modifier = Modifier.alpha(dim),
                    onToggle = { save(s.copy(click = !s.click)); BlueHiveApplication.playClickSound() },
                )
                SwitchRow(
                    title = "Error sound",
                    checked = s.error,
                    hasBelow = false,
                    modifier = Modifier.alpha(dim),
                    onToggle = { save(s.copy(error = !s.error)); BlueHiveApplication.playClickSound() },
                )
            }
        }
    }
}

/** The shared row shell: focus look, up/down ticks, silent at the ends. */
@Composable
private fun SoundsRow(
    focusRequester: FocusRequester?,
    hasAbove: Boolean,
    hasBelow: Boolean,
    modifier: Modifier,
    onLeftRight: ((Int) -> Unit)?,
    onClick: (() -> Unit)?,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) FocusBg else PanelBg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Hairline,
                shape = RoundedCornerShape(10.dp),
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown ->
                        if (hasBelow) { BlueHiveApplication.playHoverSound(); false } else true
                    Key.DirectionUp ->
                        if (hasAbove) { BlueHiveApplication.playHoverSound(); false } else true
                    Key.DirectionLeft -> { onLeftRight?.invoke(-1); true }
                    Key.DirectionRight -> { onLeftRight?.invoke(1); true }
                    else -> false
                }
            }
            // ONE focus node, as on Settings: clickable is the focus target and
            // turns DPAD_CENTER/ENTER into the press.
            .clickable(interactionSource = interaction, indication = null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) { content(focused) }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null,
    hasAbove: Boolean = true,
    hasBelow: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SoundsRow(focusRequester, hasAbove, hasBelow, modifier, onLeftRight = null, onClick = onToggle) {
        Text(title, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .size(width = 40.dp, height = 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) On else Hairline),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else OffKnob)
            )
        }
    }
}

@Composable
private fun VolumeRow(
    percent: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SoundsRow(
        focusRequester = null, hasAbove = true, hasBelow = true, modifier = modifier,
        onLeftRight = { dir ->
            if (!enabled) return@SoundsRow
            val next = (percent + dir * STEP).coerceIn(0, 100)
            if (next != percent) onChange(next)
        },
        onClick = null,
    ) { focused ->
        Text("Volume", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(96.dp))
        Text("‹", color = if (focused && enabled) TextHi else TextLo, fontSize = 20.sp)
        BoxWithConstraints(
            Modifier.weight(1f).padding(horizontal = 12.dp).height(16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = maxWidth
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hairline))
            Box(Modifier.width(track * (percent / 100f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(On))
            Box(
                Modifier
                    .offset(x = (track - 16.dp) * (percent / 100f))
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Text("›", color = if (focused && enabled) TextHi else TextLo, fontSize = 20.sp)
        Text("$percent%", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp).width(48.dp))
    }
}
