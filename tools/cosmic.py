import colorsys
import math
import sys

import skia


def i32(x):
    x &= 0xFFFFFFFF
    return x - (1 << 32) if x & 0x80000000 else x


def spec(name):
    h = i32(0x811C9DC5)
    for ch in name.lower():
        h = i32((h ^ ord(ch)) * 16777619)
    s = h if h != 0 else 1

    def nxt():
        nonlocal s
        s = i32(s ^ (s << 13))
        s = i32(s ^ ((s & 0xFFFFFFFF) >> 17))
        s = i32(s ^ (s << 5))
        return (((s & 0xFFFFFFFF) >> 8) & 0xFFFF) / 65535.0

    kind = (((h & 0xFFFFFFFF) >> 3) & 0x7FFF) % 6
    hue = nxt() * 360
    hue2 = (hue + 40 + nxt() * 140) % 360
    tilt = -35 + nxt() * 70
    arms = 2 + int(nxt() * 2.99)
    bands = 3 + int(nxt() * 2.99)
    moon = nxt() * 360
    sp = [(nxt() * 2 - 1, nxt() * 2 - 1) for _ in range(3)]
    return dict(kind=kind, hue=hue, hue2=hue2, tilt=tilt, arms=arms, bands=bands, moon=moon, sparkles=sp)


def hsv(h, s, v, a=1.0):
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, max(0, min(1, s)), max(0, min(1, v)))
    return skia.Color4f(r, g, b, max(0, min(1, a))).toColor()


def radial(colors, stops, c, r):
    return skia.GradientShader.MakeRadial(skia.Point(*c), r, colors, stops)


def circle(cv, c, r, shader=None, color=None, stroke=0):
    p = skia.Paint(AntiAlias=True)
    if shader:
        p.setShader(shader)
    if color is not None:
        p.setColor(color)
    if stroke:
        p.setStyle(skia.Paint.kStroke_Style)
        p.setStrokeWidth(stroke)
    cv.drawCircle(c[0], c[1], r, p)


def glow(cv, color, c, r):
    col = skia.Color4f(color)
    circle(cv, c, r, radial([color, skia.Color4f(col.fR, col.fG, col.fB, 0).toColor()], [0, 1], c, r))


def sphere(cv, c, r, hue):
    circle(cv, c, r, radial([hsv(hue, .25, 1), hsv(hue, .65, .9), hsv(hue, .85, .35)], [0, .45, 1], (c[0] - r * .35, c[1] - r * .4), r * 1.6))


def gloss(cv, c, r):
    p = skia.Paint(AntiAlias=True)
    p.setShader(skia.GradientShader.MakeLinear([skia.Point(0, c[1] - r * .92), skia.Point(0, c[1] - r * .1)], [skia.Color4f(1, 1, 1, .6).toColor(), skia.Color4f(1, 1, 1, 0).toColor()]))
    cv.drawOval(skia.Rect.MakeXYWH(c[0] - r * .62, c[1] - r * .9, r * 1.24, r * .8), p)


def arc(cv, color, start, sweep, rect, width, rnd=False):
    p = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=width, Color=color)
    if rnd:
        p.setStrokeCap(skia.Paint.kRound_Cap)
    cv.drawArc(rect, start, sweep, False, p)


def draw(cv, name, size):
    sp = spec(name)
    r = size / 2
    c = (r, r)
    k = sp["kind"]
    W = lambda a: skia.Color4f(1, 1, 1, a).toColor()
    if k == 0:
        pr = r * .52
        ring = hsv(sp["hue2"], .35, 1)
        rect = skia.Rect.MakeXYWH(c[0] - r * .95, c[1] - r * .24, r * 1.9, r * .48)
        cv.save(); cv.rotate(sp["tilt"], c[0], c[1]); arc(cv, hsv(sp["hue2"], .35, 1, .55), 180, 180, rect, r * .09); cv.restore()
        sphere(cv, c, pr, sp["hue"]); gloss(cv, c, pr)
        cv.save(); cv.rotate(sp["tilt"], c[0], c[1]); arc(cv, ring, 0, 180, rect, r * .1, True); arc(cv, W(.6), 20, 140, rect, r * .03, True); cv.restore()
    elif k == 1:
        pr = r * .8
        sphere(cv, c, pr, sp["hue"])
        cv.save()
        path = skia.Path(); path.addOval(skia.Rect.MakeXYWH(c[0] - pr, c[1] - pr, pr * 2, pr * 2)); cv.clipPath(path, doAntiAlias=True)
        cv.save(); cv.rotate(sp["tilt"] * .4, c[0], c[1])
        n = sp["bands"]
        for i in range(n):
            y = c[1] - pr + (i + .6) * (2 * pr / (n + .4))
            p = skia.Paint(AntiAlias=True, Color=hsv(sp["hue2"] if i % 2 == 0 else sp["hue"], .55, .95, .38))
            cv.drawOval(skia.Rect.MakeXYWH(c[0] - pr * 1.3, y - pr * .08, pr * 2.6, pr * .16 + (i % 2) * pr * .07), p)
        cv.restore()
        circle(cv, c, pr, radial([skia.ColorTRANSPARENT, skia.Color4f(0, 0, 0, .45).toColor()], [.6, 1], (c[0] - pr * .2, c[1] - pr * .25), pr * 1.25))
        cv.restore()
        gloss(cv, c, pr)
    elif k == 2:
        glow(cv, hsv(sp["hue"], .5, 1, .55), c, r)
        cv.save(); cv.rotate(sp["tilt"], c[0], c[1])
        for a in range(sp["arms"]):
            path = skia.Path()
            for i in range(25):
                t = i / 24
                ang = a * (6.2832 / sp["arms"]) + t * 3.6
                rr = r * (.1 + .82 * t)
                x, y = c[0] + math.cos(ang) * rr, c[1] + math.sin(ang) * rr * .6
                path.moveTo(x, y) if i == 0 else path.lineTo(x, y)
            for col, w in ((hsv(sp["hue2"], .45, 1, .28), r * .22), (hsv(sp["hue2"], .35, 1, .6), r * .09), (W(.4), r * .03)):
                p = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=w, Color=col, StrokeCap=skia.Paint.kRound_Cap)
                cv.drawPath(path, p)
        cv.restore()
        glow(cv, W(1), c, r * .32); circle(cv, c, r * .12, color=W(1))
    elif k == 3:
        cv.save(); cv.rotate(sp["tilt"], c[0], c[1])
        p = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=r * .035, Color=W(.35))
        cv.drawOval(skia.Rect.MakeXYWH(c[0] - r * .8, c[1] - r * .3, r * 1.6, r * .6), p)
        a = (c[0] - r * .42, c[1]); b = (c[0] + r * .5, c[1])
        glow(cv, hsv(sp["hue"], .6, 1, .8), a, r * .55); glow(cv, hsv(sp["hue2"], .6, 1, .8), b, r * .4)
        circle(cv, a, r * .24, radial([W(1), hsv(sp["hue"], .5, 1)], [0, 1], a, r * .24))
        circle(cv, b, r * .16, radial([W(1), hsv(sp["hue2"], .5, 1)], [0, 1], b, r * .16))
        cv.restore()
    elif k == 4:
        rad = math.radians(sp["moon"])
        p1 = (c[0] + math.cos(rad) * r * .3, c[1] + math.sin(rad) * r * .3)
        p2 = (c[0] - math.cos(rad) * r * .32, c[1] - math.sin(rad) * r * .26)
        glow(cv, hsv(sp["hue"], .7, 1, .85), p1, r * .75); glow(cv, hsv(sp["hue2"], .7, 1, .8), p2, r * .7)
        glow(cv, hsv((sp["hue"] + sp["hue2"]) / 2, .4, 1, .6), c, r * .5)
        for (x, y) in sp["sparkles"]:
            circle(cv, (c[0] + x * r * .75, c[1] + y * r * .75), r * .045, color=W(.85))
        glow(cv, W(1), c, r * .28); circle(cv, c, r * .09, color=W(1))
    else:
        pc = (c[0] - r * .1, c[1] + r * .05); pr = r * .66
        glow(cv, hsv(sp["hue"], .5, 1, .35), pc, pr * 1.4)
        sphere(cv, pc, pr, sp["hue"])
        cv.save(); path = skia.Path(); path.addOval(skia.Rect.MakeXYWH(pc[0] - pr, pc[1] - pr, pr * 2, pr * 2)); cv.clipPath(path, doAntiAlias=True)
        circle(cv, (pc[0] + pr * .75, pc[1] + pr * .55), pr * 1.05, color=skia.Color4f(0, 0, 0, .42).toColor())
        circle(cv, (pc[0] - pr * .3, pc[1] + pr * .15), pr * .16, color=hsv(sp["hue"], .6, .5, .35))
        circle(cv, (pc[0] + pr * .15, pc[1] - pr * .35), pr * .1, color=hsv(sp["hue"], .6, .5, .3))
        cv.restore(); gloss(cv, pc, pr)
        rad = math.radians(sp["moon"])
        sphere(cv, (c[0] + math.cos(rad) * r * .78, c[1] + math.sin(rad) * r * .78), r * .17, sp["hue2"])


def render(name, size, path):
    s = skia.Surface(size, size)
    cv = s.getCanvas(); cv.clear(skia.ColorTRANSPARENT)
    draw(cv, name, size)
    s.makeImageSnapshot().save(path)


if __name__ == "__main__":
    render(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 256, sys.argv[3] if len(sys.argv) > 3 else sys.argv[1] + ".png")
