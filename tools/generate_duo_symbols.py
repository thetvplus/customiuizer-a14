"""Prepare used 24B91020 vector masters. Pass the private glyphs.json export.

CoreUI middle master raw4 is explicit, not a resolved UIFont bold request.
The Android offline extension keeps its separately labelled original mask.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT=Path(__file__).resolve().parent.parent
NAMES={'airplane':'airplane','headphones':'headphones','airpods':'airpods',
       'wifi_warning':'wifi.exclamationmark','bolt':'bolt.fill'}

def transformed(commands,matrix):
    a,b,c,d,e,f=matrix
    return [[row[0],*[v for x,y in zip(row[1::2],row[2::2])
                     for v in (a*x+c*y+e,b*x+d*y+f)]] for row in commands]

def polygons(commands):
    result=[];current=[]
    for op,*v in commands:
        if op=='M':current=[tuple(v)]
        elif op=='L':current.append(tuple(v))
        elif op in ('Q','C'):
            x,y=current[-1]
            for j in range(1,65):
                t=j/64;u=1-t
                if op=='Q':p=(u*u*x+2*u*t*v[0]+t*t*v[2],u*u*y+2*u*t*v[1]+t*t*v[3])
                else:p=(u**3*x+3*u*u*t*v[0]+3*u*t*t*v[2]+t**3*v[4],u**3*y+3*u*u*t*v[1]+3*u*t*t*v[3]+t**3*v[5])
                current.append(p)
        elif op=='Z':result.append(current);current=[]
        else:raise ValueError(op)
    assert not current
    return result

def optical_anchor(layers,width,height):
    # Offline A14 optical adaptation, not SwiftUI's font measurement.
    size=1024;combined=Image.new('L',(size,size))
    for layer in layers:
        contours=polygons(layer['commands'])
        def area(p):return sum(x*v-u*y for (x,y),(u,v) in zip(p,p[1:]+p[:1]))
        contours.sort(key=lambda p:abs(area(p)),reverse=True)
        sign=1 if area(contours[0])>0 else -1
        mask=Image.new('L',(size,size));draw=ImageDraw.Draw(mask)
        for p in contours:draw.polygon([(x/width*size,y/height*size) for x,y in p],fill=1 if area(p)*sign>0 else 0)
        combined=ImageChops.lighter(combined,mask)
    total=cx=cy=0
    for i,value in enumerate(combined.tobytes()):
        if value:total+=1;cx+=i%size+.5;cy+=i//size+.5
    assert total
    return cx/total/size,cy/total/size

def offline_mask():
    p=ROOT/'app/src/test/resources/duo-native/duo_ios_offline.png'
    im=Image.open(p).getchannel('A');w,h=im.size
    def alpha(x,y):return im.getpixel((x,y)) if 0<=x<w and 0<=y<h else 0
    edges={};points={}
    def point(a,b):
        edge=tuple(sorted((a,b)))
        if edge not in points:
            av,bv=alpha(*a),alpha(*b);t=(127.5-av)/(bv-av)
            points[edge]=(a[0]+.5+(b[0]-a[0])*t,a[1]+.5+(b[1]-a[1])*t)
        return edge
    def connect(a,b):edges.setdefault(a,[]).append(b);edges.setdefault(b,[]).append(a)
    for y in range(-1,h):
        for x in range(-1,w):
            c=[(x,y),(x+1,y),(x+1,y+1),(x,y+1)];v=[alpha(*a) for a in c]
            cut=[i for i in range(4) if (v[i]>=127.5)!=(v[(i+1)%4]>=127.5)]
            if len(cut)==2:connect(*(point(c[i],c[(i+1)%4]) for i in cut))
            elif len(cut)==4:
                pairs=((0,1),(2,3)) if ((sum(v)>=510)==(v[0]>=127.5)) else ((3,0),(1,2))
                for a,b in pairs:connect(point(c[a],c[(a+1)%4]),point(c[b],c[(b+1)%4]))
    commands=[]
    while edges:
        start=next(iter(edges));current=start;previous=None;first=True
        while True:
            commands.append(['M' if first else 'L',*points[current]]);first=False
            links=edges.pop(current);nxt=next((p for p in links if p!=previous),start)
            previous,current=current,nxt
            if current==start:break
        commands.append(['Z'])
    total=cx=cy=0
    for y in range(h):
        for x in range(w):
            a=im.getpixel((x,y));total+=a;cx+=(x+.5)*a;cy+=(y+.5)*a
    return w,h,cx/total/w,cy/total/h,commands,hashlib.sha256(p.read_bytes()).hexdigest()

def kotlin_commands(commands):
    names={'M':'moveTo','L':'lineTo','Q':'quadTo','C':'cubicTo','Z':'close'}
    return '\n'.join('            '+names[r[0]]+'('+','.join(f'{x:.7f}'.rstrip('0').rstrip('.')+'f' for x in r[1:])+')' for r in commands)

def generate(source):
    data=json.loads(source.read_text(encoding='utf8'));assert data['build']=='24B91020'
    code='''package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Path

/** 24B91020 original vector commands. CoreUI raw master 4 is explicit;
 * UIFont interpolation remains unresolved. Optical anchors are offline A14
 * adaptation. No image, SVG, JSON or path-string parsing at runtime. */
internal object DuoIosSymbols {
    class Glyph(val width: Float,val height: Float,val centerX: Float,val centerY: Float,val paths: Array<Path>)
'''
    fixtures=[]
    for name,native in NAMES.items():
        g=next(g for g in data['glyphs'] if g['logicalName']==native)
        variant=next(v for v in g['variants'] if v['attributes']['renditionAttributeType(26)']==4)
        x,y,w,h=variant['viewBox'];assert x==y==0
        layers=[]
        for layer in variant['layers']:
            opacity=float(layer['presentation'].get('opacity',1))
            if opacity==0:continue
            assert opacity==1 and layer['fillRule']=='nonzero'
            layers.append({'commands':transformed(layer['commands'],layer['transform'])})
        cx,cy=optical_anchor(layers,w,h)
        code+=f'    // {variant["id"]}\n    val {name} by lazy(LazyThreadSafetyMode.NONE) {{\n'
        for i,layer in enumerate(layers):
            code+=f'        val layer{i}=Path().apply {{\n'+kotlin_commands(layer['commands'])+'\n        }\n'
        code+=f'        Glyph({w}f,{h}f,{cx:.8f}f,{cy:.8f}f,arrayOf('+','.join('layer'+str(i) for i in range(len(layers)))+'))\n    }\n'
        fixtures.append({'name':name,'source':variant['id'],'viewBox':[x,y,w,h],
                         'layers':[{'d':' '.join(r[0]+' '.join(str(v) for v in r[1:]) for r in l['commands']),
                                    'transform':l['transform'],'opacity':float(l['presentation'].get('opacity',1))}
                                   for l in variant['layers']]})
    w,h,cx,cy,commands,digest=offline_mask()
    code+=f'    // Android offline extension; mask SHA-256 {digest}.\n    val offline by lazy(LazyThreadSafetyMode.NONE) {{\n        val path=Path().apply {{\n            fillType=Path.FillType.EVEN_ODD\n'+kotlin_commands(commands)+f'\n        }}\n        Glyph({w}f,{h}f,{cx:.8f}f,{cy:.8f}f,arrayOf(path))\n    }}\n}}\n'
    return code,json.dumps(fixtures,ensure_ascii=False,indent=2)+'\n'

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--native-glyphs',type=Path,required=True);p.add_argument('--check',action='store_true');args=p.parse_args()
    code,fixtures=generate(args.native_glyphs)
    outputs={ROOT/'app/src/main/java/tv/withaibuild/customiuizer/mods/duostatusbar/DuoIosSymbols.kt':code,
             ROOT/'app/src/test/resources/duo-native/vector-masters.json':fixtures}
    for path,content in outputs.items():
        if args.check:assert path.read_text(encoding='utf8')==content,path
        else:path.write_text(content,encoding='utf8',newline='\n')
