"""Where the pack tools read the downloaded sources and write the pack: $MIIPACK_DIR, or tools/miipack/build."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.environ.get('MIIPACK_DIR') or os.path.join(HERE, 'build')
SRC = os.path.join(ROOT, 'src') + '/'
OUT = os.path.join(ROOT, 'out', 'pack') + '/'
ZIP = os.path.join(ROOT, 'out', 'miipack.zip')
DOWNLOADS = os.path.join(ROOT, 'downloads') + '/'
