# Details redesign: Stage 0 baseline

Recorded 2026-10-24 on the v376 code (commit `ac47236`). Stage 0 changed only build checks and tests, not app code:

- The three dex files are byte-identical to the v376 build installed on the onn box.
- The only APK entry that differs is `META-INF/version-control-info.textproto`, where the build records the git commit.

The plan is in `details-redesign-architecture.md`.

---

## Compiled size (release dex)

| Method | Code units | Registers | Status |
|---|---|---|---|
| `TVShowsDetailsScreenContent` | 11,297 | 287 | being split (ratchet ceiling) |
| `MoviesDetailsScreenContent` | 8,682 | 191 | being split (ratchet ceiling) |
| TV episode-row item lambda | 2,035 | 61 | within budget |
| TV `extractAndPlay` | 2,009 | 62 | within budget |
| Movie `extractAndPlayMovie` | 1,807 | 62 | within budget |

The budget is 2,500 units and 100 registers. Of the 922 details methods, only the two main functions exceed it.

**Other app methods over 5,000 units** (reported, not enforced):

| Method | Code units | Registers |
|---|---|---|
| `SidebarFavoritesKt.FavoritesScreen` | 5,653 | 208 |
| `HomeScreenComposeKt.HomeScreenComposeContent` | 5,031 | 118 |
| `ProfileManageActivityKt.ProfileManageScreen` | 5,009 | 133 |

---

## Compiled or interpreted

This uses ART's own compiler on the onn box: `dex2oat --compiler-filter=speed --dump-stats` on this APK.

| Run | Compiled | Not compiled: huge method |
|---|---|---|
| Default limit (10,000 units) | 76,767 of 76,770 | 2 |
| Limit raised to 11,500 | 76,768 of 76,770 | 1 |

**What this shows:**

- Raising the limit past 11,297 compiles exactly one more method.
- The only method between 10,000 and 11,500 units is `TVShowsDetailsScreenContent`.
- So today ART never compiles the TV screen's main function. It runs in the interpreter every time the screen recomposes, on every box.

The other two skipped methods are:

- `o.r0`, a library method at 11,852 units;
- `g0.<clinit>`, skipped as "large method without branches".

---

## APK

| | |
|---|---|
| Size | 20,079,137 bytes, 3 dex files |
| sha256 | `b50cd296b39ea6ba63f51c21148f99ef3154e363e979a1fb43c1ec7baa022747` |
| Copy kept for A/B tests | `C:\Users\TheMo\AndroidStudioProjects\BlueHive-baselines\v376-app-release.apk`, plus its R8 mapping |

---

## Tests and gates

| | Result |
|---|---|
| JVM unit tests | 153 pass, 0 fail. 24 are new: `DetailsExtractionMessagesTest` (22) and `DetailsLaunchExtrasGoldenTest` (2). |
| `verifyReleaseOnDevice` | 12,710 classes, 0 failures (onn box) |
| `checkMethodSizes` | Passes. Proven to fail when a function grows past its ratchet ceiling, and when any other method exceeds the budget. Takes about 4 s. |
| `checkDetailsBoundaries` | Passes. Proven to fail on all three rules, using 4 planted violations. |

**`DetailsLaunchExtrasGolden`** records every extra of the 12 launch paths: player, extractor and details-to-details. Stage 6's `PlayerIntents` tests will check against it.

**Tests that can't be written yet:** `pickNextServer` and the server cache key are local functions inside the screens. Their tests arrive when Stage 6 lifts them into `AnimeServerList`.

---

## Speed

Recorded 2026-10-25 between 00:00 and 00:25, with the same v376 code on both boxes. The scripts and their rules are in `tools/details-perf/`.

**Compare each box only with itself.** gfxinfo works differently on Android 7 and Android 14.

| | onn box | AFTMM |
|---|---|---|
| Device | onn 4K, Google TV (Android 14) | Fire TV Stick 4K (Fire OS 6.7 / Android 7.1) |
| UI resolution | 1920x1080 @ 320 dpi (override on a 4K panel) | 1920x1080 @ 320 dpi |
| Movie | Primetime, opened from Favorites | The Nightmare Before Christmas, opened from Home |
| TV show | Texhnolyze (anime, 20 episodes), opened from Favorites | Grey's Anatomy (Season 2, 27 episodes), from Home |
| Overlay show | Texhnolyze | Liar Game (anime, 26 episodes) |

### Opening a details page

Logcat "Displayed", first open discarded.

| | onn box | AFTMM |
|---|---|---|
| Movie | 226, 441, 217, 210, 426, 198 ms (**median 222**) | 632, 606, 572, 602, 586, 619 ms (**median 604**) |
| TV show | 447, 449, 449, 433, 172, 423 ms (**median 440**) | 625, 589, 615, 602, 639 ms (**median 615**) |

On the onn box the times split into two groups, around 200 and around 440 ms. The TV page usually lands in the slower group; the movie page usually in the faster one.

### Episode row: 10 presses, 0.6 s apart

| | Frames | Janky | p50 | p90 | p95 | p99 | Slow UI thread |
|---|---|---|---|---|---|---|---|
| onn, Right | 162 | 17.9% | 31 ms | 44 | 53 | 61 | 20 |
| onn, Left | 156 | 9.6% | 29 ms | 38 | 44 | 57 | 14 |
| AFTMM, Right | 336 | 43.8% | 12 ms | 31 | 46 | 53 | 77 |
| AFTMM, Left | 331 | 28.1% | 11 ms | 24 | 28 | 44 | 46 |

### Opening and closing the source overlay

Five cycles, 1.8 s apart.

| | Frames | Janky | p50 | p90 | p95 | p99 | Slow UI thread |
|---|---|---|---|---|---|---|---|
| onn | 219 | 7.3% | 27 ms | 53 | 61 | 73 | 16 |
| AFTMM | 202 | 35.2% | 12 ms | 32 | 48 | 53 | 40 |

### The heaviest frame when the overlay opens or closes

From `framestats`. Opening or closing the overlay re-runs the whole TV screen function, which today runs interpreted.

| | Open | Close |
|---|---|---|
| onn | 72.2 and 73.5 ms (UI thread 51.5, 51.3). The first run, 159.4 ms, was warm-up. | 48.4, 59.9, 63.1 ms (UI thread 21.4, 25.7, 43.3) |
| AFTMM | 51.8, 49.3, 55.8 ms (UI thread 41.8, 40.3, 45.8) | 54.8, 48.7, 54.1 ms (UI thread 44.3, 37.8, 42.6) |

**What the numbers say:**

- **Every frame of the TV details screen on the onn box misses the 16.7 ms budget.** Its median is 27–31 ms, so the screen runs at about 30 fps.
- **One press costs three or four frames.** Each overlay open or close spends 40–52 ms on the UI thread for that single press, on both boxes.

### Left behind on the boxes

- **AFTMM:** now has the v376 baseline build (it had the published v370).
  - Its first profile (christoff) was entered; it has no favorites, so it is on a different account from the onn box. Nothing was added or changed.
  - The box was put back to sleep.
- **onn box:** returned to the Favorites screen it was on.

**Still to measure:** loading-overlay recompositions, with a debug build and Layout Inspector, at a later stage.

---

## After each stage

Release builds, measured the same way as above. Each stage is its own commit.

| Stage | Build | TV function | Movie function | Huge methods ART skips | Verified on the onn box |
|---|---|---|---|---|---|
| 0 | v376 | 11,297 units, 287 registers | 8,682 units, 191 registers | 2, including the TV function | 12,710 classes, 0 failures |
| 1 | v377 | 11,339, 280 | 8,704, 193 | not measured | 12,712 classes, 0 failures |
| 2 | v378 | **9,800, 251** | **6,307, 163** | **1: the TV function is compiled now** | 12,715 classes, 0 failures |
| 3 | v379 | 9,576, 237 | 6,084, 163 | not re-measured (still under the limit) | 12,717 classes, 0 failures |
| 4 | v380 | 8,637, 233 | 5,073, 155 | not re-measured (still under the limit) | 12,721 classes, 0 failures |
| 5 | v382 | 5,220, 155 | 3,721, 134 | not re-measured (still under the limit) | 12,728 classes, 0 failures |
| 6 | v387 | 5,200, 154 | **2,569, 92** | not re-measured (still under the limit) | 12,734 classes, 0 failures |
| 7 | v391 | **3,726, 117** | 2,010, 89 | not re-measured (still under the limit) | 12,718 classes, 0 failures |
| 8 | v396 | 3,563, 99 | 1,994, 90 | not re-measured (still under the limit) | 12,720 classes, 0 failures |
| 9 | v400 | **2,860, 67** | **1,505, 56** | not re-measured (still under the limit) | 12,724 classes, 0 failures |
| 10 | v402 | **2,142, 57** | 1,515, 56 | not re-measured (every details method is under 2,500) | 12,725 classes, 0 failures |

- **Stage 1** only moved code. R8 then inlined the five moved helpers into both functions, which is why they grew slightly.
- **Stage 2** took the TV function under ART's 10,000-unit limit, so ART compiles it instead of interpreting it (`dex2oat --dump-stats`: 76,730 of 76,732 methods compiled). The `checkMethodSizes` ceiling keeps it under the limit. Its speed has not been measured yet.
- **Stage 3** replaced the two copies of the five header buttons with one. The functions shrink less than in Stage 2, because each screen keeps what its presses do as lambdas, and those compile separately anyway. The APK lost 16 KB.
- **Stage 4** moved the trailer, favorite and Extras state into holders that the header, backdrop, trailer layer and Extras row read themselves. A trailer press, a favorite toggle or an Extras toggle now re-runs those pieces instead of the whole screen function. Both functions lost about 1,000 units.
- **Stage 5** put both screens on `DetailsScaffold`. The play area and the overlays are slots, so each compiles as a method of its own and re-runs on its own. The TV play-area slot is 2,542 units / 110 registers, 61 of them the variables it captures. That is just over the 2,500 / 100 budget, and no trimming fixes it, so `checkMethodSizes` ratchets pieces of the TV function (2,570 / 112) until Stage 8 splits the play area by what it uses.
- **Stage 6** (four commits, v384–v387) moved the movie screen's playback into `shared/playback`: the player intents, the request, the loading session and its overlays, the source chain and the anime flow. The movie function is 2,569 units / 92 registers, under the register budget, even though R8 inlines the three `remember…` functions back into it while it is their only caller. It also reads no playback state any more: the Back handlers and the playback effects read it in small composables of their own, so the prompt opening, an extraction starting or ending, a failover or a toast re-runs only the pieces that use them, not the whole movie function. The TV screen switches over in Stage 7.
- **Stage 7** (four commits, v388–v391) put the TV screen on the same playback core: the loading session and its overlays, the source chain, the anime flow and the Back handlers, then one shared call for both screens' anime server overlays. The TV function went from 5,200 / 154 to 3,726 / 117. With the three `remember…` functions called from both screens, R8 no longer inlines them into either one, and the movie function (2,010 / 89) and the TV play-area slot (2,406 / 92) are now under the 2,500 / 100 budget: only the TV function is still on the ratchet. Neither screen function reads playback state any more, so the prompt, the picker and a loading start, failover or end re-run only the pieces that show them.
- **Stage 8** (five commits, v392–v396) split the TV screen's seasons and episodes into the plan's files: SeasonAvailability (the date rules and the season check), EpisodesState, TvFocusMap and UnairedGate (the state), EpisodeCard (one tile, taking plain values so unchanged tiles skip), EpisodeRow and SeasonColumn, EpisodeSourceOverlay and EpisodeAnimeButtons, and TvPlayArea, so the screen's play-area slot is one call. The TV file went from 2,808 to 902 lines. Every details method except the TV screen function is under the 2,500 / 100 budget (the largest is EpisodeCard at 2,006 / 67); the TV function, at 3,563 / 99, is now the screen's state setup and effects, which Stages 9-10 take on. The three values the row remembered per composition are file-level constants (P4), and opening a source overlay re-runs the row and the overlay, not the screen function (P1).
- **Stage 9** (four commits, v397–v400) took what the two Activities did into shared code and per-screen Args: `DetailsIntents` builds the details-to-details launches (pinned by `DetailsIntentsTest`), `DetailsBackNavigation` holds Back and the history stack, and `TvShowDetailsArgs` / `MovieDetailsArgs` read the launch extras once. Each screen function then left its Activity's file for its own (`TvShowDetailsScreen.kt`, `MovieDetailsScreen.kt`; the `-keep` rules went with them) and takes the Args instead of one parameter per extra. That alone took the TV function from 3,540 / 96 to 2,860 / 67 and the movie function from 1,997 / 88 to 1,505 / 56: Compose checks and stores every parameter of a restartable function, and its restart call passes them all in consecutive registers. The TV function is under the register budget now; its units are Stage 10's. The Activity files are 113 and 121 lines.
- **Stage 10** (three commits, v401–v402) locked it in. The TV function handed five blocks to the files that own their state, each called where it was, so every effect keeps its place (C5): the four label strings to `TvInfoLabels`, the 6 s unaired-press expiry to `UnairedGate`, and the first-focus retry loop, the watched-marks refresh and the season loads to `EpisodesState`. That took it from 2,860 / 67 to 2,190 / 56, and arming an unaired episode no longer re-runs the screen function. `checkMethodSizes` lost its ratchet: every details method is held to 2,500 / 100. Then the two `-keep` rules went, so R8 optimises the screen classes like the rest of the app: TV 2,142 / 57, movie 1,515 / 56, and the device verifier passes. The largest details method is the TV function at 2,142 units, down from 11,297 before the redesign, and every details file is under 760 lines. Speed has not been re-measured.

## Speed after the movie peek layout (v447, onn box)

Measured 2026-10-26 23:25-23:33 on the box's clock, at the user's word, with nobody using the box: profile marlan, The Nightmare Before Christmas opened from Favorites (Primetime, yesterday's movie, is no longer in that profile's favourites). v376 cannot go back on the box without wiping the sign-in, so this is v447 against yesterday's v376 numbers, not an interleaved A/B.

### Opening the movie page

Logcat "Displayed", 18 warm opens (first open dropped). `measure_open.sh` and `measure_open_home.sh` now match the Activity's new package (`details.moviesdetails`); they had found nothing since the redesign moved it.

| | Fast group | Slow group | Median |
|---|---|---|---|
| v376, 2026-10-25 (Primetime, 6 opens) | 198, 210, 217, 226 | 426, 441 | 222 |
| v447 (Nightmare Before Christmas, 18 opens) | 162-194 (5 opens) | 353-434 (13 opens) | 380 |

Each group is as fast as yesterday's or faster; the slow group now comes up far more often. Whether that is today's changes or the different movie is not known without an A/B on the same movie.

### Frames on the movie page (`measure_movie_page.sh`)

| | Frames | Janky | p50 | p90 | p99 | Slow UI thread |
|---|---|---|---|---|---|---|
| Idle, 20 s | 0 | - | - | - | - | 0 |
| Header <-> Watch buttons (4 presses) | 40 | 17.5% | 24 ms | 29 | 40 | 2 |
| Slide to the row and back (x3) | 195 | 15.4% | 38 ms | 57 | 73 | 24 |
| Row, 10 Rights | 241 | 8.7% | 25 ms | 32 | 44 | 8 |

- The idle page draws nothing.
- The row is lighter than the TV episode row was at baseline (p50 31 ms, p99 61).
- The slide is the heaviest thing on the page: about 32 frames per slide at a 38 ms median, so roughly 26 fps for its 350 ms.

### The slide as one GPU layer: tried, no better (v448 / v449)

Same box, same session, two runs each of `measure_movie_page.sh`, 2026-10-27 00:00-00:20. v448 moved the page with `graphicsLayer { translationY }`; v449 is the same code with the old placement offset.

| Slide to the row and back (x3) | Janky | p50 | p90 | p99 | Slow UI thread |
|---|---|---|---|---|---|
| v448 GPU layer, run 1 | 43.6% | 34 ms | 81 | 150 | 61 |
| v448 GPU layer, run 2 | 36.2% | 44 ms | 69 | 77 | 54 |
| v449 re-placing, run 1 | 37.3% | 26 ms | 81 | 97 | 56 |
| v449 re-placing, run 2 | 29.5% | 27 ms | 65 | 73 | 48 |

The layer was no cheaper (worse at the median), and both have the same slow UI-thread frames, so moving the page is not what costs: it is the work that runs when the row takes focus (the heading's 350 ms grey-to-white fade, the focused card's glow and title). The code stays on the placement offset, which also moves the trailer's SurfaceView. Last night's single v447 run (24 slow frames) was a quiet moment for the box; the v449 runs are the fair comparison. Idle (0 frames), the header D-pad (p50 19-23 ms) and the row (p50 22-27 ms) did not change.

### What the slide was paying for, and the fix (v450)

A system trace of three slides on v449 (`atrace -a com.bluehive.tv gfx view input am sched freq`, read with a small parser in the session scratchpad) showed where the slow UI frames went: 94 of 249 frames were over 16.7 ms, and inside them `Recomposer:recompose` took 925 + 378 ms -- 20-33 ms in each of the worst frames, about nine recompositions per frame -- against 640 + 443 ms of drawing.

The cause was the Recommended Media heading's grey-to-white fade: its colour was animated in RecommendedMediaSection's body, so each frame of the 350 ms fade (on the way in and out) rebuilt the section, the row and every visible card. v450 moves the heading into its own composable that reads the colour only while drawing (`BasicText(color = { ... })`, with the style Material's Text would build) and reads the focus flag through a lambda.

Same box, same session, two runs each:

| Slide to the row and back (x3) | Frames | Janky | p50 | p90 | p99 | Slow UI thread |
|---|---|---|---|---|---|---|
| v449, run 1 | 161 | 37.3% | 26 ms | 81 | 97 | 56 |
| v449, run 2 | 173 | 29.5% | 27 ms | 65 | 73 | 48 |
| v450, run 1 | 216 | 5.1% | 29 ms | 38 | 57 | 7 |
| v450, run 2 | 207 | 3.9% | 25 ms | 38 | 57 | 5 |

Slow UI-thread frames during the slide went from about 50 to 5-7. Idle stayed at 0 frames and the header D-pad at p50 20-21 ms; the row's 10 Rights stayed at p50 22 ms (p99 65-85 ms, varying run to run).


## Efficiency pass: v514 against v516 (onn box, 2026-10-30)

Interleaved A/B on the onn box, 13:47-14:10 on the box's clock, at the user's word, with nobody using it: A = v514's code (before the v515 efficiency pass), B = v516's code, built as 517/519 (A) and 518/520 (B) so they could alternate without a downgrade. One `tools/details-perf/measure_round.sh` per install, profile dre2: The Dark Knight from Home's movie row, SPY x FAMILY (anime, 4 seasons) from Continue Watching, which opens on S2 E5. Each figure below is round 1 / round 2 of that build.

### Opening a page (logcat "Displayed", warm opens, the first after each launch dropped)

| | A (before) | B (after) |
|---|---|---|
| Movie, 12 opens | 268, 402-443 (11 opens); **median 413 ms** | 225-263 (7 opens), 400-439 (5); **median 253 ms** |
| TV show, 10 opens | 416-450, every open; **median 430 ms** | 328-367 (5 opens), 429, 593-619 (4); **median 398 ms** |

The movie page opens fast far more often (7 of 12 against 1 of 12): one layout pass fewer (fix 9). The TV page is split: half its opens are ~80 ms faster, but 4 of 10 took about 600 ms, which A never did. Not explained yet; needs a trace.

### Frames (gfxinfo)

| | A p50 | A p90 | A p99 | A slow UI | B p50 | B p90 | B p99 | B slow UI |
|---|---|---|---|---|---|---|---|---|
| Movie header <-> Watch | 21 / 21 | 29 / 32 | 38 / 42 | 4 / 4 | 21 / 21 | 27 / 26 | 32 / 31 | 4 / 3 |
| Movie slide (x3) | 27 / 27 | 32 / 36 | 65 / 77 | 7 / 6 | 24 / 23 | 32 / 32 | **42 / 40** | 5 / 6 |
| Movie row, 10 Rights | 22 / 21 | 61 / 57 | 73 / 73 | 11 / 12 | 23 / 25 | **31 / 32** | **46 / 42** | 13 / 12 |
| TV episode row, 6 Rights | 31 / 32 | 61 / 57 | 81 / 73 | 18 / 19 | 29 / 29 | 61 / 57 | 69 / 77 | 19 / 17 |
| TV episode row, 6 Lefts | 44 / 38 | 57 / 53 | 81 / 77 | 18 / 14 | **32 / 36** | 48 / 57 | 73 / 81 | 10 / 13 |
| Season picker (OK, D D U U, Back) | 34 / 34 | 44 / 46 | 53 / 48 | 6 / 9 | 29 / 32 | 34 / 44 | 48 / 53 | **1 / 2** |
| TV slide (x3) | 34 / 32 | 42 / 44 | 61 / 65 | 5 / 6 | 31 / 29 | 40 / 42 | 53 / 53 | 11 / 13 |

### The heaviest frame (framestats)

| | A: slowest frame (UI thread) | B: slowest frame (UI thread) |
|---|---|---|
| Episode overlay opens (runs 2-3) | 149 (81), 128 (57) / 145 (74), 144 (72) ms | 120 (14), 129 (22) / 107 (13), 123 (17) ms |
| Episode overlay closes | 59-80 ms (UI 34-52) | 55-76 ms (UI 24-45) |
| Watch press: snap + loading, 1.6 s | 111-137 ms (UI 76-108); 22-32 frames over 16.7 ms | 99-163 ms (UI 55-79); 19-29 frames over 16.7 ms |

### What it shows

- **Clear wins:** movie opens (median 413 -> 253 ms), the movie slide's and row's tails (p99 ~70 -> ~42 ms, row p90 ~59 -> ~32), the overlay opening's UI-thread work (57-81 -> 13-22 ms: fix 7), the season picker's slow UI frames (6-9 -> 1-2: fix 3), and the Watch press's UI thread (peak 76-108 -> 55-79 ms).
- **No change:** the episode row going Right (the bounce fix shows on the Lefts' median, 41 -> 34 ms, not on the Rights).
- **Worse, to look at:** 4 of 10 TV opens at ~600 ms, and the TV slide's slow UI-thread frames (5-6 -> 11-13) although its p50 and p99 improved. Both are TV-page only; the always-present page blur layer (detailsBackdropBlur on API 31+) and the season list's new layer are the first suspects.
- The box ends on B (versionCode 520): later builds must be numbered 521 or above.

### Traced: the slow TV opens and the TV slide (2026-10-30, v523)

- **Slow TV opens (fixed, v523 `f1a6115`).** atrace of the opens: each ~600 ms one spent ~230 ms on the main thread in its first frame decoding the page's built-in art (`ImageDecoder` > `getAndroidPixels`: backdrop_details 1920x1080 75 ms, backdrop_details_gradient 1280x815 90 ms, the caps, glows and title-card frames the rest); the ~350 ms opens found it still in the Resources cache. It alternated open by open once a garbage collection had emptied that cache. Warming the cache from the application did not help. `utilities/HeldArt` now decodes the art once off the main thread at start-up and the pages draw it through `heldPainter` (~14 MB held). Ten opens after: 554, 519 (warm-up still running), then 360-421 ms; no ~650 ms opens.
- **TV slide (no regression).** The render thread is GPU-bound at the same cost on both builds (median 19.2 ms per frame on v514 code, 18.4 on v516; the same rounded-rect, fill and texture ops). On v516 the main thread does about a fifth of the work in those frames (draw 57 ms against 606 ms, recompose 18 against 127, over three slides) and then waits for the render thread (`postAndWait`), which gfxinfo counts as a slow UI-thread frame. Getting the slide under 16.7 ms would mean drawing less per frame (overdraw of the full-screen backdrop, gradient and background), not changing our code.
