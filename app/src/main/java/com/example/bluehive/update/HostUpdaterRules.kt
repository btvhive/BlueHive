package com.example.bluehive.update

/**
 * Which app may install BlueHive's updates (v664), free of Android so it can be
 * unit-tested (app/src/test/.../update/HostUpdaterRulesTest.kt).
 *
 * WHY MORE THAN THE INSTALLER OF RECORD. Up to v663 only the app that last
 * installed BlueHive counted. But whenever BlueHive falls back to updating itself,
 * Android records BlueHive as its own installer, and from then on that TV never
 * used the host again: setup switches back, Android's question on every update.
 * Now, when the installer of record is not a host, an app that answers the update
 * action is used IF it is signed with a trusted host key. Its first install asks
 * "Install?" once (Android only skips that for the installer of record); after
 * that it is the installer again and updates are silent.
 *
 * WHY A SIGNING-KEY PIN. Any app can declare the action. Without the pin a stranger
 * app could receive every update request and simply never install (it could not
 * install a fake BlueHive: Android refuses a different signing key).
 */
object HostUpdaterRules {

    /**
     * The host to hand updates to, or null to update ourselves.
     *
     * @param self BlueHive's package.
     * @param installerOfRecord what Android says installed BlueHive (may be self).
     * @param answering every other package that answers the update action.
     * @param certsOf the SHA-256 signing-certificate digests of each answering package.
     * @param trustedCerts host keys trusted when the host is NOT the installer of record.
     */
    fun chooseHost(
        self: String,
        installerOfRecord: String?,
        answering: Collection<String>,
        certsOf: Map<String, Collection<String>>,
        trustedCerts: Set<String>,
    ): String? {
        val candidates = answering.filter { it != self }.toSet()
        // 1. The installer of record, as before: Android vouches for it.
        if (!installerOfRecord.isNullOrBlank() && installerOfRecord != self && installerOfRecord in candidates) {
            return installerOfRecord
        }
        // 2. Otherwise only an app signed with a trusted host key. Sorted, so the
        //    choice is stable if (unusually) two are installed.
        val trusted = trustedCerts.map { it.lowercase() }.toSet()
        return candidates.sorted().firstOrNull { pkg ->
            certsOf[pkg].orEmpty().any { it.lowercase() in trusted }
        }
    }
}
