# tvshowdetails

This folder will hold the TV show details screen, split out of `TVShowsDetailsScreenCompose.kt` (4,987 lines today).

The full plan is in `docs/details-redesign-architecture.md`.

| File | Holds |
|---|---|
| TVShowsDetailsScreenCompose.kt | the Activity (same class name, new package) |
| TvShowDetailsArgs.kt | typed intent extras |
| TvShowDetailsScreen.kt | root: creates the state, plugs into `shared/layout/DetailsScaffold` |
| TvFocusMap.kt | focus requesters shared between sections |
| TvInfoLabels.kt | seasons, status, release date, language |
| TvPlayArea.kt | decorations + season column + episode row + source overlay |
| SeasonAvailability.kt | aired/unaired rules + season check (pure, tested) |
| SeasonColumn.kt | the season list |
| EpisodesState.kt | season loads, prefetch, first focus, watched markers |
| EpisodeRow.kt | the episode row + hold-to-repeat |
| EpisodeCard.kt | one episode tile |
| UnairedGate.kt | the press-twice override for unaired episodes |
| EpisodeSourceOverlay.kt | the "Watch on …" grid under a tile |
| EpisodeAnimeButtons.kt | Watch in English / Japanese under a tile |

Rules:

- Never import anything from `moviesdetails`.
- Code both screens need goes in `details/shared`.
