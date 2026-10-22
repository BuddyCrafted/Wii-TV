// The pointing hand, ported from WiiHand.java: upright, index finger up, three folded fingers to its right,
// thumb on the left, a thick dark outline, light blue over the lower palm and the player number on it.
// Drawn with its fingertip at (x, y); u is one hand unit in pixels (the hand is ~55 units tall).
// Canvas has no path union, so the outline is stroked at twice its width under the fill: the fill then
// covers the inner half and every seam between the parts, leaving the outer half as the outline.

const PARTS = [
  [-5.5, 0, 5.5, 36, 5.5], // index finger
  [4.5, 17, 12.5, 34, 4], // middle finger, folded
  [11.5, 19, 19, 35, 3.8], // ring finger
  [18, 21, 24.5, 36, 3.3], // little finger
  [-9, 27, 24.5, 55, 8.5], // palm
  [-15.5, 25.5, -3, 41, 6], // thumb
];

function shape(c, u) {
  c.beginPath();
  for (const [l, t, r, b, rad] of PARTS) c.roundRect(l * u, t * u, (r - l) * u, (b - t) * u, rad * u);
}

export function drawHand(c, x, y, u, font) {
  c.save();
  c.translate(x, y);
  c.save();
  c.translate(2.5 * u, 3.5 * u);
  shape(c, u);
  c.fillStyle = 'rgba(0,0,0,0.25)';
  c.lineWidth = 5.6 * u;
  c.strokeStyle = 'rgba(0,0,0,0.25)';
  c.lineJoin = 'round';
  c.stroke();
  c.restore();
  shape(c, u);
  c.strokeStyle = '#161616';
  c.lineJoin = 'round';
  c.lineWidth = 5.6 * u;
  c.stroke();
  const g = c.createLinearGradient(0, 30 * u, 0, 55 * u);
  g.addColorStop(0, '#ffffff');
  g.addColorStop(1, '#a9d8f6');
  c.fillStyle = g;
  c.fill();
  c.beginPath();
  for (const [lx, t, b] of [[5, 24, 32], [12, 25, 33], [18.5, 27, 34]]) {
    c.moveTo(lx * u, t * u);
    c.lineTo(lx * u, b * u);
  }
  c.lineWidth = 1.6 * u;
  c.lineCap = 'round';
  c.stroke();
  c.font = `bold ${19 * u}px ${font}`;
  c.textAlign = 'center';
  c.textBaseline = 'alphabetic';
  c.fillStyle = '#1c8ce3';
  c.fillText('1', 9 * u, 50.5 * u);
  c.restore();
}
