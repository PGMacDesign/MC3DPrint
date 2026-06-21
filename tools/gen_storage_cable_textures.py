#!/usr/bin/env python3
"""
Textures for the Filament Rack and the MC3D Cable.

Rack: a machined shelf with a 2x4 grid of dark cubbies on the front (the
renderer draws the actual tier-colored spools into those cubbies). Cable: dark
metal conduit with a cyan emissive core line so it reads as a powered wire.

Run:  python3 tools/gen_storage_cable_textures.py
(16x16, quantized to the shared brief palette, same as the other block art.)
"""
from tex_common import (
    BODY, FRAME, GLOW, new, quantize_to_palette, save_block, save_item, shade,
)
from PIL import ImageDraw


def rect(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1, y1], fill=(c[0], c[1], c[2], 255))


def _disc(d, cx, cy, r, c):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(c[0], c[1], c[2], 255))


def _ring(d, cx, cy, r, c, w=1):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(c[0], c[1], c[2], 255), width=w)


# The rack faces are 32×32 (double the old 16) so the round Concept-A "spool bays"
# read crisply. Slot grid below MUST stay in lockstep with FilamentRackRenderer's
# spool positions (the spools render as 3D items proud of this backing).
RACK_S = 32
RACK_COLS = [5, 13, 19, 27]   # face px → renderer offsets {-0.34375, -0.09375, +0.09375, +0.34375}
RACK_ROWS = [9, 23]           # face px → renderer offsets {+0.21875 (top), -0.21875 (bottom)}
RACK_BAY_R = 6                # big bays that overlap slightly into a cohesive recessed strip


def rack_front():
    """Concept A — Spool Bays. Each slot is a recessed circular bay with a brushed
    bezel rim and a small cyan status LED; the tier-colored spool renders on top."""
    S = RACK_S
    img = new(S)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, S - 1, S - 1, BODY[2])            # light machined body
    for ry in (16, 31):                             # mid divider + base rail
        rect(d, 1, ry - 1, S - 2, ry, FRAME[1])
        rect(d, 1, ry - 1, S - 2, ry - 1, BODY[1])  # lit lip
    r = RACK_BAY_R
    for cy in RACK_ROWS:
        for cx in RACK_COLS:
            _disc(d, cx, cy, r, FRAME[2])           # bay opening
            _disc(d, cx, cy, r - 1, FRAME[3])       # recess depth
            _ring(d, cx, cy, r, BODY[1], w=1)       # brushed bezel rim
            led = min(cx + 4, S - 2)
            rect(d, led, cy + 5, led, cy + 5, GLOW[2])          # cyan status LED
            rect(d, led - 1, cy + 5, led - 1, cy + 5, GLOW[3])
    rect(d, 0, 0, S - 1, 0, BODY[1])                # light bevel — all edges light
    rect(d, 0, 0, 0, S - 1, BODY[1])
    rect(d, S - 1, 0, S - 1, S - 1, BODY[1])
    rect(d, 0, S - 1, S - 1, S - 1, BODY[1])
    return quantize_to_palette(img)


def rack_side():
    S = RACK_S
    img = new(S)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, S - 1, S - 1, BODY[2])            # light body, matches the front
    rect(d, 0, 0, S - 1, 0, BODY[1])                # top highlight
    rect(d, 0, 0, 0, S - 1, BODY[1])                # left highlight
    rect(d, 4, 0, 5, S - 1, BODY[3])                # front upright (subtle, not black)
    rect(d, 26, 0, 27, S - 1, BODY[3])              # back upright
    rect(d, 0, 15, S - 1, 16, BODY[3])              # mid shelf board
    rect(d, S - 1, 0, S - 1, S - 1, BODY[3])        # right edge (light-grey shade)
    rect(d, 0, S - 1, S - 1, S - 1, BODY[3])        # bottom edge
    return quantize_to_palette(img)


def rack_top():
    S = RACK_S
    img = new(S)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, S - 1, S - 1, BODY[2])
    rect(d, 0, 0, S - 1, 0, BODY[1])
    rect(d, 0, 0, 0, S - 1, BODY[1])
    for i in range(3, S - 1, 8):                    # plank seams
        rect(d, i, 2, i, S - 3, BODY[3])
    rect(d, 0, S - 1, S - 1, S - 1, FRAME[1])
    rect(d, S - 1, 0, S - 1, S - 1, FRAME[1])
    return quantize_to_palette(img)


def cable():
    img = new(16)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 15, 15, FRAME[1])         # dark metal jacket
    rect(d, 0, 0, 15, 0, FRAME[0])          # top highlight
    rect(d, 0, 0, 0, 15, FRAME[0])
    rect(d, 15, 0, 15, 15, FRAME[3])
    rect(d, 0, 15, 15, 15, FRAME[3])
    # cyan emissive core running down the middle (the "powered" read)
    rect(d, 6, 0, 9, 15, GLOW[3])
    rect(d, 7, 0, 8, 15, GLOW[2])
    rect(d, 7, 0, 7, 15, GLOW[1])
    return quantize_to_palette(img)


def cable_item():
    """Inventory/GUI icon — a thin diagonal cable with FLAT ends and a cyan core
    stripe. A rotated-rectangle bar (not a line) so both ends are clean flat cuts
    rather than tapered points. Transparent background so it reads as a wire."""
    img = new(16)
    d = ImageDraw.Draw(img)
    a, b = (3, 12), (12, 3)                       # bottom-left -> top-right
    hw_perp = 0.7071                              # 45° perpendicular unit component

    def bar(hw, color):
        o = hw * hw_perp
        d.polygon([(a[0] - o, a[1] - o), (a[0] + o, a[1] + o),
                   (b[0] + o, b[1] + o), (b[0] - o, b[1] - o)],
                  fill=color + (255,))

    bar(2.6, FRAME[3])                            # dark outline / AO
    bar(1.6, FRAME[1])                            # metal jacket
    d.line([a, b], fill=GLOW[2] + (255,), width=1)   # cyan emissive core stripe
    return img


def main():
    written = [
        save_block(rack_front(), "filament_rack_front"),
        save_block(rack_side(), "filament_rack_side"),
        save_block(rack_top(), "filament_rack_top"),
        save_block(cable(), "mc3dcable"),
        save_item(cable_item(), "mc3dcable"),
    ]
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
