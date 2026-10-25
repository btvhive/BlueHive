# Details-screen speed measurements

These scripts recorded the Stage 0 baseline of the details redesign. They are kept so every later stage can be measured the same way. The baseline numbers are in `docs/details-redesign-baseline.md`.

Run them from Git Bash on the PC, with the box connected over adb. Only arrows, OK on a card or an episode tile, and Back are ever sent. Each step waits for the expected screen first.

## Before each script

| Script | Measures | Start from |
|---|---|---|
| `measure_open.sh <dev> <rights> <Activity> <runs>` | details open time ("Displayed") | the Favorites screen; a fresh one always focuses the first card |
| `measure_open_home.sh <dev> <Activity> <runs>` | details open time ("Displayed") | Home, with focus already on the card |
| `measure_frames.sh <dev> <label> <gap> <keys…>` | gfxinfo summary for a burst of key presses | the screen under test |
| `measure_overlay_frames.sh <dev> <reps>` | the heaviest frame when the episode source overlay opens and closes | focus on an aired episode tile of an anime show |

## Rules

- **Discard the first open after a launch.** It's a cold start.
- **Compare each box only with itself.** gfxinfo works differently on Android 7 (AFTMM) and Android 14 (onn).
- **For a before/after comparison, interleave the builds.** Install the kept v376 APK (`..\BlueHive-baselines\v376-app-release.apk`), measure, install the stage build, measure, and repeat.
- **For the overlay test, use an anime show.** There, a stray OK only opens the server prompt. On a regular show, a stray OK would start playback and write watch history.
- **Never press OK on a details page header.** On the Favorites screen it is "Remove from Favorites".
