package com.example.bluehive.details.moviesdetails

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.bluehive.models.MediaItem
import com.example.bluehive.repository.RecommendationsRepository
import androidx.activity.OnBackPressedCallback
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.details.shared.navigation.DetailsScreen
import com.example.bluehive.details.shared.navigation.DetailsIntents
import com.example.bluehive.details.shared.navigation.DetailsBackNavigation



class MoviesDetailsScreenCompose : ComponentActivity() {
    private lateinit var args: MovieDetailsArgs


    companion object {
        private const val TAG = "MoviesDetailsScreenCompose"
    }

    fun openMediaDetails(media: MediaItem) {
        DetailsBackNavigation.pushCurrent(
            this,
            args.toHistoryItem(),
            media,
        )

        val intent = DetailsIntents.open(
            this, MoviesDetailsScreenCompose::class.java, DetailsScreen.Movie,
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
                    this@MoviesDetailsScreenCompose, MoviesDetailsScreenCompose::class.java, DetailsScreen.Movie,
                    args.profileId, args.sourceScreen, args.searchQuery,
                )
            }
        })


        // Extract media data from intent
        args = MovieDetailsArgs.from(intent)

        val recommendationsRepository = RecommendationsRepository()


        // ✅ Debug logging to see what we received
        Log.d(TAG, "📝 Intent extras received:")
        Log.d(TAG, "  mediaTitle: ${args.mediaTitle}")
        Log.d(TAG, "  mediaType: ${args.mediaType}")
        Log.d(TAG, "  mediaId: ${args.mediaId}")
        Log.d(TAG, "  backdropUrl: ${args.backdropUrl}")
        Log.d(TAG, "  trailerUrl: ${args.trailerUrl}")
        Log.d(TAG, "  logoUrl (raw): '${args.logoUrl}'")
        Log.d(TAG, "  logoUrl isEmpty: ${args.logoUrl.isNullOrEmpty()}")
        Log.d(TAG, "  logoUrl isBlank: ${args.logoUrl.isNullOrBlank()}")

        Log.d(TAG, "Received media: ${args.mediaTitle} (ID: ${args.mediaId}, Type: ${args.mediaType})")

        Log.d(TAG, "🔍 Intent extras:")
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            Log.d(TAG, "  $key: $value (type: ${value?.javaClass?.simpleName})")
        }

        setContent {
            MovieDetailsScreen(
                args = args,
                recommendationsRepository = recommendationsRepository,
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
