package com.example.bluehive.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v677: the Fire TV tile is repaired once per install or update, from app start too. */
class FireTvTileOnceTest {

    @Test fun freshInstallNeverSentBefore_sends() =
        assertTrue(FireTvTile.needsSend(installStamp = 1_762_600_000_000L, doneFor = 0L))

    @Test fun sameBuildAlreadySent_doesNotSendAgain() =
        assertFalse(FireTvTile.needsSend(installStamp = 1_762_600_000_000L, doneFor = 1_762_600_000_000L))

    @Test fun updatedSinceLastSend_sends() =
        assertTrue(FireTvTile.needsSend(installStamp = 1_762_700_000_000L, doneFor = 1_762_600_000_000L))

    @Test fun reinstallWithOlderStampStillSends() =
        assertTrue(FireTvTile.needsSend(installStamp = 1_762_500_000_000L, doneFor = 1_762_600_000_000L))

    @Test fun unknownInstallTime_doesNotSend() =
        assertFalse(FireTvTile.needsSend(installStamp = 0L, doneFor = 0L))
}
