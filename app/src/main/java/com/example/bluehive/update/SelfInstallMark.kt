package com.example.bluehive.update

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Who reopens BlueHive after an update (v665).
 *
 * Up to v664 PackageReplacedReceiver reopened BlueHive after EVERY update. When
 * Off-Grid Drive installs the update it also opens BlueHive, so on Android 9 (where
 * the receiver may start screens) BlueHive started twice: "loads twice", seen on a
 * Fire TV Stick 4K Max on 2026-11-06. Now the receiver reopens BlueHive only after
 * an update BlueHive installed ITSELF: the self-update path marks the version it is
 * about to install, and the receiver checks the newly installed version against it.
 */
object SelfInstallMark {

    private const val PREFS = "bh_self_install"
    private const val PENDING = "pending_version"

    /** The decision, free of Android (unit-tested). */
    fun shouldReopen(pendingSelfInstallVersion: Int, installedVersion: Int, canReopen: Boolean): Boolean =
        canReopen && pendingSelfInstallVersion > 0 && pendingSelfInstallVersion == installedVersion

    /** UpdateManager.install, just before commit: BlueHive itself installs [apk]. */
    fun markPending(context: Context, apk: File) {
        val version = runCatching {
            val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
            if (info == null) 0 else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
        }.getOrDefault(0)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(PENDING, version).commit()
    }

    /** PackageReplacedReceiver: the version BlueHive meant to install itself, then forgotten. */
    fun take(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = p.getInt(PENDING, 0)
        p.edit().remove(PENDING).commit()
        return v
    }
}
