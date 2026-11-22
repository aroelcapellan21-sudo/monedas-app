"""Graba los 4 sonidos de la página (Cristal, Pulso, Alarma, Campana) como WAV para la app: mismos tonos que SON en app.js."""
import math, struct, wave, os
RATE = 22050
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "res", "raw")
os.makedirs(D, exist_ok=True)


def onda(tipo, f, t):
    x = (f * t) % 1.0
    if tipo == "sine":
        return math.sin(2 * math.pi * f * t)
    if tipo == "triangle":
        return 4 * abs(x - 0.5) - 1
    return 2 * x - 1   # sawtooth


def grabar(nombre, tonos, largo):
    n = int(RATE * largo)
    buf = [0.0] * n
    for fr, t0, dur, tipo, v in tonos:
        i0 = int(t0 * RATE)
        for k in range(int((dur + 0.05) * RATE)):
            i = i0 + k
            if i >= n:
                break
            t = k / RATE
            env = min(1.0, t / 0.02) * max(0.0, math.exp(-5.0 * t / max(dur, 0.01)))   # sube rápido y se apaga como la página
            buf[i] += v * env * onda(tipo, fr, t)
    pico = max(1e-9, max(abs(x) for x in buf))
    with wave.open(os.path.join(D, nombre + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x / pico * 0.9)) * 32767)) for x in buf))


grabar("cristal", [(1318, 0, .5, "sine", .5), (1760, .12, .6, "sine", .4), (2093, .26, .9, "sine", .3)], 1.3)
grabar("pulso", [(440, 0, .18, "triangle", .55), (440, .25, .18, "triangle", .55)], 0.6)
grabar("alarma", [x for i in range(4) for x in ((880, i * .3, .14, "sawtooth", .3), (660, i * .3 + .15, .14, "sawtooth", .3))], 1.4)
grabar("campana", [(fr, 0, 1.3 - i * .25, "sine", .5 / (i + 1)) for i, fr in enumerate((523, 1046, 1568))], 1.5)
print("ok", sorted(os.listdir(D)))
