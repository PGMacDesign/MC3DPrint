#!/usr/bin/env python3
"""
Generate the MC3DPrint RESIN consumable item textures.

A "resin" is a small vial/flask of glowing resin: a machined glass phial with a
dark stopper, a tinted glowing liquid inside, a top-left sheen on the glass, and
a small meniscus highlight on the liquid. Colour encodes the EFFECT family; tier
encodes intensity:

  Effect family       hue
  ------------------  ----------------------------
  base (raw)          pale translucent off-white/grey
  verdant             green
  xp                  bright chartreuse / yellow-green
  treasure            gold / amber
  overdrive           red / orange
  quartermaster       blue / cyan
  ore_salting         stony grey-brown

  Tier  fill   glow            tier pips   glint
  ----  -----  --------------  ---------   -----
  T1    low    dim             1           no
  T2    mid    brighter        2           no
  T3    high   brightest       3           yes (1px white sparkle)

12 items: resin_base, resin_verdant_t1/t2, resin_xp_t1/t2/t3,
resin_treasure_t2/t3, resin_overdrive_t2/t3, resin_quartermaster_t3,
resin_ore_salting_t3.

Same item/generated models resolve off these filenames. Run from repo root:
  python3 tools/gen_resin_items.py
"""
from PIL import Image  # noqa: F401  (kept for parity / future ImageDraw use)

from tex_common import (BODY, FRAME, GLOW, shade, ramp3, new,
                        quantize_to_palette, save_item)


def acc(img):
    return (img.load(), img.size[0], img.size[1])


def put(px, x, y, c, a=255):
    if 0 <= x < px[1] and 0 <= y < px[2]:
        px[0][x, y] = (c[0], c[1], c[2], a)


# ---------------------------------------------------------------------------
# Effect-family base hues (the glowing liquid colour).
# ---------------------------------------------------------------------------
EFFECT = {
    "base":          (0xD8, 0xDC, 0xCE),  # pale off-white/grey raw resin
    "verdant":       (0x3F, 0xB8, 0x42),  # green
    "xp":            (0x9B, 0xE0, 0x2A),  # chartreuse / yellow-green
    "treasure":      (0xE0, 0xA8, 0x2E),  # gold / amber
    "overdrive":     (0xE6, 0x52, 0x28),  # red / orange
    "quartermaster": (0x35, 0xA8, 0xE6),  # blue / cyan
    "ore_salting":   (0x8C, 0x7A, 0x66),  # stony grey-brown
}

# Per-tier intensity. fill_top = topmost liquid row (lower y = fuller phial);
# glow_t lightens the liquid highlights as tiers climb.
TIER_FILL = {1: 9, 2: 7, 3: 6}     # liquid surface row in the phial body
TIER_GLOW = {1: 0.10, 2: 0.24, 3: 0.40}
TIER_PIPS = {1: 1, 2: 2, 3: 3}

# Phial silhouette (16x16): a rounded-shoulder bottle, neck, stopper.
# rows of the GLASS interior as (y, x_left, x_right) inclusive.
BODY_ROWS = [
    (5, 6, 9),    # shoulder taper
    (6, 5, 10),
    (7, 4, 11),
    (8, 4, 11),
    (9, 4, 11),
    (10, 4, 11),
    (11, 4, 11),
    (12, 5, 10),  # rounded base
    (13, 6, 9),
]
NECK_ROWS = [
    (3, 7, 8),
    (4, 6, 9),
]


def resin(effect, tier):
    """Draw one tinted resin phial at 16x16."""
    S = 16
    img = new(S); px = acc(img)
    base = EFFECT[effect]
    glow = TIER_GLOW[tier]
    lo = shade(base, -0.34 + (-0.06))          # liquid shadow
    mid = shade(base, glow * 0.5)               # liquid body, brightens by tier
    hi = shade(base, 0.30 + glow)               # liquid highlight + glow
    surf = shade(base, 0.46 + glow)             # bright liquid surface meniscus
    fill_top = TIER_FILL[tier]

    glass = BODY[2]
    glass_hi = BODY[0]
    glass_lo = BODY[4]
    cork = FRAME[1]
    cork_hi = FRAME[0]
    cork_lo = FRAME[3]

    # --- stopper / cork (top) ---
    for x in range(6, 10):
        put(px, x, 0, cork_hi if x < 8 else cork)   # cork cap top, lit left
    for x in range(6, 10):
        put(px, x, 1, cork if x < 9 else cork_lo)
    for x in range(7, 9):
        put(px, x, 2, cork_lo)                       # cork shoulder into neck

    # --- neck (glass, empty above the liquid) ---
    for (y, xl, xr) in NECK_ROWS:
        for x in range(xl, xr + 1):
            if x == xl:
                put(px, x, y, glass_hi)
            elif x == xr:
                put(px, x, y, glass_lo)
            else:
                put(px, x, y, glass)

    # --- body: glass walls + tinted liquid fill ---
    for (y, xl, xr) in BODY_ROWS:
        for x in range(xl, xr + 1):
            edge_l = (x == xl)
            edge_r = (x == xr)
            if y < fill_top:
                # empty glass above the liquid line
                if edge_l:
                    put(px, x, y, glass_hi)
                elif edge_r:
                    put(px, x, y, glass_lo)
                else:
                    put(px, x, y, shade(glass, 0.18))   # bright airy glass
            else:
                # tinted liquid; glass rim still reads on the very edges
                if edge_l:
                    put(px, x, y, shade(hi, 0.10))      # lit glass+liquid edge
                elif edge_r:
                    put(px, x, y, lo)                   # shadow edge
                elif y == fill_top:
                    put(px, x, y, surf)                 # bright meniscus surface
                else:
                    # top-left lit interior gradient
                    if x <= xl + 1 and y <= fill_top + 2:
                        put(px, x, y, hi)
                    elif x >= xr - 1 or y >= 12:
                        put(px, x, y, lo)
                    else:
                        put(px, x, y, mid)

    # --- one clean top-left glass sheen streak ---
    put(px, 5, 6, glass_hi)
    put(px, 5, 7, shade(glass, 0.30))
    put(px, 6, 7, shade(glass, 0.22))

    # --- tier pips: small glowing dots down the lit-left interior ---
    pip = surf
    pip_rows = {1: [11], 2: [10, 12], 3: [9, 11, 13]}[tier]
    for i, py in enumerate(pip_rows):
        if py <= max(r[0] for r in BODY_ROWS):
            put(px, 8, py, pip)

    # --- T3 glint: a 1px white sparkle on the liquid surface ---
    if tier >= 3:
        put(px, 6, fill_top, GLOW[0])
        put(px, 5, fill_top, shade(GLOW[1], 0.0))

    quantize_to_palette(img, extra=[lo, mid, hi, surf,
                                    shade(glass, 0.18), shade(glass, 0.30),
                                    shade(glass, 0.22), shade(hi, 0.10)])
    return img


def main():
    items = [
        ("resin_base",              "base",          1),
        ("resin_verdant_t1",        "verdant",       1),
        ("resin_verdant_t2",        "verdant",       2),
        ("resin_xp_t1",             "xp",            1),
        ("resin_xp_t2",             "xp",            2),
        ("resin_xp_t3",             "xp",            3),
        ("resin_treasure_t2",       "treasure",      2),
        ("resin_treasure_t3",       "treasure",      3),
        ("resin_overdrive_t2",      "overdrive",     2),
        ("resin_overdrive_t3",      "overdrive",     3),
        ("resin_quartermaster_t3",  "quartermaster", 3),
        ("resin_ore_salting_t3",    "ore_salting",   3),
    ]
    written = []
    for name, effect, tier in items:
        written.append(save_item(resin(effect, tier), name))
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
