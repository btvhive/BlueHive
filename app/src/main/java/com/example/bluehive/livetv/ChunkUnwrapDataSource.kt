package com.example.bluehive.livetv

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hands ExoPlayer plain MPEG-TS for Live TV chunks that arrive disguised as
 * pictures (see [ChunkUnwrap]); every other response passes through as it came.
 *
 * Every request is read from byte 0: where the video starts inside a wrapper is
 * only known after its header, so a load resuming at an offset re-reads and
 * skips. Unwrapped answers report `Content-Type: video/mp2t`, so the HLS
 * extractor factory tries the TS extractor first instead of trusting the
 * CDN's `image/webp`.
 *
 * THE RELAY FALLBACK (2026-11-18). A chunk that is a picture in a wrapping this
 * app cannot read ([ChunkUnwrap.Kind.UNREADABLE_IMAGE]) is fetched again through
 * the server, which unwraps it and answers plain MPEG-TS; from then on every
 * chunk of this tune goes straight to the relay. A new trick by the site is then
 * a server-only fix. No relay (null) or a server with the relay off (404) leaves
 * the chunk as it was, which fails the way it always did.
 */
@UnstableApi
class ChunkUnwrapDataSource(
    private val upstream: DataSource,
    private val relay: ((Uri) -> Uri)? = null,
    private val relayAll: AtomicBoolean = AtomicBoolean(false),
) : DataSource {

    private var raw: DataSourceInputStream? = null
    private var stream: InputStream? = null
    private var kind = ChunkUnwrap.Kind.PASSTHROUGH
    private var remaining = C.LENGTH_UNSET.toLong()
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val chunk = !isPlaylist(dataSpec.uri)
        val relayed = if (chunk && relayAll.get()) relay?.invoke(dataSpec.uri) else null
        var unwrapped = openUnwrapped(dataSpec, relayed ?: dataSpec.uri)
        if (unwrapped.kind == ChunkUnwrap.Kind.UNREADABLE_IMAGE && relayed == null && chunk) {
            val via = relay?.invoke(dataSpec.uri)
            if (via != null) {
                Log.w(TAG, "chunk in a wrapping this app cannot read; using the server relay for this tune")
                relayAll.set(true)
                raw?.close()
                unwrapped = openUnwrapped(dataSpec, via)
            }
        }
        kind = unwrapped.kind
        ChunkUnwrap.skipFully(unwrapped.stream, dataSpec.position)
        stream = unwrapped.stream
        remaining = dataSpec.length
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = stream ?: return C.RESULT_END_OF_INPUT
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val n = s.read(buffer, offset, want)
        if (n < 0) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        return n
    }

    private fun openUnwrapped(dataSpec: DataSpec, from: Uri): ChunkUnwrap.Unwrapped {
        val whole = dataSpec.buildUpon().setUri(from).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build()
        val input = DataSourceInputStream(upstream, whole).also { raw = it }
        input.open()
        return ChunkUnwrap.unwrapping(input)
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (kind == ChunkUnwrap.Kind.PASSTHROUGH) upstream.responseHeaders
        else mapOf("Content-Type" to listOf("video/mp2t"))

    override fun close() {
        try {
            raw?.close()
        } finally {
            raw = null
            stream = null
            uri = null
        }
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val relay: ((Uri) -> Uri)? = null,
    ) : DataSource.Factory {
        /** One per tune: once a chunk needed the relay, the rest go there first. */
        private val relayAll = AtomicBoolean(false)
        override fun createDataSource(): DataSource =
            ChunkUnwrapDataSource(upstream.createDataSource(), relay, relayAll)
    }

    companion object {
        private const val TAG = "LiveChunks"

        /** Playlists (ours and any variant) are never relayed as chunks. */
        internal fun isPlaylist(u: Uri): Boolean =
            (u.lastPathSegment ?: "").endsWith(".m3u8", ignoreCase = true)

        /**
         * The relay address for [chunk] on the channel whose playlist is
         * [playlist] (".../api/live/{ch}/index.m3u8?t=GRANT"): the same grant, at
         * ".../api/live/{ch}/chunk?t=GRANT&u=<chunk>".
         */
        fun relayFor(playlist: Uri): (Uri) -> Uri = { chunk ->
            val segs = playlist.pathSegments
            playlist.buildUpon()
                .path(segs.dropLast(1).joinToString("/", prefix = "/") + "/chunk")
                .appendQueryParameter("u", chunk.toString())
                .build()
        }
    }
}
