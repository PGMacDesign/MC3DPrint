#!/usr/bin/env python3
"""
Generate the MC3DPrint BLOCK textures to the VISUAL-REVAMP-BRIEF.

Hero (32x32): tier1..4_printer, tier5..8_fabricator (base), filament_winder.
Secondary (16x16): printer_casing, filament_converter, remote_terminal,
                   clock_generator, creative_energy_source, printite_ore.

Filenames are kept identical so existing cube_all models still resolve. Run
from repo root:  python3 tools/gen_block_textures.py
(Active variants are produced by gen_formed_textures.py over these bases.)
"""
from PIL import Image

from tex_common import (BODY, FRAME, GLOW, TIER, ACCENT_TEAL, ramp3, shade,
                        new, quantize_to_palette, save_block)

H = 32  # hero resolution


def put(px, x, y, c, a=255):
    if 0 <= x < px[1] and 0 <= y < px[2]:
        px[0][x, y] = (c[0], c[1], c[2], a)


def rect(px, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            put(px, xx, yy, c)


def hline(px, x, y, w, c):
    rect(px, x, y, w, 1, c)


def vline(px, x, y, h, c):
    rect(px, x, y, 1, h, c)


def acc(img):
    """wrap pixel access with width/height for bounds checks"""
    return (img.load(), img.size[0], img.size[1])


# ---------------------------------------------------------------------------
# Emissive cyan glow dot (white core, halo, body) — radial, breaks light rule.
# ---------------------------------------------------------------------------
def glow_dot(px, cx, cy, r, intensity=1.0):
    rng = int(r) + 1
    for dy in range(-rng, rng + 1):
        for dx in range(-rng, rng + 1):
            d = (dx * dx + dy * dy) ** 0.5
            if d > r + 0.5:
                continue
            t = d / max(r, 0.001)
            if t < 0.32:
                c = GLOW[0]                       # white core
            elif t < 0.6:
                c = GLOW[1]                       # halo
            elif t < 0.85:
                c = GLOW[2]                       # body
            else:
                c = GLOW[3]                       # falloff
            if intensity < 1.0:
                c = shade(GLOW[3] if intensity < 0.5 else c, 0)
                c = tuple(int(c[i] * (0.55 + 0.45 * intensity)) for i in range(3))
            put(px, cx + dx, cy + dy, c)


# ---------------------------------------------------------------------------
# HERO: printer / fabricator block face
# ---------------------------------------------------------------------------
def printer_face(tier, fabricator=False):
    """
    Dark square frame; a machined extruder carriage on a horizontal rail ~1/3
    down; glowing cyan nozzle below it; a tier-accent spool dot top-corner with
    a 1px feed line down to the carriage; a light-grey bed slab in the lower
    third. Fabricators get a heavier frame, a bigger tier-accent core and a
    larger hotend; detail rises with tier.
    """
    img = new(H)
    px = acc(img)
    tcol = TIER[tier]
    thi, tmid, tlo = ramp3(tcol)

    # --- base panel fill (dark metal) ---
    rect(px, 0, 0, H, H, FRAME[2])
    # subtle interior panel a touch lighter so the frame border reads
    inset = 5 if fabricator else 4
    rect(px, inset, inset, H - 2 * inset, H - 2 * inset, FRAME[1])

    # --- outer frame bevel: light top-left, shadow bottom-right (single light) ---
    hline(px, 0, 0, H, FRAME[0])
    vline(px, 0, 0, H, FRAME[0])
    hline(px, 0, H - 1, H, FRAME[3])
    vline(px, H - 1, 0, H, FRAME[3])
    # second inner bevel for higher tiers / fabricators (more detail)
    if fabricator or tier >= 3:
        hline(px, 2, 2, H - 4, shade(FRAME[1], 0.18))
        vline(px, 2, 2, H - 4, shade(FRAME[1], 0.18))
        hline(px, 2, H - 3, H - 4, FRAME[3])
        vline(px, H - 3, 2, H - 4, FRAME[3])
    if fabricator:
        # heavier/denser frame: a darker base tint + panel-seam rivets along
        # the top and bottom margins so it reads as an industrial core block.
        for sx in range(6, H - 5, 4):
            put(px, sx, 4, FRAME[3])
            put(px, sx, H - 5, FRAME[3])
        # a 3rd inner bevel ring (extra thickness)
        hline(px, 3, 3, H - 6, FRAME[3])
        vline(px, 3, 3, H - 6, FRAME[3])

    # --- corner bolt studs ---
    for (bx, by) in [(3, 3), (H - 4, 3), (3, H - 4), (H - 4, H - 4)]:
        put(px, bx, by, BODY[3])
        put(px, bx, by, BODY[1] if (bx < 8 and by < 8) else BODY[3])
    # re-light the top-left stud, shadow the bottom-right ones
    put(px, 3, 3, BODY[1])
    put(px, H - 4, H - 4, FRAME[3])

    # --- horizontal rail (gantry) ~1/3 down ---
    rail_y = 11
    rail_x0, rail_x1 = 4, H - 4
    hline(px, rail_x0, rail_y, rail_x1 - rail_x0, BODY[3])         # rail bar
    hline(px, rail_x0, rail_y + 1, rail_x1 - rail_x0, FRAME[3])    # rail shadow (AO)
    hline(px, rail_x0, rail_y - 1, rail_x1 - rail_x0, BODY[4])     # rail top edge faint
    # rail end mounts
    put(px, rail_x0, rail_y, BODY[2])
    put(px, rail_x1 - 1, rail_y, BODY[2])

    # --- extruder carriage (machined light-grey box riding the rail) ---
    cw = 10 if fabricator else 8
    cx0 = (H - cw) // 2 - (0 if fabricator else 1)
    cy0 = rail_y - 1
    ch = 7 if fabricator else 6
    # body
    rect(px, cx0, cy0, cw, ch, BODY[2])
    # top-left lit edges
    hline(px, cx0, cy0, cw, BODY[0])
    vline(px, cx0, cy0, ch, BODY[1])
    # bottom-right shadow edges + AO seam
    hline(px, cx0, cy0 + ch - 1, cw, BODY[4])
    vline(px, cx0 + cw - 1, cy0, ch, BODY[3])
    # a darker AO line just under the carriage
    hline(px, cx0 + 1, cy0 + ch, cw - 2, FRAME[3])
    # carriage detail: a single recessed vent slot (a dark inset window) so it
    # reads as a machined head, not a face. Inset shadow top-left, lit bottom.
    vw = cw - 4
    rect(px, cx0 + 2, cy0 + 2, vw, 2, FRAME[3])
    hline(px, cx0 + 2, cy0 + 2, vw, FRAME[3])
    hline(px, cx0 + 2, cy0 + 3, vw, shade(FRAME[1], -0.1))
    put(px, cx0 + 2, cy0 + 2, FRAME[3])
    put(px, cx0 + 2 + vw - 1, cy0 + 3, FRAME[0])  # lit bottom-right corner of inset
    # tier indicator dot on the carriage (just under the vent)
    put(px, cx0 + cw - 3, cy0 + 4, thi)
    put(px, cx0 + cw - 2, cy0 + 4, tmid)
    if fabricator:
        # extra vent slit row on the carriage (denser machine)
        rect(px, cx0 + 2, cy0 + 5, vw, 1, FRAME[3])

    # --- nozzle cone descending from carriage center ---
    nz_x = cx0 + cw // 2
    nz_y0 = cy0 + ch
    # cone: 2px wide tapering to 1px
    put(px, nz_x - 1, nz_y0, BODY[4])
    put(px, nz_x, nz_y0, BODY[3])
    put(px, nz_x, nz_y0 + 1, BODY[4])
    nozzle_tip_y = nz_y0 + 2

    # --- light-grey bed slab in lower third ---
    bed_y = H - 9
    bed_x0, bed_x1 = 5, H - 5
    rect(px, bed_x0, bed_y, bed_x1 - bed_x0, 3, BODY[3])  # slab thickness 3px
    hline(px, bed_x0, bed_y, bed_x1 - bed_x0, BODY[2])    # lit top (toned down)
    hline(px, bed_x0, bed_y + 2, bed_x1 - bed_x0, BODY[4])  # shadow underside
    hline(px, bed_x0, bed_y + 3, bed_x1 - bed_x0, FRAME[3])  # AO under bed
    # bed support legs
    vline(px, bed_x0 + 1, bed_y + 3, 2, FRAME[3])
    vline(px, bed_x1 - 2, bed_y + 3, 2, FRAME[3])

    # --- a couple of laid filament layers on the bed (cyan, dim) for printers ---
    if not fabricator:
        layer_y = bed_y - 1
        for lx in range(nz_x - 3, nz_x + 4):
            put(px, lx, layer_y, GLOW[3])
        put(px, nz_x - 1, layer_y, GLOW[2])
        put(px, nz_x, layer_y, GLOW[2])

    # --- glowing cyan nozzle dot (the hotend) ---
    hot_r = 2.4 if not fabricator else 3.0
    hot_y = nozzle_tip_y + (1 if not fabricator else 2)
    if fabricator:
        # fabricator: a bigger tier-accent core ring behind a larger hotend
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d = (dx * dx + dy * dy) ** 0.5
                if 2.2 <= d <= 3.3:
                    put(px, nz_x + dx, hot_y + dy, tlo)
                elif 1.4 <= d < 2.2:
                    put(px, nz_x + dx, hot_y + dy, tmid)
    glow_dot(px, nz_x, hot_y, hot_r)
    # brighter hotend for higher tiers
    if tier >= 3 or fabricator:
        put(px, nz_x, hot_y, GLOW[0])
        put(px, nz_x, hot_y - 1, GLOW[1])

    # --- tier-accent spool dot top-corner + 1px feed line to carriage ---
    sp_x, sp_y = 6, 6
    # spool: small donut, accent coil + dark hub
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 2.3:
                if d < 0.8:
                    c = FRAME[3]                 # hub hole
                elif dx < 0 and dy < 0:
                    c = thi                      # lit
                elif dx > 0 or dy > 0:
                    c = tlo                      # shadow
                else:
                    c = tmid
                put(px, sp_x + dx, sp_y + dy, c)
    # flange rim hint
    put(px, sp_x - 2, sp_y - 1, BODY[3])
    put(px, sp_x + 2, sp_y + 1, BODY[4])
    # feed line: from spool down/right to carriage top (cyan-tinted near head)
    fx, fy = sp_x + 2, sp_y + 1
    tx, ty = cx0 + 2, cy0
    steps = max(abs(tx - fx), abs(ty - fy))
    for s in range(1, steps):
        x = fx + round((tx - fx) * s / steps)
        y = fy + round((ty - fy) * s / steps)
        put(px, x, y, tlo if s < steps - 2 else GLOW[3])

    # higher tiers: add side vents on the frame (more detail)
    if tier >= 2 or fabricator:
        vy = H - 13
        for i in range(3):
            put(px, 3, vy + i * 2, FRAME[3])
            put(px, 4, vy + i * 2, shade(FRAME[1], 0.12))
            put(px, H - 4, vy + i * 2, FRAME[3])
            put(px, H - 5, vy + i * 2, shade(FRAME[1], 0.12))
    if tier >= 4 or fabricator:
        # a tiny status LED (tier accent) near a bottom corner
        put(px, 5, H - 6, thi)

    quantize_to_palette(img, extra=ramp3(tcol))
    return img


# ---------------------------------------------------------------------------
# HERO: filament winder — body with horizontal spindle + partial spool winding
# ---------------------------------------------------------------------------
def filament_winder():
    img = new(H)
    px = acc(img)

    rect(px, 0, 0, H, H, FRAME[2])
    rect(px, 4, 4, H - 8, H - 8, FRAME[1])
    hline(px, 0, 0, H, FRAME[0]); vline(px, 0, 0, H, FRAME[0])
    hline(px, 0, H - 1, H, FRAME[3]); vline(px, H - 1, 0, H, FRAME[3])
    hline(px, 2, 2, H - 4, shade(FRAME[1], 0.18))
    vline(px, 2, 2, H - 4, shade(FRAME[1], 0.18))
    hline(px, 2, H - 3, H - 4, FRAME[3]); vline(px, H - 3, 2, H - 4, FRAME[3])

    # two upright bearing posts holding a horizontal spindle
    post_y0, post_h = 9, 14
    for postx in (8, H - 9):
        rect(px, postx, post_y0, 2, post_h, BODY[3])
        vline(px, postx, post_y0, post_h, BODY[1])      # lit left edge
        vline(px, postx + 1, post_y0, post_h, BODY[4])  # shadow right
        # bearing cap
        rect(px, postx - 1, post_y0 - 1, 4, 2, BODY[2])
        put(px, postx - 1, post_y0 - 1, BODY[0])

    # spindle shaft across
    spin_y = post_y0 + 3
    hline(px, 9, spin_y, H - 18, FRAME[3])
    hline(px, 9, spin_y + 1, H - 18, BODY[4])

    # partial wound spool on the spindle (grey, tier-less) — left 2/3
    coil_x0, coil_x1 = 11, H - 13
    coil_y0, coil_y1 = spin_y - 2, spin_y + 6
    for x in range(coil_x0, coil_x1):
        for y in range(coil_y0, coil_y1):
            # rounded ends
            edge = (x < coil_x0 + 1 or x >= coil_x1 - 1)
            c = BODY[3] if (y < coil_y0 + 1 or y >= coil_y1 - 1 or edge) else BODY[2]
            if y == coil_y0:
                c = BODY[1]
            if y == coil_y1 - 1:
                c = BODY[4]
            put(px, x, y, c)
    # winding lines (a few darker verticals = wound filament layers)
    for x in range(coil_x0 + 2, coil_x1 - 1, 2):
        for y in range(coil_y0 + 1, coil_y1 - 1):
            put(px, x, y, BODY[4])

    # flange disc on the right end of the coil
    fl_x = coil_x1
    for dy in range(-4, 5):
        for dx in range(0, 2):
            d = abs(dy)
            if d <= 4:
                c = BODY[2] if dx == 0 else BODY[4]
                if dy < 0:
                    c = BODY[1] if dx == 0 else BODY[3]
                put(px, fl_x + dx, spin_y + 2 + dy, c)

    # feed filament coming in from top with a cyan feed glow at the contact pt
    feed_x = coil_x1 + 1
    for y in range(5, coil_y0):
        put(px, feed_x, y, GLOW[3])
    put(px, feed_x, coil_y0 - 1, GLOW[2])
    glow_dot(px, feed_x, coil_y0, 1.8)

    # control strip with tiny teal status at the base
    rect(px, 6, H - 6, 6, 2, FRAME[3])
    put(px, 7, H - 5, ACCENT_TEAL)
    put(px, 9, H - 5, BODY[4])

    quantize_to_palette(img)
    return img


# ---------------------------------------------------------------------------
# SECONDARY 16x16 blocks
# ---------------------------------------------------------------------------
S = 16


def s_acc(img):
    return (img.load(), img.size[0], img.size[1])


def s_frame(px, light=FRAME[0], dark=FRAME[3], base=FRAME[2], inner=FRAME[1]):
    rect(px, 0, 0, S, S, base)
    rect(px, 2, 2, S - 4, S - 4, inner)
    hline(px, 0, 0, S, light); vline(px, 0, 0, S, light)
    hline(px, 0, S - 1, S, dark); vline(px, S - 1, 0, S, dark)
    # corner studs
    for (x, y) in [(2, 2), (S - 3, 2), (2, S - 3), (S - 3, S - 3)]:
        put(px, x, y, BODY[3])
    put(px, 2, 2, BODY[1])
    put(px, S - 3, S - 3, FRAME[3])


def printer_casing():
    img = new(S); px = s_acc(img)
    s_frame(px)
    # cross-brace plating + a small light-grey access hatch center
    rect(px, 6, 6, 4, 4, BODY[3])
    hline(px, 6, 6, 4, BODY[1]); vline(px, 6, 6, 4, BODY[1])
    hline(px, 6, 9, 4, BODY[4]); vline(px, 9, 6, 4, BODY[4])
    put(px, 7, 7, BODY[2])
    # rivet lines along edges
    for i in (4, 8, 11):
        put(px, i, 3, FRAME[3]); put(px, i, S - 4, FRAME[3])
        put(px, 3, i, FRAME[3]); put(px, S - 4, i, FRAME[3])
    quantize_to_palette(img)
    return img


def filament_converter():
    img = new(S); px = s_acc(img)
    s_frame(px)
    # clearer motif: a machined drum in the middle with a cyan throughput arrow
    # passing left->right (a converter = filament transformed as it passes).
    # central converter drum (light-grey machined body)
    rect(px, 6, 5, 4, 6, BODY[3])
    hline(px, 6, 5, 4, BODY[1]); vline(px, 6, 5, 6, BODY[1])
    hline(px, 6, 10, 4, BODY[4]); vline(px, 9, 5, 6, BODY[4])
    # a teal readout window on the drum
    rect(px, 7, 6, 2, 1, ACCENT_TEAL)
    put(px, 7, 8, BODY[2])
    # cyan throughput arrow across the middle (left in, right out)
    ay = 8
    for x in range(3, 13):
        put(px, x, ay, GLOW[3])
    put(px, 4, ay, GLOW[2]); put(px, 11, ay, GLOW[1])
    # arrowhead at the right
    put(px, 12, ay, GLOW[0])
    put(px, 11, ay - 1, GLOW[2]); put(px, 11, ay + 1, GLOW[2])
    put(px, 10, ay - 2, GLOW[3]); put(px, 10, ay + 2, GLOW[3])
    quantize_to_palette(img)
    return img


def remote_terminal():
    img = new(S); px = s_acc(img)
    s_frame(px)
    # a screen: dark inset field with teal console lines (console identity)
    rect(px, 4, 4, 8, 6, (0x10, 0x14, 0x1E))
    hline(px, 4, 4, 8, FRAME[3]); vline(px, 4, 4, 6, FRAME[3])
    hline(px, 4, 9, 8, FRAME[0]); vline(px, 11, 4, 6, FRAME[0])
    # accent top line = console
    hline(px, 5, 5, 6, ACCENT_TEAL)
    # text rows
    put(px, 5, 7, ACCENT_TEAL); put(px, 6, 7, BODY[2]); put(px, 7, 7, BODY[2])
    put(px, 5, 8, BODY[3]); put(px, 6, 8, BODY[3])
    # a couple status LEDs below the screen
    put(px, 5, 12, (0x46, 0xC6, 0x6B))  # green ready
    put(px, 8, 12, (0xE0, 0xB4, 0x3A))  # amber working
    quantize_to_palette(img, extra=[(0x46, 0xC6, 0x6B), (0xE0, 0xB4, 0x3A),
                                    (0x10, 0x14, 0x1E)])
    return img


def clock_generator():
    img = new(S); px = s_acc(img)
    s_frame(px)
    # a clock-faced energy core: dark dial + cyan hands + ticks
    cx, cy = 8, 8
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 4.3:
                c = (0x10, 0x14, 0x1E)
                if 3.4 <= d <= 4.3:
                    c = BODY[3] if (dx < 0 or dy < 0) else BODY[4]  # bezel
                put(px, cx + dx, cy + dy, c)
    # tick marks
    for (tx, ty) in [(cx, cy - 3), (cx + 3, cy), (cx, cy + 3), (cx - 3, cy)]:
        put(px, tx, ty, BODY[2])
    # cyan hands
    put(px, cx, cy, GLOW[0])
    put(px, cx, cy - 1, GLOW[2]); put(px, cx, cy - 2, GLOW[3])  # minute up
    put(px, cx + 1, cy, GLOW[2]); put(px, cx + 2, cy, GLOW[3])  # hour right
    quantize_to_palette(img, extra=[(0x10, 0x14, 0x1E)])
    return img


def creative_energy_source():
    img = new(S); px = s_acc(img)
    s_frame(px, base=FRAME[3], inner=FRAME[2])
    # a brilliant magenta-cyan creative core (full-bright energy)
    cx, cy = 8, 8
    mag = (0xE8, 0x4F, 0xB0)
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 5:
                if d < 1.2:
                    c = (0xFF, 0xFF, 0xFF)
                elif d < 2.4:
                    c = (0xFF, 0xCF, 0xF0)
                elif d < 3.6:
                    c = mag
                else:
                    c = (0x9B, 0x2E, 0x70)
                put(px, cx + dx, cy + dy, c)
    # cyan corner conduits to show "energy"
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        put(px, x, y, GLOW[1])
    quantize_to_palette(img, extra=[(0xFF, 0xCF, 0xF0), (0x9B, 0x2E, 0x70),
                                    (0xE8, 0x4F, 0xB0)])
    return img


def printite_ore():
    img = new(S); px = s_acc(img)
    # stone base (use frame greys as "stone")
    stone = [(0x88, 0x88, 0x90), (0x70, 0x70, 0x78), (0x58, 0x58, 0x60),
             (0x44, 0x44, 0x4C)]
    rect(px, 0, 0, S, S, stone[2])
    # mottled stone texture (deliberate, not random) via a fixed pattern
    pattern = [(2, 1), (5, 2), (9, 1), (13, 3), (1, 5), (7, 5), (11, 6),
               (14, 8), (3, 9), (6, 11), (10, 10), (13, 12), (2, 13), (8, 14)]
    for i, (x, y) in enumerate(pattern):
        put(px, x, y, stone[1] if i % 2 else stone[3])
    hline(px, 0, 0, S, stone[0]); vline(px, 0, 0, S, stone[0])
    hline(px, 0, S - 1, S, stone[3]); vline(px, S - 1, 0, S, stone[3])
    # cyan crystal clusters (printite = cyan-glow ore)
    clusters = [(5, 5), (10, 8), (7, 11)]
    for (cx, cy) in clusters:
        put(px, cx, cy, GLOW[0])
        put(px, cx - 1, cy, GLOW[2]); put(px, cx + 1, cy, GLOW[2])
        put(px, cx, cy - 1, GLOW[2]); put(px, cx, cy + 1, GLOW[3])
        put(px, cx + 1, cy + 1, GLOW[3])
    quantize_to_palette(img, extra=stone)
    return img


def main():
    written = []
    # heroes
    for t in (1, 2, 3, 4):
        written.append(save_block(printer_face(t, fabricator=False),
                                  f"tier{t}_printer"))
    for t in (5, 6, 7, 8):
        written.append(save_block(printer_face(t, fabricator=True),
                                  f"tier{t}_fabricator"))
    written.append(save_block(filament_winder(), "filament_winder"))
    # secondary
    written.append(save_block(printer_casing(), "printer_casing"))
    written.append(save_block(filament_converter(), "filament_converter"))
    written.append(save_block(remote_terminal(), "remote_terminal"))
    written.append(save_block(clock_generator(), "clock_generator"))
    written.append(save_block(creative_energy_source(), "creative_energy_source"))
    written.append(save_block(printite_ore(), "printite_ore"))
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
