package com.example.bluehive.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.bluehive.auth.AuthActivity

/**
 * Fires after BlueHive is replaced by an update, and opens BlueHive again on the
 * new version where Android allows it (v656).
 *
 * Until v656 this only logged, on the assumption that the host app would reopen
 * BlueHive. No host does: Off-Grid Drive reopens itself, not its add-ons, so every
 * BlueHive update ended on the TV's home screen.
 *
 * ANDROID 10+ NEEDS "DISPLAY OVER OTHER APPS". A receiver has no window on screen,
 * and Android 10+ blocks it from starting an activity (BAL_BLOCK) unless the app
 * holds that permission. Off-Grid Drive measured the alternatives on the onn box
 * (Android 14, 2026-11-04): a direct start and an alarm-clock PendingIntent were
 * both blocked, and the install force-stops the app so no saved screen survives;
 * with the permission on, the app was back on screen 2 s after the install. Setup
 * step 2 (SetupStepsActivity) offers the switch. Android 9 and older have no such
 * block, so they always reopen.
 *
 * The relaunch carries EXTRA_SKIP_UPDATE_CHECK so the fresh build does not walk
 * straight back into the update flow.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // v665: only after an update BlueHive installed itself. When the host app
        // installed it, the host opens BlueHive; reopening here too started it
        // twice on Android 9 (SelfInstallMark). Log.e: release strips i/w.
        val pending = SelfInstallMark.take(context)
        if (!SelfInstallMark.shouldReopen(pending, com.example.bluehive.BuildConfig.VERSION_CODE, SetupSteps.canReopenAfterUpdates(context))) {
            Log.e(TAG, "Package replaced. Not reopening (self-install mark $pending, host-installed or Android blocks it).")
            return
        }
        Log.i(TAG, "Package replaced. Reopening (overlay permission on, or Android ${Build.VERSION.RELEASE}).")
        try {
            context.startActivity(
                Intent(context, AuthActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(AuthActivity.EXTRA_SKIP_UPDATE_CHECK, true)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Reopen not possible (${e.message}). The update is installed; open BlueHive from the apps row.")
        }
    }

    private companion object {
        const val TAG = "SelfUpdate"
    }
}
