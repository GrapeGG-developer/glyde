import sys
from render import draw
from PIL import Image
outs=[]
for i,(en,dark) in enumerate([(0,0),(1,0),(1,1),(0.55,1)]):
    draw('orb.agsl',400,400,dict(size=[400,400],time=3.0,tilt=[0.0,0.0],tint=[0.1,0.65,0.81,1],energy=en,spin=0,press=0,dark=dark),f'/tmp/o{i}.png')
    bg=Image.new('RGBA',(400,400),(230,240,245,255) if not dark else (10,20,30,255))
    bg.alpha_composite(Image.open(f'/tmp/o{i}.png').convert('RGBA'))
    outs.append(bg)
c=Image.new('RGB',(1600,400))
for i,o in enumerate(outs):c.paste(o,(i*400,0))
c.save(sys.argv[1] if len(sys.argv)>1 else '/tmp/orbs.png')
