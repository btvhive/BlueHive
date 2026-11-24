package com.example.bluehive.api

import android.util.Log
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.net.android5Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WEBSITE 4's BEST SERVER, ASKED BY THE TV ITSELF (2026-11-23).
 *
 * Lyra ("Prism", stream.hls.lol/mbox) is the MovieBox server vidora.ac's own
 * player uses: HEVC 1080p DASH with the right running time, and it carries
 * titles nothing else behind Vidora has (Teen Titans played on vidora.ac on
 * Lyra while Website 4 found nothing).
 *
 * WHY THE TV ASKS AND NOT OUR SERVER. MEASURED 2026-11-23, same request, same
 * headers: from a home connection Lyra answered with the real stream
 * (sacdn.hakunaymatata.com); from the VPS it answered with a decoy
 * (schooling.cfd/.../hm-decoy, even dressed up with a believable running time).
 * Lyra judges the ADDRESS that asks. The TV is on a home connection, so it gets
 * the real answer. The links it returns are not tied to that address either:
 * the CloudFront policy carries only an expiry (about 7 days).
 *
 * HEVC ONLY: every Lyra stream seen is H.265, so a TV without a hardware HEVC
 * decoder (PlaybackCaps) never asks, and keeps today's Website 4 path.
 *
 * Returns null whenever Lyra cannot be used -- no HEVC, no answer, no tracks, or
 * a decoy -- and the caller carries on with the server's Website 4 lookup.
 */
object LyraResolver {
    private const val TAG = "LyraResolver"
    private const val ORIGIN = "https://vidora.ac"
    private const val ENDPOINT = "https://stream.hls.lol/mbox"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().android5Tls()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun resolve(type: String, tmdbId: Int, season: Int?, episode: Int?): ExtractResponse? {
        if ("hevc" !in PlaybackCaps.value.split(',')) {
            Log.i(TAG, "skipped: no hardware HEVC decoder (Lyra is HEVC-only)")
            return null
        }
        val query = "tmdbId=$tmdbId&type=$type" +
            if (type == "tv") "&seasonId=$season&episodeId=$episode" else ""
        val key = "$type:$tmdbId:${season ?: 0}:${episode ?: 0}"
        val body = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(
                    Request.Builder().url("$ENDPOINT?$query")
                        .header("Origin", ORIGIN)
                        .header("Referer", "$ORIGIN/")
                        .build()
                ).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()
        }
        val fresh = body?.let { pickTrack(it) }?.takeIf { !isDecoy(it) }
        val url = if (fresh != null) {
            Log.i(TAG, "Lyra resolved $key -> ${realHost(fresh)}")
            remember(key, fresh, expiryOf(body))
            fresh
        } else {
            // LYRA BLINKS (2026-11-23): S3E8 played, then answered "no_source"
            // for 9 minutes (S3E10 for 8) while every other episode was fine,
            // then came back by itself. A link it already gave us stays signed
            // for about 7 days, so use that rather than fail.
            recall(key)?.also { Log.w(TAG, "Lyra had nothing for $key; using the link it gave earlier") }
                ?: return null.also { Log.i(TAG, "nothing usable for $key (${body?.take(60) ?: "no answer"})") }
        }
        return ExtractResponse(
            isHls        = false,
            m3u8         = url,
            referer      = "$ORIGIN/",
            user_agent   = null,
            subtitle_url = null,
            page_url     = "$ORIGIN/$type/$tmdbId" + if (type == "tv") "/$season/$episode" else "",
            proxy_url    = null,
        )
    }

    // ── links Lyra already gave, kept until shortly before they expire ──────────
    private const val PREFS = "lyra_links"
    private const val MARGIN_MS = 2 * 60 * 60 * 1000L          // stop 2 h before the signature does
    private const val FALLBACK_LIFE_MS = 24 * 60 * 60 * 1000L  // when Lyra gives no expiry
    private const val MAX_LINKS = 300

    private val prefs: android.content.SharedPreferences?
        get() = BlueHiveApplication.appContext?.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    private fun remember(key: String, url: String, expiresAtMs: Long?) {
        val now = System.currentTimeMillis()
        val until = (expiresAtMs ?: (now + FALLBACK_LIFE_MS)) - MARGIN_MS
        val prefs = prefs ?: return
        runCatching {
            val e = prefs.edit().putString(key, encodeLink(url, until))
            // Drop expired links; if still too many, the soonest to expire go first.
            val live = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to decodeUntil(it) } }
            live.filter { (_, t) -> t == null || t <= now }.forEach { (k, _) -> e.remove(k) }
            live.filter { (_, t) -> t != null && t > now }.sortedBy { it.second }
                .dropLast(MAX_LINKS).forEach { (k, _) -> e.remove(k) }
            e.apply()
        }
    }

    private fun recall(key: String): String? =
        runCatching { prefs?.getString(key, null) }.getOrNull()?.let { decodeLink(it, System.currentTimeMillis()) }

    internal fun encodeLink(url: String, untilMs: Long): String = "$untilMs|$url"

    internal fun decodeUntil(raw: String): Long? = raw.substringBefore('|', "").toLongOrNull()

    /** The stored link, or null when it is malformed or past its time. */
    internal fun decodeLink(raw: String, nowMs: Long): String? {
        val until = decodeUntil(raw) ?: return null
        val url = raw.substringAfter('|', "")
        return url.takeIf { until > nowMs && it.startsWith("http") }
    }

    /** Lyra's "expiresAt" ("2026-11-30T23:12:28Z") in ms, or null. No java.time: API 22. */
    internal fun expiryOf(json: String): Long? = runCatching {
        val s = JSONObject(json).optString("expiresAt").takeIf { it.isNotBlank() } ?: return null
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(s)?.time
    }.getOrNull()

    /** The original-language DASH track's URL, else the first DASH track's. */
    internal fun pickTrack(json: String): String? {
        val tracks = runCatching { JSONObject(json).optJSONArray("tracks") }.getOrNull() ?: return null
        val all = (0 until tracks.length()).mapNotNull { tracks.optJSONObject(it) }
            .filter { it.optString("url").startsWith("http") && it.optString("kind", "dash") == "dash" }
        return (all.firstOrNull { it.optString("mode") == "original" } ?: all.firstOrNull())?.optString("url")
    }

    /**
     * The real media host, read from the manifest URL's q= parameter (base64url
     * JSON whose "d" is the CDN folder), or null when it cannot be read.
     */
    internal fun realHost(mpdUrl: String): String? {
        val q = Regex("[?&]q=([^&]+)").find(mpdUrl)?.groupValues?.get(1) ?: return null
        val raw = q.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        val json = raw.decodeBase64()?.utf8() ?: return null
        return runCatching { JSONObject(json).optString("d") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** A decoy, or a URL whose real host cannot be read (treated as unsafe). */
    internal fun isDecoy(mpdUrl: String): Boolean {
        val host = realHost(mpdUrl) ?: return true
        return "decoy" in host || "schooling.cfd" in host
    }
}
