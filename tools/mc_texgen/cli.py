"""
mc-texgen command line: orchestrates the stages into shippable packs.

  python3 -m tools.mc_texgen --config mc3dprint --phase pbr
  python3 -m tools.mc_texgen --config mc3dprint --phase upscale --scale 4
  python3 -m tools.mc_texgen --config mc3dprint --phase both --scale 4 --pbr

Phases:
  pbr      Phase 0: labPBR _n/_s companion maps for the EXISTING resolution,
           written as a small overlay pack (default -> resourcepacks/pbr_addon).
  upscale  Phase 1: a pixel-art upscaled pack at --scale (default -> build out_dir).
  both     runs BOTH phases: the pbr overlay pack AND the upscale pack. In every
           phase, --pbr is what gates _n/_s maps inside the UPSCALE pack.
"""
import argparse
import os

from PIL import Image

from . import assemble, config, detail, masks, pbr, upscale
from .ingest import records

# Repo root inferred from this file: tools/mc_texgen/cli.py -> ../../..
_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def _preview_dir():
    d = os.path.join(_ROOT, "docs/texgen-previews")
    os.makedirs(d, exist_ok=True)
    return d


def _upscale_frames(rec, factor):
    """Upscale a record (per-frame for animation strips) and restack."""
    if rec.frames == 1:
        return upscale.upscale(rec.open(), factor)
    fw = rec.frame_size * factor
    out = Image.new("RGBA", (fw, fw * rec.frames), (0, 0, 0, 0))
    for i, frame in rec.iter_frames():
        out.paste(upscale.upscale(frame, factor), (0, i * fw))
    return out


def phase_pbr(cfg, out_root, description, previews=True):
    """Emit an _n/_s-only overlay pack for the base resolution."""
    assemble.write_pack_meta(out_root, description)
    hero = None
    n_maps, s_maps, mask_dbg = [], [], []
    count = 0
    for rec in records(cfg, cfg.pbr_kinds):
        n_img, s_img = pbr.maps_for_record(rec, cfg)
        meta = rec.mcmeta_path if rec.animated else None
        np = assemble.save_texture(out_root, cfg.namespace, rec.kind, rec.name + "_n", n_img, meta)
        sp = assemble.save_texture(out_root, cfg.namespace, rec.kind, rec.name + "_s", s_img, meta)
        count += 1
        # Mask debug tiles exist only to feed the preview contact sheets; skip the
        # classify/save cost entirely (and the temp files in the tracked docs dir)
        # when previews are off.
        if previews and rec.kind == "block":
            n_maps.append(np)
            s_maps.append(sp)
            grid = masks.classify(rec.open().crop((0, 0, rec.frame_size, rec.frame_size)),
                                  cfg, rec.relname)
            dbg = masks.debug_image(grid)
            dpath = os.path.join(_preview_dir(), "_tmp_mask_" + rec.name + ".png")
            dbg.save(dpath)
            mask_dbg.append(dpath)
        if rec.name.startswith("tier4"):
            # pack.png hero is the ALBEDO texture; the normal map's tint and
            # height-alpha make an unreadable icon.
            hero = rec.open().crop((0, 0, rec.frame_size, rec.frame_size))
    assemble.write_pack_icon(out_root, hero)
    if previews:
        _write_previews(n_maps, s_maps, mask_dbg)
    return count


def _write_previews(n_maps, s_maps, mask_dbg):
    """Contact sheets to docs/ so the result is reviewable without the binaries."""
    from tex_common import contact_sheet
    pdir = _preview_dir()
    if mask_dbg:
        contact_sheet(mask_dbg, scale=6, cols=6).save(os.path.join(pdir, "masks_blocks.png"))
    if n_maps:
        contact_sheet(n_maps, scale=6, cols=6).save(os.path.join(pdir, "pbr_normal_blocks.png"))
    if s_maps:
        contact_sheet(s_maps, scale=6, cols=6).save(os.path.join(pdir, "pbr_specular_blocks.png"))
    for p in mask_dbg:  # temp mask tiles were only inputs to the contact sheet
        os.remove(p)


def phase_upscale(cfg, out_root, factor, description, with_pbr=False):
    """Emit an upscaled base-texture pack (optionally with matching _n/_s)."""
    assemble.write_pack_meta(out_root, description)
    hero = None
    count = 0
    for rec in records(cfg, cfg.kinds):
        up = _upscale_frames(rec, factor)
        if cfg.detail:
            grid = masks.classify(up, cfg, rec.relname)
            up = detail.apply(up, grid, cfg)
        meta = rec.mcmeta_path if rec.animated else None
        assemble.save_texture(out_root, cfg.namespace, rec.kind, rec.name, up, meta)
        count += 1
        if with_pbr and rec.kind in cfg.pbr_kinds:
            # Recompute maps at the upscaled resolution so bump/AO match the pack.
            up_rec = _UpscaledRecord(rec, up)
            n_img, s_img = pbr.maps_for_record(up_rec, cfg)
            assemble.save_texture(out_root, cfg.namespace, rec.kind, rec.name + "_n", n_img, meta)
            assemble.save_texture(out_root, cfg.namespace, rec.kind, rec.name + "_s", s_img, meta)
        if rec.name.startswith("tier4"):
            hero = up
    assemble.write_pack_icon(out_root, hero)
    return count


class _UpscaledRecord:
    """Adapter so pbr.maps_for_record can run on an already-upscaled image."""

    def __init__(self, rec, img):
        self._img = img
        self.relname = rec.relname
        self.frames = rec.frames
        self.frame_size = img.size[0] if rec.frames == 1 else img.size[0]

    def open(self):
        return self._img

    def iter_frames(self, img=None):
        img = img if img is not None else self._img
        fw = img.size[0]
        for i in range(self.frames):
            yield i, img.crop((0, i * fw, fw, (i + 1) * fw))


PBR_README = """\
# MC3DPrint labPBR PBR Add-on

labPBR 1.3 specular + normal companion maps for MC3DPrint's blocks and items.
Load this pack ABOVE the mod (it only adds `_n`/`_s` maps, not albedo) with an
Iris/Oculus shaderpack that supports labPBR, and MC3DPrint machines gain bump,
reflectance and an emissive cyan glow.

Generated by `tools/mc_texgen` (pure Python + PIL, deterministic). Regenerate:

    python3 -m tools.mc_texgen --config mc3dprint --phase pbr

Channel layout is documented in `tools/mc_texgen/pbr.py`; the one gotcha to know
is that `_s` alpha 255 means emission OFF, so every non-glow pixel is 255.
"""


def main(argv=None):
    ap = argparse.ArgumentParser(prog="mc_texgen", description="Minecraft texture pipeline")
    ap.add_argument("--config", default="mc3dprint", help="config name, .mctg.py path")
    ap.add_argument("--phase", choices=("pbr", "upscale", "both"), default="pbr")
    ap.add_argument("--scale", type=int, default=4, help="upscale factor (2/3/4/6/8)")
    ap.add_argument("--pbr", action="store_true", help="also emit _n/_s in upscale/both")
    ap.add_argument("--out", default=None, help="output pack root (overrides default)")
    ap.add_argument("--detail", action="store_true", help="force detail synthesis on")
    ap.add_argument("--no-previews", action="store_true", help="skip docs/ contact sheets")
    args = ap.parse_args(argv)

    cfg = config.load(args.config)
    if args.detail:
        cfg = config.override(cfg, detail=True)

    summary = []
    if args.phase in ("pbr", "both"):
        out = args.out or os.path.join(_ROOT, "src/main/resources/resourcepacks/pbr_addon")
        os.makedirs(out, exist_ok=True)
        n = phase_pbr(cfg, out, "MC3DPrint: labPBR PBR Add-on", previews=not args.no_previews)
        assemble.write_readme(out, PBR_README)
        summary.append(f"pbr: {n} textures -> {out}")
    if args.phase in ("upscale", "both"):
        out = args.out or os.path.join(cfg.out_dir, f"upscale_{args.scale}x")
        os.makedirs(out, exist_ok=True)
        # --pbr alone gates _n/_s emission, matching the documented contract; "both"
        # without --pbr is just the upscale pack.
        n = phase_upscale(cfg, out, args.scale,
                          f"MC3DPrint: {args.scale}x", with_pbr=args.pbr)
        summary.append(f"upscale {args.scale}x: {n} textures -> {out}")

    print("mc-texgen done:")
    for line in summary:
        print("  " + line)
    return 0
