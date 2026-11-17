package com.example.bluehive.utilities

import android.os.Build

/**
 * The "snap to dust" animation (ThanosEffect: removing a Favorite, the episode
 * card's snap, the anime language row) needs Android 7: the library declares
 * minSdk 24 and has no older version (2026-11-17, Android 5 support).
 *
 * Below Android 7 nothing of it is composed or called. Things leave the way they
 * already do when the dust can't start (no renderer, a failed capture): the card
 * or row simply goes. From Android 7 on nothing changes.
 */
object SnapEffect {
    const val MIN_SDK = 24

    fun available(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= MIN_SDK
}
