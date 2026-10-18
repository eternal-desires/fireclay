#!/usr/bin/env python3
"""Fireclay sound generator (contract section 5).

Synthesizes every Fireclay sound deterministically with numpy (fixed seeds),
writes a temporary 16-bit WAV, and encodes it with ffmpeg (libvorbis, q4,
mono, 44.1 kHz) into src/main/resources/assets/fireclay/sounds/. Also writes
assets/fireclay/sounds.json and verifies every file with ffprobe.

Run from the repo root:  PYTHONPATH=tools python3 tools/gen_sounds.py
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "fireclay")
SOUNDS = os.path.join(ASSETS, "sounds")


# ---------------------------------------------------------------- DSP helpers
def t_axis(dur):
    return np.arange(int(round(dur * SR))) / SR


def white(rng, n):
    return rng.standard_normal(n)


def pink(rng, n):
    """Pink noise by 1/sqrt(f) spectral shaping."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    spec /= np.sqrt(f)
    x = np.fft.irfft(spec, n)
    return x / (np.max(np.abs(x)) + 1e-12)


def brown(rng, n):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    spec /= f
    x = np.fft.irfft(spec, n)
    x -= x.mean()
    return x / (np.max(np.abs(x)) + 1e-12)


def fft_filter(x, lo=None, hi=None, order=4):
    """Zero-phase spectral band filter with Butterworth-like magnitude."""
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = np.ones_like(f)
    if hi is not None:
        g *= 1.0 / np.sqrt(1.0 + (f / hi) ** (2 * order))
    if lo is not None:
        ff = np.maximum(f, 1e-6)
        g *= 1.0 / np.sqrt(1.0 + (lo / ff) ** (2 * order))
    return np.fft.irfft(spec * g, n)


def lowpass(x, hi, order=4):
    return fft_filter(x, None, hi, order)


def highpass(x, lo, order=4):
    return fft_filter(x, lo, None, order)


def bandpass(x, lo, hi, order=4):
    return fft_filter(x, lo, hi, order)


def exp_env(n, tau, start=0):
    e = np.zeros(n)
    k = np.arange(n - start) / SR
    e[start:] = np.exp(-k / tau)
    return e


def attack(n, a):
    """Linear attack ramp over a seconds."""
    e = np.ones(n)
    m = min(n, max(1, int(a * SR)))
    e[:m] = np.linspace(0, 1, m)
    return e


def fade(x, fin=0.0, fout=0.0):
    x = x.copy()
    n = len(x)
    if fin > 0:
        m = min(n, int(fin * SR))
        x[:m] *= np.linspace(0, 1, m) ** 2
    if fout > 0:
        m = min(n, int(fout * SR))
        x[n - m:] *= np.linspace(1, 0, m) ** 2
    return x


def place(buf, sig, at):
    i = int(at * SR)
    j = min(len(buf), i + len(sig))
    if j > i:
        buf[i:j] += sig[: j - i]
    return buf


def normalize(x, peak_db):
    x = x - np.mean(x)
    p = np.max(np.abs(x)) + 1e-12
    return x * (10 ** (peak_db / 20.0) / p)


def soft_clip(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


# ---------------------------------------------------------------- sounds
def wheel_throw(seed):
    """Wet clay slap: pink noise burst lowpassed ~900 Hz + 90 Hz sine thump."""
    rng = np.random.default_rng(seed)
    dur = 0.30
    t = t_axis(dur)
    n = len(t)
    cut = 800 + 250 * rng.random()
    burst = lowpass(pink(rng, n), cut, 3) * exp_env(n, 0.035 + 0.01 * rng.random())
    # a wet "smack" transient a few ms long, slightly brighter
    smack = bandpass(white(rng, n), 600, 2600, 2) * exp_env(n, 0.006)
    f0 = 80 + 25 * rng.random()
    thump = np.sin(2 * np.pi * (f0 * t - 30 * t * t)) * exp_env(n, 0.05)
    # a tiny squelchy secondary slap
    sec = lowpass(pink(rng, n), 1200, 2) * exp_env(n, 0.02, int((0.05 + 0.03 * rng.random()) * SR))
    x = 1.0 * burst + 0.55 * smack + 0.9 * thump + 0.35 * sec
    x *= attack(n, 0.002)
    return normalize(fade(x, 0, 0.04), -3.0)


def bellows_pump(seed):
    """Airy whoosh: pink noise band 300-1200 Hz with a breath-shaped envelope."""
    rng = np.random.default_rng(seed)
    dur = 0.62 + 0.06 * rng.random()
    t = t_axis(dur)
    n = len(t)
    noise = pink(rng, n)
    # sweep the band upward slightly by mixing two bands with a crossfade
    lo_band = bandpass(noise, 250, 900, 2)
    hi_band = bandpass(noise, 600, 1700, 2)
    mix = np.clip(t / dur, 0, 1)
    air = lo_band * (1 - mix) + hi_band * mix * 0.8
    # breath envelope: fast rise 0.08 s, sustain, decay from 0.2 s
    env = np.where(t < 0.08, t / 0.08, np.exp(-np.maximum(t - 0.2, 0) / 0.14))
    env = np.minimum(env, 1.0)
    # leather creak: low filtered noise blip at the start
    creak = bandpass(white(rng, n), 120, 400, 2) * exp_env(n, 0.03)
    x = air * env + 0.25 * creak
    return normalize(fade(x, 0.005, 0.05), -4.0)


def kiln_roar(seed):
    """3 s low rumbling fire: brown noise lowpass 300 Hz, 3 Hz tremolo, fire crackle."""
    rng = np.random.default_rng(seed)
    dur = 3.0
    t = t_axis(dur)
    n = len(t)
    body = lowpass(brown(rng, n), 300, 3)
    rumble = lowpass(brown(rng, n), 90, 2)
    phase = rng.random() * 2 * np.pi
    trem = 1.0 - 0.4 * (0.5 + 0.5 * np.sin(2 * np.pi * 3.0 * t + phase))
    # slow random flutter of the flame
    flutter = lowpass(rng.standard_normal(n), 6, 2)
    flutter = 1 + 0.25 * flutter / (np.max(np.abs(flutter)) + 1e-12)
    x = (body / np.max(np.abs(body)) + 0.6 * rumble / np.max(np.abs(rumble))) * trem * flutter
    # sparse soft crackles
    crackle = np.zeros(n)
    for _ in range(14):
        at = rng.random() * (dur - 0.05)
        k = int(0.012 * SR)
        click = highpass(rng.standard_normal(k * 4), 1500, 2)[:k] * np.exp(-np.arange(k) / (0.002 * SR))
        place(crackle, click * (0.3 + 0.7 * rng.random()), at)
    x = x / np.max(np.abs(x)) + 0.12 * crackle
    x = fade(x, 0.3, 0.3)
    x = x - np.mean(x)
    # match loudness (RMS) across variants so the ambient loop does not jump
    # between roar1/roar2; cap the peak at -6 dBFS
    x *= 10 ** (-18.0 / 20.0) / (np.sqrt(np.mean(x ** 2)) + 1e-12)
    pk = np.max(np.abs(x))
    if pk > 10 ** (-6.0 / 20.0):
        x *= 10 ** (-6.0 / 20.0) / pk
    return x


def kiln_overheat(seed):
    """0.8 s crackle (random impulses) + hiss band 3-6 kHz."""
    rng = np.random.default_rng(seed)
    dur = 0.85
    t = t_axis(dur)
    n = len(t)
    hiss = bandpass(white(rng, n), 3000, 6000, 3)
    hiss_env = np.minimum(t / 0.05, 1.0) * np.exp(-np.maximum(t - 0.25, 0) / 0.25)
    crack = np.zeros(n)
    count = 26 + int(rng.integers(0, 10))
    for _ in range(count):
        at = (rng.random() ** 1.4) * (dur - 0.05)
        k = int((0.004 + 0.01 * rng.random()) * SR)
        seg = white(rng, k) * np.exp(-np.arange(k) / (0.0015 * SR + rng.random() * 0.002 * SR))
        seg = np.concatenate([seg, np.zeros(64)])
        place(crack, seg * (0.3 + 0.7 * rng.random()) * (1 if rng.random() < 0.5 else -1), at)
    crack = highpass(crack, 700, 2)
    sizzle = lowpass(brown(rng, n), 500, 2) * 0.2 * hiss_env
    x = 0.55 * hiss * hiss_env / np.max(np.abs(hiss)) + crack / (np.max(np.abs(crack)) + 1e-12) + sizzle
    return normalize(fade(x, 0.002, 0.12), -3.5)


def kiln_crack(seed):
    """Ceramic crack/shatter: sharp transient + filtered noise burst + tonal fragments."""
    rng = np.random.default_rng(seed)
    dur = 0.9
    t = t_axis(dur)
    n = len(t)
    # sharp crack transient
    k = int(0.006 * SR)
    trans = np.zeros(n)
    trans[:k] = white(rng, k) * np.exp(-np.arange(k) / (0.0012 * SR))
    trans = highpass(trans, 1200, 2)
    # main noise burst, bandpassed and decaying
    burst = bandpass(white(rng, n), 1200 + 600 * rng.random(), 7500, 2) * exp_env(n, 0.06)
    body = bandpass(white(rng, n), 300, 1400, 2) * exp_env(n, 0.03)
    # tonal ceramic fragments: inharmonic short pings scattered over time
    frags = np.zeros(n)
    nfr = 5 + int(rng.integers(0, 4))
    for i in range(nfr):
        f = 1800 + 3500 * rng.random()
        at = 0.003 + (rng.random() ** 1.6) * 0.45
        d = 0.07 + 0.12 * rng.random()
        tt = t_axis(d)
        tone = (np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(2 * np.pi * f * 2.76 * tt)) * np.exp(-tt / (d / 4))
        # each fragment has a tiny clicky impact
        tone[: int(0.002 * SR)] += white(rng, int(0.002 * SR)) * 0.6
        place(frags, tone * (0.25 + 0.55 * rng.random()) * (1.0 - 0.6 * at), at)
    # a few late trickling bits (shards settling)
    trickle = np.zeros(n)
    for _ in range(6):
        at = 0.15 + 0.6 * rng.random()
        kk = int(0.01 * SR)
        seg = highpass(white(rng, kk * 4), 2500, 2)[:kk] * np.exp(-np.arange(kk) / (0.002 * SR))
        place(trickle, seg * 0.25 * rng.random(), at)
    x = 1.2 * trans + 0.8 * burst + 0.5 * body + 0.6 * frags + trickle
    x = soft_clip(x / np.max(np.abs(x)), 1.3)
    return normalize(fade(x, 0, 0.1), -3.0)


def kiln_ping(seed):
    """Glaze tinkle: 4-6 high sine pings 2.6-4.2 kHz, 0.35 s exp decay, random delays 0-1.1 s."""
    rng = np.random.default_rng(seed)
    dur = 1.65
    n = int(dur * SR)
    x = np.zeros(n)
    count = 4 + int(rng.integers(0, 3))
    delays = np.sort(rng.random(count) * 1.1)
    delays[0] = 0.0
    for d in delays:
        f = 2600 + 1600 * rng.random()
        tt = t_axis(0.45)
        tau = 0.35 / 4.0
        tone = np.sin(2 * np.pi * f * tt) + 0.18 * np.sin(2 * np.pi * f * 2.41 * tt) * np.exp(-tt / (tau / 2))
        env = np.exp(-tt / tau) * attack(len(tt), 0.0015)
        place(x, tone * env * (0.5 + 0.5 * rng.random()), d)
    return normalize(fade(x, 0, 0.05), -4.0)


def kiln_masterwork(seed):
    """Rising 3-note chime 660/830/990 Hz with 2nd harmonic, 0.25 s apart, 1.2 s tail."""
    rng = np.random.default_rng(seed)
    dur = 0.5 + 1.25
    n = int(dur * SR)
    x = np.zeros(n)
    for i, f in enumerate((660.0, 830.0, 990.0)):
        tt = t_axis(dur - 0.25 * i)
        detune = 1 + 0.002 * (rng.random() - 0.5)
        ff = f * detune
        tone = (np.sin(2 * np.pi * ff * tt)
                + 0.45 * np.sin(2 * np.pi * 2 * ff * tt) * np.exp(-tt / 0.35)
                + 0.12 * np.sin(2 * np.pi * 3 * ff * tt) * np.exp(-tt / 0.15)
                + 0.08 * np.sin(2 * np.pi * 5.4 * ff * tt) * np.exp(-tt / 0.05))
        # subtle shimmer
        tone *= 1 + 0.06 * np.sin(2 * np.pi * 5.5 * tt)
        env = np.exp(-tt / 0.45) * attack(len(tt), 0.004)
        place(x, tone * env * (0.8 + 0.1 * i), 0.25 * i)
    return normalize(fade(x, 0, 0.25), -3.0)


def sentinel_awaken(seed):
    """3 s deep rumble sweep 40->90 Hz + grinding noise + final bright chime 880/1320 Hz."""
    rng = np.random.default_rng(seed)
    dur = 3.0
    t = t_axis(dur)
    n = len(t)
    # exponential sweep 40->90 Hz over 2.6 s, held after
    ts = np.minimum(t, 2.6)
    k = np.log(90 / 40) / 2.6
    phase = 2 * np.pi * 40 * (np.exp(k * ts) - 1) / k + 2 * np.pi * 90 * np.maximum(t - 2.6, 0)
    sweep = np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    sweep_env = np.minimum(t / 0.6, 1.0) * np.where(t > 2.5, np.exp(-(t - 2.5) / 0.25), 1.0)
    # grind: brown noise lowpass 200, amplitude-modulated irregularly (stone scraping)
    grind = lowpass(brown(rng, n), 200, 3)
    grind /= np.max(np.abs(grind))
    am = lowpass(rng.standard_normal(n), 9, 2)
    am = 0.55 + 0.45 * np.clip(am / (np.max(np.abs(am)) + 1e-12) * 1.6, -1, 1)
    gravel = bandpass(white(rng, n), 300, 1800, 2) * 0.12
    gravel *= (lowpass(rng.standard_normal(n), 14, 2) > 0.002).astype(float)
    grind_env = np.minimum(t / 0.3, 1.0) * np.where(t > 2.4, np.exp(-(t - 2.4) / 0.2), 1.0)
    rumble = 0.8 * sweep * sweep_env + (0.75 * grind * am + gravel) * grind_env
    rumble = soft_clip(rumble / np.max(np.abs(rumble)), 1.2)
    # final chime at 2.45 s: 880 + 1320 Hz with sparkle
    chime = np.zeros(n)
    tt = t_axis(dur - 2.45)
    cenv = np.exp(-tt / 0.22) * attack(len(tt), 0.003)
    c = (np.sin(2 * np.pi * 880 * tt) + 0.8 * np.sin(2 * np.pi * 1320 * tt)
         + 0.25 * np.sin(2 * np.pi * 1760 * tt) * np.exp(-tt / 0.1)
         + 0.1 * np.sin(2 * np.pi * 2640 * tt) * np.exp(-tt / 0.06))
    place(chime, c * cenv, 2.45)
    x = rumble / np.max(np.abs(rumble)) + 0.55 * chime
    return normalize(fade(x, 0.05, 0.08), -3.0)


# ---------------------------------------------------------------- table
# (relative path under sounds/, generator, seed)
JOBS = [
    ("block/potters_wheel/throw1", wheel_throw, 101),
    ("block/potters_wheel/throw2", wheel_throw, 102),
    ("block/potters_wheel/throw3", wheel_throw, 103),
    ("block/bellows/pump1", bellows_pump, 201),
    ("block/bellows/pump2", bellows_pump, 202),
    ("block/kiln/roar1", kiln_roar, 301),
    ("block/kiln/roar2", kiln_roar, 302),
    ("block/kiln/overheat1", kiln_overheat, 401),
    ("block/kiln/overheat2", kiln_overheat, 402),
    ("block/kiln/crack1", kiln_crack, 501),
    ("block/kiln/crack2", kiln_crack, 502),
    ("block/kiln/crack3", kiln_crack, 503),
    ("block/kiln/ping1", kiln_ping, 601),
    ("block/kiln/ping2", kiln_ping, 602),
    ("block/kiln/ping3", kiln_ping, 603),
    ("block/kiln/masterwork", kiln_masterwork, 701),
    ("entity/terracotta_sentinel/awaken", sentinel_awaken, 801),
]

# contract section 5: event id -> list of sound paths
EVENTS = [
    ("block.potters_wheel.throw", ["block/potters_wheel/throw1", "block/potters_wheel/throw2", "block/potters_wheel/throw3"]),
    ("block.bellows.pump", ["block/bellows/pump1", "block/bellows/pump2"]),
    ("block.kiln.roar", ["block/kiln/roar1", "block/kiln/roar2"]),
    ("block.kiln.overheat", ["block/kiln/overheat1", "block/kiln/overheat2"]),
    ("block.kiln.crack", ["block/kiln/crack1", "block/kiln/crack2", "block/kiln/crack3"]),
    ("block.kiln.ping", ["block/kiln/ping1", "block/kiln/ping2", "block/kiln/ping3"]),
    ("block.kiln.masterwork", ["block/kiln/masterwork"]),
    ("entity.terracotta_sentinel.awaken", ["entity/terracotta_sentinel/awaken"]),
]


def write_wav(path, x):
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode(x, out_path, tmpdir):
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    wav = os.path.join(tmpdir, os.path.basename(out_path) + ".wav")
    write_wav(wav, x)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav,
         "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
         "-ac", "1", "-ar", str(SR), "-c:a", "libvorbis", "-q:a", "4", out_path],
        check=True)


def probe(path):
    r = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries",
         "stream=codec_name,channels,sample_rate:format=duration", "-of", "json", path],
        check=True, capture_output=True, text=True)
    d = json.loads(r.stdout)
    s = d["streams"][0]
    return s["codec_name"], int(s["channels"]), int(s["sample_rate"]), float(d["format"]["duration"])


def main():
    with tempfile.TemporaryDirectory() as tmp:
        peaks = {}
        for rel, fn, seed in JOBS:
            x = fn(seed)
            peaks[rel] = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
            rms = 20 * np.log10(np.sqrt(np.mean(x * x)) + 1e-12)
            peaks[rel] = (peaks[rel], rms)
            encode(x, os.path.join(SOUNDS, rel + ".ogg"), tmp)

    sounds_json = {}
    for ev, files in EVENTS:
        sounds_json[ev] = {
            "sounds": ["fireclay:" + f for f in files],
            "subtitle": "subtitles.fireclay." + ev,
        }
    with open(os.path.join(ASSETS, "sounds.json"), "w") as fh:
        json.dump(sounds_json, fh, indent=2)
        fh.write("\n")

    ok = True
    print(f"{'file':42} {'codec':7} {'ch':>2} {'rate':>6} {'dur s':>6} {'peak dB':>8} {'rms dB':>7}")
    for rel, _, _ in JOBS:
        p = os.path.join(SOUNDS, rel + ".ogg")
        codec, ch, rate, dur = probe(p)
        pk, rms = peaks[rel]
        good = codec == "vorbis" and ch == 1 and rate == SR
        ok &= good
        print(f"{rel + '.ogg':42} {codec:7} {ch:>2} {rate:>6} {dur:6.3f} {pk:8.2f} {rms:7.2f}{'' if good else '  !!'}")
    print("sounds.json events:", len(sounds_json))
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
