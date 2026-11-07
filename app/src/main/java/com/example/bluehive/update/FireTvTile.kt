package com.example.bluehive.update

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * v666: blank Fire TV home tile after an update.
 *
 * Fire TV draws a sideloaded app's tile from the Amazon Appstore's own record of the
 * app, not from our icon. On an update the Appstore sometimes reads the app while
 * Fire OS's second install stage has it half removed ("SideloadedAppManagerService:
 * cannot update entry ... NameNotFoundException") and never retries, so the tile
 * stays blank until a reboot. Sending the Appstore Amazon's own "package removed"
 * then "package added" rebuilds the record and the logo returns within seconds
 * (proven on the Fire TV Stick 4K Max, 2026-11-06; the host app does the same since
 * its 1.79). Sent after every update, also when the host installed it: hosts older
 * than 1.79 don't send it, and a second send only refreshes the row once more.
 */
object FireTvTile {
    private const val TAG = "FireTvTile"
    private val APPSTORE_RECEIVER =
        ComponentName("com.amazon.venezia", "com.amazon.mas.client.apps.AppManagerBroadcastReceiver")
    private const val REMOVED = "com.amazon.tv.action.PACKAGE_REMOVED"
    private const val ADDED = "com.amazon.tv.action.PACKAGE_ADDED"

    /** After the Appstore's own (possibly failed) pass at the update. */
    private const val FIRST_DELAY_MS = 4_000L
    private const val ADDED_DELAY_MS = 2_000L

    private fun isFireTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")

    /** Asks the Appstore to rebuild this app's tile; [done] runs when finished. */
    fun refresh(context: Context, done: () -> Unit = {}) {
        if (!isFireTv(context)) { done(); return }
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({
            send(app, REMOVED)
            main.postDelayed({
                send(app, ADDED)
                done()
            }, ADDED_DELAY_MS)
        }, FIRST_DELAY_MS)
    }

    // Log.e: release builds strip d/i/w.
    private fun send(context: Context, action: String) {
        try {
            context.sendBroadcast(
                Intent(action, Uri.fromParts("package", context.packageName, null)).setComponent(APPSTORE_RECEIVER)
            )
            Log.e(TAG, "Sent $action to the Appstore.")
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't send $action: ${e.message}")
        }
    }
}
