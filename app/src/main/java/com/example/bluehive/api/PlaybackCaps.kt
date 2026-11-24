package com.example.bluehive.api

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

/**
 * What this TV can play, sent with every extraction as ?caps= (2026-11-22).
 *
 * The server's VidLove picker measures every server (real picture size, codec,
 * bitrate, speed) and plays the best one THIS TV can decode:
 *   datauri  -- this app reads playlists that carry data: init segments
 *               (DataUriSource; Fermat sends one);
 *   hevc     -- a HARDWARE H.265 decoder that does 1920x1080 (Euler and
 *               Pythagoras are HEVC; MEASURED Brand New Day: the only HD
 *               server that day was Euler 2592x1080 HEVC, and without this the
 *               picker fell to Gauss at 854x400);
 *   uhd      -- hardware decoders that do 3840x2160 (Fermat answered one film
 *               at 3840x1600 with nothing smaller -- a 1080p stick cannot
 *               decode that).
 *
 * Software decoders do not count: they exist on every Android TV and choke on
 * HD video. Worked out once per process.
 */
object PlaybackCaps {
    val value: String by lazy {
        val caps = mutableListOf("datauri")
        try {
            val hevcHd = supports(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080)
            if (hevcHd) caps += "hevc"
            val avc4k = supports(MediaFormat.MIMETYPE_VIDEO_AVC, 3840, 2160)
            if (avc4k && (!hevcHd || supports(MediaFormat.MIMETYPE_VIDEO_HEVC, 3840, 2160))) caps += "uhd"
        } catch (e: Exception) {
            Log.e("scraper-vidapi", "playback caps: codec list unreadable ($e)")
        }
        caps.joinToString(",").also { Log.e("scraper-vidapi", "playback caps: $it") }
    }

    private fun supports(mime: String, w: Int, h: Int): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && isHardware(info) &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                runCatching {
                    info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(w, h) == true
                }.getOrDefault(false)
        }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated
        val n = info.name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw."))
    }
}
