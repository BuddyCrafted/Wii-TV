# Draws YouTubeHD's icons (webos/youtubehd/*.png, copied over youtube-webos's own by webos/youtubehd.sh): YouTube's
# red play button with big "HD" letters under it. Usage: python3 webos/youtubehd/icons.py <bold font file>
import os
import sys
from PIL import Image, ImageDraw, ImageFont

here = os.path.dirname(os.path.abspath(__file__))
font = sys.argv[1]
RED = (255, 0, 51)
INK = (15, 15, 15)


def draw(n):
    k = 4  # drawn larger, then scaled down for smooth edges
    s = n * k
    img = Image.new('RGBA', (s, s), (255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    # The play button: a rounded red rectangle with a white triangle.
    bw, bh = s * 0.62, s * 0.44
    x0, y0 = (s - bw) / 2, s * 0.09
    d.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], radius=bh * 0.28, fill=RED)
    cx, cy, t = s / 2 + bh * 0.04, y0 + bh / 2, bh * 0.22
    d.polygon([(cx - t * 0.85, cy - t), (cx - t * 0.85, cy + t), (cx + t * 1.05, cy)], fill=(255, 255, 255))
    # "HD", as big as fits under it.
    size = int(s * 0.46)
    f = ImageFont.truetype(font, size)
    while f.getbbox('HD')[2] - f.getbbox('HD')[0] > s * 0.84:
        size -= 2
        f = ImageFont.truetype(font, size)
    l, t2, r, b = f.getbbox('HD')
    top = y0 + bh + (s - (y0 + bh) - (b - t2)) / 2 - t2 - s * 0.01
    d.text(((s - (r - l)) / 2 - l, top), 'HD', font=f, fill=INK)
    return img.resize((n, n), Image.LANCZOS)


for name, n in [('icon', 80), ('mediumLargeIcon', 115), ('largeIcon', 130), ('extraLargeIcon', 192)]:
    draw(n).save(os.path.join(here, f'{name}.png'))
