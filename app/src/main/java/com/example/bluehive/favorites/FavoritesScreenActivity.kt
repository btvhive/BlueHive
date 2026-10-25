package com.example.bluehive.favorites

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.bluehive.BlueHiveApplication


// ─────────────────────────────────────────────────────────────────────────────
//  Entry point
// ─────────────────────────────────────────────────────────────────────────────

fun openFavoritesScreen(context: Context, profileId: Int = -1) {
    context.startActivity(
        Intent(context, FavoritesScreenActivity::class.java)
            .putExtra("PROFILE_ID", profileId)
    )
}

/**
 * Shows FavoritesScreen. Code outside favorites/ may use only this class and
 * openFavoritesScreen (checkDetailsBoundaries).
 *
 * Moved here from sidebarComponents/SidebarFavorites.kt in Stage F1 of the
 * Favorites and Home redesign (docs/favorites-home-redesign-architecture.md).
 */
class FavoritesScreenActivity : ComponentActivity() {

    private val refreshTrigger = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                BlueHiveApplication.playBackOutSound()
                finish()
            }
        })

        val profileId = intent.getIntExtra("PROFILE_ID", -1)
        setContent {
            FavoritesScreen(profileId = profileId, refreshTrigger = refreshTrigger.intValue)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++
    }

    override fun onPause() {
        super.onPause()
        // glSurfaceView lifecycle handled by VanishContainer internally
    }
}
