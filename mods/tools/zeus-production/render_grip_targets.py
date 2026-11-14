"""Original texture/geometry diagnostic side view with source contact guides."""
from pathlib import Path
import json,numpy as np
from numba import njit
from PIL import Image,ImageDraw,ImageFont
W=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text('utf8'))
body=read(W/'evidence/source_body_native.json');mesh=read(W/'audit_baseline/assets/ssjj_zeus/source_mesh.json')
points=[];uv=[];faces=[];offset=0
for b,m in zip(body,mesh['parts']):
    assert b['bone']==m['bone'];p=np.array(b['native_positions']);points.append(p);uv.append(np.array(m['uv']));faces.append(np.array(m['triangles'])+offset);offset+=len(p)
p=np.vstack(points);uv=np.vstack(uv);faces=np.vstack(faces)
tex=np.asarray(Image.open(W/'audit_baseline/assets/ssjj_zeus/textures/gun/uv/hand_of_zeuss2.png').convert('RGB'))
width,height=1800,760;scale=1600.;origin=np.array([900.,320.])
@njit
def render(p,uv,faces,tex,w,h,scale):
    out=np.full((h,w,3),235,np.uint8);depth=np.full((h,w),-1e9)
    for tri in faces:
        vv=p[tri];xy=np.empty((3,2));xy[:,0]=900+vv[:,2]*scale;xy[:,1]=320-vv[:,1]*scale
        uvv=uv[tri];x0=max(0,int(xy[:,0].min()));x1=min(w-1,int(xy[:,0].max()+1));y0=max(0,int(xy[:,1].min()));y1=min(h-1,int(xy[:,1].max()+1))
        den=(xy[1,1]-xy[2,1])*(xy[0,0]-xy[2,0])+(xy[2,0]-xy[1,0])*(xy[0,1]-xy[2,1])
        if abs(den)<1e-9:continue
        for y in range(y0,y1+1):
            for x in range(x0,x1+1):
                a=((xy[1,1]-xy[2,1])*(x+.5-xy[2,0])+(xy[2,0]-xy[1,0])*(y+.5-xy[2,1]))/den
                b=((xy[2,1]-xy[0,1])*(x+.5-xy[2,0])+(xy[0,0]-xy[2,0])*(y+.5-xy[2,1]))/den;c=1-a-b
                if min(a,b,c)<-1e-6:continue
                z=a*vv[0,0]+b*vv[1,0]+c*vv[2,0]
                if z<=depth[y,x]:continue
                u=a*uvv[0]+b*uvv[1]+c*uvv[2];tx=int(u[0]*(tex.shape[1]-1))%tex.shape[1];ty=int(u[1]*(tex.shape[0]-1))%tex.shape[0]
                out[y,x]=tex[ty,tx];depth[y,x]=z
    return out
im=Image.fromarray(render(p,uv,faces,tex,width,height,scale));d=ImageDraw.Draw(im);f=ImageFont.truetype('source-assets/external/arial.ttf',23);small=ImageFont.truetype('source-assets/external/arial.ttf',18)
diag=read(W/'evidence/source_grip_diagnostics.json');sur=read(W/'evidence/grip_surface_measurements.json')
def xy(v):return (int(900+v[2]*scale),int(320-v[1]*scale))
def arrow(point,text,pos,color):
    q=xy(point);d.line((q,pos),fill=color,width=3);d.ellipse((q[0]-5,q[1]-5,q[0]+5,q[1]+5),fill=color);d.text(pos,text,font=small,fill=color)
d.text((24,16),'Zeus original textured geometry | +X side view | native rootless coordinates',font=f,fill='black')
arrow(diag['source_palm_mesh_bounds_native']['R']['mean'],'R source palm mean: Z .258 / Y .022',(1330,50),'red')
arrow(sur['current_4pixel_palm_volume']['center'],'R current 4px palm: Z .309 / Y .004',(1330,98),(145,0,0))
arrow(sur['queries']['source_left_palm_mean']['point'],'L fore-end underside contact',(380,565),'blue')
arrow([.04,-.040,.170],'Black rear grip target: Z .17 / Y -.04',(1100,620),(10,110,30))
# Target band, source grip below the receiver. Keeps the solve away from stock tail.
tl=xy([0,.005,.12]);br=xy([0,-.085,.195]);d.rectangle((tl[0],tl[1],br[0],br[1]),outline=(10,150,30),width=3)
arrow([0,.035,.455],'Stock tail: do not target',(1540,500),(100,50,150))
d.text((24,700),'Green band guides a finite-volume solve; it is not a fitted palm center or a no-clipping certificate.',font=small,fill='black')
im.save(W/'previews/grip_target_surface_annotation.png');print(W/'previews/grip_target_surface_annotation.png')
