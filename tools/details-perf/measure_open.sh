#!/bin/sh
# Open-time measurement for a details screen, started from BlueHive's
# Favorites screen (fresh instance: focus always on the first card).
#
#   measure_open.sh <device> <rights> <DetailsActivity> <runs>
#
#   rights           = DPAD_RIGHT presses from the first card to the title
#   DetailsActivity  = MoviesDetailsScreenCompose | TVShowsDetailsScreenCompose
#
# Only arrows, CENTER on a Favorites card, and BACK on a details page are
# ever sent. Each step waits for the expected activity before going on and
# stops the whole run if it does not appear.
D="$1"; RIGHTS="$2"; ACT="$3"; RUNS="$4"

wait_for() {  # activity-name-fragment, max tenths
  n=0
  while [ $n -lt "$2" ]; do
    adb -s "$D" shell dumpsys activity activities 2>/dev/null | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$1" && return 0
    sleep 0.5; n=$((n + 1))
  done
  return 1
}

marker=$(adb -s "$D" shell 'date "+%m-%d %H:%M:%S.000"' | tr -d '\r')
i=1
while [ $i -le "$RUNS" ]; do
  wait_for FavoritesScreenActivity 40 || { echo "STOP: not on Favorites before run $i"; exit 1; }
  sleep 2
  r=0
  while [ $r -lt "$RIGHTS" ]; do adb -s "$D" shell input keyevent KEYCODE_DPAD_RIGHT; sleep 0.6; r=$((r + 1)); done
  adb -s "$D" shell input keyevent KEYCODE_DPAD_CENTER
  wait_for "$ACT" 40 || { echo "STOP: $ACT did not open on run $i"; exit 1; }
  sleep 6
  adb -s "$D" shell input keyevent KEYCODE_BACK
  i=$((i + 1))
done
wait_for FavoritesScreenActivity 40
echo "--- Displayed since $marker"
adb -s "$D" logcat -d -v time -T "$marker" 2>/dev/null | grep "Displayed com.bluehive.tv/com.example.bluehive.$ACT" | sed 's/.*Displayed //'
