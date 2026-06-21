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
    rect(d, 0, 0, 15, 15, BODY[2])          # body
    rect(d, 0, 0, 15, 0, BODY[1])           # top highlight
    rect(d, 0, 0, 0, 15, BODY[1])           # left highlight
    rect(d, 15, 0, 15, 15, FRAME[1])        # right shade
    rect(d, 0, 15, 15, 15, FRAME[1])        # bottom shade
    # 2 rows x 4 columns of recessed cubbies
    cols = [1, 5, 9, 13]
    rows = [2, 9]
    for ry in rows:
        for cx in cols:
            rect(d, cx, ry, cx + 2, ry + 4, FRAME[2])      # recess
            rect(d, cx, ry, cx + 2, ry, FRAME[3])          # inner top shadow
            rect(d, cx, ry, cx, ry + 4, FRAME[3])          # inner left shadow
    # mid shelf divider
    rect(d, 1, 7, 14, 7, FRAME[1])
    return quantize_to_palette(img)


def rack_side():
    img = new(16)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 15, 15, BODY[3])          # darker plank body
    rect(d, 0, 0, 15, 0, BODY[2])
    rect(d, 2, 0, 2, 15, FRAME[2])          # front upright
    rect(d, 13, 0, 13, 15, FRAME[2])        # back upright
    rect(d, 0, 7, 15, 8, FRAME[1])          # mid shelf board
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
    """Inventory/GUI icon — a thin diagonal cable strand (AE2 fluix-style), NOT a
    full block face, so it reads as a wire in the menu. Transparent background."""
    img = new(16)
    d = ImageDraw.Draw(img)
    a, b = (3, 12), (12, 3)                       # bottom-left -> top-right diagonal
    d.line([a, b], fill=FRAME[3] + (255,), width=4)   # dark outline / AO
    d.line([a, b], fill=FRAME[1] + (255,), width=2)   # metal jacket
    d.line([a, b], fill=GLOW[2] + (255,), width=1)    # cyan emissive core
    for (cx, cy) in (a, b):                       # connector plugs at each end
        rect(d, cx - 1, cy - 1, cx + 1, cy + 1, FRAME[2])
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
