package com.example.bluehive.sidebarComponents

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.livetv.LivePlayerActivity
import com.example.bluehive.livetv.LiveTvScreen
import com.example.bluehive.utilities.ConfirmationOverlay
import kotlinx.coroutines.android.awaitFrame

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

    // ASK BEFORE LEAVING LIVE TV (owner, 2026-11-07). Back that would close this
    // screen raises "Do you want to exit Live TV?" with Exit and Cancel instead
    // of closing straight away. Back that a page inside Live TV still uses (its
    // search page, for one) never gets here: those register their own Back
    // handlers after this activity's callback, so they are asked first.
    private var askExit by mutableStateOf(false)

    @OptIn(ExperimentalComposeUiApi::class)   // Modifier.focusRestorer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Only ever RAISES the question: while it is up, the BackHandler in
        // setContent (registered later, so it is asked first) treats Back as Cancel.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                askExit = true
            }
        })

        val profileId = intent.getIntExtra("PROFILE_ID", -1)
        setContent {
            // Cancel puts focus back on whatever the viewer was on (a channel,
            // a pill): the page is one focus group that remembers its last
            // focused item, and focus is handed back to the group once the
            // question has gone.
            val pageFocus = remember { FocusRequester() }
            var returnFocus by remember { mutableStateOf(false) }
            val cancel = { askExit = false; returnFocus = true }

            LaunchedEffect(returnFocus) {
                if (returnFocus) {
                    awaitFrame()
                    runCatching { pageFocus.requestFocus() }
                    returnFocus = false
                }
            }

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(pageFocus)
                        .focusRestorer()
                ) {
                    LiveTvScreen(profileId, resumeTick) { row, index ->
                        LivePlayerActivity.start(this@LiveTvScreenActivity, profileId, row, index)
                    }
                }

                BackHandler(enabled = askExit) { cancel() }

                if (askExit) {
                    ConfirmationOverlay(
                        message     = "Do you want to exit Live TV?",
                        confirmText = "Exit",
                        cancelText  = "Cancel",
                        onConfirm   = {
                            BlueHiveApplication.playBackOutSound()
                            finish()
                        },
                        onCancel    = cancel,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) resumeTick++ else resumedOnce = true
    }
}
