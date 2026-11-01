package com.example.bluehive.livetv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The wrappings measured on zlive.st, rebuilt byte for byte: MPEG-TS in a
 * WebP's EXIF chunk and MPEG-TS gzipped into a PNG's pixels (2026-10-28), and
 * MPEG-TS as a PNG's uncompressed IDAT (2026-11-01).
 */
class ChunkUnwrapTest {

    private fun ts(packets: Int): ByteArray = ByteArray(packets * 188) { i ->
        if (i % 188 == 0) 0x47 else ((i * 31 + i / 188) and 0xff).toByte()
    }

    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun be(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    private fun riffChunk(fourcc: String, data: ByteArray): ByteArray =
        fourcc.toByteArray(Charsets.ISO_8859_1) + le(data.size) + data + (if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf())

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = "WEBP".toByteArray() + chunks.fold(byteArrayOf()) { acc, c -> acc + c }
        return "RIFF".toByteArray() + le(body.size) + body
    }

    /** As measured: a 13-byte VP8L image (odd, so padded) then EXIF holding the TS. */
    private fun measuredWebp(payload: ByteArray) =
        webp(riffChunk("VP8L", ByteArray(13) { if (it == 0) 0x2f else 0 }), riffChunk("EXIF", payload))

    private fun pngChunk(kind: String, data: ByteArray): ByteArray {
        val k = kind.toByteArray(Charsets.ISO_8859_1)
        val crc = CRC32().apply { update(k); update(data) }.value.toInt()
        return be(data.size) + k + data + be(crc)
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val pa = kotlin.math.abs(b - c)
        val pb = kotlin.math.abs(a - c)
        val pc = kotlin.math.abs(a + b - 2 * c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    /** A pixel-packed PNG; row y uses filter [filterFor] (cycling all five by default). */
    private fun pixelPng(
        payload: ByteArray,
        width: Int = 64,
        colour: Int = 2,
        tag: String = "TIKTIKPX",
        filterFor: (Int) -> Int = { it % 5 },
    ): ByteArray {
        val bpp = mapOf(0 to 1, 4 to 2, 2 to 3, 6 to 4).getValue(colour)
        val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(payload) } }.toByteArray()
        val data = tag.toByteArray(Charsets.ISO_8859_1) + be(gz.size) + gz
        val stride = width * bpp
        val rows = (data.size + stride - 1) / stride
        val px = data + ByteArray(rows * stride - data.size)
        val raw = ByteArrayOutputStream()
        for (y in 0 until rows) {
            val f = filterFor(y)
            raw.write(f)
            for (i in 0 until stride) {
                val x = px[y * stride + i].toInt() and 0xff
                val a = if (i >= bpp) px[y * stride + i - bpp].toInt() and 0xff else 0
                val b = if (y > 0) px[(y - 1) * stride + i].toInt() and 0xff else 0
                val c = if (y > 0 && i >= bpp) px[(y - 1) * stride + i - bpp].toInt() and 0xff else 0
                val pred = when (f) { 0 -> 0; 1 -> a; 2 -> b; 3 -> (a + b) shr 1; else -> paeth(a, b, c) }
                raw.write((x - pred) and 0xff)
            }
        }
        val idat = ByteArrayOutputStream().also {
            DeflaterOutputStream(it, Deflater(6)).use { d -> d.write(raw.toByteArray()) }
        }.toByteArray()
        val ihdr = be(width) + be(rows) + byteArrayOf(8, colour.toByte(), 0, 0, 0)
        return byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
            pngChunk("IHDR", ihdr) + pngChunk("IDAT", idat) + pngChunk("IEND", byteArrayOf())
    }

    private fun unwrap(bytes: ByteArray) = ChunkUnwrap.unwrapping(ByteArrayInputStream(bytes))

    @Test
    fun webpAsMeasured_givesTheExactTs() {
        val payload = ts(400)
        val out = unwrap(measuredWebp(payload))
        assertEquals(ChunkUnwrap.Kind.WEBP, out.kind)
        assertArrayEquals(payload, out.stream.readBytes())
    }

    @Test
    fun webp_tsInAnyChunkName_afterAnEvenSizedImage() {
        val payload = ts(50)
        val bytes = webp(riffChunk("VP8 ", ByteArray(4000) { 7 }), riffChunk("XMP ", payload))
        assertArrayEquals(payload, unwrap(bytes).stream.readBytes())
    }

    @Test
    fun webp_aChunkThatOnlyStartsWith0x47_isNotTakenForVideo() {
        val decoy = ByteArray(2000) { if (it == 0) 0x47 else 1 }
        val payload = ts(20)
        val bytes = webp(riffChunk("VP8L", decoy), riffChunk("EXIF", payload))
        assertArrayEquals(payload, unwrap(bytes).stream.readBytes())
    }

    @Test(expected = IOException::class)
    fun webp_withNoVideoChunk_failsLoudly() {
        unwrap(webp(riffChunk("VP8L", ByteArray(13)))).stream.readBytes()
    }

    @Test
    fun webp_resumeAtAnOffset_skipsWithinTheVideo() {
        val payload = ts(30)
        val out = unwrap(measuredWebp(payload))
        ChunkUnwrap.skipFully(out.stream, 1000)
        assertArrayEquals(payload.copyOfRange(1000, payload.size), out.stream.readBytes())
    }

    @Test
    fun pixelPng_everyRowFilter_givesTheExactTs() {
        val payload = ts(900)
        val out = unwrap(pixelPng(payload))
        assertEquals(ChunkUnwrap.Kind.PIXEL_PNG, out.kind)
        assertArrayEquals(payload, out.stream.readBytes())
    }

    @Test
    fun pixelPng_greyAndRgba_andTheMeasured512Width() {
        val payload = ts(300)
        assertArrayEquals(payload, ChunkUnwrap.decodePixelPng(pixelPng(payload, width = 100, colour = 0)))
        assertArrayEquals(payload, ChunkUnwrap.decodePixelPng(pixelPng(payload, width = 37, colour = 6)))
        assertArrayEquals(payload, ChunkUnwrap.decodePixelPng(pixelPng(payload, width = 512) { 4 }))
    }

    /** As measured 2026-11-01 (tenovos.io): IHDR width = payload size, height 1, RGB; IDAT = the raw TS. */
    private fun rawTsPng(vararg idats: ByteArray): ByteArray {
        val size = idats.sumOf { it.size }
        val ihdr = be(size) + be(1) + byteArrayOf(8, 2, 0, 0, 0)
        return byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
            pngChunk("IHDR", ihdr) + idats.fold(byteArrayOf()) { acc, d -> acc + pngChunk("IDAT", d) } +
            pngChunk("IEND", byteArrayOf())
    }

    @Test
    fun rawTsPng_asMeasured_givesTheExactTs() {
        val payload = ts(2000)
        val out = unwrap(rawTsPng(payload))
        assertEquals(ChunkUnwrap.Kind.RAW_PNG, out.kind)
        assertArrayEquals(payload, out.stream.readBytes())
    }

    @Test
    fun rawTsPng_splitAcrossIdats_andResumedAtAnOffset() {
        val payload = ts(600)
        val split = rawTsPng(payload.copyOfRange(0, 188 * 250), payload.copyOfRange(188 * 250, payload.size))
        assertArrayEquals(payload, unwrap(split).stream.readBytes())
        val out = unwrap(rawTsPng(payload))
        ChunkUnwrap.skipFully(out.stream, 5000)
        assertArrayEquals(payload.copyOfRange(5000, payload.size), out.stream.readBytes())
    }

    @Test
    fun rawTsPng_isNotTakenForAPixelPng_andTheOtherWayRound() {
        assertNull(ChunkUnwrap.rawTsInPng(pixelPng(ts(300))))        // compressed pixels never start with sync bytes
        assertNull(ChunkUnwrap.decodePixelPng(rawTsPng(ts(300))))
        assertArrayEquals(ts(300), unwrap(pixelPng(ts(300))).stream.readBytes())
    }

    @Test
    fun ordinaryPng_passesThroughUntouched() {
        val picture = pixelPng(ts(10), tag = "NOTVIDEO")
        assertNull(ChunkUnwrap.decodePixelPng(picture))
        val out = unwrap(picture)
        assertEquals(ChunkUnwrap.Kind.PASSTHROUGH, out.kind)
        assertArrayEquals(picture, out.stream.readBytes())
    }

    @Test
    fun brokenPng_isNotDecoded() {
        val good = pixelPng(ts(10))
        assertNull(ChunkUnwrap.decodePixelPng(good.copyOf(good.size / 2)))
    }

    @Test
    fun plainTs_playlistAndEmpty_passThrough() {
        val plain = ts(10)
        val playlist = "#EXTM3U\n#EXTINF:3.84,\nhttps://cdn.example/a.image\n".toByteArray()
        for (bytes in listOf(plain, playlist, byteArrayOf(), byteArrayOf(1, 2, 3))) {
            val out = unwrap(bytes)
            assertEquals(ChunkUnwrap.Kind.PASSTHROUGH, out.kind)
            assertArrayEquals(bytes, out.stream.readBytes())
        }
    }

    @Test
    fun webpStreams_theVideoBeginsBeforeTheChunkEnds() {
        val payload = ts(5000)
        val bytes = measuredWebp(payload)
        val reads = mutableListOf<Int>()
        val counting = object : java.io.FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { reads += it }
        }
        val out = ChunkUnwrap.unwrapping(counting)
        val first = ByteArray(188)
        assertEquals(188, out.stream.read(first, 0, 188))
        assertTrue("headers only, not the whole chunk, read before the first packet",
            reads.filter { it > 0 }.sum() < 1024)
    }
}
