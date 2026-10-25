// settings/SettingsActivity.kt
package com.example.bluehive.settings

import com.example.bluehive.utilities.SoundPrefs
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.example.bluehive.BlueHiveApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BuildConfig
import com.example.bluehive.diagnostics.DiagnosticsActivity

/**
 * Settings — opened from the sidebar's Settings item (previously a TODO stub).
 * A vertical list of entries; Diagnostics is the first. New settings grow as
 * SettingsEntry rows here without touching the sidebar again.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back is NOT intercepted anywhere on this screen — the system default
        // (finish → back to Home, focus on the sidebar) is the correct behavior.
        setContent { SettingsScreen() }
    }

    /**
     * Leaving this screen, however it happens, is a back-out.
     *
     * Hooked on finish() rather than on a BackHandler ON PURPOSE. An
     * interceptor layer here was part of the eaten-first-press bug (see the
     * note in SettingsScreen), and finish() catches every exit -- the system
     * Back today, anything that calls it later -- without standing in the key's
     * way at all.
     */
    override fun finish() {
        BlueHiveApplication.playBackOutSound()
        super.finish()
    }
}

private val PageBg   = Color(0xFF0A0A0A)
private val PanelBg  = Color(0xFF141418)
private val Hairline = Color(0xFF26262C)
private val TextHi   = Color.White
private val TextLo   = Color(0xFF9A9AA3)

@Composable
private fun SettingsScreen() {
    // NO BackHandler here — the default activity Back (finish) is exactly what
    // we want, and an interceptor layer was part of the eaten-first-press bug.
    val context = LocalContext.current

    val firstEntryFocus = remember { FocusRequester() }
    val soundsEntryFocus = remember { FocusRequester() }
    // Re-read on every return, so the row shows what the Sounds page just saved.
    var soundsLine by remember { mutableStateOf(soundsSummary(SoundPrefs.current)) }

    // Initial focus once the tree is composed…
    LaunchedEffect(Unit) { firstEntryFocus.requestFocus() }
    // …and RE-focus every time we come back from Diagnostics, so focus never
    // sits in limbo where the first Back press gets consumed resolving it.
    // (runCatching: on the very first ON_RESUME the node isn't attached yet —
    // the LaunchedEffect above owns that case.)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                runCatching { firstEntryFocus.requestFocus() }
                soundsLine = soundsSummary(SoundPrefs.current)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp)
        ) {
            Text("Settings", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))

            Column(Modifier.widthIn(max = 640.dp)) {
                SettingsEntry(
                    title = "Diagnostics",
                    subtitle = "App version, server connectivity, storage — and send logs to support",
                    focusRequester = firstEntryFocus,
                    hasAbove = false,
                    hasBelow = true,
                    onClick = {
                        context.startActivity(Intent(context, DiagnosticsActivity::class.java))
                    },
                )

                Spacer(Modifier.height(12.dp))

                val forName = SoundPrefs.currentProfileName.let { if (it.isNotBlank()) " for $it" else "" }
                SettingsEntry(
                    title = "Sounds",
                    subtitle = "Menu and navigation sounds$forName — $soundsLine",
                    focusRequester = soundsEntryFocus,
                    hasAbove = true,
                    hasBelow = false,
                    onClick = {
                        context.startActivity(Intent(context, SoundsActivity::class.java))
                    },
                )
            }
        }

        // Version footer — bottom-left, out of the way.
        Text(
            "BlueHive v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            color = TextLo,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(48.dp),
        )
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    focusRequester: FocusRequester,
    /** Is there a row to move UP to? Ends of the list stay silent. */
    hasAbove: Boolean,
    /** Is there a row to move DOWN to? */
    hasBelow: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFF1D2540) else PanelBg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Hairline,
                shape = RoundedCornerShape(10.dp),
            )
            .focusRequester(focusRequester)
            // THE TICK COMES FROM THE KEY, NOT FROM FOCUS.
            //
            // onFocusChanged would also fire for the screen's opening focus and
            // for the ON_RESUME re-focus above, neither of which is a move the
            // viewer made -- the exact noise muteHoverSounds was added to stop
            // after returning from the player. Reading the press means the
            // sound can only follow a real one.
            //
            // And only when there is somewhere to go: at the ends of the list
            // the key is consumed silently, so the screen does not tick at a
            // wall. DPAD_CENTER/ENTER fall through (false) to clickable below,
            // which is what actually performs the press.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown ->
                        if (hasBelow) { BlueHiveApplication.playHoverSound(); false } else true
                    Key.DirectionUp ->
                        if (hasAbove) { BlueHiveApplication.playHoverSound(); false } else true
                    // Nothing either side of a full-width row.
                    Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            }
            // clickable is ITSELF a focus target and handles DPAD_CENTER/ENTER
            // as clicks. Adding .focusable() alongside it created TWO stacked
            // focus nodes — D-pad focus landed on one, click handling lived on
            // the other, and the OK button needed multiple presses to connect.
            // ONE node = one press.
            .clickable(interactionSource = interaction, indication = null) {
                BlueHiveApplication.playClickSound()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextLo, fontSize = 13.sp)
        }
        Text("›", color = if (focused) TextHi else TextLo, fontSize = 24.sp)
    }
}
