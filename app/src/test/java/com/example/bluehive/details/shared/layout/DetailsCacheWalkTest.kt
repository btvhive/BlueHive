package com.example.bluehive.details.shared.layout

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Fix 5 (v515): every details page open walked the 200 MB image cache four
 * times (and the files folder twice) only to log the totals -- and release
 * builds strip that log. The walk and its logger are gone.
 */
class DetailsCacheWalkTest {

    @Test
    fun `no source calls the cache walk`() {
        val callers = MainSource.all().filterValues { it.contains("CacheMonitor") || it.contains("DetailsCacheStats") }
        assertEquals(emptySet<String>(), callers.keys)
    }

    @Test
    fun `the logger and its composable are deleted`() {
        assertFalse(File("src/main/java/com/example/bluehive/utilities/CacheMonitor.kt").exists())
        assertFalse(File("src/main/java/com/example/bluehive/details/shared/layout/DetailsCacheStats.kt").exists())
    }

    @Test
    fun `neither page walks any folder on open`() {
        for ((path, code) in MainSource.all().filterKeys { it.startsWith("details/") }) {
            assertFalse(path, code.contains("walkTopDown") || code.contains("walkBottomUp"))
        }
    }
}
