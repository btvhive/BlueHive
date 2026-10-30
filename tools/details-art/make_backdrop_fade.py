"""Makes app/src/main/res/drawable-xhdpi/backdrop_details_fade.webp: the dark
fade the details pages draw over the backdrop (DetailsGradient), rebuilt from
the original backdrop_details_gradient.webp so it reads seamlessly (v528).

The original's shape is kept -- the solid band down its left and along its
bottom, the soft rounded window the backdrop shows through, the two tilted
shapes in its bottom-left corner -- but:
  - its grain is gone: the original's alpha jumps about +/-5 steps pixel to
    pixel (lossy compression), which reads as speckle over a bright backdrop;
  - its fades are eased: a gentle blur (FADE_SMOOTH_PX) softens the kinks
    where each ramp starts and stops, which the eye picks out as lines;
  - one colour: the original's navy-black (its mean, ROUNDED), not 0-7 noise;
  - a fine, even dither (+/-1 level, triangular) instead of bare 8-bit steps,
    so the ramps show no lines, and lossless WebP so nothing is lost.
Only the soft window over the backdrop changes: the solid bands, the corner
shapes and the hard outer edges (EDGE_KEEP_PX) keep the original's pixels.

Re-run from the repo root (needs Pillow and numpy):

    python tools/details-art/make_backdrop_fade.py
"""
import os
import sys

import numpy as np
from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(REPO, 'app', 'src', 'main', 'res', 'drawable-xhdpi')
SRC = os.path.join(RES, 'backdrop_details_gradient.webp')
OUT = os.path.join(RES, 'backdrop_details_fade.webp')

FADE_SMOOTH_PX = 10.0   # sigma of the easing blur, in the image's own pixels
EDGE_KEEP_PX = 12       # the original is kept this close to its outer edges
COLOUR = (5, 5, 7)      # the original's mean where it is solid (4.95, 5.27, 6.76)

# The soft window over the backdrop, in the image's pixels: right of the left
# band, above the bottom band, and clear of the corner shapes (bottom-left).
WINDOW_LEFT = 300
WINDOW_BOTTOM = 700
CORNER_RIGHT = 400
CORNER_TOP = 420
FEATHER_PX = 24
SEED = 7


def gaussian_blur(a, sigma):
    """Separable Gaussian, edges extended (the image's top and right edges are the screen's)."""
    r = int(3 * sigma)
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    p = np.pad(a, r, mode='edge')
    p = np.apply_along_axis(lambda m: np.convolve(m, k, mode='valid'), 1, p)
    p = np.apply_along_axis(lambda m: np.convolve(m, k, mode='valid'), 0, p)
    return p


def dilate(mask, px):
    """Grow a boolean mask by px in every direction (a square, which is enough here)."""
    out = mask.copy()
    for _ in range(px):
        g = out.copy()
        g[1:, :] |= out[:-1, :]
        g[:-1, :] |= out[1:, :]
        g[:, 1:] |= out[:, :-1]
        g[:, :-1] |= out[:, 1:]
        out = g
    return out


def dither(a):
    """8 bits from a float alpha (0..255) with triangular noise of +/-1 level
    before rounding: the 1-level steps of a slow ramp break up into an even,
    fine grain -- a fifth of the original's -- instead of showing as lines."""
    rng = np.random.default_rng(SEED)
    noise = rng.random(a.shape) - rng.random(a.shape)       # triangular, -1..1
    return np.clip(np.round(a + noise), 0, 255).astype(np.uint8)


def main(out=OUT):
    src = np.asarray(Image.open(SRC).convert('RGBA')).astype(np.float64)
    alpha = src[..., 3]
    # Outside the shape: judged on a lightly blurred copy, because the faint,
    # grainy corner shapes have single pixels near 0 that are not outside.
    outside = gaussian_blur(alpha, 2.0) < 1.5
    keep = dilate(outside, EDGE_KEEP_PX)          # near a hard outer edge: as it was
    # Averaged over the shape only: a plain blur pulls the transparent outside
    # in and leaves a light strip inside every hard edge (a dark rim beside it).
    inside = (~outside).astype(np.float64)
    smooth = gaussian_blur(alpha * inside, FADE_SMOOTH_PX) / np.maximum(gaussian_blur(inside, FADE_SMOOTH_PX), 1e-6)
    # Only the soft window over the backdrop is smoothed -- the part that
    # looked patchy. The solid bands and the corner shapes keep every pixel
    # (they are solid, or sit over the dark page, and blurring melted the hard
    # edges inside them), with a FEATHER_PX blend at the window's border.
    h, w = alpha.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    def ramp(v, lo, hi):
        return np.clip((v - lo) / (hi - lo), 0.0, 1.0)
    window = (ramp(xx, WINDOW_LEFT, WINDOW_LEFT + FEATHER_PX)
              * (1.0 - ramp(yy, WINDOW_BOTTOM - FEATHER_PX, WINDOW_BOTTOM)))
    corner = ramp(xx, CORNER_RIGHT - FEATHER_PX, CORNER_RIGHT) + ramp(-yy, -CORNER_TOP, -CORNER_TOP + FEATHER_PX)
    window *= np.clip(corner, 0.0, 1.0)
    window[keep] = 0.0
    new = alpha * (1.0 - window) + smooth * window
    new[outside] = 0.0
    a8 = dither(new)

    rgba = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    rgba[..., 0], rgba[..., 1], rgba[..., 2] = COLOUR
    rgba[..., 3] = a8
    Image.fromarray(rgba, 'RGBA').save(out, 'WEBP', lossless=True)

    fade = ~outside & (alpha < 250) & ~keep
    print(f'wrote {out}')
    print(f'  shape kept: mean |new - original| in the fade {np.abs(new - alpha)[fade].mean():.2f} '
          f'alpha steps of 255; the original\'s own grain was {np.abs(alpha - smooth)[fade].std():.2f}')


if __name__ == '__main__':
    main(*sys.argv[1:])
