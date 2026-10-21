import { test } from 'node:test';
import assert from 'node:assert/strict';
import { COLS, ROWS, pageCount, indexOf, initialState, move, turnPage, clamp, orderApps } from '../web/js/nav.js';

test('pageCount always has at least one page', () => {
  assert.equal(pageCount(0), 1);
  assert.equal(pageCount(12), 1);
  assert.equal(pageCount(13), 2);
});

test('indexOf maps page/row/col to a channel index', () => {
  assert.equal(indexOf({ page: 0, row: 0, col: 0 }), 0);
  assert.equal(indexOf({ page: 0, row: 2, col: 3 }), 11);
  assert.equal(indexOf({ page: 1, row: 1, col: 1 }), 17);
});

test('moving within the grid', () => {
  const s = initialState();
  assert.deepEqual(move(s, 'right', 1), { state: { ...s, col: 1 }, effect: 'move' });
  assert.deepEqual(move(s, 'down', 1), { state: { ...s, row: 1 }, effect: 'move' });
  assert.equal(move(s, 'up', 1).effect, 'bump');
  assert.equal(move(s, 'left', 1).effect, 'bump');
});

test('right from the last column turns the page', () => {
  const s = { zone: 'grid', page: 0, row: 1, col: COLS - 1 };
  assert.deepEqual(move(s, 'right', 2), { state: { zone: 'grid', page: 1, row: 1, col: 0 }, effect: 'page' });
  assert.equal(move(s, 'right', 1).effect, 'bump');
});

test('left from the first column goes back a page', () => {
  const s = { zone: 'grid', page: 1, row: 2, col: 0 };
  assert.deepEqual(move(s, 'left', 2), { state: { zone: 'grid', page: 0, row: 2, col: COLS - 1 }, effect: 'page' });
});

test('down from the bottom row reaches the bar, nearest button', () => {
  const left = { zone: 'grid', page: 0, row: ROWS - 1, col: 1 };
  const right = { zone: 'grid', page: 0, row: ROWS - 1, col: 2 };
  assert.deepEqual(move(left, 'down', 1).state, { zone: 'bar', page: 0, index: 0 });
  assert.deepEqual(move(right, 'down', 1).state, { zone: 'bar', page: 0, index: 1 });
});

test('bar navigation and returning to the grid keeps the page', () => {
  const bar = { zone: 'bar', page: 2, index: 0 };
  assert.deepEqual(move(bar, 'right', 3).state, { zone: 'bar', page: 2, index: 1 });
  assert.equal(move(bar, 'left', 3).effect, 'bump');
  assert.equal(move(bar, 'down', 3).effect, 'bump');
  assert.deepEqual(move({ ...bar, index: 1 }, 'up', 3).state, { zone: 'grid', page: 2, row: ROWS - 1, col: COLS - 1 });
});

test('turnPage clamps to the available pages', () => {
  const s = initialState();
  assert.equal(turnPage(s, -1, 2).effect, 'bump');
  assert.deepEqual(turnPage(s, 1, 2).state, { zone: 'grid', page: 1, row: 0, col: 0 });
});

test('clamp pulls the selection back when pages disappear', () => {
  assert.equal(clamp({ zone: 'grid', page: 3, row: 0, col: 0 }, 2).page, 1);
});

test('orderApps puts pinned first, hides hidden, sorts the rest', () => {
  const apps = [
    { pkg: 'c', label: 'charlie' },
    { pkg: 'a', label: 'Alpha' },
    { pkg: 'x', label: 'Hidden' },
    { pkg: 'b', label: 'bravo' },
    { pkg: 'z', label: 'Zulu' },
  ];
  const out = orderApps(apps, { pinned: ['z', 'missing', 'c'], hidden: ['x'] }).map((a) => a.pkg);
  assert.deepEqual(out, ['z', 'c', 'a', 'b']);
});
