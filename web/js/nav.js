// Pure D-pad navigation logic for the channel grid and bottom bar.
// Kept free of DOM access so it can be unit tested with `node --test`.

export const COLS = 4;
export const ROWS = 3;
export const PER_PAGE = COLS * ROWS;

// Bottom bar buttons, left to right.
export const BAR = ['wii', 'mail'];

export function pageCount(channelCount) {
  return Math.max(1, Math.ceil(channelCount / PER_PAGE));
}

export function indexOf({ page, row, col }) {
  return page * PER_PAGE + row * COLS + col;
}

export function initialState() {
  return { zone: 'grid', page: 0, row: 0, col: 0 };
}

/**
 * Move the selection one step.
 * @param {object} state  {zone:'grid', page,row,col} | {zone:'bar', page, index}
 * @param {'up'|'down'|'left'|'right'} dir
 * @param {number} pages  total number of pages
 * @returns {{state: object, effect: 'move'|'page'|'bump'}}
 */
export function move(state, dir, pages) {
  const s = { ...state };

  if (s.zone === 'bar') {
    if (dir === 'up') {
      return {
        state: { zone: 'grid', page: s.page, row: ROWS - 1, col: s.index === 0 ? 0 : COLS - 1 },
        effect: 'move',
      };
    }
    if (dir === 'left' && s.index > 0) return { state: { ...s, index: s.index - 1 }, effect: 'move' };
    if (dir === 'right' && s.index < BAR.length - 1) return { state: { ...s, index: s.index + 1 }, effect: 'move' };
    return { state: s, effect: 'bump' };
  }

  switch (dir) {
    case 'up':
      if (s.row > 0) return { state: { ...s, row: s.row - 1 }, effect: 'move' };
      return { state: s, effect: 'bump' };
    case 'down':
      if (s.row < ROWS - 1) return { state: { ...s, row: s.row + 1 }, effect: 'move' };
      return { state: { zone: 'bar', page: s.page, index: s.col < COLS / 2 ? 0 : 1 }, effect: 'move' };
    case 'left':
      if (s.col > 0) return { state: { ...s, col: s.col - 1 }, effect: 'move' };
      if (s.page > 0) return { state: { ...s, page: s.page - 1, col: COLS - 1 }, effect: 'page' };
      return { state: s, effect: 'bump' };
    case 'right':
      if (s.col < COLS - 1) return { state: { ...s, col: s.col + 1 }, effect: 'move' };
      if (s.page < pages - 1) return { state: { ...s, page: s.page + 1, col: 0 }, effect: 'page' };
      return { state: s, effect: 'bump' };
    default:
      return { state: s, effect: 'bump' };
  }
}

/** Jump to a page directly (used by the on-screen page arrows). */
export function turnPage(state, delta, pages) {
  const page = Math.min(pages - 1, Math.max(0, state.page + delta));
  if (page === state.page) return { state, effect: 'bump' };
  const col = delta > 0 ? 0 : COLS - 1;
  const row = state.zone === 'grid' ? state.row : 0;
  return { state: { zone: 'grid', page, row, col }, effect: 'page' };
}

/** Keep a state valid after the channel list changes size. */
export function clamp(state, pages) {
  if (state.page < pages) return state;
  return { ...state, page: pages - 1 };
}

/**
 * Order apps: pinned packages first (in the given order), then the rest
 * alphabetically by label. Hidden packages are dropped.
 */
export function orderApps(apps, { pinned = [], hidden = [] } = {}) {
  const hiddenSet = new Set(hidden);
  const visible = apps.filter((a) => !hiddenSet.has(a.pkg));
  const byPkg = new Map(visible.map((a) => [a.pkg, a]));
  const first = pinned.map((p) => byPkg.get(p)).filter(Boolean);
  const firstSet = new Set(first.map((a) => a.pkg));
  const rest = visible
    .filter((a) => !firstSet.has(a.pkg))
    .sort((a, b) => a.label.localeCompare(b.label, undefined, { sensitivity: 'base' }));
  return [...first, ...rest];
}
