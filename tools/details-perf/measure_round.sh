#!/bin/sh
# One full speed round on the onn box for the build that is installed (v516
# efficiency check, 2026-10-30). Start: BlueHive freshly launched to Home.
#
#   measure_round.sh <device> <label> <outdir>
#
# Route (profile marlan's Home): Down from the first focus lands on the
# movie row's first card (The Dark Knight); Up, Right, Right reaches the
# Continue Watching panel, OK enters it, Down x3 is SPY x FAMILY (anime,
# 4 seasons), which opens on S2 E5's tile. Back from that page lands on the
# same Continue Watching entry.
#
# Sends only arrows, OK on Home cards and Continue Watching, OK on an episode
# tile, OK on Watch in English and on "Default server" -- and Back 1.6 s
# later, which cancels the extraction before any player opens -- and Back.
# Never OK on a details header.
D="$1"; LABEL="$2"; OUT="$3"
HERE=$(dirname "$0")
mkdir -p "$OUT"
LOG="$OUT/$LABEL.txt"
: > "$LOG"

wait_for() {
  n=0
  while [ $n -lt "$2" ]; do
    adb -s "$D" shell dumpsys activity activities 2>/dev/null | grep -m1 -E "topResumedActivity" | grep -q "$1" && return 0
    sleep 0.5; n=$((n + 1))
  done
  echo "STOP: waited for $1" | tee -a "$LOG"; exit 1
}
key() { adb -s "$D" shell input keyevent "$1"; sleep "${2:-0.6}"; }
back_home() {  # Back until Home is up (a Back can land mid-transition and be lost)
  for t in 1 2 3; do
    key KEYCODE_BACK 1.5
    adb -s "$D" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity | grep -q HomeScreenCompose && return 0
  done
  wait_for HomeScreenCompose 20
}
frames() { sh "$HERE/measure_frames.sh" "$D" "$@" >> "$LOG"; }
peak() {  # label: heaviest frames since the last reset
  printf "%s: " "$1" >> "$LOG"
  adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv framestats | python -I "$HERE/framestats_max.py" >> "$LOG"
}
reset() { adb -s "$D" shell dumpsys gfxinfo com.bluehive.tv reset > /dev/null; }

wait_for HomeScreenCompose 60
sleep 10
marker=$(adb -s "$D" shell 'date "+%m-%d %H:%M:%S.000"' | tr -d '\r')

# ---------------------------------------------------------------- movie
key KEYCODE_DPAD_DOWN 1.5                      # The Dark Knight
for i in 1 2 3 4 5 6; do
  key KEYCODE_DPAD_CENTER 0.2
  wait_for MoviesDetailsScreenCompose 40
  sleep 7
  key KEYCODE_BACK 0.2
  wait_for HomeScreenCompose 40
  sleep 3
done

key KEYCODE_DPAD_CENTER 0.2
wait_for MoviesDetailsScreenCompose 40
sleep 8
frames "movie header <-> Watch (D D U U)" 0.6 KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_UP
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN 1
frames "movie slide (D U x3)" 1.0 KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP
key KEYCODE_DPAD_DOWN 1.2
frames "movie row (10 R)" 0.6 KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT \
  KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
back_home
sleep 3

# ---------------------------------------------------------------- TV
key KEYCODE_DPAD_UP 1.2; key KEYCODE_DPAD_RIGHT 0.8; key KEYCODE_DPAD_RIGHT 0.8
key KEYCODE_DPAD_CENTER 1.5
key KEYCODE_DPAD_DOWN 0.8; key KEYCODE_DPAD_DOWN 0.8; key KEYCODE_DPAD_DOWN 0.8
for i in 1 2 3 4 5 6; do
  key KEYCODE_DPAD_CENTER 0.2
  wait_for TVShowsDetailsScreenCompose 40
  sleep 7
  if [ $i -lt 6 ]; then
    key KEYCODE_BACK 1.2; key KEYCODE_BACK 0.2      # tile -> season picker -> Home
    wait_for HomeScreenCompose 40
    sleep 3
  fi
done
adb -s "$D" exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o "Season 2 of 4" | head -1 >> "$LOG" \
  || echo "check: season picker label not found" >> "$LOG"

frames "tv episode row (6 R)" 0.6 KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
frames "tv episode row (6 L)" 0.6 KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT

sh "$HERE/measure_overlay_frames.sh" "$D" 3 >> "$LOG"

for i in 1 2 3; do
  key KEYCODE_DPAD_CENTER 1.8                   # the overlay
  key KEYCODE_DPAD_CENTER 1.2                   # Watch in English -> server prompt
  reset; sleep 0.5
  key KEYCODE_DPAD_CENTER 1.6                   # Default server: the snap starts
  peak "watch press $i"
  key KEYCODE_BACK 2.5                          # cancel the extraction
  key KEYCODE_BACK 1.5                          # close the overlay
done

key KEYCODE_BACK 1.5                            # tile -> season picker (focus mode)
frames "tv season picker (OK D D U U Back)" 0.6 KEYCODE_DPAD_CENTER KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_UP KEYCODE_BACK
frames "tv slide (D U x3)" 1.0 KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP KEYCODE_DPAD_DOWN KEYCODE_DPAD_UP
back_home

echo "--- Displayed since $marker" >> "$LOG"
adb -s "$D" logcat -d -v time -T "$marker" 2>/dev/null | grep -E "Displayed com.bluehive.tv/.*(MoviesDetailsScreenCompose|TVShowsDetailsScreenCompose)" | sed 's/.*Displayed com.bluehive.tv\/com.example.bluehive.details.//' >> "$LOG"
echo "done $LABEL"
