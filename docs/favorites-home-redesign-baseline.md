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

**Not measured yet: it needs your OK to drive the boxes.** When it has it, the plan is the details redesign's method (`tools/details-perf/`), on each box, compared only with itself:

| Measurement | How | Keys sent |
|---|---|---|
| Idle home screen | gfxinfo and the slowest frames over 30 s with no input; the 6 s carousel tick should show | none |
| Home shelf D-pad | 10 presses, 0.6 s apart, on the first shelf (`measure_frames.sh`) | Right, then Left |
| Favorites grid D-pad | 10 presses along a row, then 5 down (`measure_frames.sh`) | Right, Down |

Favorites is opened through the sidebar, one screenshot per step. In Favorites only arrows are sent: OK in remove mode deletes a favourite.

## After each stage

| Stage | Build | FavoritesScreen | HomeScreenComposeContent | Verified on the onn box |
|---|---|---|---|---|
| 0 | v405 code (the APK was up to date: no app code changed) | 5,660 units, 209 registers | 5,030 units, 118 registers | v405: 12,726 classes, 0 failures |
