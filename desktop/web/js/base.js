// Pixels for the channel menu's static parts, ported line for line from the Android launcher's WiiBase.java:
// the striped backdrop, the blue line, the bottom panel with its button tracks, the round buttons' bodies and
// the empty-slot texture. Coordinates are pixels of the 1920 x 1080 reference ("ref px"); s converts them to
// screen pixels. The colour tables come from WiiPalette.java via scripts/prepare.mjs.
import * as P from '../gen/palette.js';

export const CX = 959.5;
export const LINE_TOP = 783.36;
export const LINE_BOTTOM = 898.48;
const CURVE = [288, 448, 516, 686];
export const LINE_HALF = 2.5;
const LINE_R = 0x3e, LINE_G = 0xbc, LINE_B = 0xe8;
const HIGHLIGHT_D = -8.5, HIGHLIGHT_SIGMA = 1.3, HIGHLIGHT_AMP = 6;

export const BTN_CX = 172.1;
export const BTN_CY = 908.9;
export const BTN_RING = 88.6;
export const BTN_RING_HALF = 2.7;
const TRACK_CY = 918;
const TRACK_END_X = 178;
const BODY_R = 86;
const BODY = [219.876, -27.784, -14.379, 0.952, -0.734, 13.346, 24.161, 7.595, 4.184, 31.043, -6.78, -32.405];

function bez(a, b, c, d, t) {
  const u = 1 - t;
  return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
}

/** Height of the blue line's centre at reference x. */
export function lineY(x) {
  const xm = x > CX ? 2 * CX - x : x;
  if (xm <= CURVE[0]) return LINE_TOP;
  if (xm >= CURVE[3]) return LINE_BOTTOM;
  let lo = 0, hi = 1;
  for (let i = 0; i < 22; i++) {
    const t = (lo + hi) * 0.5;
    if (bez(CURVE[0], CURVE[1], CURVE[2], CURVE[3], t) < xm) lo = t;
    else hi = t;
  }
  return bez(LINE_TOP, LINE_TOP, LINE_BOTTOM, LINE_BOTTOM, (lo + hi) * 0.5);
}

const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
const clamp255 = (v) => {
  const i = Math.round(v);
  return i < 0 ? 0 : i > 255 ? 255 : i;
};

function band(v, lo, hi, soft) {
  if (v < lo) return clamp01(1 - (lo - v) / soft);
  if (v > hi) return clamp01(1 - (v - hi) / soft);
  return 1;
}

function panel(xm, d) {
  let fi = xm / P.PANEL_STEP_X, fj = Math.max(0, d) / P.PANEL_STEP_D;
  if (fi < 0) fi = 0;
  if (fi > P.PANEL_NX - 1.001) fi = P.PANEL_NX - 1.001;
  if (fj > P.PANEL_ND - 1.001) fj = P.PANEL_ND - 1.001;
  const i = fi | 0, j = fj | 0;
  const ax = fi - i, ay = fj - j;
  const n = P.PANEL_NX;
  const c00 = P.PANEL[j * n + i], c10 = P.PANEL[j * n + i + 1];
  const c01 = P.PANEL[(j + 1) * n + i], c11 = P.PANEL[(j + 1) * n + i + 1];
  const w00 = (1 - ax) * (1 - ay), w10 = ax * (1 - ay), w01 = (1 - ax) * ay, w11 = ax * ay;
  const ch = (sh) => Math.round(((c00 >> sh) & 255) * w00 + ((c10 >> sh) & 255) * w10 + ((c01 >> sh) & 255) * w01 + ((c11 >> sh) & 255) * w11);
  return [ch(16), ch(8), ch(0)];
}

function trackDelta(y) {
  const i = Math.round(y) - P.TRACK_Y0;
  return i >= 0 && i < P.TRACK_DELTA.length ? P.TRACK_DELTA[i] : 0;
}

function socketDelta(xm, y, rightSide, tracks = true) {
  if (xm > 330 || y < 780) return 0;
  let delta = 0;
  if (!tracks) {
    // no grooves: just the seat round the button (snow and leaves themes)
  } else if (xm <= TRACK_END_X) {
    let dl = trackDelta(y);
    if (y < 870) dl *= clamp01((165 - xm) / 45);
    delta += dl;
  } else {
    const r = Math.hypot(xm - TRACK_END_X, y - TRACK_CY);
    if (r >= 98) delta += trackDelta(TRACK_CY + r);
    else if (y > TRACK_CY - 98) delta += -3;
  }
  const bx = xm - BTN_CX, by = y - BTN_CY;
  const rr = Math.hypot(bx, by);
  if (rr > 0) {
    const towardCentre = bx / rr;
    if (towardCentre > 0) delta += 9 * towardCentre * band(rr, 99, 111, 4);
    const realX = rightSide ? -bx : bx;
    const c = (realX + by) / (rr * 1.41421356);
    if (c > 0 && rr > BTN_RING) delta -= 18 * c * clamp01(1 - (rr - BTN_RING - 1) / 14);
  }
  return delta;
}

// ---- themes (WiiBase.java): classic keeps the measured greys, others map them onto the theme's colours ----

const mixRgb = (a, b, t) => {
  if (t <= 0) return a & 0xffffff;
  if (t >= 1) return b & 0xffffff;
  const ch = (sh) => Math.round(((a >> sh) & 255) + (((b >> sh) & 255) - ((a >> sh) & 255)) * t);
  return (ch(16) << 16) | (ch(8) << 8) | ch(0);
};
/** Classic with its own accent: drawn exactly as measured. */
export const untouched = (t) => !t || (t.classic && t.accent === 0x3fc0ec);

/** The whole backdrop as RGBA bytes, w x h screen pixels, in a theme's colours (null: classic). */
export function backdrop(w, h, theme = null) {
  const s = w / 1920;
  const recolour = !!theme && !theme.classic;
  const tracks = !theme || (theme.deco !== 'SNOW' && theme.deco !== 'LEAVES');
  const plain = untouched(theme);
  const lineR = plain ? LINE_R : (theme.accent >> 16) & 255;
  const lineG = plain ? LINE_G : (theme.accent >> 8) & 255;
  const lineB = plain ? LINE_B : theme.accent & 255;
  const out = new Uint8ClampedArray(w * h * 4);
  const colLine = new Float32Array(w), colCos = new Float32Array(w), colBase = new Float32Array(w);
  const colAmp = new Float32Array(w), colXm = new Float32Array(w), colRight = new Uint8Array(w);
  for (let x = 0; x < w; x++) {
    const rx = (x + 0.5) / s - 0.5;
    const ly = lineY(rx);
    const slope = lineY(rx + 0.5) - lineY(rx - 0.5);
    colLine[x] = ly;
    colCos[x] = 1 / Math.sqrt(1 + slope * slope);
    const fd = Math.abs(rx - CX) / 32;
    const i = Math.min(P.BG_BASE.length - 2, fd | 0);
    const a = Math.min(1, fd - i);
    colBase[x] = P.BG_BASE[i] * (1 - a) + P.BG_BASE[i + 1] * a;
    colAmp[x] = P.BG_AMP[i] * (1 - a) + P.BG_AMP[i + 1] * a;
    colRight[x] = rx > CX ? 1 : 0;
    colXm[x] = colRight[x] ? 2 * CX - rx : rx;
  }
  const period = P.STRIPE_PERIOD, phase = P.STRIPE_PHASE;
  const inv2s2 = 1 / (2 * P.STRIPE_SIGMA * P.STRIPE_SIGMA);
  const hl2 = 2 * HIGHLIGHT_SIGMA * HIGHLIGHT_SIGMA;
  for (let y = 0; y < h; y++) {
    const ry = (y + 0.5) / s - 0.5;
    let ph = ((ry - phase) / period) % 1;
    if (ph < 0) ph += 1;
    const dist = Math.min(ph, 1 - ph) * period;
    const k = Math.exp(-dist * dist * inv2s2);
    let o = y * w * 4;
    for (let x = 0; x < w; x++, o += 4) {
      const dY = ry - colLine[x];
      const dp = dY * colCos[x];
      let r, g, b;
      if (dp < 0) {
        const hd = dp - HIGHLIGHT_D;
        const v = colBase[x] + colAmp[x] * k + HIGHLIGHT_AMP * Math.exp((-hd * hd) / hl2);
        if (recolour) {
          const fy = clamp01(ry / LINE_TOP);
          const c = mixRgb(mixRgb(theme.bgTopDark, theme.bgBottomDark, fy),
            mixRgb(theme.bgTopLight, theme.bgBottomLight, fy), clamp01((v - 218) / 36));
          r = (c >> 16) & 255;
          g = (c >> 8) & 255;
          b = c & 255;
        } else {
          r = g = b = v;
        }
      } else {
        const c = panel(colXm[x], dY);
        const dl = socketDelta(colXm[x], ry, colRight[x] === 1, tracks);
        r = c[0] + dl;
        g = c[1] + dl;
        b = c[2] + dl;
        if (recolour) {
          const m = mixRgb(theme.panelDark, theme.panelLight, clamp01(((r + g + b) / 3 - 150) / 70));
          r = (m >> 16) & 255;
          g = (m >> 8) & 255;
          b = m & 255;
        }
      }
      const cov = clamp01((LINE_HALF - Math.abs(dp)) * s + 0.5);
      if (cov > 0) {
        r += (lineR - r) * cov;
        g += (lineG - g) * cov;
        b += (lineB - b) * cov;
      }
      out[o] = clamp255(r);
      out[o + 1] = clamp255(g);
      out[o + 2] = clamp255(b);
      out[o + 3] = 255;
    }
  }
  return out;
}

/** One round button's body and blue ring in a size x size square, transparent outside the ring. */
export function button(size, s, theme = null) {
  const plain = untouched(theme);
  const ringR = plain ? 0x4d : (theme.accent >> 16) & 255;
  const ringG = plain ? 0xbd : (theme.accent >> 8) & 255;
  const ringB = plain ? 0xe3 : theme.accent & 255;
  const out = new Uint8ClampedArray(size * size * 4);
  const c = size / 2;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const dx = (x + 0.5 - c) / s, dy = (y + 0.5 - c) / s;
      const r = Math.sqrt(dx * dx + dy * dy);
      const outer = clamp01((BTN_RING + BTN_RING_HALF - r) * s + 0.5);
      if (outer <= 0) continue;
      const nx = dx / BODY_R, ny = dy / BODY_R;
      const r2 = nx * nx + ny * ny;
      const k = BODY;
      const v = k[0] + k[1] * nx + k[2] * ny + k[3] * nx * nx + k[4] * ny * ny + k[5] * nx * ny
        + k[6] * nx * nx * nx + k[7] * ny * ny * ny + k[8] * nx * nx * ny + k[9] * nx * ny * ny
        + k[10] * r2 * r2 + k[11] * r2 * r2 * r2;
      let cr = v, cg = v, cb = v;
      if (theme && !theme.classic) {
        const m = mixRgb(theme.buttonDark, theme.buttonLight, clamp01((v - 175) / 75));
        cr = (m >> 16) & 255;
        cg = (m >> 8) & 255;
        cb = m & 255;
      }
      const ring = clamp01((BTN_RING_HALF - Math.abs(r - BTN_RING)) * s + 0.5);
      cr += (ringR - cr) * ring;
      cg += (ringG - cg) * ring;
      cb += (ringB - cb) * ring;
      const o = (y * size + x) * 4;
      out[o] = clamp255(cr);
      out[o + 1] = clamp255(cg);
      out[o + 2] = clamp255(cb);
      out[o + 3] = Math.round(outer * 255);
    }
  }
  return out;
}

function hash(x, y) {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return h ^ (h >>> 16);
}

/** The frosted grey an empty channel slot shows: darker at the top, faint horizontal grain. */
export function emptySlot(tw, th, s, theme = null) {
  const recolour = !!theme && !theme.classic;
  const out = new Uint8ClampedArray(tw * th * 4);
  for (let y = 0; y < th; y++) {
    const t = y / Math.max(1, th - 1);
    const base = 0xcf + (0xdf - 0xcf) * t * t;
    const ry = (y / s) | 0;
    const streak = (((hash(ry, 7) & 255) / 255) - 0.5) * 3;
    for (let x = 0; x < tw; x++) {
      const rx = (x / s) | 0;
      const n = (((hash(rx, ry) & 255) / 255) - 0.5) * 5;
      const v = clamp255(base + streak + n);
      const o = (y * tw + x) * 4;
      if (recolour) {
        const m = mixRgb(theme.slotDark, theme.slotLight, clamp01((v - 200) / 40));
        out[o] = (m >> 16) & 255;
        out[o + 1] = (m >> 8) & 255;
        out[o + 2] = m & 255;
      } else {
        out[o] = out[o + 1] = out[o + 2] = v;
      }
      out[o + 3] = 255;
    }
  }
  return out;
}
