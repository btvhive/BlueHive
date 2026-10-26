# Favorites and Home redesign: baseline

Stage 0 of `docs/favorites-home-redesign-architecture.md`, recorded 2026-10-25 on the v405 code (commit 27f18e6), before anything in the two screens moves.

---

## Method sizes

Release build, dexdump of the APK mapped through R8's `mapping.txt`, the way `checkMethodSizes` matches methods.

| Method | Code units | Registers | Gate |
|---|---|---|---|
| `SidebarFavoritesKt.FavoritesScreen` | **5,660** | **209** | ratchet, ceiling 5,720 / 214 |
| `HomeScreenComposeKt.HomeScreenComposeContent` | **5,030** | **118** | ratchet, ceiling 5,080 / 121 |

Everything else written in the two files is under the 2,500 / 100 budget already. The largest, of 161 methods in all:

| Method | Code units | Registers |
|---|---|---|
| Favorites' press-and-prefetch coroutine (`navigateToDetails`) | 1,556 | 32 |
| Favorites' grid cell (the `itemsIndexed` content) | 1,127 | 58 |
| a Home lambda (`HomeScreenComposeKt$$ExternalSyntheticLambda47`) | 1,034 | 32 |
| `TvShowsRadioButton` | 821 | 53 |
| `AnimeRadioButton` | 709 | 50 |
| `MoviesRadioButton` | 699 | 49 |

**Where R8 put `FavoritesScreen`.** In v405 it ships inside `kotlin.reflect.KClasses`, together with `toFavoriteEntry` and details code: `RecommendedMediaSection`, `MovieInfoLabels`, `DetailsTrailerLayer`, `TrailerFailedNotice`, `AnimeServerOverlays`, `PlaybackLoadingOverlay`, `PlaybackErrorOverlay` and `rememberExtrasState`. ART verifies a whole class at once, so an R8 miscompile of `FavoritesScreen` would also crash those details pieces. R8 repacks on every build.

## Details launches (recorded)

| Record | Launch | Extras |
|---|---|---|
| `FavoritesLaunchExtrasGolden` | a movie (MoviesDetailsScreenCompose) | 15 |
| | a show, or any type but "movie" (TVShowsDetailsScreenCompose) | 17 |
| `HomeLaunchExtrasGolden` | a show (TVShowsDetailsScreenCompose) | 16 |
| | a movie, or any type but "tv" (MoviesDetailsScreenCompose) | 16 |

Read off `navigateToDetails` and `HomeScreenCompose.openDetailsScreen` by script, then checked by the same script against the source: the same keys in the same order, each value from the source the record names. The two screens route an unknown media type to opposite screens (Favorites to TV, Home to movie); both kept.

## Tests and gates

| | Result |
|---|---|
| JVM unit tests | 194 pass, 0 fail. 4 are new: the two records' consistency tests. |
| `checkMethodSizes` | Passes, with the two ratchets. Proven to fail when a function is over its ceiling (a ceiling lowered for one run, not committed). |
| `checkDetailsBoundaries` | Passes. The Favorites and Home rules arrive with their folders (F1, H1). |
| `verifyReleaseOnDevice` | Skipped for this build: no app code changed (v405 verified 12,726 classes, 0 failures). |

## Speed

Measured 2026-10-25 between 16:58 and 17:22 UTC (12:58–13:22 on the boxes' clocks), with the user's OK and nobody watching. The scripts are `tools/details-perf/measure_frames.sh` and `tools/favorites-home-perf/` (see its README). **Compare each box only with itself:** gfxinfo counts differently on Android 7 and Android 14.

| | onn box | Fire Stick (AFTMM) |
|---|---|---|
| Android | 14 | 7.1.2 |
| BlueHive | v405 | v376 (installed for the details baseline) |
| Profile | marlan | christoff (Favorites: marlan, see below) |

The Fire Stick's v376 has the same Favorites and home-screen code as v405; only the details package imports differ. Its shelf code predates the other session's uncommitted shelf changes, which the onn box's v405 has. It was not upgraded: v405 is untested on Android 7, and going back would mean wiping the app's data.

### Idle home screen: 30 s, no input

| | onn box (3 runs) | Fire Stick (2 runs) |
|---|---|---|
| Frames | 600, 598, 684 | 715, 714 |
| Janky | 9.3%, 9.4%, 11.8% | 23.5%, 20.2% |
| Median / 90th / 99th percentile | 34–38 / 53 / 89–97 ms | 12–13 / 20–22 / 46–48 ms |
| Slow UI-thread frames | 45, 38, 45 | 75, 61 |
| Rhythm | a burst of about 58 frames every 3 s, two per 6 s carousel cycle | about 63 frames every 3 s |
| UI time of each burst's first frame | 31–59 ms, median 47 | 13–44 ms, medians 31 and 25 |
| Heaviest UI frame of each burst | 47–65 ms, median 55 | 28–49 ms |

The home screen is drawing about two seconds of every three while it sits idle. A burst's first frame is the carousel change, and it includes the home function re-running for the tick; H1 takes that re-run out.

### Home shelf D-pad: 10 presses from the first card of What's Popular

| | onn Right | onn Left | Fire Stick Right | Fire Stick Left |
|---|---|---|---|---|
| Frames | 318 | 290 | 921 | 809 |
| Janky | 19.2% | 16.6% | 34.4% | 29.5% |
| Median / 90th / 95th / 99th percentile | 38 / 53 / 69 / 81 ms | 36 / 57 / 65 / 97 ms | 13 / 26 / 32 / 44 ms | 10 / 21 / 26 / 42 ms |
| Slow UI-thread frames | 43 | 30 | 111 | 69 |

The carousels keep animating during the presses, as they do for a viewer.

### Favorites grid D-pad: profile marlan, 8 favourites

With 8 favourites (7 in the first row), the presses were 6 Rights then 6 Lefts along the first row, and Down + Up three times between the two rows. Only arrows were sent.

| | onn, row | onn, rows | Fire Stick, row | Fire Stick, rows |
|---|---|---|---|---|
| Frames | 24 | 12 | 24 | 12 |
| Janky | 50% | 50% | 50% | 41.7% |
| Median / 90th / 99th percentile | 40 / 61 / 73 ms | 38 / 53 / 57 ms | 17 / 24 / 29 ms | 13 / 21 / 23 ms |
| Slow UI-thread frames | 12 | 6 | 12 | 5 |
| UI time per press (first frame) | | 19–40 ms | | 7–18 ms |

Every press draws two frames, and one of them is a slow UI-thread frame on both boxes.

### How the boxes were driven

- **onn box:** BlueHive was restarted to the home screen. One Down landed on the first card. Left on the first card opened the sidebar, and Favorites was four Downs below Profiles.
- **Fire Stick:** woken, BlueHive started as christoff. christoff has no favourites, so the profile was switched to marlan for the Favorites measurement, then back to christoff, which leaves the picker order as it was. The box was then returned to Amazon's home screen and put back to sleep.
- **Key timing:** `adb shell input` takes about 1.4 s per key on Android 7, so the Fire Stick's presses landed about 2 s apart instead of 0.6 s.

## After each stage

| Stage | Build | FavoritesScreen | HomeScreenComposeContent | Verified on the onn box |
|---|---|---|---|---|
| 0 | v405 code (the APK was up to date: no app code changed) | 5,660 units, 209 registers | 5,030 units, 118 registers | v405: 12,726 classes, 0 failures |
| F1 | v406 | 4,977 units, 141 registers (ceiling now 5,030 / 145) | 5,030 units, 118 registers | v406: 12,728 classes, 0 failures (checked at 18:01 UTC, after the build: both boxes were playing video at build time) |
| F2 | v408 | 3,837 units, 95 registers (ceiling now 3,880 / 97) | 5,030 units, 118 registers | v408: 12,733 classes, 0 failures (checked at 18:33 UTC, after the build: the box was in use at build time) |
| F3 | v409 | 1,131 units, 45 registers: under the budget, off the ratchet | 5,030 units, 118 registers | v409: 12,737 classes, 0 failures (18:49 UTC), then installed on the onn box for Round A (18:50 UTC) |
| H1 | v410 | 1,131 units, 45 registers | 4,153 units, 99 registers (ceiling now 4,190 / 101) | not yet: the box is in Round A |
| H2 | v412 | 1,131 units, 45 registers | 3,012 units, 67 registers (ceiling now 3,040 / 69) | v412: 12,748 classes, 0 failures (00:47 UTC 2026-10-26); not installed |
| H3 | v413 | 1,131 units, 45 registers | 1,076 units, 34 registers: under the budget, off the ratchet | v413: 12,749 classes, 0 failures (00:54 UTC 2026-10-26); installed on the Fire Stick 00:57 UTC, which showed its account lockout; installed on the onn box for Round B instead, about 01:05 UTC (home screen reached, no crash) |

### F3 (v409)

- **FavoritesScreen: 3,837 → 1,131 units, 95 → 45 registers.** The UI is now seven section composables, and the root keeps the state, the effects and the layout skeleton. `checkMethodSizes` holds it to the 2,500 / 100 budget like everything else in `favorites/`.
- **The sections:**

| Method | Code units | Registers |
|---|---|---|
| `FavoriteCard` | 1,465 | 77 |
| `RemoveConfirmOverlay` | 1,304 | 61 |
| `FavoritesTypeTabs` | 887 | 47 |
| `FavoritesControlBar` | 880 | 50 |
| `NoMatchesPanel` | 590 | 31 |
| `NoFavoritesPanel` | 495 | 30 |
| `FavoritesGrid` | 464 | 33 |

- **Gates:**
  - JVM unit tests: 224 pass (no new ones: F3 moves UI only).
  - `checkMethodSizes`: passes. Only `HomeScreenComposeContent` is left on the ratchet.
  - `checkDetailsBoundaries`: passes.

### F2 (v408)

- **FavoritesScreen: 4,977 → 3,837 units, 141 → 95 registers**, under the register budget for the first time. Its fifteen pieces of state are now three holder references, and its seven effects are child composables.
- **The new code:**

| Method | Code units | Registers |
|---|---|---|
| `FavoritesData.load` (the fetch) | 594 | 31 |
| `FavoritesGridEffects` (effects 3 to 6) | 461 | 23 |
| `FavoritesData.commitRemovals` | 205 | 20 |
| `FavoritesResetOnDispose` (effect 1) | 176 | 14 |

- **R8 still packs FavoritesScreen with other code:** in v408 it is `kotlin.io.ByteStreamsKt` again, now next to the details header buttons, `DetailsBackNavigation` and `TvWideLazyRow`. F3 brings it under 2,500 units.
- **Gates:**
  - JVM unit tests: 224 pass. 16 are new: `FavoritesFilterTest` (8), `FavoritesGridFocusTest` (5, one of which checks `gridTarget` against the old arrow branches for every grid up to 30 cards, 1 to 8 to a row) and `FavoritesDataTest` (3). One more is the other session's.
  - `checkMethodSizes`: passes with the lower ceiling.
  - `checkDetailsBoundaries`: passes.

### F1 (v406)

- **FavoritesScreen: 5,660 → 4,977 units, 209 → 141 registers.** What moved out of its source: the ~30 constants, the two lists, the six buttons' configs (their `Color` and `Dp` values included), and the press-and-prefetch (whose coroutine was already a method of its own).
- **The rest of `favorites/` is under the budget, and `checkMethodSizes` now enforces that.** The largest:

| Method | Code units | Registers |
|---|---|---|
| `prefetchDetailsLaunch` (the press-and-prefetch) | 1,160 | 29 |
| the grid cell (the `itemsIndexed` content) | 1,127 | 58 |
| `FavoritesStyleKt.<clinit>` (every constant, built once) | 651 | 60 |
| `navigateToDetails`' coroutine, now only the launch | 382 | 32 |
| `toFavoriteEntry` | 203 | 28 |

- **R8 still packs FavoritesScreen with other code.** In v406 it ships in `kotlin.io.ByteStreamsKt`, next to `DetailsBackHandlers`, `DetailsToast`, the extraction messages and `TvWideLazyRow`. F2 and F3 keep shrinking it.
- **Gates:**
  - JVM unit tests: 207 pass. 13 are new: `FavoriteEntryTest` (5) and `FavoritesLaunchTest` (8).
  - `checkMethodSizes`: passes. Proven to fail with the budget lowered to 1,100 units (it flagged two `favorites/` methods) and with the ceiling below 4,977 (lowered for one run, not committed).
  - `checkDetailsBoundaries`: passes. Proven to fail with throwaway files. It flagged `DetailsIntents` and `home` used from `favorites/`, and `FavoriteEntry` used from outside. It let `DetailsExtra` and `openFavoritesScreen` through.

### H1 (v410)

- **HomeScreen (was HomeScreenComposeContent): 5,030 → 4,153 units, 118 → 99 registers.** What left it: the two extra `ShelfStack` calls (one call now, keyed by tab) and the read of the carousel tick. The constants were at file level already; they moved to `HomeStyle`, with the tab buttons' configs, which were built on every composition.
- **Idle cost:** the screen no longer re-runs every 6 s; only the two carousels do (they must). Measured in Round C with your OK; the idle baseline is above.
- **The new code**, all under the budget: the tab buttons 645–773 units, the screen's largest lambda 1,034 / 32.
- **Gates:**
  - JVM unit tests: 228 pass. 4 are new: `HomeShelvesTest` (the per-tab table against the three old calls, and the pause rule).
  - `checkMethodSizes` now covers `home/`. `HomeScreen` keeps a ratchet until H3.
  - `checkDetailsBoundaries`: outside `home/`, only `HomeScreenCompose.kt` may use it, and only `HomeScreen`. Proven to fail with a throwaway file (it flagged `HomeScreen` and `HomeShelfTab`, and let `homeScreenSectionRules` through).
- Seen in the report of methods outside the budget: `ProfileManageScreen` at 5,009 units (reported only; not part of this plan).

### H2 (v412)

- **HomeScreen: 4,153 → 3,012 units, 99 → 67 registers.** Its 31 pieces of state are now five holders: `HomeEntryTransition`, `HomeSidebarState`, `HomeShelfState`, `CatalogTabsState` and `HomeFocusMap`. Their properties keep the old variable names, so every read is the old read with a holder in front.
- **Effects:** 2 to 6 are `HomeFocusEffects` (440 units), 7 to 10 are `HomeEntryEffects` (322), and 11 is `SidebarReturnEffect` (251), called where the effects were. Effect 1 is still `CarouselClockTicker`; 12 and 13 stay in the screen until H3.
- **Gates:** JVM unit tests 233 pass, 5 new in `HomeStateTest` (tab neighbours, when Continue Watching counts as shown, start values, `openSidebar`). `checkMethodSizes` and `checkDetailsBoundaries` pass.

### H3 (v413)

- **HomeScreen: 3,012 → 1,076 units, 67 → 34 registers.** The UI is six sections, called where their code was: `HomeTopFrames` (316 units), `HomeContinueWatchingPanel` (515), `HomeCatalogTabs` (908), `HomeShelves` (738), `HomeSidebar` (566) and `OfflineRetry` (381, with effects 12 and 13). The sidebar dim, the entry overlay and the Back handler stay in the root, in their layer order.
- State only one section used moved into it: the two frame states, the banner flag and its coroutine scope, the nav-sound lambda, and the sidebar-return mute cap.
- **The ratchet list is empty:** every function the two redesigns split is under 2,500 / 100.
- **Gates:** 233 JVM tests pass; `checkMethodSizes` and `checkDetailsBoundaries` pass.

