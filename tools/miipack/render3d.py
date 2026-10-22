"""Renders the published 3D Mii parts to sprites in the app's head units.

Head units: the face is one unit wide, x runs right and y down from the middle of the eye line. Every sprite is
saved cropped, and meta records the head-unit position of its top-left pixel.

Sets: 'hi' (320 px per unit, front only) for the editor, 'lo' (80 px per unit, four views) for the plaza.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from r3d import Cam, Frame, load_obj, raster, unit_normals  # noqa: E402
import paths  # noqa: E402

SRC = paths.SRC + 'modelos_3d/'
OUT = paths.OUT

S = 1.9011406844106462  # head units per model unit
ZC, YE, XC = 0.007, 0.33238, 0.042  # head centre (z), eye line (y), head centre depth (x), model units

SETS = {'hi': {'px': 320, 'ss': 2, 'views': ['F']},
        'lo': {'px': 80, 'ss': 4, 'views': ['F', 'L', 'R', 'B']}}
YAW = {'F': 0.0, 'L': -45.0, 'R': 45.0, 'B': 180.0}


def nrm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


LIGHT = nrm([0.25, 0.45, 0.86])
HALF = nrm(LIGHT + np.array([0, 0, 1.0]))

# Icons 13, 39 and 65 have no usable mesh in the published set; they borrow the closest looking style.
HAIR_SUBST = {13: 11, 39: 0, 65: 53}
BALD, CAP, BEANIE = 33, 34, 35


def centred(mesh):
    mesh.V = mesh.V - np.array([XC, YE, ZC])
    return mesh


def two_sided(n):
    s = np.sign(n[..., 2:3])
    s[s == 0] = 1
    return n * s


class Shot:
    """One view of a box in head units at a set's resolution, rendered supersampled."""

    def __init__(self, box, yaw, px, ss):
        self.box, self.px, self.ss = box, px, ss
        L, T, R, B = box
        self.w = int(np.ceil((R - L) * px))
        self.h = int(np.ceil((B - T) * px))
        sc = px * ss * S
        self.cam = Cam(sc, L / S, -T / S, self.w * ss, self.h * ss, yaw)

    def frame(self):
        return Frame(self.w * self.ss, self.h * self.ss)

    def down(self, cov, *chans):
        """Coverage-weighted box filter: returns coverage and each channel averaged over covered samples."""
        ss = self.ss
        h, w = self.h, self.w
        c = cov.astype(float).reshape(h, ss, w, ss)
        a = c.mean(axis=(1, 3))
        out = [a]
        for ch in chans:
            v = (ch * cov).reshape(h, ss, w, ss).sum(axis=(1, 3))
            n = c.sum(axis=(1, 3))
            out.append(np.where(n > 0, v / np.maximum(n, 1e-9), 0))
        return out


def bbox_units(mesh, objs, yaw, pad=0.04):
    P = np.concatenate([mesh.V[np.unique(mesh.objs[o]['tri'][:, :, 0])] for o in objs])
    a = np.radians(yaw)
    c, s = np.cos(a), np.sin(a)
    z = -s * P[:, 0] + c * P[:, 2]
    x = z * S
    y = -P[:, 1] * S
    return [x.min() - pad, y.min() - pad, x.max() + pad, y.max() + pad]


def union(a, b):
    return [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])]


def save(name, rgba, shot, meta):
    """Crops to the covered pixels and records the head-unit origin of the top-left pixel."""
    a = rgba[..., 3]
    ys, xs = np.nonzero(a > 0.002)
    if len(xs) == 0:
        return
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    crop = np.clip(rgba[y0:y1, x0:x1], 0, 1)
    path = OUT + name + '.png'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray((crop * 255 + 0.5).astype(np.uint8), 'RGBA').save(path, optimize=True)
    L, T = shot.box[0], shot.box[1]
    meta[name] = [round(L + x0 / shot.px, 5), round(T + y0 / shot.px, 5)]


def skin_shade(n):
    d = np.clip(n @ LIGHT, 0, 1)
    return 0.70 + 0.34 * d


def hair_shade(n):
    d = np.clip(n @ LIGHT, 0, 1)
    sp = np.clip(n @ HALF, 0, 1) ** 28
    return 0.46 + 0.66 * d, 0.32 * sp


SHADE_MAX = 1.25  # shades are stored divided by this so highlights fit in a byte


def sample_tex(tex, uv):
    h, w = tex.shape[:2]
    u = np.mod(uv[..., 0], 1.0)
    v = np.mod(1.0 - uv[..., 1], 1.0)
    return tex[(v * (h - 1)).astype(int), (u * (w - 1)).astype(int)]


def load_all():
    head = centred(load_obj(SRC + 'cabezas/Miiheads/head.obj'))
    hair = centred(load_obj(SRC + 'cabellos/Miihair/Hair.obj'))
    spec = centred(load_obj(SRC + 'cabellos/Miihair/special.obj'))
    nose = centred(load_obj(SRC + 'narices/Noses/Standart.obj'))
    flat = centred(load_obj(SRC + 'narices/Noses/flat.obj'))
    beard = centred(load_obj(SRC + 'barbas/beard/beard.obj'))
    return head, hair, spec, nose, flat, beard


def face_geometry(head):
    """Per faceline: top and half width in head units, for fitting hair made on faceline 0."""
    out = []
    for o in head.objs:
        P = head.V[np.unique(o['tri'][:, :, 0])]
        out.append({'top': round(float(-P[:, 1].max() * S), 4), 'hw': round(float(np.abs(P[:, 2]).max() * S), 4),
                    'chin': round(float(-P[:, 1].min() * S), 4)})
    return out


# ---- facelines ---------------------------------------------------------------------------------------------

TEX_BOX = (-0.62, -0.95, 0.62, 0.85)  # the face texture's head-unit box
GRID = (16, 24)  # face texture mesh vertices across and down, for the side views


def render_faces(head, meta, grids):
    for f in range(len(head.objs)):
        for sname, st in SETS.items():
            for v in st['views']:
                box = bbox_units(head, [f], YAW[v])
                shot = Shot(box, YAW[v], st['px'], st['ss'])
                fr = raster(shot.frame(), shot.cam, head, [f])
                cov = fr.oid >= 0
                n = two_sided(unit_normals(fr))
                a, sh = shot.down(cov, skin_shade(n))
                z = np.zeros_like(a)
                save('%s/face/%d_%s' % (sname, f, v), np.dstack([sh / SHADE_MAX, z, z, a]), shot, meta)
        grids[str(f)] = {v: face_grid(head, f, YAW[v]) for v in ('L', 'R')}


def face_grid(head, f, yaw):
    """Where each vertex of a grid over the front face texture lands in a turned view, in head units."""
    px = 120
    box = bbox_units(head, [f], 0.0, pad=0.3)
    shot = Shot(box, 0.0, px, 1)
    fr = raster(shot.frame(), shot.cam, head, [f])
    cov = fr.oid >= 0
    from scipy import ndimage
    _, (iy, ix) = ndimage.distance_transform_edt(~cov, return_indices=True)
    depth = fr.pos[..., 0][iy, ix]
    nx, ny = GRID
    L, T, R, B = TEX_BOX
    a = np.radians(yaw)
    c, s = np.cos(a), np.sin(a)
    pts = []
    for j in range(ny):
        for i in range(nx):
            hx = L + (R - L) * i / (nx - 1)
            hy = T + (B - T) * j / (ny - 1)
            col = int(np.clip((hx - box[0]) * px, 0, shot.w - 1))
            row = int(np.clip((hy - box[1]) * px, 0, shot.h - 1))
            x = depth[row, col]
            z = hx / S
            y = -hy / S
            z2 = -s * x + c * z
            pts += [round(float(z2 * S), 4), round(float(-y * S), 4)]
    return pts


# ---- hair ------------------------------------------------------------------------------------------------

def hair_source(icon, hmap, hair, spec):
    if icon == BALD:
        return None
    if icon == CAP:
        return spec, 0
    if icon == BEANIE:
        return spec, 1
    mi = HAIR_SUBST.get(icon, hmap.get(str(icon)))
    return hair, mi


def render_hair(head, hair, spec, hmap, meta, icons=range(72)):
    texs = {'Material_1': np.array(Image.open(SRC + 'cabellos/Miihair/tex1_64x16_0e47a0ca344bc0ef_0.png')
                                   .convert('L')) / 255.0,
            'Material_3': np.array(Image.open(SRC + 'cabellos/Miihair/tex1_32x64_2544ee5e171e08fd_0.png')
                                   .convert('L')) / 255.0}
    mids = {'Material_1': 1, 'Material_3': 3, 'Material_2': 2}
    for icon in icons:
        srcm = hair_source(icon, hmap, hair, spec)
        if srcm is None:
            continue
        mesh, oi = srcm
        for sname, st in SETS.items():
            for v in st['views']:
                yaw = YAW[v]
                box = union(bbox_units(mesh, [oi], yaw), bbox_units(head, [0], yaw))
                shot = Shot(box, yaw, st['px'], st['ss'])
                alone = raster(shot.frame(), shot.cam, mesh, [oi], mtl_ids=mids)
                both = raster(shot.frame(), shot.cam, head, [0], oid_base=1000)
                both = raster(both, shot.cam, mesh, [oi], mtl_ids=mids)
                hcov = alone.oid >= 0
                # Hair seen in front of the face always covers it; around the face, what lies in front of the
                # head's middle goes over the body and the rest behind it.
                face = raster(shot.frame(), shot.cam, head, [0]).oid >= 0
                seen = (both.oid >= 0) & (both.oid < 1000)
                front = seen & (face | (both.pos[..., 0] < 0))
                if v == 'B':
                    front = hcov.copy()
                back = hcov & ~front
                n = two_sided(unit_normals(alone))
                d, sp = hair_shade(n)
                hat = np.isin(alone.mid, (1, 3))
                tex = np.ones(hcov.shape)
                for name, t in texs.items():
                    m = alone.mid == mids[name]
                    if m.any():
                        tex[m] = sample_tex(t, alone.uv[m])
                dd = d * np.where(hat, 0.35 + 0.65 * tex, 1.0)
                for layer, sel in (('f', front & ~hat), ('b', back & ~hat), ('hf', front & hat), ('hb', back & hat)):
                    if not sel.any():
                        continue
                    a, r, g = shot.down(sel, dd / SHADE_MAX, sp)
                    save('%s/hair/%d_%s_%s' % (sname, icon, v, layer), np.dstack([r, g, np.zeros_like(r), a]),
                         shot, meta)


# ---- noses -----------------------------------------------------------------------------------------------

def nose_list(nose, flat):
    return [(nose, i) for i in range(len(nose.objs))] + [(flat, i) for i in range(len(flat.objs))]


def split_decal(mesh, oi):
    """The skin bump and the line decal of one nose, as two meshes; the decal moved a hair toward the viewer."""
    o = mesh.objs[oi]
    import copy
    out = []
    for want_skin in (True, False):
        sel = np.array([(m == 'Skin') == want_skin for m in o['mtl']])
        m2 = copy.copy(mesh)
        m2.V = mesh.V.copy()
        if not want_skin and sel.any():
            vi = np.unique(o['tri'][sel][:, :, 0])
            m2.V[vi, 0] -= 0.006
        m2.objs = [{'name': o['name'], 'tri': o['tri'][sel], 'mtl': [m for m, k in zip(o['mtl'], sel) if k]}]
        out.append(m2 if sel.any() else None)
    return out


def render_noses(head, nose, flat, meta):
    texdir = SRC + 'narices/Noses/'
    mtl = {}
    for f in ('Standart.mtl', 'flat.mtl'):
        cur = None
        for line in open(texdir + f):
            p = line.split()
            if not p:
                continue
            if p[0] == 'newmtl':
                cur = p[1]
            elif p[0] == 'map_d':
                mtl[cur] = np.array(Image.open(texdir + p[1]).convert('RGBA'))[..., 3] / 255.0
    for k, (mesh, oi) in enumerate(nose_list(nose, flat)):
        skin, decal = split_decal(mesh, oi)
        for sname, st in SETS.items():
            for v in st['views']:
                if v == 'B':
                    continue
                yaw = YAW[v]
                box = bbox_units(mesh, [oi], yaw, pad=0.06)
                shot = Shot(box, yaw, st['px'], st['ss'])
                both = raster(shot.frame(), shot.cam, head, [0], oid_base=1000)
                if skin is not None:
                    both = raster(both, shot.cam, skin, [0])
                bump = (both.oid >= 0) & (both.oid < 1000)
                n = two_sided(unit_normals(both))
                a, sh = shot.down(bump, skin_shade(n))
                z = np.zeros_like(a)
                save('%s/nose/%d_%s_s' % (sname, k, v), np.dstack([sh / SHADE_MAX, z, z, a]), shot, meta)
                if decal is None:
                    continue
                dfr = raster(shot.frame(), shot.cam, decal, [0])
                vis = (dfr.oid >= 0) & (dfr.depth <= both.depth + 1e-4)
                line = np.zeros(vis.shape)
                t = mtl[decal.objs[0]['mtl'][0]]
                if vis.any():
                    line[vis] = sample_tex(t, dfr.uv[vis])
                a = shot.down(np.ones(vis.shape, bool), line)[1]
                z = np.zeros_like(a)
                save('%s/nose/%d_%s_l' % (sname, k, v), np.dstack([z, z, z, a]), shot, meta)


# ---- beards ----------------------------------------------------------------------------------------------

def render_beards(head, beard, meta):
    for b in range(len(beard.objs)):
        for f in range(len(head.objs)):
            for sname, st in SETS.items():
                for v in st['views']:
                    if v == 'B':
                        continue
                    yaw = YAW[v]
                    box = bbox_units(beard, [b], yaw, pad=0.06)
                    shot = Shot(box, yaw, st['px'], st['ss'])
                    both = raster(shot.frame(), shot.cam, head, [f], oid_base=1000)
                    both = raster(both, shot.cam, beard, [b])
                    sel = (both.oid >= 0) & (both.oid < 1000)
                    n = two_sided(unit_normals(both))
                    d, sp = hair_shade(n)
                    a, r, g = shot.down(sel, d / SHADE_MAX, sp)
                    save('%s/beard/%d_%d_%s' % (sname, b, f, v), np.dstack([r, g, np.zeros_like(r), a]), shot,
                         meta)


def main(stages):
    head, hair, spec, nose, flat, beard = load_all()
    hmap = json.load(open(os.path.join(paths.HERE, 'hairmap.json')))['map']
    mpath = OUT + 'meta3d.json'
    meta = json.load(open(mpath)) if os.path.exists(mpath) else {}
    sprites = meta.setdefault('sprites', {})
    meta['faces'] = face_geometry(head)
    meta['px'] = {k: v['px'] for k, v in SETS.items()}
    meta['shadeMax'] = SHADE_MAX
    meta['texBox'] = TEX_BOX
    meta['grid'] = GRID
    if 'faces' in stages:
        grids = meta.setdefault('grids', {})
        render_faces(head, sprites, grids)
    if 'hair' in stages:
        render_hair(head, hair, spec, hmap, sprites)
    if 'noses' in stages:
        render_noses(head, nose, flat, sprites)
    if 'beards' in stages:
        render_beards(head, beard, sprites)
    os.makedirs(OUT, exist_ok=True)
    json.dump(meta, open(mpath, 'w'))


if __name__ == '__main__':
    main(sys.argv[1:] or ['faces', 'hair', 'noses', 'beards'])
