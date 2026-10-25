# Details screens redesign: architecture

**Status:** design only, nothing built yet.

- Written 2026-10-24 against commit `ac47236` (v376), after reading both screens end to end.
- Revised the same day: added the shared frame (§4) and grouped the TV test rounds (§11).

---

## 1. Why: the numbers

| | TV show details | Movie details |
|---|---|---|
| File | `TVShowsDetailsScreenCompose.kt`, 4,987 lines | `MoviesDetailsScreenCompose.kt`, 2,899 lines |
| Main function | `TVShowsDetailsScreenContent`, **2,788 lines** | `MoviesDetailsScreenContent`, **1,887 lines** |
| Compiled size | 11,297 code units, 287 registers | 8,682 code units, 191 registers |
| Rank in the APK | 3rd-largest method in the whole app | 4th |

Only two library methods in the APK are bigger than the TV screen's main function.

What that size has cost so far:

1. **Crashes.** R8 has miscompiled the TV function twice. The first was on 2026-09-21 ("type Conflict"), the second in v371–v375 ("type Long (Low Half)"). Each time ART rejected the whole class, and opening any show crashed the app. The `-keep` rules added after the first crash did not stop the second.
2. **Crashes spread between screens.** The movie source buttons call `usesBackendExtraction()`, which lives in the TV file. So the TV crash also took down movie playback.
3. **Copies drift apart.** Each file has its own copy of:
   - the five header buttons;
   - the source chain and its failover;
   - the Miruro (anime) flow;
   - the loading, error and prompt overlays;
   - the backdrop, logo and overview;
   - back navigation;
   - the `StreamingSource` model.

   Eight files build the "open details" Intent by hand (six other screens plus both Activities). The copies already differ in 15 small ways (§10).
4. **Speed.**
   - All state lives in the one function, and the main `Box` content is inline. So any state that changes while you use the page re-runs the entire 2,788-line function body. That includes the extraction stage ticks, toasts, opening or closing the source overlay, and the loading flags.
   - ART's compiler refuses methods over 10,000 code units (its "huge method" limit). The TV function, at 11,297, most likely always runs uncompiled in the interpreter. This is to be confirmed in Stage 0.

---

## 2. Ground rules

1. **Nothing the viewer can see, hear or press changes.** That means the same pixels, focus moves, sounds, timings, network calls and player extras.
2. **Differences between the two screens are kept**, as parameters. Unifying them is a separate decision, made one item at a time (§10).
3. **Small stages, grouped testing.** Each stage is one build that passes the gates and is committed and revertable on its own. You test the TV after each group of stages (§11), not after every one.
4. **No new runtime libraries.**
5. **A stage that fails a gate or a checklist item doesn't ship**, and nothing is built on top of it.

---

## 3. Target structure

The tree below uses `├──` connectors. Sizes are estimates, in lines.

```
app/src/main/java/com/example/bluehive/details/
├── moviesdetails/                     only the movie screen
│   ├── MoviesDetailsScreenCompose.kt     Activity: intent → MovieDetailsArgs → screen      ~120
│   ├── MovieDetailsArgs.kt               typed intent extras                                ~60
│   ├── MovieDetailsScreen.kt             root: creates the state, plugs into the frame      ~120
│   ├── MovieInfoLabels.kt                revenue, budget (+ refresh), release, language     ~120
│   ├── MovieSourceGrid.kt                the 2-column "Watch on …" buttons                  ~170
│   └── MovieAnimeButtons.kt              Watch in English / Japanese + early-press queue    ~200
│
├── tvshowdetails/                     only the TV screen
│   ├── TVShowsDetailsScreenCompose.kt    Activity                                           ~130
│   ├── TvShowDetailsArgs.kt              typed intent extras (incl. last season/episode)    ~70
│   ├── TvShowDetailsScreen.kt            root: creates the state, plugs into the frame      ~150
│   ├── TvFocusMap.kt                     every focus requester that crosses sections        ~80
│   ├── TvInfoLabels.kt                   seasons, status, release, language                 ~120
│   ├── TvPlayArea.kt                     decorations + seasons + episodes + source overlay  ~150
│   ├── SeasonAvailability.kt             aired/unaired rules + season check (pure, tested)  ~200
│   ├── SeasonColumn.kt                   season list, its keys, reliable focus              ~220
│   ├── EpisodesState.kt                  season loads, prefetch, first focus, watched marks ~300
│   ├── EpisodeRow.kt                     the episode LazyRow + hold-to-repeat               ~200
│   ├── EpisodeCard.kt                    one tile: date, still, badge, fold, scrim, popup   ~380
│   ├── UnairedGate.kt                    press-twice override, 6 s window, notice           ~90
│   ├── EpisodeSourceOverlay.kt           "Watch on …" grid under a tile + its D-pad rules   ~260
│   └── EpisodeAnimeButtons.kt            Watch in English / Japanese under a tile           ~200
│
└── shared/                            used by both; imports neither screen
    ├── layout/
    │   ├── DetailsScaffold.kt            the frame both screens plug into (§4)              ~200
    │   ├── DetailsBackdrop.kt            background, rating + star, backdrop, gradient      ~120
    │   ├── DetailsTrailerLayer.kt        trailer player + release on leave                  ~60
    │   ├── DetailsLogoOrTitle.kt         logo, or the wrapped title when there is none      ~110
    │   ├── DetailsOverview.kt            genres line + overview with the auto-shrink        ~90
    │   ├── DetailsHeaderButtons.kt       Favorites, Extras, Trailer, Rewind, Fast-forward   ~420
    │   ├── DetailsRecommendations.kt     the Extras row and what a pick does                ~80
    │   └── DetailsToasts.kt              the bottom-centre notices                          ~80
    ├── state/
    │   ├── TrailerState.kt               playing / played / extracting / seek               ~90
    │   ├── FavoriteState.kt              check on open + toggle                             ~70
    │   ├── ExtrasState.kt                Extras toggle + recommendations                    ~110
    │   └── DetailsBackHandlers.kt        the three Back handlers, in their fixed order      ~80
    ├── playback/
    │   ├── StreamSource.kt               the one source model (replaces both copies)        ~30
    │   ├── PlaybackRequest.kt            Movie(tmdbId) | Episode(tmdbId, season, ep, name)  ~40
    │   ├── PlaybackSession.kt            the one loading screen: flags, stages, cancel      ~150
    │   ├── SourcePlaybackController.kt   source chain + both failover halves                ~350
    │   ├── AnimePlaybackController.kt    Miruro: backend first, on-device fallback          ~380
    │   ├── AnimeServerList.kt            server cache, pick-next, ensure-list               ~150
    │   ├── PlayerIntents.kt              every player/extractor Intent and all its extras   ~220
    │   ├── ExtractionMessages.kt         error wording, reason parsing, labels, relay url   ~200
    │   └── PlaybackOverlays.kt           loading + trap, error, default-server prompt,      ~150
    │                                     picker
    └── navigation/
        ├── DetailsIntents.kt             one builder for "open details for this title"      ~120
        └── DetailsBackNavigation.kt      history stack; back to History/Favorites/Search    ~120
```

**Budgets**, enforced by the gates in §9:

- No method in `details/` over **2,500 code units or 100 registers**. Today the TV function is at 11,297 and 287.
- No file over about 600 lines.
- Each root screen around 150 lines or less.

**As built (Stage 10):** the tree holds, except for the movie screen. `MovieSourceGrid.kt` and `MovieAnimeButtons.kt` were never split out, so those buttons are still in `MovieDetailsScreen.kt` (757 lines, the longest file). Every method is under the budget. The roots are longer than planned (the TV function is 427 lines, the movie's 466, much of it comments), but at 2,142 and 1,515 units they are well under it.

**The Activity class names stay as they are; only their package changes.** That keeps logs, adb checks and crash reports comparable before and after. A rename can come at the very end if wanted.

**Helper files elsewhere that only the details screens use stay where they are** during the refactor: `DetailsTrailerPlayer.kt`, `PlayerReturnFocus.kt`, the two prompts in `Overlays.kt`, and `ExtractionErrorOverlay.kt`. Moving them is an optional tidy-up afterwards.

---

## 4. The shared frame: `DetailsScaffold`

Both screens draw the same frame around their own content:

- the background, rating, backdrop, trailer and gradient;
- the logo or title, the genres and the overview;
- the five header buttons;
- the Extras switch.

Today each screen writes that frame out by hand. With the scaffold it exists once, and each screen plugs in only what is its own.

**What the scaffold owns:**

- the layer order (C1), from the background down to the play area;
- the Extras switch: the play area while Extras is off, the recommendations row while it is on.

**What each screen plugs in:**

| Slot | Movie | TV |
|---|---|---|
| `labels` | revenue, budget, release, language | seasons, status, release, language |
| `playArea` | anime buttons, or the source grid | decorations, season column, episode row, source overlay |
| `overlays` | its overlay stack, in today's movie order | its overlay stack, in today's TV order |
| hooks | what picking a recommendation does; Up from the row | the same (TV prefetches first) |

**The scaffold:**

```kotlin
@Composable
fun DetailsScaffold(
    info: DetailsInfo,                  // title, logo, backdrop, trailer url, rating, overview, genres
    header: DetailsHeaderSpec,          // focus requesters, enable flag, press timing
    trailer: TrailerState,
    favorite: FavoriteState,
    extras: ExtrasState,
    onOpenRecommendation: (MediaItem) -> Unit,
    onUpFromRecommendations: () -> Unit,
    labels: @Composable BoxScope.() -> Unit,
    playArea: @Composable BoxScope.() -> Unit,
    overlays: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        DetailsBackdrop(info, trailer)          // background, rating, star, backdrop
        DetailsTrailerLayer(info, trailer)
        DetailsGradient(info)
        DetailsLogoOrTitle(info)
        labels()
        DetailsOverview(info)                   // genres + overview
        DetailsHeaderButtons(header, trailer, favorite, extras)   // incl. "Loading trailer…"
        if (!extras.isOn) playArea()
        else DetailsRecommendations(extras, onOpenRecommendation, onUpFromRecommendations)
        overlays()
    }
}
```

**A root screen** (movie shown; TV has the same shape plus its season and episode state):

```kotlin
@Composable
fun MovieDetailsScreen(args: MovieDetailsArgs, onOpenDetails: (MediaItem) -> Unit) {
    val scope    = rememberCoroutineScope()
    val trailer  = rememberTrailerState()
    val favorite = rememberFavoriteState(args, sendTitle = false)
    val extras   = rememberExtrasState(args, loadOn = Dispatchers.IO)
    val session  = rememberPlaybackSession(scope)
    val anime    = rememberAnimePlayback(session, PlaybackRequest.Movie(args.tmdbId), …)
    val sources  = rememberSourcePlayback(session, PlaybackRequest.Movie(args.tmdbId), …)
    DetailsBackHandlers(extras, anime, session)   // always right after the holders (C2)
    // … focus restore on player return, budget/revenue refresh, diagnostics …
    DetailsScaffold(
        info = args.info, header = …, trailer = trailer, favorite = favorite, extras = extras,
        onOpenRecommendation    = { trailer.stop(); onOpenDetails(it) },
        onUpFromRecommendations = { … },
        labels   = { MovieInfoLabels(args, budgetRevenue) },
        playArea = { if (args.isAnime) MovieAnimeButtons(…) else MovieSourceGrid(…) },
        overlays = { /* the movie's overlay stack, in the C1 order */ },
    )
}
```

**Rules:**

1. **The scaffold only arranges.** It holds no state of its own and registers no Back handlers.
2. **The slots are `BoxScope` lambdas** called inside the scaffold's `Box`. Everything they draw lands in the same `Box` at the same absolute positions as today.
3. **The overlay stack stays per screen.** The two screens stack their notices in a different order today (§10, #14).
   - Most of those pairs can never be on screen together.
   - One rare pair can: on movies, a "not available yet" notice still fading out while the server prompt opens.
   - So the order is kept exactly, rather than argued away.

**What it buys:**

| | |
|---|---|
| One copy of the frame | The layer order and the Extras switch exist once instead of twice, so they can't drift. |
| Small roots | About 120 lines (movie) and 150 (TV), instead of about 300. |
| Early crash protection | From Stage 5, the play area and the overlays compile as separate methods. The method R8 keeps miscompiling shrinks halfway through the plan, not at the end. |
| Early speed win | The play area and the overlays become their own recomposition scopes from Stage 5 (P1). |

---

## 5. Dependency rules

```
  Home · Search · Catalog · Favorites · History · Continue Watching
                        │  start the two Activities
                        ▼
  moviesdetails ─────►  shared  ◄───── tvshowdetails
          ✗ never import each other ✗
                        │
                        ▼
          api · models · utilities · webview · auth
```

1. **`moviesdetails` and `tvshowdetails` never import each other.** A crash or a bad compile in one screen can't reach the other. That was the v371 failure.
2. **`shared` never imports either screen.**
3. **Nothing outside `details/` uses a screen's internals.** Only the two Activities, and `DetailsIntents` after Stage 9, are public.
4. **A build check enforces this (§9).** Kotlin's `internal` can't: it covers the whole app module.

---

## 6. How state works

**Decision: use plain state-holder classes that hold Compose snapshot state, made by `rememberXxx()` functions. No ViewModels or Flows.**

**Why:** a write to Compose state lands in the same frame it's made, exactly like today's `var x by remember { mutableStateOf(…) }`. Several focus fixes depend on that same-frame ordering:

- the server picker grabs focus in the frame `showServerPicker` turns on;
- `RestoreFocusOnPlayerReturn` steps aside while a modal is open.

A Flow collected into Compose arrives a frame later and would bring those races back. ViewModels also bring nothing on a TV, since the screen never rotates.

Every holder has this shape:

```kotlin
@Stable
class TrailerState internal constructor() {
    var isPlaying by mutableStateOf(false); internal set
    var hasPlayed by mutableStateOf(false); internal set
    // …
    fun stop() { isPlaying = false; hasPlayed = false }
}

@Composable
fun rememberTrailerState(): TrailerState = remember { TrailerState() }
```

Rules:

1. **All page work runs in the screen's `rememberCoroutineScope()`,** which is passed into the holders.
   - Cancellation works as it does today: leaving the page stops the work.
   - `runCatchingCancellable`, `launchIfActive` and `ensureActive` stay at the same points, so the v350 crash fix stays intact.
2. **Activity-result launchers are still created in composition** with `rememberLauncherForActivityResult`, inside the holder's `remember…` function, and handed to the holder.
3. **The two "state hops" stay exactly as they are during the move stages:** `failoverRequest` and `playbackFailedSource`. That includes running the next chain step in the screen's scope, not the effect's. Simplifying them is a later step, with tests.
4. **One `PlaybackSession` per screen, shared by the source chain and the anime flow.** Today the two flows share these flags: `isExtracting`, `extractPending`, `extractStage`, `extractJob` and `extractCancelled`.
   - Sharing them is what stops two extractions running at once.
   - It also gives a single loading screen and a single Back-to-cancel.
   - Splitting them would be a behaviour change.
5. **The root screen owns every FocusRequester that more than one section uses**, including `lastStreamFocusRequester`. They live in `TvFocusMap` or the movie equivalent. Sections are given requesters; they never create ones that other sections request.
6. **Holders take plain values from the Args:** tmdbId, mediaType, title and profileId. Nothing reads `LocalActivity.current as? XxxActivity` to get the profileId any more.
7. **No holder registers a Back handler.** Holders expose their "is open" flags and their close actions. `DetailsBackHandlers` registers all three handlers (C2).

---

## 7. Contracts every stage must keep

### C1: Layer order

The root `Box` draws in this order, and D-pad traversal follows it. From Stage 5, `DetailsScaffold` owns every step from the background down to the play area. The overlay stack after it is written out in each root, because the two orders differ (§4, rule 3).

**TV:**

1. background
2. content rating
3. star + score
4. backdrop (hidden once the trailer has played)
5. trailer layer
6. gradient
7. info block: logo or title, labels, genres, overview
8. Favorites, Extras and Trailer buttons
9. "Loading trailer…"
10. Rewind, Fast-forward
11. either the frame decorations, season column, episode row and source overlay, or the recommendations
12. loading overlay
13. error overlay
14. no-dub toast
15. checking-servers overlay
16. default-server prompt
17. server picker
18. server-error toast
19. unaired notice
20. not-yet-available toast

**Movie:**

1. background
2. content rating
3. star + score
4. backdrop
5. trailer
6. gradient
7. logo or title
8. revenue
9. budget
10. release
11. language
12. genres
13. overview
14. Favorites, Extras and Trailer buttons
15. "Loading trailer…"
16. Rewind, Fast-forward
17. either the anime English/Japanese buttons or the source grid, or the recommendations
18. loading overlay
19. no-dub toast
20. not-yet-available toast
21. error overlay
22. checking-servers overlay
23. default-server prompt
24. server picker
25. server-error toast

Pieces that position themselves are written as **`BoxScope` extension functions**, as `TvDetailsInfoBlock` already is. They emit straight into the screen's `Box`, so every absolute offset and the order stay exactly as they are now. **No wrapper `Box` or `Column` may be added around them.**

### C2: Back button order

Who answers Back, first to last:

1. the focused element's own key handler (episode tile, overlay buttons, focus traps), and any overlay's own handler while that overlay is showing;
2. the extraction-cancel handler;
3. the prompt/picker handler;
4. the Extras-collapse handler;
5. the Activity's history back.

Compose gives the last-registered handler priority. **`DetailsBackHandlers` registers 4, 3 and 2 in that order, and each root calls it right after creating its state holders**, before anything else is composed. It is called unconditionally, outside any `if`. A handler inside a section that's only sometimes shown would jump the queue.

### C3: Timings

| What | Value |
|---|---|
| Pre-launch delay | 200 ms |
| Stage dots | 900 / 2,500 / 3,500 ms, restarted for each chain attempt |
| Toasts | 2.5 s (no dub, server error), 4 s (not yet available) |
| Unaired override window | 6 s |
| Anime early-press wait | 8 s |
| Episode overview popup | appears after 1.5 s; 280 ms fade in, 160 ms fade out |
| TV recommendation click | waits 1 s while prefetching |
| Episode hold-to-repeat | 140 ms |
| Season-check timeout | 3 s |

### C4: Launch paths

Every Intent carries exactly today's extras. Unit tests on `PlayerIntents` pin them before anything switches over.

| Launch | TV | Movie |
|---|---|---|
| `HlsPlayerActivity` (source chain) | adds SEASON / EPISODE / EPISODE_NAME | n/a |
| `MiruroPlayerActivity`, after a backend extract | `anime_tv` tags, season + episode | `anime_movie` |
| `MiruroPlayerActivity`, after an on-device extract | `animeRelayUrl` fallback | same |
| `MiruroExtractorActivity`, extract / enumerate | capture-servers and server-name flags | same |

### C5: Effects order

The root calls its state hooks in the same relative order the code runs today. Each hook keeps its own effects in their current order. Examples:

- the TV first-focus retry loop;
- the correction when the listed seasons change;
- refreshing watched markers on resume;
- where `RestoreFocusOnPlayerReturn` sits relative to the launchers.

### C6: Sounds

Every `play…Sound()` call stays on the same event.

### C7: Exact strings and offsets

- **Label padding.** It was tuned by hand and differs between the screens:
  - "Language:" is followed by 8 spaces on TV and 9 on movies;
  - "Release Date:" is followed by 5 spaces on TV and 6 on movies.
- **Header button press timing.** TV uses 110/80 ms and movies use 180/140 ms.
- **The backdrop's accessibility label.** It reads "TVShowsDetailsScreenCompose backdrop" on TV and "Movie backdrop" on movies.

---

## 8. Performance design

| | What it does |
|---|---|
| **P1: narrow recomposition** | Each section is its own restartable composable that reads only its own state. A stage tick re-runs only the loading overlay. Opening a source overlay re-runs only the episode row and the overlay, not the header. Today both re-run the whole 2,788-line function. The scaffold delivers the first big step of this at Stage 5. |
| **P2: late reads** | Values that change during use are read in the leaf, or passed as `() -> T`: the stage, overlay anchor, armed episode and toasts. |
| **P3: skippable episode cards** | `EpisodeCard` takes plain values: episode, isWatched, isUnaired, isSelected, total and a requester. Cards whose values didn't change are skipped when the row recomposes. Keys stay `tmdbEpisodeId`, and a `contentType` is added. |
| **P4: compute once** | `isJapaneseAnimation`, the labels and the source lists are remembered. Static Modifiers for the decorations are hoisted out of composition. |
| **P5: nothing new to pay for** | No Flow or ViewModel layer, and no new libraries. |
| **P6: compiled, not interpreted** | Every details method ends far below ART's 10,000-unit huge-method limit (the budget is 2,500), so ART can compile them. |

**Measurements**, taken before (Stage 0) and after (Stage 10) on two boxes:

- the AFTMM, the weakest box (Android 7);
- the onn box.

Builds are interleaved and the first run is discarded.

| Metric | How |
|---|---|
| Open details → first frame | logcat `ActivityTaskManager` "Displayed" |
| Episode row while holding Right | `gfxinfo framestats`: p50 / p90 / p99, janky % |
| Loading overlay | recompositions per stage tick (debug build, Layout Inspector) |
| Huge methods | `dex2oat --dump-stats`, "not compiled: huge method" count |
| APK size | release APK bytes |

Target: every number equal or better.

**Optional later win:** the baseline profile has **0 entries** for the details screens, because its generator can't get past sign-in to reach them. Adding them would precompile these screens at install.

---

## 9. Gates and tests

**Build gates**, run on every `assembleRelease`:

1. **`verifyReleaseOnDevice`.** Already exists since v376.
2. **`checkMethodSizes`, new.**
   - It reads the release dex and fails the build if any method in `com.example.bluehive.details` is over 2,500 code units or 100 registers.
   - Until Stage 10 the functions being split had ratchet ceilings instead; since then every method in `details/` is held to the budget.
   - It also warns about the app's other giants: `FavoritesScreen` (5,653), `HomeScreenComposeContent` (5,031) and `MediaCatalogScreen` (3,507).
3. **`checkDetailsBoundaries`, new.** It fails the build if `moviesdetails` imports `tvshowdetails` or the other way round, or if `shared` imports either.

**Unit tests (JVM)**, written in Stage 0 before anything moves. They pin today's behaviour of everything pure:

- `extractionErrorMessage` (every branch);
- `parseExtractReason`;
- `pickNextServer`;
- the server cache keys;
- `animeRelayUrl`;
- `miruroSourceLabel`;
- the `PlayerIntents` extras for every launch path.

The existing `SeasonAvailabilityTest`, `UnairedEpisodeTest` and `SourceCatalogTest` must keep passing.

**On the TV, each test round (§11).** You run these; I don't drive the TV.

- **Smoke test (every round):**
  - open a movie, a show, an anime movie and an anime show;
  - play one source on each;
  - check that Back from the player lands on the same button;
  - check that Back leaves to wherever you came from.
- **The checklist items** (§12) for the stages in that round.
- **Screenshots**, with your OK and only when nobody's watching. The same 8 states are captured before and after, and compared pixel by pixel.

---

## 10. Differences between the screens, kept as they are

| # | Today | Kept as |
|---|---|---|
| 1 | Header button press: TV 110/80 ms, movie 180/140 ms | a timing parameter |
| 2 | When the error overlay closes, TV returns focus to the source button; movies don't | a parameter |
| 3 | Favorite toggle: TV sends the title, movies don't | a parameter (movie favorites may be stored without a title) |
| 4 | Picking a recommendation: TV prefetches the next page for 1 s; movies open at once | the screen's own hook |
| 5 | Opening in Extras: TV uses the prefetch cache; movies always fetch | a parameter |
| 6 | Anime: TV stops the trailer when English/Japanese is pressed; movies stop it when the prompt is answered | call sites unchanged |
| 7 | Anime Miruro link: TV finds it by name; movies take the 3rd entry | same result today; unified only with a test |
| 8 | The movie anime notice says "try VidFast below", but anime movies have no VidFast button | kept, and flagged as a bug |
| 9 | Toast wording: "episode" vs "title"; the not-yet-available text differs | parameters |
| 10 | Dead code: the hard-coded source lists in both `getStreamingSources` can never run | removed in Stage 7d (no behaviour change); their notes moved to SourceCatalog's built-in lists |
| 11 | Movie Up-from-recommendations asks for focus twice | kept (harmless) |
| 12 | TV releases the trailer player a second time when the page closes; `DetailsTrailerPlayer` already releases it | a parameter (redundant but harmless) |
| 13 | Extras loads recommendations on the main thread on TV, and on a background thread on movies | a parameter |
| 14 | The overlay stack order differs: the not-available notice sits under the error message on movies and over it on TV | each root writes its own stack |
| 15 | The backdrop's accessibility label differs | a parameter |
| 16 | Loading headline: for an extraction that is not the source chain's, TV shows the session's status line when one is set; movies always show the stage words. In practice both show the stage words, because only the chain sets that line and it clears it | a parameter (`useBackendStatus`) |
| 17 | Anime launch: movies stop the trailer when an extraction starts; TV already stopped it on the English/Japanese press (#6) | a parameter (`onLaunch`) |
| 18 | A source press: TV records the episode and selects it (`stopTrailerAndSelectEpisode`); movies only stop the trailer | the screen's own `onStart` |
| 19 | Log wording: TV lines name the episode ("s1e2", "episode"), movie lines say "movie" | derived from the request |
| 20 | Details-to-details launches: movies send budget and revenue, and fall back to "movie" / "" for a missing type or title; TV sends its season count and status, and its Back also sends the budget and revenue it never reads | one branch per screen in `DetailsIntents`, pinned by `DetailsIntentsTest` (Stage 9a) |

Found while moving the playback core (Stages 6–7) and **not** kept, because nothing can tell them apart:

- BACK during loading now logs one line on both screens (the movie's). Logs only; the crash and diagnostic reports capture the whole process log, not by tag.
- The movie's "Default server" called `stopTrailer()` just before `launchExtract`, which stops the trailer itself on its next line. The duplicate went with the shared prompt (7d).

Each of these can be unified later as its own small change. That's your call, item by item.

---

## 11. Stages and test rounds

### Stages

| Stage | What moves | Risk |
|---|---|---|
| **0** | Safety net: baseline numbers, unit tests pinning behaviour, the two new gates (report-only) | none (the app doesn't change) |
| **1** | Create `details/` and move both files into it (package change only). Move the shared helpers (`usesBackendExtraction` etc.) to `shared/playback`, so movies stop depending on the TV file. Update the manifest, the 6 callers, the tests and the keep rules. | low (compile-checked) |
| **2** | Shared display pieces: backdrop, trailer layer, gradient, logo/title, genres + overview, toasts | low |
| **3** | One `DetailsHeaderButtons` (including "Loading trailer…"), with the timing set per screen | low–medium (focus) |
| **4** | `TrailerState`, `FavoriteState`, `ExtrasState`, `DetailsRecommendations`. `DetailsBackHandlers` takes over the Extras handler. The prompt and extraction handlers move in at Stages 6 and 7, registered after it. | medium |
| **5** | `DetailsScaffold`: both screens plug into the frame, and the play area and the overlays become slots | medium |
| **6** | Playback core, **movie screen first**: StreamSource, PlaybackRequest, PlaybackSession, both controllers, PlayerIntents, the loading and error overlays. Done in four commits, 6a–6d (v384–v387). | highest |
| **7** | The TV screen switches to the same playback core. The default-server prompt, picker and "Checking servers" overlays join PlaybackOverlays here, where the two screens' copies can be compared. | high |
| **8** | TV seasons and episodes: availability, season column, episode state, row, card, unaired gate, source overlay, play area, focus map | high (focus) |
| **9** | Activities: Args, `DetailsBackNavigation`, `DetailsIntents`. Done in four commits, 9a–9d (v397–v400); in 9d each screen function moved to its own file (`TvShowDetailsScreen.kt`, `MovieDetailsScreen.kt`) and takes the Args, and the two `-keep` rules moved with them. Optional: the 6 outside callers use `DetailsIntents` too (not done). | low–medium |
| **10** | Lock it in: remove the two `-keep` rules, enforce the size and boundary gates, take the final measurements, update the notes. Done in three commits, 10a–10c (v401–v402): the TV function's labels and effects moved beside their state (2,860 → 2,190 units), the ratchet went (one budget for every method), then the `-keep` rules. The speed re-measurement on the boxes is not done yet. | low |

### Test rounds

| Round | After stages | What you test |
|---|---|---|
| 1 | 1–3 | the smoke test, plus the header buttons on both screens (D-pad, sounds, toggles: M2, M3) |
| 2 | 4–5 | the trailer, favorites, Extras and recommendations, and Back order (M2–M4, T11), plus the smoke test |
| 3 | 6 | every movie playback item (M5–M9) |
| 4 | 7 | every TV playback item (T7–T9) |
| 5 | 8 | every TV season and episode item (T1–T6, T10) |
| 6 | 9–10 | back navigation (M10, T12), then the full checklist once |

- Every stage is still its own build (all gates) and its own commit. A problem found in a round can be traced to the exact stage by installing the stage builds in between.
- Only a round's last build goes on the onn box for you.
- Nothing is published until you say so.
- The movie screen goes first at Stage 6 because it has no episodes. The controllers are proven on the simpler screen before the TV screen uses them.

---

## 12. TV checklist (full)

**Movie**

| | Check |
|---|---|
| M1 | Open from Home. You see the logo or title, rating, release date, language, genres and overview. Revenue and budget appear after a moment. |
| M2 | Favorites: add and remove; the label flips; it survives reopening. |
| M3 | Trailer: play and pause; rewind and fast-forward while it plays; the backdrop comes back when a source starts. |
| M4 | Extras shows recommendations. Back closes Extras. Up from the row lands on Extras. Picking a title opens it, and Back returns to the previous title in Extras. |
| M5 | Pressing a source shows the loading screen with stage text, then it plays. Back from the player puts focus on the same button. |
| M6 | Back during loading closes the overlay and returns focus, and no player opens later. |
| M7 | Failover shows "trying 2 of N" and the amber note. If every source fails, the error message names them. |
| M8 | Anime movie: English opens the default-server prompt, and Default plays. Choose opens the picker, and a server plays. Back from the picker goes to the prompt; Back from the prompt goes to the button. |
| M9 | An anime press made before the title resolves still opens. An unmapped title shows the notice. |
| M10 | Back leaves to where you came from: Home, History, Favorites or Search. |

**TV**

| | Check |
|---|---|
| T1 | Open a show. Seasons are listed with none unaired, Season 1's episodes show, and focus is where you expect. |
| T2 | From Continue Watching, you land on the right season and episode. |
| T3 | Switching seasons twice quickly gives the right episodes with no flicker. Right from a season goes to the last-focused episode. |
| T4 | Holding Right scrolls the episode row at a steady pace, and Right on the last episode is silent. The overview pops up after 1.5 s. The air date is above the still; the watched fold and episode badge show. |
| T5 | Unaired episode: the first press plays the error sound and shows the notice with the date. A second press within 6 s opens it. Moving away cancels it. |
| T6 | Episode overlay: OK shows "SELECT A SITE…" and the grid. The D-pad moves inside the grid (2 columns, lone last button). Back goes to the tile; Back from a tile goes to its season. |
| T7 | A source plays. Back from the player lands on the same button, or its tile. |
| T8 | Back during loading works. An error message can be dismissed, and focus returns to the button. |
| T9 | Anime show: English and Japanese go through the prompt and picker flows. A brand-new show shows the notice. |
| T10 | After you watch an episode, its watched fold appears when you come back. |
| T11 | Extras shows recommendations. Back closes it, and picking one opens it. |
| T12 | Back leaves to where you came from. |

**Both:** the navigation ticks, clicks, back-out and error sounds play where they do today.

---

## 13. Risks

| Risk | Guard |
|---|---|
| Focus moves differently (most likely) | The root owns shared requesters. Modifier chains move verbatim, including the load-bearing ones noted in `MediaCard`. Checklist T1–T8 and M4–M6. |
| Back answers in the wrong order | C2: `DetailsBackHandlers` registers the handlers unconditionally, in a fixed order, right after the state holders. |
| Something shifts on screen | `BoxScope` extensions and slots with no wrapper layouts; screenshot diff. |
| The frame changes how the screen is composed | The slots becoming their own recomposition scopes is intended: it's the speed win. Keys, effects and focus requesters are unchanged. Tested in round 2. |
| A timing changes | The C3 list; the state hops are kept during the moves. |
| R8 miscompiles again partway through | The verifier gate runs on every build, and from Stage 5 the method R8 kept miscompiling is already split. |
| A stage is left half-done | Each stage is one commit, revertable on its own, and nothing is published until your test pass. |
| Moving the Activity package breaks something | Every reference is compile-checked. No string references exist outside the `-keep` rules and comments. The manifest is updated in the same commit. |

---

## 14. Not in this plan

- Fixing the differences in §10. Those are separate decisions.
- The other big screens: `FavoritesScreen` (5,653 units), `HomeScreenComposeContent` (5,031) and `MediaCatalogScreen` (3,507). They carry the same risk, and the size gate will report them.
- Moving details-only helpers out of `utilities/` and `webview/`.
