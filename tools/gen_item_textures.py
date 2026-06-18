#!/usr/bin/env python3
"""
Generate the MC3DPrint ITEM textures to the VISUAL-REVAMP-BRIEF.

Hero (32x32): filament_spool_t1..t8, blank_blueprint_disc, blueprint_disc,
              scanner.
Secondary (16x16): extrudium_crystal, speed/efficiency/rf_efficiency/buffer
              upgrade, creative_filament_spool.

Same filenames -> item/generated models still resolve. Run from repo root:
  python3 tools/gen_item_textures.py
"""
import math

from PIL import Image, ImageDraw

from tex_common import (BODY, FRAME, GLOW, TIER, ACCENT_TEAL, ramp3, shade,
                        new, quantize_to_palette, save_item)


def acc(img):
    return (img.load(), img.size[0], img.size[1])


def put(px, x, y, c, a=255):
    if 0 <= x < px[1] and 0 <= y < px[2]:
        px[0][x, y] = (c[0], c[1], c[2], a)


def rect(px, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            put(px, xx, yy, c)


# ---------------------------------------------------------------------------
# HERO 32x32: filament spool — a coil DONUT with a visible dark hub hole,
# concentric layer arcs, top-left sheen, and a thin grey flange rim.
# ---------------------------------------------------------------------------
def filament_spool(tier):
    """A clean wound reel: a dark hub hole, a smooth tier-accent coil with two
    crisp concentric layer rings, one top-left sheen crescent, and a thin grey
    flange. No modulo banding / scattered sheen — that was the grain. The coil
    reads as a donut, not noise, and downscales cleanly to the 16px inventory icon."""
    H = 32
    img = new(H); px = acc(img)
    cx, cy = 15.5, 15.5
    chi, cmid, clo = ramp3(TIER[tier])
    sheen = shade(chi, 0.24)

    R_OUT = 14.0      # outer flange edge
    R_FLANGE = 12.5   # coil outer edge
    R_COIL_IN = 6.0   # coil inner edge
    R_HUB = 3.6       # hub hole radius
    RINGS = (8.4, 10.6)  # two clean concentric "wound layer" lines

    for y in range(H):
        for x in range(H):
            dx = (x + 0.5) - cx
            dy = (y + 0.5) - cy
            d = math.hypot(dx, dy)
            if d > R_OUT:
                continue
            lit = (-dx - dy) / (math.sqrt(2) * max(d, 0.001))  # top-left light
            if d <= R_HUB:
                # dark hub hole (clearly a reel)
                c = FRAME[3] if d > R_HUB - 1.3 else FRAME[2]
            elif d <= R_COIL_IN:
                # grey hub flange between hole and coil — smooth lit/shadow
                c = BODY[2] if lit > 0.25 else (BODY[4] if lit < -0.25 else BODY[3])
            elif d <= R_FLANGE:
                # wound coil: smooth 3-shade, no banding
                c = chi if lit > 0.45 else (clo if lit < -0.45 else cmid)
                for rr in RINGS:                       # crisp concentric layers
                    if abs(d - rr) < 0.5:
                        c = clo
                        break
            else:
                # thin grey flange rim, darker outer lip
                if d > R_OUT - 1.0:
                    c = BODY[4] if lit < 0 else BODY[3]
                else:
                    c = BODY[1] if lit > 0.3 else (BODY[4] if lit < -0.3 else BODY[2])
            put(px, x, y, c)

    # one clean top-left sheen crescent on the coil (single tight band)
    for y in range(H):
        for x in range(H):
            dx = (x + 0.5) - cx; dy = (y + 0.5) - cy
            d = math.hypot(dx, dy)
            if R_COIL_IN + 0.6 < d < R_FLANGE - 0.6:
                lit = (-dx - dy) / (math.sqrt(2) * max(d, 0.001))
                if lit > 0.8:
                    put(px, x, y, sheen)

    # a 1px loose filament end with a cyan magic tip (the feed end)
    ex, ey = int(cx + R_FLANGE - 1), int(cy)
    put(px, ex, ey, cmid); put(px, ex + 1, ey, clo)
    put(px, ex + 2, ey - 1, GLOW[3]); put(px, ex + 2, ey, GLOW[2])

    quantize_to_palette(img, extra=list(ramp3(TIER[tier])) + [sheen])
    return img


# ---------------------------------------------------------------------------
# HERO 32x32: blueprint discs
# ---------------------------------------------------------------------------
def _disc_body(px, H, face=FRAME[1]):
    """A clean dark schematic disc: bright outer bevel, a 1px machined rim band,
    a dark inset ring (so it reads as recessed), then a flat dark `face` the holo
    content sits on. Returns (cx, cy, R)."""
    cx, cy = 15.5, 15.5
    R = 14.0
    for y in range(H):
        for x in range(H):
            dx = (x + 0.5) - cx; dy = (y + 0.5) - cy
            d = math.hypot(dx, dy)
            if d > R:
                continue
            lit = (-dx - dy) / (math.sqrt(2) * max(d, 0.001))
            if d > R - 1.0:
                c = FRAME[0] if lit > 0.1 else FRAME[3]      # outer bevel rim
            elif d > R - 2.3:
                c = BODY[4] if lit > 0.3 else FRAME[2]       # machined rim band
            elif d > R - 3.3:
                c = FRAME[3]                                 # dark inset (recess)
            else:
                c = face                                     # flat recessed face
            put(px, x, y, c)
    return cx, cy, R


def blank_blueprint_disc():
    """A blank disc = a clearly EMPTY schematic bay: a dashed placeholder frame
    and a faint crosshair (no structure loaded) + a dim idle teal LED."""
    H = 32
    img = new(H); px = acc(img)
    field = (0x0B, 0x10, 0x1A)
    cx, cy, R = _disc_body(px, H, face=field)
    icx, icy = int(cx), int(cy)
    dash = shade(field, 0.75)
    faint = shade(field, 0.45)
    idle = shade(ACCENT_TEAL, -0.45)
    half = 7
    # dashed placeholder square (the empty schematic frame)
    for t in range(-half, half + 1):
        if (t + half) % 2 == 0:
            put(px, icx + t, icy - half, dash)
            put(px, icx + t, icy + half, dash)
            put(px, icx - half, icy + t, dash)
            put(px, icx + half, icy + t, dash)
    # faint empty-target crosshair in the middle
    for k in (-2, -1, 1, 2):
        put(px, icx + k, icy, faint)
        put(px, icx, icy + k, faint)
    put(px, icx, icy, shade(field, 0.9))
    # dim idle teal LED in the bay's top-right corner
    put(px, icx + half - 1, icy - half + 1, idle)
    quantize_to_palette(img, extra=[field, dash, faint, shade(field, 0.9), idle])
    return img


def blueprint_disc():
    """A written disc = a bright cyan hologram of a built structure: a FILLED
    isometric cube (lit top, mid-left, dark-right faces) floating over a faint
    projection grid, with a couple of holo scan lines. Reads instantly as 'a
    structure schematic', unlike the old wireframe-in-a-mesh."""
    H = 32
    img = new(H); px = acc(img)
    field = (0x08, 0x0E, 0x16)
    cx, cy, R = _disc_body(px, H, face=field)
    icx, icy = int(cx), int(cy)

    # faint holographic scan lines + a receding ground projection
    for sy in (icy - 4, icy + 1, icy + 6):
        for sx in range(icx - 8, icx + 9):
            if (sx + sy) % 2 == 0:
                put(px, sx, sy, GLOW[3])
    gy = icy + 7
    for gx in range(icx - 6, icx + 7):
        put(px, gx, gy, GLOW[3])
    put(px, icx - 4, gy + 1, GLOW[3]); put(px, icx + 4, gy + 1, GLOW[3])

    # filled isometric cube (ImageDraw rasterizes the three faces cleanly)
    T = (icx, icy - 7); RT = (icx + 5, icy - 4); F = (icx, icy - 1); LT = (icx - 5, icy - 4)
    LB = (icx - 5, icy + 2); FB = (icx, icy + 5); RB = (icx + 5, icy + 2)
    dr = ImageDraw.Draw(img)
    dr.polygon([T, RT, F, LT], fill=GLOW[1] + (255,))   # top face (brightest)
    dr.polygon([LT, F, FB, LB], fill=GLOW[2] + (255,))  # left face (mid)
    dr.polygon([F, RT, RB, FB], fill=GLOW[3] + (255,))  # right face (shadow)
    px = acc(img)  # re-acquire after ImageDraw

    def line(a, b, c):
        steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
        for s in range(steps + 1):
            put(px, round(a[0] + (b[0] - a[0]) * s / steps),
                round(a[1] + (b[1] - a[1]) * s / steps), c)
    # crisp white silhouette edges on the lit top-left, brighter front vertical
    for (a, b) in [(T, LT), (LT, LB), (T, RT)]:
        line(a, b, GLOW[0])
    line(F, FB, GLOW[1])
    for v in (T, LT, F):
        put(px, v[0], v[1], GLOW[0])

    quantize_to_palette(img, extra=[field])
    return img


# ---------------------------------------------------------------------------
# HERO 32x32: scanner — a handheld wand, dark body + cyan lens/emitter
# ---------------------------------------------------------------------------
def scanner():
    H = 32
    img = new(H); px = acc(img)
    # diagonal handheld wand from bottom-left grip to top-right emitter head
    # --- grip (lower-left) ---
    grip = [(6, 24), (7, 25), (8, 26), (9, 27)]
    rect(px, 6, 22, 5, 7, FRAME[2])
    # grip bevel
    for yy in range(22, 29):
        put(px, 6, yy, FRAME[0])
        put(px, 10, yy, FRAME[3])
    put(px, 6, 22, FRAME[0])
    # grip detailing (finger grooves)
    for yy in (24, 26):
        put(px, 8, yy, FRAME[3]); put(px, 9, yy, FRAME[3])
    # a teal power button on the grip
    put(px, 8, 23, ACCENT_TEAL)

    # --- shaft (diagonal body) up to the head ---
    def line(x0, y0, x1, y1, c, w=1):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for s in range(steps + 1):
            x = round(x0 + (x1 - x0) * s / steps)
            y = round(y0 + (y1 - y0) * s / steps)
            for o in range(w):
                put(px, x + o, y, c)
    # shaft body with top-left lit edge
    line(9, 23, 19, 9, FRAME[2], w=3)
    line(9, 22, 19, 8, BODY[4], w=1)   # upper-left lit edge
    line(11, 24, 21, 10, FRAME[3], w=1)  # lower-right shadow

    # --- emitter head (top-right): a casing housing a cyan scan lens ---
    hx, hy = 21, 8
    rect(px, hx - 2, hy - 3, 7, 7, FRAME[1])
    # head bevel
    hline_y = hy - 3
    for xx in range(hx - 2, hx + 5):
        put(px, xx, hy - 3, FRAME[0])
    for yy in range(hy - 3, hy + 4):
        put(px, hx - 2, yy, FRAME[0])
        put(px, hx + 4, yy, FRAME[3])
    for xx in range(hx - 2, hx + 5):
        put(px, xx, hy + 3, FRAME[3])
    # cyan lens (glowing emitter)
    lcx, lcy = hx + 1, hy
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 2.2:
                if d < 0.8:
                    c = GLOW[0]
                elif d < 1.5:
                    c = GLOW[1]
                elif d < 2.0:
                    c = GLOW[2]
                else:
                    c = GLOW[3]
                put(px, lcx + dx, lcy + dy, c)
    # scan beam: a couple of dim cyan rays out of the lens
    for k in range(1, 4):
        put(px, lcx + 2 + k, lcy - k, GLOW[3])
        put(px, lcx + 3 + k, lcy + 0, GLOW[3])
    quantize_to_palette(img)
    return img


# ---------------------------------------------------------------------------
# SECONDARY 16x16 items
# ---------------------------------------------------------------------------
def extrudium_crystal():
    S = 16
    img = new(S); px = acc(img)
    # a faceted cyan gem: a tall hexagonal-cut crystal with a bright top table,
    # crisp facet seams, and a dark outline-free silhouette.
    cx = 8
    # gem silhouette rows: (y, x_left, x_right)
    rows = [
        (2, 7, 8),
        (3, 6, 9),
        (4, 5, 10),
        (5, 5, 10),
        (6, 4, 11),
        (7, 4, 11),
        (8, 4, 11),
        (9, 5, 10),
        (10, 5, 10),
        (11, 6, 9),
        (12, 7, 8),
        (13, 8, 8),
    ]
    for (y, xl, xr) in rows:
        for x in range(xl, xr + 1):
            # facet shading by which side of the vertical seam + vertical band
            if x < cx:
                c = GLOW[1] if y < 8 else GLOW[2]      # left lit
            elif x > cx:
                c = GLOW[2] if y < 8 else GLOW[3]      # right mid/shadow
            else:
                c = GLOW[2]                            # center seam column
            put(px, x, y, c)
    # bright top table (the cut surface catching the light)
    for x in range(6, 10):
        put(px, x, 4, GLOW[0] if x < 8 else GLOW[1])
    put(px, 7, 3, GLOW[1]); put(px, 8, 3, GLOW[1])
    # crisp facet seams (darker cyan) radiating from the table
    for k in range(0, 5):
        put(px, cx, 5 + k, shade(GLOW[2], -0.22))     # central seam
    put(px, 6, 6, shade(GLOW[2], -0.22)); put(px, 5, 7, shade(GLOW[2], -0.22))
    put(px, 9, 6, shade(GLOW[3], -0.15)); put(px, 10, 7, shade(GLOW[3], -0.15))
    # a 1px white sparkle highlight
    put(px, 6, 5, GLOW[0])
    quantize_to_palette(img)
    return img


def _upgrade_chip(accent, glyph):
    """A small dark circuit chip with an accent border + a 1-letter-ish glyph."""
    S = 16
    img = new(S); px = acc(img)
    # chip body
    rect(px, 2, 2, 12, 12, FRAME[2])
    rect(px, 3, 3, 10, 10, FRAME[1])
    # bevel
    for i in range(2, 14):
        put(px, i, 2, FRAME[0]); put(px, 2, i, FRAME[0])
        put(px, i, 13, FRAME[3]); put(px, 13, i, FRAME[3])
    # accent border inset (this is what distinguishes the upgrade type)
    for i in range(4, 12):
        put(px, i, 4, accent); put(px, i, 11, shade(accent, -0.3))
        put(px, 4, i, accent); put(px, 11, i, shade(accent, -0.3))
    # chip pins
    for i in (5, 8, 11):
        put(px, i, 1, BODY[3]); put(px, i, 14, BODY[3])
        put(px, 1, i, BODY[3]); put(px, 14, i, BODY[3])
    # central glyph drawn as pixel coords (3x3-ish) in accent-light
    gl = shade(accent, 0.4)
    for (x, y) in glyph:
        put(px, 6 + x, 6 + y, gl)
    quantize_to_palette(img, extra=[accent, shade(accent, -0.3),
                                    shade(accent, 0.4)])
    return img


def speed_upgrade():
    # blue, ">" chevron (speed)
    glyph = [(0, 0), (1, 1), (0, 2), (2, 0), (3, 1), (2, 2)]
    return _upgrade_chip((0x4F, 0x9B, 0xE8), glyph)


def efficiency_upgrade():
    # green, a leaf/down-arrow (less waste)
    glyph = [(1, 0), (1, 1), (1, 2), (0, 1), (2, 1), (0, 2), (2, 2)]
    return _upgrade_chip((0x46, 0xC6, 0x6B), glyph)


def rf_efficiency_upgrade():
    # gold, a lightning bolt (energy)
    glyph = [(2, 0), (1, 1), (2, 1), (0, 2), (1, 2), (3, 1)]
    return _upgrade_chip((0xE0, 0xB4, 0x3A), glyph)


def buffer_upgrade():
    # violet, a stacked-bars buffer
    glyph = [(0, 0), (1, 0), (2, 0), (3, 0), (0, 2), (1, 2), (2, 2), (3, 2)]
    return _upgrade_chip((0x9B, 0x6B, 0xE8), glyph)


def creative_filament_spool():
    # 16x16 magenta spool + sheen (smaller cousin of the hero spool)
    S = 16
    img = new(S); px = acc(img)
    cx, cy = 8, 8
    mag = (0xE8, 0x4F, 0xB0)
    mhi, mmid, mlo = ramp3(mag)
    for y in range(S):
        for x in range(S):
            dx = (x + 0.5) - cx; dy = (y + 0.5) - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d > 7.2:
                continue
            lit = (-dx - dy) / (1.414 * max(d, 0.001))
            if d <= 2.0:
                put(px, x, y, FRAME[3] if d > 1.0 else FRAME[2])  # hub
            elif d <= 6.0:
                c = mhi if lit > 0.35 else (mlo if lit < -0.35 else mmid)
                put(px, x, y, c)
            else:
                put(px, x, y, BODY[2] if lit > 0 else BODY[4])   # flange
    # sheen + cyan-tipped magic end
    put(px, 5, 4, shade(mhi, 0.3)); put(px, 6, 4, shade(mhi, 0.3))
    put(px, 13, 8, mmid); put(px, 14, 8, GLOW[2])
    quantize_to_palette(img, extra=ramp3(mag))
    return img


def main():
    written = []
    for t in range(1, 9):
        written.append(save_item(filament_spool(t), f"filament_spool_t{t}"))
    written.append(save_item(blank_blueprint_disc(), "blank_blueprint_disc"))
    written.append(save_item(blueprint_disc(), "blueprint_disc"))
    written.append(save_item(scanner(), "scanner"))
    written.append(save_item(extrudium_crystal(), "extrudium_crystal"))
    written.append(save_item(speed_upgrade(), "speed_upgrade"))
    written.append(save_item(efficiency_upgrade(), "efficiency_upgrade"))
    written.append(save_item(rf_efficiency_upgrade(), "rf_efficiency_upgrade"))
    written.append(save_item(buffer_upgrade(), "buffer_upgrade"))
    written.append(save_item(creative_filament_spool(), "creative_filament_spool"))
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
