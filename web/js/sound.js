// All sounds are synthesised at runtime with the Web Audio API:
// no audio files, nothing taken from Nintendo.

let ctx = null;
let sfxGain = null;
let musicGain = null;
let musicTimer = null;
let nextNoteTime = 0;
let step = 0;

const state = { sounds: true, music: true, soundVolume: 0.6, musicVolume: 0.35 };

function ensure() {
  if (ctx) return ctx;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return null;
  ctx = new AC();
  const master = ctx.createDynamicsCompressor();
  master.connect(ctx.destination);
  sfxGain = ctx.createGain();
  sfxGain.gain.value = state.soundVolume;
  sfxGain.connect(master);
  musicGain = ctx.createGain();
  musicGain.gain.value = 0;
  musicGain.connect(master);
  return ctx;
}

/** Call from any user input; browsers keep audio suspended until then. */
export function unlock() {
  const c = ensure();
  if (c && c.state === 'suspended') c.resume();
  if (state.music && !musicTimer) startMusic();
}

export function configure(opts) {
  Object.assign(state, opts);
  if (!ctx) return;
  sfxGain.gain.value = state.soundVolume;
  if (state.music) startMusic();
  else stopMusic();
}

function tone(freq, start, dur, { type = 'sine', gain = 0.3, attack = 0.005, dest = sfxGain, detune = 0 } = {}) {
  const o = ctx.createOscillator();
  const g = ctx.createGain();
  o.type = type;
  o.frequency.setValueAtTime(freq, start);
  o.detune.value = detune;
  g.gain.setValueAtTime(0, start);
  g.gain.linearRampToValueAtTime(gain, start + attack);
  g.gain.exponentialRampToValueAtTime(0.0001, start + dur);
  o.connect(g).connect(dest);
  o.start(start);
  o.stop(start + dur + 0.05);
  return o;
}

function noise(start, dur, { from = 800, to = 4000, gain = 0.15 } = {}) {
  const len = Math.ceil(ctx.sampleRate * dur);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const d = buf.getChannelData(0);
  for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
  const src = ctx.createBufferSource();
  src.buffer = buf;
  const f = ctx.createBiquadFilter();
  f.type = 'bandpass';
  f.Q.value = 1.2;
  f.frequency.setValueAtTime(from, start);
  f.frequency.exponentialRampToValueAtTime(to, start + dur);
  const g = ctx.createGain();
  g.gain.setValueAtTime(0, start);
  g.gain.linearRampToValueAtTime(gain, start + dur * 0.3);
  g.gain.exponentialRampToValueAtTime(0.0001, start + dur);
  src.connect(f).connect(g).connect(sfxGain);
  src.start(start);
}

const SFX = {
  // Soft "tick" when the hand moves onto a channel.
  move(t) {
    tone(2093, t, 0.06, { gain: 0.12 });
    tone(3136, t + 0.012, 0.05, { gain: 0.05 });
  },
  // Bright two-note chime when a channel opens.
  select(t) {
    tone(1046.5, t, 0.25, { type: 'triangle', gain: 0.22 });
    tone(1568, t + 0.07, 0.35, { type: 'triangle', gain: 0.2 });
    tone(2093, t + 0.07, 0.35, { gain: 0.06 });
  },
  // Whoosh for turning the page.
  page(t) {
    noise(t, 0.28, { from: 600, to: 5000, gain: 0.18 });
    tone(784, t + 0.05, 0.15, { gain: 0.06 });
  },
  // Descending blip for "back".
  back(t) {
    tone(1318.5, t, 0.12, { type: 'triangle', gain: 0.16 });
    tone(880, t + 0.06, 0.18, { type: 'triangle', gain: 0.14 });
  },
  // Dull knock when nothing can happen.
  bump(t) {
    tone(180, t, 0.09, { type: 'square', gain: 0.05 });
    tone(120, t, 0.12, { gain: 0.15 });
  },
  // Rising swell when a channel actually starts.
  start(t) {
    [523.25, 659.25, 783.99, 1046.5, 1318.5].forEach((f, i) =>
      tone(f, t + i * 0.06, 0.6, { type: 'triangle', gain: 0.12 }),
    );
    noise(t, 0.7, { from: 2000, to: 9000, gain: 0.06 });
  },
  // Pop for opening an overlay.
  open(t) {
    tone(660, t, 0.08, { gain: 0.15 });
    tone(990, t + 0.04, 0.14, { gain: 0.15 });
  },
};

export function play(name) {
  if (!state.sounds) return;
  const c = ensure();
  if (!c || !SFX[name]) return;
  if (c.state === 'suspended') c.resume();
  SFX[name](c.currentTime + 0.005);
}

// ---- Background music --------------------------------------------------
// An original, gentle "menu" loop: a music-box melody over a soft
// electric-piano arpeggio. 16 steps per bar, 8 bars, ~100 BPM.

const BPM = 100;
const STEP = 60 / BPM / 4; // sixteenth note
const N = (m) => 440 * Math.pow(2, (m - 69) / 12); // MIDI → Hz

// Chords per bar (MIDI notes).
const CHORDS = [
  [48, 55, 64, 71], // Cmaj7
  [45, 52, 60, 67], // Am7
  [50, 57, 65, 72], // Dm7
  [43, 50, 59, 65], // G7
  [41, 48, 57, 64], // Fmaj7
  [40, 47, 55, 62], // Em7
  [50, 57, 65, 72], // Dm7
  [43, 50, 55, 62], // Gsus-ish
];

// Melody: [bar, step, midi, lengthInSteps]
const MELODY = [
  [0, 0, 76, 3], [0, 4, 79, 2], [0, 6, 76, 2], [0, 8, 74, 4], [0, 12, 72, 4],
  [1, 0, 72, 2], [1, 2, 74, 2], [1, 4, 76, 4], [1, 10, 69, 6],
  [2, 0, 77, 3], [2, 4, 76, 2], [2, 6, 74, 2], [2, 8, 72, 4], [2, 12, 74, 4],
  [3, 0, 71, 6], [3, 8, 74, 2], [3, 10, 77, 2], [3, 12, 79, 4],
  [4, 0, 81, 3], [4, 4, 79, 2], [4, 6, 77, 2], [4, 8, 76, 6],
  [5, 0, 74, 2], [5, 2, 76, 2], [5, 4, 79, 4], [5, 10, 76, 6],
  [6, 0, 77, 2], [6, 2, 76, 2], [6, 4, 74, 2], [6, 6, 72, 2], [6, 8, 74, 4], [6, 12, 77, 4],
  [7, 0, 76, 4], [7, 4, 74, 4], [7, 8, 72, 8],
];
const ARP = [0, 2, 1, 3, 2, 1, 3, 2]; // chord-tone order, one per eighth note
const TOTAL_STEPS = CHORDS.length * 16;

function scheduleStep(i, t) {
  const bar = Math.floor(i / 16);
  const s = i % 16;
  const chord = CHORDS[bar];

  if (s === 0) tone(N(chord[0] - 12), t, STEP * 14, { gain: 0.09, attack: 0.02, dest: musicGain });
  if (s % 2 === 0) {
    const note = chord[ARP[(s / 2) % ARP.length]];
    tone(N(note), t, STEP * 3, { type: 'triangle', gain: 0.045, attack: 0.008, dest: musicGain });
  }
  for (const [b, st, m, len] of MELODY) {
    if (b === bar && st === s) {
      // Music box: a pure tone plus a quiet octave partial.
      tone(N(m), t, STEP * len + 0.25, { gain: 0.08, attack: 0.004, dest: musicGain });
      tone(N(m + 12), t, STEP * 2, { gain: 0.02, attack: 0.002, dest: musicGain, detune: 4 });
    }
  }
}

function scheduler() {
  while (nextNoteTime < ctx.currentTime + 0.2) {
    scheduleStep(step, nextNoteTime);
    nextNoteTime += STEP;
    step = (step + 1) % TOTAL_STEPS;
  }
}

export function startMusic() {
  const c = ensure();
  if (!c || musicTimer || !state.music) return;
  nextNoteTime = c.currentTime + 0.1;
  musicGain.gain.cancelScheduledValues(c.currentTime);
  musicGain.gain.setValueAtTime(musicGain.gain.value, c.currentTime);
  musicGain.gain.linearRampToValueAtTime(state.musicVolume, c.currentTime + 1.5);
  musicTimer = setInterval(scheduler, 50);
}

export function stopMusic() {
  if (!musicTimer) return;
  clearInterval(musicTimer);
  musicTimer = null;
  if (ctx) {
    musicGain.gain.cancelScheduledValues(ctx.currentTime);
    musicGain.gain.setValueAtTime(musicGain.gain.value, ctx.currentTime);
    musicGain.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.3);
  }
}

/** Fade the music out while another app is in front, and back in afterwards. */
export function pause() {
  stopMusic();
  if (ctx) setTimeout(() => ctx && !musicTimer && ctx.suspend(), 400);
}

export function resume() {
  if (!ctx) return;
  ctx.resume();
  if (state.music) startMusic();
}
