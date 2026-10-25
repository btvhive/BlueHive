package com.example.bluehive.details.shared.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * The notice both details screens show at the bottom centre: no dub, server
 * unreachable, not available yet, not aired yet.
 *
 * Every notice was written out by hand in each screen. They differ only in the
 * words and in two settings, kept exactly as they were:
 *   - [centered]: the longer notices centre their lines;
 *   - [maxWidth]: some of those also wrap at 720 dp, others do not.
 * The modifier chain is the screens' chain, in the same order: Modifier.then
 * of an empty Modifier is the same chain, not a different one.
 */
@Composable
fun BoxScope.DetailsToast(
    text: String,
    dongleRegular: FontFamily,
    centered: Boolean = false,
    maxWidth: Dp = Dp.Unspecified,
) {
    Text(
        text,
        color = Color.White,
        fontSize = 18.sp,
        fontFamily = dongleRegular,
        // null is Text's own default, which is what the uncentered notices used.
        textAlign = if (centered) TextAlign.Center else null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 70.dp)
            .then(if (maxWidth.isSpecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .background(Color(0xE6000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 26.dp, vertical = 12.dp)
    )
}
