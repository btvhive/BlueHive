"""Makes the Latest Trailers panel's art, the metal TV the trailers play in
(v557, option C from the owner's mock-ups, 2026-10-31; v558 lifted to the
mock-ups' "level 2" tones, as C read too dark on the TV; v559 to "level 1";
v560 moved the rim highlight to the top-right and bottom-left; v562 softened
the glare and thinned the line, option E / 0.8 px from the mock-ups; v564
darkened the metal toward the top-left, option B with the bottom-right kept;
v565 took both dark corners 5% darker; v566 centred the label and added the
chin's buttons and status-light socket):

  app/src/main/res/drawable-xhdpi/trailer_frame.webp
      the set: a dark metal rim (RIM_TOP -> RIM_BOTTOM) around a darker panel
      (PANEL_TOP -> PANEL_BOTTOM) that holds the picture, with LATEST TRAILERS
      spaced out on the panel's chin. The rim rolls off at its outer edge and
      carries a fine light line and a soft white glare on its top-right and
      bottom-left corners (the top-left stays dark), and the panel sits a
      step into it.
  app/src/main/res/drawable-xhdpi/new_trailer_frame_focused.webp
      the focus glow behind it, fitted to the new outline: a near-white plate
      GLOW_PLATE_OUT_DP beyond the rim, with the halo -- falloff, colour and
      grain -- sampled from the original glow art, trailer_glow_halo_source.webp
      next to this script. The app tints it blue in select mode.

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
RIM_TOP, RIM_BOTTOM = (0x2C, 0x2D, 0x32), (0x1A, 0x1B, 0x1E)
PANEL_TOP, PANEL_BOTTOM = (0x1A, 0x1A, 0x1E), (0x0B, 0x0B, 0x0D)
RIM_HIGHLIGHT = 0.40         # the rim's light line at its brightest (top-right, bottom-left)
RIM_LINE_PX = 0.8            # its width: a hairline (v562; 1.3 before)
GLARE = 0.06                 # the soft white glare on the metal behind it, at its brightest
GLARE_RADIUS_DP = 10.0       # how far the glare spreads from each corner
# The chin's controls (v566): four buttons and the status light's socket.
BUTTON_W_DP, BUTTON_H_DP, BUTTON_GAP_DP = 7.0, 4.5, 3.0
LIGHT_INSET_DP = 3.0         # the light's centre, in from the panel's rounded corner
LIGHT_TO_BUTTONS_DP = 6.0    # from the light's centre to the nearest button's edge
LIGHT_SOCKET_DP = 3.2        # radius of the dark socket the light sits in
RIM_TL_DARKEN = 0.30         # how much darker the metal gets at the top-left corner
RIM_BR_DARKEN = 0.05         # ... and at the bottom-right corner
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
# The halo is always sampled from the ORIGINAL focus glow (v556's art, kept
# here), never from GLOW_OUT: resampling its own output lost strength every
# run, and by v566 the halo had faded to a sixth of its brightness.
HALO_SOURCE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'trailer_glow_halo_source.webp')
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


def smoothstep(a, b, t):
    t = max(0.0, min(1.0, (t - a) / (b - a)))
    return t * t * (3 - 2 * t)


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
    # Shaded only (no lit edge), so the top-left corner stays dark; the light
    # is the glare below.
    # The metal darkens toward the dark corners: RIM_TL_DARKEN at the
    # top-left and RIM_BR_DARKEN at the bottom-right (v565: 30% / 5%; v564
    # 25% / 0%), fading out toward the lit corners. The panel is drawn over
    # this, so it is untouched.
    face = vgrad(size, RIM_TOP, RIM_BOTTOM, 0, h)
    dark = Image.new('L', size, 0)
    dp_ = dark.load()
    for y in range(h):
        for x in range(w):
            toward_lit = abs(x / w - y / h)                     # 1 at the lit corners
            toward_br = smoothstep(0.7, 1.3, x / w + y / h)     # 1 toward the bottom-right
            dp_[x, y] = round(255 * (1 - smoothstep(0.0, 0.9, toward_lit))
                              * (RIM_TL_DARKEN * (1 - toward_br) + RIM_BR_DARKEN * toward_br))
    face = Image.composite(Image.new('RGB', size, (0, 0, 0)), face, dark)
    rim = lit(face, om, 4, 4.0, 0.0, 0.45)
    img.paste(rim, (0, 0), om)
    # Sun glare on the metal at the top-right and bottom-left corners, behind
    # the highlight line: a soft white bloom centred on each corner's curve.
    glare = Image.new('L', size, 0)
    gd = ImageDraw.Draw(glare)
    c = px(RIM_CORNER_DP) * 0.6
    r = px(GLARE_RADIUS_DP)
    for cx, cy in ((w - c, c), (c, h - c)):
        gd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    glare = glare.filter(ImageFilter.GaussianBlur(px(GLARE_RADIUS_DP) * 0.9))
    glare = ImageChops.multiply(glare, om).point(lambda v: round(v * GLARE))
    img.paste(Image.new('RGB', size, (255, 255, 255)), (0, 0), glare)
    # The rim's highlight (v560, the owner's call): a fine bright line on the
    # top-right and bottom-left corners, none on the top-left, and the faint
    # all-round line kept at the bottom-right. Along the outline, the bright
    # line follows how far a point is toward the top-right/bottom-left
    # diagonal ends; the faint one fades in toward the bottom-right.
    edge = rr_outline(size, (0.5, 0.5, w - 0.5, h - 0.5), px(RIM_CORNER_DP), RIM_LINE_PX)
    white = Image.new('RGB', size, (255, 255, 255))
    weight = Image.new('L', size, 0)
    wp = weight.load()
    for y in range(h):
        for x in range(w):
            u = x / w - y / h          # +1 top-right, -1 bottom-left, 0 on the other diagonal
            v = x / w + y / h          # 0 top-left, 2 bottom-right
            strong = RIM_HIGHLIGHT * smoothstep(0.2, 0.85, abs(u))
            faint = RIM_HIGHLIGHT * 0.25 * smoothstep(0.7, 1.3, v)
            wp[x, y] = round(255 * max(strong, faint))
    img.paste(white, (0, 0), ImageChops.multiply(edge, weight))
    # The panel, set a step into the rim.
    im = rr_mask(size, inner, px(PANEL_CORNER_DP))
    panel = lit(vgrad(size, PANEL_TOP, PANEL_BOTTOM, inner[1], inner[3]), ImageChops.invert(im), 2.5, 3.4, 0.12, 0.55)
    img.paste(panel, (0, 0), im)
    img.paste(white, (0, 0), rr_outline(size, inner, px(PANEL_CORNER_DP), 1.0).point(lambda v: round(v * 0.10)))
    # Behind the picture: plain black (the carousel draws over it).
    ImageDraw.Draw(img).rectangle(pic, fill=(0, 0, 0, 255))
    font = ImageFont.truetype(FONT, round(px(LABEL_SP)))
    # Centred in the chin, the strip from the picture's bottom to the panel's.
    chin_cy = pic[3] + (px(PANEL_PAD_DP) + px(CHIN_DP)) / 2
    spaced_text(img, w / 2, chin_cy, LABEL, font, LABEL_RGB + (255,), px(LABEL_SPACING_DP))
    # Four small buttons at the chin's right end, then the socket of the status
    # light. The light itself is drawn by the app (TrailerStatusLight.kt), red
    # or neon green, centred on the socket at status_light_center().
    light_cx, light_cy = status_light_center(w, h)
    bw, bh, gap = px(BUTTON_W_DP), px(BUTTON_H_DP), px(BUTTON_GAP_DP)
    first_right = light_cx - px(LIGHT_TO_BUTTONS_DP)
    for i in range(4):
        right = first_right - i * (bw + gap)
        box = (right - bw, chin_cy - bh / 2, right, chin_cy + bh / 2)
        bm = rr_mask(size, box, px(1.2))
        img.paste(vgrad(size, (0x4A, 0x4B, 0x52), (0x30, 0x31, 0x36), box[1], box[3]), (0, 0), bm)
        img.paste(white, (0, 0), rr_outline(size, box, px(1.2), 0.8).point(lambda v: round(v * 0.12)))
    r = px(LIGHT_SOCKET_DP)
    ImageDraw.Draw(img).ellipse((light_cx - r, light_cy - r, light_cx + r, light_cy + r), fill=(6, 6, 7, 255))
    img.save(FRAME_OUT, 'WEBP', lossless=True)
    return size


def status_light_center(w, h):
    """The status light's centre on the frame (px): in the chin, at its right end."""
    pic_bottom = px(RIM_DP + PANEL_PAD_DP) + px(VIEWPORT_H)
    return w - px(RIM_DP) - px(PANEL_CORNER_DP) - px(LIGHT_INSET_DP), pic_bottom + (px(PANEL_PAD_DP) + px(CHIN_DP)) / 2


def make_glow(frame_size):
    random.seed(SEED)
    src = Image.open(HALO_SOURCE).convert('RGBA')
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
    lx, ly = status_light_center(fw, fh)
    print(f'  status light centre = {fx + lx / PX_PER_DP:g}.dp, {fy + ly / PX_PER_DP:g}.dp')


if __name__ == '__main__':
    main()
