"""Makes the Latest Trailers panel's art, the metal TV the trailers play in
(v557, option C from the owner's mock-ups, 2026-10-31):

  app/src/main/res/drawable-xhdpi/trailer_frame.webp
      the set: a dark metal rim (RIM_TOP -> RIM_BOTTOM) around a darker panel
      (PANEL_TOP -> PANEL_BOTTOM) that holds the picture, with LATEST TRAILERS
      spaced out on the panel's chin. Lit from the top left: the rim rolls off
      at its outer edge and carries a fine highlight along its top, and the
      panel sits a step into it.
  app/src/main/res/drawable-xhdpi/new_trailer_frame_focused.webp
      the focus glow behind it, fitted to the new outline: a near-white plate
      GLOW_PLATE_OUT_DP beyond the rim, with the halo -- falloff, colour and
      grain -- sampled from the glow's own left edge (so running this again
      keeps the same halo). The app tints it blue in select mode.

Everything is measured from the picture the carousel draws (VIEWPORT_*, from
HomeScreenTrailerSectionCompose.kt), so the picture always sits exactly in the
panel. If any size here changes, update the frame/focused specs there to what
this prints. From the repo root (PowerShell or Git Bash; needs Pillow):

    python tools/home-art/make_trailer_frame.py

The glow's random grain is seeded, so the same inputs give the same picture.
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

# The picture, as HomeScreenTrailerSectionCompose.kt draws it (dp).
VIEWPORT_X, VIEWPORT_Y = 77.5, 53.5
VIEWPORT_W, VIEWPORT_H = 325.19415283203125, 182.35919189453125

# The approved look (dp / colours).
PANEL_PAD_DP = 4.0           # panel showing around the picture
CHIN_DP = 14.0               # extra panel under the picture, for the label
RIM_DP = 11.0                # metal rim around the panel
RIM_CORNER_DP = 10.0
PANEL_CORNER_DP = 6.0
RIM_TOP, RIM_BOTTOM = (0x1C, 0x1C, 0x1C), (0x13, 0x13, 0x13)
PANEL_TOP, PANEL_BOTTOM = (0x11, 0x11, 0x11), (0x07, 0x07, 0x07)
LABEL = 'LATEST TRAILERS'
LABEL_SP, LABEL_SPACING_DP, LABEL_RGB = 8.5, 3.0, (205, 210, 222)
GLOW_PLATE_OUT_DP = 1.5      # how far the glow's solid plate shows beyond the rim

PX_PER_DP = 2.0              # drawable-xhdpi: the onn box and the Fire TV sticks
SS = 4                       # supersampling for shapes
SEED = 11

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(REPO, 'app', 'src', 'main', 'res')
FRAME_OUT = os.path.join(RES, 'drawable-xhdpi', 'trailer_frame.webp')
GLOW_OUT = os.path.join(RES, 'drawable-xhdpi', 'new_trailer_frame_focused.webp')
FONT = os.path.join(RES, 'font', 'asap_condensed_bold.ttf')


def px(v):
    return v * PX_PER_DP


def rr_mask(size, box, radius):
    """Anti-aliased rounded-rect mask of `box` (px, may be fractional) on a canvas of `size`."""
    w, h = size
    m = Image.new('L', (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle([v * SS for v in box], radius=radius * SS, fill=255)
    return m.resize(size, Image.LANCZOS)


def rr_outline(size, box, radius, width):
    w, h = size
    m = Image.new('L', (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle([v * SS for v in box], radius=radius * SS, outline=255, width=round(width * SS))
    return m.resize(size, Image.LANCZOS)


def vgrad(size, top, bottom, y0, y1):
    w, h = size
    g = Image.new('RGB', size)
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = min(1.0, max(0.0, (y - y0) / max(1.0, y1 - y0)))
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return g


def lit(face, mask, blur, strength, hi, lo):
    """Light `face` from the top left over the slope of a blurred `mask`."""
    b = mask.filter(ImageFilter.GaussianBlur(blur))
    ahead, behind = ImageChops.offset(b, -1, -1), ImageChops.offset(b, 1, 1)
    hl = ImageChops.multiply(ImageChops.subtract(ahead, behind, scale=1 / strength), mask)
    sh = ImageChops.multiply(ImageChops.subtract(behind, ahead, scale=1 / strength), mask)
    out = Image.composite(Image.new('RGB', face.size, (0, 0, 0)), face, sh.point(lambda v: round(v * lo)))
    return Image.composite(Image.new('RGB', face.size, (255, 255, 255)), out, hl.point(lambda v: round(v * hi)))


def spaced_text(img, cx, cy, text, font, fill, spacing):
    d = ImageDraw.Draw(img)
    widths = [font.getlength(c) for c in text]
    x = cx - (sum(widths) + spacing * (len(text) - 1)) / 2
    for c, w in zip(text, widths):
        d.text((x, cy), c, font=font, fill=fill, anchor='lm')
        x += w + spacing


def make_frame():
    pw, ph = px(VIEWPORT_W), px(VIEWPORT_H)
    m = px(RIM_DP + PANEL_PAD_DP)                     # picture inset from the canvas edge
    w = round(pw + 2 * m)
    h = round(ph + 2 * m + px(CHIN_DP))
    size = (w, h)
    outer = (0, 0, w, h)
    inner = (px(RIM_DP), px(RIM_DP), w - px(RIM_DP), h - px(RIM_DP))
    pic = (m, m, m + pw, m + ph)

    img = Image.new('RGBA', size, (0, 0, 0, 0))
    om = rr_mask(size, outer, px(RIM_CORNER_DP))
    rim = lit(vgrad(size, RIM_TOP, RIM_BOTTOM, 0, h), om, 4, 4.0, 0.22, 0.45)
    img.paste(rim, (0, 0), om)
    # The rim's highlight: bright along the top, faint all round.
    top = Image.new('L', size, 0)
    ImageDraw.Draw(top).rectangle((0, 0, w, h * 0.45), fill=255)
    top = top.filter(ImageFilter.GaussianBlur(50))
    edge = rr_outline(size, (0.5, 0.5, w - 0.5, h - 0.5), px(RIM_CORNER_DP), 1.3)
    white = Image.new('RGB', size, (255, 255, 255))
    img.paste(white, (0, 0), ImageChops.multiply(edge, top).point(lambda v: round(v * 0.36)))
    img.paste(white, (0, 0), edge.point(lambda v: round(v * 0.09)))
    # The panel, set a step into the rim.
    im = rr_mask(size, inner, px(PANEL_CORNER_DP))
    panel = lit(vgrad(size, PANEL_TOP, PANEL_BOTTOM, inner[1], inner[3]), ImageChops.invert(im), 2.5, 3.4, 0.12, 0.55)
    img.paste(panel, (0, 0), im)
    img.paste(white, (0, 0), rr_outline(size, inner, px(PANEL_CORNER_DP), 1.0).point(lambda v: round(v * 0.10)))
    # Behind the picture: plain black (the carousel draws over it).
    ImageDraw.Draw(img).rectangle(pic, fill=(0, 0, 0, 255))
    font = ImageFont.truetype(FONT, round(px(LABEL_SP)))
    spaced_text(img, w / 2, pic[3] + px(PANEL_PAD_DP) + px(CHIN_DP) / 2, LABEL, font, LABEL_RGB + (255,), px(LABEL_SPACING_DP))
    img.save(FRAME_OUT, 'WEBP', lossless=True)
    return size


def make_glow(frame_size):
    random.seed(SEED)
    src = Image.open(GLOW_OUT).convert('RGBA')
    sw, sh = src.size
    plate_l = next(x for x in range(sw) if src.getpixel((x, sh // 2))[3] >= 250)
    rows = [y for y in range(sh) if src.getpixel((plate_l + 2, y))[3] >= 250 and abs(y - sh // 2) < sh * 0.18]
    plate_rgb = src.getpixel((sw // 2, sh // 2))[:3]
    halo = plate_l

    plate_out = px(GLOW_PLATE_OUT_DP)
    edge = plate_out + halo
    fw, fh = frame_size
    w, h = round(fw + 2 * edge), round(fh + 2 * edge)
    r = px(RIM_CORNER_DP) + plate_out
    x0, y0, x1, y1 = halo, halo, w - halo, h - halo
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle((x0, y0, x1 - 1, y1 - 1), radius=r, fill=plate_rgb + (255,))
    pxl = img.load()
    cx0, cy0, cx1, cy1 = x0 + r, y0 + r, x1 - 1 - r, y1 - 1 - r
    for y in range(h):
        for x in range(w):
            dx = max(cx0 - x, 0, x - cx1)
            dy = max(cy0 - y, 0, y - cy1)
            d = math.hypot(dx, dy) - r
            if d <= 0.5:
                continue
            sx = plate_l - 1 - int(d)
            if sx < 0:
                continue
            p = src.getpixel((sx, random.choice(rows)))
            if p[3]:
                pxl[x, y] = p
    img.save(GLOW_OUT, 'WEBP', lossless=True)
    return (w, h), edge


def main():
    fw, fh = make_frame()
    (gw, gh), edge = make_glow((fw, fh))
    fx = VIEWPORT_X - RIM_DP - PANEL_PAD_DP
    fy = VIEWPORT_Y - RIM_DP - PANEL_PAD_DP
    e = edge / PX_PER_DP
    print(f'wrote {FRAME_OUT}: {fw} x {fh} px')
    print(f'wrote {GLOW_OUT}: {gw} x {gh} px')
    print('specs for HomeScreenTrailerSectionCompose.kt:')
    print(f'  frameX = {fx:g}.dp, frameY = {fy:g}.dp, frameW = {fw / PX_PER_DP:g}.dp, frameH = {fh / PX_PER_DP:g}.dp')
    print(f'  focusedX = {fx - e:g}.dp, focusedY = {fy - e:g}.dp, focusedW = {gw / PX_PER_DP:g}.dp, focusedH = {gh / PX_PER_DP:g}.dp')


if __name__ == '__main__':
    main()
