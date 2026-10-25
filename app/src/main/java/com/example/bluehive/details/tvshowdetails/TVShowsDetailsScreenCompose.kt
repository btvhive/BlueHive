package com.example.bluehive.details.tvshowdetails

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import androidx.activity.OnBackPressedCallback
import com.example.bluehive.repository.EpisodesRepository
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.details.shared.navigation.DetailsScreen
import com.example.bluehive.details.shared.navigation.DetailsIntents
import com.example.bluehive.details.shared.navigation.DetailsBackNavigation



class TVShowsDetailsScreenCompose: ComponentActivity() {


    private lateinit var args: TvShowDetailsArgs
    internal var currentSeasonForWatch: Int = 1
    internal var currentEpisodeForWatch: Int = 1
    internal var currentEpisodeNameForWatch: String? = null


    companion object {
        private const val TAG = "TVShowsDetailsScreenCompose"
    }

    fun openMediaDetails(media: MediaItem) {
        DetailsBackNavigation.pushCurrent(
            this,
            args.toHistoryItem(),
            media,
        )

        val intent = DetailsIntents.open(
            this, TVShowsDetailsScreenCompose::class.java, DetailsScreen.Tv,
            media, args.profileId, args.sourceScreen, args.searchQuery,
        )

        startActivity(intent)

        // ✅ Only one Details Activity alive at a time
        finish()
    }



    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as BlueHiveApplication).setAppState(
            BlueHiveApplication.AppState.DETAILS_VIEWING
        )

        // ✅ Handle system back with sound + history stack
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                DetailsBackNavigation.goBack(
                    this@TVShowsDetailsScreenCompose, TVShowsDetailsScreenCompose::class.java, DetailsScreen.Tv,
                    args.profileId, args.sourceScreen, args.searchQuery,
                )
            }
        })


        // Extract media data from intent
        args = TvShowDetailsArgs.from(intent)

        val recommendationsRepository = RecommendationsRepository()
        val episodesRepository = EpisodesRepository()


        setContent {
            TvShowDetailsScreen(
                args = args,
                recommendationsRepository = recommendationsRepository,
                episodesRepository = episodesRepository,
                onEpisodeSelected = { season, episode, epName ->
                    currentSeasonForWatch       = season
                    currentEpisodeForWatch      = episode
                    currentEpisodeNameForWatch  = epName
                },
                onMediaClick = { media ->
                    openMediaDetails(media)
                }
            )
        }
    }


    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            (application as BlueHiveApplication).setAppState(
                BlueHiveApplication.AppState.DETAILS_VIEWING
            )
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            (application as BlueHiveApplication).setAppState(
                BlueHiveApplication.AppState.HOME_ACTIVE
            )
        }
        super.onDestroy()
    }
}
