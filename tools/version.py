import datetime
import json
import os
import re
import subprocess
import sys

CODENAMES = ["Andromeda", "Betelgeuse", "Cassiopeia", "Draco", "Eridanus", "Fornax", "Gemini", "Hydra", "Izar", "Juno", "Kepler", "Lyra", "Mira", "Nova", "Orion", "Pulsar", "Quasar", "Rigel", "Sirius", "Triton", "Ursa", "Vega", "Wezen", "Xena", "Yildun", "Zenith"]
STAGES = {"insider": "Insider", "beta": "Beta", "dev": "Dev"}
NEW = re.compile(r"^v?Y(\d+)\.M(\d+)\.W(\d+)\.B(\d+)(?:-([A-Z][a-z]+))?(?:-(Insider|Beta|Dev|RC))?$", re.I)
OLD = re.compile(r"^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-(insider|beta|rc|dev)\.?(\d+)?)?", re.I)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def codename_for(y, m):
    return CODENAMES[((y - 26) * 12 + (m - 11)) % len(CODENAMES)]


def parse(v):
    m = NEW.match(v)
    if m:
        y, mo, w, b = int(m[1]), int(m[2]), int(m[3]), int(m[4])
        stage = (m[6] or "release").lower()
        if stage == "rc":
            stage = "beta"
        return {"new": True, "y": y, "m": mo, "w": w, "b": b, "stage": stage, "codename": m[5] or codename_for(y, mo),
                "code": y * 10000000 + mo * 100000 + w * 10000 + min(b, 9999)}
    m = OLD.match(v)
    if not m:
        raise SystemExit("bad version " + v)
    major, minor, patch = int(m[1]), int(m[2]), int(m[3] or 0)
    stage = (m[4] or "release").lower()
    n = int(m[5] or 0)
    sc = 10 + min(n, 29) if stage == "insider" else 40 + min(n, 39) if stage in ("beta", "rc") else 1 if stage == "dev" else 99
    return {"new": False, "y": major, "m": minor, "w": 0, "b": patch, "stage": "beta" if stage == "rc" else stage,
            "codename": codename_for(major, minor) if major >= 20 else "", "n": n,
            "code": major * 1000000 + minor * 10000 + patch * 100 + sc}


def build_number():
    try:
        return int(subprocess.check_output(["git", "rev-list", "--count", "HEAD"], cwd=ROOT).decode().strip())
    except Exception:
        return int(os.environ.get("GITHUB_RUN_NUMBER", "1"))


def next_name(stage=None, b=None):
    now = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=3)))
    y, m = now.year % 100, now.month
    w = now.day // 7
    name = "Y%d.M%d.W%d.B%d-%s" % (y, m, w, b if b is not None else build_number(), codename_for(y, m))
    if stage and stage.lower() in STAGES:
        name += "-" + STAGES[stage.lower()]
    return name


def display(version):
    p = parse(version)
    if p["new"]:
        return "Glyde " + version.lstrip("v")
    if p["y"] < 20:
        return "GT Unblock " + version
    s = "Glyde %d.%02d %s" % (p["y"], p["m"], p["codename"])
    if p["stage"] == "insider":
        s += " Insider %d" % (p["n"] or 1)
    elif p["stage"] == "beta":
        s += " Beta %d" % (p["n"] or 1)
    elif p["stage"] == "dev":
        s += " Dev %d" % (p["n"] or 1)
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
    version = arg.lstrip("v") if not NEW.match(arg) else arg.lstrip("v")
    changelog = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()
    i = info(version)
    if mode == "notes":
        print(notes(version, changelog) or i["display"])
    elif mode == "json":
        print(json.dumps(i, ensure_ascii=False))
    else:
        for k, v in i.items():
            print("%s=%s" % (k, v))
