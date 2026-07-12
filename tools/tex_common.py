#!/usr/bin/env python3
"""
Shared palette + drawing helpers for the MC3DPrint texture generators.

Everything is built to the VISUAL-REVAMP-BRIEF palette ramps so the whole set
quantizes to a small, cohesive set of colours. Single top-left light source;
tight flat ramps; 1px bevels + fake AO; cyan emissive glow used sparingly.

Usage:  from tex_common import *
"""
import json
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


# ---------------------------------------------------------------------------
# Animated textures. Minecraft animates a square sheet of N stacked frames
# (a vertical strip, each frame `size`×`size`) driven by a sibling `.png.mcmeta`.
# We build the strip + the meta so the asset is a drop-in for cube_all /
# item/generated — no model change needed.
# ---------------------------------------------------------------------------
def _frame_strip(frames):
    """Stack same-size square frames top-to-bottom into one tall sheet."""
    w = frames[0].size[0]
    strip = Image.new("RGBA", (w, w * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * w), f)
    return strip


def _write_mcmeta(png_path, frametime, interpolate):
    meta = {"animation": {"frametime": frametime, "interpolate": interpolate}}
    with open(png_path + ".mcmeta", "w") as fh:
        json.dump(meta, fh, indent=2)


def save_animated_block(frames, name, frametime=14, interpolate=True):
    path = os.path.join(BLOCK_DIR, name + ".png")
    _frame_strip(frames).save(path)
    _write_mcmeta(path, frametime, interpolate)
    return path


def save_animated_item(frames, name, frametime=14, interpolate=True):
    path = os.path.join(ITEM_DIR, name + ".png")
    _frame_strip(frames).save(path)
    _write_mcmeta(path, frametime, interpolate)
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


# ---------------------------------------------------------------------------
# Style profiles (alternate texture styles — docs/texture-packs.md).
#
# A StyleProfile is a deterministic per-image TRANSFORM over the committed
# default textures, not a re-run of the drawing generators: the default set
# stays byte-identical by construction, and every texture (hand-drawn or not)
# gets the treatment uniformly. gen_style_packs.py orchestrates.
#
# Shared invariants (see the doc): silhouettes and the T1-T8 tier hue map
# survive; GUI geometry is untouched (pure recolour); code-drawn GUI text is
# light, so outputs stay dark-to-mid wherever text renders; animation frames
# keep their count/timing (transforms run per-frame).
# ---------------------------------------------------------------------------
import colorsys

RESOURCEPACKS = os.path.join(ROOT, "src/main/resources/resourcepacks")

# Blueprint Mode palette: cyanotype field + white technical line-work.
BP_WELL = (0x12, 0x2B, 0x4D)        # inset/slot wells (GUIs)
BP_FIELD = (0x1A, 0x3E, 0x6E)       # dark field
BP_PANEL = (0x2B, 0x5A, 0x9E)       # lighter panel tone
BP_BRIGHT = (0x3A, 0x6F, 0xB8)      # brightest field level
BP_LINE = (0xE8, 0xF1, 0xFF)        # white line-work


def _hsv(r, g, b):
    return colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)


def _rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return (clamp(r * 255), clamp(g * 255), clamp(b * 255))


def _color_dist(p, q):
    return ((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2) ** 0.5


class StyleProfile:
    """name/id, display title, pack description + the per-image transform."""

    def __init__(self, name, title, description, transform):
        self.name = name
        self.title = title
        self.description = description
        self._transform = transform

    def transform(self, img, kind):
        """kind: 'block' | 'item' | 'gui'. Returns a NEW RGBA image."""
        return self._transform(img.convert("RGBA"), kind)

    def transform_animated(self, strip, kind):
        """Per-frame transform of a vertical frame strip (frames stay square)."""
        w, h = strip.size
        out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for y0 in range(0, h, w):
            frame = strip.crop((0, y0, w, y0 + w))
            out.paste(self.transform(frame, kind), (0, y0))
        return out


def _darkmode_transform(img, kind):
    """Matte near-black chassis; glow + tier accents stay saturated."""
    out = img.copy()
    px = out.load()
    w, h = out.size
    # GUI keeps a slightly higher value floor so panels stay usable.
    floor, gain = (0.07, 0.32) if kind == "gui" else (0.055, 0.30)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, s, v = _hsv(r, g, b)
            if s > 0.28 and v > 0.25:
                # coloured signal (glow, tier trim, resin bodies): keep it loud
                nr, ng, nb = _rgb(hh, min(1.0, s * 1.1), v * 0.85)
            else:
                # grey chassis: compress into the dark band
                nr, ng, nb = _rgb(hh, s * 0.6, floor + gain * v)
            px[x, y] = (nr, ng, nb, a)
    return out


def _blueprint_transform(img, kind):
    """The machine as its own schematic: blue field + white line-work."""
    src = img
    spx = src.load()
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    opx = out.load()

    # GUIs are dark console panels (values 0.06-0.31): they need a far lower
    # seam threshold and value bands resolved at the dark end, or the whole
    # screen collapses into one flat field.
    gui = kind == "gui"
    seam = 18 if gui else 90

    def field_for(v):
        if gui:
            if v < 0.12:
                return BP_WELL
            if v < 0.20:
                return BP_FIELD
            return BP_PANEL if v < 0.28 else BP_BRIGHT
        return BP_FIELD if v < 0.55 else (BP_PANEL if v < 0.85 else BP_BRIGHT)

    def sample(x, y):
        if 0 <= x < w and 0 <= y < h:
            return spx[x, y]
        return None

    for y in range(h):
        for x in range(w):
            r, g, b, a = spx[x, y]
            if a == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            edge = False
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                n = sample(nx, ny)
                if n is None or n[3] == 0:
                    edge = True          # silhouette boundary
                    break
                # Interior seam: ink only the BRIGHTER side of a strong
                # contrast step (1px lines; both-sides inking floods these
                # dense 32px textures white).
                nlum = 0.299 * n[0] + 0.587 * n[1] + 0.114 * n[2]
                if _color_dist((r, g, b), n[:3]) > seam and lum >= nlum:
                    edge = True
                    break
            hh, s, v = _hsv(r, g, b)
            colored = s > 0.45 and v > 0.35
            if edge:
                if colored:
                    # tier/glow accents render as coloured line-work,
                    # saturation bumped to read against the blue field
                    c = _rgb(hh, min(1.0, s * 1.15), 0.95)
                else:
                    c = BP_LINE
            else:
                if colored:
                    # soft hue tint inside accent regions keeps big features
                    # (spool wraps, resin bodies) tier-readable without
                    # breaking the flat schematic look
                    hue = _rgb(hh, 0.75, 0.75)
                    c = tuple(clamp(BP_PANEL[i] * 0.65 + hue[i] * 0.35) for i in range(3))
                else:
                    c = field_for(v)
                    # graph-paper grid on block faces only (never GUIs/items)
                    if kind == "block" and (x % 4 == 0 or y % 4 == 0):
                        c = tuple(clamp(c[i] * 0.90 + BP_LINE[i] * 0.10) for i in range(3))
            opx[x, y] = (c[0], c[1], c[2], a)
    return out


STYLES = [
    StyleProfile(
        "blueprint_mode", "MC3DPrint: Blueprint Mode",
        "Every machine as its own schematic: blueprint blues + white line-work.",
        _blueprint_transform),
    StyleProfile(
        "dark_mode", "MC3DPrint: Dark Mode",
        "Matte near-black machines; the cyan glow and tier accents stay loud.",
        _darkmode_transform),
]
