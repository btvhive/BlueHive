package com.example.bluehive.testutil

import java.io.File

/**
 * The app's own source, for tests that guard HOW code is written where a JVM
 * test cannot run it: what Compose reads while composing versus while drawing
 * is invisible without a device, but the read itself is plain to see in the
 * source. Unit tests run with the app module as the working directory.
 */
internal object MainSource {

    private const val CRLF = "\r\n"
    private const val LF = "\n"

    /** The file at [path] under com/example/bluehive, comments removed (strings kept), with LF line ends. */
    fun code(path: String): String = stripComments(read(File("src/main/java/com/example/bluehive/$path")))

    /** Every main source file, by path relative to com/example/bluehive, comments removed. */
    fun all(): Map<String, String> {
        val root = File("src/main/java/com/example/bluehive")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to stripComments(read(it)) }
    }

    /** A checkout may have CRLF line ends (core.autocrlf); the guards slice on LF. */
    private fun read(file: File): String = file.readText().replace(CRLF, LF)

    /** [source] without its comments. Strings and char literals are kept as they are, URLs in them included. */
    fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var blockDepth = 0
        var inString = false
        var rawString = false
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            when {
                blockDepth > 0 -> when {
                    c == '/' && next == '*' -> { blockDepth++; i++ }
                    c == '*' && next == '/' -> { blockDepth--; i++ }
                }
                rawString -> {
                    out.append(c)
                    if (source.startsWith("\"\"\"", i)) { out.append("\"\""); i += 2; rawString = false }
                }
                inString -> {
                    out.append(c)
                    if (c == '\\' && next != null) { out.append(next); i++ }
                    else if (c == '"') inString = false
                }
                source.startsWith("\"\"\"", i) -> { out.append("\"\"\""); i += 2; rawString = true }
                c == '"' -> { out.append(c); inString = true }
                c == '\'' && next != null -> {
                    // A char literal: copy it whole, so '"' does not open a string.
                    val end = source.indexOf('\'', i + if (next == '\\') 3 else 2)
                    if (end < 0) out.append(c) else { out.append(source, i, end + 1); i = end }
                }
                c == '/' && next == '/' -> { while (i < source.length && source[i] != '\n') i++; out.append('\n') }
                c == '/' && next == '*' -> { blockDepth = 1; i++ }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
