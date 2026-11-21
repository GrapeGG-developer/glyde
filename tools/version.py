import datetime
import json
import os
import re
import subprocess
import sys

WEEKLY = [
    ("Astra", "star"), ("Orbis", "planet"), ("Pinwheel", "galaxy"), ("Halley", "comet"), ("Selene", "moon"), ("Orion", "nebula"),
    ("Vega", "star"), ("Kepler", "planet"), ("Triangulum", "galaxy"), ("Encke", "comet"), ("Luna", "moon"), ("Carina", "nebula"),
    ("Sirius", "star"), ("Titan", "planet"), ("Sombrero", "galaxy"), ("Hale", "comet"), ("Phoebe", "moon"), ("Lyra", "nebula"),
    ("Polaris", "star"), ("Gliese", "planet"), ("Whirlpool", "galaxy"), ("Tempel", "comet"), ("Europa", "moon"), ("Helix", "nebula"),
]
MONTHLY = ["Andromeda", "Betelgeuse", "Cassiopeia", "Draco", "Eridanus", "Fornax", "Gemini", "Hydra", "Izar", "Juno", "Kepler", "Lyra"]
STAGES = {"insider": "insider", "beta": "beta", "dev": "dev", "rc": "beta"}
WEEK = re.compile(r"^v?(\d{2})\.(\d{1,2})\.(\d)\.(\d+)(?:-(insider|beta|rc|dev)(\d+)?)?$", re.I)
YMWB = re.compile(r"^v?Y(\d+)\.M(\d+)\.W(\d+)\.B(\d+)(?:-([A-Z][a-z]+))?(?:-(Insider|Beta|Dev|RC))?$", re.I)
OLD = re.compile(r"^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-(insider|beta|rc|dev)\.?(\d+)?)?$", re.I)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MSK = datetime.timezone(datetime.timedelta(hours=3))


def weekly(y, m, w):
    return WEEKLY[(((y - 26) * 12 + (m - 11)) * 5 + (w - 2)) % len(WEEKLY)]


def parse(v):
    v = v.strip()
    m = WEEK.match(v)
    if m:
        y, mo, w, b = int(m[1]), int(m[2]), int(m[3]), int(m[4])
        stage = STAGES.get((m[5] or "").lower(), "release")
        name, kind = weekly(y, mo, w)
        return {"scheme": "week", "y": y, "m": mo, "w": w, "b": b, "stage": stage, "n": int(m[6] or 0), "codename": name, "kind": kind,
                "code": 1_000_000_000 + y * 10_000_000 + mo * 100_000 + w * 10_000 + min(b, 9999)}
    m = YMWB.match(v)
    if m:
        y, mo, w, b = int(m[1]), int(m[2]), int(m[3]), int(m[4])
        stage = STAGES.get((m[6] or "").lower(), "release")
        return {"scheme": "ymwb", "y": y, "m": mo, "w": w, "b": b, "stage": stage, "n": 0, "codename": m[5] or "Andromeda", "kind": "galaxy",
                "code": y * 10_000_000 + mo * 100_000 + w * 10_000 + min(b, 9999)}
    m = OLD.match(v)
    if not m:
        raise SystemExit("bad version " + v)
    major, minor, patch = int(m[1]), int(m[2]), int(m[3] or 0)
    stage = STAGES.get((m[4] or "").lower(), "release")
    n = int(m[5] or 0)
    sc = 10 + min(n, 29) if stage == "insider" else 40 + min(n, 39) if stage == "beta" else 1 if stage == "dev" else 99
    return {"scheme": "old", "y": major, "m": minor, "w": 0, "b": patch, "stage": stage, "n": n,
            "codename": MONTHLY[(minor - 11) % 12] if major >= 20 else "", "kind": "galaxy",
            "code": major * 1_000_000 + minor * 10_000 + patch * 100 + sc}


def tags():
    try:
        out = subprocess.check_output(["git", "tag", "--list"], cwd=ROOT).decode().split()
    except Exception:
        out = []
    return out


def next_name(stage=None):
    now = datetime.datetime.now(MSK)
    y, m, w = now.year % 100, now.month, now.day // 7
    taken = 0
    for t in tags():
        try:
            p = parse(t)
        except SystemExit:
            continue
        if p["y"] == y and p["m"] == m and p["w"] == w and p["scheme"] in ("week", "ymwb"):
            taken = max(taken, p["b"] if p["scheme"] == "week" else taken + 1)
    name = "%d.%d.%d.%d" % (y, m, w, taken + 1)
    if stage and stage.lower() in STAGES:
        name += "-" + STAGES[stage.lower()]
    return name


def display(version):
    p = parse(version)
    clean = version.lstrip("v")
    if p["scheme"] == "week":
        base = "Glyde %d.%d.%d.%d %s" % (p["y"], p["m"], p["w"], p["b"], p["codename"])
        if p["stage"] != "release":
            base += " " + p["stage"].capitalize() + (" %d" % p["n"] if p["n"] else "")
        return base
    if p["scheme"] == "ymwb":
        return "Glyde " + clean
    if p["y"] < 20:
        return "GT Unblock " + clean
    s = "Glyde %d.%02d %s" % (p["y"], p["m"], p["codename"])
    if p["stage"] != "release":
        s += " %s %d" % (p["stage"].capitalize(), p["n"] or 1)
    elif p["b"]:
        s += " Patch %d" % p["b"]
    return s


def notes(version, changelog):
    out, on = [], False
    for line in changelog.splitlines():
        if line.startswith("## "):
            if on:
                break
            head = line[3:].strip().split()
            if head and head[0].lstrip("v") == version.lstrip("v"):
                on = True
                continue
        elif on:
            out.append(line)
    return "\n".join(out).strip()


def info(version):
    p = parse(version)
    return {
        "name": version.lstrip("v"),
        "code": p["code"],
        "codename": p["codename"],
        "kind": p["kind"],
        "display": display(version),
        "pre": "true" if p["stage"] != "release" else "false",
        "ring": "insider" if p["stage"] in ("insider", "dev") else "beta" if p["stage"] == "beta" else "stable",
    }


if __name__ == "__main__":
    arg = sys.argv[1]
    mode = sys.argv[2] if len(sys.argv) > 2 else ""
    if arg == "next":
        print(next_name(mode or None))
        raise SystemExit
    if arg == "codenames":
        print(json.dumps(WEEKLY))
        raise SystemExit
    version = arg.lstrip("v")
    changelog = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()
    i = info(version)
    if mode == "notes":
        print(notes(version, changelog) or i["display"])
    elif mode == "json":
        print(json.dumps(i, ensure_ascii=False))
    else:
        for k, v in i.items():
            print("%s=%s" % (k, v))
