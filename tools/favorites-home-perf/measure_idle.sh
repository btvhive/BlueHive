#!/bin/sh
# Frames while BlueHive sits on screen with no input at all.
#
#   measure_idle.sh <device> <label> <seconds>
#
# Resets BlueHive's gfxinfo, sends nothing for <seconds>, then prints
# gfxinfo's summary for the window and every frame of it as bursts on a
# timeline (framestats_timeline.py). gfxinfo keeps only the last 120 frames,
# so the frame data is sampled every 2 s and merged. On the home screen the
# carousels advance every 6 s; whatever re-runs with them shows up as a burst
# every 6 s.
D="$1"; LABEL="$2"; SECS="$3"
HERE=$(dirname "$0")
TMP=$(mktemp)

adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv reset >/dev/null
n=0
while [ $n -lt "$SECS" ]; do
  adb -s "$D" shell sleep 2
  adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv framestats >> "$TMP"
  n=$((n + 2))
done
echo "=== $LABEL (${SECS} s, no input)"
adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Frame deadline missed" | head -8 | sed 's/^ *//'
python "$HERE/framestats_timeline.py" < "$TMP"
rm -f "$TMP"
