package com.example.bluehive.livetv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.InputStream

/**
 * Hands ExoPlayer plain MPEG-TS for Live TV chunks that arrive disguised as
 * pictures (see [ChunkUnwrap]); every other response passes through as it came.
 *
 * Every request is read from byte 0: where the video starts inside a wrapper is
 * only known after its header, so a load resuming at an offset re-reads and
 * skips. Unwrapped answers report `Content-Type: video/mp2t`, so the HLS
 * extractor factory tries the TS extractor first instead of trusting the
 * CDN's `image/webp`.
 */
@UnstableApi
class ChunkUnwrapDataSource(private val upstream: DataSource) : DataSource {

    private var raw: DataSourceInputStream? = null
    private var stream: InputStream? = null
    private var kind = ChunkUnwrap.Kind.PASSTHROUGH
    private var remaining = C.LENGTH_UNSET.toLong()
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val whole = dataSpec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build()
        val input = DataSourceInputStream(upstream, whole).also { raw = it }
        input.open()
        val unwrapped = ChunkUnwrap.unwrapping(input)
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

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkUnwrapDataSource(upstream.createDataSource())
    }
}
