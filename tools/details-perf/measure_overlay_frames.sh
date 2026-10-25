#!/bin/sh
# The heavy frame when the episode source overlay opens (and closes).
# Precondition: focus on an AIRED episode tile of an ANIME show, overlay shut.
#
#   measure_overlay_frames.sh <device> <reps>
D="$1"; REPS="$2"
HERE="$(dirname "$0")"
i=1
while [ $i -le "$REPS" ]; do
  adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv reset >/dev/null
  sleep 0.5
  adb -s "$D" shell input keyevent KEYCODE_DPAD_CENTER
  sleep 1.8
  printf "open  %s: " "$i"
  adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv framestats | python -I "$HERE/framestats_max.py"
  adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv reset >/dev/null
  sleep 0.5
  adb -s "$D" shell input keyevent KEYCODE_BACK
  sleep 1.8
  printf "close %s: " "$i"
  adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv framestats | python -I "$HERE/framestats_max.py"
  i=$((i + 1))
done
