package com.example.bluehive.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The red tab on a new title's poster: a red tab hanging from the poster's top
 * edge with the label on the red and the detail in a full-height white block on
 * the right -- "NEW RELEASE | HD", "NEW SEASON | S3", "NEW EPISODE | S2 E5".
 *
 * The owner's pick 2026-11-21 (docs/mockups/new-content-bars, tab-taller "5,
 * split block" + tab-split-wider "3"): 1.5x the first tab's height so the words
 * and numbers read from the couch. NEW EPISODE runs the full poster width (its
 * label and "S23 E6" were squeezed at 84%); the others are 84% wide.
 *
 * Sizes are the mock-up's, in dp: tab 18 tall, label 8.6, number 10, bottom
 * corners 6.
 */
@Composable
fun NewContentTab(label: String, detail: String?, modifier: Modifier = Modifier) {
    val font = AppTypography.passionRegular
    val width = if (label == "NEW EPISODE") 1f else 0.84f
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth(width)
                .height(18.dp)
                .shadow(3.dp, TabShape, clip = false)
                .clip(TabShape)
                .background(NewRed),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(horizontal = 2.5.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = TextStyle(color = Color.White, fontSize = 8.6.sp, fontFamily = font, letterSpacing = 0.15.sp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (!detail.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxHeight().background(Color.White).padding(horizontal = 3.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = detail,
                        style = TextStyle(color = NewRed, fontSize = 10.sp, fontFamily = font),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private val NewRed = Color(0xFFD9232A)
private val TabShape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
