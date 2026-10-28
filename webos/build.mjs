// Builds the LG webOS app in webos/dist from the desktop menu (desktop/web), which is the same channel menu:
// webOS runs it as a web app, and desktop/web/js/platform.js talks to the TV through webOS's own services.
// Then `npx @webos-tools/cli ares-package webos/dist` makes the .ipk (see .github/workflows/ci.yml).
// Usage: node webos/build.mjs [build number]
import { cpSync, readFileSync, writeFileSync, rmSync, copyFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const dist = join(here, 'dist');
const build = parseInt(process.argv[2] || '0', 10) || 0;
const version = readFileSync(join(root, 'VERSION'), 'utf8').trim();

execFileSync(process.execPath, [join(root, 'desktop', 'scripts', 'prepare.mjs')], { stdio: 'inherit' });
rmSync(dist, { recursive: true, force: true });
cpSync(join(root, 'desktop', 'web'), dist, { recursive: true });
for (const f of ['icon.png', 'largeIcon.png']) copyFileSync(join(here, f), join(dist, f));
// YouTubeHD's channel shows its own icon (webos/youtubehd).
copyFileSync(join(here, 'youtubehd', 'extraLargeIcon.png'), join(dist, 'youtubehd.png'));

// webOS wants a three-part number; the build number keeps each one newer than the last.
const app = JSON.parse(readFileSync(join(here, 'appinfo.json'), 'utf8'));
app.version = `${version.split('.').concat('0', '0').slice(0, 2).join('.')}.${build}`;
writeFileSync(join(dist, 'appinfo.json'), JSON.stringify(app, null, 2) + '\n');

// One plain script for older TVs: webOS 6 (2021) runs Chromium 79, which won't load module scripts from the
// app's own files and doesn't know newer syntax; esbuild joins the modules and writes it for Chrome 68 (webOS 5).
execFileSync(process.platform === 'win32' ? 'npx.cmd' : 'npx', ['--yes', 'esbuild@0.24.0', join(dist, 'js', 'app.js'),
  '--bundle', '--format=iife', '--target=chrome68', `--outfile=${join(dist, 'app.js')}`], { stdio: 'inherit' });
rmSync(join(dist, 'js'), { recursive: true, force: true });

// The version Settings shows (platform.js reads it), then the bundle as a plain script.
const html = readFileSync(join(dist, 'index.html'), 'utf8');
const info = `<script>window.WIIOS_INFO = ${JSON.stringify({ version, build })};</script>\n`;
const out = html.replace('<script type="module" src="js/app.js"></script>', info + '<script src="app.js"></script>');
if (out === html) throw new Error('index.html: the menu script tag changed; update webos/build.mjs');
writeFileSync(join(dist, 'index.html'), out);
console.log(`webos: ${app.id} ${app.version} -> webos/dist`);
