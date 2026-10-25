# moviesdetails

The movie details screen, split out of `MoviesDetailsScreenCompose.kt` (2,899 lines before the redesign).

The plan is in `docs/details-redesign-architecture.md`, and the sizes after each stage are in `docs/details-redesign-baseline.md`.

| File | Holds |
|---|---|
| MoviesDetailsScreenCompose.kt | the Activity (same class name, new package) |
| MovieDetailsArgs.kt | typed intent extras |
| MovieDetailsScreen.kt | root: creates the state, plugs into `shared/layout/DetailsScaffold`; also the 2-column "Watch on …" buttons and Watch in English / Japanese |
| MovieInfoLabels.kt | revenue, budget, release date, language |

The plan also had `MovieSourceGrid.kt` and `MovieAnimeButtons.kt`. They were not split out: the movie screen function is well under the budget without them.

Rules, checked on every build:

- Never import anything from `tvshowdetails` (`checkDetailsBoundaries`).
- Code both screens need goes in `details/shared`.
- No method over 2,500 dex code units or 100 registers (`checkMethodSizes`, after each release build).
