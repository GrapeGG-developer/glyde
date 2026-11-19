import math

CAP = 700
XH = 515
ASC = 750
DESC = -215
SQ = 2.3


class G:
    def __init__(self, h):
        self.h = h
        self.strokes = []
        self.dots = []

    def L(self, *pts):
        self.strokes.append(list(pts))

    def P(self, pts):
        self.strokes.append(pts)

    def dot(self, x, y, k=1.18):
        self.dots.append((x, y, self.h * k))


def arc(cx, cy, rx, ry, a0, a1, n=2.0, step=2.5):
    pts = []
    cnt = max(2, int(abs(a1 - a0) / step) + 1)
    e = 2.0 / n
    for i in range(cnt + 1):
        t = math.radians(a0 + (a1 - a0) * i / cnt)
        c, s = math.cos(t), math.sin(t)
        x = cx + rx * math.copysign(abs(c) ** e, c)
        y = cy + ry * math.copysign(abs(s) ** e, s)
        pts.append((x, y))
    return pts


def chain(*parts):
    out = []
    for p in parts:
        for q in p:
            if not out or abs(out[-1][0] - q[0]) > 0.01 or abs(out[-1][1] - q[1]) > 0.01:
                out.append(q)
    return out


def side_bowl(x0, xr, yt, yb, r, n=2.0):
    r = min(r, (yt - yb) / 2, xr - x0)
    return chain(
        [(x0, yt), (xr - r, yt)],
        arc(xr - r, yt - r, r, r, 90, 0, n),
        [(xr, yb + r)],
        arc(xr - r, yb + r, r, r, 0, -90, n),
        [(x0, yb)],
    )


def side_bowl_left(x0, xl, yt, yb, r, n=2.0):
    r = min(r, (yt - yb) / 2, x0 - xl)
    return chain(
        [(x0, yt), (xl + r, yt)],
        arc(xl + r, yt - r, r, r, 90, 180, n),
        [(xl, yb + r)],
        arc(xl + r, yb + r, r, r, 180, 270, n),
        [(x0, yb)],
    )


class M:
    def __init__(self, h):
        self.h = h
        self.b = h
        self.c = CAP - h
        self.x = XH - h
        self.a = ASC - h
        self.d = DESC + h

    def top(self, H):
        return H - self.h

    def mid(self, H, f=0.5):
        return self.b + (H - 2 * self.h) * f


def U_A(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, b), (W / 2, t), (W, b))
    y = m.mid(H, 0.30)
    g.L((W * 0.5 * (y - b) / (t - b), y), (W - W * 0.5 * (y - b) / (t - b), y))


def U_B(g, m, W, H):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.53)
    g.P(chain([(0, b), (0, t)], side_bowl(0, W * 0.88, t, mid, (t - mid) / 2)[0:]))
    g.P(side_bowl(0, W, mid, b, (mid - b) / 2))


def U_V(g, m, W, H):
    U_B(g, m, W, H)


def U_C(g, m, W, H):
    t, b = m.top(H), m.b
    g.P(arc(W / 2, (t + b) / 2, W / 2, (t - b) / 2, 42, 318, SQ))


def U_D(g, m, W, H):
    t, b = m.top(H), m.b
    g.P(chain([(0, b), (0, t)], side_bowl(0, W, t, b, (t - b) * 0.42, SQ)))


def U_E(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((W, t), (0, t), (0, b), (W, b))
    g.L((0, m.mid(H, 0.5)), (W * 0.84, m.mid(H, 0.5)))


def U_F(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((W, t), (0, t), (0, b))
    g.L((0, m.mid(H, 0.48)), (W * 0.84, m.mid(H, 0.48)))


def U_G(g, m, W, H):
    t, b = m.top(H), m.b
    cy = (t + b) / 2
    g.P(chain(arc(W / 2, cy, W / 2, (t - b) / 2, 42, 360, SQ), [(W, m.mid(H, 0.42)), (W * 0.55, m.mid(H, 0.42))]))


def U_H(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, b), (0, t))
    g.L((W, b), (W, t))
    g.L((0, m.mid(H, 0.5)), (W, m.mid(H, 0.5)))


def U_I(g, m, W, H):
    g.L((0, m.b), (0, m.top(H)))


def U_J(g, m, W, H):
    t, b = m.top(H), m.b
    r = W / 2
    g.P(chain([(W, t)], arc(W / 2, b + r * 1.05, W / 2, r * 1.05, 0, -165, SQ)))


def U_K(g, m, W, H):
    t, b = m.top(H), m.b
    y0 = m.mid(H, 0.34)
    g.L((0, b), (0, t))
    g.L((W * 0.95, t), (0, y0))
    s = 0.36
    g.L((W * 0.95 * s, y0 + (t - y0) * s), (W, b))


def U_L(g, m, W, H):
    g.L((0, m.top(H)), (0, m.b), (W, m.b))


def U_M(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, b), (0, t), (W / 2, m.mid(H, 0.30)), (W, t), (W, b))


def U_N(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, b), (0, t), (W, b), (W, t))


def U_O(g, m, W, H):
    t, b = m.top(H), m.b
    g.P(arc(W / 2, (t + b) / 2, W / 2, (t - b) / 2, 0, 360, SQ))


def U_P(g, m, W, H):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.44)
    g.P(chain([(0, b), (0, t)], side_bowl(0, W, t, mid, (t - mid) / 2)))


def U_Q(g, m, W, H):
    U_O(g, m, W, H)
    g.L((W * 0.62, m.mid(H, 0.24)), (W * 1.02, m.b - H * 0.04))


def U_R(g, m, W, H):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.44)
    g.P(chain([(0, b), (0, t)], side_bowl(0, W * 0.96, t, mid, (t - mid) / 2)))
    g.L((W * 0.48, mid), (W, b))


def U_S(g, m, W, H):
    t, b = m.top(H), m.b
    r = (t - b) / 4
    cx = W / 2
    g.P(chain(arc(cx, t - r, W / 2 * 0.94, r, 18, 270, 2.0), arc(cx, b + r, W / 2, r, 90, -162, 2.0)))


def U_T(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, t), (W, t))
    g.L((W / 2, t), (W / 2, b))


def U_U(g, m, W, H):
    t, b = m.top(H), m.b
    r = W / 2
    g.P(chain([(0, t)], arc(W / 2, b + r, W / 2, r, 180, 360, SQ), [(W, t)]))


def U_Vv(g, m, W, H):
    g.L((0, m.top(H)), (W / 2, m.b), (W, m.top(H)))


def U_W(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, t), (W * 0.25, b), (W / 2, m.mid(H, 0.72)), (W * 0.75, b), (W, t))


def U_X(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, t), (W, b))
    g.L((W, t), (0, b))


def U_Y(g, m, W, H):
    t, b = m.top(H), m.b
    y = m.mid(H, 0.46)
    g.L((0, t), (W / 2, y), (W, t))
    g.L((W / 2, y), (W / 2, b))


def U_Z(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((W * 0.03, t), (W, t), (0, b), (W, b))


def C_BE(g, m, W, H):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.56)
    g.P(chain([(W * 0.92, t), (0, t), (0, b)], []))
    g.P(chain([(0, mid)], side_bowl(0, W, mid, b, (mid - b) / 2)))


def C_GE(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((W, t), (0, t), (0, b))


def C_DE(g, m, W, H, desc):
    t, b = m.top(H), m.b
    g.L((0, desc), (0, b), (W, b), (W, desc))
    g.L((W * 0.10, b), (W * 0.30, t), (W * 0.84, t), (W * 0.84, b))


def C_ZHE(g, m, W, H):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.5)
    g.L((W / 2, b), (W / 2, t))
    g.L((W * 0.04, t), (W / 2 - W * 0.02, mid))
    g.L((W * 0.96, t), (W / 2 + W * 0.02, mid))
    g.L((0, b), (W * 0.36, mid + (t - b) * 0.06))
    g.L((W, b), (W * 0.64, mid + (t - b) * 0.06))


def C_ZE(g, m, W, H):
    t, b = m.top(H), m.b
    r1 = (t - b) * 0.24
    r2 = (t - b) * 0.26
    g.P(chain(arc(W * 0.47, t - r1, W * 0.45, r1, 150, -90, 2.0), [(W * 0.42, b + 2 * r2)]))
    g.P(chain([(W * 0.42, b + 2 * r2)], arc(W / 2, b + r2, W / 2, r2, 90, -152, 2.0)))


def C_I(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, t), (0, b), (W, t), (W, b))


def C_BREVE(g, m, W, y):
    g.P(arc(W / 2, y + 85, W * 0.2, 85, 200, 340))


def C_EL(g, m, W, H):
    t, b = m.top(H), m.b
    g.P(chain([(0, b)], arc(W * 0.22, b + (t - b) * 0.55, W * 0.22, (t - b) * 0.55, 270, 360)[1:], [(W * 0.22, t), (W, t), (W, b)]))


def C_PE(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, b), (0, t), (W, t), (W, b))


def C_U(g, m, W, H, desc=None):
    t, b = m.top(H), m.b
    g.L((0, t), (W * 0.52, m.mid(H, 0.30)))
    g.L((W, t), (W * 0.30, b if desc is None else desc))


def C_EF(g, m, W, H, top=None, bot=None):
    t, b = m.top(H), m.b
    g.L((W / 2, b if bot is None else bot), (W / 2, t if top is None else top))
    yb = m.mid(H, 0.16)
    yt = m.mid(H, 0.86)
    g.P(arc(W / 2, (yt + yb) / 2, W / 2, (yt - yb) / 2, 0, 360, SQ))


def C_TSE(g, m, W, H, desc):
    t, b = m.top(H), m.b
    g.P([(0, t), (0, b), (W * 0.86, b), (W * 0.86, t)])
    g.L((W * 0.86, b), (W, b), (W, desc))


def C_CHE(g, m, W, H):
    t, b = m.top(H), m.b
    y = m.mid(H, 0.42)
    r = (t - b) * 0.22
    g.P(chain([(0, t), (0, y + r)], arc(r, y + r, r, r, 180, 270), [(W, y)]))
    g.L((W, t), (W, b))


def C_SHA(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, t), (0, b), (W, b), (W, t))
    g.L((W / 2, b), (W / 2, t))


def C_SHCHA(g, m, W, H, desc):
    t, b = m.top(H), m.b
    w = W * 0.88
    g.L((0, t), (0, b), (w, b), (w, t))
    g.L((w / 2, b), (w / 2, t))
    g.L((w, b), (W, b), (W, desc))


def C_SOFT(g, m, W, H, x0=0.0):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.56)
    g.L((x0, t), (x0, b))
    g.P(chain([(x0, mid)], side_bowl(x0, W, mid, b, (mid - b) / 2)))


def C_HARD(g, m, W, H):
    t, b = m.top(H), m.b
    x0 = W * 0.26
    g.L((0, t), (x0, t))
    C_SOFT(g, m, W, H, x0)


def C_YERU(g, m, W, H):
    t, b = m.top(H), m.b
    C_SOFT(g, m, W * 0.66, H)
    g.L((W, t), (W, b))


def C_E(g, m, W, H):
    t, b = m.top(H), m.b
    g.P(arc(W / 2, (t + b) / 2, W / 2, (t - b) / 2, 138, -138, SQ))
    g.L((W * 0.32, m.mid(H, 0.5)), (W, m.mid(H, 0.5)))


def C_YU(g, m, W, H):
    t, b = m.top(H), m.b
    g.L((0, b), (0, t))
    x1 = W * 0.30
    g.L((0, m.mid(H, 0.5)), (x1, m.mid(H, 0.5)))
    r = (W - x1) / 2
    g.P(arc(x1 + r, (t + b) / 2, r, (t - b) / 2, 0, 360, SQ))


def C_YA(g, m, W, H):
    t, b = m.top(H), m.b
    mid = m.mid(H, 0.44)
    g.P(chain([(W, b), (W, t)], side_bowl_left(W, W * 0.04, t, mid, (t - mid) / 2)))
    g.L((W * 0.52, mid), (0, b))
