"""Cuts the channel's interface out of the Mii Plaza, Channel Icon and Wii Menu Banner sheets into out/pack/ui.

Boxes are the sprites' tight bounds in the published sheets; halves are mirrored into whole shapes.
"""
import json
import os
import sys

from PIL import Image, ImageOps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import OUT, SRC  # noqa: E402

SHEETS = SRC + 'interfaz_y_texturas/sprites/'

PLAZA = {
    'arrow_back': (519, 30, 583, 89), 'whistle': (594, 32, 656, 84), 'help': (666, 31, 709, 84),
    'face': (618, 99, 672, 153), 'face_feat': (680, 99, 703, 119), 'badge': (710, 98, 742, 128),
    'eye': (747, 99, 767, 111), 'plus': (747, 117, 767, 137), 'dash_ring': (778, 99, 839, 159),
    'sq_ring': (519, 192, 602, 275), 'sq_fill': (607, 195, 685, 273), 'remote': (692, 194, 752, 254),
    'mail': (759, 194, 821, 233), 'ball': (520, 282, 592, 354), 'parade': (599, 282, 659, 323),
    'ball_tip': (519, 360, 582, 414), 'talk': (588, 360, 633, 396), 'crown': (641, 361, 689, 400),
    'palette': (697, 361, 740, 403), 'boygirl': (748, 360, 790, 403), 'pencil': (668, 533, 691, 557),
    'hand': (519, 624, 562, 686), 'hand_blur': (567, 623, 611, 685), 'grab': (618, 643, 661, 686),
    'grab_blur': (667, 642, 711, 687), 'poof': (719, 626, 777, 687), 'dots_ring': (715, 784, 771, 840),
    'z': (306, 127, 336, 155), 'count': (519, 420, 599, 487),
}
HALVES = {'ring': (521, 100, 562, 183), 'disc': (569, 101, 609, 182)}
FLOOR = (1, 1, 257, 257)

# The 15 faces of the banner sheet, in its three rows, and where the Wii Menu's tile shows each one:
# centre x, centre y and width as fractions of the tile, measured on a capture of the original menu.
CROWD_BOXES = [
    (8, 64, 51, 113), (63, 62, 97, 117), (108, 63, 152, 119), (165, 65, 211, 111), (224, 66, 257, 111),
    (8, 127, 48, 174), (58, 129, 100, 175), (110, 129, 152, 173), (165, 128, 201, 171), (216, 125, 258, 169),
    (12, 189, 48, 235), (59, 187, 99, 243), (113, 192, 149, 234), (163, 190, 203, 235), (220, 187, 256, 235),
]
CROWD_X = [[0.112, 0.336, 0.539, 0.750, 0.934], [0.026, 0.204, 0.428, 0.651, 0.882], [0.079, 0.283, 0.513, 0.737, 0.921]]
CROWD_Y = [0.157, 0.520, 0.880]
FACE_W = 0.224
LOGO = (177, 12, 249, 55)
# Font.png's bold face is ASCII from '/' down its first column; these are the tops of '0' to '9'.
DIGIT_TOPS = [37, 71, 103, 136, 169, 203, 235, 269, 301, 334]
DIGIT_H = 28


def pad(im, n=1):
    return ImageOps.expand(im, border=n, fill=(0, 0, 0, 0))


def save(im, name):
    path = OUT + 'ui/' + name + '.png'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, optimize=True)
    return im.size


def main():
    plaza = Image.open(SHEETS + 'Mii_Plaza.png').convert('RGBA')
    sizes = {}
    for name, box in PLAZA.items():
        sizes[name] = save(pad(plaza.crop(box)), name)
    for name, box in HALVES.items():
        half = plaza.crop(box)
        whole = Image.new('RGBA', (half.width * 2, half.height), (0, 0, 0, 0))
        whole.paste(half, (0, 0))
        whole.paste(ImageOps.mirror(half), (half.width, 0))
        sizes[name] = save(pad(whole), name)
    save(plaza.crop(FLOOR).convert('RGB'), 'floor')

    banner = Image.open(SHEETS + 'Wii_Menu_Banner.png').convert('RGBA')
    crowd = []
    for i, box in enumerate(CROWD_BOXES):
        name = 'crowd%02d' % i
        save(pad(banner.crop(box)), name)
        row, col = divmod(i, 5)
        crowd.append([name, CROWD_X[row][col], CROWD_Y[row], FACE_W])
    font = Image.open(SHEETS + 'Font.png').convert('RGBA')
    for d, top in enumerate(DIGIT_TOPS):
        cell = font.crop((0, top - 1, 34, top - 1 + DIGIT_H))
        x0, _, x1, _ = cell.getchannel('A').point(lambda v: 255 if v > 8 else 0).getbbox()
        save(pad(cell.crop((x0, 0, x1, DIGIT_H))), 'n%d' % d)
    icon = Image.open(SHEETS + 'Channel_Icon.png').convert('RGBA')
    save(pad(icon.crop(LOGO)), 'logo')
    meta = {'crowd': crowd}
    json.dump(meta, open(OUT + 'metaui.json', 'w'), indent=1)
    print('ui sprites', len(sizes) + len(crowd) + 2, sizes)


if __name__ == '__main__':
    main()
