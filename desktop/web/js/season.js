// The seasonal themes' scenery, ported from the Android launcher's SeasonDeco.java. Winter: pine trees and snow
// on the hills either side of the clock (the blue line's curves), snow falling in two layers, and the kids'
// scenes taking turns (sledding down the hills, a skater on the left pond, hockey on the right one with a fight
// every third game, a snowball fight). Fall: leaf piles on the hills and leaves tumbling down, behind the channels
// and a few in front. The trees, piles and ponds are baked into the backdrop; everything that moves is computed
// from the clock. Geometry in 1920 x 1080 reference px; s converts to screen px. The scenes stay silent here.
import { lineY } from './base.js';

/** An Android colour int (0xAARRGGBB) as a CSS colour; `alpha` (0..255) replaces its own, like Paint.setAlpha. */
export function argb(c, alpha) {
  const a = alpha === undefined ? (c >>> 24) & 255 : Math.max(0, Math.min(255, Math.round(alpha)));
  return `rgba(${(c >> 16) & 255},${(c >> 8) & 255},${c & 255},${(a / 255).toFixed(3)})`;
}

export function circle(c, x, y, r) {
  c.beginPath();
  c.arc(x, y, Math.max(0, r), 0, Math.PI * 2);
  c.fill();
}

/** Canvas.drawOval: the ellipse inside l, t, r, b. */
export function oval(c, l, t, r, b, stroke = false) {
  c.beginPath();
  c.ellipse((l + r) / 2, (t + b) / 2, Math.abs(r - l) / 2, Math.abs(b - t) / 2, 0, 0, Math.PI * 2);
  if (stroke) c.stroke();
  else c.fill();
}

export function rrect(c, l, t, r, b, rad, stroke = false) {
  c.beginPath();
  c.roundRect(l, t, r - l, b - t, rad);
  if (stroke) c.stroke();
  else c.fill();
}

export function line(c, x0, y0, x1, y1) {
  c.beginPath();
  c.moveTo(x0, y0);
  c.lineTo(x1, y1);
  c.stroke();
}

/** Canvas.drawArc(l, t, r, b, 180, 180, true): the top half of the oval as a filled wedge (a hat). */
function topHalf(c, l, t, r, b) {
  const cx = (l + r) / 2, cy = (t + b) / 2;
  c.beginPath();
  c.moveTo(cx, cy);
  c.ellipse(cx, cy, (r - l) / 2, (b - t) / 2, 0, Math.PI, Math.PI * 2);
  c.closePath();
  c.fill();
}

const rad = (deg) => (deg * Math.PI) / 180;

/** java.util.Random's nextFloat, so the baked scenery lands where it does on Android. */
export function javaRandom(seed) {
  const MUL_HI = 0x5, MUL_LO = 0xdeece66d;   // 0x5DEECE66D as high 16 / low 32 bits
  let hi = ((Math.floor(seed / 0x100000000) ^ MUL_HI) & 0xffff) >>> 0;
  let lo = ((seed >>> 0) ^ MUL_LO) >>> 0;
  const next = (bits) => {
    // seed = (seed * 0x5DEECE66D + 0xB) mod 2^48, in 16-bit pieces to stay exact.
    const a0 = lo & 0xffff, a1 = lo >>> 16, a2 = hi & 0xffff;
    const b0 = MUL_LO & 0xffff, b1 = MUL_LO >>> 16, b2 = MUL_HI;
    let r0 = a0 * b0 + 0xb;
    let r1 = a1 * b0 + a0 * b1 + Math.floor(r0 / 65536);
    r0 &= 0xffff;
    let r2 = a2 * b0 + a1 * b1 + a0 * b2 + Math.floor(r1 / 65536);
    r1 &= 0xffff;
    r2 &= 0xffff;
    lo = ((r1 << 16) | r0) >>> 0;
    hi = r2;
    // (seed >>> (48 - bits)) as an int
    return Math.floor((hi * 0x100000000 + lo) / Math.pow(2, 48 - bits));
  };
  return { nextFloat: () => next(24) / (1 << 24) };
}

// ---- baked scenery --------------------------------------------------------------------------

/** Pine trees with snow on them: small ones on the hills, tall ones in the side margins; and the ponds. */
export function bakeWinter(c, s) {
  bakePonds(c, s);
  const trees = [[30, 150], [100, 120], [520, 64], [590, 78], [665, 58]];
  for (const t of trees) {
    for (let side = 0; side < 2; side++) {
      const x = side === 0 ? t[0] : 1920 - t[0] + (t[0] < 200 ? 0 : 14);
      const h = side === 0 ? t[1] : t[1] * 0.9;
      const base = lineY(x) - 3;
      // A soft snow mound under each tree.
      c.fillStyle = '#fff';
      oval(c, (x - h * 0.42) * s, (base - h * 0.08) * s, (x + h * 0.42) * s, (base + h * 0.06) * s);
      tree(c, x, base, h, s);
    }
  }
}

function tree(c, x, base, h, s) {
  const w = h * 0.62;
  c.fillStyle = argb(0xff7a4e2d);
  c.fillRect((x - h * 0.05) * s, (base - h * 0.16) * s, h * 0.1 * s, h * 0.16 * s);
  const greens = [0xff2f6b4f, 0xff367a5a, 0xff3f8a66];
  for (let k = 0; k < 3; k++) {
    const top = base - h * (0.42 + k * 0.27) - h * 0.2, bottom = base - h * (0.12 + k * 0.25);
    const half = (w / 2) * (1 - k * 0.24);
    c.beginPath();
    c.moveTo(x * s, top * s);
    c.lineTo((x + half) * s, bottom * s);
    c.lineTo((x - half) * s, bottom * s);
    c.closePath();
    c.fillStyle = argb(greens[k]);
    c.fill();
    // Snow along the top of each tier.
    const cap = (bottom - top) * 0.38;
    c.beginPath();
    c.moveTo(x * s, top * s);
    c.lineTo((x + half * 0.42) * s, (top + cap) * s);
    c.lineTo((x + half * 0.14) * s, (top + cap * 0.8) * s);
    c.lineTo((x - half * 0.14) * s, (top + cap) * s);
    c.lineTo((x - half * 0.42) * s, (top + cap * 0.82) * s);
    c.closePath();
    c.fillStyle = argb(0xf2ffffff);
    c.fill();
  }
}

export const LEAF_COLOURS = [0xffc2410c, 0xffea580c, 0xffb45309, 0xffdc2626, 0xffca8a04];

/** Fall: little piles of leaves on the hills. */
export function bakeFall(c, s) {
  const r = javaRandom(23);
  for (const px of [60, 470, 640, 1280, 1450, 1860]) {
    for (let i = 0; i < 9; i++) {
      const x = px + (r.nextFloat() - 0.5) * 70;
      const y = lineY(x) - 4 - r.nextFloat() * 9;
      const len = 9 + r.nextFloat() * 7;
      c.save();
      c.translate(x * s, y * s);
      c.rotate(rad((r.nextFloat() - 0.5) * 70));
      c.fillStyle = argb(LEAF_COLOURS[i % LEAF_COLOURS.length]);
      oval(c, -len * s, -len * 0.45 * s, len * s, len * 0.45 * s);
      c.restore();
    }
  }
}

/** Beach: a wave above the line in the theme's accent colour. */
export function bakeBeach(c, s, accent) {
  c.save();
  c.lineCap = 'round';
  c.lineWidth = 4 * s;
  c.strokeStyle = argb(accent, 0x70);
  c.beginPath();
  for (let rx = 0; rx <= 1920; rx += 6) {
    const y = (lineY(rx) - 30 + 7 * Math.sin(rx / 52)) * s;
    if (rx === 0) c.moveTo(rx * s, y);
    else c.lineTo(rx * s, y);
  }
  c.stroke();
  c.restore();
}

// ---- the frozen ponds: a skater on the left, two hockey players on the right ------------------

const POND_L_X = 585, POND_L_Y = 990, POND_L_RX = 115, POND_L_RY = 38;
const POND_R_X = 1335, POND_R_Y = 992, POND_R_RX = 165, POND_R_RY = 48;

function bakePonds(c, s) {
  for (const [x, y, rx, ry] of [[POND_L_X, POND_L_Y, POND_L_RX, POND_L_RY], [POND_R_X, POND_R_Y, POND_R_RX, POND_R_RY]]) {
    c.fillStyle = '#fff';
    oval(c, (x - rx - 8) * s, (y - ry - 6) * s, (x + rx + 8) * s, (y + ry + 6) * s);
    c.fillStyle = argb(0xffc9e7f7);
    oval(c, (x - rx) * s, (y - ry) * s, (x + rx) * s, (y + ry) * s);
    c.lineWidth = 3 * s;
    c.strokeStyle = argb(0xff9ccbe6);
    oval(c, (x - rx) * s, (y - ry) * s, (x + rx) * s, (y + ry) * s, true);
    c.lineWidth = 2.5 * s;
    c.strokeStyle = argb(0xccffffff);
    line(c, (x - rx * 0.5) * s, (y - ry * 0.35) * s, (x - rx * 0.1) * s, (y - ry * 0.45) * s);
    line(c, (x + rx * 0.15) * s, (y + ry * 0.3) * s, (x + rx * 0.55) * s, (y + ry * 0.2) * s);
  }
  // Hockey goals at the right pond's ends, mouths facing each other.
  for (let side = -1; side <= 1; side += 2) net(c, s, POND_R_X + side * (POND_R_RX - 24), POND_R_Y + 6, side);
}

/** A hockey goal seen from the side and a little above; `back` is the side the net bulges toward. */
function net(c, s, x, y, back) {
  const h = 26, depth = 22, skew = 9;
  const posts = [[x + back * skew * 0.4, y - skew], [x, y]];
  c.fillStyle = argb(0x33000000);
  oval(c, (x - (back < 0 ? depth + 6 : 6)) * s, (y - 5) * s, (x + (back > 0 ? depth + 6 : 6)) * s, (y + 4) * s);
  // The netting across the back, between the two side panels.
  c.fillStyle = argb(0x80ffffff);
  c.beginPath();
  c.moveTo((x + back * depth) * s, y * s);
  c.lineTo((x + back * (depth + skew * 0.4)) * s, (y - skew) * s);
  c.lineTo((x + back * skew * 0.4) * s, (y - skew - h) * s);
  c.lineTo(x * s, (y - h) * s);
  c.closePath();
  c.fill();
  const panel = (px, py) => {
    c.beginPath();
    c.moveTo(px * s, py * s);
    c.lineTo(px * s, (py - h) * s);
    c.bezierCurveTo((px + back * depth * 0.7) * s, (py - h) * s, (px + back * depth) * s, (py - h * 0.45) * s,
      (px + back * depth) * s, py * s);
    c.closePath();
  };
  for (const [px, py] of posts) {
    panel(px, py);
    c.fillStyle = argb(0xb3ffffff);
    c.fill();
    // The mesh: a diamond grid clipped to the panel.
    c.save();
    c.clip();
    c.lineWidth = 0.9 * s;
    c.strokeStyle = argb(0x99788c9c);
    c.beginPath();
    for (let k = -h; k < depth + h; k += 5) {
      const bx = px + back * k;
      c.moveTo(bx * s, py * s);
      c.lineTo((bx + back * h) * s, (py - h) * s);
      c.moveTo(bx * s, (py - h) * s);
      c.lineTo((bx + back * h) * s, py * s);
    }
    c.stroke();
    c.restore();
    // The curved back frame of the panel.
    c.lineWidth = 1.8 * s;
    c.strokeStyle = argb(0xffc62828);
    c.beginPath();
    c.moveTo(px * s, (py - h) * s);
    c.bezierCurveTo((px + back * depth * 0.7) * s, (py - h) * s, (px + back * depth) * s, (py - h * 0.45) * s,
      (px + back * depth) * s, py * s);
    c.stroke();
  }
  c.lineCap = 'round';
  // White base bar along the ice, red posts and crossbar at the mouth.
  c.lineWidth = 2 * s;
  c.strokeStyle = argb(0xffeceff1);
  line(c, x * s, y * s, (x + back * depth) * s, y * s);
  line(c, (x + back * skew * 0.4) * s, (y - skew) * s, (x + back * (depth + skew * 0.4)) * s, (y - skew) * s);
  c.lineWidth = 3.2 * s;
  c.strokeStyle = argb(0xffe53935);
  line(c, x * s, y * s, x * s, (y - h) * s);
  line(c, (x + back * skew * 0.4) * s, (y - skew) * s, (x + back * skew * 0.4) * s, (y - skew - h) * s);
  line(c, x * s, (y - h) * s, (x + back * skew * 0.4) * s, (y - skew - h) * s);
  c.lineCap = 'butt';
}

// ---- moving parts ---------------------------------------------------------------------------

/** Falling snow; the back layer goes behind the channels, the front one (bigger, faster, fewer) over them. */
export function snow(c, s, t, front) {
  const n = front ? 22 : 70;
  const halo = argb(front ? 0x66a9c9e8 : 0x55a9c9e8), flake = argb(front ? 0xd9ffffff : 0xf0ffffff);
  for (let i = 0; i < n; i++) {
    const k = front ? i + 101 : i;
    const seedX = ((k * 7919) % 1920) / 1920, seedY = ((k * 104729) % 1080) / 1080;
    const speed = (front ? 60 : 22) + (k % 7) * (front ? 12 : 9);
    const size = (front ? 4 : 2.5) + (k % 5) * 1.3;
    const y = ((seedY * 1180 + t * speed) % 1180) - 50;
    const x = seedX * 1920 + Math.sin(t * 0.7 + k) * (front ? 34 : 22);
    c.fillStyle = halo;
    circle(c, x * s, y * s, (size + 1.2) * s);
    c.fillStyle = flake;
    circle(c, x * s, y * s, size * s);
  }
}

/** Leaves tumbling down and swaying; the front layer is a few over the channels. */
export function leaves(c, s, t, front) {
  const n = front ? 6 : 26;
  for (let i = 0; i < n; i++) {
    const k = front ? i + 61 : i;
    const seedX = ((k * 7919) % 1920) / 1920, seedY = ((k * 104729) % 1200) / 1200;
    const speed = (front ? 70 : 38) + (k % 6) * 8;
    const y = ((seedY * 1220 + t * speed) % 1220) - 70;
    const x = seedX * 1920 + 46 * Math.sin(t * 0.55 + k * 1.3);
    const len = (front ? 27 : 19) + (k % 4) * 4;
    c.save();
    c.translate(x * s, y * s);
    c.rotate(rad(t * (25 + (k % 5) * 12) * (k % 2 === 0 ? 1 : -1) + k * 47));
    // Tumbling: the leaf turns edge-on and back as it falls.
    c.scale(s * (0.45 + 0.55 * Math.abs(Math.cos(t * 1.4 + k))), s);
    c.beginPath();
    c.moveTo(-len, 0);
    c.quadraticCurveTo(0, -len * 0.62, len, 0);
    c.quadraticCurveTo(0, len * 0.62, -len, 0);
    c.closePath();
    c.fillStyle = argb(LEAF_COLOURS[k % LEAF_COLOURS.length]);
    c.fill();
    c.lineWidth = 1.3;
    c.strokeStyle = argb(0x73000000);
    line(c, -len * 1.15, 0, len * 0.9, 0);
    c.restore();
  }
}

// ---- Winter's kids take turns: one scene at a time, each walking in from the edge and back off ----------

const WALK_IN = 4;
const SKATE_PERIOD = (2 * Math.PI) / 0.5;
const SLIDE = 5, STAND = 0.7, WALK = 8, SIT = 0.6, CYCLE = SLIDE + STAND + WALK + SIT, CROSS = 12;
// The run ends where the line flattens, clear of the clock's AM/PM on the right-hand side.
const TOP = 150, BOTTOM = 700;
/** Riders and skaters are a third bigger than reference px, so they read across the room. */
const BIG = 1.35;
/** Sledding (two runs each, then off across the screen), skating (three figure-eights), hockey, snowballs. */
const SCENES = [WALK_IN + 0.6 + (5 + 0.7 + 8 + 0.6) + 5 + 0.7 + 12, 2 * WALK_IN + 3 * SKATE_PERIOD, 2 * WALK_IN + 32,
  2 * WALK_IN + 24];
const SCENES_TOTAL = SCENES.reduce((a, b) => a + b, 0);
const FIRST_THROW = 1.5, THROW_GAP = 3.5;
// The fight: they square up, drop their sticks, trade punches, then pick the sticks up and play on.
// It starts with Red's slapshot: wind-up, shot, the puck in Blue's net, Red celebrating and Blue fuming.
const SHOT_AT = 9.6, WIND = 0.6, SHOT_FLIGHT = 0.3, GOAL_FOR = 2.4;
const FIGHT_FROM = 13.2, STICKS_DOWN = 14.7, STICKS_UP = 24.5, FIGHT_TO = 26;

const floorMod = (a, m) => {
  const r = a % m;
  return r < 0 ? r + m : r;
};
const ramp = (q, from, to) => {
  const u = Math.max(0, Math.min(1, (q - from) / (to - from)));
  return u * u * (3 - 2 * u);
};
/** 0..1 across the walk in, then 1, then 1..0 across the walk out. */
const walk = (lt, d) => (lt < WALK_IN ? lt / WALK_IN : lt > d - WALK_IN ? (d - lt) / WALK_IN : 1);
const darker = (c) => (0xff000000 | ((((c >> 16) & 255) * 3 / 4) << 16) | ((((c >> 8) & 255) * 3 / 4) << 8)
  | (((c & 255) * 3) / 4)) >>> 0;

export function winterKids(c, s, t) {
  const round = Math.floor(t / SCENES_TOTAL);
  let lt = t % SCENES_TOTAL, k = 0;
  while (k < SCENES.length - 1 && lt >= SCENES[k]) lt -= SCENES[k++];
  const d = SCENES[k];
  c.save();
  if (k === 0) sledScene(c, s, lt, d);
  else if (k === 1) skateScene(c, s, lt, d);
  else if (k === 2) hockeyScene(c, s, lt, d, round);
  else snowballScene(c, s, lt, d);
  c.restore();
}

function sledScene(c, s, lt, d) {
  if (lt < WALK_IN) {
    // Walking in from the screen edge to the top of the hill, sled in tow.
    const x = -40 + (TOP + 40) * walk(lt, d);
    walkWithSled(c, s, x, false, true, 0xffe53935, 0xff1e88e5, lt);
    walkWithSled(c, s, 1920 - x, true, true, 0xff43a047, 0xfffdd835, lt);
    return;
  }
  const u = lt - WALK_IN, runs = SIT + CYCLE + SLIDE + STAND;
  if (u >= runs) {
    // After the last run they stroll on across the screen, passing each other, and off the far side.
    const f = (u - runs) / CROSS;
    const x = BOTTOM + (1960 - BOTTOM) * f;
    walkWithSled(c, s, x, false, true, 0xffe53935, 0xff1e88e5, lt);
    walkWithSled(c, s, 1920 - x, true, true, 0xff43a047, 0xfffdd835, lt);
    return;
  }
  // Sit down on the sled first, then run, walk back up, run again.
  const phase = u < SIT ? CYCLE - SIT + u : (u - SIT) % CYCLE;
  rider(c, s, phase, lt, false, 0xffe53935, 0xff1e88e5);
  rider(c, s, phase, lt, true, 0xff43a047, 0xfffdd835);
}

function rope(c) {
  c.lineWidth = 1.6;
  c.strokeStyle = argb(0xff6d4c41);
  line(c, -16, 0, 2, -24);
}

function walkWithSled(c, s, x, right, inward, coat, hat, t) {
  const y = lineY(Math.max(0, Math.min(1920, x))) - 3;
  c.save();
  c.translate(x * s, y * s);
  // Walking toward the middle when coming in, toward the edge when leaving.
  const facingRight = inward !== right;
  c.scale((facingRight ? s : -s) * BIG, s * BIG);
  sled(c, -46);
  rope(c);
  walker(c, coat, hat, t, true);
  c.restore();
}

/** A kid walking along the ground at (x, y), facing `facingRight`; `still` stands instead of stepping. */
function kid(c, s, x, y, facingRight, coat, hat, t, still) {
  c.save();
  c.translate(x * s, y * s);
  c.scale((facingRight ? s : -s) * BIG, s * BIG);
  walker(c, coat, hat, t, !still);
  c.restore();
}

function skateScene(c, s, lt, d) {
  const startX = POND_L_X - POND_L_RX * 0.68, y = POND_L_Y + 2;
  if (lt < WALK_IN || lt > d - WALK_IN) {
    const x = -40 + (startX + 40) * walk(lt, d);
    kid(c, s, x, y, lt < WALK_IN, 0xffec407a, 0xffffffff, lt, false);
    return;
  }
  skater(c, s, lt - WALK_IN);
}

function hockeyScene(c, s, lt, d, round) {
  if (lt < WALK_IN || lt > d - WALK_IN) {
    const f = walk(lt, d);
    const ax = 1960 + (POND_R_X - 70 - 1960) * f, bx = 1990 + (POND_R_X + 70 - 1990) * f;
    const inward = lt < WALK_IN;
    kid(c, s, ax, POND_R_Y, !inward, 0xffe53935, 0xffe53935, lt, false);
    kid(c, s, bx, POND_R_Y, !inward, 0xff1e88e5, 0xff1e88e5, lt + 0.4, false);
    return;
  }
  hockey(c, s, lt - WALK_IN, round % 3 === 2);
}

/** Two people on the snow beside the ponds throwing snowballs at each other over the date. */
function snowballScene(c, s, lt, d) {
  const ax0 = 448, bx0 = 1545, ground = 1012;
  if (lt < WALK_IN || lt > d - WALK_IN) {
    const f = walk(lt, d);
    const inward = lt < WALK_IN;
    const ax = -40 + (ax0 + 40) * f, bx = 1960 - (1960 - bx0) * f;
    kid(c, s, ax, ground, inward, 0xffff7043, 0xff5c6bc0, lt, false);
    kid(c, s, bx, ground, !inward, 0xff26a69a, 0xffffca28, lt, false);
    return;
  }
  // They take turns: each one crouches, scoops up snow, packs it, winds up and throws; the other flinches.
  const pt = lt - WALK_IN, flight = 1.3;
  const qa = floorMod(pt - FIRST_THROW, 2 * THROW_GAP), qb = floorMod(pt - FIRST_THROW - THROW_GAP, 2 * THROW_GAP);
  thrower(c, s, ax0, ground, true, 0xffff7043, 0xff5c6bc0, qa, pt);
  thrower(c, s, bx0, ground, false, 0xff26a69a, 0xffffca28, qb, pt);
  const sinceFirst = pt - FIRST_THROW;
  if (sinceFirst < 0) return;
  const n = Math.floor(sinceFirst / THROW_GAP);
  const since = sinceFirst - n * THROW_GAP;
  const fromLeft = n % 2 === 0;
  const ay = ground, by = ground;
  if (since < flight) {
    // The snowball arcs high over the date to the other one.
    const u = since / flight;
    const x0 = fromLeft ? ax0 + 14 : bx0 - 14, x1 = fromLeft ? bx0 - 6 : ax0 + 6;
    const x = x0 + (x1 - x0) * u;
    const y = ay - 74 + (by - 62 - (ay - 74)) * u - 300 * 4 * u * (1 - u);
    snowballAt(c, x * s, y * s, 9 * s, 2 * s);
  } else if (since < flight + 0.45) {
    // A puff of snow where it lands.
    const u = (since - flight) / 0.45;
    const hx = fromLeft ? bx0 : ax0, hy = (fromLeft ? by : ay) - 60;
    c.fillStyle = argb(0xffffffff, 255 * (1 - u));
    for (let i = 0; i < 6; i++) {
      const a = (i * Math.PI) / 3;
      circle(c, (hx + Math.cos(a) * 18 * u) * s, (hy + Math.sin(a) * 18 * u) * s, (6 - 3 * u) * s);
    }
  }
}

function snowballAt(c, x, y, r, stroke) {
  c.fillStyle = '#fff';
  circle(c, x, y, r);
  c.lineWidth = stroke;
  c.strokeStyle = argb(0xffb0c8dc);
  c.beginPath();
  c.arc(x, y, r, 0, Math.PI * 2);
  c.stroke();
}

/**
 * A snowball thrower with arms. `q` is the time since their last throw, in a 7 s round: idle, flinch when the
 * other one's snowball lands (4.8 s), crouch and scoop (5.1), stand and pack it (5.9), wind up (6.4) and throw.
 */
function thrower(c, s, x, y, facingRight, coat, hat, q, t) {
  const crouch = ramp(q, 5.1, 5.4) * (1 - ramp(q, 5.8, 6.1));
  const flinch = ramp(q, 4.8, 4.9) * (1 - ramp(q, 4.95, 5.15));
  // Arm angles in degrees from hanging straight down; positive swings forward, 180 is straight up.
  let front, back;
  const holding = q >= 5.55;
  if (q < 0.25) {
    front = 205 - 150 * ramp(q, 0, 0.25);
    back = 70;
  } else if (q < 0.7) {
    front = 55 - 40 * ramp(q, 0.25, 0.7);
    back = 70 - 85 * ramp(q, 0.25, 0.7);
  } else if (q < 5.1) {
    front = 15 + 120 * flinch;
    back = -15 + 150 * flinch;
  } else if (q < 6.1) {
    const scoop = crouch;
    front = 15 + 45 * scoop + 50 * ramp(q, 5.8, 6.1);
    back = -15 + 75 * scoop + 30 * ramp(q, 5.8, 6.1);
  } else if (q < 6.4) {
    front = 110;
    back = 90;
  } else {
    front = 110 + 95 * ramp(q, 6.4, 6.9);
    back = 90 - 10 * ramp(q, 6.4, 6.9);
  }
  const lean = 38 * crouch - 12 * flinch + (q > 6.4 ? -8 * ramp(q, 6.4, 6.9) : 0)
    + (q < 0.5 ? 14 * (1 - ramp(q, 0.15, 0.5)) * ramp(q, 0, 0.1) : 0);
  c.save();
  c.translate(x * s, y * s);
  c.scale((facingRight ? s : -s) * BIG, s * BIG);
  const hipY = -18 + 7 * crouch;
  // Legs, knees bending forward in the crouch.
  c.lineCap = 'round';
  c.lineWidth = 5;
  c.strokeStyle = argb(0xff283593);
  const kneeX = 7 * crouch, kneeY = hipY + (0 - hipY) * 0.5 - 2 * crouch;
  line(c, 0, hipY, kneeX + 2, kneeY);
  line(c, kneeX + 2, kneeY, 5, 0);
  line(c, 0, hipY, kneeX - 3, kneeY);
  line(c, kneeX - 3, kneeY, -5, 0);
  c.translate(0, hipY);
  c.rotate(rad(lean));
  c.translate(0, -hipY);
  const shX = 1, shY = hipY - 20;
  const sleeve = darker(coat);
  arm(c, shX, shY, back, sleeve, hat);
  c.fillStyle = argb(coat);
  rrect(c, -8, hipY - 24, 8, hipY + 2, 7);
  scarf(c, hat, 0, hipY - 23, t);
  head(c, hat, 0, hipY - 32);
  const hand = arm(c, shX, shY, front, sleeve, hat);
  if (holding) {
    // The snowball grows as they scoop and pack it.
    const r = 2.5 + 3.5 * ramp(q, 5.55, 6.3);
    if (q >= 6.1 && q < 6.4) snowballAt(c, hand[0] + 2, hand[1] - 2, r, 1.4);
    else snowballAt(c, hand[0], hand[1] - 3, r, 1.4);
  }
  c.restore();
}

/** Draws one arm from the shoulder and returns where the mitten is. */
function arm(c, sx, sy, degrees, sleeve, mitten) {
  const a = rad(degrees);
  const ex = sx + 14 * Math.sin(a), ey = sy + 14 * Math.cos(a);
  c.lineCap = 'round';
  c.lineWidth = 4.5;
  c.strokeStyle = argb(sleeve);
  line(c, sx, sy, ex, ey);
  c.fillStyle = argb(mitten);
  circle(c, ex, ey, 3.4);
  return [ex, ey];
}

function rider(c, s, phase, t, right, coat, hat) {
  let x;
  const sliding = phase < SLIDE;
  if (sliding) {
    const u = phase / SLIDE;
    x = TOP + (BOTTOM - TOP) * u * u * (3 - 2 * u);   // slow at the top, gliding to a stop on the flat
  } else if (phase < SLIDE + STAND) {
    x = BOTTOM;
  } else if (phase < SLIDE + STAND + WALK) {
    x = BOTTOM - ((BOTTOM - TOP) * (phase - SLIDE - STAND)) / WALK;
  } else {
    x = TOP;
  }
  const y = lineY(x) - 3;
  const slope = (lineY(x + 3) - lineY(x - 3)) / 6;
  const sx = right ? 1920 - x : x;
  c.save();
  c.translate(sx * s, y * s);
  c.scale((right ? -s : s) * BIG, s * BIG);
  c.rotate(Math.atan(slope));
  if (sliding) {
    const u = phase / SLIDE;
    spray(c, Math.min(1, u * 3) * (1 - u));
    sled(c, 0);
    sitter(c, coat, hat, t);
  } else {
    const walking = phase >= SLIDE + STAND && phase < SLIDE + STAND + WALK;
    // Facing uphill to walk back up; the sled waits downhill on its rope.
    c.scale(-1, 1);
    sled(c, -46);
    rope(c);
    walker(c, coat, hat, walking ? t : 0, walking);
  }
  c.restore();
}

function spray(c, amount) {
  for (let i = 0; i < 5; i++) {
    c.fillStyle = argb(0xffffffff, 255 * (0.7 - i * 0.12) * amount);
    circle(c, -30 - i * 9, -3 - (i % 2) * 4, 3.2 - i * 0.4);
  }
}

/** The sled, its runner curling up at the front; `dx` moves it along the ground. */
function sled(c, dx) {
  c.lineWidth = 2.6;
  c.lineCap = 'round';
  c.strokeStyle = argb(0xff5d4037);
  c.beginPath();
  c.moveTo(dx - 28, 0);
  c.lineTo(dx + 22, 0);
  c.quadraticCurveTo(dx + 32, 0, dx + 30, -9);
  c.stroke();
  c.fillStyle = argb(0xffa0522d);
  rrect(c, dx - 26, -9, dx + 22, -4, 2);
}

/** Sitting on the sled: legs forward, coat, head, hat with a bobble, scarf streaming back. */
function sitter(c, coat, hat, t) {
  c.fillStyle = argb(0xff283593);
  rrect(c, -4, -15, 20, -8, 3);
  c.fillStyle = argb(coat);
  rrect(c, -18, -33, 2, -9, 7);
  scarf(c, hat, -6, -33, t);
  head(c, hat, -7, -41);
}

/** Standing and walking: legs stepping, coat, an arm forward. */
function walker(c, coat, hat, t, walking) {
  const step = walking ? Math.sin(t * 6) : 0;
  const bob = walking ? Math.abs(step) * 1.5 : 0;
  c.lineCap = 'round';
  c.lineWidth = 5;
  c.strokeStyle = argb(0xff283593);
  line(c, 0, -18 - bob, 6 * step, 0);
  line(c, 0, -18 - bob, -6 * step, 0);
  c.fillStyle = argb(coat);
  rrect(c, -8, -42 - bob, 8, -16 - bob, 7);
  c.lineWidth = 4;
  c.strokeStyle = argb(coat);
  line(c, 4, -36 - bob, 2, -24 - bob);
  scarf(c, hat, 0, -41 - bob, t);
  head(c, hat, 0, -50 - bob);
}

function scarf(c, colour, x, y, t) {
  const flutter = 3 * Math.sin(t * 14);
  c.lineWidth = 4;
  c.strokeStyle = argb(colour);
  c.beginPath();
  c.moveTo(x, y);
  c.quadraticCurveTo(x - 12, y - 2 + flutter, x - 22, y + 2 - flutter);
  c.stroke();
}

function head(c, hat, x, y) {
  c.fillStyle = argb(0xfff2c9a0);
  circle(c, x, y, 8);
  c.fillStyle = argb(hat);
  topHalf(c, x - 8.5, y - 10, x + 8.5, y + 8);
  c.fillStyle = '#fff';
  circle(c, x, y - 10, 3.2);
}

/** A skater gliding figure-eights on the left pond, arms out, ponytail swinging. */
function skater(c, s, t) {
  const a = t * 0.5 - Math.PI / 2;   // starts at the pond's left end
  const x = POND_L_X + POND_L_RX * 0.68 * Math.sin(a);
  const y = POND_L_Y + 2 + POND_L_RY * 0.45 * Math.sin(2 * a);
  const right = Math.cos(a) > 0;
  c.save();
  c.translate(x * s, y * s);
  c.scale((right ? s : -s) * BIG, s * BIG);
  c.rotate(rad(-8));
  c.lineCap = 'round';
  // Skates and legs, one leg trailing.
  c.lineWidth = 2;
  c.strokeStyle = argb(0xff37474f);
  line(c, -4, 0, 6, 0);
  line(c, -22, -6, -12, -5);
  c.lineWidth = 4.5;
  c.strokeStyle = argb(0xfff8bbd0);
  line(c, 0, -16, 1, -1);
  line(c, -2, -16, -17, -6);
  // Dress.
  c.fillStyle = argb(0xffec407a);
  c.beginPath();
  c.moveTo(-6, -38);
  c.lineTo(5, -38);
  c.lineTo(10, -16);
  c.lineTo(-12, -16);
  c.closePath();
  c.fill();
  // Arms out.
  c.lineWidth = 3.5;
  c.strokeStyle = argb(0xfff2c9a0);
  line(c, -1, -34, 16, -40);
  line(c, -1, -34, -18, -30);
  // Head, hair and a swinging ponytail.
  c.fillStyle = argb(0xfff2c9a0);
  circle(c, 0, -46, 7.5);
  c.fillStyle = argb(0xff6d4c41);
  topHalf(c, -8, -54.5, 8, -40);
  const swing = 4 * Math.sin(t * 5);
  oval(c, -16, -52 + swing, -6, -44 + swing);
  c.restore();
}

const playerX = (t, b) => POND_R_X + (b ? 70 : -70) + 18 * Math.sin(t * 0.8);
const playerY = (t, b) => POND_R_Y + 6 * Math.sin(t * (b ? 1.3 : 1.1));

/** Two hockey players on the right pond passing the puck back and forth; `fight` is the easter egg. */
function hockey(c, s, t, fight) {
  let ax = playerX(t, false), ay = playerY(t, false), bx = playerX(t, true), by = playerY(t, true);
  // In a fight round, play stops at the slapshot and carries on from Red's stick after the fight.
  const pt = !fight || t < SHOT_AT ? t : t > FIGHT_TO ? t - (FIGHT_TO - SHOT_AT) : SHOT_AT;
  const pax = playerX(pt, false), pay = playerY(pt, false), pbx = playerX(pt, true), pby = playerY(pt, true);
  // The puck travels A -> B -> A, slowing as it reaches each stick.
  const ph = (pt % 3.2) / 1.6;
  let u = ph < 1 ? ph : 2 - ph;
  u = u * u * (3 - 2 * u);
  const fromX = pax + 16, toX = pbx - 16;
  let px = fromX + (toX - fromX) * u, py = pay + (pby - pay) * u + 2;
  let windA = 0, fumeB = 0, goal = false;
  if (fight && t >= SHOT_AT && t <= FIGHT_TO) {
    const netX = POND_R_X + POND_R_RX - 30, netY = POND_R_Y + 3;
    const shot = t - SHOT_AT;
    if (shot < WIND) {
      windA = shot / WIND;   // stick drawn back, puck on the blade
      px = ax + 16;
      py = ay + 2;
    } else if (shot < WIND + SHOT_FLIGHT) {
      const k = (shot - WIND) / SHOT_FLIGHT;   // the shot, past Blue into the net
      windA = 1 - k * 1.6;
      px = ax + 16 + (netX - ax - 16) * k;
      py = ay + 2 + (netY - ay - 2) * k;
    } else if (t < STICKS_UP) {
      px = netX;
      py = netY;
      goal = shot < WIND + SHOT_FLIGHT + GOAL_FOR;
      windA = goal ? -0.6 : 0;   // stick up in the air to celebrate
    } else {
      // After the fight Blue fishes the puck out and passes it back to Red.
      const k = (t - STICKS_UP) / (FIGHT_TO - STICKS_UP);
      px = netX + (ax + 16 - netX) * k;
      py = netY + (ay + 2 - netY) * k;
    }
    fumeB = ramp(shot, WIND + SHOT_FLIGHT, WIND + SHOT_FLIGHT + 0.5);
  }
  let f = 0, punchA = 0, punchB = 0, sticks = true;
  if (fight && t > FIGHT_FROM && t < FIGHT_TO) {
    f = t < FIGHT_FROM + 1.5 ? (t - FIGHT_FROM) / 1.5 : t > FIGHT_TO - 1.5 ? (FIGHT_TO - t) / 1.5 : 1;
    f = f * f * (3 - 2 * f);
    sticks = t < STICKS_DOWN || t > STICKS_UP;
    const midY = POND_R_Y + 2;
    // Square up nose to nose, shoving back and forth a little as the punches land.
    const shove = t > STICKS_DOWN + 0.4 && t < STICKS_UP - 0.4 ? 7 * Math.sin(t * 1.7) : 0;
    ax += (POND_R_X - 21 + shove - ax) * f;
    bx += (POND_R_X + 21 + shove - bx) * f;
    ay += (midY - ay) * f;
    by += (midY - by) * f;
    if (!sticks) {
      // Sticks lie on the ice behind each of them.
      dropStick(c, s, POND_R_X - 82, POND_R_Y + 10, 0.25);
      dropStick(c, s, POND_R_X + 76, POND_R_Y + 16, -0.2);
      if (t > STICKS_DOWN + 0.6 && t < STICKS_UP - 0.6) {
        punchA = Math.max(0, Math.sin(t * 8));
        punchB = Math.max(0, Math.sin(t * 8 + Math.PI));
      }
    }
  }
  player(c, s, ax, ay, true, 0xffe53935, t, sticks, punchA, f, windA);
  player(c, s, bx, by, false, 0xff1e88e5, t + 1, sticks, punchB, Math.max(f, fumeB), 0);
  c.fillStyle = argb(0xff212121);
  oval(c, (px - 4.5) * s, (py - 2) * s, (px + 4.5) * s, (py + 2) * s);
  if (goal) {
    // GOAL! over the net, popping in.
    const pop = Math.min(1, (t - SHOT_AT - WIND - SHOT_FLIGHT) / 0.25);
    c.font = `bold ${26 * s * (0.6 + 0.4 * pop)}px Roboto, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif`;
    c.textAlign = 'center';
    const gx = (POND_R_X + POND_R_RX - 40) * s, gy = (POND_R_Y - 58) * s;
    c.lineWidth = 5 * s;
    c.lineJoin = 'round';
    c.strokeStyle = '#fff';
    c.strokeText('GOAL!', gx, gy);
    c.fillStyle = argb(0xffe53935);
    c.fillText('GOAL!', gx, gy);
    c.textAlign = 'left';
  }
  // A little burst where a punch lands.
  const hit = Math.max(punchA, punchB);
  if (hit > 0.85) {
    const hx = (ax + bx) / 2 + (punchA > punchB ? 10 : -10), hy = (ay + by) / 2 - 66;
    c.lineCap = 'round';
    c.lineWidth = 2.4 * s;
    c.strokeStyle = argb(0xffffeb3b);
    for (let i = 0; i < 6; i++) {
      const a = (i * Math.PI) / 3 + t * 3;
      const r0 = 5, r1 = 5 + (9 * (hit - 0.85)) / 0.15;
      line(c, (hx + r0 * Math.cos(a)) * s, (hy + r0 * Math.sin(a)) * s, (hx + r1 * Math.cos(a)) * s, (hy + r1 * Math.sin(a)) * s);
    }
  }
}

function dropStick(c, s, x, y, tilt) {
  c.lineCap = 'round';
  c.lineWidth = 2.4 * s * BIG;
  c.strokeStyle = argb(0xff8d6e63);
  const dx = 22 * BIG, dy = 22 * BIG * tilt;
  line(c, (x - dx) * s, (y - dy) * s, (x + dx) * s, (y + dy) * s);
  line(c, (x + dx) * s, (y + dy) * s, (x + dx + 7) * s, (y + dy - 3) * s);
}

/** One player. Without the stick their fists are up: `punch` 0..1 throws the front fist; `anger` flushes the face. */
function player(c, s, x, y, facingRight, jersey, t, stick, punch, anger, wind) {
  c.save();
  c.translate(x * s, y * s);
  c.scale((facingRight ? s : -s) * BIG, s * BIG);
  let lean = 3 * Math.sin(t * 2);
  c.lineCap = 'round';
  c.lineWidth = 2;
  c.strokeStyle = argb(0xff37474f);
  line(c, -8, 0, 2, 0);
  line(c, 2, 0, 12, 0);
  c.lineWidth = 5;
  c.strokeStyle = argb(0xff263238);
  line(c, -2, -16, -4, -1);
  line(c, 3, -16, 7, -1);
  if (stick) {
    // Stick from the hands down to the ice in front; `wind` swings it back for a slapshot (or up in the air).
    if (wind !== 0) lean = 0;
    c.save();
    c.translate(4 + lean, -30);
    c.rotate(rad(wind * 120));
    c.translate(-(4 + lean), 30);
    c.lineWidth = 2.4;
    c.strokeStyle = argb(0xff8d6e63);
    line(c, 4 + lean, -30, 16, -2);
    line(c, 16, -2, 22, -2);
    c.restore();
  } else {
    lean = 2 * punch;
  }
  c.fillStyle = argb(jersey);
  rrect(c, -8 + lean * 0.3, -40, 9 + lean * 0.3, -15, 6);
  if (!stick) {
    // Guard fist held up by the chin, the other one jabbing forward.
    c.lineWidth = 4.5;
    c.strokeStyle = argb(jersey);
    line(c, 2, -35, 9, -42);
    const fx = 10 + 14 * punch, fy = -40 - 4 * punch;
    line(c, 4, -34, fx, fy);
    c.fillStyle = argb(0xfff2c9a0);
    circle(c, 10, -43, 3.4);
    circle(c, fx + 1, fy, 3.6);
  }
  let skin = 0xfff2c9a0;
  if (anger > 0) {
    const g = Math.floor(0xc9 - 0x50 * anger), b = Math.floor(0xa0 - 0x50 * anger);
    skin = (0xff000000 | (0xf2 << 16) | (g << 8) | b) >>> 0;
  }
  c.fillStyle = argb(skin);
  circle(c, 1 + lean * 0.4, -47, 7.5);
  c.fillStyle = argb(jersey);
  topHalf(c, -7 + lean * 0.4, -56, 9 + lean * 0.4, -40);
  if (anger > 0.5) {
    // An angry brow.
    c.lineWidth = 1.6;
    c.strokeStyle = argb(0xff3e2723);
    line(c, 2 + lean * 0.4, -50, 7 + lean * 0.4, -48);
  }
  c.restore();
}
