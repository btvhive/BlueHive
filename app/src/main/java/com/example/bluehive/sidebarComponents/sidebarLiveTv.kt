package com.example.bluehive.sidebarComponents

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.livetv.LivePlayerActivity
import com.example.bluehive.livetv.LiveTvScreen

// ─────────────────────────────────────────────────────────────────────────────
//  Live TV. The Activity keeps the name and place the sidebar and the manifest
//  already use; the page (design C), its data and the player live in livetv/.
// ─────────────────────────────────────────────────────────────────────────────

fun openLiveTvScreen(context: Context, profileId: Int = -1) {
    context.startActivity(
        Intent(context, LiveTvScreenActivity::class.java)
            .putExtra("PROFILE_ID", profileId)
    )
}

class LiveTvScreenActivity : ComponentActivity() {

    /** Bumped each time the page comes back from the player. */
    private var resumeTick by mutableIntStateOf(0)
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                BlueHiveApplication.playBackOutSound()
                finish()
            }
        })

        val profileId = intent.getIntExtra("PROFILE_ID", -1)
        setContent {
            LiveTvScreen(profileId, resumeTick) { row, index ->
                LivePlayerActivity.start(this, profileId, row, index)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) resumeTick++ else resumedOnce = true
    }
}
