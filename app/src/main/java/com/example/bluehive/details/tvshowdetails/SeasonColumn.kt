package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.focus.focusRequester
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R

@Composable
fun SeasonButton(
    label: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = label,
            fontSize = 16f
        ),
        isToggleable = true,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = modifier,
        dimensions = ModularButtonDimensions(
            mainWidth = 117.5.dp,
            mainHeight = 30.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 117.5.dp,
            secondHeight = 22.dp,
            secondYOffset = 13.5.dp,
            shadowHeight = 12.dp,
            shadowOffset = (-3).dp,
            glowWidth = 135.6.dp,
            glowHeight = 51.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared — see SeasonButtonColors in utilities/ModularButton.kt. It is
        // derived from StreamSourceButtonColors, so these chips and the watch
        // buttons brighten identically on focus.
        colors = SeasonButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-9.25).dp,  // lesser the number the farther it moves left
            offsetY = (-2.5).dp,
            cornerRadius = 10.dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = 110,
            bounceBackDelayMillis = 80
        ),
        externalToggled = isSelected,
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}
