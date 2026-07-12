"""
Pure-Python pixel-art scalers (RGBA, deterministic, edge-clamped).

EPX/Scale2x and Scale3x are the classic license-free pixel-art expansion
algorithms (AdvanceMAME). They interpolate only across matching orthogonal
neighbours, so hard edges and alpha cutouts stay crisp instead of blurring the
way a bilinear/Lanczos resize would. Out-of-bounds samples clamp to the edge
pixel so silhouette borders expand cleanly.

Colours (RGBA tuples) are compared by exact equality, which is correct for the
small quantized palettes these mod textures use.
"""
from PIL import Image


def _grid(img):
    px = img.load()
    w, h = img.size
    return px, w, h


def _at(px, w, h, x, y):
    """Edge-clamped pixel fetch (treats out-of-bounds as the nearest edge)."""
    if x < 0:
        x = 0
    elif x >= w:
        x = w - 1
    if y < 0:
        y = 0
    elif y >= h:
        y = h - 1
    return px[x, y]


def nearest(img, factor):
    """Integer NEAREST upscale (baseline; also the fallback for odd factors)."""
    return img.resize((img.size[0] * factor, img.size[1] * factor), Image.NEAREST)


def scale2x(img):
    """EPX / Scale2x: doubles the image. Canonical rule set with the B!=H & D!=F
    guard; when the plus-neighbours match across, the pixel just quadruples."""
    px, w, h = _grid(img)
    out = Image.new("RGBA", (w * 2, h * 2), (0, 0, 0, 0))
    o = out.load()
    for y in range(h):
        for x in range(w):
            e = px[x, y]
            b = _at(px, w, h, x, y - 1)   # up
            d = _at(px, w, h, x - 1, y)   # left
            f = _at(px, w, h, x + 1, y)   # right
            hh = _at(px, w, h, x, y + 1)  # down
            if b != hh and d != f:
                e0 = d if d == b else e
                e1 = f if b == f else e
                e2 = d if d == hh else e
                e3 = f if hh == f else e
            else:
                e0 = e1 = e2 = e3 = e
            o[2 * x, 2 * y] = e0
            o[2 * x + 1, 2 * y] = e1
            o[2 * x, 2 * y + 1] = e2
            o[2 * x + 1, 2 * y + 1] = e3
    return out


def scale3x(img):
    """Scale3x: triples the image (canonical AdvanceMAME rule set)."""
    px, w, h = _grid(img)
    out = Image.new("RGBA", (w * 3, h * 3), (0, 0, 0, 0))
    o = out.load()
    for y in range(h):
        for x in range(w):
            e = px[x, y]
            a = _at(px, w, h, x - 1, y - 1)
            b = _at(px, w, h, x, y - 1)
            c = _at(px, w, h, x + 1, y - 1)
            d = _at(px, w, h, x - 1, y)
            f = _at(px, w, h, x + 1, y)
            g = _at(px, w, h, x - 1, y + 1)
            hh = _at(px, w, h, x, y + 1)
            i = _at(px, w, h, x + 1, y + 1)
            if b != hh and d != f:
                e0 = d if d == b else e
                e1 = b if (d == b and e != c) or (b == f and e != a) else e
                e2 = f if b == f else e
                e3 = d if (d == b and e != g) or (d == hh and e != a) else e
                e4 = e
                e5 = f if (b == f and e != i) or (hh == f and e != c) else e
                e6 = d if d == hh else e
                e7 = hh if (d == hh and e != i) or (hh == f and e != g) else e
                e8 = f if hh == f else e
            else:
                e0 = e1 = e2 = e3 = e4 = e5 = e6 = e7 = e8 = e
            block = (e0, e1, e2, e3, e4, e5, e6, e7, e8)
            for dy in range(3):
                for dx in range(3):
                    o[3 * x + dx, 3 * y + dy] = block[dy * 3 + dx]
    return out


def upscale(img, factor):
    """Dispatch to the composition of EPX scalers that hits ``factor`` exactly.

    2 -> scale2x, 4 -> scale2x^2, 8 -> scale2x^3 (power-of-two edges stay
    crispest via EPX). 3 -> scale3x, 6 -> scale3x then scale2x. Anything else
    has no clean EPX decomposition, so it falls back to NEAREST (still sharp,
    just no edge interpolation).
    """
    if factor == 1:
        return img.convert("RGBA")
    if factor == 2:
        return scale2x(img)
    if factor == 3:
        return scale3x(img)
    if factor == 4:
        return scale2x(scale2x(img))
    if factor == 6:
        return scale2x(scale3x(img))
    if factor == 8:
        return scale2x(scale2x(scale2x(img)))
    return nearest(img, factor)
