#!/bin/sh
# Frame timing on the movie details page's peek layout (2026-10-26).
#
#   measure_movie_page.sh <device>
#
# Start from BlueHive's Favorites screen with focus on a MOVIE card (a fresh
# Favorites screen focuses the first card). Opens it with CENTER, waits for
# the recommended row to load, then measures, each from a gfxinfo reset:
#   idle    20 s with no input
#   buttons Down, Down, Up, Up between the header and the Watch buttons
#   slide   Down into the row and Up out of it, three times (the 110 dp slide)
#   row     10 Rights along the recommended row, 0.6 s apart
# and goes Back to Favorites. Only CENTER on the Favorites card, arrows and
# Back are sent; OK is never pressed on the details page.
D="$1"
HERE=$(dirname "$0")

wait_for() {
  n=0
  while [ $n -lt "$2" ]; do
    adb -s "$D" shell dumpsys activity activities 2>/dev/null | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$1" && return 0
    sleep 0.5; n=$((n + 1))
  done
  return 1
}
key() { adb -s "$D" shell input keyevent "$1"; sleep "${2:-0.6}"; }

wait_for FavoritesScreenActivity 40 || { echo "STOP: not on Favorites"; exit 1; }
key KEYCODE_DPAD_CENTER 1
wait_for MoviesDetailsScreenCompose 40 || { echo "STOP: the movie page did not open"; exit 1; }
sleep 8

sh "$HERE/../favorites-home-perf/measure_idle.sh" "$D" "movie page idle" 20 | head -9

# Focus starts on Favorites in the header.
sh "$HERE/measure_frames.sh" "$D" "header <-> Watch buttons (Down Down Up Up)" 0.6 \
  KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_UP

# To the bottom-left Watch button, then the slide in and out.
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN 1
sh "$HERE/measure_frames.sh" "$D" "slide to the row and back (Down Up x3)" 1.0 \
  KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP

# Into the row, then along it.
key KEYCODE_DPAD_DOWN 1.2
sh "$HERE/measure_frames.sh" "$D" "row, 10 Rights" 0.6 \
  KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT \
  KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT

key KEYCODE_BACK 2
wait_for FavoritesScreenActivity 20 || echo "NOTE: Back did not land on Favorites"
