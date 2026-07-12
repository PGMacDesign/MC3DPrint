"""
Walk a mod's texture tree into TextureRecords.

Layout mirrors gen_style_packs.py: <source_root>/<ns>/textures/<kind>/*.png with
sibling <name>.png.mcmeta marking an animation strip (square frames stacked
vertically, exactly like tex_common._frame_strip builds them). Patchouli book
art and any other non-texture dirs are simply not under textures/<kind>, so the
kind allow-list is all the exclusion we need.
"""
import os
from dataclasses import dataclass

from PIL import Image


@dataclass
class TextureRecord:
    kind: str            # 'block' | 'item' | 'gui'
    name: str            # file stem, e.g. 'tier4_printer'
    relname: str         # 'block/tier4_printer' (override-map + log key)
    path: str            # absolute source path
    animated: bool       # has a sibling .png.mcmeta
    mcmeta_path: str      # sibling path (may not exist)
    width: int
    height: int
    frame_size: int      # square frame edge (== width; == height when static)
    frames: int          # 1 for static

    def open(self):
        return Image.open(self.path).convert("RGBA")

    def iter_frames(self, img=None):
        """Yield (index, frame_image) for each square frame, top-to-bottom.

        Static textures yield exactly one frame. This is the unit the neighbor-
        sensitive stages (Sobel normals, EPX upscale) operate on so a frame
        never bleeds into the one stacked below it.
        """
        img = img if img is not None else self.open()
        w = self.frame_size
        for i in range(self.frames):
            yield i, img.crop((0, i * w, w, (i + 1) * w))


def kind_dir(cfg, kind):
    return os.path.join(cfg.source_root, cfg.namespace, "textures", kind)


def records(cfg, kinds=None):
    """Yield TextureRecords for the requested kinds (default: all cfg.kinds)."""
    for kind in (kinds if kinds is not None else cfg.kinds):
        d = kind_dir(cfg, kind)
        if not os.path.isdir(d):
            continue
        for fname in sorted(os.listdir(d)):
            if not fname.endswith(".png"):
                continue
            path = os.path.join(d, fname)
            name = fname[:-4]
            meta = path + ".mcmeta"
            animated = os.path.exists(meta)
            with Image.open(path) as im:
                w, h = im.size
            # Animation strips are N square frames stacked vertically (frame edge
            # == width); a static texture is a single frame.
            if animated and w and h % w == 0:
                frame, frames = w, h // w
            else:
                frame, frames = h, 1
            yield TextureRecord(
                kind=kind, name=name, relname=f"{kind}/{name}", path=path,
                animated=animated, mcmeta_path=meta, width=w, height=h,
                frame_size=frame, frames=frames,
            )
