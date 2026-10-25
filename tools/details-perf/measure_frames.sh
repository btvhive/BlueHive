#!/bin/sh
# Frame timing for a burst of D-pad presses on the screen that is up now.
#
#   measure_frames.sh <device> <label> <interval_s> <key> [<key> ...]
#
# Resets BlueHive's gfxinfo, sends the keys with <interval_s> between them,
# waits for the last frames, then prints gfxinfo's summary for the window.
D="$1"; LABEL="$2"; GAP="$3"; shift 3

adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv reset >/dev/null
sleep 1
for k in "$@"; do
  adb -s "$D" shell input keyevent "$k"
  sleep "$GAP"
done
sleep 1.5
echo "=== $LABEL"
adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Frame deadline missed" | head -8 | sed 's/^ *//'
