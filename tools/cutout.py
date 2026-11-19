import sys
import numpy as np
from PIL import Image
from scipy import ndimage

def cutout(src, dst, size, pad=0.04, t=46):
    im = Image.open(src).convert("RGB")
    a = np.asarray(im).astype(np.float32)
    d = (255.0 - a).max(axis=2)
    lab, _ = ndimage.label(d < t)
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(edge))
    alpha = np.ones(d.shape, np.float32)
    alpha[bg] = np.clip(d[bg] / t, 0, 1) ** 1.2
    al = alpha[..., None]
    rgb = np.where(al > 0.01, (a - (1 - al) * 255.0) / np.maximum(al, 0.01), 0)
    rgb = np.clip(rgb, 0, 255)
    out = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    img = Image.fromarray(out, "RGBA")
    ys, xs = np.where(alpha > 0.06)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    img = img.crop(box)
    s = max(img.size)
    s2 = int(s * (1 + 2 * pad))
    canvas = Image.new("RGBA", (s2, s2), (0, 0, 0, 0))
    canvas.paste(img, ((s2 - img.size[0]) // 2, (s2 - img.size[1]) // 2), img)
    canvas = canvas.resize((size, size), Image.LANCZOS)
    canvas.save(dst)

if __name__ == "__main__":
    cutout(sys.argv[1], sys.argv[2], int(sys.argv[3]))
