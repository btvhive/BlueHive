package com.example.bluehive.livetv

import com.example.bluehive.api.LiveChannelDto
import com.example.bluehive.api.LiveRowDto

/**
 * Local News (2026-11-01): the server's "Local News" row holds every local news
 * station it knows (Vizio's free list), each with the states it serves, home
 * state first. The TV shows the stations of the state its viewer picked --
 * once, kept on the TV -- from a state pill beside the search bar (design C of
 * live_tv_state_entry_designs_abcd, the user's pick). Pure, so the JVM tests
 * call it.
 */
internal object LocalNews {

    const val ROW = "Local News"

    /** The longest state name the pill shows in full; longer ones show their code ("NC"). */
    private const val PILL_NAME_MAX = 9

    /** A state the picker offers: how many stations serve it, and a few of their names. */
    data class StateChoice(val code: String, val name: String, val stations: Int, val examples: List<String>)

    private val STATE_NAMES = mapOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
        "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "DC" to "Washington, D.C.",
        "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois",
        "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana",
        "ME" to "Maine", "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota",
        "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada",
        "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York",
        "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma", "OR" to "Oregon",
        "PA" to "Pennsylvania", "PR" to "Puerto Rico", "RI" to "Rhode Island", "SC" to "South Carolina",
        "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont",
        "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
    )

    fun stateName(code: String): String = STATE_NAMES[code] ?: code

    /** The pill's words: "New York", "NC" for a name too long for it, "Pick state" before one is picked. */
    fun pillLabel(code: String?): String = when {
        code == null -> "Pick state"
        stateName(code).length <= PILL_NAME_MAX -> stateName(code)
        else -> code
    }

    /**
     * The row's stations for [state], working ones first as in every row; inside
     * that, the state's own stations, then neighbours' that also serve it, then
     * the ones for everyone. Nothing until a state is picked.
     */
    fun stationsFor(row: LiveRowDto, state: String?): List<LiveChannelDto> {
        if (state == null) return emptyList()
        return row.channels
            .filter { it.states.isNullOrEmpty() || state in it.states }
            .sortedWith(compareBy({ statusRank(it.status) }, { reach(it, state) }))
    }

    private fun statusRank(status: String?) = when (status) {
        "ok" -> 0
        "off" -> 2
        else -> 1
    }

    private fun reach(c: LiveChannelDto, state: String) = when {
        c.states.isNullOrEmpty() -> 2
        c.states.firstOrNull() == state -> 0
        else -> 1
    }

    /** Every state some station serves, A-Z by name. */
    fun choices(row: LiveRowDto?): List<StateChoice> {
        val stations = row?.channels.orEmpty()
        return stations.flatMap { it.states.orEmpty() }.toSet().map { code ->
            val serving = stations.filter { code in it.states.orEmpty() }.sortedBy { reach(it, code) }
            StateChoice(code, stateName(code), serving.size, serving.take(3).map { it.name })
        }.sortedBy { it.name }
    }

    /** "Cleveland 19 News, News 5, FOX19 Now Cincinnati". */
    fun examples(choice: StateChoice): String = choice.examples.joinToString(", ")

    fun stationsLabel(n: Int): String = if (n == 1) "1 station" else "$n stations"
}
