package com.example.bluehive.update

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * The two first-open setup steps (v656), mirroring Off-Grid Drive 1.62:
 *
 *   1. REQUIRED  "Install unknown apps" for BlueHive, so updates can install.
 *                Before v656 this was only asked the first time an update
 *                arrived, as an extra stop in the middle of the update.
 *   2. OPTIONAL  "Display over other apps", the only thing Android 10+ accepts
 *                for reopening BlueHive after a self-update (PackageReplacedReceiver).
 *                Shown once, always with Not now.
 *
 * NEITHER STEP ON FIRE TV. Fire OS hides "Install unknown apps" behind Developer
 * Options and answers the overlay link with a placeholder screen
 * (CTSDummyIntentHandler), so a required step there could trap a viewer on a dead
 * button. Fire TV keeps the old behaviour: the update screen asks when it has to.
 * The same placeholder test guards every other TV: a step is only offered when its
 * settings link leads to a real screen.
 */
object SetupSteps {

    private const val PREFS = "bh_setup_steps"
    private const val REOPEN_ASKED = "reopen_asked"

    /** AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES is @hide; see SelfUpdateActivity. */
    private const val OP_REQUEST_INSTALL_PACKAGES = "android:request_install_packages"

    fun isFireTv(context: Context): Boolean =
        Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")

    /**
     * BlueHive may install APKs. Reads the appop, like SelfUpdateActivity, because
     * canRequestPackageInstalls() answered true on the onn box while the switch was off.
     */
    fun canInstallApps(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(OP_REQUEST_INSTALL_PACKAGES, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(OP_REQUEST_INSTALL_PACKAGES, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(true)
    }

    /** "Display over other apps" is on, or Android is old enough not to need it. */
    fun canReopenAfterUpdates(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || runCatching { Settings.canDrawOverlays(context) }.getOrDefault(true)

    fun installSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** The link opens a real settings screen (not missing, not Fire OS's placeholder). */
    private fun leadsSomewhere(context: Context, intent: Intent): Boolean {
        val target = runCatching { intent.resolveActivity(context.packageManager) }.getOrNull() ?: return false
        return !target.className.contains("Dummy", ignoreCase = true)
    }

    /** Step 1 is due: Android 8+, not Fire TV, switch off, and the switch can be reached. */
    fun installStepDue(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            // v659: the host app installs the updates, so BlueHive needs no switch.
            !HostUpdater.available(context) &&
            !isFireTv(context) &&
            !canInstallApps(context) &&
            leadsSomewhere(context, installSettingsIntent(context))

    /**
     * Step 2 can be offered here: Android 10+ (older TVs reopen without it), not Fire
     * TV, not a low-RAM device (Android hides the setting there), real settings link.
     */
    fun reopenStepAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        if (isFireTv(context)) return false
        if (context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true) return false
        return leadsSomewhere(context, overlaySettingsIntent(context))
    }

    /** Step 2 is due: available, switch off, and never shown before. */
    fun reopenStepDue(context: Context): Boolean =
        !prefs(context).getBoolean(REOPEN_ASKED, false) &&
            // v659: the host app reopens BlueHive from the foreground after an update.
            !HostUpdater.available(context) &&
            reopenStepAvailable(context) &&
            !canReopenAfterUpdates(context)

    /** Step 2 has been on screen: never show it again. */
    fun markReopenAsked(context: Context) {
        prefs(context).edit().putBoolean(REOPEN_ASKED, true).apply()
    }

    /** Either step is due, so the front door routes to SetupStepsActivity first. */
    fun anyDue(context: Context): Boolean = installStepDue(context) || reopenStepDue(context)

    /**
     * This copy was installed fresh rather than updated over an older BlueHive.
     * An update to v656 shows step 2 as "Just updated" instead of "Step 2 of 2".
     */
    fun freshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
