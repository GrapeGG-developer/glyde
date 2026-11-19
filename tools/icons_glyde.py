import numpy as np
from PIL import Image, ImageFilter, ImageDraw
from scipy import ndimage

SRC = 'branding/glyde/logo.png'
RES = 'app/src/main/res'
DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
BIRD = 0.56

src = Image.open(SRC).convert('RGB')
im = np.asarray(src).astype(np.float32)


def rounded_logo():
    mn = im.min(2)
    white = mn > 247
    lab, n = ndimage.label(white)
    outer = np.zeros_like(white)
    h, w = white.shape
    for p in [(0, 0), (0, w - 1), (h - 1, 0), (h - 1, w - 1)]:
        if lab[p]:
            outer |= lab == lab[p]
    outer = ndimage.binary_dilation(outer, iterations=2)
    a = ndimage.gaussian_filter((~outer).astype(np.float32), 0.8)
    return Image.fromarray(np.dstack([im, a * 255]).clip(0, 255).astype(np.uint8))


def backdrop():
    sat = im.max(2) - im.min(2)
    w = ((sat < 22) & (im.min(2) < 250)).astype(np.float32)
    w[:60, :] = 0
    w[-60:, :] = 0
    w[:, :60] = 0
    w[:, -60:] = 0
    out = np.zeros_like(im)
    den = ndimage.gaussian_filter(w, 60) + 1e-6
    for c in range(3):
        out[..., c] = ndimage.gaussian_filter(im[..., c] * w, 60) / den
    return out


BACK = backdrop()


def layers(n):
    s = n * BIRD / 850.0
    x0, y0, x1, y1 = 70, 110, 978, 925
    cw, ch = x1 - x0, y1 - y0
    pw, ph = int(round(cw * s)), int(round(ch * s))
    ox, oy = int(round(n / 2 - (525 - x0) * s)), int(round(n / 2 - (530 - y0) * s))
    top = np.median(im[y0:y0 + 40, 300:700].reshape(-1, 3), 0)
    bot = np.median(im[y1 - 40:y1, 300:700].reshape(-1, 3), 0)
    t = np.clip((np.arange(n) - oy) / max(ph, 1), 0, 1)[:, None, None]
    bg = (top[None, None, :] * (1 - t) + bot[None, None, :] * t) * np.ones((n, n, 3))
    bg = Image.fromarray(bg.clip(0, 255).astype(np.uint8)).convert('RGBA')
    piece = np.asarray(src.crop((x0, y0, x1, y1)).resize((pw, ph), Image.LANCZOS).convert('RGBA')).copy()
    yy, xx = np.mgrid[0:ph, 0:pw].astype(np.float32)
    f = 120 * s
    e = np.minimum.reduce([xx, yy, pw - 1 - xx, ph - 1 - yy]) / f
    a = np.clip(e, 0, 1)
    piece[..., 3] = (a * a * (3 - 2 * a) * 255).astype(np.uint8)
    fg = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    pi = Image.fromarray(piece)
    fg.paste(pi, (ox, oy))
    return bg, fg


def silhouette():
    mn = im.min(2)
    dark = ndimage.binary_opening(mn < 150, iterations=3)
    lab, n = ndimage.label(dark)
    m = lab == lab[466, 600]
    m = ndimage.binary_fill_holes(ndimage.binary_closing(m, iterations=6))
    m = ndimage.binary_opening(m, iterations=10)
    a = ndimage.gaussian_filter(m.astype(np.float32), 4)
    a = np.clip((a - 0.4) / 0.2, 0, 1)
    return a


def main():
    rounded = rounded_logo()
    rounded.save('branding/glyde/logo_rounded.png')
    for k, m in DENS.items():
        n = int(108 * m)
        bg, fg = layers(n)
        bg.save(f'{RES}/mipmap-{k}/ic_launcher_background.png')
        fg.save(f'{RES}/mipmap-{k}/ic_launcher_foreground.png')
        s = int(48 * m)
        rounded.resize((s, s), Image.LANCZOS).save(f'{RES}/mipmap-{k}/ic_launcher.png')
        comb = bg.copy()
        comb.alpha_composite(fg)
        c = comb.crop((int(18 * m), int(18 * m), int(90 * m), int(90 * m))).resize((s, s), Image.LANCZOS)
        mask = Image.new('L', (s * 4, s * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255)
        c.putalpha(mask.resize((s, s), Image.LANCZOS))
        c.save(f'{RES}/mipmap-{k}/ic_launcher_round.png')
    rounded.resize((512, 512), Image.LANCZOS).save('branding/glyde/app_icon_512.png')
    rounded.resize((512, 512), Image.LANCZOS).save(f'{RES}/drawable-nodpi/logo_glass.png')
    a = silhouette()
    ys, xs = np.where(a > 0.05)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    side = max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    sil = Image.fromarray((a * 255).astype(np.uint8))
    white = Image.new('RGBA', sil.size, (255, 255, 255, 0))
    white.putalpha(sil)
    for name, frac, base in [('ic_launcher_mono', 0.5, 108), ('ic_stat_glyde', 0.92, 24)]:
        for k, m in DENS.items():
            n = int(base * m)
            span = side / frac
            box = (int(cx - span / 2), int(cy - span / 2), int(cx + span / 2), int(cy + span / 2))
            canvas = Image.new('RGBA', (int(span), int(span)), (255, 255, 255, 0))
            canvas.paste(white.crop((max(box[0], 0), max(box[1], 0), min(box[2], 1024), min(box[3], 1024))), (max(-box[0], 0), max(-box[1], 0)))
            canvas.resize((n, n), Image.LANCZOS).save(f'{RES}/drawable-{k}/{name}.png')
    bg, fg = layers(432)
    comb = bg.copy()
    comb.alpha_composite(fg)
    prev = Image.new('RGB', (1060, 300), (40, 40, 50))
    for i, shape in enumerate(['circle', 'squircle', 'square']):
        c = comb.crop((72, 72, 360, 360)).resize((240, 240))
        mk = Image.new('L', (240, 240), 0)
        d = ImageDraw.Draw(mk)
        if shape == 'circle':
            d.ellipse((0, 0, 239, 239), fill=255)
        elif shape == 'squircle':
            d.rounded_rectangle((0, 0, 239, 239), 70, fill=255)
        else:
            d.rounded_rectangle((0, 0, 239, 239), 20, fill=255)
        prev.paste(c, (20 + i * 260, 30), mk)
    mono = Image.open(f'{RES}/drawable-xxxhdpi/ic_launcher_mono.png').crop((72, 72, 360, 360)).resize((240, 240))
    prev.paste((200, 220, 255), (800, 30), mono)
    prev.save('branding/glyde/adaptive_preview.png')


if __name__ == '__main__':
    main()
