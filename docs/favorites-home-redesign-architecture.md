# Favorites and Home redesign: architecture plan

**Status:** approved 2026-10-25; **done 2026-10-26**: all stages built, Rounds A, B and C passed on the onn box (v414). Nothing published. §9 #7 and #8 fixed afterwards in v415 (on the onn box, for you to test); the other §9 items stay as they are until you pick them.
- Stage 0 is done: the two launch records, the two ratchets, and the size and speed baselines in `docs/favorites-home-redesign-baseline.md`.
- F1 is done (v406, verified on the onn box): Favorites is in `favorites/`.
- F2 is done (v408, verified on the onn box): the three state holders and the seven effects as child composables.
- F3 is done (v409, verified): the seven section composables. `FavoritesScreen` is down to 1,131 units / 45 registers, under the budget and off the ratchet.
- Round A is open: v409 is on the onn box since 18:50 UTC, for the Favorites checklist (§11). Nothing is published (ground rule 7).
- H1 is done (v410, started at your word while Round A runs; not installed): the home screen is in `home/`, the carousel tick no longer re-runs it, and the three shelf calls are one. `HomeScreen` is down to 4,153 units / 99 registers. The on-device check of v410 is still to run.
- H2 is done (v412, verified on the onn box; not installed): five state holders, and effects 2 to 11 as three child composables. `HomeScreen` is down to 3,012 units / 67 registers. Effects 12 and 13 (offline) move with the OfflineRetry section in H3.
- H3 is done (v413, verified on the onn box; not installed): six sections, called where their code was. `HomeScreen` is down to 1,076 units / 34 registers, under the budget and off the ratchet, which is now empty.
- Round B is open: v413 is on the onn box since about 01:05 UTC 2026-10-26, for the Home checklist (§11). It went on the Fire Stick first (00:57 UTC), but BlueHive opened its account-standing lockout there ("Connection trouble": Off Grid Drive reports that account inactive), a gate before the home screen and not part of this redesign. Nothing is published unless you say so.
- L is done (v414; device check 12,749 classes, 0 failures after Round B; installed on the onn box): the ratchet code is gone, one 2,500 / 100 budget holds for `details/`, `favorites/` and `home/`, and the rest of the app is reported from 2,500 units (eight functions, led by `ProfileManageScreen` at 5,009 / 133). Final speed measured on the onn box (baseline doc, "Speed after the redesign"): typical frames faster, janky frames on Home about halved, the carousel-change frame unchanged. The Fire Stick could not be measured: its account is locked out. Round C passed on the onn box (2026-10-26).

The same treatment the details screens got (`docs/details-redesign-architecture.md`): split the app's last two giant composables into folders of small, focused files, with no change in behaviour, and lock the result in with the build gates.

---

## 1. Why: the numbers

Measured on the v405 release build (dexdump of the APK, mapped through R8's `mapping.txt`, the way `checkMethodSizes` does it):

| | `FavoritesScreen` | `HomeScreenComposeContent` |
|---|---|---|
| File | `sidebarComponents/SidebarFavorites.kt` | `HomeScreenCompose.kt` |
| Lines in the function | 1,342 | 925 |
| Dex code units | **5,660** | **5,030** |
| Registers | **209** | **118** |
| Where R8 put it | inside `kotlin.reflect.KClasses` | its own file class |

For scale: the TV details function was 11,297 units and 287 registers when R8 broke it. Every details method is now under 2,500 / 100 (largest 2,132 / 56).

**Why it matters beyond Favorites.** R8 packs static functions from different files into one class. In v405, `kotlin.reflect.KClasses` holds `FavoritesScreen` together with details code: the Extras row (`RecommendedMediaSection`), `MovieInfoLabels`, `DetailsTrailerLayer`, the anime server, loading and error overlays, `rememberExtrasState` and `TrailerFailedNotice`. ART verifies a whole class at once, so an R8 miscompile in `FavoritesScreen` would crash those details pieces too. The device verifier would catch it before an install, but the details screens' safety currently depends on this function. R8 re-decides the packing on every build.

**Why `FavoritesScreen` is so big:**
- `Box`, `Column` and `Row` are inline functions, so the two empty states, the control bar and the remove-confirm overlay all compile into the function itself.
- Six `ModularButton` calls build their `ModularButtonColors` (ten `Color` values each, and a `Color` is a 64-bit value, two registers) and `ModularButtonDimensions` in place or through helpers called with ten colour arguments.
- About 30 layout and colour constants are local variables, and the 28-genre and 5-sort lists are rebuilt on every composition.

**Why `HomeScreenComposeContent` is so big:**
- 25 pieces of state and 10 focus requesters live in the function, plus 13 effects.
- Three near-identical `ShelfStack` calls, one per tab, each with about 17 arguments and 7 lambdas.
- **It re-runs in full every 6 seconds while the screen sits idle.** The carousel clock lives in it, and it reads `cycleTick` to provide `LocalCarouselCycleTick`.

**Already fine (measured):** every other method from the two files is under the budget. The largest are Favorites' press-and-prefetch coroutine (1,556 / 32), its grid cell (1,127 / 58) and the TV Shows tab button (821 / 53).

---

## 2. Ground rules

Same as the details redesign:

1. **No behaviour change.** Same layout, focus, sounds, timings, strings and requests. Differences or bugs found on the way are kept and listed (§9), and fixed only with your OK.
2. **One build with all gates and one commit per stage.** A script verifies each move: the new code, with its names mapped back, is the old code.
3. **Every new method under 2,500 units / 100 registers.**
4. **Effects keep their order.** A run of effects moves into a restartable child composable called exactly where it was. R8 inlines a one-caller `remember…` function straight back into its caller, so moved effects never go there.
5. **Performance rules:** P1, state is read where it is used; P3, cards take plain values; P4, constants live at file level, built once.
6. **Shared checkout.** The other session edits `ShelfStack.kt`, `TvWideLazyRow.kt`, `GoodCopyCountdown.kt` and `MediaItem.kt`. This plan touches none of them, and only its own files are committed.
7. **Builds stay on the onn box.** A round's build goes on the onn box for you to test; nothing is published to the update server or GitHub until you say so, so other users never get an untested build (your rule, 2026-10-25).

---

## 3. Target structure

```
app/src/main/java/com/example/bluehive/
├── favorites/                          the Favorites screen
│   ├── FavoritesScreenActivity.kt        Activity + openFavoritesScreen                     ~60
│   ├── FavoritesScreen.kt                root: creates the state, places the sections       ~150
│   ├── FavoritesStyle.kt                 layout constants, colours, button configs (P4)     ~130
│   ├── FavoriteEntry.kt                  the model + toFavoriteEntry (pure, tested)         ~90
│   ├── FavoritesData.kt                  entries, loading, batch fetch, remove/commit/undo  ~190
│   ├── FavoritesFilter.kt                media / genre / sort + filterAndSort (tested)      ~110
│   ├── FavoritesGridFocus.kt             focused card, repeat pace, requesters, gridTarget  ~160
│   ├── FavoritesControlBar.kt            header, Filter + Sort, Remove Media, 3 type tabs   ~260
│   ├── FavoritesGrid.kt                  vanish layer + grid + its key handling             ~200
│   ├── FavoriteCard.kt                   one cell: press dip, vanish, MediaCard             ~160
│   ├── FavoritesEmptyStates.kt           "No favorite content" + "No content found"         ~120
│   ├── RemoveConfirmOverlay.kt           "Exit Remove Mode": Accept / Undo                  ~200
│   └── FavoritesLaunch.kt                press-and-prefetch + details extras (typed, tested) ~130
│
└── home/                               the home screen's content
    ├── HomeScreen.kt                     root: creates the state, places the sections       ~150
    ├── HomeStyle.kt                      palette, tab button configs, SHELF_STACK_Y         ~70
    ├── HomeFocusMap.kt                   every focus requester + tab neighbours             ~80
    ├── HomeSidebarState.kt               sidebar flags, return target, return-focus restore ~180
    ├── HomeEntryTransition.kt            "Welcome back" overlay state + its effects         ~100
    ├── CarouselClock.kt                  the 6 s tick, around the two carousels only (P1)   ~60
    ├── HomeTopFrames.kt                  trailer + trending frames, focus and Left keys     ~150
    ├── HomeContinueWatchingPanel.kt      the panel beside the first shelf                   ~90
    ├── CatalogTabs.kt                    parking node + Movies / TV Shows / Anime buttons   ~360
    ├── HomeShelfState.kt                 first shelf, enter/scroll events, shelf focus      ~120
    ├── HomeShelves.kt                    ONE ShelfStack call, keyed by tab                  ~110
    ├── HomeSidebar.kt                    the sidebar and where each item goes               ~100
    └── OfflineRetry.kt                   banner, reload, network callback                   ~80
```

- **`FavoritesScreenActivity` moves into `favorites/`**, class name unchanged. The manifest, `DetailsBackNavigation` and the home sidebar's import are updated (compile-checked).
- **`HomeScreenCompose`, the Activity, stays where it is** (`com.example.bluehive.HomeScreenCompose`). It is the app's hub: the manifest, the profile picker and notes across the app name it. Only the screen moves into `home/`.
- **Helpers other screens use stay where they are:** `HistoryDropdown` (the History screen), `MediaCard`, `TitleCardPress`, `ModularButton`, `ShelfStack`, the trailer, trending and Continue Watching sections, `CloseApplicationHandler`, `RetryOverlay` and `ProfileEntryOverlay`.
- **`favorites/` and `home/` never use each other's internals or any details internals.** Each screen is reached only through its entry points: Favorites through `FavoritesScreenActivity` and `openFavoritesScreen` (the home sidebar opens it this way), details through its two Activities. To build its launch, Favorites also uses `DetailsExtra` and `putDetailsExtras` (§4). The boundary gate enforces it (§8).

---

## 4. Favorites design

**State holders** (plain `@Stable` classes with snapshot state, as in the details screens):

| Holder | Holds |
|---|---|
| `FavoritesData` | the entries, loading and navigating flags, the batch fetch (one call per 200 favourites; a silent re-fetch on resume keeps the cards on screen), the removed list, commit (one `toggleFavorite` each) and undo |
| `FavoritesFilter` | media type, genre and sort, and the filtered list: `derivedStateOf` over a pure `filterAndSort`, so it is unit-tested |
| `FavoritesGridFocus` | grid has focus, the focused index, the 80 ms repeat clock, the requester pool keyed to the list size, `focusGridItem` (the scroll nudge), and a pure `gridTarget(index, key, count, perRow)` for the D-pad, unit-tested |

The two sound flags (`hoverArmed`, `suppressNextHover`) live with the focus holder.

**Sections** are `BoxScope` extensions, so every element keeps its absolute offset and nothing gets a wrapper layout:
- `FavoritesControlBar`: the header, Filter and Sort dropdowns, Remove Media, and the All Media / Movies / TV Shows tabs.
- `FavoritesGrid` and `FavoriteCard`: the vanish layer, the grid, and the cell with its press dip, per-card vanish controller and key handling. The vanish layer keeps its place in the tree.
- `FavoritesEmptyStates`: "No favorite content added yet" and "No content found for this filter".
- `RemoveConfirmOverlay`: "Exit Remove Mode" with Accept and Undo.

**Effects:** the seven effects keep their order (F-C5).

**Launching details:** `FavoritesLaunch` builds the extras as a typed list (`DetailsExtra`, as `DetailsIntents` does), so a JVM test can pin them. They stay exactly as today, with `""` and `"N/A"` defaults and `SOURCE_SCREEN = FAVORITES`. They do not switch to `DetailsIntents`, whose defaults differ (§9, #5).

---

## 5. Home design

**State holders:**

| Holder | Holds |
|---|---|
| `HomeFocusMap` | trailer, trending, the three tab buttons, Continue Watching, sidebar, shelf stack and both parking nodes; `neighborOf` and `requesterFor` |
| `HomeSidebarState` | sidebar focused, can-focus, open and return requests, return target, parking, `lastFocusedTarget`; `openSidebar(from)` |
| `HomeEntryTransition` | overlay shown, minimum time elapsed, first shelf ready, `startContent` |
| `CatalogTabsState` | selected tab, tab parking, pending focus, the tab sound arming |
| `HomeShelfState` | on the first shelf, the enter-at and scroll-to events, shelf focus, restore pending, Continue Watching history known |

**`CarouselClock` (the P1 fix).** The 6 s tick and its `CompositionLocalProvider` move into a composable that wraps only the two carousels. The home function stops reading `cycleTick`, so it no longer re-runs every 6 s. `LocalCarouselCycleTick` stays a static local, so the carousels themselves recompose exactly as now.

**`HomeShelves`: one `ShelfStack` call instead of three.** It sits inside `key(tab)`, which disposes and creates the stack on a tab switch exactly as the three `if` blocks do. What differs per tab becomes a parameter:
- the `lastFocusedTarget` value: `MOVIES_SHELF`, `TV_SHOWS_SHELF` or `ANIME_SHELF`;
- `onFirstPageReady`, which only the Movies stack reports (the entry overlay waits on it);
- the log tags.

**Sections:**
- `HomeTopFrames`: the trailer and trending frames with their focus properties and Left keys.
- `HomeContinueWatchingPanel`.
- `CatalogTabs`: the tab parking node and the three tab buttons.
- `HomeSidebar`: the sidebar and where each of its items goes.
- `OfflineRetry`.
- The entry overlay and `CloseApplicationHandler` stay last, on top.

**Effects:** the 13 effects keep their order (H-C5).

**Optional, asked separately:** one `CatalogTabButton` in place of the three near-identical tab button functions (they differ in text, offset and neighbour rules).

---

## 6. Contracts every stage must keep

### Favorites

- **F-C1 Focus:**
  - When the cards load, focus lands on the first (or the previously focused) card.
  - Up from the first row goes to the Filter dropdown.
  - Down past the last full row lands on the last card, unless you are already on the last row.
  - Entering remove mode jumps to the first card.
  - Removing a card moves focus to its neighbour before the card goes.
  - The confirm overlay takes focus on Accept; Left and Right move between Accept and Undo.
- **F-C2 Back:**
  - In the grid, Back goes to the Filter dropdown; in remove mode it opens the confirm overlay.
  - The overlay swallows Back.
  - Anywhere else, Back plays the back-out sound and leaves.
- **F-C3 Sounds:**
  - The hover sound is armed one frame after the auto-focus lands, so opening the page is silent.
  - Click on select; the title-card sound on Down.
  - The type tabs' hover sound, with its one-shot suppression after a tab press.
- **F-C4 Timings:**
  - 80 ms D-pad repeat.
  - 150 ms before a removed card goes.
  - 800 ms press-and-prefetch.
  - 50 ms tab focus hand-off.
  - A 2,500 ms vanish with particle size 2.
- **F-C5 Effects order:** reset-on-dispose, fetch, filter reset, auto-land, page-enter focus, restore after removal, focus the overlay.
- **F-C6 Requests:**
  - `getFavorites`, then one batch call per 200.
  - `toggleFavorite` per removed title on commit.
  - The details extras as recorded in Stage 0.

### Home

- **H-C1 Focus:**
  - Trailer frame first.
  - Trailer: Right goes to trending, Down to the next tab's button, Left opens the sidebar unless a trailer is selected.
  - Trending: Left goes to the trailer, Down to Continue Watching, or to the shelf when there is no history.
  - Tab switching uses the parking node and the pending hop.
  - Continue Watching: Down goes to row 2, Left to the shelf.
  - A Continue Watching panel that disappears hands focus to the shelf.
- **H-C2 Back:**
  - Anywhere on the screen, Back opens the sidebar; on the sidebar, it opens the exit overlay.
  - On a shelf, Back opens the choice overlay: "Top of page" goes to the next tab's button and the first shelf; "back to the row" asks synchronously, with a second try two frames later.
- **H-C3 Sounds:**
  - One tick per press when returning from the sidebar (muted up to 1 s while focus is restored).
  - The first arrival on a tab button is silent.
  - Hover on the frames' Left and on Continue Watching's Down.
- **H-C4 Timings:**
  - Two frames, then focus the trailer; `startContent` after 300 ms.
  - Entry overlay up at least 2 s, with a safety net 6 s after `startContent`.
  - Carousel tick every 6 s, paused while the sidebar is open or another screen is on top; the first tick after a resume is a full interval away.
  - 400 ms overlay fade.
- **H-C5 Effects order:**
  1. carousel clock
  2. focus-debug log
  3. Continue Watching gone
  4. shelf restore second chance
  5. pending tab focus
  6. sidebar focus
  7. startup
  8. minimum time
  9. safety net
  10. dismiss
  11. sidebar return
  12. offline check
  13. network callback
- **H-C6 Layer order:**
  1. background and frames under the blur (API 31+)
  2. the dim (below API 31)
  3. sidebar
  4. retry banner
  5. entry overlay
  6. close-app handler

---

## 7. Performance design

| | Today | After |
|---|---|---|
| Idle home screen | the whole home function re-runs every 6 s | only the two carousels re-run (they must) |
| Favorites interactions | a filter change, remove mode, a dropdown or the loading flag re-runs the whole 1,342-line function | each section re-runs alone (P1) |
| Favorites constants | ~30 locals, two lists and six button configs rebuilt per composition | built once at file level (P4) |
| Home shelf code | three copies of the `ShelfStack` call | one |

Speed is measured before and after (needs your OK to drive the boxes):
- the idle home screen over 30 s, where the 6 s spikes should go;
- the Favorites grid D-pad, the same way as the details baseline;
- the home shelf D-pad.

---

## 8. Gates and tests

**Build gates:**
- **`checkMethodSizes`** extends to `favorites/` and `home/`:
  - While the split runs, each function has a ratchet ceiling about 1% / 2.5% above its size: `FavoritesScreen` 5,720 / 214, `HomeScreenComposeContent` 5,080 / 121. A ceiling drops at each stage and goes once the function is under budget.
  - Then one budget holds for `details/`, `favorites/` and `home/` (since L).
  - The report on the rest of the app drops to 2,500 units, to show the next candidates (done in L; the ratchet code went with it).
- **The boundary gate** gains rules:
  - Outside code reaches Favorites only through `FavoritesScreenActivity` and `openFavoritesScreen` (live since F1).
  - `favorites/` uses no details code but the two details Activities and `DetailsExtra` / `putDetailsExtras`, and nothing in `home/` (live since F1).
  - Outside `home/`, only `HomeScreenCompose.kt` uses it, and only `HomeScreen` (live since H1). Like all code outside `details/` and `favorites/`, `home/` reaches those only through their entry points.
- **`verifyReleaseOnDevice`** stays as it is.

**JVM tests.** The Stage 0 record pins what can be read today; the rest arrives as the logic is lifted out (as in the details redesign):
- **Favorites:**
  - `toFavoriteEntry`: the genres JSON and every fallback;
  - `filterAndSort`: every type, genre and sort;
  - `gridTarget`: edges and the last-row rule;
  - the details extras for a movie and a show.
- **Home:**
  - the sidebar return-target mapping;
  - tab neighbours;
  - when Continue Watching counts as shown;
  - the entry overlay's dismiss rule;
  - the carousel pause rule;
  - the details extras built from a `MediaItem`.

**On the TV:** one test round per screen, with the checklists in §11. You run them.

---

## 9. Found while reading, kept as they are

| # | What | Plan |
|---|---|---|
| 1 | Favorites' type tabs move focus with `Handler.postDelayed(50 ms)`, which is not cancelled if the page closes inside that window | kept; candidate fix after the split |
| 2 | Favorites resets every piece of state when it leaves the screen; that state is discarded anyway, so the code does nothing | kept: since F2 it is the child composable `FavoritesResetOnDispose`, still effect 1 of 7; it goes only with your OK |
| 3 | The home function re-runs every 6 s | fixed in H1 (CarouselClock): a performance fix, nothing on screen changes |
| 4 | The TV Shows and Anime shelves log a line on every recomposition | kept (HomeShelfTab.renderLog); since H1 the screen no longer recomposes on every tick, so most of those lines are gone |
| 5 | Favorites and Home build their own details-launch extras, with `""` and `"N/A"` defaults (Home adds budget and revenue), unlike `DetailsIntents` | kept per screen, pinned by tests |
| 6 | Found in F1. In Favorites' press-and-prefetch, a failed details request fails its `async`, which cancels the pressing coroutine. The `try`/`catch` logs that and clears the navigating flag, but the request's exception still travels up to the screen's coroutine scope. What that does on a box (a crash, or a scope that stops launching) is untested | kept: `prefetchDetailsLaunch` starts the requests in the same scope as before; candidate fix after the split |
| 7 | Found in F2. Undo's focus restore asks the card requesters that existed before the undo. The restored cards make the list longer, so the grid gets new requesters, and the old ones are attached to nothing: focus is probably not put back on a card. Untested | **fixed in v415** (2026-10-26): Undo restores focus through the grid's latest requesters (`FavoritesGridFocus.requestCardFocus`) |
| 8 | Found in F2. In remove mode, two OK presses on one card within 150 ms record it twice in the removed list. Accept then toggles it twice, which puts it back in the favourites on the server. Undo adds it twice, and two cards with one key can crash the grid. Untested | **fixed in v415** (2026-10-26): `FavoritesData.startRemoval` ignores a second OK on a card that is already leaving; 3 JVM tests |

---

## 10. Stages and test rounds

| Stage | What moves | Risk |
|---|---|---|
| **0** | Safety net: record both screens' details extras, baseline sizes and speed (your OK to drive the boxes), extend the gates with the two ratchets | none |
| **F1** | Favorites into `favorites/` (Activity + screen); constants to `FavoritesStyle` (P4); `FavoriteEntry` with its mapping, and `FavoritesLaunch` with typed extras, with tests | low |
| **F2** | Favorites state holders and their pure functions, with tests; effects into child composables, in order | medium |
| **F3** | Favorites sections: control bar, grid and card, empty states, confirm overlay. The root is about 150 lines and comes off the ratchet | medium (focus) |
| **Round A** | the Favorites checklist (F1–F12) | |
| **H1** | Home screen into `home/` (the Activity stays); constants to `HomeStyle`; `CarouselClock` (P1); `HomeShelves` (one call, keyed by tab) | medium |
| **H2** | Home state holders: focus map, sidebar, entry transition, tabs, shelf state; effects in order | medium–high (focus) |
| **H3** | Home sections: top frames, Continue Watching panel, tabs, sidebar, offline retry. The root is about 150 lines and comes off the ratchet | medium |
| **Round B** | the Home checklist (H1–H14) | |
| **L** | Lock it in: one budget for the three folders, the boundary rules, final measurements (your OK), notes | low |
| **Round C** | smoke test of both screens, plus the speed check | |

**Favorites goes first:**
- it has the highest register count (209, close to the 287 at which the TV function broke);
- it currently shares an R8 class with details code (§1);
- it is self-contained;
- it is away from the other session's shelf files.

Each stage is its own build and commit, and only a round's last build goes on the onn box. No build is published until you say so (ground rule 7).

---

## 11. Checklists

### Favorites

| # | Check |
|---|---|
| F1 | Opens with "Favorite Content"; the cards load and focus lands on the first card with no sound |
| F2 | Grid D-pad: Left, Right, Up and Down at the usual pace. Down past the last full row lands on the last card. Up from the first row goes to Filter, with a sound |
| F3 | Filter (genres) and Sort (5 orders) change the grid; the grid fades while a dropdown is open |
| F4 | All Media, Movies and TV Shows switch the type; focus hops to the neighbouring tab; the sounds are as before |
| F5 | "No content found for this filter" when nothing matches |
| F6 | Opening a movie and a show: the press dip, then the details page; Back returns to Favorites |
| F7 | Remove Media: the header changes, the red tint, focus on the first card; a pressed card disintegrates and focus moves to its neighbour |
| F8 | Back in remove mode opens "Exit Remove Mode" with the count. Accept keeps the removals (still gone after reopening); Undo restores them |
| F9 | Switching Remove Media off also keeps the removals |
| F10 | Back from the grid goes to Filter; Back again leaves the page |
| F11 | With no favourites: the "No favorite content added yet" panel |
| F12 | Coming back from details refreshes the list with no flash |

### Home

| # | Check |
|---|---|
| H1 | "Welcome back" overlay until the first shelf has cards (at least 2 s, at most about 6.5 s), keys blocked meanwhile; then focus on the trailer frame |
| H2 | Both carousels advance together every 6 s and pause while the sidebar is open or another screen is on top; after a resume the first advance is a full 6 s away |
| H3 | Trailer frame: Left opens the sidebar (not while a trailer is selected); Right goes to trending; Down goes to the next tab's button |
| H4 | Trending: Left goes to the trailer; Down goes to Continue Watching, or to the shelf when there is no history |
| H5 | Movies, TV Shows and Anime switch shelves; focus hops to the next button with no flash onto the trailer; the first arrival is silent, then one tick per move |
| H6 | Each tab's shelves: D-pad; Left on the first card opens the sidebar; Up from the first shelf goes to the tabs |
| H7 | Continue Watching: Down goes to row 2 with a sound; Left goes to the shelf; hidden with no history |
| H8 | Sidebar: blur on the onn box, dim on the Fire Stick; every item opens its screen; Right returns focus to exactly where it was (card included) with one tick |
| H9 | Back on a shelf opens the choice overlay: Top of page, and back to the same card |
| H10 | Back elsewhere opens the sidebar; Back on the sidebar opens the exit overlay |
| H11 | Offline: the banner on open; reconnecting reloads; Retry works |
| H12 | Coming back from details, search or the catalog: Continue Watching refreshes |
| H13 | Titles open from trending, the shelves and Continue Watching |
| H14 | The idle home screen does not hitch every 6 s (speed check) |

---

## 12. Risks

| Risk | Answer |
|---|---|
| Home is the most-used screen, so a focus slip there is the most visible | verbatim moves checked by script, and Round B before anything else on Home |
| Shared checkout with the other session's shelf work | this plan never touches `ShelfStack.kt` or `TvWideLazyRow.kt`; only its own files are committed |
| The Thanos vanish effect (GL surface, per-card controllers) | the vanish layer keeps its place in the tree; remove mode has its own checklist items (F7–F9) |
| R8 inlines moved code back, or repacks classes | the ratchets absorb build-to-build noise; mapping checks at every stage |
| `key(tab)` must dispose and create the shelf stack like the three `if` blocks | checklist items H5, H6 and H9 |
| A static CompositionLocal | kept static, so the carousels recompose exactly as now |

---

## 13. Not in this plan

- Switching `LocalCarouselCycleTick` to a dynamic local (a separate change, measured on its own).
- Unifying the hand-built details launches (Favorites, Home and the other callers) with `DetailsIntents`.
- The History, Search and Media Catalog screens. They are all under 5,000 units today; after Stage L the gate lists everything over 2,500.
- Any visual change.
