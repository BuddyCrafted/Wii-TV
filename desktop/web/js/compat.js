// Older browser engines: LG's 2021 TVs (webOS 6) run Chromium 79, which has no canvas roundRect (Chrome 99+).
// Loaded first; does nothing where the real one exists.
if (typeof CanvasRenderingContext2D !== 'undefined' && !CanvasRenderingContext2D.prototype.roundRect) {
  CanvasRenderingContext2D.prototype.roundRect = function roundRect(x, y, w, h, r) {
    let rad = Array.isArray(r) ? r[0] : r;
    rad = Math.max(0, Math.min(Number(rad) || 0, Math.abs(w) / 2, Math.abs(h) / 2));
    this.moveTo(x + rad, y);
    this.arcTo(x + w, y, x + w, y + h, rad);
    this.arcTo(x + w, y + h, x, y + h, rad);
    this.arcTo(x, y + h, x, y, rad);
    this.arcTo(x, y, x + w, y, rad);
    this.closePath();
  };
}

/** Reads a file of the app as an ArrayBuffer or text; XMLHttpRequest also works where fetch can't (file://). */
export function load(url, type = 'arraybuffer') {
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open('GET', url);
    x.responseType = type;
    x.onload = () => (x.status === 200 || (x.status === 0 && x.response) ? resolve(x.response) : reject(new Error(`HTTP ${x.status}`)));
    x.onerror = () => reject(new Error(`can't read ${url}`));
    x.send();
  });
}
