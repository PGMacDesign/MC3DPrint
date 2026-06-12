#!/usr/bin/env python3
"""
Generate the MC3DPrint ITEM textures to the VISUAL-REVAMP-BRIEF.

Hero (32x32): filament_spool_t1..t8, blank_blueprint_disc, blueprint_disc,
              scanner.
Secondary (16x16): printite_crystal, speed/efficiency/rf_efficiency/buffer
              upgrade, creative_filament_spool.

Same filenames -> item/generated models still resolve. Run from repo root:
  python3 tools/gen_item_textures.py
"""
import math

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
    H = 32
    img = new(H); px = acc(img)
    cx, cy = 15.5, 15.5
    coil = ramp3(TIER[tier])          # (highlight, base, shadow)
    chi, cmid, clo = coil

    R_OUT = 14.0     # outer flange edge
    R_FLANGE = 13.0  # inner flange (coil starts)
    R_COIL_IN = 5.5  # inner coil edge
    R_HUB = 4.0      # hub hole radius

    for y in range(H):
        for x in range(H):
            dx = (x + 0.5) - cx
            dy = (y + 0.5) - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if d > R_OUT:
                continue
            # light direction: top-left -> dot product with (-1,-1)
            lit = (-dx - dy) / (math.sqrt(2) * max(d, 0.001))  # -1..1
            if d <= R_HUB:
                # dark hub hole — keep it clearly visible
                if d > R_HUB - 1.0:
                    put(px, x, y, FRAME[3])             # hub rim
                else:
                    put(px, x, y, FRAME[2] if d < R_HUB - 1.8 else FRAME[3])
            elif d <= R_COIL_IN:
                # inner flange ring between hub and coil
                c = BODY[3] if lit > 0 else BODY[4]
                put(px, x, y, c)
            elif d <= R_FLANGE:
                # the wound filament coil — base/highlight/shadow by light + arcs
                if lit > 0.35:
                    c = chi
                elif lit < -0.35:
                    c = clo
                else:
                    c = cmid
                # concentric layer arcs: darken at 2 radii bands
                band = (d - R_COIL_IN)
                if abs((band % 2.6) - 0.0) < 0.7:
                    c = shade(c, -0.18)
                put(px, x, y, c)
            else:
                # thin grey flange rim
                c = BODY[2] if lit > 0 else BODY[4]
                if d > R_OUT - 0.9:
                    c = BODY[4] if lit < 0 else BODY[3]
                put(px, x, y, c)

    # top-left sheen highlight arc on the coil
    for a in range(200, 250, 4):
        rad = math.radians(a)
        for rr in (R_COIL_IN + 2.5, R_COIL_IN + 5.0):
            x = int(cx + math.cos(rad) * rr)
            y = int(cy + math.sin(rad) * rr)
            put(px, x, y, shade(chi, 0.25))
    # a couple of crisp hub spokes so it reads as a reel
    put(px, int(cx), int(cy - R_HUB), FRAME[3])
    put(px, int(cx), int(cy + R_HUB - 1), FRAME[3])
    # a 1px loose filament end with cyan tip (the feed end)
    ex, ey = int(cx + R_FLANGE - 1), int(cy)
    put(px, ex, ey, cmid)
    put(px, ex + 1, ey, clo)
    put(px, ex + 2, ey - 1, GLOW[3])

    quantize_to_palette(img, extra=ramp3(TIER[tier]))
    return img


# ---------------------------------------------------------------------------
# HERO 32x32: blueprint discs
# ---------------------------------------------------------------------------
def _disc_body(px, H):
    cx, cy = 15.5, 15.5
    R = 13.0
    for y in range(H):
        for x in range(H):
            dx = (x + 0.5) - cx; dy = (y + 0.5) - cy
            d = math.hypot(dx, dy)
            if d > R:
                continue
            lit = (-dx - dy) / (math.sqrt(2) * max(d, 0.001))
            if d > R - 1.0:
                c = FRAME[0] if lit > 0 else FRAME[3]     # bevel rim
            elif d > R - 2.2:
                c = FRAME[1]
            else:
                c = FRAME[2] if lit > -0.2 else FRAME[1]  # dark disc face
            put(px, x, y, c)
    # center hub
    for y in range(H):
        for x in range(H):
            dx = (x + 0.5) - cx; dy = (y + 0.5) - cy
            d = math.hypot(dx, dy)
            if d <= 2.2:
                put(px, x, y, FRAME[3] if d > 1.2 else FRAME[1])
    return cx, cy, R


def blank_blueprint_disc():
    H = 32
    img = new(H); px = acc(img)
    cx, cy, R = _disc_body(px, H)
    icx, icy = int(cx), int(cy)
    # an empty recessed face panel (the slot where a schematic would write) so it
    # reads as a blank disc, not just a dark circle. Inset shadow top-left.
    field = (0x10, 0x14, 0x1E)
    rect(px, icx - 6, icy - 6, 12, 12, field)
    rect(px, icx - 6, icy - 6, 12, 1, FRAME[3])   # top inset shadow
    rect(px, icx - 6, icy - 6, 1, 12, FRAME[3])   # left inset shadow
    rect(px, icx - 6, icy + 5, 12, 1, FRAME[1])   # bottom inset light
    rect(px, icx + 5, icy - 6, 1, 12, FRAME[1])   # right inset light
    # faint empty grid hint (very dim) so it echoes the written disc
    for i in range(icx - 5, icx + 6, 5):
        for j in range(icy - 5, icy + 6):
            put(px, i, j, shade(field, 0.6))
    # tiny dim teal idle status dot in a corner
    put(px, icx + 5, icy - 5, shade(ACCENT_TEAL, -0.35))
    quantize_to_palette(img, extra=[field, shade(field, 0.6),
                                    shade(ACCENT_TEAL, -0.35)])
    return img


def blueprint_disc():
    H = 32
    img = new(H); px = acc(img)
    cx, cy, R = _disc_body(px, H)
    icx, icy = int(cx), int(cy)
    # cyan holographic schematic: a small isometric structure glyph + grid
    field = (0x10, 0x14, 0x1E)
    rect(px, icx - 6, icy - 6, 12, 12, field)
    # grid lines
    for i in range(icx - 6, icx + 6, 3):
        for j in range(icy - 6, icy + 6):
            put(px, i, j, GLOW[3])
    for j in range(icy - 6, icy + 6, 3):
        for i in range(icx - 6, icx + 6):
            put(px, i, j, GLOW[3])
    # an isometric "structure" — a small cube wireframe in bright cyan
    cube = [
        (icx - 2, icy + 3), (icx + 2, icy + 3),         # bottom front edge
        (icx - 2, icy + 3), (icx - 2, icy - 1),         # left vert
        (icx + 2, icy + 3), (icx + 2, icy - 1),         # right vert
        (icx - 2, icy - 1), (icx + 2, icy - 1),         # top front
        (icx - 2, icy - 1), (icx, icy - 3),             # back-left up
        (icx + 2, icy - 1), (icx + 4, icy - 3),         # back-right up
        (icx, icy - 3), (icx + 4, icy - 3),             # top back
    ]
    # draw the cube edges
    def line(x0, y0, x1, y1, c):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for s in range(steps + 1):
            put(px, round(x0 + (x1 - x0) * s / steps),
                round(y0 + (y1 - y0) * s / steps), c)
    edges = [
        ((icx - 2, icy + 3), (icx + 2, icy + 3)),
        ((icx - 2, icy + 3), (icx - 2, icy - 1)),
        ((icx + 2, icy + 3), (icx + 2, icy - 1)),
        ((icx - 2, icy - 1), (icx + 2, icy - 1)),
        ((icx - 2, icy - 1), (icx, icy - 3)),
        ((icx + 2, icy - 1), (icx + 4, icy - 3)),
        ((icx, icy - 3), (icx + 4, icy - 3)),
        ((icx + 2, icy - 1), (icx + 2, icy + 1)),
    ]
    for (a, b) in edges:
        line(a[0], a[1], b[0], b[1], GLOW[2])
    # bright vertices
    for (vx, vy) in [(icx - 2, icy + 3), (icx + 2, icy + 3), (icx - 2, icy - 1),
                     (icx + 2, icy - 1), (icx, icy - 3), (icx + 4, icy - 3)]:
        put(px, vx, vy, GLOW[0])
    quantize_to_palette(img, extra=[(0x10, 0x14, 0x1E)])
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
def printite_crystal():
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
    written.append(save_item(printite_crystal(), "printite_crystal"))
    written.append(save_item(speed_upgrade(), "speed_upgrade"))
    written.append(save_item(efficiency_upgrade(), "efficiency_upgrade"))
    written.append(save_item(rf_efficiency_upgrade(), "rf_efficiency_upgrade"))
    written.append(save_item(buffer_upgrade(), "buffer_upgrade"))
    written.append(save_item(creative_filament_spool(), "creative_filament_spool"))
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
