import json
import os
import re
import sys

CODENAMES = ["Andromeda", "Betelgeuse", "Cassiopeia", "Draco", "Eridanus", "Fornax", "Gemini", "Hydra", "Izar", "Juno", "Kepler", "Lyra", "Mira", "Nova", "Orion", "Pulsar", "Quasar", "Rigel", "Sirius", "Triton", "Ursa", "Vega", "Wezen", "Xena", "Yildun", "Zenith"]


def parse(v):
    m = re.match(r"^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-(insider|beta|rc|dev)\.?(\d+)?)?", v, re.I)
    if not m:
        raise SystemExit("bad version " + v)
    major, minor, patch = int(m[1]), int(m[2]), int(m[3] or 0)
    stage = (m[4] or "release").lower()
    n = int(m[5] or 0)
    if stage == "insider":
        sc = 10 + min(n, 29)
    elif stage in ("beta", "rc"):
        sc = 40 + min(n, 39)
    elif stage == "dev":
        sc = 1
    else:
        sc = 99
    return major, minor, patch, stage, n, major * 1000000 + minor * 10000 + patch * 100 + sc


def codename(version, changelog):
    major, minor = parse(version)[:2]
    pat = re.compile(r"^##\s+v?%d\.%d(?:\.\d+)?(?:-\S+)?\s+([A-Z][a-z]+)\s*$" % (major, minor), re.M)
    m = pat.search(changelog)
    if m and m[1] in CODENAMES:
        return m[1]
    seen = []
    for mm in re.finditer(r"^##\s+v?(\d+)\.(\d+)", changelog, re.M):
        key = (int(mm[1]), int(mm[2]))
        if key[0] >= 20 and key not in seen:
            seen.append(key)
    seen.sort()
    if (major, minor) in seen:
        return CODENAMES[seen.index((major, minor)) % len(CODENAMES)]
    return CODENAMES[len(seen) % len(CODENAMES)]


def display(version, cn):
    major, minor, patch, stage, n, _ = parse(version)
    if major < 20:
        return "GT Unblock " + version
    s = "Glyde %d.%02d %s" % (major, minor, cn)
    if stage == "insider":
        s += " Insider %d" % (n or 1)
    elif stage in ("beta", "rc"):
        s += " Beta %d" % (n or 1)
    elif stage == "dev":
        s += " Dev %d" % (n or 1)
    elif patch:
        s += " Patch %d" % patch
    return s


def notes(version, changelog):
    lines = changelog.splitlines()
    out, on = [], False
    for line in lines:
        if line.startswith("## "):
            if on:
                break
            head = line[3:].strip().split()
            if head and head[0].lstrip("v") == version:
                on = True
                continue
        elif on:
            out.append(line)
    return "\n".join(out).strip()


if __name__ == "__main__":
    version = sys.argv[1].lstrip("v")
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    changelog = open(os.path.join(root, "CHANGELOG.md"), encoding="utf-8").read()
    major, minor, patch, stage, n, code = parse(version)
    cn = codename(version, changelog)
    info = {
        "name": version,
        "code": code,
        "codename": cn,
        "display": display(version, cn),
        "pre": "true" if stage != "release" else "false",
        "ring": "insider" if stage in ("insider", "dev") else "beta" if stage in ("beta", "rc") else "stable",
    }
    if len(sys.argv) > 2 and sys.argv[2] == "notes":
        print(notes(version, changelog) or info["display"])
    elif len(sys.argv) > 2 and sys.argv[2] == "json":
        print(json.dumps(info, ensure_ascii=False))
    else:
        for k, v in info.items():
            print("%s=%s" % (k, v))
