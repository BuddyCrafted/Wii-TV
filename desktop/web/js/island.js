// The Island village theme, ported from the Android launcher's IslandScene.java: a cosy village look of our own
// (no game's characters, logos or art). Sky with drifting clouds, rolling grass hills with flowers and apple
// trees, channels on cream signboards with a wooden edge, the clock on a wooden sign, round cream buttons, and two
// butterflies over the grass. The scenery is baked once; clouds and butterflies are computed from the clock each
// frame. Geometry in 1920 x 1080 reference px; s converts to screen px.
import { BTN_RING } from './base.js';
import { argb, circle, oval, rrect, line, javaRandom } from './season.js';

const CREAM = 0xfffff9ea, CREAM_EDGE = 0xffd9bf8a, CREAM_RIM = 0xffe6d3a8, WOOD_DARK = 0xff8b5a2b,
  SIGN_TEXT = 0xfffff8ec;
/** The clock's wooden sign, centred under the channels. */
const SIGN_L = 650, SIGN_T = 828, SIGN_R = 1270, SIGN_B = 982;

// ---- baked scenery --------------------------------------------------------------------------

/** The whole backdrop (in place of the stripes, the line and the panel), drawn into c at w x h. */
export function backdrop(c, w, h, s) {
  const sky = c.createLinearGradient(0, 0, 0, 800 * s);
  sky.addColorStop(0, argb(0xff67bfef));
  sky.addColorStop(1, argb(0xffc9ebfb));
  c.fillStyle = sky;
  c.fillRect(0, 0, w, h);
  hill(c, s, 792, 22, 260, 0.3, 0xff9cdb7b);
  hill(c, s, 846, 18, 190, 1.7, 0xff76c95b);
  // A lighter rim along the near hill, where the sun catches it.
  c.lineWidth = 5 * s;
  c.strokeStyle = argb(0x66c8f2a8);
  c.beginPath();
  for (let x = 0; x <= 1920; x += 12) {
    const y = (846 + 18 * Math.sin(x / 190 + 1.7)) * s;
    if (x === 0) c.moveTo(0, y);
    else c.lineTo(x * s, y);
  }
  c.stroke();
  flowers(c, s);
  // In the open grass, clear of the channels above, the buttons and the clock's sign.
  tree(c, s, 545, 955, 0.6);
  tree(c, s, 1395, 960, 0.56);
  tree(c, s, 1530, 945, 0.66);
  clockSign(c, s);
}

function hill(c, s, y0, amp, period, phase, colour) {
  c.beginPath();
  c.moveTo(0, 1080 * s);
  for (let x = 0; x <= 1920; x += 12) c.lineTo(x * s, (y0 + amp * Math.sin(x / period + phase)) * s);
  c.lineTo(1920 * s, 1080 * s);
  c.closePath();
  c.fillStyle = argb(colour);
  c.fill();
}

function flowers(c, s) {
  const rnd = javaRandom(5);
  const petals = [0xffff9ec4, 0xffffffff, 0xffffe066, 0xffffb3d9, 0xffb9e3ff];
  for (let i = 0; i < 34; i++) {
    const x = rnd.nextFloat() * 1920, y = 880 + rnd.nextFloat() * 180;
    if (x > SIGN_L - 20 && x < SIGN_R + 20 && y < SIGN_B + 30) continue;
    const rr = 5 + rnd.nextFloat() * 3;
    c.fillStyle = argb(petals[i % petals.length]);
    for (let k = 0; k < 5; k++) {
      const a = (k * Math.PI * 2) / 5;
      circle(c, (x + Math.cos(a) * rr) * s, (y + Math.sin(a) * rr) * s, rr * 0.75 * s);
    }
    c.fillStyle = argb(i % 2 === 0 ? 0xffffc233 : 0xfff08a24);
    circle(c, x * s, y * s, rr * 0.6 * s);
  }
}

function tree(c, s, x, base, k) {
  c.fillStyle = argb(0xff8b5a2b);
  rrect(c, (x - 14 * k) * s, (base - 95 * k) * s, (x + 14 * k) * s, base * s, 6 * s);
  const cy = base - 140 * k;
  const g = c.createRadialGradient((x - 25 * k) * s, (cy - 30 * k) * s, 0, (x - 25 * k) * s, (cy - 30 * k) * s, 95 * k * s);
  g.addColorStop(0, argb(0xff6ccb5f));
  g.addColorStop(1, argb(0xff3e9e45));
  c.fillStyle = g;
  circle(c, x * s, cy * s, 72 * k * s);
  circle(c, (x - 52 * k) * s, (cy + 18 * k) * s, 48 * k * s);
  circle(c, (x + 54 * k) * s, (cy + 16 * k) * s, 50 * k * s);
  for (const a of [[-30, -20], [22, -38], [40, 10], [-50, 28], [5, 22]]) {
    c.fillStyle = argb(0xffe53935);
    circle(c, (x + a[0] * k) * s, (cy + a[1] * k) * s, 9 * k * s);
    c.fillStyle = argb(0xb3ffffff);
    circle(c, (x + a[0] * k - 3 * k) * s, (cy + a[1] * k - 3 * k) * s, 2.6 * k * s);
  }
}

/** The clock's wooden sign on two posts. */
function clockSign(c, s) {
  c.fillStyle = argb(WOOD_DARK);
  c.fillRect((SIGN_L + 70) * s, (SIGN_B - 10) * s, 26 * s, (1040 - SIGN_B + 10) * s);
  c.fillRect((SIGN_R - 96) * s, (SIGN_B - 10) * s, 26 * s, (1040 - SIGN_B + 10) * s);
  c.fillStyle = argb(0x33000000);
  rrect(c, (SIGN_L + 4) * s, (SIGN_T + 10) * s, (SIGN_R + 4) * s, (SIGN_B + 10) * s, 26 * s);
  const wood = c.createLinearGradient(0, SIGN_T * s, 0, SIGN_B * s);
  wood.addColorStop(0, argb(0xffd69455));
  wood.addColorStop(1, argb(0xffb8763a));
  c.fillStyle = wood;
  rrect(c, SIGN_L * s, SIGN_T * s, SIGN_R * s, SIGN_B * s, 26 * s);
  // Wood grain.
  c.lineCap = 'round';
  c.lineWidth = 3 * s;
  c.strokeStyle = argb(0x339c5e28);
  for (let i = 0; i < 4; i++) {
    const y = SIGN_T + 30 + i * 32;
    line(c, (SIGN_L + 40 + (i % 2) * 60) * s, y * s, (SIGN_R - 60 - (i % 3) * 40) * s, y * s);
  }
  c.lineCap = 'butt';
  c.lineWidth = 6 * s;
  c.strokeStyle = argb(WOOD_DARK);
  rrect(c, SIGN_L * s, SIGN_T * s, SIGN_R * s, SIGN_B * s, 26 * s, true);
}

/** A round cream button body, size x size, for the WiiOS and envelope buttons. */
export function button(c, size, s) {
  const cx = size / 2, rr = BTN_RING * s;
  c.fillStyle = argb(0x33000000);
  circle(c, cx + 3 * s, cx + 6 * s, rr);
  const g = c.createLinearGradient(0, cx - rr, 0, cx + rr);
  g.addColorStop(0, argb(0xfffffcf2));
  g.addColorStop(1, argb(0xfff0e0bc));
  c.fillStyle = g;
  circle(c, cx, cx, rr);
  c.lineWidth = 5 * s;
  c.strokeStyle = argb(CREAM_EDGE);
  c.beginPath();
  c.arc(cx, cx, rr - 2.5 * s, 0, Math.PI * 2);
  c.stroke();
}

// ---- per frame ------------------------------------------------------------------------------

/** Clouds drifting slowly to the right across the sky. */
export function clouds(c, s, t) {
  c.fillStyle = argb(0xf2ffffff);
  for (let i = 0; i < 6; i++) {
    const span = 1920 + 500;
    const x = ((i * 431 + t * (7 + i * 2.2)) % span) - 250;
    const y = 45 + ((i * 157) % 5) * 46;
    const k = 0.8 + (i % 3) * 0.25;
    circle(c, x * s, y * s, 34 * k * s);
    circle(c, (x + 40 * k) * s, (y - 14 * k) * s, 42 * k * s);
    circle(c, (x + 84 * k) * s, y * s, 32 * k * s);
    rrect(c, (x - 30 * k) * s, y * s, (x + 112 * k) * s, (y + 30 * k) * s, 15 * k * s);
  }
}

/** Two butterflies fluttering over the grass. */
export function butterflies(c, s, t) {
  butterfly(c, s, t, 470, 832, 0xffff8fc7, 0);
  butterfly(c, s, t, 1420, 846, 0xffffd54f, 2.4);
}

function butterfly(c, s, t, cx, cy, colour, off) {
  const x = cx + 90 * Math.sin(t * 0.33 + off);
  const y = cy + 26 * Math.sin(t * 0.9 + off * 1.7);
  const flap = 0.25 + 0.75 * Math.abs(Math.sin(t * 9 + off));
  c.save();
  c.translate(x * s, y * s);
  c.scale(s, s);
  c.fillStyle = argb(colour);
  for (let side = -1; side <= 1; side += 2) {
    oval(c, side < 0 ? -15 * flap : 0, -12, side < 0 ? 0 : 15 * flap, 3);
    oval(c, side < 0 ? -11 * flap : 0, -1, side < 0 ? 0 : 11 * flap, 11);
  }
  c.fillStyle = argb(0xff5d4037);
  rrect(c, -1.6, -11, 1.6, 10, 1.6);
  c.restore();
}

const innerInset = (s) => 12 * s;
/** Size of a sign's face, for the channel art. */
export const innerW = (w, s) => Math.max(1, Math.round(w - 2 * innerInset(s)));
export const innerH = (h, s) => Math.max(1, Math.round(h - 10 * s - 2 * innerInset(s)));

/**
 * One channel as a cream signboard with a wooden edge, at the origin, w x h; `art` (a canvas at the inner size)
 * fills its face, null is an empty sign. `lift` is the focus glow (0..1), `accent` an 0xAARRGGBB colour.
 */
export function sign(c, s, w, h, art, lift, accent) {
  const rad = 30 * s, edge = 10 * s;
  if (!art) {
    c.fillStyle = argb(0xb3fff9ea);
    rrect(c, 0, 0, w, h - edge, rad);
    c.lineWidth = 3 * s;
    c.strokeStyle = argb(0x99e6d3a8);
    rrect(c, 0, 0, w, h - edge, rad, true);
    return;
  }
  if (lift > 0.01) {
    for (let k = 3; k >= 1; k--) {
      c.strokeStyle = argb(accent, (lift * 70) / k);
      c.lineWidth = (8 + k * 6) * s;
      rrect(c, -4 * s, -4 * s, w + 4 * s, h + 2 * s, rad + 4 * s, true);
    }
  }
  c.fillStyle = argb(CREAM_EDGE);
  rrect(c, 0, edge, w, h, rad);
  c.fillStyle = argb(CREAM);
  rrect(c, 0, 0, w, h - edge, rad);
  const inset = innerInset(s);
  c.save();
  c.beginPath();
  c.roundRect(inset, inset, w - 2 * inset, h - edge - 2 * inset, rad - inset * 0.6);
  c.clip();
  c.drawImage(art, inset, inset);
  c.restore();
  c.lineWidth = 3 * s;
  c.strokeStyle = argb(CREAM_RIM);
  rrect(c, 0, 0, w, h - edge, rad, true);
  if (lift > 0.01) {
    c.lineWidth = 6 * s;
    c.strokeStyle = argb(accent, 255 * Math.min(1, lift));
    rrect(c, -4 * s, -4 * s, w + 4 * s, h + 2 * s, rad + 4 * s, true);
  }
}

/** The time and date on the wooden sign. */
export function clock(c, s, font, time, ampm, date) {
  const cx = (SIGN_L + SIGN_R) / 2;
  const big = `bold ${78 * s}px ${font}`, small = `bold ${36 * s}px ${font}`;
  c.font = big;
  const tw = c.measureText(time).width;
  c.font = small;
  const aw = ampm ? c.measureText(ampm).width + 10 * s : 0;
  const left = cx * s - (tw + aw) / 2;
  c.textAlign = 'left';
  c.font = big;
  c.fillStyle = argb(0x55000000);
  c.fillText(time, left + 2 * s, 912 * s + 3 * s);
  c.fillStyle = argb(SIGN_TEXT);
  c.fillText(time, left, 912 * s);
  if (ampm) {
    c.font = small;
    c.fillText(ampm, left + tw + 10 * s, 912 * s);
  }
  c.textAlign = 'center';
  c.font = small;
  c.fillStyle = argb(0x55000000);
  c.fillText(date, cx * s + 2 * s, 958 * s + 2 * s);
  c.fillStyle = argb(SIGN_TEXT);
  c.fillText(date, cx * s, 958 * s);
}
