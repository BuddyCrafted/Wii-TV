"""Assembles out/pack into miipack.zip for the TV.

The app looks for the zip in its external files folder: /sdcard/Android/data/com.gathin.tvhome/files/miipack.zip
"""
import glob
import json
import os
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import OUT, SRC, ZIP  # noqa: E402

SOUNDS = SRC + 'sonidos/Everything/'

# Editor nose order -> rendered nose (the meshes' order differs from the editor's).
NOSE_MAP = [6, 0, 10, 1, 7, 11, 8, 2, 3, 4, 5, 9]

# Head-unit scales of the 2D parts, matched by eye against the original's faces.
CAL = {'eye': 205.0, 'brow': 205.0, 'mouth': 215.0, 'feature': 1.2, 'glasses': 0.98, 'mustache': 1.2,
       'featureY': 0.03, 'glassesY': 0.035, 'mustacheY': 0.11}

# What each sound of the channel plays. Guesses from length and pitch, to be confirmed by ear.
SOUND_MAP = {
    'move': {'file': 'Sample_0001', 'vol': 0.55},
    'select': {'file': 'Sample_0016', 'vol': 0.7},
    'page': {'file': 'Sample_0005', 'vol': 0.6},
    'back': {'file': 'Sample_0010', 'vol': 0.7},
    'menu': {'file': 'Sample_0012', 'vol': 0.6},
    'start': {'file': 'Sample_0025', 'vol': 0.6},
    'whistle': {'file': 'Sample_0011', 'vol': 0.6, 'loop': 1},
    'pop': {'file': 'Sample_0008', 'vol': 0.7},
    'lift': {'file': 'Sample_0014', 'vol': 0.7},
    'drop': {'file': 'Sample_0015', 'vol': 0.7},
    'poof': {'file': 'Sample_0017', 'vol': 0.7},
    'hop': {'file': 'Sample_0009', 'vol': 0.5},
}

COUNTS = {'hair': 72, 'eye': 48, 'brow': 24, 'nose': 12, 'mouth': 24, 'face': 8, 'feature': 12, 'glasses': 9,
          'mustache': 4, 'beard': 4}


def main():
    m3 = json.load(open(OUT + 'meta3d.json'))
    m2 = json.load(open(OUT + 'meta2d.json'))
    sprites = m3['sprites']
    sizes = {}
    for name in sprites:
        with Image.open(OUT + name + '.png') as im:
            sizes[name] = im.size
    boxes = {}
    for st in ('hi', 'lo'):
        px = m3['px'][st]
        head = [-0.6, -0.8, 0.6, 0.7]
        back = [-0.6, -0.8, 0.6, 0.7]
        for name, (ox, oy) in sprites.items():
            if not name.startswith(st + '/'):
                continue
            w, h = sizes[name]
            r = [ox, oy, ox + w / px, oy + h / px]
            tgt = back if name.endswith('_b') or name.endswith('_hb') else head
            if '/hair/' in name:
                r = [min(r[0], -r[2]), r[1] - 0.06, max(r[2], -r[0]), r[3] + 0.06]  # flips and taller faces
            tgt[:] = [min(tgt[0], r[0]), min(tgt[1], r[1]), max(tgt[2], r[2]), max(tgt[3], r[3])]
        boxes[st] = {'head': [round(v, 3) for v in head], 'back': [round(v, 3) for v in back]}
    ui = json.load(open(OUT + 'metaui.json')) if os.path.exists(OUT + 'metaui.json') else {}
    pack = {
        'version': 1, 'px': m3['px'], 'shadeMax': m3['shadeMax'], 'faces': m3['faces'], 'texBox': m3['texBox'],
        'grid': m3['grid'], 'grids': m3['grids'], 'sprites': sprites, 'boxes': boxes, 'parts': m2['parts'],
        'icons': m2['icons'], 'cal': CAL, 'noseMap': NOSE_MAP, 'counts': COUNTS, 'sounds': SOUND_MAP, 'ui': ui,
    }
    with zipfile.ZipFile(ZIP, 'w') as z:
        z.writestr('pack.json', json.dumps(pack, separators=(',', ':')), compress_type=zipfile.ZIP_DEFLATED)
        for f in sorted(glob.glob(OUT + '**/*.png', recursive=True)):
            z.write(f, os.path.relpath(f, OUT), compress_type=zipfile.ZIP_STORED)
        used = sorted({v['file'] for v in SOUND_MAP.values()})
        for f in sorted(glob.glob(SOUNDS + '*.wav')):
            n = os.path.splitext(os.path.basename(f))[0]
            z.write(f, 'sound/' + n + '.wav', compress_type=zipfile.ZIP_DEFLATED)
    print(ZIP, os.path.getsize(ZIP), 'bytes;', 'boxes', boxes, 'sounds used', len(used))


if __name__ == '__main__':
    main()
