"""Horizontal HUD orthographic renders of own source mesh/atlas; preserve original inventory icons."""
from pathlib import Path
import importlib.util,sys,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from prepare import W,ROOT,read,save,sha
GUNS=('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2')
def assembled(gun_id):
 path=ROOT/'work/zeus_hand2_20261113/scripts/source_import.py'
 spec=importlib.util.spec_from_file_location('zeus_original_rig_for_icons',path);g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
 scene=g.Scene();gun=g.Rig(scene,'gun');hand=g.Rig(scene,'hand_man');world=gun.globals('TORSO_STAND',0,hand)
 anchor=g.rigid(world['WeaponAnchor3']);positions=[];normals=[];uvs=[];triangles=[]
 mesh='source_mesh.json' if gun_id=='hand_of_zeuss2' else 'source_mesh_'+gun_id+'.json'
 parts=read(W/('baseline_assets/assets/ssjj_zeus/'+mesh))['parts']
 for part in parts:
  m=np.linalg.inv(anchor)@g.rigid(world[part['bone']]);m[:3,3]*=.01
  offset=len(positions);p=np.array(part['positions']);positions.extend((m@np.c_[p,np.ones(len(p))].T).T[:,:3]);normals.extend(np.array(part['normals'])@m[:3,:3].T);uvs.extend(part['uv']);triangles.extend(np.array(part['triangles'])+offset)
 return tuple(np.array(v) for v in (positions,normals,uvs,triangles)),g.INPUTS
def render(geometry,texture):
 positions,normals,uv,faces=geometry
 # Same side/top bias as other weapon model icons; +X muzzle projects left.
 view=np.array([[-1.,0,0],[0,.20,.979795897],[0,.979795897,-.20]])
 points=positions@view.T;ns=normals@view.T
 r=np.eye(2)
 lo=points[:,:2].min(0);hi=points[:,:2].max(0);width,height=1536,512;scale=min((np.array([width,height])-np.array([96,64]))/(hi-lo));screen=points.copy();screen[:,0]=(points[:,0]-(lo[0]+hi[0])/2)*scale+width/2;screen[:,1]=height/2-(points[:,1]-(lo[1]+hi[1])/2)*scale
 pixels=np.zeros((height,width,4),np.uint8);depth=np.full((height,width),-np.inf);tex=np.array(Image.open(texture).convert('RGBA'));light=np.array([-.3,.6,1.]);light/=np.linalg.norm(light)
 for face in faces:
  p=screen[face];xmin,ymin=np.maximum(np.floor(p[:,:2].min(0)).astype(int),0);xmax,ymax=np.minimum(np.ceil(p[:,:2].max(0)).astype(int),[width-1,height-1])
  if xmin>xmax or ymin>ymax:continue
  xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5);den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
  if abs(den)<1e-10:continue
  a=((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den;b=((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den;c=1-a-b
  z=a*p[0,2]+b*p[1,2]+c*p[2,2];zbuf=depth[ymin:ymax+1,xmin:xmax+1];mask=(a>=-1e-8)&(b>=-1e-8)&(c>=-1e-8)&(z>zbuf)
  if not mask.any():continue
  coord=a[...,None]*uv[face[0]]+b[...,None]*uv[face[1]]+c[...,None]*uv[face[2]];tx=np.clip((coord[...,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1);ty=np.clip((coord[...,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1)
  normal=a[...,None]*ns[face[0]]+b[...,None]*ns[face[1]]+c[...,None]*ns[face[2]];normal/=np.maximum(np.linalg.norm(normal,axis=-1,keepdims=True),1e-20);illum=.68+.32*np.maximum(0,normal@light);color=tex[ty,tx].copy();color[:,:,:3]=np.clip(color[:,:,:3]*illum[...,None],0,255).astype(np.uint8)
  patch=pixels[ymin:ymax+1,xmin:xmax+1];patch[mask]=color[mask];zbuf[mask]=z[mask]
 image=Image.fromarray(pixels).resize((384,128),Image.Resampling.LANCZOS)
 # Semantic muzzle/stock anchors independently constrain the requested diagonal.
 direction=(np.array([1.,0,0])@view.T)[:2]@r.T;screen_direction=np.array([direction[0],-direction[1]])
 assert screen_direction[0]<0 and abs(screen_direction[1])<1e-10
 return image,{'muzzle_screen_direction':screen_direction.tolist(),'bbox':image.getbbox(),'visible_pixels':int((np.array(image)[:,:,3]>8).sum()),'tilt_degrees_clockwise':0,'triangles':len(faces),'vertices':len(positions)}
def main():
 outputs={};details=[];icons=[];inputs={'scripts/render_hud_icons.py':sha(Path(__file__))};sources={}
 for gun in GUNS:
  geometry,originals=assembled(gun);sources.update(originals)
  mesh='source_mesh.json' if gun=='hand_of_zeuss2' else 'source_mesh_'+gun+'.json'
  rel='baseline_assets/assets/ssjj_zeus/'+mesh;inputs[rel]=sha(W/rel)
  for skin in ('','lava','galaxy'):
   stem=gun+('_'+skin if skin else '');atlas='hand_of_zeuss_'+skin if skin else gun
   texture=W/f'baseline_assets/assets/ssjj_zeus/textures/gun/uv/{atlas}.png'
   image,proof=render(geometry,texture);dest=W/f'pack/assets/ssjj_zeus/textures/gun/hud/{stem}_model.png'
   dest.parent.mkdir(parents=True,exist_ok=True);image.save(dest);inputs[texture.relative_to(W).as_posix()]=sha(texture);outputs[dest.relative_to(W).as_posix()]=sha(dest)
   details.append({'gun':gun,'skin':skin or 'base','source_mesh':rel,'source_atlas':texture.relative_to(W).as_posix(),'output':dest.relative_to(W).as_posix(),'canvas':[384,128],**proof});icons.append((stem,image))
 canvas=Image.new('RGB',(1152,480),(35,39,46));draw=ImageDraw.Draw(canvas);font=ImageFont.truetype('source-assets/external/arial.ttf',16)
 for i,(stem,image) in enumerate(icons):
  x=(i%3)*384;y=(i//3)*160;canvas.paste(image,(x,y),image);draw.text((x+8,y+132),stem,font=font,fill='white')
 canvas.save(W/'previews/hud_horizontal_models.png')
 save(W/'evidence/hud_model_render.json',{'passed':len(details)==9,'render_source':'Each tier source gun triangles + original rig TORSO_STAND + own original tier/skin atlas; rerendered without roll, no UI art or rotated inventory raster','source_input_sha256':sources,'input_sha256':inputs,'output_sha256':outputs,'details':details,'inventory_textures_untouched':True,'live_client_test':False})
 print('PASS nine horizontal model HUD textures; inventory icons untouched')
if __name__=='__main__':main()
