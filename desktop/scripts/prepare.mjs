// Copies what the desktop menu shares with the Android launcher into web/gen/, so there is one source for each:
// the colour tables measured off the console capture (WiiPalette.java), the interface sounds and the font.
// Run before every desktop build (tauri.conf.json's beforeBuildCommand / beforeDevCommand).
import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const android = join(root, 'launcher', 'app', 'src', 'main');
const out = join(here, '..', 'web', 'gen');
mkdirSync(out, { recursive: true });

// WiiPalette.java -> palette.js: every `static final <type> NAME = value;` (numbers and number arrays).
const java = readFileSync(join(android, 'java', 'com', 'gathin', 'tvhome', 'WiiPalette.java'), 'utf8');
const consts = [];
const re = /static final (?:int|float)(\[\])?\s+(\w+)\s*=\s*([^;]+);/g;
let m;
while ((m = re.exec(java))) {
  let v = m[3].replace(/\/\/.*$/gm, '').replace(/(\d)f\b/g, '$1').replace(/\s+/g, ' ').trim();
  if (m[1]) v = '[' + v.replace(/^\{/, '').replace(/\}$/, '').trim().replace(/,$/, '') + ']';
  consts.push(`export const ${m[2]} = ${v};`);
}
if (!consts.some((c) => c.startsWith('export const PANEL ='))) throw new Error('WiiPalette.java: PANEL not found');
writeFileSync(join(out, 'palette.js'), '// Generated from WiiPalette.java by scripts/prepare.mjs; do not edit.\n' + consts.join('\n') + '\n');

// MenuMusic.java -> menu_song.js: the channel menu's tune (tempo, chords, roots, melody and rhythms).
const music = readFileSync(join(android, 'java', 'com', 'gathin', 'tvhome', 'MenuMusic.java'), 'utf8');
const arr = (name) => {
  const mm = new RegExp(`static final (?:int|double)\\[\\](?:\\[\\])? ${name} = (\\{[\\s\\S]*?\\});`).exec(music);
  if (!mm) throw new Error(`MenuMusic.java: ${name} not found`);
  return mm[1].replace(/\/\/.*$/gm, '').replace(/\{/g, '[').replace(/\}/g, ']').replace(/,(\s*\])/g, '$1').replace(/\s+/g, ' ');
};
const bpm = /static final Song MENU = new Song\(([\d.]+),/.exec(music);
if (!bpm) throw new Error('MenuMusic.java: MENU not found');
writeFileSync(join(out, 'menu_song.js'), '// Generated from MenuMusic.java by scripts/prepare.mjs; do not edit.\n'
  + `export default { bpm: ${bpm[1]}, chords: ${arr('CHORDS')}, roots: ${arr('ROOTS')}, melody: ${arr('MELODY')}, comp: ${arr('COMP')}, bass: ${arr('BASS')} };\n`);

// Theme.java -> themes.js: every `static final Theme NAME = new Theme(...)`, fields in the constructor's order.
const themeJava = readFileSync(join(android, 'java', 'com', 'gathin', 'tvhome', 'Theme.java'), 'utf8');
const FIELDS = ['bgTopDark', 'bgTopLight', 'bgBottomDark', 'bgBottomLight', 'panelDark', 'panelLight', 'buttonDark',
  'buttonLight', 'slotDark', 'slotLight', 'accent', 'clock', 'date', 'wordmark', 'tileBorder', 'iconTileTop',
  'iconTileBottom', 'iconTileText'];
const themes = [];
const tre = /static final Theme (\w+) = new Theme\(([\s\S]*?)\);/g;
while ((m = tre.exec(themeJava))) {
  const args = m[2].replace(/\/\/.*$/gm, '').split(',').map((x) => x.trim());
  if (args.length !== FIELDS.length + 3) throw new Error(`Theme.java: ${m[1]} has ${args.length} arguments`);
  const t = { id: JSON.parse(args[0]), classic: args[1] === 'true' };
  FIELDS.forEach((f, i) => (t[f] = Number(args[i + 2])));
  t.deco = args[args.length - 1].replace('Deco.', '');
  themes.push(t);
}
if (!themes.some((t) => t.id === 'classic')) throw new Error('Theme.java: CLASSIC not found');
writeFileSync(join(out, 'themes.js'), '// Generated from Theme.java by scripts/prepare.mjs; do not edit.\nexport default '
  + JSON.stringify(themes) + ';\n');

for (const s of ['move', 'select', 'page', 'back', 'menu', 'start']) {
  copyFileSync(join(android, 'res', 'raw', `wii_${s}.wav`), join(out, `wii_${s}.wav`));
}
copyFileSync(join(android, 'res', 'font', 'outfit.ttf'), join(out, 'outfit.ttf'));
console.log(`prepare: ${consts.length} palette constants, menu tune, ${themes.length} themes, 6 sounds, font -> web/gen`);
