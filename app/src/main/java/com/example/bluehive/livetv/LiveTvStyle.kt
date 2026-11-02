package com.example.bluehive.livetv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  Design C ("guide list"), the user's pick of 2026-10-28, from the approved
//  mockup live_tv_browse_designs_abc. Its 680 x 382 px frame is the TV's
//  960 x 540 dp, so EVERY mockup px is x 1.412 here (docs/live-tv-plan.md §4a).
//  Names (Asap Condensed Bold), meta and chips (Inter), buttons (Passion One).
// ─────────────────────────────────────────────────────────────────────────────

// ── Category chips (top row) ──────────────────────────────────────────────────
internal val CHIPS_LEFT = 22.6.dp            // 16 px
internal val CHIPS_TOP = 19.8.dp             // 14 px
internal val CHIP_HEIGHT = 31.1.dp           // 22 px
internal val CHIP_GAP = 8.5.dp               // 6 px
internal val CHIP_PAD_H = 15.5.dp            // 11 px
internal val CHIP_RADIUS = 8.5.dp            // 6 px
internal val CHIP_TEXT = 16.9.sp             // 12 px
internal val CHIP_ON = Color(0xFF2644A6)
internal val CHIP_OFF = Color(0xFF222340)
internal val CHIP_TEXT_OFF = Color(0xFFCFD3DC)

// ── Search bar (over the channel list; design B of live_tv_search_designs_abc) ─
internal val BAR_TOP = 70.6.dp               // 50 px
internal val BAR_HEIGHT = 39.5.dp            // 28 px
internal val BAR_RADIUS = 9.9.dp             // 7 px
internal val BAR_BORDER = 1.4.dp             // 1 px
internal val BAR_PAD_H = 14.1.dp             // 10 px
internal val BAR_GAP = 11.3.dp               // 8 px
internal val BAR_ICON = 18.4.dp              // 13 px
internal val BAR_TEXT = 16.9.sp              // 12 px
internal val BAR_BG = Color(0xFF0E0F14)
internal val BAR_EDGE = Color(0xFF2A2D37)
internal val BAR_INK = Color(0xFF8B90A0)

// ── Local News' state pill (design C of live_tv_state_entry_designs_abcd) ────
// In Local News the bar gives up its right end to the pill: Up to the bar, Right.
internal val BAR_LOCAL_WIDTH = 321.9.dp      // 228 px
internal val PILL_LEFT = 355.8.dp            // 252 px
internal val PILL_WIDTH = 135.6.dp           // 96 px, to the list's right edge
internal val PILL_GAP = 7.1.dp               // 5 px
internal val PILL_BG = Color(0x2E7CB342)     // Local News green at 18%
internal val PILL_EDGE = Color(0x997CB342)   // at 60%
internal val PILL_INK = Color(0xFFCDEBA6)
internal val EMPTY_TOP = 14.1.dp             // 10 px into the list: "Pick your state..."

// ── On now (design C of live_tv_on_now_designs_abc) ──────────────────────────
// Sport pills in the search bar's place; games show their start and league in
// the number and flag columns; the panel leads with the matchup picture.
internal val CHIP_DOT_GAP = 7.1.dp           // 5 px: On now's chip, dot to name
internal val SPORT_PILL_TOP = 73.4.dp        // 52 px
internal val SPORT_PILL_HEIGHT = 33.9.dp     // 24 px
internal val SPORT_PILL_PAD_H = 11.3.dp      // 8 px
internal val SPORT_PILL_GAP = 7.1.dp         // 5 px
internal val SPORT_PILL_COUNT_GAP = 5.6.dp   // 4 px
internal val SPORT_COUNT_IDLE = Color(0xFF7D8496)
internal val SPORT_COUNT_ON = Color(0xFFC9D5FF)
internal val GAME_WHEN_WIDTH = 53.7.dp       // 38 px: LIVE or "4:25"
internal val GAME_LEAGUE_WIDTH = 73.4.dp     // 52 px, wider for a long league (leagueColumn)
internal val GAME_LEAGUE_GAP = 8.5.dp        // 6 px: a long league tag to the name
internal val LIVE_TAG_BG = Color(0xFFC62828)
internal val GAME_ART_HEIGHT = 180.7.dp      // 128 px, the panel's width at 16:9-ish
internal val GAME_NAME_TOP = 14.1.dp         // 10 px
internal val GAME_NAME_TEXT = 31.1.sp        // 22 px
internal val GAME_META_TOP = 7.1.dp          // 5 px
internal val GAME_META_GAP = 8.5.dp          // 6 px: LIVE to "NFL · kicked off..."
internal val GAME_HINT_TOP = 16.9.dp         // 12 px
internal val GAME_ART_BG = Color(0xFF0B1433)

// ── The player's tuning screen (design B of live_tv_tuning_globe_designs_abc) ─
// Laid out in the mockup's 680 px frame like the guide: the movie loader's
// globe at 62 px (its 100 dp radius is 70.8 px there), the channel's card under it.
internal val TUNE_TAGS_TOP = 56.5.dp         // 40 px
internal val TUNE_TAG_HEIGHT = 24.dp         // 17 px
internal val TUNE_TAG_PAD_H = 8.5.dp         // 6 px
internal val TUNE_TAG_GAP = 4.2.dp           // 3 px
internal val TUNE_GLOBE_Y = 211.8.dp         // 150 px: the globe's centre
internal const val TUNE_GLOBE_SCALE = 0.875f // 62 / 70.8 px
internal val TUNE_NAME_TOP = 338.9.dp        // 240 px
internal val TUNE_NAME_TEXT = 42.4.sp        // 30 px
internal val TUNE_META_TOP = 398.2.dp        // 282 px
internal val TUNE_DOT_GAP = 7.1.dp           // 5 px
internal val TUNE_DETAIL_TOP = 426.4.dp      // 302 px
internal val TUNE_LINE_TEXT = 15.5.sp        // 11 px
internal val TUNE_BAR_TOP = 463.1.dp         // 328 px
internal val TUNE_BAR_WIDTH = 220.dp         // 156 px, the movie loader's bar
internal val TUNE_BAR_HEIGHT = 2.dp
internal val TUNE_DOTS_TOP = 480.dp          // 340 px
internal val TUNE_DOTS_HEIGHT = 8.5.dp       // 6 px
internal val TUNE_DOTS_GAP = 8.5.dp          // 6 px
internal val TUNE_NOTE_TOP = 497.dp          // 352 px: the movie loader's amber warning line
internal val TUNE_NAME_MAX = 800.dp
internal val TUNE_NOTE_MAX = 480.dp
internal val TUNE_DETAIL = Color(0x99FFFFFF)  // white at 60%, as the movie loader's detail
internal val TUNE_NOTE = Color(0xF2FFB74D)    // its amber at 95%
internal val TUNE_DOT_ACTIVE = Color(0xFF88BBFF)
internal val TUNE_DOT_DONE = Color(0xFF4466FF)
internal val TUNE_DOT_TODO = Color(0x1FFFFFFF)

// ── Channel list (left) ───────────────────────────────────────────────────────
internal val LIST_LEFT = 22.6.dp             // 16 px
// 92 px: the bar's 28 px plus 14 px, the same gap as chips to bar (the user
// found the mockup's 6 px too tight, 2026-10-30).
internal val LIST_TOP = 129.9.dp
internal val LIST_WIDTH = 468.8.dp           // 332 px
internal val LIST_HEIGHT = 387.5.dp          // to 22.6 dp above the bottom: 9 rows
internal const val LIST_VISIBLE_ROWS = 9
internal val ROW_HEIGHT = 39.5.dp            // 28 px
internal val ROW_GAP = 2.8.dp                // 2 px
internal val ROW_PAD_H = 14.1.dp             // 10 px
internal val ROW_RADIUS = 8.5.dp             // 6 px
internal val ROW_IDLE = Color(0xD912141B)    // rgba(18,20,27,.85)
internal val ROW_FOCUSED = Color(0xFF2644A6)
internal val FOCUS_RING = 2.8.dp             // 2 px white
internal val NUMBER_WIDTH = 48.dp            // 34 px
internal val NUMBER_TEXT = 15.5.sp           // 11 px
internal val NUMBER_IDLE = Color(0xFF7D8496)
internal val NUMBER_FOCUSED = Color(0xFFDFE6FF)
internal val FLAG_WIDTH = 42.4.dp            // 30 px
internal val NAME_TEXT = 19.8.sp             // 14 px
internal val NAME_OFF = Color(0xFF7D8496)    // a channel whose last try failed
internal val DOT_SIZE = 9.9.dp               // 7 px
internal val DOT_GAP = 5.6.dp                // 4 px
internal val LIVE_DOT = Color(0xFFFF3B3B)
internal val OFF_DOT = Color(0xFF6B7080)

// ── Small tags (flag, FHD) ────────────────────────────────────────────────────
internal val TAG_TEXT = 15.5.sp              // 11 px
internal val TAG_HEIGHT = 21.2.dp            // 15 px line
internal val TAG_PAD_H = 7.1.dp              // 5 px
internal val TAG_RADIUS = 5.6.dp             // 4 px
internal val TAG_GAP = 5.6.dp                // 4 px
internal val TAG_BG = Color(0xFF2A2E3B)
internal val TAG_TEXT_COLOR = Color(0xFFD7DBE4)

// ── Details panel (right) ─────────────────────────────────────────────────────
internal val PANEL_LEFT = 511.1.dp           // 362 px
internal val PANEL_TOP = 70.6.dp             // 50 px
internal val PANEL_WIDTH = 426.4.dp          // 302 px
internal val PANEL_HEIGHT = 353.dp           // 250 px
internal val PANEL_RADIUS = 14.1.dp          // 10 px
internal val PANEL_BG = Color(0xEB14161E)    // rgba(20,22,30,.92)
internal val PANEL_BORDER = 1.4.dp           // 1 px
internal val PANEL_BORDER_COLOR = Color(0xFF2A2E3B)
internal val PANEL_BAND = 5.6.dp             // 4 px, the row's colour
internal val PANEL_SIDE = 22.6.dp            // 16 px
internal val PANEL_NAME_TOP = 19.8.dp        // 14 px
internal val PANEL_NAME_TEXT = 36.7.sp       // 26 px
internal val PANEL_META_TOP = 5.6.dp         // 4 px
internal val PANEL_META_TEXT = 16.9.sp       // 12 px
internal val PANEL_META_COLOR = Color(0xFFCFD3DC)
internal val PANEL_LIVE_TOP = 14.1.dp        // 10 px
internal val PANEL_LIVE_COLOR = Color(0xFFFF8A8A)
internal val PANEL_NOTE_TOP = 11.3.dp        // 8 px
internal val PANEL_NOTE_TEXT = 15.5.sp       // 11 px
internal val PANEL_NOTE_COLOR = Color(0xFF9AA1B2)
internal val PANEL_HINT_TOP = 25.4.dp        // 18 px

// ── Hint bars (gold) ──────────────────────────────────────────────────────────
internal val HINT_LEFT = 511.1.dp            // 362 px
internal val HINT_TOP = 446.1.dp             // 316 px
internal val HINT_WIDTH = 426.4.dp           // 302 px
internal val HINT_HEIGHT = 36.7.dp           // 26 px (the panel's own: 24 px = 33.9 dp)
internal val PANEL_HINT_HEIGHT = 33.9.dp
internal val HINT_BORDER = 2.1.dp            // 1.5 px
internal val HINT_RADIUS = 9.9.dp            // 7 px
internal val HINT_PAD_H = 14.1.dp            // 10 px
internal val HINT_GAP = 14.1.dp              // 10 px
internal val HINT_TEXT = 15.5.sp             // 11 px
internal val HINT_BG = Color(0xD90A0A0E)     // rgba(10,10,14,.85)
internal val HINT_TEXT_COLOR = Color(0xFFEEEEEE)
internal val HINT_SEPARATOR = Color(0xFF777777)
internal val GOLD = Color(0xFFC9A227)
internal val KEY_RADIUS = 4.2.dp             // 3 px
internal val KEY_PAD_H = 5.6.dp              // 4 px
internal val KEY_GAP = 5.6.dp                // 4 px
internal val KEY_TEXT_COLOR = Color(0xFF111111)

// ── Messages (loading, error) ─────────────────────────────────────────────────
internal val MESSAGE_TEXT = 16.9.sp

// ─────────────────────────────────────────────────────────────────────────────
//  The search page (design C of live_tv_search_designs_abc, the user's pick of
//  2026-10-30, opened from the bar): the same 680 x 382 px frame, so x 1.412.
//  The keyboard card keeps its own dp sizes; the mockup drew it at them.
// ─────────────────────────────────────────────────────────────────────────────
internal val SEARCH_LEFT = 22.6.dp           // 16 px
internal val SEARCH_TITLE_TOP = 16.9.dp      // 12 px
internal val SEARCH_TITLE_TEXT = 31.1.sp     // 22 px
internal val SEARCH_NOTE_TOP = 56.5.dp       // 40 px
internal val SEARCH_KEYBOARD_TOP = 84.7.dp   // 60 px

internal val TILES_LEFT = 426.4.dp           // 302 px
internal val TILES_WIDTH = 511.1.dp          // 362 px
internal val TILES_TITLE_TOP = 19.8.dp       // 14 px
internal val TILES_TITLE_TEXT = 28.2.sp      // 20 px
internal val TILES_NOTE_TOP = 56.5.dp        // 40 px
internal val TILES_TOP = 84.7.dp             // 60 px
internal val TILE_RADIUS = 9.9.dp            // 7 px
internal val TILE_PAD_START = 18.4.dp        // 13 px
internal val TILE_PAD_END = 14.1.dp          // 10 px
internal val TILE_META_TEXT = 15.5.sp        // 11 px

internal val PICK_WIDTH = 163.8.dp           // 116 px, three across
internal val PICK_HEIGHT = 89.dp             // 63 px
internal val PICK_COL_GAP = 9.9.dp           // 7 px
internal val PICK_ROW_GAP = 8.5.dp           // 6 px
internal val PICK_BAND = 5.6.dp              // 4 px
internal val PICK_NAME_TEXT = 25.4.sp        // 18 px
internal val PICK_META_TOP = 4.2.dp          // 3 px

internal val RESULT_WIDTH = 249.9.dp         // 177 px, two across
internal val RESULT_HEIGHT = 48.dp           // 34 px
internal val RESULT_COL_GAP = 11.3.dp        // 8 px
internal val RESULT_ROW_GAP = 8.5.dp         // 6 px
internal val RESULT_BAND = 4.2.dp            // 3 px
internal val RESULT_NAME_TEXT = 21.2.sp      // 15 px
internal val RESULT_PAD_END = 31.1.dp        // 22 px, room for the dot
internal val RESULT_DOT_END = 14.1.dp        // 10 px
internal const val RESULT_VISIBLE_ROWS = 7
internal val RESULTS_HEIGHT = 387.dp         // 7 rows

internal val SEARCH_HINT_TOP = 482.9.dp      // 342 px
internal val META_FOCUSED = Color(0xFFDFE6FF)
