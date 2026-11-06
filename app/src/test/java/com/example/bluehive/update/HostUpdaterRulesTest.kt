package com.example.bluehive.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which app installs BlueHive's updates (v664). The case that matters: after a
 * fallback self-update Android lists BlueHive as its own installer, and a trusted
 * host must still be found, while an untrusted app answering the action never is.
 */
class HostUpdaterRulesTest {

    private val self = "com.bluehive.tv"
    private val host = "com.offgriddrive.tv"
    private val hostKey = "409e0139a3b89df9a9b0d6a36f19c8c37db47bd91d36f4fce40b12815e4eea1b"
    private val trusted = setOf(hostKey)
    private val stranger = "com.stranger.app"
    private val strangerKey = "b".repeat(64)

    private fun choose(
        installer: String?,
        answering: List<String>,
        certs: Map<String, List<String>> = mapOf(host to listOf(hostKey), stranger to listOf(strangerKey)),
    ) = HostUpdaterRules.chooseHost(self, installer, answering, certs, trusted)

    @Test fun installerOfRecordThatAnswersIsUsed() = assertEquals(host, choose(installer = host, answering = listOf(host)))

    /** Android vouches for the installer of record, so its key is not checked (as before v664). */
    @Test fun installerOfRecordNeedsNoPin() =
        assertEquals(stranger, choose(installer = stranger, answering = listOf(stranger, host)))

    @Test fun afterASelfUpdateTheTrustedHostIsFound() = assertEquals(host, choose(installer = self, answering = listOf(host)))

    @Test fun withNoInstallerRecordedTheTrustedHostIsFound() {
        assertEquals(host, choose(installer = null, answering = listOf(host)))
        assertEquals(host, choose(installer = "", answering = listOf(host)))
    }

    @Test fun installedByAnotherAppTheTrustedHostIsFound() =
        assertEquals(host, choose(installer = "com.android.packageinstaller", answering = listOf(host)))

    @Test fun anUntrustedAppAnsweringIsNeverUsed() {
        assertNull(choose(installer = self, answering = listOf(stranger)))
        assertEquals(host, choose(installer = self, answering = listOf(stranger, host)))
    }

    @Test fun keysCompareIgnoringCase() =
        assertEquals(host, choose(installer = self, answering = listOf(host), certs = mapOf(host to listOf(hostKey.uppercase()))))

    @Test fun anyOfSeveralSigningKeysMayMatch() =
        assertEquals(host, choose(installer = self, answering = listOf(host), certs = mapOf(host to listOf(strangerKey, hostKey))))

    @Test fun unreadableKeysMeanNotTrusted() =
        assertNull(choose(installer = self, answering = listOf(host), certs = emptyMap()))

    @Test fun nothingAnsweringMeansUpdateYourself() {
        assertNull(choose(installer = host, answering = emptyList()))
        assertNull(choose(installer = self, answering = emptyList()))
    }

    @Test fun blueHiveIsNeverItsOwnHost() {
        assertNull(choose(installer = self, answering = listOf(self), certs = mapOf(self to listOf(hostKey))))
    }

    /** The installer of record must still answer the action; otherwise fall to the pin. */
    @Test fun installerThatDoesNotAnswerFallsToThePin() {
        assertEquals(host, choose(installer = "com.old.host", answering = listOf(host)))
        assertNull(choose(installer = "com.old.host", answering = listOf(stranger)))
    }

    @Test fun twoTrustedHostsGiveAStableChoice() {
        val other = "com.another.host"
        val certs = mapOf(host to listOf(hostKey), other to listOf(hostKey))
        assertEquals(other, choose(installer = self, answering = listOf(host, other), certs = certs))
        assertEquals(other, choose(installer = self, answering = listOf(other, host), certs = certs))
    }
}
