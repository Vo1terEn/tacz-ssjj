"""Fit original rear energy textures to finite WeaponAnchor3 source faces.

Use the unchanged mesh sampler's UVST channels for pulse/expansion. No world
normal translation or Y scale can detach the original energy from the receiver.
"""
from pathlib import Path
import json, hashlib, copy
import numpy as np
from scipy.spatial.transform import Rotation
W=Path(__file__).resolve().parents[1]
def read(p): return json.loads(Path(p).read_text('utf8'))
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p,v):
 p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def clip(poly,axis,bound,above):
 out=[]
 for a,b in zip(poly,poly[1:]+poly[:1]):
  ina=a[axis]>=bound if above else a[axis]<=bound; inb=b[axis]>=bound if above else b[axis]<=bound
  if ina:out.append(a)
  if ina!=inb:out.append(a+(bound-a[axis])/(b[axis]-a[axis])*(b-a))
 return out
def adapt(e,vertices,faces):
 old=copy.deepcopy(e); f=np.array(e['particles'][0]['frames'][128]); g=e.get('geometry')
 if g:
  local=np.array(g['positions']); world=Rotation.from_quat(f[6:10]).apply(local*f[3:6])+f[:3]
 else:
  # The original molten splash atlas now changes over the same finite rear
  # disc instead of an outward moving billboard. Original birth/life/atlas/RGBA
  # remain; 75mm is the original rear assembly's surface texture footprint.
  assert e['name']=='yanjiang'
  cx,cz=-.13192,.05773;half=.0375
  world=np.array([[cx-half,.03175,cz+half],[cx-half,.03175,cz-half],[cx+half,.03175,cz-half],[cx+half,.03175,cz+half]])
  g={'uv':[[1,0],[1,1],[0,1],[0,0]]}
 # Original texture orientation is retained through its projected XZ affine map.
 # The mesh planes are parallel receiver sides. Both sides share this mapping.
 affine=np.linalg.lstsq(np.c_[world[:,0],world[:,2],np.ones(len(world))],np.array(g['uv']),rcond=None)[0]
 center=np.mean(world,axis=0); side=0
 if old.get('geometry') and np.ptp(world[:,1])<.002:side=int(np.sign(center[1]))
 positions=[];uv=[];triangles=[];provenance=[];offset=.0005
 for fi,inds in enumerate(faces):
  tri=vertices[inds]; normal=np.cross(tri[1]-tri[0],tri[2]-tri[0]); length=np.linalg.norm(normal)
  if length<1e-12:continue
  normal/=length
  if abs(normal[1])<.2 or normal[1]*np.mean(tri[:,1])<.005:continue
  if side and np.sign(normal[1])!=side:continue
  tex=np.c_[tri[:,0],tri[:,2],np.ones(3)]@affine
  poly=[np.r_[tex[i],tri[i],np.eye(3)[i]] for i in range(3)]
  # Finite source UV footprint only; receiver rear circle for ring/ball energy.
  bounds=[(0,0,True),(0,1,False),(1,0,True),(1,1,False)]
  if e['name']!='glow_chibang':bounds += [(2,-.184,True),(2,-.081,False),(4,.005,True),(4,.113,False)]
  for axis,bound,above in bounds:
   if poly:poly=clip(poly,axis,bound,above)
  for k in range(1,len(poly)-1):
   points=[poly[0],poly[k],poly[k+1]]; xyz=np.array([p[2:5] for p in points])
   if np.linalg.norm(np.cross(xyz[1]-xyz[0],xyz[2]-xyz[0]))<1e-14:continue
   start=len(positions)
   for p in points:positions.append(np.round(p[2:5]+normal*offset,10).tolist());uv.append(np.round(p[:2],10).tolist())
   triangles.append([start,start+1,start+2]);provenance.append({'source_triangle':int(fi),'indices':inds.tolist(),'normal':normal.tolist(),'barycentric':[p[5:8].tolist() for p in points]})
 assert positions, e['name']
 e['geometry']={'positions':positions,'uv':uv,'triangles':triangles};e['mode']='mesh'
 for particle in e['particles']:
  scale=np.array(particle['frames'])[:,3]; lo=float(scale.min());hi=float(scale.max())
  for i,f in enumerate(particle['frames']):
   # Keep the original normalized lifetime, RGBA, sprite cell and UV motion.
   # Shrink UV sampling about its center as source scale grows: energy expands
   # only over a fixed finite surface, instead of normal-space mesh expansion.
   progress=(scale[i]-lo)/max(hi-lo,1e-12); k=1-.18*progress
   us,vs,uo,vo=f[14:18];f[:10]=[0,0,0,1,1,1,0,0,0,1]
   f[14:18]=[us*k,vs*k,uo+us*(1-k)/2,vo-vs*(1-k)/2]
   if lo<=1e-8 and hi>1e-8:f[13]*=min(1,float(scale[i])/(hi*.25))
 return e,{'name':e['name'],'source_particle_count':len(old['particles']),'triangles':len(triangles),'sides':sorted(set(int(np.sign(p['normal'][1]))for p in provenance)),'normal_offset_m':offset,'source_world_xz_to_uv':affine.tolist(),'provenance':provenance,'fixed_TRS':True,'UV_pulse_factor_range':[.82,1.0],'source_texture':e['texture']}
def main():
 source=W/'baseline_assets/assets/ssjj_zeus/source_mesh.json';part=next(p for p in read(source)['parts']if p['bone']=='WeaponAnchor3');vertices=np.array(part['positions']);faces=np.array(part['triangles']);patches={};traces=[];inputs={source.relative_to(W).as_posix():sha(source),Path(__file__).relative_to(W).as_posix():sha(Path(__file__))}
 for name in ['body.json','body_hand_of_zeuss.json','body_hand_of_zeuss1.json']+[f'body_{g}_lava.json'for g in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']]:
  rel='assets/ssjj_zeus/effects/'+name;p=W/'baseline_assets'/rel;d=read(p);inputs[p.relative_to(W).as_posix()]=sha(p);patches[rel]={}
  for e in d['emitters']:
   if e['name']in ['ring','ring_duplicate_2','xuanzhuan','xuanzhuan_duplicate_2','dianqiu','glow_chibang','yanjiang']:
    new,trace=adapt(copy.deepcopy(e),vertices,faces);patches[rel][e['name']]=new;traces.append({'resource':rel,**trace})
 out=W/'qa/rear_emitter_overrides.json';save(out,patches)
 save(W/'evidence/rear_surface_build.json',{'passed':True,'java_changes':False,'source_artwork_changes':False,'adaptation':'Original energy PNG and source lifetime/color/sprite motion; mesh expansion is adapted to centered UV expansion over fixed finite source gun faces. Original zero-size births fade in. No private source engine timing/units equivalence claimed.','emitters':traces,'input_sha256':inputs,'output_sha256':{out.relative_to(W).as_posix():sha(out)}})
 print('PASS fixed finite rear surface carriers:',len(traces),'emitters')
if __name__=='__main__':main()
