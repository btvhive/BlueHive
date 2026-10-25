package com.example.bluehive.details.shared.playback

import com.example.bluehive.api.ApiClient
import com.example.bluehive.auth.SessionManager
import com.example.bluehive.utilities.ServerOption
import com.example.bluehive.utilities.runCatchingCancellable

/**
 * The Miruro server lists of a details page, and the failover trail through
 * them. Plain collections, not snapshot state: they are only read and written
 * from callbacks, never during composition.
 *
 * Moved here from the movie screen in Stage 6 of the details redesign.
 */
class AnimeServerList internal constructor() {
    // Server lists captured during a DEFAULT extraction, cached for the lifetime
    // of this detail screen (cleared when the user navigates away). Keyed by
    // url + audio so "Choose a server" can skip the enumerate webview pass when
    // the list is already known. Value = (options, defaultIndex).
    val cache = mutableMapOf<String, Pair<List<ServerOption>, Int>>()

    fun key(url: String, dub: Boolean) = "$url|${if (dub) "dub" else "sub"}"

    // ── Automatic server failover ────────────────────────────────────────────
    // When the default (or chosen) server is down — a hard extract failure, or a
    // dub request that fell back to sub — walk the rest of the provider list until
    // one yields a real stream. Works for any anime, dub or sub. `tried` is reset
    // at the start of each play (default attempt).
    val tried = mutableSetOf<String>()

    /**
     * The next untried server for this url and track, from the cached list or,
     * failing that, the list the picker is showing ([shown]).
     */
    fun pickNext(url: String, dub: Boolean, shown: List<ServerOption>): String? {
        val list = cache[key(url, dub)]?.first
            ?: shown.takeIf { it.isNotEmpty() }
            ?: return null
        val untried = list.filter { it.name !in tried }
        if (untried.isEmpty()) return null
        val want = if (dub) "dub" else "sub"
        // Prefer a provider whose tags advertise the requested track; else any untried.
        return (untried.firstOrNull { o -> o.tags.any { it.equals(want, true) } } ?: untried.first()).name
    }

    /**
     * Fetch and cache the server list for this url and track, unless it is
     * cached already. TWO CALLERS, TWO REASONS.
     *
     * The dub failover is only as good as the list it picks from, and on the
     * "Default server" path there is no list: nothing has opened the picker,
     * so the cache is empty and pick-next returns null on the first ask. That
     * is the exact path the failover exists for.
     *
     * The play path calls it for whichever track actually played, so that
     * backing out of the player and opening "Choose a server" finds the list
     * already here instead of showing a spinner. It used to be dub-only, which
     * left every sub viewer paying for an enumerate the moment they went
     * looking for a different provider.
     *
     * Cheap now in a way it was not before: the extractor harvests the list
     * during the extraction itself and the API caches it, so on the play path
     * this is one HTTP round trip, no browser. Idempotent, and silent on
     * failure: a missing list means no failover and a picker that fetches its
     * own, which is where we already were.
     */
    suspend fun ensure(url: String, dub: Boolean, request: PlaybackRequest) {
        if (cache[key(url, dub)] != null) return
        val list = runCatchingCancellable {
            ApiClient.bluehiveApi.extractServers(
                type    = request.apiType,
                tmdbId  = request.tmdbId,
                season  = request.apiSeason,
                episode = request.apiEpisode,
                audio   = if (dub) "dub" else "sub",
                device  = SessionManager.get().deviceFingerprint,
            )
        }.getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return
        val opts = list.servers.map { ServerOption(it.name, emptyList()) }
        if (opts.isEmpty()) return
        cache[key(url, dub)] =
            opts to list.servers.indexOfFirst { it.is_default }.coerceAtLeast(0)
    }
}
