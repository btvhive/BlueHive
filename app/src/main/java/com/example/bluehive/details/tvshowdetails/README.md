# tvshowdetails

The TV show details screen, split out of `TVShowsDetailsScreenCompose.kt` (4,987 lines before the redesign).

The plan is in `docs/details-redesign-architecture.md`, and the sizes after each stage are in `docs/details-redesign-baseline.md`.

| File | Holds |
|---|---|
| TVShowsDetailsScreenCompose.kt | the Activity (same class name, new package) |
| TvShowDetailsArgs.kt | typed intent extras |
| TvShowDetailsScreen.kt | root: creates the state, plugs into `shared/layout/DetailsScaffold` with the peek layout (no Extras: Search, gold/bronze Favorites, recommended row under the episodes, since v469) |
| TvFocusMap.kt | focus requesters shared between sections |
| TvInfoLabels.kt | seasons, status, release date, language; missing ones close up (`shared/layout/DetailsInfoLines`) |
| TvPlayArea.kt | the episode row between its caps, the season cap + picker |
| SeasonAvailability.kt | aired/unaired rules + season check (pure, tested); `ReturnCounter`, which counts returns to the page and not Lifecycle's replay on open (v515) |
| SeasonAccordion.kt | the season picker inside the left cap: a list; OK to choose (white tile + gold arrow; playing season dull white, grey while choosing), OK to play (stays choosing), Back to leave (v505-508; list since v502, card wheel before) |
| EpisodesState.kt | season loads, prefetch, first focus, watched markers |
| EpisodeRow.kt | the episode row + hold-to-repeat |
| EpisodeCard.kt | one episode tile |
| UnairedGate.kt | the press-twice override for unaired episodes |
| EpisodeSourceOverlay.kt | the episode overlay over the page: the still, title, overview and "Watch on …" buttons (v458+) |
| EpisodeSnap.kt | the overlay's snap to dust on a Watch press, and the wait for the playback it starts (v463+) |
| EpisodeAnimeButtons.kt | Watch in English / Japanese in the overlay |

Rules, checked on every build:

- Never import anything from `moviesdetails` (`checkDetailsBoundaries`).
- Code both screens need goes in `details/shared`.
- No method over 2,500 dex code units or 100 registers (`checkMethodSizes`, after each release build). Effects moved out of a composable go in a restartable child composable, called where they were: R8 inlines a one-caller `remember…` function straight back. A `remember…` both screens call stays out (v469: `rememberDetailsPeek`, `rememberDetailsHeaderGate`, `rememberDetailsSearch`, `rememberTimedNotice`).
- Animated values are read where they are used -- in a `graphicsLayer`, `drawBehind` / `drawWithCache` or `offset { }` lambda (`offsetWithY`) -- never while composing, or every frame of the animation recomposes what read it (v450, v515). The source-guard tests (`testutil/MainSource`) hold the v515 fixes to that.
