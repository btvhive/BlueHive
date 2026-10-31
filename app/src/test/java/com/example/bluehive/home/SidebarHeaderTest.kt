package com.example.bluehive.home

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expanded sidebar (owner's design, 2026-10-31): a profile header -- the
 * avatar at twice the rail's size, rounded, the profile's name and a blue
 * "Switch profile" under it -- and the Search Bar row drawn as a rounded,
 * selectable search field. The collapsed rail is unchanged.
 */
class SidebarHeaderTest {

    private val code = MainSource.code("sidebarComponents/SidebarDisplay.kt")

    @Test
    fun `the header shows a rounded avatar at twice the rail's size, the name and Switch profile`() {
        assertTrue(code.contains("private val SIDEBAR_AVATAR_WIDTH: Dp  = 20.dp"))
        assertTrue(code.contains("private val HEADER_AVATAR        = 40.dp"))
        val header = code.substringAfter("private fun SidebarProfileHeader(").substringBefore("private fun SidebarSearchField(")
        assertTrue(header.contains("Modifier.size(HEADER_AVATAR).clip(HEADER_AVATAR_SHAPE)"))
        assertTrue(header.contains("text       = profileName.ifBlank { item.label }"))
        assertTrue(header.contains("\"Switch profile \\u203A\""))
        assertTrue(header.contains("color    = if (selected) SWITCH_PROFILE_ON_HL else SWITCH_PROFILE_BLUE"))
    }

    @Test
    fun `the Search Bar row is a rounded search field that highlights when selected`() {
        val field = code.substringAfter("private fun SidebarSearchField(").substringBefore("private fun SidebarMenuRow(")
        assertTrue(field.contains(".clip(SEARCH_SHAPE)"))
        assertTrue(field.contains("if (selected) Modifier.background(ROW_HIGHLIGHT) else Modifier.background(SEARCH_FILL)"))
        assertTrue(field.contains("\"Search titles\\u2026\""))
        // It is still menu item 1: OK on it opens the keyboard, as before.
        assertTrue(code.contains("SidebarSearchField(item = menuItems[1], selected = selectedIndex == 1)"))
        assertTrue(code.contains("if (menuItems[selectedIndex].id == \"search\") {"))
    }

    @Test
    fun `the panel shows no selection while the keyboard is up, and the rail keeps its icons`() {
        assertTrue(code.contains("selectedIndex = if (isSearchActive) -1 else selectedIndex,"))
        assertTrue(code.contains("SidebarRailIcons(menuItems = menuItems, width = baseW)"))
        // The old per-item label offsets went with the old label layer.
        assertFalse(code.contains("CustomYOffset ="))
        assertFalse(code.contains("unfocusedCustomYOffset"))
    }

    @Test
    fun `the profile name reaches the sidebar`() {
        assertTrue(MainSource.code("home/HomeScreen.kt").contains("profileName      = profileName,"))
        assertTrue(MainSource.code("home/HomeSidebar.kt").contains("profileName      = profileName,"))
    }
}
