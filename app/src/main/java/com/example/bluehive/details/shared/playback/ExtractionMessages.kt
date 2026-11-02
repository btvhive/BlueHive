package com.example.bluehive.details.shared.playback

import com.example.bluehive.BuildConfig
import com.example.bluehive.api.ExtractServerOption
import com.example.bluehive.api.SourceCatalog
import com.example.bluehive.auth.SessionManager
import java.net.URLEncoder
import java.util.UUID

// Used by both details screens. Moved here word for word from
// TVShowsDetailsScreenCompose.kt in Stage 1 of the details redesign
// (docs/details-redesign-architecture.md): the movie screen used to call into
// the TV file for these, so when ART rejected the TV class in v371-v375, movie
// playback died with it. DetailsExtractionMessagesTest pins their behaviour.

/**
 * Sources the backend extractor can drive.
 *
 * The name doubles as the backend's source key once lowercased ("VidEasy" ->
 * "videasy"), which is why the two must stay in step with `_SOURCES` in
 * bluehive_plugin/extract.py — adding a source here without a template there
 * yields a 400 from /api/extract.
 *
 * MEASURED 2026-08-03, and the difference matters:
 *
 *   VidFast + VidEasy → moon.ironwallnet.net, segments on rotating throwaway
 *       `.site` hosts. Identical manifest token for the same episode, so they are
 *       ONE upstream wearing two names — and roughly half those segment hosts are
 *       severed by the ISP filter (signtime/housestrong/solaratom). Picking the
 *       other one does not escape a blocked episode.
 *
 *   VidSpark → flixcdn.cyou, segments on *.tiktokcdn.com. A genuinely separate
 *       upstream, and all three hosts complete a TLS handshake from the blocked
 *       TV (3/3). An ISP cannot blocklist TikTok's CDN without breaking TikTok,
 *       which makes this the real failover target rather than a second front door.
 */
/**
 * Pull the backend's failure code out of a 502 body.
 *
 * /api/extract returns `{"detail": {"reason": "...", "detail": "..."}}` on a
 * source failure. `reason` is a stable code (source_blocked / no_stream /
 * timeout / unknown); the human-readable half lives in [extractionErrorMessage]
 * so the wording is the app's business, not the API's.
 *
 * errorBody() is a one-shot stream — read it once, here, or a later caller gets
 * an empty string and every failure looks like "unknown".
 */
internal fun parseExtractReason(resp: retrofit2.Response<*>?): String? = runCatching {
    val raw = resp?.errorBody()?.string().orEmpty()
    if (raw.isBlank()) null
    else org.json.JSONObject(raw).optJSONObject("detail")?.optString("reason")?.ifBlank { null }
}.getOrNull()

/**
 * What the user actually reads when extraction fails.
 *
 * WHY THIS EXISTS: every failure used to say "This episode isn't available from
 * this source right now." MEASURED 2026-08-04 — picking VidFast on a filtered
 * line fails because the ISP blocks `vidfast.vc`, and the app told the user the
 * episode didn't exist. The remedy for that is "pick another source"; the remedy
 * for a genuinely missing episode is the opposite. One message could not serve
 * both, and the wrong one sends people hunting for a problem that isn't there.
 *
 * The source is NAMED, by the viewer's name for it: "Your network is blocking
 * Website 2" is a fact someone can act on; "this source is unavailable" is
 * not. Callers pass labels ("Website 2"), never routing names (2026-11-01).
 */
internal fun extractionErrorMessage(
    httpCode: Int?,
    reason: String?,
    sourceName: String,
    unit: String,               // "episode" or "movie"
    /**
     * Every source the chain tried, in order, including [sourceName] last.
     *
     * NAMING ONLY THE LAST ONE WAS WRONG TWICE. Pressing VidNest on South Park
     * s15e4 walked VidNest -> VidFast -> VidLove and then reported "VidLove
     * doesn't have this episode" -- a source the viewer never chose, blamed for
     * a failure three sources wide. Worse, it reads as a fact about VidLove,
     * which sends someone to try the others when the others have already been
     * tried.
     */
    tried: List<String> = emptyList(),
): String = when {
    // ── MORE THAN ONE SOURCE WAS TRIED ──────────────────────────────────
    //
    // Deliberately claims less than the single-source messages below. Each
    // source failed for its own reason and only the LAST one's is in hand, so
    // "none of them have it" would be an assertion about sources whose reason
    // we never saw. "could not play it" is what is actually known.
    tried.size > 1 -> {
        val list = tried.dropLast(1).joinToString(", ") + " and " + tried.last()
        "Tried $list." +
            "\n\nNone of them could play this $unit."
    }
    // Our own extractor, not the source — a restart or a cold container.
    httpCode == 503 ->
        "$sourceName is starting up.\n\nGive it a moment and try again."

    // The ISP severed the connection to the source's domain. Nothing the user
    // can fix by retrying, and nothing wrong with the title itself.
    reason == "source_blocked" ->
        "Your network is blocking $sourceName.\n\nPick a different website below — " +
            "the others usually work."

    // Site loaded fine, produced no video. Usually the title genuinely isn't
    // there (or hasn't aired yet).
    reason == "no_stream" ->
        "$sourceName doesn't have this $unit.\n\nTry a different website."

    reason == "timeout" ->
        "$sourceName took too long to respond.\n\nTry again, or pick another website."

    // The manifest loaded and its segments are dead — the source has a broken
    // copy. MEASURED 2026-08-30: MoviesAPI served Jeepers Creepers as a playlist
    // whose every segment 403'd, verified dead from an unfiltered datacenter IP,
    // while VidFast served the same title perfectly. "Couldn't load" would send
    // someone hunting for a network problem that is not there; the title is fine
    // and another source has it.
    reason == "dead_segments" ->
        "$sourceName has a broken copy of this $unit.\n\nTry another website — " +
            "the $unit itself is fine."

    else ->
        "Couldn't load this $unit from $sourceName.\n\nTry another website."
}

/**
 * The loading screen's amber note when a website gave no video and the chain
 * moves on: "Website 1 is acting up, so we're trying Website 2..." (the
 * user's wording, 2026-11-01). Both are the viewer's names for the sources.
 */
internal fun failoverNote(failed: String, next: String): String =
    "$failed is acting up, so we\u2019re trying $next\u2026"

/** The same note when the stream died mid-play and the chain picks it up at the dead spot. */
internal fun droppedNote(failed: String, next: String): String =
    "$failed dropped out, so we\u2019re picking up on $next\u2026"

/**
 * Relay URL for an anime stream, tagged so the fallback can be accounted for.
 *
 * VidFast/VidEasy/VidSpark get this minted server-side by /api/extract, which
 * already knows the episode. Miruro is extracted ON DEVICE, so the backend never
 * sees the playback — without these tags an anime relay lands in
 * bluehive.relay_sessions as an anonymous byte count with no title attached.
 *
 * The type is forced to anime_* rather than passing the screen's mediaType:
 * Miruro is anime-only, and the whole point of the column is telling anime
 * traffic apart from the rest.
 *
 * sid is minted per CALL, not per episode — the table answers "how many
 * playbacks fell back to the relay", so two viewers of one episode are two rows.
 */
internal fun animeRelayUrl(
    m3u8: String,
    tmdbId: Int,
    mediaType: String,          // anime_tv | anime_movie
    title: String,
    profileId: Int,
    sourceName: String,
    season: Int? = null,
    episode: Int? = null,
): String {
    fun q(k: String, v: Any?): String =
        if (v == null || v.toString().isBlank()) ""
        else "&" + k + "=" + URLEncoder.encode(v.toString(), "UTF-8")

    return BuildConfig.API_BASE_URL.trimEnd('/') +
            "/api/hls/proxy?url=" + URLEncoder.encode(m3u8, "UTF-8") +
            "&sid=" + UUID.randomUUID().toString().replace("-", "") +
            q("tmdb", tmdbId) + q("mtype", mediaType) +
            q("season", season) + q("episode", episode) +
            q("src", sourceName) + q("title", title) +
            q("profile", profileId.takeIf { it > 0 }) +
            q("device", runCatching { SessionManager.get().deviceFingerprint }.getOrNull())
}

/**
 * What an anime stream is called in the player's title strip and in history.
 *
 * NAMES THE SERVER, NOT THE TRACK. "Miruro (Dub)" spent the one line of space
 * on something the viewer can hear within a second of playback starting; which
 * of six providers is serving it is the thing they cannot know and the thing
 * that matters when it stalls.
 *
 * Prefers what the backend observed over what was asked for: a requested
 * server that the site quietly ignored would otherwise be reported as in use.
 * Falls back to the plain source name when neither is known, so the strip
 * never shows a dangling "Miruro ".
 */
internal fun miruroSourceLabel(
    servers: List<ExtractServerOption>?,
    requested: String?,
): String {
    val inUse = servers?.firstOrNull { it.is_default }?.name?.takeIf { it.isNotBlank() }
        ?: requested?.takeIf { it.isNotBlank() }
    return if (inUse == null) "Miruro" else "Miruro $inUse"
}

/**
 * Should this source be played through the backend extractor rather than the
 * embedded browser?
 *
 * BACKEND-OWNED NOW. This was a hardcoded list of eight names, which quietly
 * broke the thing sources.conf exists for: a source added there that the client
 * had never heard of failed the match and fell through to the raw embed --
 * losing the relay and keeping a browser engine resident for the whole title,
 * which is what OOM-kills the 2 GB box. The answer travels with the source.
 *
 * The compiled-in list survives as SourceCatalog.BUILTIN_EXTRACTABLE and is
 * consulted only when the backend did not say, so an older server behaves
 * exactly as this function did before.
 */
internal fun usesBackendExtraction(sourceName: String): Boolean =
    SourceCatalog.canBackendExtract(sourceName)
