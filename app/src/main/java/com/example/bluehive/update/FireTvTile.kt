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

    // ── v677: once per install or update, whatever installed it ───────────────────
    //
    // Until v677 only the update receiver asked, so a tile stayed blank when nothing
    // ran it: a FRESH install (2026-11-08, AFTKA .225: reinstalled by the host after
    // an un-pair, logo missing until the next update), or an update whose process
    // died within the ~6 s of delays. Now app start asks too, and only the finished
    // send is remembered, so a send that never happened is retried at the next start.

    private const val PREFS = "firetv_tile"
    private const val KEY_DONE_FOR = "done_for_update_time"
    private var inFlight = false                      // main thread only
    private val waiters = mutableListOf<() -> Unit>() // main thread only

    /** Whether a send is due: [installStamp] = lastUpdateTime, [doneFor] = the stamp last sent for. */
    fun needsSend(installStamp: Long, doneFor: Long): Boolean = installStamp > 0L && installStamp != doneFor

    /**
     * [refresh], unless it was already done since this build was installed (keyed by
     * the package's lastUpdateTime). Main thread; [done] runs when it is finished.
     */
    fun refreshOnce(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        if (!isFireTv(app)) { done(); return }
        val stamp = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime }
            .getOrDefault(0L)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!needsSend(stamp, prefs.getLong(KEY_DONE_FOR, 0L))) { done(); return }
        waiters += done
        if (inFlight) return
        inFlight = true
        refresh(app) {
            prefs.edit().putLong(KEY_DONE_FOR, stamp).apply()
            inFlight = false
            val all = waiters.toList()
            waiters.clear()
            all.forEach { it() }
        }
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
