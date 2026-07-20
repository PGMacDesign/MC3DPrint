#!/usr/bin/env python3
"""
Textures for the Filament Tier Item Sorter (16x16 top/side/bottom).

Machined light-grey body in the shared brief palette. TOP reads as an item intake:
a recessed funnel mouth with cyan converging guides and corner LEDs. SIDE shows a
routing "belt" window with three tier-coloured chips riding a cyan flow line — the
sort-by-tier read. BOTTOM is a plain bolted service plate. No facing, so every side
is interchangeable.

Run:  python3 tools/gen_sorter_textures.py
(then re-run tools/gen_item_model_defs.py and tools/gen_style_packs.py)
"""
from tex_common import BODY, FRAME, GLOW, TIER, new, quantize_to_palette, save_block, shade
from PIL import ImageDraw

S = 16


def rect(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1, y1], fill=(c[0], c[1], c[2], 255))


def bevel(d):
    rect(d, 0, 0, S - 1, 0, BODY[1])          # top highlight
    rect(d, 0, 0, 0, S - 1, BODY[1])          # left highlight
    rect(d, S - 1, 0, S - 1, S - 1, BODY[3])  # right shade
    rect(d, 0, S - 1, S - 1, S - 1, BODY[3])  # bottom shade


def top():
    img = new(S)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, S - 1, S - 1, BODY[2])
    rect(d, 3, 3, 12, 12, FRAME[2])           # recessed funnel mouth
    rect(d, 4, 4, 11, 11, FRAME[3])           # deeper well
    for gx, gy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        dx = 1 if gx < 8 else -1
        dy = 1 if gy < 8 else -1
        d.line([gx, gy, gx + 3 * dx, gy + 3 * dy], fill=GLOW[3] + (255,))  # converging guides
    rect(d, 7, 7, 8, 8, GLOW[1])              # bright intake centre
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        rect(d, cx, cy, cx, cy, GLOW[2])      # corner LEDs
    bevel(d)
    return quantize_to_palette(img)


def side():
    img = new(S)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, S - 1, S - 1, BODY[2])
    rect(d, 1, 1, S - 2, 2, FRAME[1])         # top rail
    rect(d, 1, S - 3, S - 2, S - 2, FRAME[1]) # bottom rail
    rect(d, 1, 6, S - 2, 10, FRAME[2])        # routing-belt window
    rect(d, 1, 8, S - 2, 8, GLOW[3])          # cyan flow line
    for i, tier in enumerate((2, 4, 6)):      # three tier-coded chips on the belt
        x = 3 + i * 5
        rect(d, x, 7, x + 2, 9, TIER[tier])
    bevel(d)
    return quantize_to_palette(img)


def bottom():
    img = new(S)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, S - 1, S - 1, BODY[3])      # darker service plate
    rect(d, 2, 2, S - 3, S - 3, BODY[2])
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        rect(d, cx, cy, cx, cy, FRAME[2])     # bolt heads
    bevel(d)
    return quantize_to_palette(img)


def main():
    written = [
        save_block(top(), "filament_item_sorter_top"),
        save_block(side(), "filament_item_sorter_side"),
        save_block(bottom(), "filament_item_sorter_bottom"),
    ]
    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
