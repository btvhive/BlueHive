"""Read `dumpsys gfxinfo <pkg> framestats` output from stdin (one dump, or
several concatenated: frames are merged by their vsync time); print the frames
as bursts on a timeline.

Frames less than 250 ms apart form one burst. Per burst: when it started
(seconds after the first frame), how many frames, the UI-thread time of its
first frame (what started the burst: a tick, a key press), and the heaviest
total and UI-thread times in ms. total = FrameCompleted - IntendedVsync; ui =
SyncQueued - IntendedVsync (input, recomposition, layout and recording). Rows
whose Flags are not 0 (first frame of a window and so on) are skipped, as
Android does.
"""
import sys

frames = {}
header = None
inside = False
for raw in sys.stdin:
    line = raw.strip()
    if line == "---PROFILEDATA---":
        inside = not inside
        header = None
        continue
    if not inside or not line:
        continue
    cols = [c for c in line.split(",") if c != ""]
    if header is None:
        header = cols
        continue
    try:
        rec = dict(zip(header, (int(c) for c in cols)))
    except ValueError:
        continue
    if rec.get("Flags", 1) != 0:
        continue
    iv = rec["IntendedVsync"]
    frames[iv] = ((rec["FrameCompleted"] - iv) / 1e6, (rec["SyncQueued"] - iv) / 1e6)

if not frames:
    print("no frames")
    sys.exit(0)
rows = sorted((iv, t, u) for iv, (t, u) in frames.items())
t0 = rows[0][0]
bursts = []
for iv, total, ui in rows:
    if bursts and iv - bursts[-1]["last"] < 250_000_000:
        b = bursts[-1]
        b["n"] += 1
        b["last"] = iv
        b["total"] = max(b["total"], total)
        b["ui"] = max(b["ui"], ui)
    else:
        bursts.append({"start": iv, "last": iv, "n": 1, "first_ui": ui, "total": total, "ui": ui})
print(f"{len(rows)} frames in {len(bursts)} bursts over {(rows[-1][0] - t0) / 1e9:.1f} s")
for b in bursts:
    print(f"  t={(b['start'] - t0) / 1e9:6.2f}s  frames={b['n']:3d}  first ui={b['first_ui']:6.1f} ms  "
          f"heaviest total={b['total']:6.1f} ms  ui={b['ui']:6.1f} ms")
