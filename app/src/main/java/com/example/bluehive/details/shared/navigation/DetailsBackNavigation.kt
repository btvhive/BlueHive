package com.example.bluehive.details.shared.navigation

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.SourceScreen
import com.example.bluehive.models.MediaItem
import com.example.bluehive.searchBarComponent.SearchBarActivity
import com.example.bluehive.sidebarComponents.FavoritesScreenActivity
import com.example.bluehive.sidebarComponents.HistoryScreenActivity

/**
 * Back on a details page, and the history stack behind it: picking a
 * recommendation pushes this page's title, and Back pops it -- or, with the
 * stack empty, returns to the screen the page was opened from (History,
 * Favorites, Search), or finishes to Home.
 *
 * Moved here from the two Activities in Stage 9 of the details redesign; both
 * did exactly this. This file may not name the screens
 * (checkDetailsBoundaries), so each Activity passes its own class as [target].
 */
object DetailsBackNavigation {

    /** Before opening [opening] from this page: this page's title ([current]) goes on the history stack. */
    fun pushCurrent(activity: Activity, current: MediaItem, opening: MediaItem) {
        val app = activity.application as BlueHiveApplication
        app.pushDetailsHistory(current)
        Log.d(
            "DetailsHistory",
            "Details -> openMediaDetails: pushed '${opening.title}', size=${app.historySize()}"
        )
    }

    /** ✅ Handle system back with sound + history stack. */
    fun goBack(
        activity: Activity,
        target: Class<out Activity>,
        screen: DetailsScreen,
        profileId: Int,
        sourceScreen: String?,
        searchQuery: String?,
    ) {
        BlueHiveApplication.playBackOutSound()

        val app = activity.application as BlueHiveApplication
        val previous = app.popPreviousDetails()

        if (previous == null) {
            when (sourceScreen) {
                SourceScreen.HISTORY -> {
                    Log.d("DetailsHistory", "Back: returning to History")
                    activity.startActivity(
                        Intent(activity, HistoryScreenActivity::class.java)
                            .putExtra("PROFILE_ID", profileId)
                    )
                    activity.finish()
                    return
                }
                SourceScreen.FAVORITES -> {
                    Log.d("DetailsHistory", "Back: returning to Favorites")
                    activity.startActivity(
                        Intent(activity, FavoritesScreenActivity::class.java)
                            .putExtra("PROFILE_ID", profileId)
                    )
                    activity.finish()
                    return
                }
                SourceScreen.SEARCH -> {
                    Log.d("DetailsHistory", "Back: returning to Search")
                    activity.startActivity(
                        Intent(activity, SearchBarActivity::class.java)
                            .putExtra("PROFILE_ID",    profileId)
                            .putExtra("INITIAL_QUERY", searchQuery ?: "")
                    )
                    activity.finish()
                    return
                }
                else -> {
                    Log.d("DetailsHistory", "Back: no previous details, finishing to Home")
                    activity.finish()
                    return
                }
            }
        }

        Log.d("DetailsHistory", "Back: navigating to previous '${previous.title}'")

        val intent = DetailsIntents.back(
            activity, target, screen,
            previous, profileId, sourceScreen, searchQuery,
        )
        activity.startActivity(intent)
        activity.finish()
    }
}
