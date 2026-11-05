package com.example.bluehive

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import com.example.bluehive.api.SessionExpiredBus
import com.example.bluehive.auth.ActiveProfile
import com.example.bluehive.auth.DeviceEventStream
import com.example.bluehive.auth.HostEntitlementRelay
import com.example.bluehive.auth.SessionManager
import com.example.bluehive.details.moviesdetails.MoviesDetailsScreenCompose
import com.example.bluehive.details.tvshowdetails.TVShowsDetailsScreenCompose
import com.example.bluehive.home.HomeScreen
import com.example.bluehive.models.MediaItem
import com.example.bluehive.update.UpdateGate
import com.example.bluehive.utilities.SoundPrefs


/**
 * The home screen's Activity. Its screen, HomeScreen, and everything it is
 * built from live in home/ since Stage H1 of the Favorites and Home redesign;
 * this is the only file outside home/ that may use them (checkDetailsBoundaries).
 */
class HomeScreenCompose : ComponentActivity() {

    private val refreshTrigger = mutableIntStateOf(0)

    private val sessionExpiredListener: () -> Unit = {
        returnToHost()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SessionManager.get().isAuthenticated) {
            returnToHost()
            return
        }

        (application as BlueHiveApplication).setAppState(
            BlueHiveApplication.AppState.HOME_ACTIVE
        )

        val profileId       = intent.getIntExtra("PROFILE_ID", -1)
        val profileName     = intent.getStringExtra("PROFILE_NAME") ?: ""
        val profileAvatarId = intent.getIntExtra("PROFILE_AVATAR_ID", R.drawable.avatar1)
        // Also set by the picker; this covers Android rebuilding the home screen
        // after killing the process, when the picker never ran.
        if (profileId > 0) {
            ActiveProfile.id = profileId
            // Same for the profile's interface sounds (SoundPrefs).
            SoundPrefs.activate(this, profileId, profileName)
        }

        setContent {
            HomeScreen(
                onMediaClick     = { media -> openDetailsScreen(media, profileId) },
                profileAvatarRes = profileAvatarId,
                profileId        = profileId,
                profileName      = profileName,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (application as BlueHiveApplication).setAppState(
            BlueHiveApplication.AppState.HOME_ACTIVE
        )
        SessionExpiredBus.register(sessionExpiredListener)
        if (!SessionManager.get().isAuthenticated) {
            returnToHost()
            return
        }
        refreshTrigger.intValue++

        // Re-check the host's account standing.
        //
        // THIS ONE HOOK COVERS EVERY RETURN PATH. Sidebar pages, watch history,
        // favorites, settings, the media catalog, a movie or show detail page, the
        // profile picker, and coming back from the TV launcher all resume this
        // activity — so there is no need to instrument each of them.
        //
        // Placed AFTER the auth guard on purpose: an unauthenticated session is on
        // its way out of the app and has nothing to report against.
        //
        // Rate-limited internally (3s floor — just long enough to collapse a
        // duplicate onResume) and self-cancelling when the token has not changed,
        // so bouncing in and out of a detail page costs nothing.
        HostEntitlementRelay.checkNow(this)

        // ── Is this TV still allowed to run this build? ──────────────────────
        //
        // Deliberately alongside the entitlement check rather than inside it.
        // They ask different questions of different servers and only one of them
        // can lock somebody out of a paid account; tangling them would mean an
        // update bug could take entitlement down with it.
        //
        // Same placement for the same reason though: every route back into the
        // app resumes this activity, and a cold start lands here after the
        // loading screen — so one hook covers reboot and navigation both.
        //
        // Rate limited internally, runs off the main thread, waits a couple of
        // seconds so the home screen is not yanked away the instant it appears,
        // and does nothing at all when the device is already current.
        UpdateGate.checkNow(this)
    }

    override fun onPause() {
        super.onPause()
        SessionExpiredBus.unregister(sessionExpiredListener)
    }

    // PHASE 2: under the host model BlueHive owns no pairing screen. A genuine
    // session loss (host reports revoked) means the HOST must handle re-pairing.
    // BlueHive's job is simply to stop and hand control back. We finishAffinity()
    // to tear down BlueHive's task; the user is dropped back to the host,
    // which owns the pairing front door. LoginScreenActivity is never launched.
    private fun returnToHost() {
        Log.w("HomeScreenCompose", "Session ended — returning to host")
        DeviceEventStream.stopAndMarkExited()
        finishAffinity()
    }




    private fun openDetailsScreen(media: MediaItem, profileId: Int) {
        val mediaType = media.mediaType  // Get from the MediaItem itself

        Log.d("HomeScreenCompose", "Opening ${mediaType.uppercase()} details for: ${media.title}")

        val intent = when (mediaType.lowercase()) {
            "tv" -> Intent(this, TVShowsDetailsScreenCompose::class.java)
            else -> Intent(this, MoviesDetailsScreenCompose::class.java)
        }.apply {
            putExtra("PROFILE_ID", profileId)
            putExtra("media_type", mediaType)
            putExtra("media_id", media.tmdbId)
            putExtra("media_title", media.title)
            putExtra("poster_url", media.posterUrl ?: "")
            putExtra("backdrop_url", media.backdropUrl ?: "")
            putExtra("youtube_trailer_url", media.trailerUrl ?: "")
            putExtra("overview", media.overview ?: "")
            putExtra("vote_average", media.voteAverage ?: 0.0)
            putExtra("contentRating", media.contentRating ?: "N/A")
            putExtra("original_language", media.originalLanguage ?: "N/A")
            putExtra("release_date", media.releaseDate ?: "N/A")
            putExtra("on_streaming", media.onStreaming == true)
            putExtra("logo_url", media.logoUrl ?: "")
            putExtra("genres", media.genres?.joinToString(", ") ?: "N/A")

            // TV-specific extras
            if (mediaType.lowercase() == "tv") {
                putExtra("number_of_seasons", media.numberOfSeasons ?: 0)
                putExtra("status", media.status ?: "N/A")
            } else {
                // Movie-specific extras
                putExtra("budget", media.budget ?: 0L)
                putExtra("revenue", media.revenue ?: 0L)
            }
        }

        startActivity(intent)
    }
}
