#!/bin/sh
# Open-time measurement for a details screen, started from BlueHive's Home
# with focus already on the card to open (Back from details returns to Home
# with focus on the same card).
#
#   measure_open_home.sh <device> <DetailsActivity> <runs>
#
# Only CENTER on the Home card and BACK on the details page are sent, and
# each waits for the expected activity first.
D="$1"; ACT="$2"; RUNS="$3"

wait_for() {
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
  wait_for HomeScreenCompose 40 || { echo "STOP: not on Home before run $i"; exit 1; }
  sleep 3
  adb -s "$D" shell input keyevent KEYCODE_DPAD_CENTER
  wait_for "$ACT" 40 || { echo "STOP: $ACT did not open on run $i"; exit 1; }
  sleep 7
  adb -s "$D" shell input keyevent KEYCODE_BACK
  i=$((i + 1))
done
wait_for HomeScreenCompose 40
echo "--- Displayed since $marker"
adb -s "$D" logcat -d -v time -T "$marker" 2>/dev/null | grep "Displayed com.bluehive.tv/.*$ACT" | sed 's/.*Displayed //'
