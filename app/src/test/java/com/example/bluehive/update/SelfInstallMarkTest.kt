package com.example.bluehive.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who reopens BlueHive after an update (v665): BlueHive itself only after an update
 * it installed itself; after a host's install the host opens it (Android 9 "loads
 * twice", Fire TV Stick 4K Max, 2026-11-06).
 */
class SelfInstallMarkTest {

    @Test fun reopensAfterItsOwnInstall() =
        assertTrue(SelfInstallMark.shouldReopen(pendingSelfInstallVersion = 665, installedVersion = 665, canReopen = true))

    /** The host installed it: no mark, the host opens BlueHive. */
    @Test fun doesNotReopenAfterAHostInstall() =
        assertFalse(SelfInstallMark.shouldReopen(pendingSelfInstallVersion = 0, installedVersion = 665, canReopen = true))

    /** A self-install of 665 that failed, then the host installed 666: not ours. */
    @Test fun anOldMarkForAnotherVersionDoesNotCount() {
        assertFalse(SelfInstallMark.shouldReopen(665, 666, true))
        assertFalse(SelfInstallMark.shouldReopen(666, 665, true))
    }

    @Test fun neverWhenAndroidBlocksIt() =
        assertFalse(SelfInstallMark.shouldReopen(665, 665, canReopen = false))

    @Test fun anUnreadableVersionNeverCounts() =
        assertFalse(SelfInstallMark.shouldReopen(pendingSelfInstallVersion = 0, installedVersion = 0, canReopen = true))
}
