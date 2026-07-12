#!/usr/bin/env python3
"""
Generate the alternate-style resource packs (docs/texture-packs.md).

For each StyleProfile in tex_common.STYLES, transforms every committed default
texture (block + item + gui; patchouli book art excluded) into
  src/main/resources/resourcepacks/<style>/assets/mc3dprint/textures/...
plus the pack's pack.mcmeta (dual-era universal manifest) and pack.png.

Run AFTER the default generators, from repo root:
  python3 tools/gen_block_textures.py && python3 tools/gen_formed_textures.py ...
  python3 tools/gen_style_packs.py

Deterministic (no randomness); the default assets are inputs only and are
never written. Contact sheets for eyeballing land in build/style-previews/.
"""
import json
import os
import shutil

from PIL import Image

from tex_common import (ASSETS, BLOCK_DIR, ITEM_DIR, ROOT, RESOURCEPACKS,
                        STYLES, contact_sheet)

GUI_DIR = os.path.join(ASSETS, "textures/gui")

# Resource pack_format range covering every shipped target: 1.20.1 (15)
# through 26.2 (88). The build-all.sh smoke check + PackManifestTest assert
# this range covers each node; bump max_* when adding a version node.
FORMAT_MIN = 15
FORMAT_MAX = 88

PREVIEW_DIR = os.path.join(ROOT, "build/style-previews")


def out_dir(style, kind):
    return os.path.join(RESOURCEPACKS, style.name, "assets/mc3dprint/textures", kind)


def emit_kind(style, src_dir, kind):
    """Transform every png in src_dir; copy animation .mcmeta verbatim."""
    dst = out_dir(style, kind)
    os.makedirs(dst, exist_ok=True)
    written = []
    for name in sorted(os.listdir(src_dir)):
        src = os.path.join(src_dir, name)
        if name.endswith(".png.mcmeta"):
            shutil.copyfile(src, os.path.join(dst, name))
            continue
        if not name.endswith(".png"):
            continue
        img = Image.open(src).convert("RGBA")
        animated = os.path.exists(src + ".mcmeta")
        styled = (style.transform_animated(img, kind) if animated
                  else style.transform(img, kind))
        path = os.path.join(dst, name)
        styled.save(path)
        written.append(path)
    return written


def write_pack_meta(style):
    """Dual-era manifest: pack_format (1.20.1), supported_formats
    (1.20.2-1.21.8), min_format/max_format (1.21.9+). Older clients ignore
    the fields they don't know."""
    meta = {
        "pack": {
            "pack_format": FORMAT_MIN,
            "supported_formats": {
                "min_inclusive": FORMAT_MIN,
                "max_inclusive": FORMAT_MAX,
            },
            "min_format": FORMAT_MIN,
            "max_format": FORMAT_MAX,
            "description": style.title,
        }
    }
    root = os.path.join(RESOURCEPACKS, style.name)
    with open(os.path.join(root, "pack.mcmeta"), "w") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")


def write_pack_icon(style):
    """64x64 icon: the styled T4 printer hero centred on the style's field."""
    hero = Image.open(os.path.join(BLOCK_DIR, "tier4_printer.png")).convert("RGBA")
    styled = style.transform(hero, "block")
    # Field colour = the most common opaque pixel of the styled hero.
    counts = {}
    px = styled.load()
    for y in range(styled.size[1]):
        for x in range(styled.size[0]):
            p = px[x, y]
            if p[3] > 0:
                counts[p[:3]] = counts.get(p[:3], 0) + 1
    field = max(counts, key=counts.get) if counts else (20, 22, 26)
    icon = Image.new("RGBA", (64, 64), field + (255,))
    up = styled.resize((styled.size[0] * 2 - 8, styled.size[1] * 2 - 8), Image.NEAREST)
    icon.paste(up, ((64 - up.size[0]) // 2, (64 - up.size[1]) // 2), up)
    icon.save(os.path.join(RESOURCEPACKS, style.name, "pack.png"))


def main():
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    for style in STYLES:
        written = []
        written += emit_kind(style, BLOCK_DIR, "block")
        written += emit_kind(style, ITEM_DIR, "item")
        written += emit_kind(style, GUI_DIR, "gui")
        write_pack_meta(style)
        write_pack_icon(style)
        # Contact sheets: blocks+items together; GUIs separately (256px).
        small = [p for p in written if "/gui/" not in p]
        contact_sheet(small, scale=6, cols=9).save(
            os.path.join(PREVIEW_DIR, style.name + "_textures.png"))
        contact_sheet([p for p in written if "/gui/" in p], scale=2, cols=2).save(
            os.path.join(PREVIEW_DIR, style.name + "_guis.png"))
        print(f"{style.name}: {len(written)} textures -> resourcepacks/{style.name}")


if __name__ == "__main__":
    main()
