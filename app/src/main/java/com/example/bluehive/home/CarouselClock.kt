package com.example.bluehive.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.example.bluehive.homeScreenSectionRules.CAROUSEL_AUTO_CYCLE_DELAY_MS
import com.example.bluehive.homeScreenSectionRules.LocalCarouselCycleTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


/**
 * The 6 s clock the trailer and trending carousels advance on, together.
 *
 * The home screen used to keep the tick and read it to provide
 * LocalCarouselCycleTick, so the whole screen re-ran on every tick while it sat
 * idle. Now the screen only holds this object: CarouselClockTicker advances it,
 * and CarouselTickProvider, around the two carousels, is the one place that
 * reads it. LocalCarouselCycleTick stays a static local, so the carousels
 * recompose on a tick exactly as before. Since Stage H1 of the Favorites and
 * Home redesign (docs/favorites-home-redesign-architecture.md, P1).
 */
@Stable
internal class CarouselClock {
    var tick by mutableLongStateOf(0L)
}

/** The carousels pause while the sidebar is open or another screen is on top. Pure, so HomeShelvesTest pins it. */
internal fun carouselPaused(sidebarFocused: Boolean, homeInForeground: Boolean): Boolean =
    sidebarFocused || !homeInForeground

/**
 * Effect 1 of the home screen's 13 (H-C5), called where the clock's effect
 * was: one tick every CAROUSEL_AUTO_CYCLE_DELAY_MS unless [paused].
 */
@Composable
internal fun CarouselClockTicker(clock: CarouselClock, paused: Boolean) {
    // The clock itself pauses when the carousels should pause (sidebar open or
    // home not in foreground). Keying the timer on this value means: while
    // paused, no ticks accumulate; on resume, the loop restarts and the FIRST
    // tick is a full interval away. Without this, the free-running timer would
    // fire a stale tick the instant the sidebar closed — landing on whatever
    // phase it happened to be at — and jump both carousels forward at once.
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) {
            delay(CAROUSEL_AUTO_CYCLE_DELAY_MS)
            clock.tick++
        }
    }
}

/** Gives [content] (the two carousels) the tick. The clock's only reader, so only they re-run on a tick. */
@Composable
internal fun CarouselTickProvider(clock: CarouselClock, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCarouselCycleTick provides clock.tick, content = content)
}
