package com.example.bluehive.update

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Path
import com.example.bluehive.R
import com.example.bluehive.auth.AuthActivity
import kotlinx.coroutines.android.awaitFrame

/**
 * BlueHive's first-open setup (v656; layout H2 since v658, see the layout
 * section below): the step in a centred bee column, a picture of the TV's own
 * settings list beside it, and a red arrow at BlueHive's row.
 *
 *   step 1  REQUIRED · ONE TIME   "Install unknown apps", one button, no skip
 *   step 2  STEP 2 · OPTIONAL      "Display over other apps", Turn it on / Not now
 *           (JUST UPDATED · OPTIONAL when an older BlueHive was updated to this one)
 *
 * AuthActivity routes here before anything else when SetupSteps.anyDue(). Each
 * time the viewer comes back from Settings the switches are re-read, so turning a
 * switch on moves straight to the next step. Done (or Not now) re-enters
 * AuthActivity with EXTRA_SETUP_DONE so the gate cannot loop.
 */
class SetupStepsActivity : ComponentActivity() {

    /** 1 or 2; 0 = nothing left, continue into the app. */
    private var step by mutableIntStateOf(0)
    private var stepOneShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = nextStep()
        if (step == 0) { proceed(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Step 1 is required: Back leaves BlueHive rather than skipping it.
                // Step 2 is optional: Back is Not now.
                if (step == 1) finishAffinity() else proceed()
            }
        })

        setContent {
            val s = step
            if (s == 1 || s == 2) {
                key(s) {
                    if (s == 1) stepOneShown = true
                    if (s == 2) LaunchedEffect(Unit) { SetupSteps.markReopenAsked(this@SetupStepsActivity) }
                    SetupStepScreen(
                        step = s,
                        afterUpdate = s == 2 && !stepOneShown && !SetupSteps.freshInstall(this@SetupStepsActivity),
                        onTurnOn = { openSettings(s) },
                        onNotNow = if (s == 2) ({ proceed() }) else null,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Settings: re-read the switches and move on if one changed.
        if (step == 1 && SetupSteps.canInstallApps(this)) {
            step = if (SetupSteps.reopenStepDue(this)) 2 else 0
        } else if (step == 2 && SetupSteps.canReopenAfterUpdates(this)) {
            step = 0
        }
        if (step == 0 && !isFinishing) proceed()
    }

    private fun nextStep(): Int = when {
        SetupSteps.installStepDue(this) -> 1
        SetupSteps.reopenStepDue(this) -> 2
        else -> 0
    }

    private fun openSettings(s: Int) {
        val intent = if (s == 1) SetupSteps.installSettingsIntent(this) else SetupSteps.overlaySettingsIntent(this)
        runCatching { startActivity(intent) }.onFailure {
            // The link resolved a moment ago, so this should not happen. If it does,
            // never trap the viewer: the update screen still asks when it must.
            Log.w(TAG, "could not open settings for step $s: ${it.message}")
            if (s == 1 && SetupSteps.reopenStepDue(this)) step = 2 else proceed()
        }
    }

    private fun proceed() {
        if (isFinishing) return
        startActivity(
            Intent(this, AuthActivity::class.java)
                .putExtra(AuthActivity.EXTRA_SETUP_DONE, true)
                .putExtras(intent ?: Intent())
        )
        finish()
    }

    private companion object {
        const val TAG = "SetupSteps"
    }
}

// ── Layout H2 (v658) ─────────────────────────────────────────────────────────
// The approved hybrid (docs/mockups/bluehive-setup/bh-setup-h2arrow-1/2.png):
// layout B's centred bee column on the left, layout A's picture of the TV's next
// screen on the right, and a red arrow pointing at BlueHive's row. No card.

private val Bg = Color(0xFF0E0E10)
private val Accent = Color(0xFF378ADD)
private val AccentSoft = Color(0xFF85B7EB)
private val AccentInk = Color(0xFF06121F)
private val TextHi = Color(0xFFF5F5F2)
private val TextBody = Color(0xFFA9A9A3)

@Composable
private fun SetupStepScreen(
    step: Int,
    afterUpdate: Boolean,
    onTurnOn: () -> Unit,
    onNotNow: (() -> Unit)?,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                // The faint blue glow, left of centre as in the mockup.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.30f, size.height * 0.45f),
                        radius = size.width * 0.45f,
                    )
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // -50 dp: shifted left to leave room for the arrow beside the preview.
        Row(
            Modifier.width(840.dp).offset(x = (-50).dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BeeRing(required = step == 1)
                Spacer(Modifier.height(18.dp))
                Label(
                    text = when {
                        step == 1 -> "REQUIRED · ONE TIME"
                        afterUpdate -> "JUST UPDATED · OPTIONAL"
                        else -> "STEP 2 · OPTIONAL"
                    },
                    required = step == 1,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (step == 1) "Let BlueHive install its own updates" else "Let BlueHive open again after updates",
                    color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (step == 1)
                        "BlueHive keeps itself up to date. Your TV needs your OK once before BlueHive can install an update."
                    else
                        "Your TV closes BlueHive whenever it installs an update. Switch this on and BlueHive opens again by itself.",
                    color = TextBody, fontSize = 15.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 440.dp),
                )
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Turn it on", primary = true, takeFocus = true, onClick = onTurnOn)
                    if (onNotNow != null) PillButton("Not now", primary = false, takeFocus = false, onClick = onNotNow)
                }
                if (!afterUpdate) {
                    Spacer(Modifier.height(22.dp))
                    StepDots(step)
                }
            }
            Spacer(Modifier.width(28.dp))
            // The faint divider, fading out at both ends.
            Box(
                Modifier.width(1.dp).fillMaxHeight().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent, 0.2f to Color(0xFF2A2B31),
                        0.8f to Color(0xFF2A2B31), 1f to Color.Transparent,
                    )
                )
            )
            Spacer(Modifier.width(56.dp))
            NextScreen(step)
        }
    }
}

/** Layout B's bee in a ring: blue for the required step, faint for the optional one. */
@Composable
private fun BeeRing(required: Boolean) {
    Box(
        Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.18f), Color.Transparent)))
            .border(4.dp, if (required) Accent else Color(0x14FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // blue_bee.png carries ~20% transparent margin; 98 dp shows the bee itself
        // at the mockup's 66 dp.
        Image(
            painterResource(R.drawable.blue_bee), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.size(98.dp),
        )
    }
}

@Composable
private fun Label(text: String, required: Boolean) {
    Text(
        text,
        color = if (required) AccentInk else Color(0xFFC9CBD1),
        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (required) Accent else Color(0xFF2A2C33))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Pill buttons from the mockup. OK and Enter press them; focus is a white ring,
 * since a TV has no pointer. The primary one takes focus as soon as it is drawn,
 * or pressing OK on a fresh screen would do nothing.
 */
@Composable
private fun PillButton(label: String, primary: Boolean, takeFocus: Boolean, onClick: () -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (takeFocus) {
        LaunchedEffect(Unit) { awaitFrame(); runCatching { focus.requestFocus() } }
    }
    val shape = RoundedCornerShape(28.dp)
    // The ring sits in a 3 dp margin that is always there, so focus never resizes
    // the button or moves its neighbour.
    Box(
        Modifier
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                // On key UP: pressing on key down opens Settings while the key is
                // still held, and the release could land on the Settings screen.
                if (e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) { onClick(); true } else false
            }
            .border(3.dp, if (focused) TextHi else Color.Transparent, RoundedCornerShape(31.dp))
            .padding(3.dp)
    ) {
        Row(
            Modifier
                .height(50.dp)
                .clip(shape)
                .background(
                    when {
                        primary -> Accent
                        focused -> Color(0xFF2A2C33)
                        else -> Color.Transparent
                    }
                )
                .border(1.5.dp, if (!primary && !focused) Color(0xFF3A3B42) else Color.Transparent, shape)
                .padding(start = 26.dp, end = if (primary) 6.dp else 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                label,
                color = when {
                    primary -> AccentInk
                    focused -> TextHi
                    else -> Color(0xFFB9B9B4)
                },
                fontSize = 17.sp, fontWeight = FontWeight.Medium,
            )
            if (primary) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(AccentInk),
                    contentAlignment = Alignment.Center,
                ) { Arrow() }
            }
        }
    }
}

@Composable
private fun Arrow() {
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 18f
        val p = Path().apply {
            moveTo(3f * u, 9f * u); lineTo(14f * u, 9f * u)
            moveTo(9.5f * u, 4.5f * u); lineTo(14f * u, 9f * u); lineTo(9.5f * u, 13.5f * u)
        }
        drawPath(p, Accent, style = Stroke(width = 2.2f * u, cap = StrokeCap.Round))
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..2) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == step) AccentSoft else Color(0xFF34353B)))
        }
        Spacer(Modifier.width(4.dp))
        Text("Step $step of 2", color = Color(0xFF6E6E68), fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
}

// ── The next screen, drawn in the TV's own Settings style ───────────────────

private class FakeRow(val name: String, val allowed: Boolean, val icon: Color?)

@Composable
private fun NextScreen(step: Int) {
    val title = if (step == 1) "Install unknown apps" else "Allow display over other apps"
    // An app above BlueHive, as on real TVs: the list starts on its FIRST app, so
    // the viewer has to move to BlueHive (Aniyomi / Android TV Remote Service on the
    // old onn box, 2026-11-05). A null icon marks BlueHive.
    val rows = if (step == 1) listOf(
        FakeRow("Aniyomi", false, Color(0xFF2F6FD0)),
        FakeRow("BlueHive", false, null),
        FakeRow("Captive Portal Login", false, Color(0xFF0F8B7D)),
        FakeRow("Downloader", true, Color(0xFFF07D1A)),
    ) else listOf(
        FakeRow("Android TV Remote Service", false, Color(0xFFFFFFFF)),
        FakeRow("BlueHive", false, null),
        FakeRow("Google Play services", false, Color(0xFFFFFFFF)),
        FakeRow("Off-Grid Drive", true, Color(0xFF2A1A12)),
    )
    val card = RoundedCornerShape(10.dp)
    Column(Modifier.width(290.dp)) {
        Text("NEXT SCREEN", color = Color(0xFF888780), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(9.dp))
        // NOT CLIPPED: the arrow sits outside the card, beside BlueHive's row.
        Column(
            Modifier
                .shadow(18.dp, card, clip = false)
                .background(Color(0xFF1F222A), card)
                .border(1.dp, Color(0xFF2E3138), card)
                .padding(start = 22.dp, top = 16.dp, bottom = 10.dp, end = 0.dp),
        ) {
            Text(title, color = Color(0xFFE3E3E6), fontSize = 19.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(12.dp))
            rows.forEach { r ->
                val bh = r.icon == null
                Box {
                    SettingsRow(r.name, r.allowed, focused = bh, icon = r.icon)
                    if (bh) PointerArrow(Modifier.align(Alignment.CenterEnd).offset(x = 92.dp))
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            buildAnnotatedString {
                append("Highlight ")
                withStyle(SpanStyle(color = AccentSoft, fontWeight = FontWeight.Medium)) { append("BlueHive") }
                append(", press the middle button, then back.")
            },
            color = Color(0xFFD6D6D1), fontSize = 13.sp, lineHeight = 19.sp,
        )
    }
}

/**
 * The red arrow pointing left at BlueHive's row: pure red, white edge, soft white
 * glow. The glow is drawn as widening, fading white strokes rather than a blur, so
 * it looks the same on every Android version.
 */
@Composable
private fun PointerArrow(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 84.dp, height = 46.dp)) {
        val u = size.width / 84f
        val p = Path().apply {
            moveTo(2f * u, 23f * u); lineTo(30f * u, 2f * u); lineTo(30f * u, 15f * u)
            lineTo(82f * u, 15f * u); lineTo(82f * u, 31f * u); lineTo(30f * u, 31f * u)
            lineTo(30f * u, 44f * u); close()
        }
        for ((w, a) in listOf(16f to 0.10f, 11f to 0.18f, 7f to 0.35f)) {
            drawPath(p, Color.White.copy(alpha = a), style = Stroke(width = w * u, join = StrokeJoin.Round))
        }
        drawPath(p, Color(0xFFFF1A0F))
        drawPath(p, Color.White, style = Stroke(width = 2.5f * u, join = StrokeJoin.Round))
    }
}

@Composable
private fun SettingsRow(name: String, allowed: Boolean, focused: Boolean, icon: Color?) {
    // The TV's own highlight: a light rounded row, no frame (the arrow points).
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .then(
                if (focused) Modifier
                    .offset(x = (-10).dp)
                    .width(250.dp)
                    .clip(shape)
                    .background(Color(0xFFE8E9EE))
                    .padding(start = 10.dp)
                else Modifier.width(240.dp)
            )
            .padding(top = 7.dp, bottom = 7.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == null) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2E9BE8))) {
                Image(
                    painterResource(R.drawable.blue_bee), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
                )
            }
        } else {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(icon))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (focused) Color(0xFF1F222A) else Color(0xFFE3E3E6), fontSize = 12.sp, lineHeight = 14.sp,
                maxLines = 1)
            Text(
                if (allowed) "Allowed" else "Not allowed",
                color = if (focused) Color(0xFF5F6368) else Color(0xFF9AA0A6), fontSize = 9.5.sp, lineHeight = 12.sp,
            )
        }
        Toggle(allowed)
    }
}

@Composable
private fun Toggle(on: Boolean) {
    Box(
        Modifier.size(width = 28.dp, height = 16.dp).clip(RoundedCornerShape(8.dp))
            .background(if (on) Color(0xFFA8C7FA) else Color(0xFF8E8E8E)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(horizontal = 3.dp).size(11.dp).clip(CircleShape)
                .background(if (on) Color(0xFF0B3A75) else Color.White)
        )
    }
}
