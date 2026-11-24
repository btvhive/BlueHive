package com.example.bluehive.webview.exoplayer_resources

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSchemeDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

// ─────────────────────────────────────────────────────────────────────────────
//  PLAYLISTS THAT CARRY A PIECE OF THE VIDEO INLINE (2026-11-22).
//
//  VidLove's Fermat server sends its fMP4 init segment inside the playlist:
//      #EXT-X-MAP:URI="data:video/mp4;base64,AAAAIGZ0eXBpc282..."
//  Cronet fetches web addresses only, so every Fermat play failed with
//  ERR_UNKNOWN_URL_SCHEME (MEASURED on the Fire Stick, Spider-Man: Brand New
//  Day). data: addresses are now read here, everything else goes to the
//  network exactly as before. The app tells the server it can (PlaybackCaps
//  "datauri"); an older app never gets such a playlist.
// ─────────────────────────────────────────────────────────────────────────────

object DataUriSource {
    @UnstableApi
    fun wrap(factory: DataSource.Factory): DataSource.Factory =
        DataSource.Factory { Routing(factory.createDataSource()) }

    @UnstableApi
    private class Routing(private val network: DataSource) : DataSource {
        private val inline = DataSchemeDataSource()
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            network.addTransferListener(transferListener)
            inline.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val ds = if ("data".equals(dataSpec.uri.scheme, ignoreCase = true)) inline else network
            current = ds
            return ds.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            (current ?: network).read(buffer, offset, length)

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            current?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                current?.close()
            } finally {
                current = null
            }
        }
    }
}
