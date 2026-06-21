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


def rack_front():
    img = new(16)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 15, 15, BODY[2])          # light machined body
    # Clean shelf panel — two horizontal ledges the spools sit on. No filled slots:
    # the spools are circular coils and rendered proud on top, so the old vertical
    # slits (HDD-bay look) are gone. Just a light rack with a lit lip + thin shadow.
    for sy in (7, 14):
        rect(d, 1, sy, 14, sy, BODY[1])         # lit shelf lip
        rect(d, 1, sy + 1, 14, sy + 1, BODY[3]) # thin under-shadow
    # light bevel on ALL four edges — the right edge matches the left (no dark side)
    rect(d, 0, 0, 15, 0, BODY[1])           # top
    rect(d, 0, 0, 0, 15, BODY[1])           # left
    rect(d, 15, 0, 15, 15, BODY[1])         # right
    rect(d, 0, 15, 15, 15, BODY[1])         # bottom
    return quantize_to_palette(img)


def rack_side():
    img = new(16)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 15, 15, BODY[2])          # light body, matches the front
    rect(d, 0, 0, 15, 0, BODY[1])           # top highlight
    rect(d, 0, 0, 0, 15, BODY[1])           # left highlight
    rect(d, 2, 0, 2, 15, BODY[3])           # front upright (subtle, not black)
    rect(d, 13, 0, 13, 15, BODY[3])         # back upright
    rect(d, 0, 7, 15, 8, BODY[3])           # mid shelf board
    rect(d, 15, 0, 15, 15, BODY[3])         # right edge (light-grey shade)
    rect(d, 0, 15, 15, 15, BODY[3])         # bottom edge
    return quantize_to_palette(img)


def rack_top():
    img = new(16)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 15, 15, BODY[2])
    rect(d, 0, 0, 15, 0, BODY[1])
    rect(d, 0, 0, 0, 15, BODY[1])
    rect(d, 0, 0, 15, 15, None) if False else None
    for i in range(1, 15, 4):
        rect(d, i, 1, i, 14, BODY[3])       # plank seams
    rect(d, 0, 15, 15, 15, FRAME[1])
    rect(d, 15, 0, 15, 15, FRAME[1])
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
