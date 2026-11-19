import os
import numpy as np
import soundfile as sf
from scipy.signal import lfilter, butter

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "res", "raw")


def t_of(d):
    return np.arange(int(SR * d)) / SR


def env(t, a, d):
    e = np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) / d)
    return e


def bell(f, d, bright=1.0, decay=0.6):
    t = t_of(d)
    mod = np.sin(2 * np.pi * f * 3.5 * t) * bright * 1.6 * np.exp(-t / (decay * 0.35))
    car = np.sin(2 * np.pi * f * t + mod)
    car += 0.35 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t / (decay * 0.5))
    car += 0.12 * np.sin(2 * np.pi * f * 4.01 * t) * np.exp(-t / (decay * 0.25))
    return car * env(t, 0.004, decay)


def glass(f, d, decay=0.9):
    t = t_of(d)
    parts = [(1.0, 1.0), (2.76, 0.45), (5.40, 0.22), (8.93, 0.10)]
    s = np.zeros_like(t)
    for k, a in parts:
        s += a * np.sin(2 * np.pi * f * k * t + k) * np.exp(-t * k ** 0.6 / decay)
    return s * np.minimum(1, t / 0.002)


def bubble(f0, f1, d, amp=1.0):
    t = t_of(d)
    f = f0 * (f1 / f0) ** (t / d)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return amp * np.sin(ph) * np.sin(np.pi * np.minimum(1, t / d)) ** 1.5


def place(buf, sig, at, gain=1.0):
    i = int(at * SR)
    n = min(len(sig), len(buf) - i)
    buf[i:i + n] += sig[:n] * gain


def reverb(x, mix=0.25, size=1.0):
    out = np.zeros_like(x)
    for dl, g in ((1557, 0.84), (1617, 0.83), (1491, 0.85), (1422, 0.86), (1277, 0.87), (1356, 0.86)):
        d = int(dl * size)
        a = np.zeros(d + 1)
        a[0] = 1
        a[d] = -g
        out += lfilter([1.0], a, x)
    out /= 6
    for dl, g in ((225, 0.5), (556, 0.5), (441, 0.5)):
        b = np.zeros(dl + 1)
        b[0] = -g
        b[dl] = 1
        a = np.zeros(dl + 1)
        a[0] = 1
        a[dl] = -g
        out = lfilter(b, a, out)
    return x * (1 - mix) + out * mix


def stereo(x, width=0.004):
    d = int(width * SR)
    l = x
    r = np.concatenate([np.zeros(d), x[:-d]]) if d > 0 else x
    return np.stack([l, r], axis=1)


def finish(x, name, gain=0.8, lp=None):
    if lp:
        b, a = butter(2, lp / (SR / 2))
        x = lfilter(b, a, x)
    n = int(0.02 * SR)
    x[-n:] *= np.linspace(1, 0, n)
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    st = stereo(x)
    os.makedirs(OUT, exist_ok=True)
    sf.write(os.path.join(OUT, name + ".ogg"), st.astype(np.float32), SR, format="OGG", subtype="VORBIS")


def note(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def pad(f, d, att=0.06, rel=0.9):
    t = t_of(d)
    s = np.zeros_like(t)
    for det, a in ((-0.0035, 0.5), (0.0, 0.7), (0.0042, 0.5)):
        ff = f * (1 + det)
        for k, ak in ((1, 1.0), (2, 0.42), (3, 0.18), (4, 0.08), (5, 0.04)):
            s += a * ak * np.sin(2 * np.pi * ff * k * t + k * 0.7)
    e = np.minimum(1.0, t / att) ** 1.5 * np.exp(-np.maximum(0, t - att) / rel)
    return s * e


def chord(notes, d, strum, att, rel, sparkle, name, gain, lp, mix):
    buf = np.zeros(int(SR * d))
    for i, n in enumerate(notes):
        f = note(n)
        sig = pad(f, d - i * strum, att, rel) * 0.55 + glass(f, d - i * strum, rel * 0.7) * 0.35 + bell(f, d - i * strum, 0.3, rel * 0.6) * 0.18
        place(buf, sig, i * strum, 1.0 - i * 0.07)
    for k, n in enumerate(sparkle):
        place(buf, glass(note(n), 0.8, 0.3) * 0.10, 0.10 + k * 0.05)
    finish(reverb(buf, mix, 1.15), name, gain, lp=lp)


def chime_on():
    chord((60, 67, 72, 76, 79, 83, 86), 2.2, 0.018, 0.035, 0.85, (96, 100, 103), "chime_on", 0.8, 9000, 0.34)


def chime_off():
    chord((69, 64, 60, 57, 52), 1.6, 0.03, 0.03, 0.5, (), "chime_off", 0.6, 4500, 0.3)


def tune_done():
    d = 1.6
    buf = np.zeros(int(SR * d))
    for i, n in enumerate((84, 88, 91, 96, 100)):
        place(buf, glass(note(n), 1.1, 0.5) * 0.7 + bell(note(n), 1.1, 0.8, 0.45) * 0.3, i * 0.055, 0.8 - i * 0.08)
    for k in range(10):
        r = np.random.default_rng(k)
        place(buf, glass(note(96 + int(r.integers(0, 12))), 0.35, 0.12) * 0.12, 0.25 + k * 0.035)
    finish(reverb(buf, 0.35, 1.0), "tune_done", 0.7)


def error():
    d = 0.9
    buf = np.zeros(int(SR * d))
    for i, n in enumerate((62, 61)):
        place(buf, bell(note(n), 0.6, 0.35, 0.22) * 0.8 + glass(note(n) / 2, 0.6, 0.25) * 0.4, i * 0.16, 0.9)
    finish(reverb(buf, 0.2, 0.7), "error", 0.6, lp=3500)


def tap():
    d = 0.18
    buf = np.zeros(int(SR * d))
    place(buf, bubble(380, 1100, 0.06, 1.0), 0.0)
    place(buf, glass(note(93), 0.15, 0.06) * 0.25, 0.015)
    finish(reverb(buf, 0.15, 0.5), "tap", 0.5)


for f in (chime_on, chime_off, tune_done, error, tap):
    f()
for old in ("chime_on.wav", "chime_off.wav"):
    p = os.path.join(OUT, old)
    if os.path.exists(p):
        os.remove(p)
print(sorted(os.listdir(OUT)))
