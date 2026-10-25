# moviesdetails

This folder will hold the movie details screen, split out of `MoviesDetailsScreenCompose.kt` (2,899 lines today).

The full plan is in `docs/details-redesign-architecture.md`.

| File | Holds |
|---|---|
| MoviesDetailsScreenCompose.kt | the Activity (same class name, new package) |
| MovieDetailsArgs.kt | typed intent extras |
| MovieDetailsScreen.kt | root: creates the state, plugs into `shared/layout/DetailsScaffold` |
| MovieInfoLabels.kt | revenue, budget, release date, language |
| MovieSourceGrid.kt | the 2-column "Watch on …" buttons |
| MovieAnimeButtons.kt | Watch in English / Japanese + the early-press queue |

Rules:

- Never import anything from `tvshowdetails`.
- Code both screens need goes in `details/shared`.
