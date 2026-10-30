"""Makes app/src/main/res/drawable-xhdpi/backdrop_details_fade.webp: the dark
fade the details pages draw over the backdrop (DetailsGradient), rebuilt from
the original backdrop_details_gradient.webp so it reads seamlessly (v528).

The original's shape is kept -- the solid band down its left and along its
bottom, the soft rounded window the backdrop shows through, the two tilted
shapes in its bottom-left corner -- but:
  - its grain is gone: the original's alpha jumps about +/-5 steps pixel to
    pixel (lossy compression), which reads as speckle over a bright backdrop;
  - its knees are rounded: each profile bent sharply in places (BAND_ and
    RAMP_SMOOTH_PX round them), which reads as lines -- worst from the side,
    where the TV lifts the darks;
  - one colour: the original's navy-black (its mean, ROUNDED), not 0-7 noise;
  - a fine, even dither (+/-1 level, triangular) instead of bare 8-bit steps,
    so the ramps show no lines, and lossless WebP so nothing is lost.
Right of the left band's hard edge the fade is rebuilt from its two profiles
(1 - band(y) x ramp(x)) with every knee rounded off; the hard outer edges
(EDGE_KEEP_PX) and the corner shapes keep the original's pixels.

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

BAND_SMOOTH_PX = 45.0   # how far each knee is rounded off (Gaussian sigma, image px)
RAMP_SMOOTH_PX = 55.0
RAMP_CLEAR_X0, RAMP_CLEAR_X1 = 1100, 1270   # columns where the ramp is fully clear
BAND_ROWS = (60, 420)                       # rows the ramp is measured on
RAMP_START_X = 130                          # the left band's hard edge, plus its anti-aliasing
EDGE_KEEP_PX = 12       # the original is kept this close to its outer edges
COLOUR = (5, 5, 7)      # the original's mean where it is solid (4.95, 5.27, 6.76)

# Where the rebuilt fade takes over: right of the left band's hard edge.
WINDOW_LEFT = 130
FEATHER_PX = 24
SOLID_EASE_PX = 40        # the band eases into solid over this many px at the bottom
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


def profiles(alpha):
    """The fade is 1 - band(y) x ramp(x) in its window (fits the original to
    a third of a level): the band from its right-hand columns, where the ramp
    is fully clear, then the ramp from the window rows."""
    t = np.clip(1.0 - alpha / 255.0, 1e-3, 1.0)
    band = t[:, RAMP_CLEAR_X0:RAMP_CLEAR_X1].mean(1)
    # Below the fade's bottom edge the image is clear, which is not the band:
    # the band is solid there (0), or smoothing drags "clear" up into it.
    bottom = int(np.nonzero(alpha[:, (RAMP_CLEAR_X0 + RAMP_CLEAR_X1) // 2] > 128)[0].max())
    band[bottom - 2:] = 0.0
    ramp_x = (t[BAND_ROWS[0]:BAND_ROWS[1], :] / band[BAND_ROWS[0]:BAND_ROWS[1], None]).mean(0)
    ramp_x[:RAMP_START_X] = ramp_x[RAMP_START_X]          # left of the band's edge: no data
    return np.clip(band, 0.0, 1.0), np.clip(ramp_x, 0.0, 1.0)


def smooth_1d(v, sigma, solid_from=None):
    """Gaussian-smoothed profile, edges extended. With [solid_from], the
    profile is held at 0 (solid) from there on, so the solid end stays solid."""
    r = int(3 * sigma)
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    out = np.convolve(np.pad(v, r, mode='edge'), k, mode='valid')
    if solid_from is not None:
        # Ease the last stretch down to exactly 0 instead of a step.
        n = len(out)
        ease = np.clip((solid_from - np.arange(n)) / SOLID_EASE_PX, 0.0, 1.0)
        out *= ease * ease * (3 - 2 * ease)
    return out


def main(out=OUT):
    src = np.asarray(Image.open(SRC).convert('RGBA')).astype(np.float64)
    alpha = src[..., 3]
    # Outside the shape: judged on a lightly blurred copy, because the faint,
    # grainy corner shapes have single pixels near 0 that are not outside.
    outside = gaussian_blur(alpha, 2.0) < 1.5
    keep = dilate(outside, EDGE_KEEP_PX)          # near a hard outer edge: as it was

    # The window, rebuilt from its two profiles with every knee rounded off:
    # the ramp's bends (x ~300, ~400, ~740) and the band's bend (y ~150) and
    # ridge (y ~420) each read as a line, worst from the side, where the TV
    # lifts the darks.
    band, ramp_x = profiles(alpha)
    solid_y = int(np.nonzero(band > 0.0)[0].max()) + 1     # where the bottom band is solid
    band_s = smooth_1d(band, BAND_SMOOTH_PX, solid_from=solid_y)
    ramp_s = smooth_1d(ramp_x, RAMP_SMOOTH_PX)
    rebuilt = (1.0 - np.outer(band_s, ramp_s)) * 255.0

    # Only the window changes: the solid bands' outer edges and the corner
    # shapes keep every pixel, with a FEATHER_PX blend at the window's border.
    h, w = alpha.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    def ramp(v, lo, hi):
        return np.clip((v - lo) / (hi - lo), 0.0, 1.0)
    # The whole fade right of the left band's hard edge: the corner shapes
    # only stick out where the fade is already solid, so they are untouched.
    window = ramp(xx, WINDOW_LEFT, WINDOW_LEFT + FEATHER_PX)
    window[solid_y:, :] = 0.0       # below the bottom band: the corner shapes' own pixels
    window[keep] = 0.0
    new = alpha * (1.0 - window) + rebuilt * window
    new[outside] = 0.0
    a8 = dither(new)

    rgba = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    rgba[..., 0], rgba[..., 1], rgba[..., 2] = COLOUR
    rgba[..., 3] = a8
    Image.fromarray(rgba, 'RGBA').save(out, 'WEBP', lossless=True)

    win = window > 0.99
    print(f'wrote {out}')
    print(f'  window: mean |new - original| {np.abs(new - alpha)[win].mean():.1f} alpha levels of 255, '
          f'max {np.abs(new - alpha)[win].max():.0f}')
    return band, ramp_x, band_s, ramp_s


if __name__ == '__main__':
    main(*sys.argv[1:])
