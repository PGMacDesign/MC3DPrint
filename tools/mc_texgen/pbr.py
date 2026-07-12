"""
labPBR 1.3 companion-map generation (_n normal, _s specular).

This is where the pipeline's value concentrates, so the channel layout is spelled
out exactly. Getting a single channel wrong (especially the emissive alpha) makes
a shader render nonsense, so every field is documented against the labPBR 1.3
standard used by Iris/Oculus.

_n (normal) RGBA:
  R = tangent-space normal X, encoded (x*0.5+0.5)*255. Z is reconstructed by the
      shader from X,Y, so only X,Y are stored.
  G = tangent-space normal Y, same encoding.
  B = ambient occlusion, 255 = no occlusion (cheap AO from local height average).
  A = height for parallax/POM, 255 = surface (highest), 0 = deepest.

_s (specular) RGBA:
  R = perceptual smoothness (0 rough .. 255 mirror), per mask class.
  G = F0 / reflectance. 0-229 = linear dielectric reflectance (value/255);
      230-254 = hardcoded metal IDs (230 = iron). METAL pixels use the metal ID;
      dielectrics use a low F0 (~0.04).
  B = 0-64 porosity (non-metal), 65-255 subsurface scattering. Kept simple/flat.
  A = EMISSION. THE classic gotcha: 255 = OFF (no emission) for backwards
      compatibility; only 0-254 is an actual emission strength. So every NON-glow
      pixel MUST be 255, never 0 (0 would be "emissive, strength ~0" and disables
      the compat path). Glow pixels scale into 0-254.
"""
from PIL import Image

from . import masks
from .masks import BODY, FRAME, GLOW, METAL, TIER, TRANSPARENT


def _clamp8(v):
    return max(0, min(255, round(v)))


def _lum(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def height_map(img, grid, cfg):
    """Grayscale height (one byte per pixel) from luminance biased by mask class.

    Returns a flat list-of-lists of 0-255 heights (transparent pixels -> 0). The
    class bias is what makes a flat-lit pixel-art texture read as geometry:
    FRAME seams sink, METAL/BODY/GLOW ride high.
    """
    px = img.load()
    w, h = img.size
    gain = cfg.pbrp("height_gain")
    bias = cfg.pbrp("height_bias")
    out = [[0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            cls = grid[y][x]
            if cls == TRANSPARENT:
                continue
            r, g, b, _a = px[x, y]
            out[y][x] = _clamp8(_lum(r, g, b) * gain + bias.get(cls, 40))
    return out


def _sobel(height, x, y, w, h):
    """3x3 Sobel gradient of the height field at (x,y), edge-clamped."""
    def hv(xx, yy):
        xx = 0 if xx < 0 else w - 1 if xx >= w else xx
        yy = 0 if yy < 0 else h - 1 if yy >= h else yy
        return height[yy][xx]
    tl, t, tr = hv(x - 1, y - 1), hv(x, y - 1), hv(x + 1, y - 1)
    left, r = hv(x - 1, y), hv(x + 1, y)
    bl, b, br = hv(x - 1, y + 1), hv(x, y + 1), hv(x + 1, y + 1)
    gx = (tr + 2 * r + br) - (tl + 2 * left + bl)
    gy = (bl + 2 * b + br) - (tl + 2 * t + tr)
    return gx, gy


def normal_map(img, grid, cfg):
    """Build the _n RGBA image from the height field of ``img`` under ``grid``."""
    height = height_map(img, grid, cfg)
    w, h = img.size
    strength = cfg.pbrp("normal_strength")
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    o = out.load()
    for y in range(h):
        for x in range(w):
            if grid[y][x] == TRANSPARENT:
                continue
            gx, gy = _sobel(height, x, y, w, h)
            # Gradient is in 0-255 height units across ~4px; normalise to a unit
            # vector tilted away from the uphill direction (labPBR tangent space).
            nx = -(gx / 255.0) * strength
            ny = -(gy / 255.0) * strength
            nz = 1.0
            inv = 1.0 / ((nx * nx + ny * ny + nz * nz) ** 0.5)
            nx *= inv
            ny *= inv
            # Cheap AO: darken where the pixel sits below its neighbours' average.
            hc = height[y][x]
            neigh = (
                height[max(0, y - 1)][x] + height[min(h - 1, y + 1)][x]
                + height[y][max(0, x - 1)] + height[y][min(w - 1, x + 1)]
            ) / 4.0
            ao = 255 if hc >= neigh else _clamp8(255 - (neigh - hc) * 2.0)
            o[x, y] = (
                _clamp8(nx * 0.5 * 255 + 127.5),
                _clamp8(ny * 0.5 * 255 + 127.5),
                ao,
                _clamp8(hc),
            )
    return out


def specular_map(img, grid, cfg):
    """Build the _s RGBA image (smoothness / F0 / porosity / emission)."""
    px = img.load()
    w, h = img.size
    smooth = cfg.pbrp("smoothness")
    poros = cfg.pbrp("porosity")
    metal_id = cfg.pbrp("metal_f0_id")
    dielec = cfg.pbrp("dielectric_f0")
    off = cfg.pbrp("emissive_off")
    emax = cfg.pbrp("emissive_max")
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    o = out.load()
    for y in range(h):
        for x in range(w):
            cls = grid[y][x]
            if cls == TRANSPARENT:
                continue
            r, g, b, _a = px[x, y]
            rr = smooth.get(cls, 96)
            gg = metal_id if cls == METAL else dielec
            bb = 0 if cls == METAL else poros.get(cls, 0)
            if cls == GLOW:
                # Emission strength tracks how bright the glow pixel is (its own
                # luminance), scaled into the legal 0-254 band. Non-glow -> off.
                aa = _clamp8((_lum(r, g, b) / 255.0) * emax)
            else:
                aa = off  # 255 = emission OFF (the gotcha); NEVER 0 here
            o[x, y] = (rr, gg, bb, aa)
    return out


def maps_for_record(rec, cfg):
    """Compute (_n, _s) strips for a record, processing animation per-frame.

    Neighbour-sensitive stages (Sobel) must not read across the frame seam, so
    each square frame is masked + mapped independently and the results restacked
    into strips that match the base texture's frame layout.
    """
    img = rec.open()
    if rec.frames == 1:
        grid = masks.classify(img, cfg, rec.relname)
        return normal_map(img, grid, cfg), specular_map(img, grid, cfg)
    fw = rec.frame_size
    n_strip = Image.new("RGBA", (fw, fw * rec.frames), (0, 0, 0, 0))
    s_strip = Image.new("RGBA", (fw, fw * rec.frames), (0, 0, 0, 0))
    for i, frame in rec.iter_frames(img):
        grid = masks.classify(frame, cfg, rec.relname)
        n_strip.paste(normal_map(frame, grid, cfg), (0, i * fw))
        s_strip.paste(specular_map(frame, grid, cfg), (0, i * fw))
    return n_strip, s_strip
