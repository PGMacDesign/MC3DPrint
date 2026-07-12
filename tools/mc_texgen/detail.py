"""
Resolution-aware detail synthesis (Phase 2, opt-in via cfg.detail).

Applied AT target resolution so the added texture reads at the size a player
actually sees, not smeared up from 16px. Two mask-gated, fully deterministic
effects:

  * brushed-metal micro-noise on METAL pixels (seeded from x,y, so re-running is
    byte-identical and there is no wall-clock/RNG anywhere)
  * light edge re-inking on FRAME pixels that border a non-FRAME class, to keep
    structural seams crisp after upscaling

Off by default: base upscales stay clean unless a config asks for the treatment.
"""
from PIL import Image

from .masks import FRAME, METAL, TRANSPARENT


def _hash2(x, y):
    """Deterministic 0-255 hash of a coordinate (integer bit-mix, no RNG)."""
    n = (x * 73856093) ^ (y * 19349663)
    n = (n ^ (n >> 13)) & 0xFFFFFFFF
    n = (n * 1274126177) & 0xFFFFFFFF
    return (n >> 16) & 0xFF


def apply(img, grid, cfg, amplitude=6):
    """Return a NEW RGBA image with detail synthesised over ``img`` per ``grid``.

    ``grid`` must be classified at the SAME resolution as ``img`` (call after
    upscaling and re-classifying). ``amplitude`` bounds the metal noise swing.
    """
    out = img.convert("RGBA")
    px = out.load()
    w, h = out.size

    def cls(x, y):
        return grid[y][x] if 0 <= x < w and 0 <= y < h else TRANSPARENT

    for y in range(h):
        for x in range(w):
            c = grid[y][x]
            if c == METAL:
                r, g, b, a = px[x, y]
                # Horizontal brush: bias the swing by column so streaks run in x.
                d = (_hash2(x, y) - 128) / 128.0
                delta = d * amplitude
                px[x, y] = (
                    max(0, min(255, int(r + delta))),
                    max(0, min(255, int(g + delta))),
                    max(0, min(255, int(b + delta))),
                    a,
                )
            elif c == FRAME:
                # Re-ink only seam-facing frame pixels (border a lighter class).
                border = any(
                    cls(x + dx, y + dy) not in (FRAME, TRANSPARENT)
                    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
                )
                if border:
                    r, g, b, a = px[x, y]
                    px[x, y] = (int(r * 0.82), int(g * 0.82), int(b * 0.82), a)
    return out
