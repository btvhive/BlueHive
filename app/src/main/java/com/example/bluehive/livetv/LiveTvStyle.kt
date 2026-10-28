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

// ── Channel list (left) ───────────────────────────────────────────────────────
internal val LIST_LEFT = 22.6.dp             // 16 px
internal val LIST_TOP = 70.6.dp              // 50 px
internal val LIST_WIDTH = 468.8.dp           // 332 px
internal val LIST_HEIGHT = 446.8.dp          // to 22.6 dp above the bottom: 10 rows
internal const val LIST_VISIBLE_ROWS = 10
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
