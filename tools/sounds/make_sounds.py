#!/usr/bin/env python3
"""Synthesises the channel menu's interface sounds into launcher/app/src/main/res/raw.

Every sound is built here from sine tones and filtered noise, so the repository carries no recorded audio.
Run from the repository root:  python3 tools/sounds/make_sounds.py
Needs numpy. Tweak the parameters below and re-run to change how the menu sounds.
"""
import os
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'launcher', 'app', 'src', 'main', 'res', 'raw')


def t_axis(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def bell(freq, seconds, decay, partials=((1.0, 1.0), (2.0, 0.28), (3.0, 0.08))):
    t = t_axis(seconds)
    tone = sum(a * np.sin(2 * np.pi * freq * m * t) for m, a in partials)
    env = np.minimum(1.0, t / 0.003) * np.exp(-t / decay)
    return tone * env


def place(buf, sig, at):
    i = int(at * RATE)
    buf[i:i + len(sig)] += sig[:len(buf) - i]
    return buf


def move():
    """Hover tick: a short bright pip that drops slightly in pitch."""
    t = t_axis(0.07)
    f = 2900 - 500 * np.minimum(1.0, t / 0.03)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    env = np.minimum(1.0, t / 0.0015) * np.exp(-t / 0.011)
    return (np.sin(phase) + 0.22 * np.sin(2 * phase)) * env * 0.55


def select():
    """Choose: two bell notes a fifth apart."""
    buf = np.zeros(int(RATE * 0.55))
    place(buf, bell(1318.5, 0.5, 0.12), 0.0)
    place(buf, bell(1975.5, 0.45, 0.14), 0.055)
    return buf * 0.42


def page():
    """Page turn: band-passed noise sweeping upward."""
    n = int(RATE * 0.32)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    f = 500 + 2600 * (t / t[-1]) ** 1.3
    out = np.zeros(n)
    y1 = y2 = 0.0
    q = 6.0
    for i in range(n):
        w0 = 2 * np.pi * f[i] / RATE
        alpha = np.sin(w0) / (2 * q)
        b0 = alpha
        a0 = 1 + alpha
        a1 = -2 * np.cos(w0)
        a2 = 1 - alpha
        x = noise[i]
        y = (b0 * x - b0 * (noise[i - 2] if i >= 2 else 0.0) - a1 * y1 - a2 * y2) / a0
        y2, y1 = y1, y
        out[i] = y
    env = np.sin(np.pi * np.minimum(1.0, t / t[-1])) ** 1.5
    out = out * env
    return out / np.max(np.abs(out)) * 0.30


def back():
    """Back / close: two soft notes stepping down."""
    buf = np.zeros(int(RATE * 0.35))
    place(buf, bell(987.8, 0.3, 0.07), 0.0)
    place(buf, bell(659.3, 0.3, 0.09), 0.05)
    return buf * 0.40


def menu():
    """A panel opening: a quick rising pop."""
    t = t_axis(0.16)
    f = 880 + 260 * np.minimum(1.0, t / 0.025)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    env = np.minimum(1.0, t / 0.002) * np.exp(-t / 0.04)
    return (np.sin(phase) + 0.18 * np.sin(2 * phase)) * env * 0.5


def start():
    """Leaving the welcome screen: a bright four-note arpeggio."""
    buf = np.zeros(int(RATE * 0.95))
    for i, f in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        place(buf, bell(f, 0.7, 0.18), 0.065 * i)
    return buf * 0.30


def noise_hp(n, seed, a=0.85):
    """White noise through a one-pole high-pass: the slap of a hit without any boom."""
    x = np.random.default_rng(seed).standard_normal(n)
    y = np.empty(n)
    prev_x = prev_y = 0.0
    for i in range(n):
        prev_y = a * (prev_y + x[i] - prev_x)
        prev_x = x[i]
        y[i] = prev_y
    return y


def punch():
    """Winter hockey fight: a cartoon slap, a sharp smack over a short low thud."""
    t = t_axis(0.2)
    smack = noise_hp(len(t), 3) * np.minimum(1.0, t / 0.0008) * np.exp(-t / 0.018)
    f = 150 - 70 * np.minimum(1.0, t / 0.08)
    thud = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.minimum(1.0, t / 0.002) * np.exp(-t / 0.045)
    return smack * 0.55 + thud * 0.6


def slapshot():
    """Winter hockey: the stick's crack on the ice and the puck snapping off it."""
    buf = np.zeros(int(RATE * 0.22))
    t = t_axis(0.12)
    crack = noise_hp(len(t), 5, 0.95) * np.minimum(1.0, t / 0.0004) * np.exp(-t / 0.01)
    knock = np.sin(2 * np.pi * 420 * t) * np.exp(-t / 0.025)
    place(buf, crack * 0.8 + knock * 0.35, 0.0)
    place(buf, bell(1900, 0.08, 0.012) * 0.25, 0.012)
    return buf


def goal():
    """Winter hockey: a short goal horn, two buzzy blasts."""
    buf = np.zeros(int(RATE * 0.75))
    for at, length in ((0.0, 0.22), (0.3, 0.42)):
        t = t_axis(length)
        f = 233.0
        tone = sum((1.0 / k) * np.sin(2 * np.pi * f * k * t) for k in range(1, 9))
        env = np.minimum(1.0, t / 0.02) * np.minimum(1.0, (length - t) / 0.04)
        place(buf, tone * env * 0.35, at)
    return buf


def write(name, sig, rate=RATE):
    if rate != RATE:
        # Background effects are kept at half rate, halving their size in the APK.
        sig = (sig[0:len(sig) // 2 * 2:2] + sig[1:len(sig) // 2 * 2:2]) / 2
    sig = np.clip(sig, -1.0, 1.0)
    tail = int(RATE * 0.004)
    sig[-tail:] *= np.linspace(1.0, 0.0, tail)
    pcm = (sig * 32767).astype('<i2')
    path = os.path.join(OUT, name + '.wav')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())
    print('wrote', os.path.relpath(path), len(pcm), 'samples')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for fn in (move, select, page, back, menu, start):
        write('wii_' + fn.__name__, fn())
    for fn in (punch, slapshot, goal):
        write('fx_' + fn.__name__, fn(), RATE // 2)
