package com.example.bluehive.livetv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

// ─────────────────────────────────────────────────────────────────────────────
//  Live TV chunks arrive disguised as pictures on TikTok's image CDN.
//
//  MEASURED 2026-10-28 on zlive.st, two wrappings:
//    - most channels: a 1x1 WebP whose EXIF chunk holds the MPEG-TS, about
//      4 MB per 3.8 s chunk (Sky Sports F1, HBO, Disney...);
//    - the "artemis" feeds (ESPN Backup): a PNG whose PIXELS are the data --
//      b"TIKTIKPX" + u32 big-endian size + a gzip stream of the MPEG-TS.
//  MEASURED 2026-11-01, a third, on a new host (enterprise.content.tenovos.io),
//  which took most channels off air until it was handled: a PNG whose IDAT
//  holds the MPEG-TS UNCOMPRESSED (IHDR width = the payload size, height 1).
//  ExoPlayer can parse none of them, so [unwrapping] turns each back into plain
//  MPEG-TS and passes everything else (the playlist itself, plain TS) through
//  untouched. Pure JVM code on streams, no Android types, so the unit tests
//  run it directly.
// ─────────────────────────────────────────────────────────────────────────────

internal object ChunkUnwrap {

    enum class Kind { WEBP, PIXEL_PNG, RAW_PNG, PASSTHROUGH }

    class Unwrapped(val kind: Kind, val stream: InputStream)

    private const val TS_SYNC = 0x47
    private const val TS_PACKET = 188

    /** The tag at the start of a pixel-packed PNG's data. */
    private val PIXEL_TAG = "TIKTIKPX".toByteArray(Charsets.ISO_8859_1)

    /** A wrapped chunk is a few MB; a PNG claiming more than this is not one. */
    private const val MAX_PIXEL_BYTES = 64 * 1024 * 1024

    /**
     * Wrap [input] so it reads as MPEG-TS. The WebP path streams (nothing is
     * buffered past the chunk headers); the PNG path has to decode the whole
     * image first, so it reads the chunk to the end before returning.
     */
    fun unwrapping(input: InputStream): Unwrapped {
        val pb = PushbackInputStream(input, TS_PACKET + 1)
        val head = readUpTo(pb, 12)
        pb.unread(head)
        return when {
            isWebp(head) -> Unwrapped(Kind.WEBP, webpPayload(pb))
            isPng(head) -> {
                val bytes = pb.readBytes()
                rawTsInPng(bytes)?.let { Unwrapped(Kind.RAW_PNG, it) }
                    ?: decodePixelPng(bytes)?.let { Unwrapped(Kind.PIXEL_PNG, ByteArrayInputStream(it)) }
                    ?: Unwrapped(Kind.PASSTHROUGH, ByteArrayInputStream(bytes))
            }
            else -> Unwrapped(Kind.PASSTHROUGH, pb)
        }
    }

    fun isWebp(b: ByteArray): Boolean =
        b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
            b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()

    fun isPng(b: ByteArray): Boolean =
        b.size >= 8 && (b[0].toInt() and 0xff) == 0x89 && b[1] == 'P'.code.toByte() &&
            b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()

    /**
     * The first RIFF chunk whose payload starts with two TS packets' sync bytes
     * (offset 0 and 188), as a stream of exactly that payload. The image chunk
     * in front of it (VP8L, 13 bytes in every chunk measured) is skipped.
     */
    private fun webpPayload(pb: PushbackInputStream): InputStream {
        readFully(pb, ByteArray(12))
        val header = ByteArray(8)
        while (true) {
            readFully(pb, header)
            val size = leInt(header, 4).toLong() and 0xffffffffL
            if (size >= TS_PACKET * 5L) {
                val probe = ByteArray(TS_PACKET + 1)
                readFully(pb, probe)
                if ((probe[0].toInt() and 0xff) == TS_SYNC && (probe[TS_PACKET].toInt() and 0xff) == TS_SYNC) {
                    pb.unread(probe)
                    return Bounded(pb, size)
                }
                skipFully(pb, size - probe.size + (size and 1L))
            } else {
                skipFully(pb, size + (size and 1L))
            }
        }
    }

    /**
     * The MPEG-TS a PNG carries uncompressed as its IDAT data, read in place (no
     * copy for the one IDAT measured), or null if [b] is not one: its IDAT must
     * start with two TS packets' sync bytes, which compressed pixels never do.
     */
    fun rawTsInPng(b: ByteArray): InputStream? {
        if (!isPng(b)) return null
        var p = 8
        val idat = ArrayList<Pair<Int, Int>>()          // (offset, length) of each IDAT's data
        while (p + 8 <= b.size) {
            val n = beInt(b, p)
            if (n < 0 || p + 12L + n > b.size) return null
            when (String(b, p + 4, 4, Charsets.ISO_8859_1)) {
                "IDAT" -> idat += (p + 8) to n
                "IEND" -> break
            }
            p += 12 + n
        }
        val (at, length) = idat.firstOrNull() ?: return null
        if (length <= TS_PACKET || (b[at].toInt() and 0xff) != TS_SYNC ||
            (b[at + TS_PACKET].toInt() and 0xff) != TS_SYNC
        ) return null
        if (idat.size == 1) return ByteArrayInputStream(b, at, length)
        val all = ByteArrayOutputStream()
        idat.forEach { (off, n) -> all.write(b, off, n) }
        return ByteArrayInputStream(all.toByteArray())
    }

    /**
     * The MPEG-TS inside a pixel-packed PNG, or null if [b] is not one (an
     * ordinary picture, a different tag, a broken file). Handles 8-bit grey,
     * grey+alpha, RGB and RGBA, every row filter, no interlacing.
     */
    fun decodePixelPng(b: ByteArray): ByteArray? {
        if (!isPng(b)) return null
        var p = 8
        var width = 0
        var height = 0
        var depth = 0
        var colour = 0
        var interlace = 0
        val idat = ByteArrayOutputStream()
        while (p + 8 <= b.size) {
            val n = beInt(b, p)
            if (n < 0 || p + 12L + n > b.size) return null
            when (String(b, p + 4, 4, Charsets.ISO_8859_1)) {
                "IHDR" -> {
                    if (n < 13) return null
                    width = beInt(b, p + 8)
                    height = beInt(b, p + 12)
                    depth = b[p + 16].toInt() and 0xff
                    colour = b[p + 17].toInt() and 0xff
                    interlace = b[p + 20].toInt() and 0xff
                }
                "IDAT" -> idat.write(b, p + 8, n)
                "IEND" -> break
            }
            p += 12 + n
        }
        val bpp = when (colour) { 0 -> 1; 4 -> 2; 2 -> 3; 6 -> 4; else -> return null }
        if (depth != 8 || interlace != 0 || width <= 0 || height <= 0) return null
        val stride = width.toLong() * bpp
        val rawSize = (stride + 1) * height
        if (rawSize > MAX_PIXEL_BYTES) return null
        val raw = inflate(idat.toByteArray(), rawSize.toInt()) ?: return null
        val px = unfilter(raw, stride.toInt(), height, bpp) ?: return null
        if (px.size < 12 || !PIXEL_TAG.indices.all { px[it] == PIXEL_TAG[it] }) return null
        val size = beInt(px, 8)
        if (size <= 0 || 12L + size > px.size) return null
        return try {
            GZIPInputStream(ByteArrayInputStream(px, 12, size)).use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
    }

    private fun inflate(data: ByteArray, expected: Int): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArray(expected)
            var off = 0
            while (off < expected && !inflater.finished()) {
                val n = inflater.inflate(out, off, expected - off)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                off += n
            }
            if (off == expected) out else null
        } catch (e: java.util.zip.DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Undo the PNG row filters. One `when` per row, a plain loop per filter. */
    private fun unfilter(raw: ByteArray, stride: Int, height: Int, bpp: Int): ByteArray? {
        val px = ByteArray(stride * height)
        for (y in 0 until height) {
            val src = y * (stride + 1) + 1
            val dst = y * stride
            val up = dst - stride
            when (raw[src - 1].toInt() and 0xff) {
                0 -> System.arraycopy(raw, src, px, dst, stride)
                1 -> for (i in 0 until stride) {
                    val a = if (i >= bpp) px[dst + i - bpp].toInt() and 0xff else 0
                    px[dst + i] = (raw[src + i] + a).toByte()
                }
                2 -> for (i in 0 until stride) {
                    val b = if (y > 0) px[up + i].toInt() and 0xff else 0
                    px[dst + i] = (raw[src + i] + b).toByte()
                }
                3 -> for (i in 0 until stride) {
                    val a = if (i >= bpp) px[dst + i - bpp].toInt() and 0xff else 0
                    val b = if (y > 0) px[up + i].toInt() and 0xff else 0
                    px[dst + i] = (raw[src + i] + ((a + b) shr 1)).toByte()
                }
                4 -> for (i in 0 until stride) {
                    val a = if (i >= bpp) px[dst + i - bpp].toInt() and 0xff else 0
                    val b = if (y > 0) px[up + i].toInt() and 0xff else 0
                    val c = if (y > 0 && i >= bpp) px[up + i - bpp].toInt() and 0xff else 0
                    px[dst + i] = (raw[src + i] + paeth(a, b, c)).toByte()
                }
                else -> return null
            }
        }
        return px
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val pa = kotlin.math.abs(b - c)
        val pb = kotlin.math.abs(a - c)
        val pc = kotlin.math.abs(a + b - 2 * c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun beInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)

    private fun leInt(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or
            ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)

    private fun readUpTo(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) out else out.copyOf(off)
    }

    private fun readFully(input: InputStream, into: ByteArray) {
        var off = 0
        while (off < into.size) {
            val r = input.read(into, off, into.size - off)
            if (r < 0) throw EOFException("chunk ended before its video")
            off += r
        }
    }

    fun skipFully(input: InputStream, n: Long) {
        var left = n
        val scratch = ByteArray(8192)
        while (left > 0) {
            val r = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (r < 0) throw EOFException("chunk ended early")
            left -= r
        }
    }

    /** At most [limit] bytes of [input]; closing it closes [input]. */
    private class Bounded(input: InputStream, private var limit: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (limit <= 0) return -1
            val r = super.read()
            if (r >= 0) limit--
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (limit <= 0) return -1
            val r = super.read(b, off, minOf(len.toLong(), limit).toInt())
            if (r > 0) limit -= r
            return r
        }

        override fun available(): Int = minOf(super.available().toLong(), limit).toInt()
        override fun markSupported(): Boolean = false
    }
}
