#!/bin/sh
# Builds YouTubeHD for LG TVs: webosbrew's youtube-webos (GPL-3.0, https://github.com/webosbrew/youtube-webos), the
# ad-free YouTube TV app with SponsorBlock, at the commit below, changed only in its app id and name so it installs
# next to LG's own YouTube instead of replacing it. Its source is that commit plus the two edits here.
# Usage: webos/youtubehd.sh <output .ipk>
set -eu
COMMIT=f1b3b72926bb0cc312b5ceddc6a5b8c8ca081914
out="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
work=$(mktemp -d)
git -C "$work" init -q
git -C "$work" fetch -q --depth 1 https://github.com/webosbrew/youtube-webos "$COMMIT"
git -C "$work" checkout -q FETCH_HEAD
cd "$work"
# Its own id and name (the edits), and without the packaging tool it pulls from a GitHub tarball (LG's own is used).
node -e '
  const fs = require("fs");
  const a = JSON.parse(fs.readFileSync("assets/appinfo.json"));
  a.id = "com.buddycrafted.youtubehd"; a.title = "YouTubeHD";
  fs.writeFileSync("assets/appinfo.json", JSON.stringify(a, null, 2) + "\n");
  const p = JSON.parse(fs.readFileSync("package.json"));
  delete p.devDependencies["@webos-tools/cli"];
  fs.writeFileSync("package.json", JSON.stringify(p, null, 2) + "\n");'
corepack enable >/dev/null 2>&1 || true
pnpm install --no-frozen-lockfile
pnpm run build
cd /
npx --yes -p @webos-tools/cli@3.2.6 ares-package -n "$work/dist" -o "$work/out"
cp "$work"/out/*.ipk "$out"
echo "YouTubeHD -> $out"
