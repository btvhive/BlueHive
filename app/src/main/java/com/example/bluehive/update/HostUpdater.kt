package com.example.bluehive.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * HAND THE UPDATE TO THE HOST APP THAT INSTALLED BLUEHIVE (v659).
 *
 * When BlueHive was installed by a host app that offers [ACTION] (Off-Grid Drive
 * 1.63+), the host downloads the update from its own record of where BlueHive came
 * from (GitHub), checks it, installs it without Android's "Install this update?"
 * question, and opens BlueHive again. So BlueHive needs neither "Install unknown
 * apps" nor "Display over other apps" of its own, and SetupSteps skips both.
 *
 * BlueHive still DECIDES: its own server, its own min_supported, its own update
 * screen. Only the install moves. The host gets the version and the file's sha256,
 * never a link.
 *
 * NAMED BY CAPABILITY, not by product, like io.host.entitlement.ASSERTION: any host
 * implementing the same action works, and nothing here ties BlueHive to a vendor.
 *
 * FALLS BACK to the self-update path whenever the host can't help: no host, an old
 * host without the action, or the host replying "unavailable" (no release, wrong
 * file) through [EXTRA_ON_UNAVAILABLE].
 */
object HostUpdater {

    private const val TAG = "HostUpdater"

    const val ACTION = "io.host.addon.action.INSTALL_UPDATE"
    private const val EXTRA_VERSION_CODE = "io.host.addon.extra.VERSION_CODE"
    private const val EXTRA_VERSION_NAME = "io.host.addon.extra.VERSION_NAME"
    private const val EXTRA_SHA256 = "io.host.addon.extra.SHA256"
    private const val EXTRA_ON_UNAVAILABLE = "io.host.addon.extra.ON_UNAVAILABLE"
    private const val EXTRA_ON_CANCELLED = "io.host.addon.extra.ON_CANCELLED"
    private const val EXTRA_ON_FAILED = "io.host.addon.extra.ON_FAILED"

    /**
     * Signing keys (SHA-256 of the certificate) of hosts trusted to install
     * BlueHive's updates when they are NOT its installer of record (v664): after a
     * fallback self-update, Android lists BlueHive as its own installer. A key, not
     * a product name. Add the new digest here before a host rotates its key.
     */
    private val TRUSTED_HOST_CERTS = setOf(
        "409e0139a3b89df9a9b0d6a36f19c8c37db47bd91d36f4fce40b12815e4eea1b",
    )

    /**
     * The app that will install BlueHive's updates, or null to update ourselves:
     * the installer of record when it offers [ACTION], else an app offering it that
     * is signed with a trusted host key (HostUpdaterRules).
     */
    fun hostPackage(context: Context): String? {
        val pm = context.packageManager
        val self = context.packageName
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(self).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(self)
        }.getOrNull()
        val answering = runCatching {
            pm.queryIntentActivities(Intent(ACTION), 0).mapNotNull { it.activityInfo?.packageName }.distinct()
        }.getOrDefault(emptyList())
        if (answering.isEmpty()) return null
        val certs = answering.filter { it != self && it != installer }.associateWith { signingCertDigests(context, it) }
        return HostUpdaterRules.chooseHost(self, installer, answering, certs, TRUSTED_HOST_CERTS)
    }

    /** SHA-256 digests of [pkg]'s signing certificates (Android 9 fallback included). */
    private fun signingCertDigests(context: Context, pkg: String): List<String> = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        fun legacy() = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.toList()?.takeIf { it.isNotEmpty() } ?: legacy()
        } else legacy()
        sigs.map { s ->
            java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }.getOrDefault(emptyList())

    fun available(context: Context): Boolean = hostPackage(context) != null

    /**
     * Starts the host's update screen for [manifest]. True if it was started; the
     * caller then closes its own screen. False means: update yourself.
     */
    fun handOff(activity: Activity, manifest: UpdateManager.Manifest): Boolean {
        val host = hostPackage(activity) ?: return false
        if (!manifest.sha256.trim().matches(Regex("[0-9a-fA-F]{64}")) || manifest.latestVersionCode <= 0) return false

        // The host's answers come back as BlueHive's own update screen, so a host
        // that can't help never leaves the person anywhere strange.
        fun reply(requestCode: Int, fallback: Boolean, autoAccept: Boolean): PendingIntent =
            PendingIntent.getActivity(
                activity, requestCode,
                Intent(activity, SelfUpdateActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(SelfUpdateActivity.EXTRA_VERSION_CODE, manifest.latestVersionCode)
                    putExtra(SelfUpdateActivity.EXTRA_VERSION_NAME, manifest.latestVersionName)
                    putExtra(SelfUpdateActivity.EXTRA_APK_URL, manifest.apkUrl)
                    putExtra(SelfUpdateActivity.EXTRA_SHA256, manifest.sha256)
                    putExtra(SelfUpdateActivity.EXTRA_MIN_SUPPORTED, manifest.minSupported)
                    putExtra(SelfUpdateActivity.EXTRA_HOST_FALLBACK, fallback)
                    putExtra(SelfUpdateActivity.EXTRA_AUTO_ACCEPT, autoAccept)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val request = Intent(ACTION).setPackage(host).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_VERSION_CODE, manifest.latestVersionCode)
            .putExtra(EXTRA_VERSION_NAME, manifest.latestVersionName)
            .putExtra(EXTRA_SHA256, manifest.sha256.trim().lowercase())
            // Unavailable: update yourself, at once (the person already said yes).
            .putExtra(EXTRA_ON_UNAVAILABLE, reply(7701, fallback = true, autoAccept = true))
            // Cancelled: back to the offer, the host may be asked again.
            .putExtra(EXTRA_ON_CANCELLED, reply(7702, fallback = false, autoAccept = false))
            // Failed (the host already said why): back to the offer, and a second
            // try goes the self-update way.
            .putExtra(EXTRA_ON_FAILED, reply(7703, fallback = true, autoAccept = false))
        return runCatching { activity.startActivity(request); true }
            .onFailure { Log.e(TAG, "host update screen didn't start: ${it.message}") }
            .getOrDefault(false)
    }

    /**
     * Android 14 lets an app claim "update ownership" of itself, after which any
     * other installer has to ask the person. BlueHive claimed it on every
     * self-update up to v658. With a host installing the updates, hand it back
     * once, so the host's installs stay silent. Cheap; call at start-up.
     */
    fun releaseSelfOwnership(context: Context) {
        if (Build.VERSION.SDK_INT < 34 || !available(context)) return
        runCatching {
            val pm = context.packageManager
            if (pm.getInstallSourceInfo(context.packageName).updateOwnerPackageName == context.packageName) {
                pm.relinquishUpdateOwnership(context.packageName)
                Log.e(TAG, "gave up update ownership of itself; the host installs updates now")
            }
        }.onFailure { Log.e(TAG, "couldn't give up update ownership: ${it.message}") }
    }
}
