import os, sys, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import SRC
from r3d import *
from PIL import Image, ImageDraw
M = SRC + 'modelos_3d/'
SHEET = np.array(Image.open(SRC + 'interfaz_y_texturas/sprites/Body_Part_Icons.png').convert('RGBA')).astype(int)

def segs(y0, y1, sheet=SHEET, thr=20):
    occ = sheet[y0:y1, :, 3] > thr
    cols = occ.any(0); out = []; x = 0; W = len(cols)
    while x < W:
        if cols[x]:
            x0 = x
            while x < W and cols[x]: x += 1
            out.append((x0, x))
        x += 1
    return out

def tight(b, sheet=SHEET):
    x0, y0, x1, y1 = b
    occ = sheet[y0:y1, x0:x1, 3] > 20
    ys, xs = np.nonzero(occ)
    return (x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1)

HAIR_BANDS = [(534, 593), (606, 668), (684, 745), (758, 826), (842, 905), (924, 991)]
def hair_icons():
    out = []
    for bi, (y0, y1) in enumerate(HAIR_BANDS):
        s = segs(y0, y1)
        if bi == 0: s = s[1:]
        out += [tight((a, y0, b, y1)) for (a, b) in s]
    return out

def classes_icon(b):
    x0, y0, x1, y1 = b
    c = SHEET[y0:y1, x0:x1]
    a = c[..., 3] > 100
    lum = c[..., :3].mean(2)
    cls = np.zeros(a.shape)
    cls[a & (lum < 90)] = 2
    cls[a & (lum >= 90)] = 1
    return cls

def norm(cls, N=48):
    ys, xs = np.nonzero(cls > 0)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    c = cls[y0:y1, x0:x1]
    h, w = c.shape; s = max(h, w)
    pad = np.zeros((s, s)); oy = s - h; ox = (s - w) // 2
    pad[oy:oy + h, ox:ox + w] = c
    im = Image.fromarray((pad * 100).astype(np.uint8)).resize((N, N), Image.NEAREST)
    return np.array(im) / 100.0

def toimg(c, cell=None):
    rgb = np.zeros(c.shape + (3,), np.uint8); rgb[c == 1] = (235, 235, 235); rgb[c == 2] = (20, 20, 20); rgb[c == 0] = (40, 90, 160)
    im = Image.fromarray(rgb)
    return im.resize((cell, cell), Image.NEAREST) if cell else im
