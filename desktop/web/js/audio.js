// Interface sounds (the Android launcher's wii_*.wav) and the channel menu's music, synthesised here by the
// same recipe as MenuMusic.java from the tune it defines (web/gen/menu_song.js).
import { load } from './compat.js';
import SONG from '../gen/menu_song.js';

export const MOVE = 'move', SELECT = 'select', PAGE = 'page', BACK = 'back', MENU = 'menu', START = 'start';
const RATE = 22050;
const SOUND_VOLUME = 0.6, MUSIC_VOLUME = 0.28;

export class Audio {
  constructor() {
    this.ctx = null;
    this.buffers = {};
    this.soundsOn = true;
    this.musicOn = true;
    this.musicSource = null;
    this.musicBuffer = null;
    this.musicGain = null;
    this.wanted = false;
  }

  /** Browsers only start audio after a key or click; call on the first one. */
  unlock() {
    if (this.ctx) {
      if (this.ctx.state === 'suspended') this.ctx.resume();
      return;
    }
    this.ctx = new AudioContext();
    for (const n of [MOVE, SELECT, PAGE, BACK, MENU, START]) {
      load(`gen/wii_${n}.wav`).then((b) => this.ctx.decodeAudioData(b))
        .then((buf) => { this.buffers[n] = buf; }).catch(() => {});
    }
    this.musicGain = this.ctx.createGain();
    this.musicGain.gain.value = MUSIC_VOLUME;
    this.musicGain.connect(this.ctx.destination);
    this.update();
  }

  play(name) {
    if (!this.soundsOn || !this.ctx || !this.buffers[name]) return;
    const src = this.ctx.createBufferSource();
    const g = this.ctx.createGain();
    g.gain.value = SOUND_VOLUME;
    src.buffer = this.buffers[name];
    src.connect(g).connect(this.ctx.destination);
    src.start();
  }

  /** Music plays while the menu is in front and music is on. */
  setWanted(on) {
    this.wanted = on;
    this.update();
  }

  setMusic(on) {
    this.musicOn = on;
    this.update();
  }

  update() {
    if (!this.ctx) return;
    const play = this.musicOn && this.wanted;
    if (play && !this.musicSource) {
      if (!this.musicBuffer) {
        const pcm = render(SONG);
        this.musicBuffer = this.ctx.createBuffer(1, pcm.length, RATE);
        this.musicBuffer.copyToChannel(pcm, 0);
      }
      const src = this.ctx.createBufferSource();
      src.buffer = this.musicBuffer;
      src.loop = true;
      src.connect(this.musicGain);
      src.start();
      this.musicSource = src;
    } else if (!play && this.musicSource) {
      this.musicSource.stop();
      this.musicSource = null;
    }
  }
}

// ---- MenuMusic.java, ported ---------------------------------------------------------------------

const hz = (midi) => 440 * Math.pow(2, (midi - 69) / 12);

class Osc {
  constructor(f) {
    const w = (2 * Math.PI * f) / RATE;
    this.c = 2 * Math.cos(w);
    this.y1 = 0;
    this.y2 = -Math.sin(w);
  }
  next() {
    const y = this.c * this.y1 - this.y2;
    this.y2 = this.y1;
    this.y1 = y;
    return this.y2;
  }
}

function mix(buf, i, v) {
  buf[i % buf.length] += v;
}

function voice(buf, seconds, at, attackS, gain, partials, decays) {
  const oscs = partials.map(([f]) => new Osc(f));
  const n = (seconds * RATE) | 0, start = (at * RATE) | 0, attack = attackS * RATE;
  const env = decays.map(() => 1), k = decays.map((d) => Math.exp(-1 / (d * RATE)));
  for (let i = 0; i < n; i++) {
    let v = 0;
    for (let p = 0; p < oscs.length; p++) {
      const [, amp, envIdx] = partials[p];
      v += amp * (envIdx === undefined ? 1 : env[envIdx]) * oscs[p].next();
    }
    mix(buf, start + i, v * env[0] * Math.min(1, i / attack) * gain);
    for (let e = 0; e < env.length; e++) env[e] *= k[e];
  }
}

function seeded(seed) {
  let x = seed >>> 0;
  const uni = () => {
    x = (Math.imul(x, 1664525) + 1013904223) >>> 0;
    return (x + 0.5) / 4294967296;
  };
  return () => Math.sqrt(-2 * Math.log(uni())) * Math.cos(2 * Math.PI * uni());
}

export function render(song) {
  const beat = 60 / song.bpm, bar = 4 * beat;
  const buf = new Float32Array((RATE * bar * song.chords.length) | 0);
  const gauss = seeded(11);
  for (let b = 0; b < song.chords.length; b++) {
    const t0 = b * bar;
    for (const hit of song.comp) {
      for (let k = 0; k < 4; k++) {
        const f = hz(song.chords[b][k]);
        // epiano: fundamental, a little 2nd harmonic, and a quick bell on the 3rd
        voice(buf, hit[1] * beat + 0.6, t0 + hit[0] * beat + k * 0.008, 0.006, k === 0 ? 0.07 : 0.06,
          [[f, 1], [f * 2, 0.12], [f * 3, 0.35, 1]], [0.9, 0.15]);
      }
    }
    song.bass.forEach((n, i) => {
      const f = hz(i % 2 === 0 ? song.roots[b] : song.roots[b] + 7);
      voice(buf, n[1] * beat + 0.2, t0 + n[0] * beat, 0.01, 0.22, [[f, 1], [f * 2, 0.2]], [0.5]);
    });
    for (let e = 0; e < 8; e++) {
      const gain = 0.035 * (e % 2 === 1 ? 1 : 0.55);
      const n = (0.12 * RATE) | 0, start = ((t0 + (e * beat) / 2) * RATE) | 0, attack = 0.003 * RATE;
      const k = Math.exp(-1 / (0.025 * RATE));
      let env = 1, prev = 0;
      for (let i = 0; i < n; i++) {
        const x = gauss();
        mix(buf, start + i, (x - prev) * env * Math.min(1, i / attack) * gain);
        prev = x;
        env *= k;
      }
    }
  }
  for (const m of song.melody) {
    const at = m[0] * bar + m[1] * beat;
    const f = hz(m[2]), f2 = hz(m[2] + 12);
    voice(buf, m[3] * beat + 0.5, at, 0.002, 0.16, [[f, 1], [f * 4, 0.25, 1]], [0.45, 0.08]);
    voice(buf, m[3] * beat + 0.3, at + 0.004, 0.002, 0.03, [[f2, 1], [f2 * 4, 0.25, 1]], [0.45, 0.08]);
  }
  let peak = 1e-6;
  for (let i = 0; i < buf.length; i++) {
    buf[i] = Math.tanh(buf[i] * 1.4);
    peak = Math.max(peak, Math.abs(buf[i]));
  }
  const k = 0.7 / peak;
  for (let i = 0; i < buf.length; i++) buf[i] *= k;
  return buf;
}
