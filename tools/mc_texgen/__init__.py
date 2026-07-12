"""
mc-texgen: a generic, config-driven Minecraft texture pipeline.

Incubated in MC3DPrint but written to be lifted into any mod repo by dropping in
a new config (see configs/). It takes a mod's default textures (+ optional
semantic hints) and produces (1) pixel-art hi-res upscales and (2) labPBR 1.3
companion maps (_n / _s) so Iris/Oculus shaders render bump, reflection and
emissive glow.

Everything is pure Python + PIL and deterministic: same input bytes always
yield the same output bytes (no wall-clock, no unseeded RNG). That keeps the
generated packs diffable and reviewable.

Stage architecture (each stage is a small, independently testable module):
  ingest  -> masks -> {upscale, pbr, detail} -> assemble
"""

import os as _os
import sys as _sys

# Put tools/ on sys.path so submodules can `from tex_common import ...` (we reuse
# the drawing tools' HSV + contact-sheet helpers rather than re-deriving them).
_TOOLS_DIR = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
if _TOOLS_DIR not in _sys.path:
    _sys.path.insert(0, _TOOLS_DIR)

__version__ = "0.1.0"
