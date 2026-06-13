#!/usr/bin/env python3
"""
Generate the "active / powered-on" block textures by compositing an energy glow
onto the NEW base textures from gen_block_textures.py. The active variants are
the SAME block, energized — hotend/core lit full-bright cyan + a faint panel
glow (per VISUAL-REVAMP-BRIEF).

Reads (and matches the resolution of) the regenerated bases:
  tier5_fabricator.png .. tier8_fabricator.png  (32x32)  -> *_active.png (32x32)
  printer_casing.png                            (16x16)  -> *_active.png (16x16)

Run AFTER gen_block_textures.py, from the repo root:
  python3 tools/gen_formed_textures.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_DIR = os.path.join(ROOT, "src/main/resources/assets/mc3dprint/textures/block")

# Constant cyan hero glow (matches the brief / the base hotend).
GLOW_CORE = (255, 255, 255)
GLOW_HALO = (191, 233, 255)
GLOW_BODY = (92, 200, 255)
GLOW_FALL = (30, 127, 207)

# Per-tier accent (matches the colored core in each new base).
TIER_ACCENT = {
    "tier5_fabricator": (0xE0, 0xB4, 0x3A),  # gold
    "tier6_fabricator": (0xE8, 0x7A, 0x3A),  # orange
    "tier7_fabricator": (0x9B, 0x6B, 0xE8),  # violet
    "tier8_fabricator": (0xE8, 0x4F, 0xB0),  # magenta
}

CASING_GLOW = (92, 200, 255)  # cyan, the casing's energized conduit colour


def clamp(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def screen(base, glow, strength):
    out = []
    for i in range(3):
        g = glow[i] * strength
        out.append(clamp(255 - (255 - base[i]) * (255 - g) / 255))
    return tuple(out)


def add_glow(base, glow, strength):
    return tuple(clamp(base[i] + glow[i] * strength) for i in range(3))


def load(name):
    return Image.open(os.path.join(BLOCK_DIR, name + ".png")).convert("RGBA")


def save(img, name):
    path = os.path.join(BLOCK_DIR, name + ".png")
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def detect_hotend(img):
    """
    Find the base texture's hotend = the brightest cyan-ish pixel (high blue,
    moderate green, low-ish red). Returns (x, y). Falls back to image center.
    """
    px = img.load()
    w, h = img.size
    best = None
    best_score = -1
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # cyan-ness: blue & green dominate red, and it's bright. Require the
            # pixel to be BLUE-dominant (b >= g) so the chamber hotend (blue cyan)
            # wins over the teal LCD readout (green-dominant) up top.
            score = (b + g) - 1.4 * r + (b > 180) * 60 + (g > 160) * 30
            if b > 150 and g > 120 and b >= g and score > best_score:
                best_score = score
                best = (x, y)
    return best if best else (w // 2, h // 2)


def light_hotend(img, cx, cy, radius):
    """Lift the hotend region to full-bright cyan with a white core + halo."""
    px = img.load()
    w, h = img.size
    rng = int(radius) + 1
    for dy in range(-rng, rng + 1):
        for dx in range(-rng, rng + 1):
            x, y = cx + dx, cy + dy
            if not (0 <= x < w and 0 <= y < h):
                continue
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = (dx * dx + dy * dy) ** 0.5
            if d > radius + 0.5:
                continue
            t = d / max(radius, 0.001)
            if t < 0.30:
                c = GLOW_CORE
            elif t < 0.58:
                c = GLOW_HALO
            elif t < 0.82:
                c = GLOW_BODY
            else:
                # outer ring: screen-blend so it spills onto the frame
                c = screen((r, g, b), GLOW_BODY, 0.6)
            px[x, y] = (c[0], c[1], c[2], a)


def faint_panel_glow(img, glow, strength=0.16):
    """Add a faint additive glow to the dark interior panel so it 'wakes up'."""
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # only lift darkish frame pixels (the panel), leave bright body alone
            if r < 110 and g < 120 and b < 130:
                px[x, y] = (*add_glow((r, g, b), glow, strength), a)


def make_fabricator_active(name):
    img = load(name)
    accent = TIER_ACCENT[name]
    w, h = img.size
    # 32px base -> bigger hotend radius; scale with resolution.
    radius = 3.4 if w >= 32 else 2.0
    cx, cy = detect_hotend(img)
    # faint panel glow first (so the bright hotend overwrites cleanly on top)
    faint_panel_glow(img, accent, strength=0.14)
    faint_panel_glow(img, GLOW_BODY, strength=0.10)
    light_hotend(img, cx, cy, radius)
    # brighten the tier-accent core ring around the hotend
    px = img.load()
    for dy in range(-int(radius) - 2, int(radius) + 3):
        for dx in range(-int(radius) - 2, int(radius) + 3):
            x, y = cx + dx, cy + dy
            if not (0 <= x < w and 0 <= y < h):
                continue
            d = (dx * dx + dy * dy) ** 0.5
            if radius + 0.5 < d <= radius + 2.0:
                r, g, b, a = px[x, y]
                if a:
                    px[x, y] = (*screen((r, g, b), accent, 0.5), a)
    save(img, name + "_active")


def make_casing_active():
    img = load("printer_casing")
    px = img.load()
    w, h = img.size
    glow = CASING_GLOW
    # center node lit cyan (the casing's energized core)
    node = {
        (7, 7): 0.95, (8, 7): 0.95, (7, 8): 0.95, (8, 8): 0.95,
        (6, 7): 0.45, (9, 7): 0.45, (6, 8): 0.45, (9, 8): 0.45,
        (7, 6): 0.45, (8, 6): 0.45, (7, 9): 0.45, (8, 9): 0.45,
        (6, 6): 0.22, (9, 6): 0.22, (6, 9): 0.22, (9, 9): 0.22,
    }
    for (x, y), s in node.items():
        if 0 <= x < w and 0 <= y < h:
            r, g, b, a = px[x, y]
            px[x, y] = (*screen((r, g, b), glow, s), a)
    # white-hot 2x2 center
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        r, g, b, a = px[x, y]
        px[x, y] = (*lerp((r, g, b), GLOW_CORE, 0.7), a)
    # conduit lines node -> edges, tapering
    for d in range(2, 7):
        s = max(0.12, 0.5 - d * 0.06)
        for (x, y) in [(7, 7 - d), (8, 7 - d), (7, 8 + d), (8, 8 + d),
                       (7 - d, 7), (7 - d, 8), (8 + d, 7), (8 + d, 8)]:
            if 1 <= x <= 14 and 1 <= y <= 14:
                r, g, b, a = px[x, y]
                px[x, y] = (*add_glow((r, g, b), glow, s), a)
    # lit corner studs
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        r, g, b, a = px[x, y]
        px[x, y] = (*screen((r, g, b), glow, 0.85), a)
    save(img, "printer_casing_active")


def main():
    make_casing_active()
    for name in TIER_ACCENT:
        make_fabricator_active(name)


if __name__ == "__main__":
    main()
