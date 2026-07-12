"""
Resource-pack assembly: pack.mcmeta (dual-era), pack.png, the texture tree.

The manifest mirrors tools/gen_style_packs.py so every emitted pack advertises
the same version window as the style packs (one FORMAT_MIN..FORMAT_MAX source of
truth in config.py). Older clients read pack_format/supported_formats; 1.21.9+
reads min_format/max_format; each ignores the fields it does not know.
"""
import json
import os
import shutil

from PIL import Image

from .config import FORMAT_MAX, FORMAT_MIN


def texture_dir(pack_root, namespace, kind):
    return os.path.join(pack_root, "assets", namespace, "textures", kind)


def write_pack_meta(pack_root, description):
    meta = {
        "pack": {
            "pack_format": FORMAT_MIN,
            "supported_formats": {"min_inclusive": FORMAT_MIN, "max_inclusive": FORMAT_MAX},
            "min_format": FORMAT_MIN,
            "max_format": FORMAT_MAX,
            "description": description,
        }
    }
    with open(os.path.join(pack_root, "pack.mcmeta"), "w") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")


def write_pack_icon(pack_root, hero_img=None, field=(24, 40, 56, 255)):
    """64x64 icon. Deterministic: a flat field with the optional hero centred."""
    icon = Image.new("RGBA", (64, 64), field)
    if hero_img is not None:
        s = hero_img.convert("RGBA")
        up = s.resize((48, 48), Image.NEAREST)
        icon.paste(up, (8, 8), up)
    icon.save(os.path.join(pack_root, "pack.png"))


def save_texture(pack_root, namespace, kind, name, img, src_mcmeta=None):
    """Write one texture into the pack, copying an animation .mcmeta if given."""
    d = texture_dir(pack_root, namespace, kind)
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, name + ".png")
    img.save(path)
    if src_mcmeta and os.path.exists(src_mcmeta):
        shutil.copyfile(src_mcmeta, path + ".mcmeta")
    return path


def write_readme(pack_root, text):
    with open(os.path.join(pack_root, "README.md"), "w") as fh:
        fh.write(text)
