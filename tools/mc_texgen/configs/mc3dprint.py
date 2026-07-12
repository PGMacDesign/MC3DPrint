"""
mc-texgen config for MC3DPrint.

The thresholds track the VISUAL-REVAMP-BRIEF palette that tex_common draws to:
a light machined BODY, dark FRAME seams, mid-grey METAL chassis, saturated TIER
accents, and a loud cyan GLOW. To port mc-texgen to another mod, copy this file,
repoint source_root/namespace, and retune the semantics block against that mod's
palette (the mask-overlay preview is the fastest way to dial it in).
"""
import os

_ROOT = os.path.dirname(  # repo root: tools/mc_texgen/configs -> ../../..
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

CONFIG = {
    "name": "mc3dprint",
    "source_root": os.path.join(_ROOT, "src/main/resources/assets"),
    "namespace": "mc3dprint",
    "kinds": ("block", "item", "gui"),
    "pbr_kinds": ("block", "item"),   # GUIs are 2D UI, no shader PBR
    "out_dir": os.path.join(_ROOT, "build/mc-texgen"),
    "scales": (2, 4),
    "pbr": True,
    "detail": False,
    "semantics": {
        # Cyan glow: bright + saturated, hue-gated to cyan-blue so gold/orange
        # tier dots (also bright + saturated) do not read as emissive.
        "glow_v_min": 0.78,
        "glow_s_min": 0.40,
        "glow_hue_range": (0.42, 0.72),
        "tier_s_min": 0.34,
        "tier_v_min": 0.34,
        "frame_v_max": 0.30,
        "metal_v_max": 0.72,
        # Per-texture escape hatch, keyed "kind/name" (e.g. "block/mc3dcable"):
        # force every opaque pixel of that texture to one class. Empty here; the
        # threshold ladder classifies the whole MC3DPrint set acceptably.
        "overrides": {},
    },
}
