#!/usr/bin/env python3
"""
Generates the "active / powered-on" block textures for the T5-T8 Fabricator
multiblock by compositing a tasteful energy glow onto the existing plain
textures. The active variants are deliberately the SAME block, energized — so
a formed structure reads as "the same casing/controller, now powered on".

Outputs (16x16 RGBA PNGs into assets/mc3dprint/textures/block/):
  printer_casing_active.png   = printer_casing + teal conduit node + lit corners
  tier5_fabricator_active.png = tier5_fabricator + brightened core + hot center/ring
  tier6_fabricator_active.png ...
  tier7_fabricator_active.png ...
  tier8_fabricator_active.png ...

Run from the repo root:  python3 tools/gen_formed_textures.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_DIR = os.path.join(ROOT, "src/main/resources/assets/mc3dprint/textures/block")

# Per-tier accent (matches the existing colored core in each base texture).
TIER_ACCENT = {
    "tier5_fabricator": (90, 200, 230),   # cyan
    "tier6_fabricator": (110, 230, 110),  # green
    "tier7_fabricator": (240, 140, 60),   # orange
    "tier8_fabricator": (190, 80, 255),   # purple
}

# Teal conduit colour for the casing — reads as "energy flowing through the frame".
CASING_GLOW = (90, 210, 230)


def clamp(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def screen(base, glow, strength):
    """Screen-blend `glow` over `base` at `strength` (0..1) — brightens, never dulls."""
    out = []
    for i in range(3):
        g = glow[i] * strength
        out.append(clamp(255 - (255 - base[i]) * (255 - g) / 255))
    return tuple(out)


def add_glow(base, glow, strength):
    """Additive glow, softer than screen for thin highlight lines."""
    return tuple(clamp(base[i] + glow[i] * strength) for i in range(3))


def load(name):
    return Image.open(os.path.join(BLOCK_DIR, name + ".png")).convert("RGBA")


def save(img, name):
    path = os.path.join(BLOCK_DIR, name + ".png")
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def make_casing_active():
    """
    Casing: dark beveled frame. Energized look = a cyan power node in the center
    with conduit lines running to the four edges (like current flowing through
    the frame), plus lit corners. Subtle, not garish.
    """
    img = load("printer_casing")
    px = img.load()
    glow = CASING_GLOW

    # A diamond "node" in the very center: 4x4 bright core with a soft ring.
    node = {
        (7, 7): 0.95, (8, 7): 0.95, (7, 8): 0.95, (8, 8): 0.95,
        (6, 7): 0.45, (9, 7): 0.45, (6, 8): 0.45, (9, 8): 0.45,
        (7, 6): 0.45, (8, 6): 0.45, (7, 9): 0.45, (8, 9): 0.45,
        (6, 6): 0.22, (9, 6): 0.22, (6, 9): 0.22, (9, 9): 0.22,
    }
    for (x, y), s in node.items():
        r, g, b, a = px[x, y]
        px[x, y] = (*screen((r, g, b), glow, s), a)

    # Conduit lines from the node out to each edge (cols/rows 7-8), tapering.
    for d in range(2, 7):  # distance from center
        s = max(0.12, 0.5 - d * 0.06)
        for (x, y) in [
            (7, 7 - d), (8, 7 - d),   # up
            (7, 8 + d), (8, 8 + d),   # down
            (7 - d, 7), (7 - d, 8),   # left
            (8 + d, 7), (8 + d, 8),   # right
        ]:
            if 1 <= x <= 14 and 1 <= y <= 14:
                r, g, b, a = px[x, y]
                px[x, y] = (*add_glow((r, g, b), glow, s), a)

    # Lit corner studs (inside the bevel) so the whole frame reads as powered.
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        r, g, b, a = px[x, y]
        px[x, y] = (*screen((r, g, b), glow, 0.85), a)
    for (x, y) in [(3, 2), (2, 3), (12, 2), (13, 3),
                   (3, 13), (2, 12), (12, 13), (13, 12)]:
        r, g, b, a = px[x, y]
        px[x, y] = (*add_glow((r, g, b), glow, 0.35), a)

    save(img, "printer_casing_active")


def make_fabricator_active(name):
    """
    Fabricator: dark frame with a colored 8x8 core (cols/rows 4-11). Energized =
    brighten the whole core toward white, add a hot bright center, and a lit
    inner ring on the dark border so the core looks like it's radiating.
    """
    img = load(name)
    px = img.load()
    accent = TIER_ACCENT[name]
    hot = lerp(accent, (255, 255, 255), 0.55)  # near-white hot accent

    # 1) Lift the entire colored core toward a brighter, hotter version.
    for y in range(4, 12):
        for x in range(4, 12):
            r, g, b, a = px[x, y]
            base = (r, g, b)
            # radial falloff from center -> hotter in the middle
            dx = (x + 0.5) - 8.0
            dy = (y + 0.5) - 8.0
            dist = (dx * dx + dy * dy) ** 0.5
            t = max(0.0, 1.0 - dist / 5.5)          # 0 at edge, 1 at center
            lit = screen(base, hot, 0.35 + 0.45 * t)  # brighten everywhere, more in center
            px[x, y] = (*lit, a)

    # 2) Hot bright core (2x2 center) — the "reactor" point.
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        r, g, b, a = px[x, y]
        px[x, y] = (*lerp((r, g, b), (255, 255, 255), 0.55), a)

    # 3) Light the inner dark ring (the #1e2024 border at rows/cols 3 & 12)
    #    so the energized core appears to spill light onto the frame.
    ring = list(range(3, 13))
    for i in ring:
        for (x, y) in [(i, 3), (i, 12), (3, i), (12, i)]:
            r, g, b, a = px[x, y]
            px[x, y] = (*add_glow((r, g, b), accent, 0.30), a)

    save(img, name + "_active")


def main():
    make_casing_active()
    for name in TIER_ACCENT:
        make_fabricator_active(name)


if __name__ == "__main__":
    main()
