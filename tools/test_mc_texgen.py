#!/usr/bin/env python3
"""
Fast, dependency-free tests for mc-texgen (plain asserts; no pytest).

    python3 tools/test_mc_texgen.py

Covers: determinism, upscale dimensions, the labPBR emissive invariant, a
hand-computed Scale2x case, and the inputs-only guarantee (never writes assets/).
"""
import hashlib
import io
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # tools/ on path
from mc_texgen import config, masks, pbr, upscale  # noqa: E402
from mc_texgen.ingest import records  # noqa: E402

K = (0, 0, 0, 255)      # black
W = (255, 255, 255, 255)  # white


def _png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _img_from(rows):
    h = len(rows)
    w = len(rows[0])
    im = Image.new("RGBA", (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = rows[y][x]
    return im


def test_determinism():
    """Same input bytes -> same output bytes, twice, for the full PBR stack."""
    cfg = config.load("mc3dprint")
    src = Image.new("RGBA", (8, 8))
    px = src.load()
    for y in range(8):
        for x in range(8):
            # deterministic pseudo-texture: glow stripe + grey field
            if x < 2:
                px[x, y] = (60, 210, 255, 255)   # cyan glow band
            elif x < 5:
                px[x, y] = (40, 44, 50, 255)      # dark frame
            else:
                px[x, y] = (190, 198, 208, 255)   # light body
    g1 = masks.classify(src, cfg, "block/sample")
    g2 = masks.classify(src, cfg, "block/sample")
    assert g1 == g2, "mask classification not deterministic"
    n1 = _png_bytes(pbr.normal_map(src, g1, cfg))
    n2 = _png_bytes(pbr.normal_map(src, g2, cfg))
    s1 = _png_bytes(pbr.specular_map(src, g1, cfg))
    s2 = _png_bytes(pbr.specular_map(src, g2, cfg))
    assert n1 == n2, "normal map not byte-deterministic"
    assert s1 == s2, "specular map not byte-deterministic"
    u1 = _png_bytes(upscale.upscale(src, 4))
    u2 = _png_bytes(upscale.upscale(src, 4))
    assert u1 == u2, "upscale not byte-deterministic"
    print("ok determinism")


def test_upscale_dimensions():
    src = Image.new("RGBA", (16, 16), (10, 20, 30, 255))
    assert upscale.upscale(src, 2).size == (32, 32)
    assert upscale.upscale(src, 3).size == (48, 48)
    assert upscale.upscale(src, 4).size == (64, 64)
    assert upscale.upscale(src, 6).size == (96, 96)
    assert upscale.upscale(src, 8).size == (128, 128)
    assert upscale.upscale(src, 5).size == (80, 80)  # nearest fallback
    print("ok upscale dimensions")


def test_scale2x_known():
    """A 2x2 checkerboard through EPX has a hand-computed 4x4 result."""
    src = _img_from([[K, W], [W, K]])
    out = upscale.scale2x(src)
    assert out.size == (4, 4)
    px = out.load()
    got = [[px[x, y] for x in range(4)] for y in range(4)]
    expected = [
        [K, K, W, W],
        [K, W, K, W],
        [W, K, W, K],
        [W, W, K, K],
    ]
    assert got == expected, f"EPX mismatch:\n{got}"
    print("ok scale2x known EPX result")


def test_emissive_invariant():
    """labPBR _s alpha: every NON-glow pixel == 255 (off); glow in 0..254."""
    cfg = config.load("mc3dprint")
    for rec in records(cfg, ("block", "item")):
        img = rec.open()
        for _i, frame in rec.iter_frames(img):
            grid = masks.classify(frame, cfg, rec.relname)
            s = pbr.specular_map(frame, grid, cfg).load()
            w, h = frame.size
            for y in range(h):
                for x in range(w):
                    cls = grid[y][x]
                    a = s[x, y][3]
                    if cls == masks.GLOW:
                        assert 0 <= a <= 254, f"{rec.relname} glow alpha {a} out of 0..254"
                    elif cls == masks.TRANSPARENT:
                        pass  # untouched (fully transparent)
                    else:
                        assert a == 255, f"{rec.relname} non-glow {cls} alpha {a} != 255 (off)"
    print("ok labPBR emissive invariant")


def test_inputs_only():
    """The tool must never mutate the source assets/ tree."""
    cfg = config.load("mc3dprint")
    root = os.path.join(cfg.source_root, cfg.namespace, "textures")
    before = {}
    for dirpath, _dirs, files in os.walk(root):
        for f in files:
            p = os.path.join(dirpath, f)
            with open(p, "rb") as fh:
                before[p] = hashlib.sha256(fh.read()).hexdigest()
    # Run the neighbour-heavy stages over every source texture.
    for rec in records(cfg, cfg.kinds):
        img = rec.open()
        for _i, frame in rec.iter_frames(img):
            grid = masks.classify(frame, cfg, rec.relname)
            pbr.normal_map(frame, grid, cfg)
            pbr.specular_map(frame, grid, cfg)
        upscale.upscale(rec.open() if rec.frames == 1 else next(rec.iter_frames())[1], 2)
    after = {}
    for p in before:
        with open(p, "rb") as fh:
            after[p] = hashlib.sha256(fh.read()).hexdigest()
    assert before == after, "source assets/ were modified"
    print("ok inputs-only (assets untouched)")


def main():
    test_determinism()
    test_upscale_dimensions()
    test_scale2x_known()
    test_emissive_invariant()
    test_inputs_only()
    print("\nALL PASS")


if __name__ == "__main__":
    main()
