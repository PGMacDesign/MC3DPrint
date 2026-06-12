#!/usr/bin/env python3
"""
Generate the MC3DPrint mod logo (src/main/resources/logo.png), 256x256.

A clean stylized desktop 3D-printer mark on the dark console palette: dark metal
gantry frame, a light-grey extruder carriage on a rail, a glowing cyan hotend
laying a small cyan layer line on a grey bed, and a tier-accent filament spool
in the corner. Designed to read at the small size Forge shows it (mods list).

Drawn at the native pixel grid then scaled up NEAREST so it stays crisp pixel
art rather than a blurry illustration. Run from repo root:
  python3 tools/gen_logo.py
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/logo.png")

# Palette (from the brief)
BODY = [(0xF4, 0xF6, 0xF8), (0xDC, 0xE1, 0xE6), (0xBC, 0xC4, 0xCC),
        (0x9A, 0xA3, 0xAD), (0x6E, 0x76, 0x7F)]
FRAME = [(0x5A, 0x60, 0x68), (0x3C, 0x41, 0x48), (0x27, 0x2B, 0x30),
         (0x15, 0x18, 0x1C)]
GLOW = [(0xFF, 0xFF, 0xFF), (0xBF, 0xE9, 0xFF), (0x5C, 0xC8, 0xFF),
        (0x1E, 0x7F, 0xCF)]
ACCENT_TEAL = (0x3F, 0xE0, 0xC0)
BG = (0x12, 0x16, 0x1E, 255)      # dark console field
TIER_TEAL = (0x34, 0xC0, 0xC0)    # spool accent

GRID = 64       # native pixel canvas
SCALE = 4       # -> 256


def main():
    img = Image.new("RGBA", (GRID, GRID), BG)
    px = img.load()

    def put(x, y, c, a=255):
        if 0 <= x < GRID and 0 <= y < GRID:
            px[x, y] = (c[0], c[1], c[2], a)

    def rect(x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                put(xx, yy, c)

    def hline(x, y, w, c):
        rect(x, y, w, 1, c)

    def vline(x, y, h, c):
        rect(x, y, 1, h, c)

    # ---- subtle rounded vignette panel so the mark sits on a console plate ----
    rect(4, 4, GRID - 8, GRID - 8, (0x1A, 0x1F, 0x2B))
    hline(4, 4, GRID - 8, (0x2C, 0x33, 0x42))          # bevel light top
    vline(4, 4, GRID - 8, (0x2C, 0x33, 0x42))
    hline(4, GRID - 5, GRID - 8, (0x0A, 0x0D, 0x14))   # bevel dark bottom
    vline(GRID - 5, 4, GRID - 8, (0x0A, 0x0D, 0x14))
    # teal console accent line near the top = identity
    hline(8, 9, GRID - 16, ACCENT_TEAL)

    # ============================ printer mark ============================
    # An open gantry frame (two uprights + top beam), a rail with an extruder
    # carriage, a cyan hotend over a bed laying a glowing layer.
    fx0, fx1 = 14, 50          # frame left/right uprights
    fy_top, fy_bot = 16, 50    # frame top/bottom

    # --- base / bed plinth ---
    rect(fx0 - 2, fy_bot, (fx1 - fx0) + 4, 4, FRAME[2])
    hline(fx0 - 2, fy_bot, (fx1 - fx0) + 4, FRAME[0])
    hline(fx0 - 2, fy_bot + 3, (fx1 - fx0) + 4, FRAME[3])
    # bed slab on the plinth
    rect(fx0 + 2, fy_bot - 3, (fx1 - fx0) - 4, 3, BODY[3])
    hline(fx0 + 2, fy_bot - 3, (fx1 - fx0) - 4, BODY[1])
    hline(fx0 + 2, fy_bot - 1, (fx1 - fx0) - 4, BODY[4])

    # --- frame uprights ---
    for ux in (fx0, fx1 - 2):
        rect(ux, fy_top, 2, fy_bot - fy_top, FRAME[1])
        vline(ux, fy_top, fy_bot - fy_top, FRAME[0])       # lit left
        vline(ux + 1, fy_top, fy_bot - fy_top, FRAME[3])   # shadow right
    # --- top beam ---
    rect(fx0, fy_top, (fx1 - fx0), 3, FRAME[1])
    hline(fx0, fy_top, (fx1 - fx0), FRAME[0])
    hline(fx0, fy_top + 2, (fx1 - fx0), FRAME[3])

    # --- horizontal rail (gantry) about 1/3 down ---
    rail_y = 26
    hline(fx0 + 2, rail_y, (fx1 - fx0) - 4, BODY[3])
    hline(fx0 + 2, rail_y + 1, (fx1 - fx0) - 4, FRAME[3])

    # --- extruder carriage on the rail ---
    cw, ch = 12, 8
    cx0 = (fx0 + fx1) // 2 - cw // 2
    cy0 = rail_y - 2
    rect(cx0, cy0, cw, ch, BODY[2])
    hline(cx0, cy0, cw, BODY[0])
    vline(cx0, cy0, ch, BODY[1])
    hline(cx0, cy0 + ch - 1, cw, BODY[4])
    vline(cx0 + cw - 1, cy0, ch, BODY[3])
    # recessed vent slot on the carriage
    rect(cx0 + 2, cy0 + 2, cw - 4, 2, FRAME[3])
    # nozzle cone
    nz_x = cx0 + cw // 2
    put(nz_x - 1, cy0 + ch, BODY[4]); put(nz_x, cy0 + ch, BODY[3])
    put(nz_x, cy0 + ch + 1, BODY[4])

    # --- glowing cyan hotend + a laid layer line on the bed ---
    hot_y = cy0 + ch + 4
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            d = (dx * dx + dy * dy) ** 0.5
            if d > 4.2:
                continue
            t = d / 4.2
            if t < 0.28:
                c = GLOW[0]
            elif t < 0.55:
                c = GLOW[1]
            elif t < 0.8:
                c = GLOW[2]
            else:
                c = GLOW[3]
            put(nz_x + dx, hot_y + dy, c)
    # a glowing layer line being printed across the bed
    layer_y = fy_bot - 4
    for lx in range(fx0 + 5, fx1 - 5):
        put(lx, layer_y, GLOW[3])
    for lx in range(nz_x - 5, nz_x + 6):
        put(lx, layer_y, GLOW[2])
    put(nz_x, layer_y, GLOW[1])

    # --- tier-accent filament spool top-right + feed line to carriage ---
    sp_x, sp_y = fx1 + 6, fy_top + 2
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 5.2:
                if d < 1.8:
                    c = FRAME[3]                          # hub hole
                elif dx < 0 and dy < 0:
                    c = tuple(min(255, v + 40) for v in TIER_TEAL)  # lit coil
                elif dx > 0 or dy > 0:
                    c = tuple(int(v * 0.7) for v in TIER_TEAL)      # shadow coil
                else:
                    c = TIER_TEAL
                if d > 4.4:
                    c = BODY[3] if (dx < 0 or dy < 0) else BODY[4]  # flange rim
                put(sp_x + dx, sp_y + dy, c)
    # feed line from spool down/left to the carriage
    fx, fy = sp_x - 4, sp_y + 3
    txp, typ = cx0 + cw - 1, cy0
    steps = max(abs(txp - fx), abs(typ - fy))
    for s in range(1, steps):
        x = fx + round((txp - fx) * s / steps)
        y = fy + round((typ - fy) * s / steps)
        put(x, y, tuple(int(v * 0.8) for v in TIER_TEAL) if s < steps - 2 else GLOW[3])

    # scale up NEAREST (crisp pixels)
    big = img.resize((GRID * SCALE, GRID * SCALE), Image.NEAREST)

    # ---- wordmark "MC3DPrint" along the bottom, drawn into the big image ----
    draw = ImageDraw.Draw(big)
    text = "MC3DPrint"
    font = None
    for cand in [
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial.ttf",
    ]:
        if os.path.exists(cand):
            try:
                font = ImageFont.truetype(cand, 34)
                break
            except Exception:
                pass
    if font is None:
        font = ImageFont.load_default()
    bb = draw.textbbox((0, 0), text, font=font)
    tw = bb[2] - bb[0]
    th = bb[3] - bb[1]
    tx = (big.size[0] - tw) // 2 - bb[0]
    ty = big.size[1] - th - 26 - bb[1]
    # drop shadow + cyan-accented wordmark
    draw.text((tx + 2, ty + 2), text, font=font, fill=(0x0A, 0x0D, 0x14, 255))
    draw.text((tx, ty), text, font=font, fill=(0xDC, 0xE1, 0xE6, 255))
    # tint the "3D" cyan for emphasis: redraw just that substring over the top
    pre = "MC"
    pre_w = draw.textbbox((0, 0), pre, font=font)[2]
    draw.text((tx + pre_w, ty), "3D", font=font, fill=GLOW[2])

    big.save(OUT)
    print("wrote", os.path.relpath(OUT, ROOT), big.size)


if __name__ == "__main__":
    main()
