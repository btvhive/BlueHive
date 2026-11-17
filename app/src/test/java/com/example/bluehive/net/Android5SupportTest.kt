package com.example.bluehive.net

import com.example.bluehive.utilities.SnapEffect
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Android 5 support (2026-11-17): the bundled Let's Encrypt roots, the version
 * gates, and source guards so a new HTTP client, a new use of ThanosEffect or a
 * manifest edit can't quietly undo it.
 */
class Android5SupportTest {

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }

    private val mainSources: List<File> =
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun `the shipped roots are exactly ISRG Root X1 and X2`() {
        val certs = Android5Tls.bundledRoots()
        assertEquals(
            listOf("CN=ISRG Root X1", "CN=ISRG Root X2"),
            certs.map { it.subjectX500Principal.name.split(",").first { p -> p.startsWith("CN=") } },
        )
        assertEquals(
            listOf(
                "96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6",
                "69:72:9B:8E:15:A8:6E:FC:17:7A:57:AF:B7:17:1D:FC:64:AD:D2:8C:2F:CA:8C:F1:50:7E:34:45:3C:CB:14:70",
            ),
            certs.map { sha256(it.encoded) },
        )
        certs.forEach { it.checkValidity() }
    }

    @Test fun `version gates`() {
        // Bundled roots and no Cronet: below Android 7.1.1 only.
        assertTrue(Android5Tls.needsBundledRoots(22))
        assertTrue(Android5Tls.needsBundledRoots(24))
        assertFalse(Android5Tls.needsBundledRoots(25))   // Fire OS 6
        assertFalse(Android5Tls.needsBundledRoots(28))   // Fire OS 7
        // The dust effect and the on-device Miruro fallback: Android 7 and up.
        assertFalse(SnapEffect.available(22))
        assertFalse(SnapEffect.available(23))
        assertTrue(SnapEffect.available(24))
        assertTrue(SnapEffect.available(28))
        assertFalse(MiruroExtractorActivity.onDeviceAvailable(22))
        assertTrue(MiruroExtractorActivity.onDeviceAvailable(24))
        assertTrue(MiruroExtractorActivity.onDeviceAvailable(28))
    }

    @Test fun `Android 5 gets a real device key through the older keystore spec`() {
        val path = com.example.bluehive.auth.DeviceIdentity::keyPath
        assertEquals(com.example.bluehive.auth.DeviceIdentity.KeyPath.LEGACY, path(22))   // Fire OS 5
        assertEquals(com.example.bluehive.auth.DeviceIdentity.KeyPath.LEGACY, path(19))
        assertEquals(com.example.bluehive.auth.DeviceIdentity.KeyPath.MODERN, path(23))
        assertEquals(com.example.bluehive.auth.DeviceIdentity.KeyPath.MODERN, path(28))   // Fire OS 7, unchanged
        assertEquals(com.example.bluehive.auth.DeviceIdentity.KeyPath.NONE, path(18))
        // Same key type both ways, so the server sees the same kind of public key.
        val src = File("src/main/java/com/example/bluehive/auth/DeviceIdentity.kt").readText()
        assertTrue(src.contains(".setKeyType(\"EC\")") && src.contains(".setKeySize(256)"))
        assertTrue(src.contains("ECGenParameterSpec(\"secp256r1\")"))
        // Before Android 6 the keystore's generator is only registered as "RSA" (the
        // type comes from setKeyType); asking for "EC" failed on the Android 5.1 emulator.
        val legacy = src.substringAfter("private fun createLegacyKey").substringBefore("\n    }\n")
        assertTrue(legacy.contains("KeyPairGenerator.getInstance(\"RSA\", \"AndroidKeyStore\")"))
        assertFalse(legacy.contains("KeyPairGenerator.getInstance(\"EC\""))
    }

    @Test fun `no pattern uses regex features Android 5 lacks`() {
        // Android 5's pattern engine threw on \R and crashed every details page
        // (emulator, 2026-11-17). Named groups (?<name>) only arrived in Android 8.
        val banned = listOf(
            Regex("""\\\\[RhHvVXK]"""),            // \\R etc. as written in a Kotlin string
            Regex("""\(\?<[A-Za-z]"""),             // named group, not lookbehind (?<= (?<!
        )
        val hits = mainSources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val isPattern = "Regex(" in line || "toRegex(" in line || "Pattern.compile(" in line
                if (isPattern && banned.any { it.containsMatchIn(line) }) "${f.name}:${i + 1}" else null
            }
        }
        assertEquals("patterns Android 5 can't compile", emptyList<String>(), hits)
    }

    @Test fun `every OkHttp client goes through android5Tls`() {
        val missing = mainSources.filter { it.name != "Android5Tls.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if ("OkHttpClient.Builder()" in line && "android5Tls()" !in line) "${f.name}:${i + 1}" else null
            }
        }
        assertEquals("clients built without .android5Tls()", emptyList<String>(), missing)
    }

    @Test fun `the app installs the roots first and video skips Cronet below 7_1_1`() {
        val app = File("src/main/java/com/example/bluehive/BlueHiveApplication.kt").readText()
        val onCreate = app.substringAfter("override fun onCreate()")
        assertTrue(onCreate.substringBefore("LanNetwork").contains("Android5Tls.install()"))
        val exo = File("src/main/java/com/example/bluehive/webview/exoplayer_resources/ExoPlayerConfig.kt").readText()
        val cronet = exo.substringAfter("private fun cronet(context: Context)").substringBefore("cronetTried = true")
        assertTrue(cronet.contains("Android5Tls.needsBundledRoots()) return null"))
    }

    @Test fun `ThanosEffect is only touched behind SnapEffect`() {
        val users = mainSources.filter { f -> f.readText().contains("import com.exjunk.") }
        assertEquals(
            setOf("EpisodeSnap.kt", "FavoritesScreen.kt", "FavoritesGrid.kt", "FavoriteCard.kt"),
            users.map { it.name }.toSet(),
        )
        // Every file that builds the effect checks the gate.
        users.filter { f -> Regex("""VanishContainer\(|rememberVanishController\(""").containsMatchIn(f.readText()) }
            .forEach { f -> assertTrue("${f.name} uses ThanosEffect without SnapEffect", f.readText().contains("SnapEffect.available()")) }
    }

    @Test fun `manifest and build stay Android 5 ready`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        // Only ThanosEffect may skip the manifest's minSdk check.
        assertEquals(
            listOf("com.exjunk.lib.thanos.vanish"),
            Regex("""tools:overrideLibrary="([^"]*)"""").findAll(manifest).map { it.groupValues[1] }.toList(),
        )
        val gradle = File("build.gradle.kts").readText()
        assertTrue(Regex("""minSdk = 22\b""").containsMatchIn(gradle))
        assertTrue(gradle.contains("val android5Ceiling"))
        assertFalse(gradle.contains("implementation(libs.androidx.viewfinder.core)"))
    }
}
