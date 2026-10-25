# Favorites and Home speed measurements

These scripts recorded the Stage 0 speed baseline of the Favorites and Home redesign. They are kept so every later stage can be measured the same way. The numbers are in `docs/favorites-home-redesign-baseline.md`.

Run them from Git Bash on the PC, with the box connected over adb. The D-pad bursts use `tools/details-perf/measure_frames.sh`.

| Script | Measures | Start from |
|---|---|---|
| `measure_idle.sh <dev> <label> <seconds>` | every frame while nothing is pressed, as bursts on a timeline (gfxinfo keeps only 120 frames, so it samples every 2 s and merges) | the home screen, loaded and settled |
| `../details-perf/measure_frames.sh <dev> <label> 0.6 <keys…>` | gfxinfo summary for a burst of key presses | the screen under test |
| `framestats_timeline.py` | reads `dumpsys gfxinfo com.bluehive.tv framestats` from stdin; prints the bursts | (helper) |

## The baseline procedure

1. **Idle home:** restart BlueHive to the home screen and wait about 15 s after the profile is chosen. Run `measure_idle.sh <dev> "idle home" 30`.
2. **Home shelf:** one Down from the home screen's first focus lands on the first card of What's Popular (check with a screenshot). Then `measure_frames.sh` with 10 × `KEYCODE_DPAD_RIGHT`, and again with 10 × `KEYCODE_DPAD_LEFT`.
3. **Favorites grid:** Left on the first card opens the sidebar. Favorites is four Downs below Profiles; check with a screenshot, then OK. With focus on the first card, run 6 Rights then 6 Lefts, then Down + Up three times. Never press Up from the first row: it leaves the grid.

## Rules

- **Only arrows in Favorites.** OK in remove mode deletes a favourite.
- **Screenshot before every OK.** The sidebar ends in **Reboot**, and on the profile picker **Sign Out** sits below the tiles.
- **Compare each box only with itself, against the same build.** gfxinfo counts differently on Android 7 (Fire Stick) and Android 14 (onn box).
- **Put a box back as you found it:** the profile it had, the screen it was on, and asleep if it was.
