package com.example.bluehive.utilities

import android.content.Context
import android.util.Log
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.ProfileResponse
import com.example.bluehive.api.ProfileSoundRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Interface sounds per profile -- what Settings > Sounds edits and what
 * BlueHiveApplication.play() obeys. Media playback never reads any of this.
 *
 * THE SERVER IS THE RECORD; THIS TV KEEPS A COPY. The settings live on the
 * profile row (bluehive.profiles.ui_sound_*), so they follow the profile to
 * every TV and survive delete + recover. The local copy is what makes them
 * apply instantly: on a cold start, on the picker, and before any request has
 * answered.
 *
 * THE PICKER PLAYS THE LAST PROFILE'S SOUNDS. Nobody is on a profile there,
 * but somebody is holding the remote, and it is most likely whoever used this
 * TV last -- so a muted household stays muted on the way back in.
 *
 * A CHANGE MADE OFFLINE IS NOT LOST. It is saved here and marked dirty; the
 * next time the server's copy arrives, the dirty local copy is sent instead of
 * being overwritten by the stale one.
 */
object SoundPrefs {
    private const val TAG = "SoundPrefs"
    private const val FILE = "ui_sounds"
    private const val KEY_LAST = "last_profile"
    private const val SAVE_DELAY_MS = 600L

    /** Which switch on the Sounds page a sample answers to. */
    enum class Kind { NAV, CLICK, ERROR }

    data class Settings(
        val volume: Int = 100,          // 0..100
        val muted: Boolean = false,
        val nav: Boolean = true,
        val click: Boolean = true,
        val error: Boolean = true,
    ) {
        fun encode() = "$volume,${muted.b()},${nav.b()},${click.b()},${error.b()}"
        private fun Boolean.b() = if (this) 1 else 0

        companion object {
            fun decode(s: String?): Settings? = runCatching {
                val p = s!!.split(',')
                Settings(p[0].toInt().coerceIn(0, 100), p[1] == "1", p[2] == "1", p[3] == "1", p[4] == "1")
            }.getOrNull()
        }
    }

    /** What play() reads. Defaults are today's behaviour until something loads. */
    @Volatile var current: Settings = Settings()
        private set

    /** The profile [current] belongs to, and its name for the Settings screens. */
    @Volatile var currentProfileId: Int = -1
        private set
    @Volatile var currentProfileName: String = ""
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSave: Job? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun cached(context: Context, profileId: Int): Settings? =
        Settings.decode(prefs(context).getString("p$profileId", null))

    private fun isDirty(context: Context, profileId: Int) =
        prefs(context).getBoolean("dirty.p$profileId", false)

    /** App start: take the last profile's sounds, so the picker matches them. */
    fun init(context: Context) {
        val last = prefs(context).getInt(KEY_LAST, -1)
        if (last > 0) {
            currentProfileId = last
            current = cached(context, last) ?: Settings()
        }
    }

    /** A profile was entered (picker, or the home screen being restored). */
    fun activate(context: Context, profileId: Int, name: String? = null) {
        if (profileId <= 0) return
        currentProfileId = profileId
        if (!name.isNullOrBlank()) currentProfileName = name
        current = cached(context, profileId) ?: Settings()
        prefs(context).edit().putInt(KEY_LAST, profileId).apply()
    }

    /**
     * The server's copy of a profile arrived (picker list, select, save).
     *
     * A response from a server that predates the sound columns carries none of
     * the fields; it is ignored rather than read as "volume 0, everything off".
     */
    fun fromServer(context: Context, p: ProfileResponse) {
        val v = p.ui_sound_volume ?: return
        if (isDirty(context, p.id)) {
            // A change this TV could not send yet: push it, do not overwrite it.
            cached(context, p.id)?.let { s -> scope.launch { push(context, p.id, s) } }
            return
        }
        val s = Settings(
            volume = v.coerceIn(0, 100),
            muted = p.ui_sound_muted ?: false,
            nav = p.ui_sound_nav ?: true,
            click = p.ui_sound_click ?: true,
            error = p.ui_sound_error ?: true,
        )
        prefs(context).edit().putString("p${p.id}", s.encode()).apply()
        if (p.id == currentProfileId) current = s
    }

    fun fromServer(context: Context, profiles: List<ProfileResponse>) =
        profiles.forEach { fromServer(context, it) }

    /**
     * The Sounds page changed something. Applies at once; the server hears after
     * [SAVE_DELAY_MS] of quiet, so holding the volume key sends one request.
     */
    fun update(context: Context, settings: Settings) {
        val id = currentProfileId
        current = settings
        if (id <= 0) return
        prefs(context).edit()
            .putString("p$id", settings.encode())
            .putBoolean("dirty.p$id", true)
            .apply()
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(SAVE_DELAY_MS)
            push(context, id, settings)
        }
    }

    /** Leaving the Sounds page: send now rather than after the delay. */
    fun flush(context: Context) {
        val id = currentProfileId
        if (id <= 0 || !isDirty(context, id)) return
        pendingSave?.cancel()
        val s = cached(context, id) ?: return
        pendingSave = scope.launch { push(context, id, s) }
    }

    private suspend fun push(context: Context, profileId: Int, s: Settings) {
        runCatching {
            ApiClient.bluehiveApi.setProfileSound(
                profileId,
                ProfileSoundRequest(s.volume, s.muted, s.nav, s.click, s.error),
            )
        }.onSuccess {
            // Only if nothing newer was saved while this request was in flight.
            if (cached(context, profileId) == s) {
                prefs(context).edit().putBoolean("dirty.p$profileId", false).apply()
            }
        }.onFailure {
            Log.w(TAG, "sound settings not saved yet for profile $profileId: ${it.message}")
        }
    }

    // ── What play() asks ─────────────────────────────────────────────────────

    /** Volume multiplier for every interface sound: 0 when muted. */
    val gain: Float get() = current.let { if (it.muted) 0f else it.volume / 100f }

    fun allows(kind: Kind): Boolean = current.let {
        !it.muted && it.volume > 0 && when (kind) {
            Kind.NAV -> it.nav
            Kind.CLICK -> it.click
            Kind.ERROR -> it.error
        }
    }
}
