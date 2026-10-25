package com.example.bluehive.details.shared.layout

import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bluehive.utilities.AppTypography

/**
 * The title's logo at the top left of a details screen -- or, when there is
 * no logo, the title itself, wrapped to fit the same space.
 *
 * Moved here word for word from both screens in Stage 2 of the details
 * redesign. A BoxScope extension: it positions itself in the screen's Box.
 */
@Composable
fun BoxScope.DetailsLogoOrTitle(
    logoUrl: String?,
    mediaTitle: String,
) {
    // ========================================================================================================================================= > logo parsing - it's already a full URL
    val fullLogoUrl = remember(logoUrl) {
        when {
            logoUrl.isNullOrBlank() -> {
                Log.d("LogoDebug", "❌ Logo URL is null or blank")
                null
            }
            logoUrl == "N/A" -> {
                Log.d("LogoDebug", "❌ Logo URL is N/A")
                null
            }
            else -> {
                Log.d("LogoDebug", "✅ Logo URL: $logoUrl")
                logoUrl // It's already a complete URL!
            }
        }
    }

    if (fullLogoUrl != null) {
        Log.d("LogoDebug", "🖼️ Rendering logo: $fullLogoUrl")
        AsyncImage(
            model = fullLogoUrl,
            contentDescription = "Logo",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 25.dp, start = 40.dp)
                .size(width = 180.dp, height = 100.dp),
            contentScale = ContentScale.Fit,
            onLoading = {
                Log.d("LogoDebug", "⏳ Logo loading...")
            },
            onSuccess = {
                Log.d("LogoDebug", "✅ Logo loaded successfully!")
            },
            onError = { error ->
                Log.e("LogoDebug", "❌ Logo failed to load: ${error.result.throwable.message}")
            }
        )
    } else {
        Log.d("LogoDebug", "❌ No logo to display — rendering title instead")
        val titleFontSize = remember(mediaTitle) {
            when {
                mediaTitle.length <= 20 -> 41.sp
                mediaTitle.length <= 23 -> 35.sp
                mediaTitle.length <= 33 -> 32.sp
                mediaTitle.length <= 40 -> 30.sp
                else -> 25.sp
            }
        }
        val wrappedTitle = remember(mediaTitle) {
            val words = mediaTitle.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (candidate.length <= 10) {
                    currentLine = candidate
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            lines.joinToString("\n")
        }

        val lineCount = remember(wrappedTitle) { wrappedTitle.count { it == '\n' } + 1 }
        val titleTopPadding = remember(lineCount) {
            when (lineCount) {
                1 -> 40.dp
                2 -> 25.dp
                else -> 8.dp
            }
        }

        Text(
            text = wrappedTitle,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = titleFontSize,
            lineHeight = titleFontSize * 0.87f,
            fontFamily = AppTypography.lalezarRegular,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = titleTopPadding, start = 30.dp)
                .width(200.dp),
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Clip
        )
    }
    // ========================================================================================================================================= >
}
