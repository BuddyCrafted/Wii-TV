"""Cuts the editor icons and the 2D face parts out of the channel's icon sheet.

Eye and mouth icons are colour keyed. Each keyed part is stored as weights: R, G and B hold how much of the pixel
is the part's first, second and third colour, the rest of its alpha is ink, so the app recolours it with a
single colour matrix. Brows and moustaches are plain masks; glasses keep the frame as ink and the lenses in G;
face features become multiply layers (white leaves the skin as it is).
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import SHEET, segs  # noqa: E402
from paths import OUT  # noqa: E402

# Rows of the sheet per part, and how many leading icons (tab and selection marks) to skip in the first row.
BANDS = {
    'face': ([(316, 377)], 2), 'feature': ([(397, 454)], 1),
    'hair': ([(534, 593), (606, 668), (684, 745), (758, 826), (842, 905), (924, 991)], 1),
    'brow': ([(1054, 1082), (1111, 1141)], 1),
    'eye': ([(1206, 1242), (1265, 1303), (1312, 1362), (1371, 1420)], 1),
    'nose': ([(1485, 1538)], 1), 'mouth': ([(1617, 1655), (1658, 1692)], 1),
    'glasses': ([(1761, 1819)], 2), 'mustache': ([(1833, 1888)], 1), 'mole': ([(1906, 1962)], 1),
    'beard': ([(1978, 2038)], 1),
}
UP = 3  # keyed parts are upscaled this much
LINE_FEATURES = (3, 4, 5, 6, 7, 10, 11)

KEYS = np.array([[0, 0, 0], [255, 255, 255], [255, 0, 0], [0, 255, 0], [0, 0, 255]], float)
BLACK, WHITE, RED, GREEN, BLUE = range(5)
# Which key goes to which stored channel (R, G, B); anything else counts as ink.
CHANNELS = {'eye': ([RED], [GREEN, WHITE], [BLUE]), 'mouth': ([RED], [GREEN], [BLUE, WHITE])}


def merged(y0, y1, gap=10):
    s = [x for x in segs(y0, y1) if x[0] < 940]
    out = []
    for a, b in s:
        if out and a - out[-1][1] < gap:
            out[-1] = (out[-1][0], b)
        else:
            out.append((a, b))
    return out


def rects(part):
    bands, skip = BANDS[part]
    out = []
    for bi, (y0, y1) in enumerate(bands):
        s = merged(y0, y1)
        if bi == 0:
            s = s[skip:]
        for a, b in s:
            occ = SHEET[y0:y1, a:b, 3] > 20
            ys = np.nonzero(occ.any(1))[0]
            out.append((a, y0 + ys.min(), b, y0 + ys.max() + 1))
    return out


def crop(r, pad=1):
    x0, y0, x1, y1 = r
    return SHEET[y0 - pad:y1 + pad, x0 - pad:x1 + pad].astype(float)


def key_weights(c):
    """Per pixel, the two-key blend closest to its colour: (h, w, 5) weights summing to the pixel's alpha."""
    rgb = c[..., :3]
    a = c[..., 3] / 255.0
    best = np.full(a.shape, np.inf)
    W = np.zeros(a.shape + (5,))
    for i in range(5):
        for j in range(i, 5):
            d = KEYS[j] - KEYS[i]
            dd = (d * d).sum()
            t = np.zeros(a.shape) if dd == 0 else np.clip(((rgb - KEYS[i]) * d).sum(-1) / dd, 0, 1)
            err = ((KEYS[i] + t[..., None] * d - rgb) ** 2).sum(-1)
            m = err < best
            best[m] = err[m]
            W[m] = 0
            W[m, i] += (1 - t[m])
            W[m, j] += t[m]
    return W * a[..., None]


def up(m, k=UP, edge=0.22):
    """Upscales a coverage map smoothly, then narrows its edge back to about one output pixel."""
    im = Image.fromarray(m.astype(np.float32), 'F')
    u = np.array(im.resize((m.shape[1] * k, m.shape[0] * k), Image.BICUBIC))
    u = ndimage.gaussian_filter(u, 0.6)
    t = np.clip((u - (0.5 - edge)) / (2 * edge), 0, 1)
    return t * t * (3 - 2 * t)


def save(img, name):
    path = OUT + name + '.png'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8), 'RGBA').save(path, optimize=True)


def keyed(part, r):
    W = key_weights(crop(r))
    ups = np.stack([up(W[..., k]) for k in range(5)], -1)
    alpha = up(W.sum(-1))
    tot = ups.sum(-1)
    ups = ups * (alpha / np.maximum(tot, 1e-6))[..., None]
    chans = []
    for keys in CHANNELS[part]:
        v = sum(ups[..., k] for k in keys)
        chans.append(np.where(alpha > 1e-4, v / np.maximum(alpha, 1e-6), 0))
    return np.dstack(chans + [alpha])


def mask(r):
    c = crop(r)
    a = up(c[..., 3] / 255.0)
    one = np.ones_like(a)
    return np.dstack([one, one, one, a])


def circle(c):
    """Centre and inner diameter of an icon's face circle (the white disc inside the grey ring)."""
    white = (c[..., 3] > 200) & (c[..., :3].min(-1) > 225)
    lab, n = ndimage.label(white)
    big = np.argmax(np.bincount(lab.ravel())[1:]) + 1
    ys, xs = np.nonzero(lab == big)
    return (xs.min() + xs.max() + 1) / 2, (ys.min() + ys.max() + 1) / 2, max(xs.max() - xs.min(), ys.max() - ys.min()) + 1


def ring_centre(c):
    """The icon's centre from its ring: height from the whole icon, width from the ring's top arc."""
    a = c[..., 3] > 200
    ys = np.nonzero(a.any(1))[0]
    y0, y1 = ys.min(), ys.max() + 1
    xs = np.nonzero(a[y0:y0 + 4].any(0))[0]
    return (xs.min() + xs.max() + 1) / 2, (y0 + y1) / 2


def overlay_parts(part, rs):
    """Differences between each icon and the plain one, with the face circle's geometry."""
    base = crop(rs[0], pad=6)
    bx, by = ring_centre(base)
    out = []
    for r in rs:
        c = crop(r, pad=6)
        x, y = ring_centre(c)
        c = np.roll(c, (int(round(by - y)), int(round(bx - x))), axis=(0, 1))
        h, w = min(c.shape[0], base.shape[0]), min(c.shape[1], base.shape[1])
        out.append((c[:h, :w], base[:h, :w]))
    return out


def main():
    meta = {'icons': {}, 'parts': {}}
    for part in BANDS:
        rs = rects(part)
        meta['icons'][part] = len(rs)
        for i, r in enumerate(rs):
            c = crop(r) / 255.0
            if part in CHANNELS:
                W = key_weights(crop(r))
                chans = [sum(W[..., k] for k in keys) for keys in CHANNELS[part]]
                a = W.sum(-1)
                img = np.dstack([np.where(a > 0, ch / np.maximum(a, 1e-6), 0) for ch in chans] + [a])
                save(img, 'icon/%s_%d' % (part, i))
            else:
                save(c, 'icon/%s_%d' % (part, i))
        if part in CHANNELS:
            for i, r in enumerate(rs):
                save(keyed(part, r), 'tex/%s_%d' % (part, i))
            meta['parts'][part] = {'px': UP}
        elif part in ('brow',):
            for i, r in enumerate(rs):
                save(mask(r), 'tex/%s_%d' % (part, i))
        elif part in ('feature', 'glasses', 'mustache'):
            cx, cy, d = circle(crop(rs[0], pad=6))
            meta['parts'][part] = {'cx': float(cx), 'cy': float(cy), 'd': int(d), 'px': UP if part != 'feature' else 4}
            for i, (c, base) in enumerate(overlay_parts(part, rs)):
                if i == 0:
                    continue
                diff = np.abs(c - base).max(-1) > 12
                diff = ndimage.binary_dilation(diff, iterations=1)
                lum = c[..., :3].mean(-1)
                if part == 'feature':
                    m = np.where(diff[..., None], np.clip(c[..., :3] / np.maximum(base[..., :3], 1), 0, 1), 1.0)
                    k = 4
                    big = np.dstack([np.array(Image.fromarray(m[..., j].astype(np.float32), 'F')
                                              .resize((m.shape[1] * k, m.shape[0] * k), Image.BICUBIC))
                                     for j in range(3)])
                    big = np.clip(big, 0, 1)
                    if i in LINE_FEATURES:
                        # The icons draw wrinkles and freckles far bolder than the faces do: keep their cores.
                        d = 1 - big
                        peak = max(d.max(), 1e-6)
                        t = np.clip((d.max(-1, keepdims=True) / peak - 0.45) / 0.35, 0, 1)
                        d = d * (t * t * (3 - 2 * t))
                        if i != 3:
                            d = ndimage.grey_erosion(d, size=(1, 5, 5)[1:] + (1,))
                        big = 1 - d * 0.85
                    save(np.dstack([big, np.ones(big.shape[:2])]), 'tex/%s_%d' % (part, i))
                elif part == 'glasses':
                    # By shade alone: the ring and the face disc are lighter than any frame or lens.
                    solid = c[..., 3] > 200
                    lens = ndimage.binary_opening(solid & (lum >= 60) & (lum < 150))
                    frame = np.where(solid & ~lens & (lum < 160), np.clip((235 - lum) / 200, 0, 1), 0)
                    fa = up(frame, edge=0.5)
                    la = up(lens.astype(float)) * (1 - fa)
                    alpha = fa + 0.82 * la
                    g = np.where(alpha > 1e-4, 0.82 * la / np.maximum(alpha, 1e-6), 0)
                    z = np.zeros_like(g)
                    save(np.dstack([z, g, z, alpha]), 'tex/%s_%d' % (part, i))
                else:
                    ink = diff & (lum < 120)
                    a = up(ink.astype(float))
                    one = np.ones_like(a)
                    save(np.dstack([one, one, one, a]), 'tex/%s_%d' % (part, i))
    json.dump(meta, open(OUT + 'meta2d.json', 'w'), indent=1)
    print(json.dumps(meta))


if __name__ == '__main__':
    main()
