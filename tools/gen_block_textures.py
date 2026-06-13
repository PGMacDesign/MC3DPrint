#!/usr/bin/env python3
"""
Generate the MC3DPrint BLOCK textures to the VISUAL-REVAMP-BRIEF.

Hero (32x32): tier1..4_printer, tier5..8_fabricator (base), filament_winder.
Secondary (16x16): printer_casing, filament_converter, remote_terminal,
                   clock_generator, creative_energy_source, printite_ore.

Filenames are kept identical so existing cube_all models still resolve. Run
from repo root:  python3 tools/gen_block_textures.py
(Active variants are produced by gen_formed_textures.py over these bases.)

Tier ladder (the headline of this generator): a T1 must read as a bare desktop
printer and a T8 as an ornate glowing fabricator, with EVERY step visibly nicer.
Four monotonic signals stack with tier:
  1. accent trim   — bare corner dot (T1) -> full glowing accent border (T8)
  2. enclosure     — open desktop bed (T1-4) -> sealed industrial chamber (T5-8)
  3. smart display — no LCD (T1) -> tall multi-row readout (T8)
  4. hotend/core   — small dim glow (T1) -> large bright core (T8)
plus more vents, a second/third frame bevel, dual rails and a body polish step.
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
# Tier-scaling detail helpers — these are what make a T6 read as clearly better
# than a T4: more accent trim, a richer display, more vents, a bigger hotend.
# ---------------------------------------------------------------------------
def _reel(px, cx, cy, r, thi, tmid, tlo, flange=None):
    """A small tier-accent filament reel (the spool feeding the printer). The
    flange rim defaults to grey; pass `flange=(lit, shadow)` to tint the rim
    with tier colour so the spool itself reads as the tier hue (printers only).
    """
    rim_lit, rim_sh = (BODY[3], BODY[4]) if flange is None else flange
    for dy in range(-r - 1, r + 2):
        for dx in range(-r - 1, r + 2):
            d = (dx * dx + dy * dy) ** 0.5
            if d > r + 0.4:
                continue
            if d < 1.2:
                c = FRAME[3]                                  # hub hole
            elif d > r - 0.7:
                c = rim_lit if (dx + dy) < 0 else rim_sh      # flange rim
            else:
                lit = -(dx + dy)
                c = thi if lit > 0.8 else (tlo if lit < -0.8 else tmid)
            put(px, cx + dx, cy + dy, c)


def _lcd(px, x, y, w, h, rows):
    """A dark recessed readout with `rows` teal lines. Bigger panel + more rows
    at higher tier = a visibly smarter machine. Returns nothing."""
    field = (0x10, 0x14, 0x1E)
    rect(px, x, y, w, h, field)
    hline(px, x, y, w, FRAME[3]); vline(px, x, y, h, FRAME[3])             # inset shadow
    hline(px, x, y + h - 1, w, FRAME[0]); vline(px, x + w - 1, y, h, FRAME[0])  # lit edge
    for i in range(rows):
        ly = y + 1 + i * 2
        if ly <= y + h - 2:
            ln = max(1, w - 3 - (i % 2) * 2)
            c = ACCENT_TEAL if i == 0 else shade(ACCENT_TEAL, -0.4)
            rect(px, x + 1, ly, ln, 1, c)


def _accent_trim(px, tier, thi, tmid, tlo):
    """Tier-accent chassis trim — the single clearest tier signal. From a bare
    corner dot (T1) up to a full glowing accent border (T8)."""
    a, b = 2, H - 3                       # inner trim ring (on the 2nd bevel)
    corners = [(a, a), (b, a), (a, b), (b, b)]
    ncorner = 1 if tier == 1 else (2 if tier == 2 else 4)
    capcol = GLOW[1] if tier >= 8 else thi
    for (x, y) in corners[:ncorner]:
        put(px, x, y, capcol)
        if tier >= 3:                     # small L-shaped cap arms
            ax = 1 if x == a else -1
            ay = 1 if y == a else -1
            put(px, x + ax, y, tmid)
            put(px, x, y + ay, tmid)
    if tier >= 4:
        hline(px, a + 2, a, b - a - 3, tmid)        # top accent rail
    if tier >= 5:
        hline(px, a + 2, b, b - a - 3, tlo)         # bottom accent rail
    if tier >= 7:
        vline(px, a, a + 2, b - a - 3, tmid)        # left accent rail
        vline(px, b, a + 2, b - a - 3, tlo)         # right accent rail
    if tier >= 8:
        # apex: 1px brighter inner glow halo just inside the accent border
        for x in range(a + 2, b - 1):
            put(px, x, a + 1, shade(thi, 0.25))
            put(px, x, b - 1, shade(thi, -0.15))
        for y in range(a + 2, b - 1):
            put(px, a + 1, y, shade(thi, 0.25))
            put(px, b - 1, y, shade(thi, -0.15))


def _vents(px, tier):
    """Cooling vent slits on the left interior wall; count grows with tier."""
    n = {1: 1, 2: 1, 3: 2, 4: 2, 5: 3, 6: 3, 7: 4, 8: 4}[tier]
    for i in range(n):
        vy = 14 + i * 2
        put(px, 4, vy, FRAME[3]); put(px, 5, vy, shade(FRAME[1], 0.14))


def _printer_tier_accents(px, tier, thi, tmid, tlo):
    """Printer-only (T1-4) tier colour. The shared _accent_trim corner caps are
    too sparse to read at a glance — these add a couple of modest tier-coloured
    chassis features so each printer's tier is obvious by hue, while staying a
    clear step below the fabricators' decoration:
      * two short tier-coloured side struts on the interior walls (the chassis
        uprights), one pixel taller per tier;
      * a thin tier-coloured brand band on the body just above the bed.
    The cyan hotend/bed glow is untouched — only chassis trim gets tier colour.
    """
    # side struts: vertical tier-coloured bars on the left + right interior
    # walls, framed with a 1px shadow so they read as raised painted uprights.
    strut_h = 4 + tier            # T1=5 .. T4=8 px tall
    sy = 7
    for wx in (3, H - 4):
        for k in range(strut_h):
            y = sy + k
            c = thi if k == 0 else (tlo if k == strut_h - 1 else tmid)
            put(px, wx, y, c)
        put(px, wx, sy - 1, FRAME[3]); put(px, wx, sy + strut_h, FRAME[3])

    # brand band: a short horizontal tier-coloured strip on the lower chassis,
    # widening one notch per tier (T1 a stub .. T4 nearly full width).
    band_y = H - 10
    bw = 4 + (tier - 1) * 4       # T1=4 .. T4=16 px wide
    bx = (H - bw) // 2
    hline(px, bx, band_y, bw, tmid)
    put(px, bx, band_y, thi)                       # lit left cap
    put(px, bx + bw - 1, band_y, tlo)              # shadow right cap
    hline(px, bx, band_y + 1, bw, FRAME[3])        # 1px AO beneath the band


def _frame(px, fabricator, tier):
    """Dark metal frame. Fabricators get a heavier 3px border, a 3rd bevel ring
    and seam rivets so they read as an industrial core a clear cut above T4."""
    bt = 3 if fabricator else 2
    rect(px, 0, 0, H, H, FRAME[2])
    rect(px, bt, bt, H - 2 * bt, H - 2 * bt, FRAME[1])
    # outer bevel: light top-left, shadow bottom-right (single light)
    hline(px, 0, 0, H, FRAME[0]); vline(px, 0, 0, H, FRAME[0])
    hline(px, 0, H - 1, H, FRAME[3]); vline(px, H - 1, 0, H, FRAME[3])
    # second inner bevel for higher tiers / fabricators
    if fabricator or tier >= 3:
        hline(px, 2, 2, H - 4, shade(FRAME[1], 0.18))
        vline(px, 2, 2, H - 4, shade(FRAME[1], 0.18))
        hline(px, 2, H - 3, H - 4, FRAME[3]); vline(px, H - 3, 2, H - 4, FRAME[3])
    if fabricator:
        hline(px, 3, 3, H - 6, FRAME[3]); vline(px, 3, 3, H - 6, FRAME[3])
        for sx in range(6, H - 5, 4):                 # seam rivets top + bottom
            put(px, sx, 4, FRAME[3]); put(px, sx, H - 5, FRAME[3])
    # corner bolt studs (lit top-left, shadowed bottom-right)
    for (bx, by) in [(3, 3), (H - 4, 3), (3, H - 4), (H - 4, H - 4)]:
        put(px, bx, by, BODY[3])
    put(px, 3, 3, BODY[1]); put(px, H - 4, H - 4, FRAME[3])


# ---------------------------------------------------------------------------
# HERO: printer / fabricator block face
# ---------------------------------------------------------------------------
def printer_face(tier, fabricator=False):
    """
    Open-frame desktop printer (T1-4) or sealed industrial fabricator (T5-8).
    Shared anatomy: dark frame, a tier-accent feed reel top-left, a smart LCD
    top-right, a gantry rail with a machined extruder carriage, a descending
    nozzle and a glowing cyan hotend. Printers expose an open heated bed with a
    couple of laid cyan filament lines; fabricators seal it inside a dark build
    chamber with a larger core. Trim, display, glow and vents all scale up with
    tier so the machine visibly gets better the higher you go.
    """
    img = new(H)
    px = acc(img)
    tcol = TIER[tier]
    thi, tmid, tlo = ramp3(tcol)

    _frame(px, fabricator, tier)

    # higher-tier polish: a faint extra highlight inset on the interior panel
    if tier >= 4:
        hline(px, 5, 5, H - 10, shade(FRAME[1], 0.1))

    # --- tier-accent feed reel, top-left, with a 1px feed line to the carriage
    reel_r = 3 if fabricator else 2
    sp_x, sp_y = (7, 7) if fabricator else (6, 7)
    # printers tint the spool flange in tier colour so the reel reads as the
    # tier hue; fabricators keep the neutral grey flange (unchanged).
    reel_flange = None if fabricator else (thi, tlo)
    _reel(px, sp_x, sp_y, reel_r, thi, tmid, tlo, flange=reel_flange)

    # --- smart LCD readout, top-right; bigger + more rows with tier ---
    lcd_spec = {
        1: None,
        2: (7, 3, 1), 3: (7, 3, 1),
        4: (8, 5, 2), 5: (8, 5, 2),
        6: (8, 6, 3), 7: (9, 6, 3), 8: (9, 7, 3),
    }[tier]
    if lcd_spec:
        lw, lh, lrows = lcd_spec
        _lcd(px, 27 - lw, 4, lw, lh, lrows)

    # --- horizontal gantry rail (~1/3 down); dual rail from T3 = sturdier ---
    rail_y = 12
    rx0, rx1 = 4, H - 4
    hline(px, rx0, rail_y, rx1 - rx0, BODY[3])
    hline(px, rx0, rail_y + 1, rx1 - rx0, FRAME[3])     # rail AO
    put(px, rx0, rail_y, BODY[2]); put(px, rx1 - 1, rail_y, BODY[2])
    if tier >= 3 or fabricator:
        hline(px, rx0, rail_y - 2, rx1 - rx0, BODY[4])  # second rail bar
        hline(px, rx0, rail_y - 1, rx1 - rx0, FRAME[3])

    # --- machined extruder carriage riding the rail ---
    cw = 11 if fabricator else 9
    cx0 = (H - cw) // 2
    cy0 = rail_y - 3
    ch = 7 if fabricator else 6
    rect(px, cx0, cy0, cw, ch, BODY[2])
    hline(px, cx0, cy0, cw, BODY[0]); vline(px, cx0, cy0, ch, BODY[1])       # lit
    hline(px, cx0, cy0 + ch - 1, cw, BODY[4]); vline(px, cx0 + cw - 1, cy0, ch, BODY[3])  # shade
    hline(px, cx0 + 1, cy0 + ch, cw - 2, FRAME[3])                            # AO under head
    # recessed cooling vent window on the head
    vw = cw - 4
    rect(px, cx0 + 2, cy0 + 2, vw, 2, FRAME[3])
    hline(px, cx0 + 2, cy0 + 3, vw, shade(FRAME[1], -0.1))
    put(px, cx0 + 2 + vw - 1, cy0 + 3, FRAME[0])
    # tier indicator dot on the carriage
    put(px, cx0 + cw - 3, cy0 + ch - 2, thi)
    put(px, cx0 + cw - 2, cy0 + ch - 2, tmid)
    if fabricator or tier >= 4:                          # second vent slit (denser)
        rect(px, cx0 + 2, cy0 + 5, vw, 1, FRAME[3])

    # --- nozzle cone descending from carriage center ---
    nz_x = cx0 + cw // 2
    nz_y0 = cy0 + ch
    put(px, nz_x - 1, nz_y0, BODY[4]); put(px, nz_x, nz_y0, BODY[3])
    put(px, nz_x, nz_y0 + 1, BODY[4])
    hot_y = nz_y0 + (3 if fabricator else 2)

    if fabricator:
        # --- sealed build chamber: a dark windowed bay framed by accent trim,
        #     with a larger glowing core (and a small object being built) ---
        wx0, wy0, wx1, wy1 = 7, 17, H - 7, H - 6
        rect(px, wx0, wy0, wx1 - wx0, wy1 - wy0, (0x0B, 0x10, 0x18))
        hline(px, wx0, wy0, wx1 - wx0, FRAME[3]); vline(px, wx0, wy0, wy1 - wy0, FRAME[3])
        hline(px, wx0, wy1 - 1, wx1 - wx0, FRAME[0]); vline(px, wx1 - 1, wy0, wy1 - wy0, FRAME[0])
        # accent door posts on the chamber
        vline(px, wx0 - 1, wy0, wy1 - wy0, tlo); vline(px, wx1, wy0, wy1 - wy0, tlo)
        # a small printed object sitting on the chamber floor
        obj_y = wy1 - 2
        rect(px, nz_x - 2, obj_y, 4, 2, GLOW[3])
        rect(px, nz_x - 1, obj_y, 2, 1, GLOW[2])
        core_r = 2.4 + (tier - 5) * 0.45
        glow_dot(px, nz_x, hot_y + 1, core_r)
        put(px, nz_x, hot_y + 1, GLOW[0]); put(px, nz_x, hot_y, GLOW[1])
    else:
        # --- open heated bed with a couple of laid cyan filament lines ---
        bed_y = H - 8
        bx0, bx1 = 5, H - 5
        rect(px, bx0, bed_y, bx1 - bx0, 3, BODY[3])
        hline(px, bx0, bed_y, bx1 - bx0, BODY[2])
        hline(px, bx0, bed_y + 2, bx1 - bx0, BODY[4])
        hline(px, bx0, bed_y + 3, bx1 - bx0, FRAME[3])      # AO under bed
        vline(px, bx0 + 1, bed_y + 3, 2, FRAME[3]); vline(px, bx1 - 2, bed_y + 3, 2, FRAME[3])
        # heated-bed accent underline grows in from T2
        if tier >= 2:
            hline(px, bx0 + 2, bed_y + 1, (bx1 - bx0 - 4) * tier // 4, tlo)
        # laid filament layer on the bed
        layer_y = bed_y - 1
        for lx in range(nz_x - (1 + tier // 2), nz_x + (2 + tier // 2)):
            put(px, lx, layer_y, GLOW[3])
        put(px, nz_x - 1, layer_y, GLOW[2]); put(px, nz_x, layer_y, GLOW[2])
        hot_r = 1.7 + (tier - 1) * 0.28
        glow_dot(px, nz_x, hot_y, hot_r)
        if tier >= 3:
            put(px, nz_x, hot_y, GLOW[0]); put(px, nz_x, hot_y - 1, GLOW[1])
        # printer-only tier chassis accents (side struts + brand band) so each
        # T1-4 reads by hue; kept modest, a clear step below the fabricators.
        _printer_tier_accents(px, tier, thi, tmid, tlo)

    # feed line from reel to the carriage top (cyan-tinted near the head)
    fx, fy = sp_x + reel_r, sp_y + 1
    tx, ty = cx0 + 2, cy0
    steps = max(abs(tx - fx), abs(ty - fy), 1)
    for s in range(1, steps):
        x = fx + round((tx - fx) * s / steps)
        y = fy + round((ty - fy) * s / steps)
        put(px, x, y, tlo if s < steps - 2 else GLOW[3])

    _vents(px, tier)
    _accent_trim(px, tier, thi, tmid, tlo)

    quantize_to_palette(img, extra=ramp3(tcol) + ((0x0B, 0x10, 0x18),))
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


# ---------------------------------------------------------------------------
# Formed-multiblock casing TOP faces (16x16). When a controller forms its base,
# each casing's top face becomes a part of one big top-down 3D printer: a frame
# rail along the perimeter, a post at each corner, a heated bed in the interior.
# These are the glowing/active look (they only show while formed). They tile
# seamlessly so the whole N×N footprint reads as a single machine from above.
# ---------------------------------------------------------------------------
def casing_bed_top():
    """Interior cell: a heated print-bed grid. Grid lines sit on the 4px lattice
    (incl. tile edges) so they line up across cells; faint cyan at the nodes."""
    img = new(S); px = acc(img)
    rect(px, 0, 0, S, S, BODY[2])                    # machined bed surface
    hline(px, 0, 0, S, BODY[1]); vline(px, 0, 0, S, BODY[1])     # top-left sheen
    hline(px, 0, S - 1, S, BODY[3]); vline(px, S - 1, 0, S, BODY[3])
    for g in range(0, S, 4):                          # tileable recessed grid
        vline(px, g, 0, S, BODY[3]); hline(px, 0, g, S, BODY[3])
    for gy in range(0, S, 4):                         # faint cyan heat at nodes
        for gx in range(0, S, 4):
            put(px, gx, gy, GLOW[3])
    put(px, 8, 8, GLOW[2])                            # a little center shimmer
    quantize_to_palette(img)
    return img


def casing_rail_top():
    """Perimeter cell (base = N/S edge, an E-W extrusion). Runs edge-to-edge so
    cells chain into one continuous frame rail; the blockstate rotates it 90° for
    the E/W edges. A cyan power groove runs down the center of the bar."""
    img = new(S); px = acc(img)
    rect(px, 0, 0, S, S, FRAME[2])                   # dark plate behind the rail
    by0, by1 = 4, 12
    rect(px, 0, by0, S, by1 - by0, BODY[2])          # the extrusion bar
    hline(px, 0, by0, S, BODY[0]); hline(px, 0, by1 - 1, S, BODY[4])
    hline(px, 0, 7, S, FRAME[3])                     # recessed channel
    hline(px, 0, 8, S, GLOW[2])                      # cyan power groove
    hline(px, 0, 9, S, FRAME[3])
    for bx in (2, 8, 14):                            # bolt studs (tile-friendly)
        put(px, bx, 5, BODY[0]); put(px, bx, 10, BODY[4])
    quantize_to_palette(img)
    return img


def casing_corner_top():
    """Corner cell (base = NW): an L of extrusion — one arm running EAST, one
    SOUTH — meeting an outer post cap with a cyan status light. The blockstate
    rotates it to the other three corners; grooves meet the adjoining rails."""
    img = new(S); px = acc(img)
    rect(px, 0, 0, S, S, FRAME[2])
    rect(px, 0, 0, 12, 12, FRAME[1])                 # inner frame mass (outer corner)
    rect(px, 4, 4, S - 4, 8, BODY[2])                # EAST arm
    hline(px, 4, 4, S - 4, BODY[0]); hline(px, 4, 11, S - 4, BODY[4])
    rect(px, 4, 4, 8, S - 4, BODY[2])                # SOUTH arm
    vline(px, 4, 4, S - 4, BODY[0]); vline(px, 11, 4, S - 4, BODY[4])
    rect(px, 3, 3, 6, 6, BODY[3]); rect(px, 4, 4, 4, 4, BODY[1])   # post cap
    put(px, 5, 5, GLOW[1])                           # cyan corner status light
    hline(px, 8, 8, S - 8, GLOW[2])                  # east-arm groove (meets E-W rail)
    vline(px, 8, 8, S - 8, GLOW[2])                  # south-arm groove (meets N-S rail)
    quantize_to_palette(img)
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
    # formed-multiblock casing top faces
    written.append(save_block(casing_bed_top(), "casing_bed_top"))
    written.append(save_block(casing_rail_top(), "casing_rail_top"))
    written.append(save_block(casing_corner_top(), "casing_corner_top"))
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
