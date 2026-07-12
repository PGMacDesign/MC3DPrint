"""
Semantic per-pixel classification: the pipeline's secret weapon.

Every downstream stage (height bias, smoothness, reflectance, emission, detail
synthesis) keys off a per-pixel class rather than raw colour, so the intent of
a texel ("this is chassis / this is glow / this is a seam") is decided once,
here, in one explainable place. The heuristic is a pure HSV threshold ladder;
all boundaries live in the config's ``semantics`` block so a new mod retunes
without touching code.

Classes (see WHY each boundary exists in config.DEFAULT_SEMANTICS):
  TRANSPARENT  alpha == 0
  GLOW         emissive accent: high value + saturation (optional hue window)
  TIER         saturated non-glow accent (tier trim, spool wraps)
  FRAME        dark low-saturation structure (seams, rails, panel edges)
  METAL        mid-grey low-saturation chassis
  BODY         everything else (the light machined body / default)
"""
from tex_common import _hsv  # reuse the one HSV helper the drawing tools use (path set in __init__)

TRANSPARENT = "TRANSPARENT"
GLOW = "GLOW"
TIER = "TIER"
FRAME = "FRAME"
METAL = "METAL"
BODY = "BODY"

# Stable debug colours for the mask-overlay contact sheet (lead eyeballs these
# to sanity-check the art-dependent heuristic).
DEBUG_COLORS = {
    TRANSPARENT: (0, 0, 0, 0),
    GLOW: (0, 255, 255, 255),
    TIER: (255, 210, 40, 255),
    FRAME: (40, 40, 60, 255),
    METAL: (150, 160, 175, 255),
    BODY: (235, 240, 245, 255),
}


def _hue_in(hue, window):
    if window is None:
        return True
    lo, hi = window
    return lo <= hue <= hi


def classify_pixel(r, g, b, a, cfg):
    """Classify one RGBA pixel. Pure function of the pixel + config thresholds."""
    if a == 0:
        return TRANSPARENT
    h, s, v = _hsv(r, g, b)
    if (v >= cfg.sem("glow_v_min") and s >= cfg.sem("glow_s_min")
            and _hue_in(h, cfg.sem("glow_hue_range"))):
        return GLOW
    if s >= cfg.sem("tier_s_min") and v >= cfg.sem("tier_v_min"):
        return TIER
    # Low-saturation grey: split by value into dark seam / mid chassis / light body.
    if v < cfg.sem("frame_v_max"):
        return FRAME
    if v < cfg.sem("metal_v_max"):
        return METAL
    return BODY


def classify(img, cfg, relname=None):
    """Return a same-size 2D grid (list of rows) of class enums for an RGBA frame.

    ``relname`` (e.g. "block/mc3dcable") consults the per-texture override map:
    a match forces every opaque pixel to that class (transparency is preserved).
    """
    forced = cfg.sem("overrides").get(relname) if relname else None
    px = img.load()
    w, h = img.size
    grid = []
    for y in range(h):
        row = []
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                row.append(TRANSPARENT)
            elif forced:
                row.append(forced)
            else:
                row.append(classify_pixel(r, g, b, a, cfg))
        grid.append(row)
    return grid


def debug_image(grid):
    """Render a class grid to an RGBA image using DEBUG_COLORS (for previews)."""
    from PIL import Image
    h = len(grid)
    w = len(grid[0]) if h else 0
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    opx = out.load()
    for y in range(h):
        for x in range(w):
            opx[x, y] = DEBUG_COLORS[grid[y][x]]
    return out
