// Checks that the desktop menu still reads the Android launcher's shared sources correctly.
// Run with: node --test desktop/tests/*.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
execFileSync(process.execPath, [join(here, '..', 'scripts', 'prepare.mjs')]);

test('palette tables come through whole', async () => {
  const P = await import('../web/gen/palette.js');
  assert.equal(P.PANEL.length, P.PANEL_NX * P.PANEL_ND);
  assert.equal(P.BG_BASE.length, P.BG_AMP.length);
  assert.ok(P.TRACK_DELTA.length > 100);
});

test('backdrop renders the blue line where WiiBase puts it', async () => {
  const B = await import('../web/js/base.js');
  const w = 960, h = 540, px = B.backdrop(w, h);
  assert.equal(px.length, w * h * 4);
  // Centre column at the line's height (898.48 ref px -> row 449 at half scale): the line's own blue.
  const o = (449 * w + 480) * 4;
  assert.deepEqual([px[o], px[o + 1], px[o + 2]], [0x3e, 0xbc, 0xe8]);
  // Top-left: the light grey stripes.
  assert.ok(px[0] > 200 && Math.abs(px[0] - px[2]) < 4);
});

test('menu tune renders at the length MenuMusic.java gives it', async () => {
  const { render } = await import('../web/js/audio.js');
  const song = (await import('../web/gen/menu_song.js')).default;
  const pcm = render(song);
  assert.equal(pcm.length, Math.floor(22050 * (240 / song.bpm) * song.chords.length));
  let peak = 0;
  for (const v of pcm) peak = Math.max(peak, Math.abs(v));
  assert.ok(Math.abs(peak - 0.7) < 1e-3);
});
