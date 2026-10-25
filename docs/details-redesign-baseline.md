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
