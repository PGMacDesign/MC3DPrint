"""
Dependency-free config loader.

No YAML: a config is either a plain Python dict, a ``.mctg.py`` file that
defines a top-level ``CONFIG`` dict, or a named module under ``configs/``. That
keeps the pipeline pure-stdlib and lets a config carry real Python (path joins,
computed thresholds) when it wants to.

The mask/pbr tunables live in ``semantics`` and ``pbr_params`` so the art-facing
heuristics can be retuned per mod without touching code (the whole point of the
package being reusable).
"""
import importlib
import os
from dataclasses import dataclass, field, replace

# Resource pack_format range covering every shipped target: 1.20.1 (15) through
# 26.2 (88). Kept in lockstep with tools/gen_style_packs.py + build-all.sh; bump
# FORMAT_MAX when a newer version node lands so emitted packs advertise it.
FORMAT_MIN = 15
FORMAT_MAX = 88

# Defaults are the MC3DPrint palette's classification boundaries. They are the
# knobs a lead most wants to eyeball, so they carry WHY comments (see masks.py).
DEFAULT_SEMANTICS = {
    # GLOW = the loud emissive accent (cyan on MC3DPrint). Gated on value AND
    # saturation so it does not swallow the light-grey body; the optional hue
    # window keeps a bright gold tier dot from reading as glow.
    "glow_v_min": 0.78,
    "glow_s_min": 0.40,
    "glow_hue_range": (0.42, 0.72),  # cyan-blue; set None to accept any hue
    # TIER = saturated non-glow accents (the tier trim dots / spool wraps).
    "tier_s_min": 0.34,
    "tier_v_min": 0.34,
    # Grey pixels split by value: dark seams -> FRAME, mid chassis -> METAL,
    # light body -> BODY (the catch-all default).
    "frame_v_max": 0.30,
    "metal_v_max": 0.72,
    # Per-texture escape hatch: {relname: "METAL"} forces every opaque pixel of
    # that texture to one class (relname is kind/name, e.g. "block/mc3dcable").
    "overrides": {},
}

# labPBR 1.3 encoding knobs. Values are 0-255 unless noted. See pbr.py for the
# exact channel layout and the 255=emissive-off gotcha.
DEFAULT_PBR_PARAMS = {
    # _s red = perceptual smoothness, per mask class.
    "smoothness": {"METAL": 205, "TIER": 150, "BODY": 96, "FRAME": 42, "GLOW": 30},
    # _s green = F0/reflectance. 0-229 linear dielectric; 230-254 hardcoded
    # metal IDs (230=iron). METAL uses the metal ID; everything else a low
    # dielectric F0 (~0.04 -> ~10/255).
    "metal_f0_id": 230,
    "dielectric_f0": 10,
    # _s blue = 0-64 porosity (non-metal), 65-255 subsurface. Kept flat/simple.
    "porosity": {"FRAME": 24, "BODY": 8, "TIER": 4, "GLOW": 0, "METAL": 0},
    # _s alpha = emission. Non-glow MUST be 255 (=off). Glow scales into 0-254.
    "emissive_off": 255,
    "emissive_max": 254,
    # Height map: luminance*gain biased per class, then clamped. FRAME sinks
    # (recessed seams), METAL/BODY/GLOW ride high.
    "height_gain": 0.65,
    "height_bias": {"METAL": 70, "BODY": 60, "TIER": 55, "GLOW": 75, "FRAME": 25},
    # Normal map strength: how hard the Sobel gradient tilts the surface.
    "normal_strength": 2.2,
}


@dataclass
class Config:
    name: str
    source_root: str            # absolute path to the assets/<ns> parent (…/assets)
    namespace: str
    kinds: tuple = ("block", "item", "gui")
    pbr_kinds: tuple = ("block", "item")   # GUIs are 2D UI; shaders never PBR them
    out_dir: str = "build/mc-texgen"
    scales: tuple = (2, 4)
    pbr: bool = True
    detail: bool = False
    semantics: dict = field(default_factory=lambda: dict(DEFAULT_SEMANTICS))
    pbr_params: dict = field(default_factory=lambda: dict(DEFAULT_PBR_PARAMS))

    def sem(self, key):
        return self.semantics.get(key, DEFAULT_SEMANTICS.get(key))

    def pbrp(self, key):
        return self.pbr_params.get(key, DEFAULT_PBR_PARAMS.get(key))


def _merge(base, over):
    """Shallow-merge a top-level dict, deep-merging its dict-valued children so a
    config can override one threshold without restating the whole block."""
    out = dict(base)
    for k, v in (over or {}).items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            merged = dict(out[k])
            merged.update(v)
            out[k] = merged
        else:
            out[k] = v
    return out


def from_dict(d):
    sem = _merge(DEFAULT_SEMANTICS, d.get("semantics"))
    pbrp = _merge(DEFAULT_PBR_PARAMS, d.get("pbr_params"))
    return Config(
        name=d["name"],
        source_root=d["source_root"],
        namespace=d["namespace"],
        kinds=tuple(d.get("kinds", ("block", "item", "gui"))),
        pbr_kinds=tuple(d.get("pbr_kinds", ("block", "item"))),
        out_dir=d.get("out_dir", "build/mc-texgen"),
        scales=tuple(d.get("scales", (2, 4))),
        pbr=d.get("pbr", True),
        detail=d.get("detail", False),
        semantics=sem,
        pbr_params=pbrp,
    )


def load(source):
    """Resolve a config from a dict, a ``.mctg.py`` path, or a configs/ name."""
    if isinstance(source, Config):
        return source
    if isinstance(source, dict):
        return from_dict(source)
    if isinstance(source, str) and source.endswith(".py") and os.path.exists(source):
        ns = {}
        with open(source) as fh:
            exec(compile(fh.read(), source, "exec"), ns)  # noqa: S102 (trusted local config)
        if "CONFIG" not in ns:
            raise ValueError(f"{source} does not define a CONFIG dict")
        return from_dict(ns["CONFIG"])
    # Bare name: import tools.mc_texgen.configs.<name> and read its CONFIG.
    mod = importlib.import_module(f"{__package__}.configs.{source}")
    return from_dict(mod.CONFIG)


def override(cfg, **kwargs):
    """Return a copy of cfg with fields replaced (CLI flags win over the file)."""
    return replace(cfg, **kwargs)
