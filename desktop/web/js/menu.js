// The channel menu, ported from the Android launcher's WiiMenuView.java: twelve rounded channel frames to a
// page with the next page's first column at the edge, the striped backdrop, the curved blue line over the
// shaded panel with the segment clock, the date and two round buttons, the pointing hand, the pop-up menu,
// the full-screen Settings pages and the welcome screen. Geometry is in ref px (1920 x 1080) scaled by s.
// Additions for computers: the hand follows the mouse, hovering focuses, clicking selects, the wheel turns
// pages, and right-click opens a channel's options.
import * as B from './base.js';
import { drawHand } from './hand.js';
import * as S from './audio.js';
import * as Season from './season.js';
import * as Island from './island.js';

export const FONT = "Roboto, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif";

const SLOTS = 12;
const MIN_PAGES = 4;
export const BTN_SETTINGS = -1, BTN_SD = -2, BTN_INPUTS = -3;
const NO_FOCUS = -1000;
const INTRO_AUTO_MS = 12000;
const MENU_ROWS = 6;

const SETTINGS_ROWS = 4;
const SETTINGS_BACK = -1;
const SET_BTN_W = 1120, SET_BTN_H = 124, SET_BTN_TOP = 250, SET_BTN_PITCH = 152, SET_BAND_TOP = 884, SET_ARROW_Y = 478;
const SET_BACK = [110, 930, 430, 1034];

const TILE_X0 = 163, TILE_Y0 = 93.5, TILE_W = 381, TILE_H = 204.5, COL_PITCH = 404, ROW_PITCH = 228;
const PAGE_W = COL_PITCH * 4, TILE_R = 25, TILE_BULGE = 3, TILE_BORDER = 4.5, TILE_BORDER_COLOR = 0xffb5b5b5;

const ARROW_L = 1797.5, ARROW_TIP = 1850, ARROW_T = 365, ARROW_B = 462.5, ARROW_Y = 414;
const BTN_RIGHT_CX = 2 * B.CX - B.BTN_CX;
const SD_L = 356, SD_T = 897, SD_R = 424, SD_B = 994;

const DIGIT_W = 50, DIGIT_H = 79, DIGIT_T = 12, DIGIT_GAP = 2.5, CLOCK_TOP = 789;
const DIGIT_X = [825, 886, 983, 1044];
const COLON_X = 959.5, COLON_DOT = 11, COLON_Y1 = 811.5, COLON_Y2 = 844.5;
const AMPM_X = 1142, AMPM_P_W = 24, AMPM_M_X = 1172, AMPM_M_W = 28, AMPM_BASE = 869, AMPM_CAP = 34;
const DATE_TRACKING = 2, DATE_EXTRA_SPACE = 3, DATE_BASE = 981, DATE_CAP = 55;
const CLOCK_COLOR = 0xff9a9a9a, DATE_COLOR = 0xff7c7c7c;
const BLUE = 0xff3fc0ec;
const SEGMENTS = [0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x27, 0x7f, 0x6f];
const HAND_UNIT = 1920 / 1024, HAND_SCALE = 0.8;
const ENTRANCE_STEP_MS = 70, ENTRANCE_TILE_MS = 420, ENTRANCE_PANEL_DELAY_MS = 520, ENTRANCE_MS = 1400;
/** Ease out with a small overshoot, so each tile pops past full size and settles. */
const opaque = (rgb) => (0xff000000 | rgb) >>> 0;

/** The menu's own colours for a theme (Theme.java's accent, clock, date, wordmark, tile frame, icon tiles). */
function colours(t) {
  if (!t) {
    return { accent: BLUE, clock: CLOCK_COLOR, date: DATE_COLOR, wordmark: 0xffa4a4a4, tileBorder: TILE_BORDER_COLOR,
      iconTop: 0xffffffff, iconBottom: 0xffe9e9e9, iconText: 0xff7a7a7a };
  }
  return { accent: opaque(t.accent), clock: opaque(t.clock), date: opaque(t.date), wordmark: opaque(t.wordmark),
    tileBorder: opaque(t.tileBorder), iconTop: opaque(t.iconTileTop), iconBottom: opaque(t.iconTileBottom),
    iconText: opaque(t.iconTileText) };
}

/**
 * Scenery baked into the backdrop, as WiiMenuView.drawDecorations: Beach's wave above the line, Winter's pine
 * trees and frozen ponds, Fall's leaf piles. (Island village has a backdrop of its own.)
 */
function drawDeco(c, theme, s) {
  if (theme.deco === 'WAVE') Season.bakeBeach(c, s, opaque(theme.accent));
  else if (theme.deco === 'SNOW') Season.bakeWinter(c, s);
  else if (theme.deco === 'LEAVES') Season.bakeFall(c, s);
}

const ease = (p) => {
  const q = p - 1;
  return 1 + q * q * (2.7 * q + 1.7);
};

const css = (c) => `rgba(${(c >> 16) & 255},${(c >> 8) & 255},${c & 255},${(((c >>> 24) & 255) / 255).toFixed(3)})`;
const withAlpha = (c, a) => ((c & 0xffffff) | (Math.max(0, Math.min(255, a | 0)) << 24)) >>> 0;
function blend(from, to, t) {
  t = Math.max(0, Math.min(1, t));
  const ch = (sh) => Math.round(((from >>> sh) & 255) + (((to >>> sh) & 255) - ((from >>> sh) & 255)) * t);
  return ((ch(24) << 24) | (ch(16) << 16) | (ch(8) << 8) | ch(0)) >>> 0;
}
const approach = (v, target, k, eps) => {
  const n = v + (target - v) * k;
  return Math.abs(target - n) < eps ? target : n;
};

function ellipsize(c, text, maxW) {
  if (c.measureText(text).width <= maxW) return text;
  let lo = 0, hi = text.length;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (c.measureText(text.slice(0, mid) + '…').width <= maxW) lo = mid;
    else hi = mid - 1;
  }
  return text.slice(0, lo) + '…';
}

/** A closed polygon with the corners in `round` (indices; all when null) rounded to radius r, like CornerPathEffect. */
function roundPoly(c, pts, r, round = null) {
  const n = pts.length;
  const mid = (i, j) => [(pts[i][0] + pts[j][0]) / 2, (pts[i][1] + pts[j][1]) / 2];
  c.beginPath();
  const start = mid(n - 1, 0);
  c.moveTo(start[0], start[1]);
  for (let i = 0; i < n; i++) {
    const p = pts[i], q = pts[(i + 1) % n];
    if (round === null || round.includes(i)) c.arcTo(p[0], p[1], q[0], q[1], r);
    else c.lineTo(p[0], p[1]);
  }
  c.closePath();
}

function setFont(c, size, weight = 'bold', style = '') {
  c.font = `${style} ${weight} ${size}px ${FONT}`;
}

export class WiiMenu {
  constructor(canvas, audio, listener) {
    this.canvas = canvas;
    this.c = canvas.getContext('2d');
    this.layer = document.createElement('canvas');
    this.audio = audio;
    this.listener = listener;
    this.channels = [];
    this.pages = MIN_PAGES;
    this.focus = 0;
    this.page = 0;
    this.pagePos = 0;
    this.lift = new Float32Array(MIN_PAGES * SLOTS + 3);
    this.handX = 0;
    this.handY = 0;
    this.handPlaced = false;
    this.mouse = null; // last pointer position in canvas px while the mouse is in charge
    this.colonOn = true;
    this.settingsLabel = 'WiiOS';
    this.backLabel = 'Back';
    this.introAlpha = 0;
    this.introLeaving = false;
    this.introStart = 0;
    this.intro = { title: '', body: '', cont: '' };
    this.menuItems = [];
    this.menuTitle = '';
    this.menuFocus = 0;
    this.menuAlpha = 0;
    this.menuOpen = false;
    this.menuLift = new Float32Array(0);
    this.menuFirst = 0;
    this.settings = [];
    this.settingsAlpha = 0;
    this.settingsLift = new Float32Array(SETTINGS_ROWS + 1);
    this.moving = false;
    this.moveStart = 0;
    this.notice = '';
    this.noticeUntil = 0;
    this.s = 1;
    this.w = 1;
    this.h = 1;
    this.base = null;
    this.btnLeft = null;
    this.btnRight = null;
    this.empty = null;
    this.lastFrame = 0;
    this.frameQueued = false;
    /** Shortest time between frames; 0 = every display frame. TVs set more (app.js). */
    this.minFrameMs = 0;
    this.theme = null;
    this.col = colours(null);
    setInterval(() => {
      this.colonOn = !this.colonOn;
      this.invalidate();
    }, 1000);
  }

  // ---- state ---------------------------------------------------------------------------------

  /** A theme from gen/themes.js (null: classic): recolours the layers and the menu's own colours. */
  setTheme(theme) {
    this.theme = theme && !B.untouched(theme) ? theme : null;
    this.col = colours(this.theme);
    for (const ch of this.channels) ch.tile = null;
    this.rebuild();
    this.invalidate();
  }

  island() {
    return !!this.theme && this.theme.deco === 'ISLAND';
  }

  /** Snow, leaves and the island's clouds keep moving (WiiMenuView redraws them at 25 frames a second). */
  seasonal() {
    return !!this.theme && (this.theme.deco === 'SNOW' || this.theme.deco === 'LEAVES' || this.theme.deco === 'ISLAND');
  }

  setChannels(list) {
    this.channels = list.slice();
    this.pages = Math.max(MIN_PAGES, Math.ceil(this.channels.length / SLOTS));
    const n = this.pages * SLOTS + 3;
    if (this.lift.length !== n) this.lift = new Float32Array(n);
    if (this.focus >= this.pages * SLOTS) this.focus = 0;
    if (this.page >= this.pages) {
      this.page = 0;
      this.pagePos = 0;
    }
    this.invalidate();
  }

  /** A channel's picture finished loading: redraw its tile. */
  artChanged(ch) {
    ch.tile = null;
    this.invalidate();
  }

  setIntroText(title, body, cont) {
    this.intro = { title, body, cont };
  }

  playIntro() {
    this.menuOpen = false;
    this.menuAlpha = 0;
    this.introAlpha = 1;
    this.introLeaving = false;
    this.introStart = performance.now();
    clearTimeout(this.introTimer);
    this.introTimer = setTimeout(() => this.dismissIntro(), INTRO_AUTO_MS);
    this.invalidate();
  }

  dismissIntro() {
    if (this.introAlpha <= 0 || this.introLeaving) return;
    this.introLeaving = true;
    clearTimeout(this.introTimer);
    this.playEntrance();
    this.invalidate();
  }

  showMenu(title, items, selected = 0) {
    if (!items.length) return;
    this.menuTitle = title;
    this.menuItems = items.slice();
    this.menuFocus = Math.max(0, Math.min(items.length - 1, selected));
    this.menuFirst = Math.max(0, this.menuFocus - MENU_ROWS + 1);
    this.menuLift = new Float32Array(items.length);
    this.menuOpen = true;
    this.invalidate();
  }

  closeMenu() {
    this.menuOpen = false;
    this.invalidate();
  }

  /** A short message in a pill at the top of the screen, for `ms` (0 = until replaced). */
  showNotice(text, ms = 4000) {
    this.notice = text;
    this.noticeUntil = ms ? performance.now() + ms : Infinity;
    if (ms) setTimeout(() => this.invalidate(), ms + 20);
    this.invalidate();
  }

  settingsOpen() {
    return this.settings.length > 0;
  }

  /** Opens a Settings page (title, () => items) on top of the menu or the page already showing. */
  pushSettings(title, source) {
    this.settings.push({ title, source, items: source(), focus: 0, sheet: 0 });
    this.menuOpen = false;
    this.invalidate();
  }

  refreshSettings() {
    if (!this.settings.length) return;
    const p = this.top();
    p.items = p.source();
    if (p.focus >= p.items.length) p.focus = p.items.length - 1;
    const sheets = this.sheets(p);
    if (p.sheet >= sheets) p.sheet = sheets - 1;
    this.invalidate();
  }

  popSettings() {
    if (!this.settings.length) return;
    this.settings.pop();
    if (this.settings.length) this.refreshSettings();
    this.invalidate();
  }

  closeSettings() {
    this.settings = [];
    this.invalidate();
  }

  top() {
    return this.settings[this.settings.length - 1];
  }

  sheets(p) {
    return Math.max(1, Math.ceil(p.items.length / SETTINGS_ROWS));
  }

  startMove() {
    if (this.focus < 0 || this.focus >= this.channels.length) return;
    this.moving = true;
    this.moveStart = performance.now();
    this.invalidate();
  }

  endMove() {
    this.moving = false;
    this.sound(S.SELECT);
    this.listener.onOrderChanged(this.channels.slice());
    this.invalidate();
  }

  sound(name) {
    this.audio.play(name);
  }

  // ---- keys: up, down, left, right, ok, okHeld, back, menu, options, pageUp, pageDown -------------

  key(k) {
    this.mouse = null;
    if (this.introAlpha > 0) {
      if (!['ok', 'up', 'down', 'left', 'right'].includes(k)) return false;
      if (!this.introLeaving) {
        this.sound(S.START);
        this.dismissIntro();
      }
      return true;
    }
    if (k === 'back') return this.back();
    if (this.menuOpen) return this.menuKey(k);
    if (this.settings.length) return this.settingsKey(k);
    if (this.moving) return this.moveKey(k);
    const onChannel = this.focus >= 0 && this.focus < this.channels.length;
    if (onChannel && (k === 'options' || k === 'okHeld')) {
      this.sound(S.MENU);
      this.listener.onChannelOptions(this.channels[this.focus]);
      return true;
    }
    if (k === 'okHeld') return true;
    if (k === 'menu') {
      this.sound(S.MENU);
      this.listener.onMenuKey();
      return true;
    }
    if (k === 'pageUp') return this.turnPage(-1), true;
    if (k === 'pageDown') return this.turnPage(1), true;
    if (k === 'left') return this.move(-1, 0), true;
    if (k === 'right') return this.move(1, 0), true;
    if (k === 'up') return this.move(0, -1), true;
    if (k === 'down') return this.move(0, 1), true;
    if (k === 'ok') return this.activate(), true;
    return false;
  }

  back() {
    if (this.settings.length && !this.menuOpen) {
      this.sound(S.BACK);
      this.popSettings();
      return true;
    }
    if (this.moving) {
      this.endMove();
      return true;
    }
    if (this.introAlpha > 0) {
      this.dismissIntro();
      return true;
    }
    if (this.menuOpen) {
      this.sound(S.BACK);
      this.closeMenu();
      return true;
    }
    return false;
  }

  move(dx, dy) {
    const before = this.focus, pageBefore = this.page;
    let f = this.focus;
    if (f === NO_FOCUS) f = this.page * SLOTS;
    if (f >= 0) {
      const pg = Math.floor(f / SLOTS), i = f % SLOTS, row = Math.floor(i / 4), col = i % 4;
      if (dx < 0) {
        if (col > 0) f--;
        else if (pg > 0) {
          f = (pg - 1) * SLOTS + row * 4 + 3;
          this.page = pg - 1;
        }
      } else if (dx > 0) {
        if (col < 3) f++;
        else if (pg < this.pages - 1) {
          f = (pg + 1) * SLOTS + row * 4;
          this.page = pg + 1;
        }
      } else if (dy < 0) {
        if (row > 0) f -= 4;
      } else if (row < 2) {
        f += 4;
      } else {
        f = col === 0 ? BTN_SETTINGS : col === 1 ? BTN_SD : BTN_INPUTS;
      }
    } else if (dy < 0) {
      const col = f === BTN_SETTINGS ? 0 : f === BTN_SD ? 1 : 3;
      f = this.page * SLOTS + 8 + col;
    } else if (dx < 0) {
      if (f === BTN_SD) f = BTN_SETTINGS;
      else if (f === BTN_INPUTS) f = BTN_SD;
    } else if (dx > 0) {
      if (f === BTN_SETTINGS) f = BTN_SD;
      else if (f === BTN_SD) f = BTN_INPUTS;
    }
    this.focus = f;
    if (f !== before) {
      this.sound(this.page !== pageBefore ? S.PAGE : S.MOVE);
      this.invalidate();
    }
  }

  turnPage(delta) {
    const np = Math.max(0, Math.min(this.pages - 1, this.page + delta));
    if (np === this.page) return;
    const i = this.focus >= 0 ? this.focus % SLOTS : 0;
    this.page = np;
    if (this.mouse) this.focus = NO_FOCUS;
    else this.focus = this.page * SLOTS + i;
    this.sound(S.PAGE);
    this.invalidate();
  }

  moveKey(k) {
    if (k === 'ok' || k === 'menu' || k === 'okHeld') {
      if (k !== 'okHeld') this.endMove();
      return true;
    }
    const d = { left: [-1, 0], right: [1, 0], up: [0, -1], down: [0, 1] }[k];
    if (!d) return false;
    const from = this.focus, pageBefore = this.page;
    this.move(d[0], d[1]);
    if (this.focus < 0 || this.focus >= this.channels.length) {
      this.focus = from;
      this.page = pageBefore;
      this.invalidate();
      return true;
    }
    if (this.focus !== from) {
      const t = this.channels[from];
      this.channels[from] = this.channels[this.focus];
      this.channels[this.focus] = t;
      const l = this.lift[from];
      this.lift[from] = this.lift[this.focus];
      this.lift[this.focus] = l;
    }
    return true;
  }

  activate() {
    const f = this.focus;
    this.sound(f === BTN_INPUTS ? S.MENU : S.SELECT);
    if (f === BTN_SETTINGS) this.listener.onSettingsButton();
    else if (f === BTN_SD) this.listener.onSdButton();
    else if (f === BTN_INPUTS) this.listener.onInputsButton();
    else if (f >= 0 && f < this.channels.length) this.listener.onChannel(this.channels[f]);
  }

  menuKey(k) {
    if (k === 'up') {
      if (this.menuFocus > 0) {
        this.menuFocus--;
        if (this.menuFocus < this.menuFirst) this.menuFirst = this.menuFocus;
        this.sound(S.MOVE);
        this.invalidate();
      }
      return true;
    }
    if (k === 'down') {
      if (this.menuFocus < this.menuItems.length - 1) {
        this.menuFocus++;
        if (this.menuFocus >= this.menuFirst + MENU_ROWS) this.menuFirst = this.menuFocus - MENU_ROWS + 1;
        this.sound(S.MOVE);
        this.invalidate();
      }
      return true;
    }
    if (k === 'ok') {
      const item = this.menuItems[this.menuFocus];
      this.sound(S.SELECT);
      this.closeMenu();
      item.action();
      return true;
    }
    return true;
  }

  settingsKey(k) {
    const p = this.top();
    const sheets = this.sheets(p);
    const first = p.sheet * SETTINGS_ROWS;
    const onSheet = Math.min(SETTINGS_ROWS, p.items.length - first);
    const row = p.focus === SETTINGS_BACK ? -1 : p.focus - first;
    const before = p.focus, sheetBefore = p.sheet;
    if (k === 'up') {
      if (p.focus === SETTINGS_BACK) p.focus = first + onSheet - 1;
      else if (row > 0) p.focus--;
    } else if (k === 'down') {
      if (p.focus !== SETTINGS_BACK) p.focus = row < onSheet - 1 ? p.focus + 1 : SETTINGS_BACK;
    } else if (k === 'left' || k === 'pageUp') {
      if (p.sheet > 0) p.sheet--;
    } else if (k === 'right' || k === 'pageDown') {
      if (p.sheet < sheets - 1) p.sheet++;
    } else if (k === 'ok') {
      if (p.focus === SETTINGS_BACK) {
        this.sound(S.BACK);
        this.popSettings();
      } else if (p.focus >= 0 && p.focus < p.items.length) {
        this.sound(S.SELECT);
        p.items[p.focus].action();
      }
      return true;
    } else {
      return true;
    }
    if (p.sheet !== sheetBefore && p.focus !== SETTINGS_BACK) {
      p.focus = Math.min(p.sheet * SETTINGS_ROWS + Math.max(0, row), p.items.length - 1);
    }
    if (p.focus !== before || p.sheet !== sheetBefore) {
      this.sound(p.sheet !== sheetBefore ? S.PAGE : S.MOVE);
      this.invalidate();
    }
    return true;
  }

  // ---- mouse -----------------------------------------------------------------------------------

  /** What is under canvas point (x, y): {kind, index} or null. */
  hit(x, y) {
    const s = this.s;
    const inR = (l, t, r, b) => x >= l * s && x <= r * s && y >= t * s && y <= b * s;
    if (this.introAlpha > 0) return { kind: 'intro' };
    if (this.menuOpen) {
      const g = this.menuGeometry();
      for (let row = 0; row < g.n; row++) {
        const iy = g.py + g.head + row * (g.itemH + g.gap);
        if (x >= g.px + 90 * s && x <= g.px + g.pw - 90 * s && y >= iy && y <= iy + g.itemH) return { kind: 'menu', index: this.menuFirst + row };
      }
      return { kind: 'menuOutside', inside: x >= g.px && x <= g.px + g.pw && y >= g.py && y <= g.py + g.ph };
    }
    if (this.settings.length) {
      const p = this.top();
      const first = p.sheet * SETTINGS_ROWS;
      const onSheet = Math.min(SETTINGS_ROWS, p.items.length - first);
      for (let r = 0; r < onSheet; r++) {
        const t = SET_BTN_TOP + r * SET_BTN_PITCH;
        if (inR(B.CX - SET_BTN_W / 2, t, B.CX + SET_BTN_W / 2, t + SET_BTN_H)) return { kind: 'setting', index: first + r };
      }
      if (inR(...SET_BACK)) return { kind: 'setting', index: SETTINGS_BACK };
      if (inR(ARROW_L - 10, SET_ARROW_Y - 60, ARROW_TIP + 10, SET_ARROW_Y + 60)) return { kind: 'sheet', delta: 1 };
      if (inR(2 * B.CX - ARROW_TIP - 10, SET_ARROW_Y - 60, 2 * B.CX - ARROW_L + 10, SET_ARROW_Y + 60)) return { kind: 'sheet', delta: -1 };
      return null;
    }
    const near = (cx, cy, r) => Math.hypot(x - cx * s, y - cy * s) <= r * s;
    if (near(B.BTN_CX, B.BTN_CY, B.BTN_RING)) return { kind: 'focus', index: BTN_SETTINGS };
    if (near(BTN_RIGHT_CX, B.BTN_CY, B.BTN_RING)) return { kind: 'focus', index: BTN_INPUTS };
    if (inR(SD_L - 8, SD_T - 8, SD_R + 8, SD_B + 8)) return { kind: 'focus', index: BTN_SD };
    if (this.page < this.pages - 1 && inR(ARROW_L - 10, ARROW_T - 10, ARROW_TIP + 10, ARROW_B + 10)) return { kind: 'page', delta: 1 };
    if (this.page > 0 && inR(2 * B.CX - ARROW_TIP - 10, ARROW_T - 10, 2 * B.CX - ARROW_L + 10, ARROW_B + 10)) return { kind: 'page', delta: -1 };
    for (let i = 0; i < SLOTS; i++) {
      const sl = this.page * SLOTS + i;
      const r = this.slotRect(sl, this.page);
      if (x >= r.l && x <= r.r && y >= r.t && y <= r.b) return { kind: 'focus', index: sl };
    }
    return null;
  }

  pointerMove(x, y) {
    this.mouse = [x, y];
    const h = this.hit(x, y);
    if (this.menuOpen) {
      if (h && h.kind === 'menu' && h.index !== this.menuFocus) {
        this.menuFocus = h.index;
        this.sound(S.MOVE);
      }
    } else if (this.settings.length) {
      const p = this.top();
      if (h && h.kind === 'setting' && h.index !== p.focus) {
        p.focus = h.index;
        this.sound(S.MOVE);
      }
    } else if (!this.moving && this.introAlpha <= 0) {
      const f = h && h.kind === 'focus' ? h.index : NO_FOCUS;
      if (f !== this.focus) {
        if (f !== NO_FOCUS) this.sound(S.MOVE);
        this.focus = f;
      }
    }
    this.invalidate();
  }

  click(x, y, right = false) {
    this.pointerMove(x, y);
    const h = this.hit(x, y);
    if (this.introAlpha > 0) return this.key('ok');
    if (this.menuOpen) {
      if (h && h.kind === 'menu') return this.key('ok');
      if (h && !h.inside) return this.back();
      return true;
    }
    if (this.settings.length) {
      if (right) return this.back();
      if (h && h.kind === 'setting') return this.settingsKey('ok');
      if (h && h.kind === 'sheet') return this.settingsKey(h.delta > 0 ? 'right' : 'left');
      return true;
    }
    if (this.moving) return this.endMove(), true;
    if (!h) return false;
    if (h.kind === 'page') return this.turnPage(h.delta), true;
    if (right) return this.key('options');
    return this.key('ok') || true;
  }

  wheel(dy) {
    if (this.menuOpen) return this.key(dy > 0 ? 'down' : 'up');
    if (this.settings.length) return this.settingsKey(dy > 0 ? 'right' : 'left');
    if (!this.moving) this.turnPage(dy > 0 ? 1 : -1);
    return true;
  }

  // ---- geometry --------------------------------------------------------------------------------

  liftIndex(f) {
    return f >= 0 ? f : this.pages * SLOTS + (-f - 1);
  }

  slotRect(slot, pos) {
    const s = this.s;
    const pg = Math.floor(slot / SLOTS), i = slot % SLOTS, row = Math.floor(i / 4), col = i % 4;
    const l = (TILE_X0 + (pg * 4 + col) * COL_PITCH - pos * PAGE_W) * s;
    const t = (TILE_Y0 + row * ROW_PITCH) * s;
    return { l, t, r: l + TILE_W * s, b: t + TILE_H * s };
  }

  handTarget(f) {
    const s = this.s;
    if (this.mouse) return this.mouse;
    if (this.settings.length) {
      const p = this.top();
      if (p.focus === SETTINGS_BACK) {
        return [(SET_BACK[0] + (SET_BACK[2] - SET_BACK[0]) * 0.72) * s, (SET_BACK[1] + (SET_BACK[3] - SET_BACK[1]) * 0.75) * s];
      }
      const row = p.focus - p.sheet * SETTINGS_ROWS;
      return [(B.CX + SET_BTN_W / 2 + 18) * s, (SET_BTN_TOP + row * SET_BTN_PITCH + SET_BTN_H * 0.62) * s];
    }
    if (f === NO_FOCUS) {
      const r = this.slotRect(this.page * SLOTS, this.page);
      return [r.l + (r.r - r.l) * 0.7, r.t + (r.b - r.t) * 0.8];
    }
    if (f >= 0) {
      const r = this.slotRect(f, this.page);
      return [r.l + (r.r - r.l) * 0.7, r.t + (r.b - r.t) * 0.8];
    }
    if (f === BTN_SD) return [((SD_L + SD_R) / 2 + 10) * s, ((SD_T + SD_B) / 2 + 14) * s];
    const cx = f === BTN_SETTINGS ? B.BTN_CX : BTN_RIGHT_CX;
    return [(cx + 26) * s, (B.BTN_CY + 22) * s];
  }

  resize(w, h) {
    this.w = Math.max(1, w);
    this.h = Math.max(1, h);
    this.s = this.w / 1920;
    this.canvas.width = this.w;
    this.canvas.height = this.h;
    this.layer.width = this.w;
    this.layer.height = this.h;
    for (const ch of this.channels) ch.tile = null;
    this.handPlaced = false;
    this.rebuild();
    this.invalidate();
  }

  /** The per-pixel layers; computed after a frame so the window shows at once. */
  rebuild() {
    const w = this.w, h = this.h, s = this.s;
    const gen = (this.buildGen = (this.buildGen || 0) + 1);
    setTimeout(() => {
      if (gen !== this.buildGen) return;
      const toCanvas = (px, cw, chh) => {
        const cv = document.createElement('canvas');
        cv.width = cw;
        cv.height = chh;
        cv.getContext('2d').putImageData(new ImageData(px, cw, chh), 0, 0);
        return cv;
      };
      const blank = (cw, chh) => {
        const cv = document.createElement('canvas');
        cv.width = cw;
        cv.height = chh;
        return cv;
      };
      const size = Math.ceil(2 * (B.BTN_RING + B.BTN_RING_HALF + 2) * s);
      if (this.island()) {
        this.base = blank(w, h);
        Island.backdrop(this.base.getContext('2d'), w, h, s);
        this.btnLeft = blank(size, size);
        this.btnRight = blank(size, size);
        Island.button(this.btnLeft.getContext('2d'), size, s);
        Island.button(this.btnRight.getContext('2d'), size, s);
      } else {
        this.base = toCanvas(B.backdrop(w, h, this.theme), w, h);
        if (this.theme) drawDeco(this.base.getContext('2d'), this.theme, s);
        const bp = B.button(size, s, this.theme);
        this.btnLeft = toCanvas(bp, size, size);
        this.btnRight = toCanvas(bp.slice(), size, size);
      }
      this.drawWordmark(this.btnLeft.getContext('2d'), size, s, this.settingsLabel);
      this.drawEnvelope(this.btnRight.getContext('2d'), size, s);
      const tw = Math.round(TILE_W * s) + 8, th = Math.round(TILE_H * s) + 8;
      this.empty = toCanvas(B.emptySlot(tw, th, s, this.theme), tw, th);
      this.invalidate();
    }, 0);
  }

  drawWordmark(c, size, s, label) {
    setFont(c, 52 * s);
    c.textAlign = 'center';
    const maxW = 128 * s;
    const tw = c.measureText(label).width;
    if (tw > maxW) setFont(c, (52 * s * maxW) / tw);
    const y = size / 2 + 17 * s;
    c.fillStyle = 'rgba(255,255,255,0.6)';
    c.fillText(label, size / 2, y + 2 * s);
    c.fillStyle = css(this.col.wordmark);
    c.fillText(label, size / 2, y);
  }

  drawEnvelope(c, size, s) {
    const cx = size / 2, cy = size / 2, ew = 97 * s, eh = 65 * s;
    const l = cx - ew / 2, t = cy - eh / 2, r = cx + ew / 2;
    c.fillStyle = '#939393';
    c.beginPath();
    c.roundRect(l, t, ew, eh, 5 * s);
    c.fill();
    c.strokeStyle = '#e2e2e2';
    c.lineWidth = 4.5 * s;
    c.lineJoin = 'round';
    c.beginPath();
    c.moveTo(l + 2 * s, t + 14 * s);
    c.lineTo(cx, t + 38 * s);
    c.lineTo(r - 2 * s, t + 14 * s);
    c.stroke();
  }

  tilePath(c, tw, th, r, b) {
    const k = r * 0.4477;
    c.beginPath();
    c.moveTo(r, 0);
    c.quadraticCurveTo(tw / 2, -2 * b, tw - r, 0);
    c.bezierCurveTo(tw - k, 0, tw, k, tw, r);
    c.quadraticCurveTo(tw + 2 * b, th / 2, tw, th - r);
    c.bezierCurveTo(tw, th - k, tw - k, th, tw - r, th);
    c.quadraticCurveTo(tw / 2, th + 2 * b, r, th);
    c.bezierCurveTo(k, th, 0, th - k, 0, th - r);
    c.quadraticCurveTo(-2 * b, th / 2, 0, r);
    c.bezierCurveTo(0, k, k, 0, r, 0);
    c.closePath();
  }

  // ---- animation -------------------------------------------------------------------------------

  invalidate() {
    if (this.frameQueued) return;
    this.frameQueued = true;
    if (this.minFrameMs > 0) {
      const wait = Math.max(0, this.minFrameMs - (performance.now() - this.lastFrame));
      setTimeout(() => requestAnimationFrame((t) => this.frame(t)), this.lastFrame === 0 ? 0 : wait);
    } else {
      requestAnimationFrame((t) => this.frame(t));
    }
  }

  step(dt) {
    const kPage = 1 - Math.exp(-dt / 0.11), kLift = 1 - Math.exp(-dt / 0.07), kHand = 1 - Math.exp(-dt / 0.06);
    const kMenu = 1 - Math.exp(-dt / 0.08);
    this.pagePos = approach(this.pagePos, this.page, kPage, 0.0008);
    let moving = this.pagePos !== this.page;
    const fi = this.focus === NO_FOCUS ? -1000 : this.liftIndex(this.focus);
    for (let i = 0; i < this.lift.length; i++) {
      const t = i === fi ? 1 : 0;
      this.lift[i] = approach(this.lift[i], t, kLift, 0.002);
      if (this.lift[i] !== t) moving = true;
    }
    const pt = this.handTarget(this.focus);
    if (!this.handPlaced || this.mouse) {
      this.handX = pt[0];
      this.handY = pt[1];
      this.handPlaced = true;
    }
    this.handX = approach(this.handX, pt[0], kHand, 0.3);
    this.handY = approach(this.handY, pt[1], kHand, 0.3);
    if (this.handX !== pt[0] || this.handY !== pt[1]) moving = true;
    const mt = this.menuOpen ? 1 : 0;
    this.menuAlpha = approach(this.menuAlpha, mt, kMenu, 0.004);
    if (this.menuAlpha !== mt) moving = true;
    for (let i = 0; i < this.menuLift.length; i++) {
      const t = this.menuOpen && i === this.menuFocus ? 1 : 0;
      this.menuLift[i] = approach(this.menuLift[i], t, kLift, 0.002);
      if (this.menuLift[i] !== t) moving = true;
    }
    const st = this.settings.length ? 1 : 0;
    this.settingsAlpha = approach(this.settingsAlpha, st, kMenu, 0.004);
    if (this.settingsAlpha !== st) moving = true;
    const sf = this.settings.length ? this.top().focus : -2;
    const srow = !this.settings.length ? -2 : sf === SETTINGS_BACK ? SETTINGS_ROWS : sf - this.top().sheet * SETTINGS_ROWS;
    for (let i = 0; i < this.settingsLift.length; i++) {
      const t = i === srow ? 1 : 0;
      this.settingsLift[i] = approach(this.settingsLift[i], t, kLift, 0.002);
      if (this.settingsLift[i] !== t) moving = true;
    }
    if (this.introLeaving) {
      this.introAlpha = approach(this.introAlpha, 0, 1 - Math.exp(-dt / 0.2), 0.004);
      if (this.introAlpha === 0) {
        this.introLeaving = false;
      } else {
        moving = true;
      }
    }
    return moving;
  }

  frame(now) {
    this.frameQueued = false;
    const dt = this.lastFrame === 0 ? 1 / 60 : Math.min(0.05, (now - this.lastFrame) / 1000);
    this.lastFrame = now;
    const animating = this.step(dt);
    const c = this.c;
    c.setTransform(1, 0, 0, 1, 0, 0);
    if (this.base) c.drawImage(this.base, 0, 0);
    else {
      c.fillStyle = '#e9e9e9';
      c.fillRect(0, 0, this.w, this.h);
    }
    const deco = this.theme ? this.theme.deco : 'NONE', s = this.s, sec = now / 1000;
    if (deco === 'SNOW') Season.snow(c, s, sec, false);
    else if (deco === 'LEAVES') Season.leaves(c, s, sec, false);
    else if (deco === 'ISLAND') Island.clouds(c, s, sec);
    this.drawTiles(c);
    if (deco === 'ISLAND') Island.butterflies(c, s, sec);
    else if (deco === 'SNOW') {
      Season.winterKids(c, s, sec);
      Season.snow(c, s, sec, true);
    } else if (deco === 'LEAVES') {
      Season.leaves(c, s, sec, true);
    }
    const panel = this.entering() ? this.entrance(ENTRANCE_PANEL_DELAY_MS) : 1;
    if (panel > 0) {
      // Entrance: the buttons, clock and date rise into place after the channels.
      c.save();
      c.globalAlpha = panel;
      c.translate(0, (1 - ease(panel)) * 70 * this.s);
      if (panel >= 1) this.drawArrows(c);
      this.drawButton(c, this.btnLeft, B.BTN_CX, BTN_SETTINGS);
      this.drawButton(c, this.btnRight, BTN_RIGHT_CX, BTN_INPUTS);
      this.drawSd(c);
      this.drawClock(c);
      this.drawDate(c);
      c.restore();
    }
    if (this.settingsAlpha > 0) this.withAlphaLayer(this.settingsAlpha, (lc) => this.drawSettings(lc));
    if (this.menuAlpha > 0) this.withAlphaLayer(this.menuAlpha, (lc) => this.drawMenu(lc));
    drawHand(c, this.handX, this.handY, HAND_UNIT * this.s * HAND_SCALE, FONT);
    if (this.introAlpha > 0) this.withAlphaLayer(this.introAlpha, (lc) => this.drawIntro(lc));
    if (this.notice && performance.now() < this.noticeUntil) this.drawNotice(c);
    if (animating || this.introAlpha > 0 || this.moving || this.entering()) {
      this.invalidate();
    } else {
      this.lastFrame = 0;
      if (this.seasonal()) {
        clearTimeout(this.sceneTimer);
        this.sceneTimer = setTimeout(() => this.invalidate(), 40);
      }
    }
  }

  /** Android's saveLayerAlpha: draw into the layer, then put it down at the given opacity. */
  withAlphaLayer(alpha, draw) {
    if (alpha >= 1) {
      this.c.save();
      draw(this.c);
      this.c.restore();
      return;
    }
    const lc = this.layer.getContext('2d');
    lc.setTransform(1, 0, 0, 1, 0, 0);
    lc.clearRect(0, 0, this.w, this.h);
    lc.save();
    draw(lc);
    lc.restore();
    this.c.save();
    this.c.globalAlpha = alpha;
    this.c.drawImage(this.layer, 0, 0);
    this.c.restore();
  }

  // ---- drawing ---------------------------------------------------------------------------------

  drawTiles(c) {
    const total = this.pages * SLOTS, s = this.s;
    for (let sl = 0; sl < total; sl++) {
      if (sl === this.focus) continue;
      const r = this.slotRect(sl, this.pagePos);
      if (r.r < -10 * s || r.l > this.w + 10 * s) continue;
      this.drawTileEntering(c, sl, r, this.lift[sl]);
    }
    if (this.focus >= 0) {
      const r = this.slotRect(this.focus, this.pagePos);
      if (this.moving) {
        const t = (performance.now() - this.moveStart) / 1000;
        const bob = Math.sin((t * Math.PI * 2) / 1.2) * 5 * s;
        c.fillStyle = 'rgba(0,0,0,0.19)';
        c.beginPath();
        c.ellipse((r.l + r.r) / 2, r.b + 6 * s, (r.r - r.l) / 2 - 10 * s, 12 * s, 0, 0, Math.PI * 2);
        c.fill();
        const dy = -14 * s + bob;
        const rr = { l: r.l, t: r.t + dy, r: r.r, b: r.b + dy };
        c.save();
        const cx = (rr.l + rr.r) / 2, cy = (rr.t + rr.b) / 2;
        c.translate(cx, cy);
        c.scale(1.08, 1.08);
        c.translate(-cx, -cy);
        this.drawTile(c, this.focus, rr, 1);
        c.restore();
      } else if (r.r >= 0 && r.l <= this.w) {
        this.drawTileEntering(c, this.focus, r, this.lift[this.focus]);
      }
    }
  }

  // ---- start-up entrance (as in WiiMenuView): after the welcome screen the channels pop in, a diagonal wave
  // from the top left, then the buttons, clock and date rise into place.

  /** Starts a moment after the welcome screen begins to fade, so the channels pop in as it clears. */
  playEntrance() {
    this.entranceStart = performance.now() + 180;
    this.invalidate();
  }

  entering() {
    if (this.entranceStart == null) return false;
    if (performance.now() - this.entranceStart < ENTRANCE_MS) return true;
    this.entranceStart = null;
    return false;
  }

  entrance(delay) {
    return Math.max(0, Math.min(1, (performance.now() - this.entranceStart - delay) / ENTRANCE_TILE_MS));
  }

  drawTileEntering(c, slot, r, lf) {
    if (!this.entering()) return this.drawTile(c, slot, r, lf);
    const i = slot % SLOTS, pg = Math.floor(slot / SLOTS);
    const col = (i % 4) + (pg - this.page) * 4, row = Math.floor(i / 4);
    const p = this.entrance((col + row) * ENTRANCE_STEP_MS);
    if (p <= 0) return;
    const sc = 0.35 + 0.65 * ease(p), cx = (r.l + r.r) / 2, cy = (r.t + r.b) / 2;
    c.save();
    c.globalAlpha = p;
    c.translate(cx, cy);
    c.scale(sc, sc);
    c.translate(-cx, -cy);
    this.drawTile(c, slot, r, lf);
    c.restore();
  }

  drawTile(c, slot, r, lf) {
    const s = this.s, tw = r.r - r.l, th = r.b - r.t;
    c.save();
    c.translate(r.l, r.t);
    const ch = slot < this.channels.length ? this.channels[slot] : null;
    if (this.island()) {
      // A cream signboard; its face shows the channel's banner, custom picture, or icon and name.
      const art = ch ? this.ensureArt(ch, Island.innerW(tw, s), Island.innerH(th, s)) : null;
      Island.sign(c, s, tw, th, art, lf, this.col.accent);
      c.restore();
      return;
    }
    this.tilePath(c, tw, th, TILE_R * s, TILE_BULGE * s);
    c.save();
    c.clip();
    if (ch) c.drawImage(this.ensureArt(ch, Math.round(tw), Math.round(th)), 0, 0, tw, th);
    else if (this.empty) c.drawImage(this.empty, -4, -4);
    else {
      c.fillStyle = '#d6d6d6';
      c.fillRect(0, 0, tw, th);
    }
    c.restore();
    c.lineJoin = 'round';
    if (lf > 0.01) {
      for (let k = 3; k >= 1; k--) {
        c.strokeStyle = css(withAlpha(this.col.accent, (lf * 64) / k));
        c.lineWidth = (TILE_BORDER + k * 5) * s;
        c.stroke();
      }
    }
    c.strokeStyle = css(blend(this.col.tileBorder, this.col.accent, lf));
    c.lineWidth = (TILE_BORDER + 1.5 * lf) * s;
    c.stroke();
    c.restore();
  }

  ensureArt(ch, tw, th) {
    tw = Math.max(1, tw);
    th = Math.max(1, th);
    if (ch.tile && ch.tile.width === tw && ch.tile.height === th) return ch.tile;
    const cv = document.createElement('canvas');
    cv.width = tw;
    cv.height = th;
    const c = cv.getContext('2d');
    c.fillStyle = '#fff';
    c.fillRect(0, 0, tw, th);
    const img = ch.image;
    if (ch.paint) {
      ch.paint(c, tw, th);
    } else if (img && img.complete && img.naturalWidth > 0) {
      const sc = Math.max(tw / img.naturalWidth, th / img.naturalHeight);
      const dw = img.naturalWidth * sc, dh = img.naturalHeight * sc;
      c.drawImage(img, (tw - dw) / 2, (th - dh) / 2, dw, dh);
    } else {
      const g = c.createLinearGradient(0, 0, 0, th);
      g.addColorStop(0, css(this.col.iconTop));
      g.addColorStop(1, css(this.col.iconBottom));
      c.fillStyle = g;
      c.fillRect(0, 0, tw, th);
      if (ch.icon && ch.icon.complete && ch.icon.naturalWidth > 0) {
        // A little larger than on the TV version, where icons are only a fallback for missing banners.
        const size = Math.round(th * 0.56);
        c.drawImage(ch.icon, (tw - size) / 2, Math.round(th * 0.09), size, size);
      } else if (ch.draw) {
        ch.draw(c, tw, th);
      }
      setFont(c, th * 0.15);
      c.fillStyle = css(this.col.iconText);
      c.textAlign = 'center';
      c.fillText(ellipsize(c, ch.label, tw * 0.86), tw / 2, th * 0.86);
    }
    ch.tile = cv;
    return cv;
  }

  drawArrows(c) {
    const s = this.s;
    if (this.page < this.pages - 1) this.drawArrow(c, ARROW_TIP * s, ARROW_Y * s, false);
    if (this.page > 0) this.drawArrow(c, (2 * B.CX - ARROW_TIP) * s, ARROW_Y * s, true);
  }

  /** Right-pointing arrow with its tip at (x, y); the back edge curves in toward the tip. */
  arrowPoints() {
    const s = this.s;
    const l = (ARROW_L - ARROW_TIP) * s, t = (ARROW_T - ARROW_Y) * s, b = (ARROW_B - ARROW_Y) * s;
    const pts = [[0, 0], [l, t]];
    const cx = l + 12 * s;
    // Samples of the curved back edge, kept clear of its ends so the rounded corners have room.
    for (let i = 0; i <= 10; i++) {
      const u = 0.2 + (0.6 * i) / 10, v = 1 - u;
      pts.push([v * v * l + 2 * v * u * cx + u * u * l, v * v * t + u * u * b]);
    }
    pts.push([l, b]);
    return pts;
  }

  drawArrow(c, x, y, flip) {
    const s = this.s;
    c.save();
    c.translate(x, y);
    if (flip) c.scale(-1, 1);
    const pts = this.arrowPoints();
    const corners = [0, 1, pts.length - 1];
    const t = (ARROW_T - ARROW_Y) * s, b = (ARROW_B - ARROW_Y) * s, l = (ARROW_L - ARROW_TIP) * s;
    roundPoly(c, pts, 7 * s, corners);
    const g = c.createLinearGradient(0, t, 0, b);
    [[0, '#6fcdf3'], [0.35, '#a9e6fb'], [0.5, '#ddf8ff'], [0.62, '#9fe0f8'], [1, '#62c5f0']].forEach(([o, col]) => g.addColorStop(o, col));
    c.fillStyle = g;
    c.fill();
    const g2 = c.createLinearGradient(l, 0, 0, 0);
    g2.addColorStop(0, 'rgba(255,255,255,0.4)');
    g2.addColorStop(1, 'rgba(255,255,255,0)');
    c.fillStyle = g2;
    c.fill();
    c.strokeStyle = '#2e66c2';
    c.lineWidth = 3.2 * s;
    c.lineJoin = 'round';
    c.stroke();
    c.restore();
  }

  drawButton(c, bmp, cxRef, id) {
    if (!bmp) return;
    const s = this.s, lf = this.lift[this.liftIndex(id)];
    const cx = cxRef * s, cy = B.BTN_CY * s;
    c.save();
    const sc = 1 + 0.06 * lf;
    c.translate(cx, cy);
    c.scale(sc, sc);
    c.translate(-cx, -cy);
    c.drawImage(bmp, cx - bmp.width / 2, cy - bmp.height / 2);
    if (lf > 0.01) {
      for (let k = 3; k >= 1; k--) {
        c.strokeStyle = css(withAlpha(this.col.accent, (lf * 70) / k));
        c.lineWidth = (3 + k * 5) * s;
        c.beginPath();
        c.arc(cx, cy, B.BTN_RING * s, 0, Math.PI * 2);
        c.stroke();
      }
    }
    c.restore();
  }

  drawSd(c) {
    const s = this.s, lf = this.lift[this.liftIndex(BTN_SD)];
    c.save();
    const sc = 1 + 0.08 * lf, cx = ((SD_L + SD_R) / 2) * s, cy = ((SD_T + SD_B) / 2) * s;
    c.translate(cx, cy);
    c.scale(sc, sc);
    c.translate(-cx, -cy);
    const cut = 9;
    const pts = (dx) => [[SD_L, SD_T], [SD_R - cut, SD_T], [SD_R, SD_T + cut], [SD_R, SD_B], [SD_L, SD_B]].map(([px, py]) => [(px + dx) * s, (py + dx) * s]);
    roundPoly(c, pts(4), 4 * s);
    c.fillStyle = 'rgba(0,0,0,0.18)';
    c.fill();
    roundPoly(c, pts(0), 4 * s);
    c.fillStyle = '#5cc8ee';
    c.fill();
    c.fillStyle = '#a9ddf3';
    c.fillRect((SD_L + 4) * s, (SD_T + 4) * s, (SD_R - 10 - SD_L - 4) * s, (915 - SD_T - 4) * s);
    c.fillStyle = '#ffffff';
    c.fillRect(366 * s, 915 * s, 56 * s, 60 * s);
    c.fillStyle = '#4fc1ea';
    c.fillRect(366 * s, 975 * s, 56 * s, 12 * s);
    if (lf > 0.01) {
      for (let k = 3; k >= 1; k--) {
        roundPoly(c, pts(0), 4 * s);
        c.strokeStyle = css(withAlpha(this.col.accent, (lf * 80) / k));
        c.lineWidth = (2 + k * 4) * s;
        c.stroke();
      }
    }
    setFont(c, 24 * s, 'bold', 'italic');
    c.textAlign = 'center';
    c.fillStyle = '#59bde8';
    c.fillText('SD', 393 * s, 953 * s);
    c.restore();
  }

  drawClock(c) {
    const s = this.s;
    const now = new Date();
    let hour = now.getHours() % 12;
    if (hour === 0) hour = 12;
    const minute = now.getMinutes(), pm = now.getHours() >= 12;
    if (this.island()) {
      // Island village: plain time and the full date on the wooden sign.
      const date = new Intl.DateTimeFormat(navigator.language || 'en-US', { weekday: 'long', month: 'long', day: 'numeric' })
        .format(now);
      Island.clock(c, s, FONT, `${hour}:${minute < 10 ? '0' : ''}${minute}`, pm ? 'PM' : 'AM', date);
      return;
    }
    c.fillStyle = css(this.col.clock);
    if (hour >= 10) this.drawDigit(c, DIGIT_X[0], Math.floor(hour / 10));
    this.drawDigit(c, DIGIT_X[1], hour % 10);
    this.drawDigit(c, DIGIT_X[2], Math.floor(minute / 10));
    this.drawDigit(c, DIGIT_X[3], minute % 10);
    if (this.colonOn) {
      const d = COLON_DOT / 2;
      for (const cy of [COLON_Y1, COLON_Y2]) {
        c.beginPath();
        c.roundRect((COLON_X - d) * s, (cy - d) * s, COLON_DOT * s, COLON_DOT * s, 2 * s);
        c.fill();
      }
    }
    setFont(c, (AMPM_CAP / 0.711) * s);
    c.textAlign = 'left';
    this.drawInkLetter(c, pm ? 'P' : 'A', AMPM_X, AMPM_P_W);
    this.drawInkLetter(c, 'M', AMPM_M_X, AMPM_M_W);
  }

  drawInkLetter(c, letter, inkLeft, inkWidth) {
    const s = this.s;
    const m = c.measureText(letter);
    const bl = -m.actualBoundingBoxLeft, bw = m.actualBoundingBoxLeft + m.actualBoundingBoxRight;
    const sx = bw > 0 ? (inkWidth * s) / bw : 1;
    c.save();
    c.translate(inkLeft * s - bl * sx, AMPM_BASE * s);
    c.scale(sx, 1);
    c.fillText(letter, 0, 0);
    c.restore();
  }

  drawDigit(c, xRef, digit) {
    const bits = SEGMENTS[digit];
    const t = DIGIT_T, g = DIGIT_GAP, k = t / 2;
    const xa = xRef, xb = xRef + DIGIT_W;
    const ya = CLOCK_TOP, yb = CLOCK_TOP + DIGIT_H, ym = CLOCK_TOP + DIGIT_H / 2;
    const poly = (...p) => {
      const pts = [];
      for (let i = 0; i < p.length; i += 2) pts.push([p[i] * this.s, p[i + 1] * this.s]);
      roundPoly(c, pts, 2.5 * this.s);
      c.fill();
    };
    if (bits & 0x01) poly(xa + g, ya, xb - g, ya, xb - t - g, ya + t, xa + t + g, ya + t);
    if (bits & 0x08) poly(xa + t + g, yb - t, xb - t - g, yb - t, xb - g, yb, xa + g, yb);
    if (bits & 0x20) poly(xa, ya + g, xa + t, ya + t + g, xa + t, ym - k - g, xa + k, ym - g, xa, ym - k - g);
    if (bits & 0x02) poly(xb, ya + g, xb, ym - k - g, xb - k, ym - g, xb - t, ym - k - g, xb - t, ya + t + g);
    if (bits & 0x10) poly(xa, ym + k + g, xa + k, ym + g, xa + t, ym + k + g, xa + t, yb - t - g, xa, yb - g);
    if (bits & 0x04) poly(xb, ym + k + g, xb, yb - g, xb - t, yb - t - g, xb - t, ym + k + g, xb - k, ym + g);
    if (bits & 0x40) poly(xa + k + g, ym, xa + t + g, ym - k, xb - t - g, ym - k, xb - k - g, ym, xb - t - g, ym + k, xa + t + g, ym + k);
  }

  drawDate(c) {
    if (this.island()) return;   // on the clock's sign
    const s = this.s, now = new Date();
    const lang = navigator.language || 'en-US';
    let day = new Intl.DateTimeFormat(lang, { weekday: 'short' }).format(now).replace('.', '');
    if (day) day = day[0].toUpperCase() + day.slice(1);
    const monthFirst = /^en(-US)?$/i.test(lang) || lang.toUpperCase().endsWith('-US');
    const rest = monthFirst ? `${now.getMonth() + 1}/${now.getDate()}` : `${now.getDate()}/${now.getMonth() + 1}`;
    const size = (DATE_CAP / 0.711) * s;
    setFont(c, size);
    c.textAlign = 'left';
    c.fillStyle = css(this.col.date);
    const track = DATE_TRACKING * s;
    const width = (str) => c.measureText(str).width + track * str.length;
    const gap = c.measureText(' ').width + DATE_EXTRA_SPACE * s;
    const wDay = width(day), wRest = width(rest);
    const total = wDay + gap + wRest;
    let x = B.CX * s - total / 2;
    const spaced = (str, x0) => {
      for (const ch of str) {
        c.fillText(ch, x0, DATE_BASE * s);
        x0 += c.measureText(ch).width + track;
      }
    };
    spaced(day, x);
    spaced(rest, x + total - wRest);
  }

  menuGeometry() {
    const s = this.s;
    const n = Math.min(MENU_ROWS, this.menuItems.length);
    const pw = 1000 * s, itemH = 92 * s, gap = 22 * s, head = 150 * s, pad = 56 * s;
    const ph = head + n * itemH + (n - 1) * gap + pad;
    return { n, pw, itemH, gap, head, pad, ph, px: (this.w - pw) / 2, py: (this.h - ph) / 2 };
  }

  drawMenu(c) {
    const s = this.s, g = this.menuGeometry();
    c.fillStyle = 'rgba(0,0,0,0.47)';
    c.fillRect(0, 0, this.w, this.h);
    c.translate(0, (1 - this.menuAlpha) * 36 * s);
    const rad = 40 * s;
    c.beginPath();
    c.roundRect(g.px, g.py, g.pw, g.ph, rad);
    c.save();
    c.clip();
    if (this.base) c.drawImage(this.base, 0, 0);
    else {
      c.fillStyle = '#ededed';
      c.fill();
    }
    c.restore();
    c.strokeStyle = css(this.col.tileBorder);
    c.lineWidth = TILE_BORDER * s;
    c.stroke();
    setFont(c, 48 * s);
    c.textAlign = 'center';
    c.fillStyle = css(DATE_COLOR);
    c.fillText(this.menuTitle, this.w / 2, g.py + 92 * s);
    for (let row = 0; row < g.n; row++) {
      const i = this.menuFirst + row;
      const iy = g.py + g.head + row * (g.itemH + g.gap);
      const r = { l: g.px + 90 * s, t: iy, r: g.px + g.pw - 90 * s, b: iy + g.itemH };
      this.pill(c, r, i < this.menuLift.length ? this.menuLift[i] : 0, g.itemH / 2, 0);
      setFont(c, 40 * s);
      c.fillStyle = '#666666';
      c.textAlign = 'center';
      c.fillText(ellipsize(c, this.menuItems[i].label, r.r - r.l - 70 * s), (r.l + r.r) / 2, (r.t + r.b) / 2 + 14 * s);
    }
    c.fillStyle = css(this.col.accent);
    if (this.menuFirst > 0) this.chevron(c, this.w / 2, g.py + g.head - 26 * s, true);
    if (this.menuFirst + g.n < this.menuItems.length) this.chevron(c, this.w / 2, g.py + g.ph - 22 * s, false);
  }

  /** A white rounded button with the grey rim, glowing blue as it is focused; `grow` is how much it swells. */
  pill(c, r, lf, rad, grow = 0.03) {
    const s = this.s;
    c.save();
    const sc = 1 + grow * lf, cx = (r.l + r.r) / 2, cy = (r.t + r.b) / 2;
    c.translate(cx, cy);
    c.scale(sc, sc);
    c.translate(-cx, -cy);
    c.beginPath();
    c.roundRect(r.l, r.t, r.r - r.l, r.b - r.t, rad);
    const g = c.createLinearGradient(0, r.t, 0, r.b);
    g.addColorStop(0, '#ffffff');
    g.addColorStop(1, grow ? '#e1e1e1' : '#e2e2e2');
    c.fillStyle = g;
    c.fill();
    if (lf > 0.01) {
      for (let k = 3; k >= 1; k--) {
        c.strokeStyle = css(withAlpha(this.col.accent, (lf * 70) / k));
        c.lineWidth = (4 + k * 6) * s;
        c.stroke();
      }
    }
    c.strokeStyle = css(blend(this.col.tileBorder, this.col.accent, lf));
    c.lineWidth = (TILE_BORDER + 2 * lf) * s;
    c.stroke();
    c.restore();
  }

  chevron(c, cx, cy, up) {
    const s = this.s, hw = 18 * s, hh = 10 * s * (up ? -1 : 1);
    c.beginPath();
    c.moveTo(cx - hw, cy - hh);
    c.lineTo(cx + hw, cy - hh);
    c.lineTo(cx, cy + hh);
    c.closePath();
    c.fill();
  }

  drawSettings(c) {
    const s = this.s;
    if (this.base) c.drawImage(this.base, 0, 0, this.base.width, Math.round(760 * s), 0, 0, this.w, this.h);
    else {
      c.fillStyle = '#e9e9e9';
      c.fillRect(0, 0, this.w, this.h);
    }
    if (!this.settings.length) return;
    const p = this.top(), sheets = this.sheets(p);
    setFont(c, 54 * s);
    c.textAlign = 'left';
    c.fillStyle = css(DATE_COLOR);
    c.fillText(p.title, 150 * s, 150 * s);
    if (sheets > 1) {
      c.textAlign = 'right';
      setFont(c, 46 * s);
      c.fillText(`${p.sheet + 1} / ${sheets}`, 1770 * s, 150 * s);
    }
    c.strokeStyle = '#c4c4c4';
    c.lineWidth = 3 * s;
    c.beginPath();
    c.moveTo(120 * s, 186 * s);
    c.lineTo(1800 * s, 186 * s);
    c.stroke();
    const first = p.sheet * SETTINGS_ROWS, onSheet = Math.min(SETTINGS_ROWS, p.items.length - first);
    for (let r = 0; r < onSheet; r++) {
      const item = p.items[first + r];
      const top = SET_BTN_TOP + r * SET_BTN_PITCH;
      const rect = { l: (B.CX - SET_BTN_W / 2) * s, t: top * s, r: (B.CX + SET_BTN_W / 2) * s, b: (top + SET_BTN_H) * s };
      this.pill(c, rect, this.settingsLift[r], Math.min((rect.b - rect.t) / 2, 40 * s));
      setFont(c, 46 * s);
      const cy = (rect.t + rect.b) / 2 + 16 * s, wdt = rect.r - rect.l;
      if (item.value == null) {
        c.textAlign = 'center';
        c.fillStyle = '#5e5e5e';
        c.fillText(ellipsize(c, item.label, wdt - 80 * s), (rect.l + rect.r) / 2, cy);
      } else {
        c.textAlign = 'right';
        c.fillStyle = '#2e86bf';
        const vw = Math.min(c.measureText(item.value).width, wdt * 0.45);
        c.fillText(ellipsize(c, item.value, vw), rect.r - 64 * s, cy);
        c.textAlign = 'left';
        c.fillStyle = '#5e5e5e';
        c.fillText(ellipsize(c, item.label, wdt - vw - 170 * s), rect.l + 64 * s, cy);
      }
    }
    if (p.sheet < sheets - 1) this.drawArrow(c, ARROW_TIP * s, SET_ARROW_Y * s, false);
    if (p.sheet > 0) this.drawArrow(c, (2 * B.CX - ARROW_TIP) * s, SET_ARROW_Y * s, true);
    const g = c.createLinearGradient(0, SET_BAND_TOP * s, 0, this.h);
    g.addColorStop(0, '#f2f2f2');
    g.addColorStop(1, '#d3d3d6');
    c.fillStyle = g;
    c.fillRect(0, SET_BAND_TOP * s, this.w, this.h - SET_BAND_TOP * s);
    c.strokeStyle = css(this.col.accent);
    c.lineWidth = 5 * s;
    c.beginPath();
    c.moveTo(0, SET_BAND_TOP * s);
    c.lineTo(this.w, SET_BAND_TOP * s);
    c.stroke();
    const br = { l: SET_BACK[0] * s, t: SET_BACK[1] * s, r: SET_BACK[2] * s, b: SET_BACK[3] * s };
    this.pill(c, br, this.settingsLift[SETTINGS_ROWS], Math.min((br.b - br.t) / 2, 40 * s));
    c.textAlign = 'center';
    setFont(c, 44 * s);
    c.fillStyle = '#5e5e5e';
    c.fillText(this.backLabel, (br.l + br.r) / 2, (br.t + br.b) / 2 + 15 * s);
  }

  drawIntro(c) {
    const s = this.s, w = this.w;
    c.fillStyle = '#ffffff';
    c.fillRect(0, 0, w, this.h);
    const left = 240 * s, headY = 340 * s;
    this.drawWarningSign(c, left + 34 * s, headY - 22 * s, 38 * s);
    setFont(c, 64 * s);
    c.textAlign = 'left';
    c.fillStyle = '#1e1e1e';
    c.fillText(this.intro.title, left + 98 * s, headY);
    c.strokeStyle = '#dadada';
    c.lineWidth = 3 * s;
    c.beginPath();
    c.moveTo(left, headY + 40 * s);
    c.lineTo(w - left, headY + 40 * s);
    c.stroke();
    setFont(c, 50 * s, 'normal');
    c.fillStyle = '#3a3a3a';
    const maxW = w - 2 * left, lineH = 50 * s * 1.2;
    let line = '', y = headY + 100 * s + 50 * s;
    for (const word of this.intro.body.split(' ')) {
      const test = line ? line + ' ' + word : word;
      if (c.measureText(test).width > maxW && line) {
        c.fillText(line, left, y);
        line = word;
        y += lineH;
      } else line = test;
    }
    if (line) c.fillText(line, left, y);
    const t = (performance.now() - this.introStart) / 1000;
    const pulse = 0.58 + 0.42 * Math.cos((t * Math.PI * 2) / 1.6);
    const ca = Math.max(0.16, pulse);
    setFont(c, 52 * s);
    const tw = c.measureText(this.intro.cont).width;
    const iconR = 32 * s, gapIcon = 22 * s, total = 2 * iconR + gapIcon + tw;
    const sx = (w - total) / 2, baseY = 880 * s, icx = sx + iconR, icy = baseY - 18 * s;
    c.globalAlpha = ca;
    c.fillStyle = '#ffffff';
    c.beginPath();
    c.arc(icx, icy, iconR, 0, Math.PI * 2);
    c.fill();
    c.strokeStyle = '#9a9a9a';
    c.lineWidth = 4.5 * s;
    c.stroke();
    c.fillStyle = '#4a4a4a';
    c.fillText(this.intro.cont, sx + 2 * iconR + gapIcon, baseY);
    setFont(c, 22 * s);
    c.textAlign = 'center';
    c.fillStyle = '#777777';
    c.fillText('OK', icx, icy + 8 * s);
    c.globalAlpha = 1;
  }

  drawWarningSign(c, cx, cy, r) {
    const s = this.s;
    roundPoly(c, [[cx, cy - r], [cx + r * 1.1, cy + r * 0.85], [cx - r * 1.1, cy + r * 0.85]], 6 * s);
    c.fillStyle = '#ffc61e';
    c.fill();
    c.strokeStyle = '#1e1e1e';
    c.lineWidth = 4.5 * s;
    c.lineJoin = 'round';
    c.stroke();
    setFont(c, r * 1.25);
    c.textAlign = 'center';
    c.fillStyle = '#1e1e1e';
    c.fillText('!', cx, cy + r * 0.65);
  }

  drawNotice(c) {
    const s = this.s;
    setFont(c, 40 * s, 'normal');
    const tw = c.measureText(this.notice).width;
    const r = { l: (this.w - tw) / 2 - 50 * s, t: 24 * s, r: (this.w + tw) / 2 + 50 * s, b: 108 * s };
    this.pill(c, r, 0, 42 * s);
    c.fillStyle = '#555555';
    c.textAlign = 'center';
    c.fillText(this.notice, this.w / 2, 80 * s);
  }
}
