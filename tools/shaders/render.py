import skia, struct, sys, numpy as np
from PIL import Image
def eff(path):
    src=open(path).read()
    e=skia.RuntimeEffect.MakeForShader(src)
    return e
def draw(path, w, h, uni, out):
    src=open(path).read()
    e=skia.RuntimeEffect.MakeForShader(src)
    if e is None: raise SystemExit('compile fail')
    names=[u.name for u in e.uniforms()]
    data=b''
    for u in e.uniforms():
        v=uni[u.name]
        v=v if isinstance(v,(list,tuple)) else [v]
        data+=struct.pack('%df'%len(v),*v)
    sh=e.makeShader(skia.Data.MakeWithCopy(data))
    surf=skia.Surface(w,h)
    with surf as c:
        c.clear(0)
        p=skia.Paint(Shader=sh)
        c.drawRect(skia.Rect(0,0,w,h),p)
    img=surf.makeImageSnapshot()
    img.save(out)
    return names
