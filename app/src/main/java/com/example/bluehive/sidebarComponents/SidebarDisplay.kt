package com.example.bluehive.sidebarComponents

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import com.example.bluehive.searchBarComponent.HoneycombKeyboardPopup

// Sidebar menu item data class
data class SidebarMenuItem(
    val id: String,
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
    val customXOffset: Dp = 0.dp,
    val customIconYOffset: Dp = 0.dp,
)


// ── Sidebar profile avatar config ─────────────────────────────────────────────
private val SIDEBAR_AVATAR_WIDTH: Dp  = 20.dp
private val SIDEBAR_AVATAR_HEIGHT: Dp = 20.dp


@Composable
fun HomeScreenSidebarCompose(
    focusRequester:   FocusRequester,
    canFocus:         Boolean,
    profileAvatarRes: Int = R.drawable.avatar1,
    profileName:      String = "",
    onFocusChanged:   (Boolean) -> Unit,
    onExitToRight:    () -> Unit,
    onProfileClick:      () -> Unit = {},
    onSearchSubmit:      (String) -> Unit = {},
    onMediaCatalogClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onFavoritesClick:    () -> Unit = {},
    onLiveTvClick:       () -> Unit = {},
    onChangelogClick:    () -> Unit = {},   // kept for caller compatibility (Movies/TV Shows removed)
    onHistoryClick:      () -> Unit = {},
) {
    val sidebarInteraction = remember { MutableInteractionSource() }
    var isSidebarFocused by remember { mutableStateOf(false) }
    var hasEverBeenFocused by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    // ── Inline search overlay state ─────────────────────────────────────────
    var isSearchActive      by remember { mutableStateOf(false) }
    var hasSearchBeenActive by remember { mutableStateOf(false) }

    // When the overlay closes, hand focus back to the sidebar Image.
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            hasSearchBeenActive = true
        } else if (hasSearchBeenActive) {
            kotlinx.coroutines.android.awaitFrame()
            focusRequester.requestFocus()
        }
    }

    // Get activity from context
    val context = LocalContext.current
    val activity = context as? Activity

    if (activity == null) {
        Log.e("SIDEBAR", "❌ Activity is null!")
    }

    val baseW = 25.5.dp
    val expandedW = 124.dp

    // Define menu items — NEW ORDER (Movies & TV Shows removed)
    val menuItems = remember {
        listOf(
            SidebarMenuItem(
                id = "profiles",
                iconRes = profileAvatarRes,
                label = "Profiles",
                onClick = onProfileClick,
                customXOffset = (-2).dp,
            ),
            SidebarMenuItem(
                id = "search",
                iconRes = R.drawable.magnifying_glass_raw_image,
                label = "Search Bar",
                onClick = {},  // activation handled in onKeyEvent — opens inline overlay
                customIconYOffset = 0.7.dp,
            ),
            SidebarMenuItem(
                id = "mediacatalog",
                iconRes = R.drawable.media_catalog,
                label = "Media Catalog",
                onClick = onMediaCatalogClick,
                customIconYOffset = 0.7.dp,
            ),
            SidebarMenuItem(
                id = "livetv",
                iconRes = R.drawable.livetv,
                label = "Live TV",
                onClick = onLiveTvClick,
            ),
            SidebarMenuItem(
                id = "favorites",
                iconRes = R.drawable.favourite,
                label = "Favorites",
                onClick = onFavoritesClick,
                customIconYOffset = 0.7.dp,
            ),
            SidebarMenuItem(
                id = "history",
                iconRes = R.drawable.history,
                label = "Watch History",
                onClick = onHistoryClick,
                customIconYOffset = 0.7.dp,
            ),
            SidebarMenuItem(
                id = "settings",
                iconRes = R.drawable.settings,
                label = "Settings",
                onClick = onSettingsClick,
                customIconYOffset = 0.7.dp,
            ),
            SidebarMenuItem(
                id = "reboot",
                iconRes = R.drawable.reboot,
                label = "Reboot",
                onClick = {
                    activity?.let {
                        Log.d("SIDEBAR", "🔴 Reboot button clicked")
                        SidebarReboot.performReboot(it, showConfirmation = true)
                    } ?: Log.e("SIDEBAR", "❌ Activity null on click")
                },
                customIconYOffset = 0.8.dp,
            )
        )
    }

    Box {
        // ========================================
        // BASE LAYER (always visible)
        // ========================================
        Image(
            painter = painterResource(id = R.drawable.sidebar),
            contentDescription = "Sidebar",
            modifier = Modifier
                .offset(x = 0.dp, y = 0.dp)
                .width(baseW)
                .fillMaxHeight()
                .focusRequester(focusRequester)
                .focusProperties { this.canFocus = canFocus }
                .onFocusChanged { state ->
                    isSidebarFocused = state.isFocused
                    if (state.isFocused || !isSearchActive) {
                        onFocusChanged(state.isFocused)
                    }

                    if (state.isFocused && !hasEverBeenFocused) {
                        hasEverBeenFocused = true
                        selectedIndex = 0
                        Log.d("SIDEBAR", "✅ First time focused - enabling panel rendering")
                    }
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> true

                            Key.DirectionRight -> {
                                if (isSidebarFocused) {
                                    BlueHiveApplication.playHoverSound()
                                    onExitToRight()
                                    Log.d("SIDEBAR", "➡️ RIGHT - leaving sidebar")
                                    true
                                } else false
                            }

                            Key.DirectionUp -> {
                                if (isSidebarFocused && selectedIndex > 0) {
                                    selectedIndex--
                                    BlueHiveApplication.playHoverSound()
                                    Log.d("SIDEBAR", "⬆️ UP - selected: ${menuItems[selectedIndex].label}")
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                if (isSidebarFocused && selectedIndex < menuItems.lastIndex) {
                                    selectedIndex++
                                    BlueHiveApplication.playHoverSound()
                                    Log.d("SIDEBAR", "⬇️ DOWN - selected: ${menuItems[selectedIndex].label}")
                                }
                                true
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (isSidebarFocused) {
                                    BlueHiveApplication.playClickSound()
                                    if (menuItems[selectedIndex].id == "search") {
                                        isSearchActive = true
                                    } else {
                                        menuItems[selectedIndex].onClick()
                                    }
                                    Log.d("SIDEBAR", "✅ Selected: ${menuItems[selectedIndex].label}")
                                }
                                true
                            }

                            else -> false
                        }
                    } else false
                }
                .focusable(interactionSource = sidebarInteraction),
            contentScale = ContentScale.FillBounds
        )


        // ========================================
        // EXPANDED PANEL (visible when focused OR searching)
        // ========================================
        // A profile header and a search field on top, then the menu rows
        // (the owner's design, 2026-10-31). While the search keyboard is up
        // the panel stays open behind it but shows no selection: the keyboard
        // has the focus (the user's call, 2026-10-16).
        if ((isSidebarFocused || isSearchActive) && hasEverBeenFocused) {
            SidebarExpandedPanel(
                menuItems     = menuItems,
                selectedIndex = if (isSearchActive) -1 else selectedIndex,
                profileName   = profileName,
                width         = expandedW,
            )
        } else {
            // ========================================
            // RAIL ICONS (the collapsed sidebar)
            // ========================================
            SidebarRailIcons(menuItems = menuItems, width = baseW)
        }


        // ========================================
        // COVER LAYER (visible when NOT focused AND not searching)
        // ========================================
        if (!isSidebarFocused && !isSearchActive) {
            Image(
                painter = painterResource(id = R.drawable.sidebar_cover),
                contentDescription = "Sidebar Cover",
                modifier = Modifier
                    .offset(x = 0.dp, y = 0.dp)
                    .width(baseW)
                    .fillMaxHeight(),
                contentScale = ContentScale.FillBounds
            )
        }

        // ========================================
        // INLINE SEARCH KEYBOARD (honeycomb, centered over the blurred home)
        // ========================================
        // Search -- or a finished Voice search -- hands the text to the search
        // page, which runs it; BACK just closes the keyboard.
        if (isSearchActive && hasEverBeenFocused) {
            HoneycombKeyboardPopup(
                onSearch = { query ->
                    val q = query.trim()
                    isSearchActive = false
                    if (q.isNotEmpty()) onSearchSubmit(q)
                },
                onDismiss = { isSearchActive = false },
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  The expanded panel: profile header, search field, menu rows
// ─────────────────────────────────────────────────────────────────────────────

private val PANEL_SHAPE          = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
private val PANEL_FILL           = Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D)))
private val PANEL_EDGE           = Color(0xFF333333)
/** The selected row: the blue the home panels' select glow is tinted from (PANEL_SELECTED_TINT). */
private val ROW_HIGHLIGHT        = Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB)))

// The profile header: the avatar at twice the rail's 20 dp, rounded, with the
// profile's name and "Switch profile" beside it.
private val HEADER_TOP           = 8.dp
private val HEADER_HEIGHT        = 52.dp
private val HEADER_AVATAR        = 40.dp
private val HEADER_AVATAR_SHAPE  = RoundedCornerShape(9.dp)
private val SWITCH_PROFILE_BLUE  = Color(0xFF3B82F6)
private val SWITCH_PROFILE_ON_HL = Color(0xFFDBEAFE)    // on the blue highlight, where blue would vanish

// The search field: a rounded bar standing in for the "Search Bar" row.
private val SEARCH_TOP           = 66.dp
private val SEARCH_HEIGHT        = 26.dp
private val SEARCH_SIDE          = 6.dp
// The keyboard overlay's own search box rounding (HoneycombOverlay FIELD_SHAPE,
// 10 dp), so the bar and the box it opens match (owner, 2026-10-31; was a pill).
private val SEARCH_SHAPE         = RoundedCornerShape(10.dp)
private val SEARCH_FILL          = Color(0xFF26262E)
private val SEARCH_EDGE          = Color(0xFF44444F)
private val SEARCH_EDGE_ON       = Color(0xFF60A5FA)
private val SEARCH_HINT          = Color(0xFF9A9AA4)

// The rest of the menu, from Media Catalog down: the old 35 dp step and 28 dp
// highlight, starting well under the search field -- 28 dp clear of it, so the
// header and search read as their own group (owner, 2026-10-31; was 8 dp).
private val ROWS_TOP             = 120.dp
private val ROW_STEP             = 35.dp
private val ROW_HEIGHT           = 28.dp
private val ROW_LABEL_X          = 30.5.dp
private val ROW_LABEL_OFF        = Color(0xFF888888)

@Composable
private fun SidebarExpandedPanel(
    menuItems: List<SidebarMenuItem>,
    selectedIndex: Int,
    profileName: String,
    width: Dp,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(PANEL_SHAPE)
            .background(PANEL_FILL)
            .border(1.dp, PANEL_EDGE, PANEL_SHAPE)
    ) {
        SidebarProfileHeader(item = menuItems[0], selected = selectedIndex == 0, profileName = profileName)
        SidebarSearchField(item = menuItems[1], selected = selectedIndex == 1)
        for (index in 2..menuItems.lastIndex) {
            SidebarMenuRow(item = menuItems[index], top = ROWS_TOP + ROW_STEP * (index - 2), selected = index == selectedIndex)
        }
    }
}

@Composable
private fun SidebarProfileHeader(item: SidebarMenuItem, selected: Boolean, profileName: String) {
    Row(
        modifier = Modifier
            .offset(y = HEADER_TOP)
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .then(if (selected) Modifier.background(ROW_HIGHLIGHT) else Modifier)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter            = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier           = Modifier.size(HEADER_AVATAR).clip(HEADER_AVATAR_SHAPE),
            contentScale       = ContentScale.Crop,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text       = profileName.ifBlank { item.label },
                color      = Color.White,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = "Switch profile \u203A",
                color    = if (selected) SWITCH_PROFILE_ON_HL else SWITCH_PROFILE_BLUE,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SidebarSearchField(item: SidebarMenuItem, selected: Boolean) {
    Row(
        modifier = Modifier
            .offset(y = SEARCH_TOP)
            .padding(horizontal = SEARCH_SIDE)
            .fillMaxWidth()
            .height(SEARCH_HEIGHT)
            .clip(SEARCH_SHAPE)
            .then(if (selected) Modifier.background(ROW_HIGHLIGHT) else Modifier.background(SEARCH_FILL))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) SEARCH_EDGE_ON else SEARCH_EDGE, SEARCH_SHAPE)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter            = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier           = Modifier.size(12.dp).graphicsLayer { alpha = if (selected) 1f else 0.7f },
            contentScale       = ContentScale.Fit,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text     = "Search titles\u2026",
            color    = if (selected) Color.White else SEARCH_HINT,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarMenuRow(item: SidebarMenuItem, top: Dp, selected: Boolean) {
    Box(
        modifier = Modifier
            .offset(y = top)
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .then(if (selected) Modifier.background(ROW_HIGHLIGHT) else Modifier)
    ) {
        Image(
            painter            = painterResource(id = item.iconRes),
            contentDescription = item.label,
            modifier           = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 5.dp + item.customXOffset, y = item.customIconYOffset)
                .size(15.dp)
                .graphicsLayer { alpha = if (selected) 1f else 0.4f },
            contentScale       = ContentScale.Fit,
        )
        Text(
            text       = item.label,
            color      = if (selected) Color.White else ROW_LABEL_OFF,
            fontSize   = if (selected) 13.sp else 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier   = Modifier
                .align(Alignment.CenterStart)
                .offset(x = ROW_LABEL_X)
                .graphicsLayer {
                    scaleX = if (selected) 1.05f else 1f
                    scaleY = if (selected) 1.05f else 1f
                }
        )
    }
}

/** The collapsed rail's icons: the avatar and one icon per row, 35 dp apart. */
@Composable
private fun SidebarRailIcons(menuItems: List<SidebarMenuItem>, width: Dp) {
    Box(
        modifier = Modifier
            .offset(x = 0.dp, y = 40.dp)
            .width(width)
            .fillMaxHeight()
    ) {
        menuItems.forEachIndexed { index, item ->
            val yOffset = (index * 35).dp
            Image(
                painter            = painterResource(id = item.iconRes),
                contentDescription = item.label,
                modifier           = Modifier
                    .offset(x = 5.dp + item.customXOffset, y = yOffset + item.customIconYOffset)
                    .size(
                        width  = if (item.id == "profiles") SIDEBAR_AVATAR_WIDTH else 15.dp,
                        height = if (item.id == "profiles") SIDEBAR_AVATAR_HEIGHT else 15.dp,
                    ),
                contentScale       = ContentScale.Fit,
            )
        }
    }
}
