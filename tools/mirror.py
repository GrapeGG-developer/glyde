import hashlib
import json
import os
import subprocess
import sys

here = os.path.dirname(os.path.abspath(__file__))
version = sys.argv[1]
dist = sys.argv[2]
info = json.loads(subprocess.check_output([sys.executable, os.path.join(here, "version.py"), version, "json"]).decode())
notes = subprocess.check_output([sys.executable, os.path.join(here, "version.py"), version, "notes"]).decode().strip()
repo = os.environ.get("GITHUB_REPOSITORY", "GrapeGG-developer/glyde")
tag = "v" + version
assets, sizes, sums = {}, {}, {}
for name in sorted(os.listdir(dist)):
    if not name.endswith(".apk"):
        continue
    path = os.path.join(dist, name)
    assets[name] = "https://github.com/%s/releases/download/%s/%s" % (repo, tag, name)
    sizes[name] = os.path.getsize(path)
    sums[name] = hashlib.sha256(open(path, "rb").read()).hexdigest()
print(json.dumps({
    "version": version,
    "code": info["code"],
    "codename": info["codename"],
    "display": info["display"],
    "ring": info["ring"],
    "changelog": notes,
    "assets": assets,
    "sizes": sizes,
    "sums": sums,
}, ensure_ascii=False, indent=1))
