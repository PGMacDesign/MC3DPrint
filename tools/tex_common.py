#!/usr/bin/env python3
"""
Shared palette + drawing helpers for the MC3DPrint texture generators.

Everything is built to the VISUAL-REVAMP-BRIEF palette ramps so the whole set
quantizes to a small, cohesive set of colours. Single top-left light source;
tight flat ramps; 1px bevels + fake AO; cyan emissive glow used sparingly.

Usage:  from tex_common import *
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/mc3dprint")
BLOCK_DIR = os.path.join(ASSETS, "textures/block")
ITEM_DIR = os.path.join(ASSETS, "textures/item")

# ---------------------------------------------------------------------------
# Palette ramps (from the brief). RGB tuples, lightest -> darkest.
# ---------------------------------------------------------------------------
# Machined light-grey body
BODY = [
    (0xF4, 0xF6, 0xF8),  # 0 lightest
    (0xDC, 0xE1, 0xE6),  # 1
    (0xBC, 0xC4, 0xCC),  # 2 base
    (0x9A, 0xA3, 0xAD),  # 3
    (0x6E, 0x76, 0x7F),  # 4 darkest
]
# Dark metal frame / rails / panel
FRAME = [
    (0x5A, 0x60, 0x68),  # 0 lightest
    (0x3C, 0x41, 0x48),  # 1
    (0x27, 0x2B, 0x30),  # 2 base
    (0x15, 0x18, 0x1C),  # 3 darkest (AO / seams)
]
# Hero magic glow — constant cyan, all tiers (light -> falloff)
GLOW = [
    (0xFF, 0xFF, 0xFF),  # 0 white core (1px)
    (0xBF, 0xE9, 0xFF),  # 1 halo
    (0x5C, 0xC8, 0xFF),  # 2 body
    (0x1E, 0x7F, 0xCF),  # 3 falloff
]
# GUI console accent (also used for terminals/converter readouts)
ACCENT_TEAL = (0x3F, 0xE0, 0xC0)
LABEL = (0xC0, 0xC0, 0xC8)

# Tier accent (chassis trim / indicator dot) — NOT the glow.
TIER = {
    1: (0x8A, 0x94, 0xA0),  # steel
    2: (0x4F, 0x9B, 0xE8),  # blue
    3: (0x34, 0xC0, 0xC0),  # teal
    4: (0x46, 0xC6, 0x6B),  # green
    5: (0xE0, 0xB4, 0x3A),  # gold
    6: (0xE8, 0x7A, 0x3A),  # orange
    7: (0x9B, 0x6B, 0xE8),  # violet
    8: (0xE8, 0x4F, 0xB0),  # magenta
}


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, t):
    """Lighten (t>0 toward white) or darken (t<0 toward black) a colour."""
    if t >= 0:
        return tuple(clamp(c[i] + (255 - c[i]) * t) for i in range(3))
    return tuple(clamp(c[i] * (1 + t)) for i in range(3))


def ramp3(base):
    """Make a 3-shade ramp (highlight, base, shadow) for a tier-accent hue."""
    return (shade(base, 0.32), base, shade(base, -0.34))


def new(size):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def quantize_to_palette(img, extra=()):
    """
    Snap every opaque pixel to the nearest colour in the brief's union palette
    (+ any per-asset accent colours passed in `extra`) to kill noise. Alpha kept.
    """
    pal = []
    for ramp in (BODY, FRAME, GLOW):
        pal.extend(ramp)
    pal.append(ACCENT_TEAL)
    pal.append(LABEL)
    for t in TIER.values():
        pal.extend(ramp3(t))
    pal.extend(extra)
    pal = list(dict.fromkeys(pal))  # de-dupe, keep order

    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            best = None
            bd = 1e9
            for pr, pg, pb in pal:
                d = (r - pr) ** 2 + (g - pg) ** 2 + (b - pb) ** 2
                if d < bd:
                    bd = d
                    best = (pr, pg, pb)
            px[x, y] = (best[0], best[1], best[2], a)
    return img


def save_block(img, name):
    path = os.path.join(BLOCK_DIR, name + ".png")
    img.save(path)
    return path


def save_item(img, name):
    path = os.path.join(ITEM_DIR, name + ".png")
    img.save(path)
    return path


def contact_sheet(paths, scale=8, cols=8, bg=(28, 32, 40, 255), labelpad=0):
    """Build a NEAREST-scaled contact sheet for self-review."""
    imgs = [Image.open(p).convert("RGBA") for p in paths]
    cell = max(im.size[0] for im in imgs) * scale
    rows = (len(imgs) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), bg)
    for i, im in enumerate(imgs):
        s = im.resize((im.size[0] * scale, im.size[1] * scale), Image.NEAREST)
        r, c = divmod(i, cols)
        # center within cell (so 16px items don't crowd 32px blocks)
        ox = c * cell + (cell - s.size[0]) // 2
        oy = r * cell + (cell - s.size[1]) // 2
        sheet.paste(s, (ox, oy), s)
    return sheet
