package com.example.bluehive.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 675: BlueHive asks Off-Grid Drive for an answer no older than a minute when it
 * checks on its home screen and when its lock screen retries, so a missed
 * payment reaches BlueHive even when Off-Grid Drive was frozen or not running.
 */
class FreshHostAnswerTest {

    private val src = File("src/main/java/com/example/bluehive/auth/HostEntitlementRelay.kt")
        .readText().replace("\r\n", "\n")

    @Test fun `no freshness request reads the plain address`() =
        assertEquals("content://io.host.entitlement.provider/assertion",
            HostEntitlementRelay.providerQuery(null))

    @Test fun `a freshness request adds the parameter Off-Grid Drive reads`() =
        assertEquals("content://io.host.entitlement.provider/assertion?fresh_within_ms=60000",
            HostEntitlementRelay.providerQuery(60_000L))

    @Test fun `a minute, above Off-Grid Drive's 30 second floor`() {
        assertEquals(60_000L, HostEntitlementRelay.FRESH_WITHIN_MS)
        assertTrue(HostEntitlementRelay.FRESH_WITHIN_MS >= 30_000L)
    }

    @Test fun `the home screen check asks for a fresh answer`() =
        assertTrue(src.contains("val fresh = readFromProvider(app, FRESH_WITHIN_MS) ?: return@launch\n            if (fresh == stored(app)) return@launch\n            Log.i(TAG, \"host assertion changed — re-reporting\")"))

    @Test fun `the lock screen retry asks for a fresh answer`() =
        assertTrue(src.contains("var standing = postOnce(app, readFromProvider(app, FRESH_WITHIN_MS) ?: stored(app) ?: return null)"))

    @Test fun `BlueHive's own start still reads the saved answer, so it never waits`() {
        assertTrue(src.contains("            ?: readFromProvider(app)\n"))
        assertTrue(src.contains("val fresh = readFromProvider(app) ?: return@launch\n            if (fresh == stored(app)) return@launch     // host had nothing newer"))
    }

    @Test fun `every provider read goes through the one address builder`() {
        assertTrue(src.contains("context.contentResolver.query(Uri.parse(providerQuery(freshWithinMs)), null, null, null, null)"))
        // The only plain read left is readPairedFromProvider: "is the host paired"
        // is answered from memory and never needs a fresh pass.
        assertEquals(1, Regex("Uri\\.parse\\(PROVIDER_URI\\)").findAll(src).count())
    }
}
