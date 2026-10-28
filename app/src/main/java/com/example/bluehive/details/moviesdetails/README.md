# moviesdetails

The movie details screen, split out of `MoviesDetailsScreenCompose.kt` (2,899 lines before the redesign).

The plan is in `docs/details-redesign-architecture.md`, and the sizes after each stage are in `docs/details-redesign-baseline.md`.

| File | Holds |
|---|---|
| MoviesDetailsScreenCompose.kt | the Activity (same class name, new package) |
| MovieDetailsArgs.kt | typed intent extras |
| MovieDetailsScreen.kt | root: creates the state, plugs into `shared/layout/DetailsScaffold`, and says what each press does |
| MovieSourceGrid.kt | the play area: the 2-column "Watch on …" grid and Watch in English / Japanese, with their four button composables (split out 2026-10-26) |
| MovieInfoLabels.kt | revenue, budget, release date, language: only the lines a title has, stacked with no gaps (`movieInfoRows`), built inside the composable so R8 keeps them out of the screen function |

**Peek layout (v417, 2026-10-26):** this screen has no Extras button. The play buttons sit under the overview, and the recommended row peeks up from the bottom; Down from the bottom play buttons enters the row and slides the page (everything but the background) up 110 dp, and Up goes back. It is `shared/layout/DetailsPeek.kt`, passed to `DetailsScaffold` as `peek`; the TV screen uses it too since v469, and both rows peek from the same place since v476 (`PEEK_ROW_TOP`).

The plan also had `MovieSourceGrid.kt` and `MovieAnimeButtons.kt`. `MovieSourceGrid.kt` was split out on 2026-10-26 (both live in it). It did not shrink `MovieDetailsScreen`'s measured size: the play area is a composable lambda, which already compiles to a method of its own. Moving the metadata strings into `MovieInfoLabels` did (2,233 -> 2,033 units).

Rules, checked on every build:

- Never import anything from `tvshowdetails` (`checkDetailsBoundaries`).
- Code both screens need goes in `details/shared`.
- No method over 2,500 dex code units or 100 registers (`checkMethodSizes`, after each release build).
