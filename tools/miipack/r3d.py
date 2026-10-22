"""Tiny orthographic software rasteriser for the published Mii meshes.

Coordinates: the meshes use y up, -x toward the viewer and z sideways. A view is a yaw around y; the camera
looks along +x after the yaw. Screen x = z (right), screen y = -y (down).
"""
import numpy as np
from PIL import Image


class Mesh:
    def __init__(self):
        self.V = None
        self.T = None
        self.N = None
        self.objs = []  # dicts: name, tri (k,3,3) indices v/t/n, mtl (k,)


def load_obj(path):
    V, T, N = [], [], []
    objs = []
    cur = None
    mtl = None
    for line in open(path, errors='replace'):
        p = line.split()
        if not p:
            continue
        k = p[0]
        if k == 'v':
            V.append([float(x) for x in p[1:4]])
        elif k == 'vt':
            T.append([float(x) for x in p[1:3]])
        elif k == 'vn':
            N.append([float(x) for x in p[1:4]])
        elif k in ('o', 'g'):
            cur = {'name': ' '.join(p[1:]), 'tri': [], 'mtl': []}
            objs.append(cur)
        elif k == 'usemtl':
            mtl = p[1]
        elif k == 'f':
            if cur is None:
                cur = {'name': 'default', 'tri': [], 'mtl': []}
                objs.append(cur)
            idx = []
            for q in p[1:]:
                a = q.split('/')
                vi = int(a[0])
                ti = int(a[1]) if len(a) > 1 and a[1] else 0
                ni = int(a[2]) if len(a) > 2 and a[2] else 0
                idx.append((vi - 1 if vi > 0 else len(V) + vi,
                            ti - 1 if ti > 0 else (len(T) + ti if ti < 0 else -1),
                            ni - 1 if ni > 0 else (len(N) + ni if ni < 0 else -1)))
            for j in range(1, len(idx) - 1):
                cur['tri'].append((idx[0], idx[j], idx[j + 1]))
                cur['mtl'].append(mtl)
    m = Mesh()
    m.V = np.array(V, dtype=np.float64)
    m.T = np.array(T, dtype=np.float64) if T else np.zeros((1, 2))
    m.N = np.array(N, dtype=np.float64) if N else np.zeros((1, 3))
    for o in objs:
        o['tri'] = np.array(o['tri'], dtype=np.int64).reshape(-1, 3, 3)
    m.objs = [o for o in objs if len(o['tri'])]
    return m


def yaw_matrix(deg):
    a = np.radians(deg)
    c, s = np.cos(a), np.sin(a)
    # Rotate around y; positive turns the face toward screen right.
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


class Frame:
    """Buffers for one view: depth (smaller = nearer), normal (camera space), uv, object id, world pos."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.depth = np.full((h, w), np.inf)
        self.normal = np.zeros((h, w, 3))
        self.uv = np.zeros((h, w, 2))
        self.oid = np.full((h, w), -1, dtype=np.int32)
        self.mid = np.full((h, w), -1, dtype=np.int32)
        self.pos = np.zeros((h, w, 3))


class Cam:
    """Orthographic camera: world point -> pixel. scale = pixels per world unit; (ox, oy) world point at pixel 0,0."""

    def __init__(self, scale, x0, y0, w, h, yaw=0.0):
        self.scale, self.x0, self.y0, self.w, self.h = scale, x0, y0, w, h
        self.R = yaw_matrix(yaw)

    def project(self, P):
        Q = P @ self.R.T
        sx = (Q[:, 2] - self.x0) * self.scale
        sy = (self.y0 - Q[:, 1]) * self.scale
        return np.stack([sx, sy, Q[:, 0]], axis=1), Q


def raster(frame, cam, mesh, objs=None, oid_base=0, mtl_ids=None, keep=None):
    """Draws the chosen objects into frame with a depth test. keep(depth_array, px, py) -> bool mask can veto."""
    objs = range(len(mesh.objs)) if objs is None else objs
    Rn = cam.R
    for oi in objs:
        o = mesh.objs[oi]
        tri = o['tri']
        vi, ti, ni = tri[:, :, 0], tri[:, :, 1], tri[:, :, 2]
        P = mesh.V[vi.reshape(-1)]
        S, Q = cam.project(P)
        S = S.reshape(-1, 3, 3)
        Q = Q.reshape(-1, 3, 3)
        Nw = mesh.N[np.maximum(ni.reshape(-1), 0)] @ Rn.T
        Nw = Nw.reshape(-1, 3, 3)
        UV = mesh.T[np.maximum(ti.reshape(-1), 0)].reshape(-1, 3, 2)
        mids = [mtl_ids.get(mm, 0) if mtl_ids else 0 for mm in o['mtl']]
        for k in range(len(tri)):
            s = S[k]
            x0 = int(max(0, np.floor(s[:, 0].min())))
            x1 = int(min(frame.w - 1, np.ceil(s[:, 0].max())))
            y0 = int(max(0, np.floor(s[:, 1].min())))
            y1 = int(min(frame.h - 1, np.ceil(s[:, 1].max())))
            if x1 < x0 or y1 < y0:
                continue
            ax, ay = s[0, 0], s[0, 1]
            bx, by = s[1, 0], s[1, 1]
            cx, cy = s[2, 0], s[2, 1]
            den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(den) < 1e-12:
                continue
            xs = np.arange(x0, x1 + 1) + 0.5
            ys = np.arange(y0, y1 + 1) + 0.5
            X, Y = np.meshgrid(xs, ys)
            l0 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
            l1 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-9) & (l1 >= -1e-9) & (l2 >= -1e-9)
            if not inside.any():
                continue
            z = l0 * s[0, 2] + l1 * s[1, 2] + l2 * s[2, 2]
            sub = frame.depth[y0:y1 + 1, x0:x1 + 1]
            m = inside & (z < sub)
            if keep is not None:
                m &= keep(z, X, Y)
            if not m.any():
                continue
            sub[m] = z[m]
            n = l0[..., None] * Nw[k, 0] + l1[..., None] * Nw[k, 1] + l2[..., None] * Nw[k, 2]
            frame.normal[y0:y1 + 1, x0:x1 + 1][m] = n[m]
            uv = l0[..., None] * UV[k, 0] + l1[..., None] * UV[k, 1] + l2[..., None] * UV[k, 2]
            frame.uv[y0:y1 + 1, x0:x1 + 1][m] = uv[m]
            pq = l0[..., None] * Q[k, 0] + l1[..., None] * Q[k, 1] + l2[..., None] * Q[k, 2]
            frame.pos[y0:y1 + 1, x0:x1 + 1][m] = pq[m]
            frame.oid[y0:y1 + 1, x0:x1 + 1][m] = oid_base + oi
            frame.mid[y0:y1 + 1, x0:x1 + 1][m] = mids[k]
    return frame


def unit_normals(frame):
    n = frame.normal
    ln = np.linalg.norm(n, axis=2, keepdims=True)
    ln[ln == 0] = 1
    n = n / ln
    # Camera space: the camera looks along +x, so a surface facing the viewer has normal x < 0.
    # Convert to (right, up, toward viewer) = (z, y, -x).
    return np.stack([n[..., 2], n[..., 1], -n[..., 0]], axis=2)


def downsample(img, f):
    h, w = img.shape[:2]
    h2, w2 = h // f, w // f
    img = img[:h2 * f, :w2 * f]
    return img.reshape(h2, f, w2, f, -1).mean(axis=(1, 3))


def save_rgba(arr, path):
    a = np.clip(arr, 0, 1)
    Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGBA').save(path)
