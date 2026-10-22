"""Downloads the Mii Channel files the pack is made from into $MIIPACK_DIR/src (default tools/miipack/build).

This repository keeps none of them: they are fetched from their public pages on The Spriters Resource and checked
against the SHA-256 they had when the pack was made, so every build comes out the same. When a site turns the
download away, open the page listed, download the file by hand and run again with --from <folder it is in>.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import DOWNLOADS, HERE, SRC  # noqa: E402

UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 16), b''):
            h.update(block)
    return h.hexdigest()


def find(folder, digest):
    for base, _, names in os.walk(folder):
        for n in names:
            p = os.path.join(base, n)
            if os.path.getsize(p) < 64 << 20 and sha256(p) == digest:
                return p
    return None


def download(url, path):
    referer = url.split('/media/')[0] + '/'
    try:
        req = urllib.request.Request(url, headers={'User-Agent': UA, 'Referer': referer})
        with urllib.request.urlopen(req, timeout=60) as r, open(path + '.part', 'wb') as f:
            shutil.copyfileobj(r, f)
    except OSError:
        # Python builds without the system's certificates (python.org's on macOS) fail TLS; curl has them.
        if not shutil.which('curl'):
            raise
        subprocess.run(['curl', '-fsL', '-A', UA, '-e', referer, '-o', path + '.part', url], check=True, timeout=120)
    os.replace(path + '.part', path)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--from', dest='src', help='folder with files already downloaded by hand')
    args = ap.parse_args()
    os.makedirs(DOWNLOADS, exist_ok=True)
    missing = []
    for e in json.load(open(os.path.join(HERE, 'sources.json'))):
        keep = DOWNLOADS + e['file']
        if not (os.path.exists(keep) and sha256(keep) == e['sha256']):
            got = find(args.src, e['sha256']) if args.src else None
            if got:
                shutil.copy(got, keep)
            else:
                try:
                    download(e['url'], keep)
                except (OSError, subprocess.SubprocessError):
                    pass
            if not os.path.exists(keep) or sha256(keep) != e['sha256']:
                print('%-16s missing' % e['name'])
                missing.append(e)
                continue
        if e['file'].endswith('.zip'):
            with zipfile.ZipFile(keep) as z:
                z.extractall(SRC + e['into'])
        else:
            os.makedirs(os.path.dirname(SRC + e['into']), exist_ok=True)
            shutil.copy(keep, SRC + e['into'])
        print('%-16s ok' % e['name'])
    if missing:
        print('\nDownload these by hand from their pages, then run: python3 fetch.py --from <folder>')
        for e in missing:
            print('  %s: %s' % (e['name'], e['page']))
        sys.exit(1)
    print('\nAll sources in', SRC)


if __name__ == '__main__':
    main()
