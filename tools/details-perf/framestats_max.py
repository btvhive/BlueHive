"""Read `dumpsys gfxinfo <pkg> framestats` from stdin; print the slowest frames.

Per frame: total = FrameCompleted - IntendedVsync, ui = SyncQueued - IntendedVsync
(input handling + Compose recomposition + layout + record), in ms. Rows whose
Flags are not 0 (first frame of a window, etc.) are skipped, as Android does.
"""
import sys

rows = []
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
    total = (rec["FrameCompleted"] - iv) / 1e6
    ui = (rec["SyncQueued"] - iv) / 1e6
    rows.append((total, ui))

if not rows:
    print("no frames")
    sys.exit(0)
rows.sort(reverse=True)
over = sum(1 for t, _ in rows if t > 16.7)
print(f"frames={len(rows)} slowest total={rows[0][0]:.1f}ms (ui {rows[0][1]:.1f}ms) "
      f"2nd={rows[1][0] if len(rows) > 1 else 0:.1f}ms over16.7ms={over}")
