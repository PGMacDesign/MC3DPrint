#!/usr/bin/env python3
"""Block textures for the Server Blueprint Repository (16x16 front/side/top).

Console aesthetic matching the machine GUI palette: a charcoal casing with a
cyan accent. The FRONT reads as a library terminal — a small screen over two
rows of disc slots. Reproducible; rerun to regenerate.

  python3 tools/gen_repository_textures.py
"""
import os
from PIL import Image, ImageDraw

PANEL = (0x1A, 0x1F, 0x2B, 255)
DARK = (0x10, 0x14, 0x1E, 255)
BEVEL_L = (0x2C, 0x33, 0x42, 255)
BEVEL_D = (0x0A, 0x0D, 0x14, 255)
ACCENT = (0x3F, 0xE0, 0xC0, 255)
ACCENT_D = (0x27, 0x86, 0x76, 255)
SCREEN = (0x0D, 0x10, 0x18, 255)
DISC = (0x23, 0x2A, 0x39, 255)

OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), os.pardir,
      "src/main/resources/assets/mc3dprint/textures/block"))


def _frame(d):
    d.rectangle([0, 0, 15, 15], fill=PANEL)
    d.line([0, 0, 15, 0], fill=BEVEL_L)
    d.line([0, 0, 0, 15], fill=BEVEL_L)
    d.line([0, 15, 15, 15], fill=BEVEL_D)
    d.line([15, 0, 15, 15], fill=BEVEL_D)


def side():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    _frame(d)
    d.line([2, 2, 13, 2], fill=ACCENT)       # accent stripe near the top
    d.line([2, 3, 13, 3], fill=ACCENT_D)
    for y in (6, 9, 12):                       # casing ribs
        d.line([2, y, 13, y], fill=BEVEL_D)
    return img


def top():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    _frame(d)
    d.rectangle([3, 3, 12, 12], outline=BEVEL_D)
    d.line([3, 3, 12, 3], fill=ACCENT_D)
    return img


def front():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    _frame(d)
    d.line([2, 2, 13, 2], fill=ACCENT)         # console stripe
    d.line([2, 3, 13, 3], fill=ACCENT_D)
    d.rectangle([3, 4, 12, 7], fill=SCREEN, outline=ACCENT_D)  # screen
    for cx in (4, 7, 10):                        # row of catalogued-disc dots
        for cy in (10, 13):
            d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=DISC, outline=ACCENT_D)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, img in (("blueprint_repository_front", front()),
                      ("blueprint_repository_side", side()),
                      ("blueprint_repository_top", top())):
        path = os.path.join(OUT, name + ".png")
        img.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
