import sys, os, math
from shapely.geometry import LineString, Point, Polygon, MultiPolygon
from shapely.ops import unary_union
from shapely.geometry.polygon import orient
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from glyphs import *

FAMILY = "Glasswave"
SB = 46


def lower(g, m, ch, sc):
    h = m.h
    b, x, a, d = m.b, m.x, m.a, m.d
    ry = (x - b) / 2
    cy = (x + b) / 2

    def bowl(cx, rx, n=SQ):
        return arc(cx, cy, rx, ry, 0, 360, n)

    W = None
    if ch == 'a':
        W = 300; g.P(bowl(W / 2, W / 2)); g.L((W, x), (W, b))
    elif ch == 'b':
        W = 300; g.L((0, a), (0, b)); g.P(bowl(W / 2, W / 2))
    elif ch == 'c':
        W = 280; g.P(arc(W / 2, cy, W / 2, ry, 45, 315, SQ))
    elif ch == 'd':
        W = 300; g.P(bowl(W / 2, W / 2)); g.L((W, a), (W, b))
    elif ch == 'e':
        W = 300; g.P(chain([(0, cy), (W, cy)], arc(W / 2, cy, W / 2, ry, 0, 318, SQ)))
    elif ch == 'f':
        W = 205; x0 = 62
        g.P(chain([(x0, b), (x0, a - 95)], arc(x0 + 95, a - 95, 95, 95, 180, 60)))
        g.L((0, x), (W, x))
    elif ch == 'g':
        W = 300; g.P(bowl(W / 2, W / 2))
        g.P(chain([(W, x), (W, b - 10)], arc(W / 2, b - 10, W / 2, (b - 10) - d, 0, -158, SQ)))
    elif ch == 'h':
        W = 280; g.L((0, a), (0, b))
        g.P(chain([(0, cy + 20)], arc(W / 2, cy + 20, W / 2, x - cy - 20, 180, 0, SQ), [(W, b)]))
    elif ch == 'i':
        W = 0; g.L((0, x), (0, b)); g.dot(0, a - 20)
    elif ch == 'j':
        W = 120; g.P(chain([(W, x), (W, b - 30)], arc(W - 100, b - 30, 100, (b - 30) - d, 0, -150)))
        g.dot(W, a - 20)
    elif ch == 'k':
        W = 250; g.L((0, a), (0, b)); y0 = b + (x - b) * 0.32
        g.L((W * 0.95, x), (0, y0)); s = 0.38; g.L((W * 0.95 * s, y0 + (x - y0) * s), (W, b))
    elif ch == 'l':
        W = 110; g.P(chain([(0, a), (0, b + 95)], arc(95, b + 95, 95, 95, 180, 270), [(W, b)]))
    elif ch == 'm':
        W = 470; g.L((0, x), (0, b)); hw = W / 2
        g.P(chain([(0, cy + 30)], arc(hw / 2, cy + 30, hw / 2, x - cy - 30, 180, 0, SQ), [(hw, b)]))
        g.P(chain([(hw, cy + 30)], arc(hw * 1.5, cy + 30, hw / 2, x - cy - 30, 180, 0, SQ), [(W, b)]))
    elif ch == 'n':
        W = 280; g.L((0, x), (0, b))
        g.P(chain([(0, cy + 20)], arc(W / 2, cy + 20, W / 2, x - cy - 20, 180, 0, SQ), [(W, b)]))
    elif ch == 'o':
        W = 300; g.P(bowl(W / 2, W / 2))
    elif ch == 'p':
        W = 300; g.L((0, x), (0, d)); g.P(bowl(W / 2, W / 2))
    elif ch == 'q':
        W = 300; g.P(bowl(W / 2, W / 2)); g.L((W, x), (W, d))
    elif ch == 'r':
        W = 185; g.L((0, x), (0, b))
        g.P(chain([(0, cy - 10)], arc(W * 0.95, cy - 10, W * 0.95, x - cy + 10, 180, 88, SQ)))
    elif ch == 's':
        W = 250; r = (x - b) / 4
        g.P(chain(arc(W / 2, x - r, W / 2 * 0.94, r, 22, 270), arc(W / 2, b + r, W / 2, r, 90, -158)))
    elif ch == 't':
        W = 200; x0 = 62
        g.P(chain([(x0, a - 70), (x0, b + 95)], arc(x0 + 95, b + 95, 95, 95, 180, 270), [(W, b)]))
        g.L((0, x), (W - 10, x))
    elif ch == 'u':
        W = 280; g.P(chain([(0, x), (0, cy - 20)], arc(W / 2, cy - 20, W / 2, cy - 20 - b, 180, 360, SQ)))
        g.L((W, x), (W, b))
    elif ch == 'v':
        W = 290; g.L((0, x), (W / 2, b), (W, x))
    elif ch == 'w':
        W = 440; g.L((0, x), (W * 0.25, b), (W / 2, b + (x - b) * 0.78), (W * 0.75, b), (W, x))
    elif ch == 'x':
        W = 280; g.L((0, x), (W, b)); g.L((W, x), (0, b))
    elif ch == 'y':
        W = 290; g.L((0, x), (W / 2, b)); g.L((W, x), (W / 2 - (W / 2) * (b - d) / (x - b), d))
    elif ch == 'z':
        W = 260; g.L((W * 0.03, x), (W, x), (0, b), (W, b))
    return W


UP = {
    'A': (U_A, 500), 'B': (U_B, 390), 'C': (U_C, 520), 'D': (U_D, 450), 'E': (U_E, 340), 'F': (U_F, 330),
    'G': (U_G, 540), 'H': (U_H, 440), 'I': (U_I, 0), 'J': (U_J, 330), 'K': (U_K, 400), 'L': (U_L, 320),
    'M': (U_M, 560), 'N': (U_N, 450), 'O': (U_O, 590), 'P': (U_P, 380), 'Q': (U_Q, 590), 'R': (U_R, 400),
    'S': (U_S, 380), 'T': (U_T, 420), 'U': (U_U, 440), 'V': (U_Vv, 500), 'W': (U_W, 720), 'X': (U_X, 460),
    'Y': (U_Y, 470), 'Z': (U_Z, 420),
}

CYR_UP = {
    0x0410: ('A', None), 0x0412: ('B', None), 0x0415: ('E', None), 0x041A: ('K', None), 0x041C: ('M', None),
    0x041D: ('H', None), 0x041E: ('O', None), 0x0420: ('P', None), 0x0421: ('C', None), 0x0422: ('T', None),
    0x0425: ('X', None),
}


def cyr_upper(g, m, cp, H, k=1.0):
    def w(v):
        return v * k
    desc = m.b - (130 if H == CAP else 120)
    if cp == 0x0411: W = w(380); C_BE(g, m, W, H)
    elif cp == 0x0413: W = w(320); C_GE(g, m, W, H)
    elif cp == 0x0414: W = w(500); C_DE(g, m, W, H, desc)
    elif cp == 0x0401: W = w(340); U_E(g, m, W, H); g.dot(W * 0.28, m.top(H) + 125 * (H / CAP) + 20, 1.1); g.dot(W * 0.78, m.top(H) + 125 * (H / CAP) + 20, 1.1)
    elif cp == 0x0416: W = w(640); C_ZHE(g, m, W, H)
    elif cp == 0x0417: W = w(380); C_ZE(g, m, W, H)
    elif cp == 0x0418: W = w(450); C_I(g, m, W, H)
    elif cp == 0x0419: W = w(450); C_I(g, m, W, H); C_BREVE(g, m, W, m.top(H) + 55)
    elif cp == 0x041B: W = w(450); C_EL(g, m, W, H)
    elif cp == 0x041F: W = w(440); C_PE(g, m, W, H)
    elif cp == 0x0423: W = w(470); C_U(g, m, W, H)
    elif cp == 0x0424: W = w(560); C_EF(g, m, W, H)
    elif cp == 0x0426: W = w(500); C_TSE(g, m, W, H, desc)
    elif cp == 0x0427: W = w(400); C_CHE(g, m, W, H)
    elif cp == 0x0428: W = w(600); C_SHA(g, m, W, H)
    elif cp == 0x0429: W = w(660); C_SHCHA(g, m, W, H, desc)
    elif cp == 0x042A: W = w(470); C_HARD(g, m, W, H)
    elif cp == 0x042B: W = w(560); C_YERU(g, m, W, H)
    elif cp == 0x042C: W = w(370); C_SOFT(g, m, W, H)
    elif cp == 0x042D: W = w(520); C_E(g, m, W, H)
    elif cp == 0x042E: W = w(700); C_YU(g, m, W, H)
    elif cp == 0x042F: W = w(400); C_YA(g, m, W, H)
    else:
        return None
    return W


def cyr_lower(g, m, cp):
    h = m.h
    up = cp - 0x20 if 0x0430 <= cp <= 0x044F else None
    if cp == 0x0451:
        W = lower(g, m, 'e', None)
        g.dot(W * 0.22, m.a - 20, 1.1); g.dot(W * 0.78, m.a - 20, 1.1)
        return W
    same = {0x0430: 'a', 0x0435: 'e', 0x043E: 'o', 0x0440: 'p', 0x0441: 'c', 0x0443: 'y', 0x0445: 'x'}
    if cp in same:
        return lower(g, m, same[cp], None)
    if cp == 0x0431:
        W = 300; b, x = m.b, m.x; cy = (x + b) / 2; ry = (x - b) / 2
        g.P(arc(W / 2, cy, W / 2, ry, 0, 360, SQ))
        g.P(chain([(W * 0.02, cy + 10)], [(W * 0.08, m.a - 160)], arc(W * 0.08 + 150, m.a - 160, 150, 160 - 0, 180, 90)[1:], [(W, m.a)]))
        return W
    if cp == 0x0444:
        W = 440; C_EF(g, m, W, XH, top=m.a, bot=m.d)
        return W
    if cp == 0x0439:
        W = 330; C_I(g, m, W, XH); C_BREVE(g, m, W, m.x + 60)
        return W
    if cp == 0x0434:
        W = 360; C_DE(g, m, W, XH, m.b - 125)
        return W
    if cp == 0x0446:
        W = 360; C_TSE(g, m, W, XH, m.b - 125)
        return W
    if cp == 0x0449:
        W = 500; C_SHCHA(g, m, W, XH, m.b - 125)
        return W
    if up is not None:
        k = 0.80
        return cyr_upper(g, m, up, XH, k) if up not in CYR_UP else build_upper(g, m, CYR_UP[up][0], XH, k)
    return None


def build_upper(g, m, ch, H, k=1.0):
    fn, W = UP[ch]
    W = W * k
    fn(g, m, W, H)
    return W


DIGIT_W = 360


def digit(g, m, ch):
    W = DIGIT_W
    t, b = m.c, m.b
    cy = (t + b) / 2
    if ch == '0': g.P(arc(W / 2, cy, W / 2, (t - b) / 2, 0, 360, SQ))
    elif ch == '1': g.L((W * 0.18, t - (t - b) * 0.2), (W * 0.58, t), (W * 0.58, b))
    elif ch == '2':
        r = (t - b) * 0.27
        g.P(chain(arc(W / 2, t - r, W / 2, r, 160, -38), [(0, b), (W, b)]))
    elif ch == '3':
        r1 = (t - b) * 0.24; r2 = (t - b) * 0.26
        g.P(chain(arc(W * 0.5, t - r1, W * 0.46, r1, 150, -90), arc(W / 2, b + r2, W / 2, r2, 90, -155)))
    elif ch == '4':
        y = b + (t - b) * 0.28
        g.L((W * 0.74, b), (W * 0.74, t), (0, y), (W, y))
    elif ch == '5':
        r = (t - b) * 0.32
        p0 = arc(W / 2, b + r, W / 2, r, 132, 132)[0]
        g.P(chain([(W * 0.92, t), (W * 0.14, t), (p0[0] - 6, p0[1] + 8)], arc(W / 2, b + r, W / 2, r, 132, -150)))
    elif ch == '6':
        r = (t - b) * 0.32
        g.P(arc(W / 2, b + r, W / 2, r, 0, 360, SQ))
        g.L((W * 0.035, b + r * 1.15), (W * 0.62, t))
    elif ch == '7': g.L((0, t), (W, t), (W * 0.32, b))
    elif ch == '8':
        r1 = (t - b) * 0.23; r2 = (t - b) * 0.27
        g.P(arc(W / 2, t - r1, W * 0.42, r1, 0, 360, 2.0))
        g.P(arc(W / 2, b + r2, W / 2, r2, 0, 360, SQ))
    elif ch == '9':
        r = (t - b) * 0.32
        g.P(arc(W / 2, t - r, W / 2, r, 0, 360, SQ))
        g.L((W * 0.965, t - r * 1.15), (W * 0.38, b))
    return W


def punct(g, m, ch):
    t, b, x, h = m.c, m.b, m.x, m.h
    if ch == '.': g.dot(0, b + 8, 1.15); return 0
    if ch == ',': g.dot(0, b + 8, 1.15); g.L((0, b + 8), (-30, b - 120)); return 0
    if ch == ':': g.dot(0, b + 8, 1.15); g.dot(0, x - 8, 1.15); return 0
    if ch == ';': g.dot(0, b + 8, 1.15); g.L((0, b + 8), (-30, b - 120)); g.dot(0, x - 8, 1.15); return 0
    if ch == '!': g.L((0, t), (0, b + 190)); g.dot(0, b + 8, 1.15); return 0
    if ch == '?':
        W = 290; r = (t - b) * 0.24
        g.P(chain(arc(W / 2, t - r, W / 2, r, 160, -60), [(W / 2 + 10, b + 260), (W / 2 + 10, b + 190)]))
        g.dot(W / 2 + 10, b + 8, 1.15); return W
    if ch == '-': g.L((0, 300), (220, 300)); return 220
    if ch == '\u2013': g.L((0, 300), (440, 300)); return 440
    if ch == '\u2014': g.L((0, 300), (820, 300)); return 820
    if ch == '_': g.L((0, b - 90), (480, b - 90)); return 480
    if ch == '(':
        W = 150; g.P(arc(W * 1.6, 290, W * 1.6, 480, 125, 235, 2.0)); return 0.3 * W
    if ch == ')':
        W = 150; g.P(arc(-W * 1.6 + 45, 290, W * 1.6, 480, 55, -55, 2.0)); return 0.3 * W
    if ch == '[': g.L((110, t + 40), (0, t + 40), (0, b - 140), (110, b - 140)); return 110
    if ch == ']': g.L((0, t + 40), (110, t + 40), (110, b - 140), (0, b - 140)); return 110
    if ch == '/': g.L((0, b - 80), (300, t + 60)); return 300
    if ch == '\\': g.L((0, t + 60), (300, b - 80)); return 300
    if ch == '|': g.L((0, t + 60), (0, b - 180)); return 0
    if ch == '+': g.L((0, 300), (380, 300)); g.L((190, 110), (190, 490)); return 380
    if ch == '=': g.L((0, 210), (380, 210)); g.L((0, 390), (380, 390)); return 380
    if ch == '<': g.L((360, 520), (0, 300), (360, 80)); return 360
    if ch == '>': g.L((0, 520), (360, 300), (0, 80)); return 360
    if ch == '*':
        cx, cy, r = 150, t - 130, 125
        for a in (90, 210, 330):
            g.L((cx, cy), (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))))
            g.L((cx, cy), (cx - r * math.cos(math.radians(a)) * 0.0 + 0, cy))
        for a in (30, 150, 270):
            g.L((cx, cy), (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))))
        return 300
    if ch == '"': g.L((0, t + 20), (0, t - 160)); g.L((150, t + 20), (150, t - 160)); return 150
    if ch == "'": g.L((0, t + 20), (0, t - 160)); return 0
    if ch == '\u00ab':
        g.L((190, 470), (40, 290), (190, 110)); g.L((390, 470), (240, 290), (390, 110)); return 390
    if ch == '\u00bb':
        g.L((0, 470), (150, 290), (0, 110)); g.L((200, 470), (350, 290), (200, 110)); return 350
    if ch == '\u2026':
        for i in range(3): g.dot(i * 230, b + 8, 1.15)
        return 460
    if ch == '\u00b7': g.dot(0, 300, 1.15); return 0
    if ch == '%':
        g.P(arc(110, t - 130, 110, 130, 0, 360, SQ)); g.P(arc(470, b + 130, 110, 130, 0, 360, SQ))
        g.L((60, b), (520, t)); return 580
    if ch == '#':
        g.L((140, t - 40), (100, b + 40)); g.L((340, t - 40), (300, b + 40))
        g.L((10, 450), (440, 450)); g.L((0, 230), (430, 230)); return 440
    if ch == '@':
        R = 360; cy = 280
        g.P(chain(arc(R, cy, R, R, -20, 330, 2.0)))
        g.P(arc(R, cy, 140, 150, 0, 360, SQ)); g.L((R + 140, cy + 150), (R + 140, cy - 80), (R + 230, cy - 120))
        return 2 * R
    if ch == '&':
        W = 470; r = 115
        g.P(chain([(W, b)], [(W * 0.25, t - 2 * r + 30)], arc(W * 0.42, t - r, r * 1.05, r, 210, -30, 2.0), [(W * 0.18, b + 300)],
                  arc(W * 0.38, b + 165, W * 0.36, 165, 150, 300, 2.0), [(W * 0.92, b + 260)]))
        return W
    if ch == '\u00b0': g.P(arc(90, t - 90, 90, 90, 0, 360, 2.0)); return 180
    if ch == '\u20bd':
        W = 400; mid = b + (t - b) * 0.44
        g.P(chain([(W * 0.16, b), (W * 0.16, t)], side_bowl(W * 0.16, W, t, mid, (t - mid) / 2)))
        g.L((0, mid), (W * 0.16, mid)); g.L((0, b + 140), (W * 0.7, b + 140)); return W
    if ch == '\u2116':
        W = 720; C_I_like = [(0, b), (0, t), (360, b), (360, t)]
        g.P(C_I_like); g.P(arc(560, x - 150, 110, 150, 0, 360, SQ)); g.L((440, b + 90), (680, b + 90)); return W
    if ch == '\u2022': g.dot(0, 300, 1.9); return 0
    if ch == '\u2191': g.L((150, b), (150, t)); g.L((0, t - 150), (150, t), (300, t - 150)); return 300
    if ch == '\u2193': g.L((150, t), (150, b)); g.L((0, b + 150), (150, b), (300, b + 150)); return 300
    if ch == '\u2192': g.L((0, 300), (480, 300)); g.L((330, 450), (480, 300), (330, 150)); return 480
    if ch == '\u2190': g.L((480, 300), (0, 300)); g.L((150, 450), (0, 300), (150, 150)); return 480
    return None


PUNCT = list(".,:;!?-_()[]/\\|+=<>*\"'%#@&") + ['\u2013', '\u2014', '\u00ab', '\u00bb', '\u2026', '\u00b7', '\u00b0', '\u20bd', '\u2116', '\u2022', '\u2191', '\u2193', '\u2192', '\u2190']


def outline(g):
    geoms = []
    h = g.h
    for st in g.strokes:
        if len(st) == 1:
            geoms.append(Point(st[0]).buffer(h, 24))
        else:
            geoms.append(LineString(st).buffer(h, quad_segs=12, cap_style='round', join_style='round'))
    for (x, y, r) in g.dots:
        geoms.append(Point(x, y).buffer(r, 24))
    if not geoms:
        return None
    u = unary_union(geoms).simplify(0.6, preserve_topology=True)
    return u


def draw(pen, geom, dx):
    polys = [geom] if isinstance(geom, Polygon) else list(geom.geoms)
    for p in polys:
        p = orient(p, sign=-1.0)
        rings = [p.exterior] + list(p.interiors)
        for ring in rings:
            pts = list(ring.coords)[:-1]
            pts = [(round(x + dx), round(y)) for x, y in pts]
            ded = []
            for q in pts:
                if not ded or ded[-1] != q:
                    ded.append(q)
            if len(ded) > 1 and ded[0] == ded[-1]:
                ded.pop()
            if len(ded) < 3:
                continue
            pen.moveTo(ded[0])
            for q in ded[1:]:
                pen.lineTo(q)
            pen.closePath()


def build(weight_name, weight, h, out):
    m = M(h)
    glyphs = {}
    cmap = {}
    adv = {}

    def add(name, cp, fn):
        g = G(h)
        W = fn(g)
        if W is None:
            return
        geom = outline(g)
        pen = TTGlyphPen(None)
        if geom is not None and not geom.is_empty:
            minx = geom.bounds[0]
            dx = SB - minx
            draw(pen, geom, dx)
            width = int(round(geom.bounds[2] - geom.bounds[0] + 2 * SB))
        else:
            width = int(W)
        glyphs[name] = pen.glyph()
        adv[name] = width
        if cp is not None:
            cmap[cp] = name

    pen = TTGlyphPen(None)
    glyphs['.notdef'] = pen.glyph(); adv['.notdef'] = 500
    for name, cp, w in (('space', 0x20, 250), ('uni00A0', 0xA0, 250), ('uni2009', 0x2009, 120), ('uni202F', 0x202F, 160)):
        glyphs[name] = TTGlyphPen(None).glyph(); adv[name] = w; cmap[cp] = name

    for ch in UP:
        add(ch, ord(ch), lambda g, ch=ch: build_upper(g, m, ch, CAP))
    for ch in 'abcdefghijklmnopqrstuvwxyz':
        add(ch, ord(ch), lambda g, ch=ch: lower(g, m, ch, None))
    for ch in '0123456789':
        g0 = None
        add('d' + ch, ord(ch), lambda g, ch=ch: digit(g, m, ch))
    for cp in list(range(0x0410, 0x0430)) + [0x0401]:
        if cp in CYR_UP:
            add('uni%04X' % cp, cp, lambda g, cp=cp: build_upper(g, m, CYR_UP[cp][0], CAP))
        else:
            add('uni%04X' % cp, cp, lambda g, cp=cp: cyr_upper(g, m, cp, CAP))
    for cp in list(range(0x0430, 0x0450)) + [0x0451]:
        add('uni%04X' % cp, cp, lambda g, cp=cp: cyr_lower(g, m, cp))
    for ch in PUNCT:
        add('uni%04X' % ord(ch), ord(ch), lambda g, ch=ch: punct(g, m, ch))

    dw = max(adv['d' + c] for c in '0123456789')
    for c in '0123456789':
        n = 'd' + c
        g = glyphs[n]
        extra = (dw - adv[n]) // 2
        if extra and hasattr(g, 'coordinates') and g.numberOfContours > 0:
            g.coordinates.translate((extra, 0))
        adv[n] = dw

    order = ['.notdef'] + [n for n in glyphs if n != '.notdef']
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    metrics = {}
    gs = fb.font['glyf']
    for n in order:
        g = gs[n]
        g.recalcBounds(gs)
        lsb = getattr(g, 'xMin', 0) if g.numberOfContours else 0
        metrics[n] = (adv[n], lsb)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=900, descent=-260, lineGap=0)
    style = weight_name
    full = f"{FAMILY} {style}" if style != "Regular" else FAMILY
    fb.setupNameTable({
        'familyName': FAMILY,
        'styleName': style,
        'uniqueFontIdentifier': f"GREENTUNE:{FAMILY}-{style}:2.0",
        'fullName': f"{FAMILY} {style}",
        'psName': f"{FAMILY}-{style.replace(' ', '')}",
        'version': "Version 2.000",
        'manufacturer': "GREENTUNE",
        'designer': "GREENTUNE",
        'description': "Glasswave is a rounded geometric typeface generated for GT Unblock.",
        'licenseDescription': "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
        'licenseInfoURL': "https://openfontlicense.org",
        'copyright': "Copyright 2026 GREENTUNE",
    })
    fb.setupOS2(
        sTypoAscender=880, sTypoDescender=-240, sTypoLineGap=0, usWinAscent=960, usWinDescent=300,
        sxHeight=XH, sCapHeight=CAP, usWeightClass=weight, achVendID="GRTN",
        fsSelection=(0x40 if style == "Regular" else 0) | (0x20 if style == "Bold" else 0) | 0x80,
        ulUnicodeRange1=(1 << 0) | (1 << 1) | (1 << 9), ulCodePageRange1=(1 << 0) | (1 << 2),
    )
    fb.setupPost()
    fb.setupHead(unitsPerEm=1000, fontRevision=2.0, macStyle=(1 if style == "Bold" else 0))
    fb.save(out)
    return len(order)


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name, wt, h, fn in (("Light", 300, 29, "glasswave_light.ttf"), ("Regular", 400, 39, "glasswave_regular.ttf"),
                            ("SemiBold", 600, 51, "glasswave_semibold.ttf"), ("Bold", 700, 61, "glasswave_bold.ttf")):
        n = build(name, wt, h, os.path.join(out, fn))
        print(fn, n, os.path.getsize(os.path.join(out, fn)))
