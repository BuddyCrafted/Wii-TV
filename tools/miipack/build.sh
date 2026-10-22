#!/bin/sh
# Builds miipack.zip from the sources fetch.py put in place.
set -e
cd "$(dirname "$0")"
python3 render3d.py
python3 parts2d.py > /dev/null
python3 ui.py
python3 make_pack.py
