package com.example.bluehive.details.shared.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.CacheMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The image-cache stats both details screens log: once 1.5 s after the page
 * opens, and again when the recommended row arrives. Each walk runs on IO --
 * gatherStats() does three full directory walks, and running it inline on
 * entry was a main-thread stall.
 *
 * Both screens had this word for word until 2026-10-27; it is its own
 * (restartable) composable so it stays out of the screens' method budgets.
 */
@Composable
fun DetailsCacheStats(screenName: String, recommendations: List<MediaItem>) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(1500)
        withContext(Dispatchers.IO) {
            CacheMonitor.logCacheStats(context = context, screenName = screenName, imageCount = 0)
        }
    }
    LaunchedEffect(recommendations) {
        if (recommendations.isNotEmpty()) {
            // 1 backdrop + 1 logo + the recommendations + 6 provider buttons
            val totalImages = 1 + 1 + recommendations.size + 6
            withContext(Dispatchers.IO) {
                CacheMonitor.logCacheStats(context = context, screenName = screenName, imageCount = totalImages)
            }
        }
    }
}
