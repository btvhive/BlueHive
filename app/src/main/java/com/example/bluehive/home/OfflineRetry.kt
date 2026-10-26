package com.example.bluehive.home

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.bluehive.utilities.NetworkMonitor
import com.example.bluehive.utilities.RetryOverlay
import kotlinx.coroutines.launch

/**
 * The offline banner: the reload, the first check (effect 12 of 13), the
 * network callback (effect 13) and the RetryOverlay.
 *
 * Moved here from HomeScreen in Stage H3 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces.
 */
@Composable
internal fun BoxScope.OfflineRetry(
    entry: HomeEntryTransition,
    focusMap: HomeFocusMap,
    context: Context,
) {
    // Item 6 — offline retry banner state.
    var showRetryBanner by remember { mutableStateOf(false) }
    val retryScope = rememberCoroutineScope()

    // ── Item 6: offline / retry banner ──────────────────────────────────────
    // Toggling startContent off→on remounts every content child, which
    // re-runs their fetch effects. That's our "reload" with zero child
    // changes. The connectivity check is the failure signal because the
    // repositories swallow errors and return empty lists.
    val reloadContent: () -> Unit = {
        retryScope.launch {
            entry.startContent = false
            kotlinx.coroutines.android.awaitFrame()
            kotlinx.coroutines.android.awaitFrame()
            entry.startContent = true
            kotlinx.coroutines.android.awaitFrame()
            focusMap.trailerFrameFocusRequester.requestFocus()
        }
        Unit
    }

    // Initial check: if we mounted offline, surface the banner immediately.
    LaunchedEffect(Unit) {
        if (!NetworkMonitor.isOnline(context)) showRetryBanner = true
    }

    // Auto-recovery: when the network returns on its own, hide the banner
    // and reload. The callback fires on a binder thread, so hop to the
    // composition's main-dispatcher scope before touching state.
    DisposableEffect(Unit) {
        val cb = NetworkMonitor.registerOnlineCallback(context) {
            retryScope.launch {
                if (showRetryBanner) {
                    showRetryBanner = false
                    reloadContent()
                }
            }
        }
        onDispose { NetworkMonitor.unregister(context, cb) }
    }

    if (showRetryBanner) {
        RetryOverlay(
            onRetry = {
                if (NetworkMonitor.isOnline(context)) {
                    showRetryBanner = false
                    reloadContent()
                }
                // Still offline → banner stays up, user can press again.
            }
        )
    }
}
