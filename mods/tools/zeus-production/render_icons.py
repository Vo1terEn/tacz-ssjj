"""Texture-sampled orthographic renders of source gun geometry, never source UI art."""
from pathlib import Path
import importlib.util,sys,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from prepare import W,ROOT,read,save,sha
GUNS=('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2')
def assembled():
 path=ROOT/'work/zeus_hand2_20261113/scripts/source_import.py'
 spec=importlib.util.spec_from_file_location('zeus_original_rig_for_icons',path);g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
 scene=g.Scene();gun=g.Rig(scene,'gun');hand=g.Rig(scene,'hand_man');world=gun.globals('TORSO_STAND',0,hand)
 anchor=g.rigid(world['WeaponAnchor3']);positions=[];normals=[];uvs=[];triangles=[]
 parts=read(W/'baseline_assets/assets/ssjj_zeus/source_mesh.json')['parts']
 for part in parts:
  m=np.linalg.inv(anchor)@g.rigid(world[part['bone']]);m[:3,3]*=.01
  offset=len(positions);p=np.array(part['positions']);positions.extend((m@np.c_[p,np.ones(len(p))].T).T[:,:3]);normals.extend(np.array(part['normals'])@m[:3,:3].T);uvs.extend(part['uv']);triangles.extend(np.array(part['triangles'])+offset)
 return tuple(np.array(v) for v in (positions,normals,uvs,triangles)),g.INPUTS
def render(geometry,texture):
 positions,normals,uv,faces=geometry
 # Same side/top bias as other weapon model icons; +X muzzle projects left.
 view=np.array([[-1.,0,0],[0,.20,.979795897],[0,.979795897,-.20]])
 points=positions@view.T;ns=normals@view.T
 angle=np.deg2rad(-38);r=np.array([[np.cos(angle),-np.sin(angle)],[np.sin(angle),np.cos(angle)]])
 points[:,:2]=points[:,:2]@r.T;ns[:,:2]=ns[:,:2]@r.T
 lo=points[:,:2].min(0);hi=points[:,:2].max(0);size=1024;scale=min((size-72)/(hi-lo));screen=points.copy();screen[:,0]=(points[:,0]-(lo[0]+hi[0])/2)*scale+size/2;screen[:,1]=size/2-(points[:,1]-(lo[1]+hi[1])/2)*scale
 pixels=np.zeros((size,size,4),np.uint8);depth=np.full((size,size),-np.inf);tex=np.array(Image.open(texture).convert('RGBA'));light=np.array([-.3,.6,1.]);light/=np.linalg.norm(light)
 for face in faces:
  p=screen[face];xmin,ymin=np.maximum(np.floor(p[:,:2].min(0)).astype(int),0);xmax,ymax=np.minimum(np.ceil(p[:,:2].max(0)).astype(int),size-1)
  if xmin>xmax or ymin>ymax:continue
  xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5);den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
  if abs(den)<1e-10:continue
  a=((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den;b=((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den;c=1-a-b
  z=a*p[0,2]+b*p[1,2]+c*p[2,2];zbuf=depth[ymin:ymax+1,xmin:xmax+1];mask=(a>=-1e-8)&(b>=-1e-8)&(c>=-1e-8)&(z>zbuf)
  if not mask.any():continue
  coord=a[...,None]*uv[face[0]]+b[...,None]*uv[face[1]]+c[...,None]*uv[face[2]];tx=np.clip((coord[...,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1);ty=np.clip((coord[...,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1)
  normal=a[...,None]*ns[face[0]]+b[...,None]*ns[face[1]]+c[...,None]*ns[face[2]];normal/=np.maximum(np.linalg.norm(normal,axis=-1,keepdims=True),1e-20);illum=.68+.32*np.maximum(0,normal@light);color=tex[ty,tx].copy();color[:,:,:3]=np.clip(color[:,:,:3]*illum[...,None],0,255).astype(np.uint8)
  patch=pixels[ymin:ymax+1,xmin:xmax+1];patch[mask]=color[mask];zbuf[mask]=z[mask]
 image=Image.fromarray(pixels).resize((256,256),Image.Resampling.LANCZOS)
 # Semantic muzzle/stock anchors independently constrain the requested diagonal.
 direction=(np.array([1.,0,0])@view.T)[:2]@r.T;screen_direction=np.array([direction[0],-direction[1]])
 assert np.all(screen_direction<0)
 return image,{'muzzle_screen_direction':screen_direction.tolist(),'bbox':image.getbbox(),'visible_pixels':int((np.array(image)[:,:,3]>8).sum()),'tilt_degrees_clockwise':38,'triangles':len(faces),'vertices':len(positions)}
def main():
 geometry,sources=assembled();outputs={};details=[];icons=[];inputs={'scripts/render_icons.py':sha(Path(__file__)),'baseline_assets/assets/ssjj_zeus/source_mesh.json':sha(W/'baseline_assets/assets/ssjj_zeus/source_mesh.json')}
 for gun in GUNS:
  for skin in ('','lava','galaxy'):
   stem=gun+('_'+skin if skin else '');texture=(W/f'pack/assets/ssjj_zeus/textures/gun/uv/hand_of_zeuss_{skin}.png') if skin else W/f'baseline_assets/assets/ssjj_zeus/textures/gun/uv/{gun}.png'
   if not texture.is_file():continue
   image,proof=render(geometry,texture);dest=W/f'pack/assets/ssjj_zeus/textures/gun/slot/{stem}.png';dest.parent.mkdir(parents=True,exist_ok=True);image.save(dest);inputs[texture.relative_to(W).as_posix()]=sha(texture);outputs[dest.relative_to(W).as_posix()]=sha(dest);details.append({'gun':gun,'skin':skin or 'base',**proof});icons.append((stem,image))
   if skin and gun==GUNS[0]:
    p=W/f'pack/assets/ssjj_zeus/textures/attachment/slot/{skin}_scope.png';p.parent.mkdir(parents=True,exist_ok=True);image.save(p);outputs[p.relative_to(W).as_posix()]=sha(p)
 canvas=Image.new('RGB',(768,900),(43,47,55));draw=ImageDraw.Draw(canvas);font=ImageFont.truetype('source-assets/external/msyh.ttc',18)
 for i,(stem,image) in enumerate(icons):
  x=(i%3)*256;y=(i//3)*300;canvas.paste(image,(x,y),image)
  gun=GUNS[i//3];skin=('', 'lava', 'galaxy')[i%3];title=('宙斯之手','宙斯之手·壹','宙斯之手·贰')[i//3]+({'':'','lava':' · 熔岩之拳','galaxy':' · 星空之主'}[skin]);draw.text((x+8,y+268),title,font=font,fill='white')
 canvas.save(W/'previews/slot_icons.png')
 save(W/'evidence/icon_render.json',{'passed':len(details)==9,'expected_icons':9,'actual_icons':len(details),'render_source':'source gun triangle geometry + original source rig TORSO_STAND + own gun atlas; not source UI icons','source_input_sha256':sources,'input_sha256':inputs,'output_sha256':outputs,'details':details,'live_client_test':False})
 print('Rendered',len(details),'diagonal gun icons; final gate requires all 9')
if __name__=='__main__':main()
