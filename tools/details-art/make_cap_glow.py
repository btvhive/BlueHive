"""Makes app/src/main/res/drawable-xhdpi/season_cap_focus_glow.webp: the glow
behind the TV page's season cap while the season picker has focus (v526).

It is the buttons' focus underglow, fitted to the cap:
  - the plate is button_focus_narrow_glow's near-white, CAP_PLATE_OUT_DP beyond
    the cap on every side, with the cap's own rounded corners
    (CAP_CORNER_DP + CAP_PLATE_OUT_DP);
  - the halo -- its falloff, colour and grain -- is sampled pixel by pixel from
    button_focus_narrow_glow as the app draws it behind the buttons
    (BUTTON_GLOW_WIDTH_DP wide), at the same distance out from the plate.

Run it again whenever the cap's art or size, or the button glow, changes, or
to move the glow further out / round its corners more. From the repo root
(PowerShell or Git Bash; needs Pillow):

    python tools/details-art/make_cap_glow.py

The app draws the image CAP_GLOW_OUT (SeasonAccordion.kt) beyond the cap on
every side: keep that equal to CAP_PLATE_OUT_DP + the halo width this prints.
The random grain is seeded, so the same inputs give the same picture.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

# What the app draws (dp), from SeasonAccordion.kt / TvPlayArea.kt.
CAP_WIDTH_DP = 134.25        # SEASON_CAP_WIDTH
CAP_HEIGHT_DP = 122.25       # SEASON_CAP_HEIGHT
CAP_CORNER_DP = 5.0          # the corners of extended_mini_left_upper_body, measured
BUTTON_GLOW_WIDTH_DP = 71.6  # button_focus_narrow_glow's width behind the buttons

# The approved look (the user's mock-up, 2026-10-30).
CAP_PLATE_OUT_DP = 2.0       # how far the white plate shows beyond the cap

PX_PER_DP = 2.0              # drawable-xhdpi: the onn box and the Fire TV sticks
SEED = 7

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(REPO, 'app', 'src', 'main', 'res')
OUT = os.path.join(RES, 'drawable-xhdpi', 'season_cap_focus_glow.webp')


def main(out=OUT):
    random.seed(SEED)
    glow = Image.open(os.path.join(RES, 'drawable', 'button_focus_narrow_glow.webp')).convert('RGBA')
    s = BUTTON_GLOW_WIDTH_DP * PX_PER_DP / glow.width
    glow = glow.resize((round(glow.width * s), round(glow.height * s)), Image.LANCZOS)
    gw, gh = glow.size
    # Where the solid plate starts, and the straight rows the halo is sampled from.
    plate_l = next(x for x in range(gw) if glow.getpixel((x, gh // 2))[3] >= 250)
    rows = [y for y in range(gh)
            if glow.getpixel((plate_l + 2, y))[3] >= 250 and abs(y - gh // 2) < gh * 0.18]
    plate_rgb = glow.getpixel((gw // 2, gh // 2))[:3]
    halo = plate_l                                  # px of halo outside the plate

    plate_out = CAP_PLATE_OUT_DP * PX_PER_DP
    edge = plate_out + halo                         # canvas margin around the cap, px
    w = round(CAP_WIDTH_DP * PX_PER_DP + 2 * edge)
    h = round(CAP_HEIGHT_DP * PX_PER_DP + 2 * edge)
    r = CAP_CORNER_DP * PX_PER_DP + plate_out
    x0, y0, x1, y1 = halo, halo, w - halo, h - halo

    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle((x0, y0, x1 - 1, y1 - 1), radius=r, fill=plate_rgb + (255,))
    px = img.load()
    cx0, cy0, cx1, cy1 = x0 + r, y0 + r, x1 - 1 - r, y1 - 1 - r
    for y in range(h):
        for x in range(w):
            # Distance outside the rounded plate.
            dx = max(cx0 - x, 0, x - cx1)
            dy = max(cy0 - y, 0, y - cy1)
            d = math.hypot(dx, dy) - r
            if d <= 0.5:
                continue
            sx = plate_l - 1 - int(d)
            if sx < 0:
                continue
            p = glow.getpixel((sx, random.choice(rows)))
            if p[3]:
                px[x, y] = p
    img.save(out, 'WEBP', lossless=True)
    print(f'wrote {out}: {w} x {h} px; drawn {edge / PX_PER_DP:g} dp beyond the cap '
          f'({CAP_PLATE_OUT_DP:g} dp plate + {halo / PX_PER_DP:g} dp halo)')


if __name__ == '__main__':
    main(*sys.argv[1:])
