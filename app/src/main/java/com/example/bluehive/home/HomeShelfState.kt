package com.example.bluehive.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The shelves: whether the first shelf is showing, the two one-frame events
 * for ShelfStack, whether focus is inside the stack, the pending restore, and
 * whether this profile has watch history (null until Continue Watching knows).
 *
 * Moved here from HomeScreen in Stage H2 of the Favorites and Home redesign
 * (docs/favorites-home-redesign-architecture.md); the code is the code it
 * replaces, its names read through the holder.
 */
@Stable
internal class HomeShelfState {
    var isOnFirstShelf by mutableStateOf(true)
    // ── WHICH SHELF ROW AN OUTSIDE ARRIVAL WANTS ───────────────────────
    //
    // Non-null for the one frame it takes ShelfStack to act on it; the stack
    // clears it. An event, not a mode -- left set, it would re-fire every time
    // the stack recomposed.
    var enterShelfAt by mutableStateOf<Int?>(null)
    // The same kind of event for a row change that leaves focus where it is
    // (ShelfStack's scrollToShelf). "Top of Page" sets it to the first shelf.
    var scrollShelfTo by mutableStateOf<Int?>(null)
    // Whether this profile has any watch history, as the Continue Watching
    // panel last reported it: null until its first load answers. Only a known
    // "no" hides the panel, so a profile with history never sees the layout
    // change while it loads.
    var continueWatchingHasHistory by mutableStateOf<Boolean?>(null)
    // IS FOCUS INSIDE A SHELF OF TITLE CARDS RIGHT NOW?
    //
    // Back means something different there -- see the router in
    // CloseApplication.kt -- so it needs a live answer, which lastFocusedTarget
    // cannot give: that one records the last place focus WAS and never clears.
    //
    // Read off the ShelfStack container, never off a list of rows. Every shelf,
    // on every tab, lives inside that one container, so adding a shelf,
    // removing one, or reordering them cannot reach this flag -- the thing being
    // watched is the box they all sit in, not its contents. Each ShelfStack call
    // site assigns it.hasFocus straight through, so it falls to false the moment
    // focus leaves for the tabs, a frame, or the sidebar.
    var isShelfFocused by mutableStateOf(false)
    // Raised when the choice overlay is dismissed with back, to confirm a beat
    // later that focus really did land back on the shelf. See the effect below.
    var restoreShelfFocusPending by mutableStateOf(false)

    // IS THE CONTINUE WATCHING PANEL ON SCREEN? It is composed only on the
    // first shelf, and draws nothing when the profile has no watch history.
    // Everything that moves focus INTO it asks this first and goes
    // elsewhere when the answer is no -- a requester with nothing behind it
    // swallows the key press and moves focus nowhere. A function, so a
    // focusProperties lambda or an effect gets the answer at the moment it
    // asks, not a value captured when it was composed.
    fun continueWatchingShown(entry: HomeEntryTransition): Boolean =
        continueWatchingPanelShown(entry.startContent, isOnFirstShelf, continueWatchingHasHistory)
}

/** The Continue Watching panel counts as shown. Pure, so HomeStateTest pins it. */
internal fun continueWatchingPanelShown(startContent: Boolean, isOnFirstShelf: Boolean, hasHistory: Boolean?): Boolean =
    startContent && isOnFirstShelf && hasHistory != false
