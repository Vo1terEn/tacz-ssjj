"""Import Zeus II directly from its original Unity meshes, rig and raw ANM.
Only conversion mechanisms are shared with earlier weapons; no earlier pose,
mesh, texture, camera fit or animation is used. Does not install or package.
"""
from __future__ import annotations
import csv, hashlib, json, struct, warnings, sys, importlib.util, io
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from scipy.spatial.transform import Rotation, Slerp
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler
W=Path(__file__).resolve().parents[1]; P=W.parents[1]
RAW=P/'宙斯、聚能/宙斯之手'
SOURCE=RAW/'完整源素材/武器/宙斯之手•贰__hand_of_zeuss2'
RIG=SOURCE/'hand_of_zeuss2'
AS=W/'pack/assets/ssjj_zeus'; INPUTS={}; J=np.diag([-1.,1.,1.,1.])
Q=np.array([[0.,0,1],[0,1,0],[-1,0,0]])
def read(p):
 p=Path(p); b=p.read_bytes(); INPUTS[p.relative_to(P).as_posix()]=hashlib.sha256(b).hexdigest(); return b
def load(p):return json.loads(read(p).decode('utf-8-sig'))
def save(p,d):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def trs(t,q,s=(1,1,1)):
 if isinstance(t,dict):t=[t[k]for k in 'xyz']
 if isinstance(q,dict):q=[q[k]for k in 'xyzw']
 if isinstance(s,dict):s=[s[k]for k in 'xyz']
 m=np.eye(4);m[:3,:3]=Rotation.from_quat(q).as_matrix()@np.diag(s);m[:3,3]=t;return m
def rigid(m):
 r=m.copy();u,_,v=np.linalg.svd(r[:3,:3]);r[:3,:3]=u@v;return r
def unit(v):return v/max(np.linalg.norm(v),1e-20)
def frame(direction,hand):
 y=-unit(direction);x=unit(hand[:3,2]-y*np.dot(hand[:3,2],y));z=unit(np.cross(x,y));return np.column_stack((np.cross(y,z),y,z))

def parse_anm(p):
 b=read(p);o=0
 def n(fmt):
  nonlocal o
  r=struct.unpack_from('<'+fmt,b,o);o+=struct.calcsize('<'+fmt);return r[0]if len(r)==1 else r
 def st():
  nonlocal o
  sz=n('H');s=b[o:o+sz].decode('utf8');o+=sz;return s
 version=n('i');clips=[]
 for _ in range(n('i')):
  c={'name':st(),'blends':[]}
  for _ in range(n('i')):
   bl={'name':st(),'tracks':[]};nt=n('i');bl['duration']=n('f')
   for _ in range(nt):
    t={'bone':st(),'keyframes':[]}
    for _ in range(n('i')):
     f=n('8f');t['keyframes'].append({'time':f[0],'translation':list(f[1:4]),'rotation_xyzw':list(f[4:8])})
    bl['tracks'].append(t)
   c['blends'].append(bl)
  clips.append(c)
 assert o==len(b)
 return {'version':version,'bytes_consumed':o,'clips':clips}


class Scene:
 def __init__(self):
  ap=SOURCE/'hand_of_zeuss2$2/assembly.json';self.doc=load(ap);self.ids={o['path_id']:o for o in self.doc['objects']};self.transforms={i:o['fields']for i,o in self.ids.items()if o['type']=='Transform'}
  self.env=UnityPy.load(read(next((RAW/'原始资源包/weapon').glob('hand_of_zeuss2$2_*'))));self.mesh_objects={o.path_id:o for o in self.env.objects if o.type.name=='Mesh'};self.mesh_cache={}
 def name(self,i):return self.ids[self.transforms[i]['m_GameObject']['m_PathID']]['name']
 def path(self,i):
  t=self.transforms[i];return (self.path(t['m_Father']['m_PathID'])+'/' if t['m_Father']['m_PathID'] else '')+self.name(i)
 def renderer_root(self,r):
  go=self.ids[r['m_GameObject']['m_PathID']]['fields'];ti=next(c['component']['m_PathID']for c in go['m_Component']if c['component']['m_PathID']in self.transforms);return self.path(ti).split('/')[0]
 def mesh_for_root(self,root):
  matches=[o['fields']for o in self.doc['objects']if o['type']=='SkinnedMeshRenderer'and self.renderer_root(o['fields'])==root and self.mesh_objects[o['fields']['m_Mesh']['m_PathID']].read().m_Name not in ('Plane001','Plane002','Plane03')];assert len(matches)==1,(root,matches);r=matches[0];return r['m_Mesh']['m_PathID'],r
 def mesh(self,i):
  if i not in self.mesh_cache:
   obj=self.mesh_objects[i].read();h=MeshHandler(obj);h.process();j=np.asarray(h.m_BoneIndices,dtype=int);w=np.asarray(h.m_BoneWeights,dtype=float)if h.m_BoneWeights else np.ones_like(j,dtype=float)
   assert w.shape==j.shape and np.max(abs(w.sum(1)-1))<2e-6
   self.mesh_cache[i]={'name':obj.m_Name,'p':np.asarray(h.m_Vertices,float),'n':np.asarray(h.m_Normals,float),'uv':np.asarray(h.m_UV0,float),'j':j,'w':w,'tri':np.asarray(h.get_triangles()[0],int),'bind':np.array([[[getattr(v,f'e{a}{b}')for b in range(4)]for a in range(4)]for v in obj.m_BindPose])}
  return self.mesh_cache[i]

class Rig:
 def __init__(self,scene,kind):
  self.kind=kind;self.scene=scene;ids=[i for i in scene.transforms if scene.path(i).split('/')[0]==kind];self.nodes=ids;self.names={scene.name(i):i for i in ids};assert len(self.names)==len(ids)
  self.parents={i:scene.transforms[i]['m_Father']['m_PathID']for i in ids};self.defaults={i:J@trs(scene.transforms[i]['m_LocalPosition'],scene.transforms[i]['m_LocalRotation'],scene.transforms[i]['m_LocalScale'])@J for i in ids}
  self.raw=parse_anm(next((RIG/'EmbeddedData').glob(kind+'.anm__*.bytes')));self.clips={c['name']:c['blends'][0]for c in self.raw['clips']};self.tracks={}
  for c,a in self.clips.items():
   self.tracks[c]={}
   for t in a['tracks']:
    fs=t['keyframes'];times=np.array([f['time']for f in fs]);qs=np.array([f['rotation_xyzw']for f in fs]);qs/=np.linalg.norm(qs,axis=1)[:,None]
    assert len(times)>0 and np.all(np.diff(times)>0)
    self.tracks[c][t['bone']]=(times,np.array([f['translation']for f in fs]),qs,Slerp(times,Rotation.from_quat(qs))if len(times)>1 else None)
 def globals(self,c,time,other=None):
  local={i:m.copy()for i,m in self.defaults.items()}
  for tracks in (self.tracks[c],other.tracks[c]if other else {}):
   for name,(times,ps,qs,s)in tracks.items():
    if name not in self.names:continue
    t=float(np.clip(time,times[0],times[-1]));p=[np.interp(t,times,ps[:,i])for i in range(3)];local[self.names[name]]=trs(p,s(t).as_quat()if s else qs[0])
  cache={}
  def visit(i):
   if i not in cache:cache[i]=(visit(self.parents[i])if self.parents.get(i)in local else np.eye(4))@local[i]
   return cache[i]
  return {self.scene.name(i):visit(i)for i in local}

def import_mesh(scene,gun):
 mid,r=scene.mesh_for_root('gun');u=scene.mesh(mid);j=u['j'];w=u['w'];slots=j[np.arange(len(j)),np.argmax(w,axis=1)];rigid_vertices=np.count_nonzero(w>1e-7,axis=1)==1
 assert np.all(rigid_vertices),'Mixed weighted gun mesh needs actual skinning';tri=u['tri'][:,[0,2,1]];assert np.all(slots[tri]==slots[tri[:,:1]])
 mat=scene.ids[r['m_Materials'][0]['m_PathID']];props=mat['fields']['m_SavedProperties'];texid=dict(props['m_TexEnvs'])['_MainTex']['m_Texture']['m_PathID'];tp=next((RIG/'Textures').glob('*__'+str(texid)+'.png'));im=Image.open(io.BytesIO(read(tp))).convert('RGBA');out=AS/'textures/gun/uv/hand_of_zeuss2.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
 pos=u['p']@J[:3,:3];norm=u['n']@J[:3,:3];uv=u['uv']*[1,-1]+[0,1];ibm=J@u['bind']@J;parts=[];bindcheck=[]
 for slot,b in enumerate(r['m_Bones']):
  bone=scene.name(b['m_PathID']);faces=tri[slots[tri[:,0]]==slot]
  if not len(faces):continue
  sel=np.unique(faces);remap=np.full(len(pos),-1);remap[sel]=np.arange(len(sel));p=(ibm[slot]@np.c_[pos[sel],np.ones(len(sel))].T).T[:,:3]*.01;n=(np.linalg.inv(ibm[slot,:3,:3]).T@norm[sel].T).T;n/=np.linalg.norm(n,axis=1)[:,None]
  parts.append({'bone':bone,'positions':p.tolist(),'normals':n.tolist(),'uv':uv[sel].tolist(),'triangles':remap[faces].tolist()});bindcheck.append({'bone':bone,'vertices':len(sel),'triangles':len(faces),'inverse_bind_determinant':float(np.linalg.det(ibm[slot,:3,:3]))})
 save(AS/'source_mesh.json',{'parts':parts});checks=[{'mesh':u['name'],'vertices':len(pos),'triangles':len(tri),'mixed_weight_vertices':int(np.count_nonzero(~rigid_vertices)),'bone_parts':bindcheck,'uv_range':[uv.min(0).tolist(),uv.max(0).tolist()]}]
 trace=[{'mesh':u['name'],'mesh_path_id':mid,'bundle':next((RAW/'原始资源包/weapon').glob('hand_of_zeuss2$2_*')).relative_to(P).as_posix(),'material_name':mat['name'],'material_path_id':mat['path_id'],'properties':props,'texture':tp.relative_to(P).as_posix(),'selection':'Only first-person gun/zszs_p1. Third-person body and animated/disabled effect planes are excluded.'}]
 return parts,trace,checks,{tp:(0,0,im.width,im.height)}
def eulers(ms):
 with warnings.catch_warnings():warnings.simplefilter('ignore');raw=Rotation.from_matrix(np.asarray(ms)).as_euler('xyz',degrees=True)
 out=[raw[0]]
 for a in raw[1:]:
  cs=[b+360*np.round((out[-1]-b)/360)for b in(a,np.array([a[0]+180,180-a[1],a[2]+180]))];out.append(min(cs,key=lambda b:np.linalg.norm(b-out[-1])))
 return np.array(out)
def keyed(ts,vs):return {format(float(t),'.9g'):np.round(v,8).tolist()for t,v in zip(ts,vs)}


def render_previews(parts,texture,targets,base,eye):
 """CPU textured depth raster, independent of the game's lighting and view chain."""
 from numba import njit
 @njit
 def raster(vertices,uvs,faces,tex,width,height,focal,near):
  out=np.zeros((height,width,3),np.uint8);out[:,:,0]=27;out[:,:,1]=31;out[:,:,2]=39;depth=np.ones((height,width))*1e20
  for fi in range(len(faces)):
   ids=faces[fi];v=vertices[ids];uv=uvs[ids];z=-v[:,2]
   if np.min(z)<near:continue
   xy=np.empty((3,2));xy[:,0]=width/2+focal*v[:,0]/z;xy[:,1]=height/2-focal*v[:,1]/z
   x0=max(0,int(np.floor(np.min(xy[:,0]))));x1=min(width-1,int(np.ceil(np.max(xy[:,0]))));y0=max(0,int(np.floor(np.min(xy[:,1]))));y1=min(height-1,int(np.ceil(np.max(xy[:,1]))))
   den=(xy[1,1]-xy[2,1])*(xy[0,0]-xy[2,0])+(xy[2,0]-xy[1,0])*(xy[0,1]-xy[2,1])
   if abs(den)<1e-9:continue
   n=np.cross(v[1]-v[0],v[2]-v[0]);n/=max(1e-12,np.sqrt(np.sum(n*n)));light=.62+.38*abs(n[0]*.3+n[1]*.6+n[2]*.74)
   for y in range(y0,y1+1):
    for x in range(x0,x1+1):
     a=((xy[1,1]-xy[2,1])*(x+.5-xy[2,0])+(xy[2,0]-xy[1,0])*(y+.5-xy[2,1]))/den;b=((xy[2,1]-xy[0,1])*(x+.5-xy[2,0])+(xy[0,0]-xy[2,0])*(y+.5-xy[2,1]))/den;c=1-a-b
     if min(a,b,c)<-1e-6:continue
     iz=a/z[0]+b/z[1]+c/z[2];zz=1/iz
     if zz>=depth[y,x]:continue
     u=(a*uv[0,0]/z[0]+b*uv[1,0]/z[1]+c*uv[2,0]/z[2])/iz;vv=(a*uv[0,1]/z[0]+b*uv[1,1]/z[1]+c*uv[2,1]/z[2])/iz
     tx=int(u*(tex.shape[1]-1))%tex.shape[1];ty=int(vv*(tex.shape[0]-1))%tex.shape[0]
     for ch in range(3):out[y,x,ch]=min(255,int(tex[ty,tx,ch]*light))
     depth[y,x]=zz
  return out,int(np.count_nonzero(depth<1e19))
 texture=np.asarray(Image.open(texture).convert('RGBA'));width,height=640,360;focal=height/2/np.tan(np.deg2rad(60)/2);pre=W/'previews';pre.mkdir(exist_ok=True)
 def vertices(matrices):
  ps=[];uv=[];faces=[];offset=0
  for part in parts:
   p=np.asarray(part['positions']);m=np.asarray(matrices[part['bone']]);ps.append((np.c_[p,np.ones(len(p))]@m.T)[:,:3]);uv.append(np.asarray(part['uv']));faces.append(np.asarray(part['triangles'])+offset);offset+=len(p)
  return np.vstack(ps),np.vstack(uv),np.vstack(faces)
 bverts,uv,faces=vertices(base);center=(bverts.min(0)+bverts.max(0))/2;bb=(bverts.max(0)-bverts.min(0)).tolist();frames=[];counts=[]
 cases=[('idle','TORSO_STAND',0),('draw middle','TORSO_RAISE',.5),('fire peak','TORSO_ATTACK',.1),('reload 0.8s','TORSO_RELOAD',.8),('reload 1.6s','TORSO_RELOAD',1.6),('reload 2.4s','TORSO_RELOAD',2.4),('inspect 1s','TORSO_SHOWGUN',1),('walk middle','TORSO_WALK',.4)]
 for label,clip,t in cases:
  ms,_,_=targets(clip,t);vs,uv,faces=vertices(ms);pixels,count=raster(vs-eye,uv,faces,texture,width,height,focal,.01);im=Image.fromarray(pixels);d=ImageDraw.Draw(im);d.text((12,10),label+' | source camera / 60 deg vertical FOV',fill=(235,235,240));frames.append(im);counts.append({'pose':label,'source_clip':clip,'time':t,'visible_pixels':count})
 sheet=Image.new('RGB',(width*2,height*4))
 for i,im in enumerate(frames):sheet.paste(im,((i%2)*width,(i//2)*height))
 sheet.save(pre/'source_camera_contact_sheet.png')
 # Independent side view framed from this gun's source bounds.
 side=Rotation.from_euler('y',85,degrees=True).as_matrix();vs=(bverts-center)@side.T;vs[:,2]-=1.25;pixels,count=raster(vs,uv,faces,texture,width,height,focal,.01);im=Image.fromarray(pixels);ImageDraw.Draw(im).text((12,10),'Zeus II | source model side view | offline',fill=(235,235,240));im.save(pre/'source_model_side.png')
 save(W/'evidence/offline_preview.json',{'status':'SOURCE_MESH_SOFTWARE_RENDER_ONLY','vertical_fov_degrees':60,'body_bounds_blocks':bb,'camera_eye_native_blocks':eye.tolist(),'poses':counts,'side_visible_pixels':count,'artifacts':['previews/source_camera_contact_sheet.png','previews/source_model_side.png'],'boundary':'Original source mesh+texture+ANM only. No player skin or original Unity shader/particles, game light, native TaCZ view chain, or live client is rendered. Default FOV is an adaptation, not a video fit.'})
def main():
 scene=Scene();gun=Rig(scene,'gun');hand=Rig(scene,'hand_man')
 parts,trace,checks,atlas=import_mesh(scene,gun);body=sorted({p['bone']for p in parts});origin=gun.globals('TORSO_STAND',0,hand)['WeaponAnchor3'][:3,3]*.01
 def native(m):
  r=rigid(m);r[:3,:3]=Q@r[:3,:3];r[:3,3]=Q@(m[:3,3]*.01-origin);return r
 # Original human forearm cross-section; no older gun calibration.
 hm,hr=scene.mesh_for_root('hand_man'); hu=scene.mesh(hm); hpos=hu['p']@J[:3,:3]; hj=hu['j'];hw=hu['w'];hbind=J@hu['bind']@J
 hnames=[scene.name(x['m_PathID'])for x in hr['m_Bones']]
 widths=[]
 for side in('L','R'):
  slot=hnames.index(f'Bip01_{side}_Forearm');selected=np.any((hj==slot)&(hw>.5),axis=1);loc=(hbind[slot]@np.c_[hpos[selected],np.ones(np.count_nonzero(selected))].T).T[:,:3]*.01
  ext=np.ptp(loc,axis=0);widths.append(float(np.sort(ext)[1]))
 transverse=float(np.mean(widths)/.25);fixture=load(P/'work/parasite_video_match_20261104/evidence/native_hand_scale/neutral_player_arm_pose.json');max_contact=0.;references=[]
 def targets(clip,t):
  nonlocal max_contact
  g=gun.globals(clip,t,hand);h=hand.globals(clip,t,gun);ms={b:native(g[b])for b in body};ss={b:np.ones(3)for b in body};land={}
  ms['muzzle_flash']=native(g['weapon_particle_fire_effect']);ss['muzzle_flash']=np.ones(3)
  for side,label in(('L','left'),('R','right')):
   stem=f'Bip01_{side}_';elbow=h[stem+'Forearm'][:3,3]*.01;wrist=h[stem+'Hand'][:3,3]*.01;fingers=np.mean([h[stem+f'Finger{i}'][:3,3]for i in range(1,5)],axis=0)*.01;contact=(wrist+fingers)*.5;r=frame(contact-elbow,h[stem+'Hand']);scale=np.array([transverse,np.linalg.norm(contact-elbow)/.75,transverse]);cap=np.array(fixture['variants']['default'][f'after_setup_anim_{label}_cap']);m=np.eye(4);m[:3,:3]=Q@r;m[:3,3]=Q@(contact-r@(scale*cap)-origin);ms[label+'hand']=m;ss[label+'hand']=scale;desired=Q@(contact-origin);max_contact=max(max_contact,float(np.linalg.norm(m[:3,3]+m[:3,:3]@(scale*cap)-desired)));land[label]={'contact':desired.tolist(),'elbow':(Q@(elbow-origin)).tolist()}
  return ms,ss,land
 base,bscale,_=targets('TORSO_STAND',0);be={b:eulers([m[:3,:3]])[0]for b,m in base.items()};animations={}
 movement_names=('walk','run','aim_walk','aim_run','walk_forward','walk_backward','walk_sideway','walk_aiming','run_hold')
 clips={'static_idle':('TORSO_STAND',True),'draw':('TORSO_RAISE',False),'shoot':('TORSO_ATTACK',False),'reload_empty':('TORSO_RELOAD',False),'reload_tactical':('TORSO_RELOAD',False),'inspect':('TORSO_SHOWGUN',False),'inspect_empty':('TORSO_SHOWGUN',False),**{n:('TORSO_WALK',True)for n in movement_names}}
 movement_seams=[]
 for name,(source,loop)in clips.items():
  duration=max(gun.clips[source]['duration'],1/30 if name=='static_idle' else 0);times=sorted({float(np.float32(t))for t in np.r_[0,duration,np.arange(0,duration,1/60)]});times=[t for i,t in enumerate(times)if not i or t-times[i-1]>1e-6];values=[targets(source,t)for t in times];bones={}
  for b in base:
   ps=np.array([v[0][b][:3,3]for v in values]);sc=np.array([v[1][b]for v in values]);rs=eulers([base[b][:3,:3].T@v[0][b][:3,:3]for v in values])
   if name=='shoot' or name in movement_names:
    ps-=base[b][:3,3];sc/=bscale[b]
   if name in movement_names:
    # Source f32 loops have tiny endpoint drift. Match the two boundary
    # chords to their mean, avoiding a pause or accumulated loop offset.
    for channel in (ps,rs,sc):
     raw_end=float(np.max(abs(channel[-1]-channel[0])));channel[-1]=channel[0]
     start=(channel[1]-channel[0])/(times[1]-times[0]);end=(channel[-1]-channel[-2])/(times[-1]-times[-2]);vel=(start+end)*.5
     channel[1]=channel[0]+vel*(times[1]-times[0]);channel[-2]=channel[-1]-vel*(times[-1]-times[-2])
     if name=='walk_forward':movement_seams.append({'bone':b,'source_endpoint_component_drift':raw_end,'corrected_endpoint_component_drift':float(np.max(abs(channel[-1]-channel[0]))),'boundary_velocity_component_error':float(np.max(abs((channel[1]-channel[0])/(times[1]-times[0])-(channel[-1]-channel[-2])/(times[-1]-times[-2]))))})
   if name=='static_idle':ps[:]=base[b][:3,3];sc[:]=bscale[b];rs[:]=0
   bones[b]={'position':keyed(times,ps*[16,-16,16]),'rotation':keyed(times,rs),'scale':keyed(times,sc)}
  animations[name]={'loop':loop,'animation_length':duration,'bones':bones}
  if name not in movement_names and name not in('reload_tactical','inspect_empty'):
   if name=='static_idle':values=[targets('TORSO_STAND',0)for _ in times]
   references.extend({'animation':name,'source':source,'time':t,'matrices':{b:m.tolist()for b,m in v[0].items()},'scales':{b:s.tolist()for b,s in v[1].items()},'landmarks':v[2]}for t,v in zip(times,values))
 for name,incoming in(('run_start',True),('run_end',False)):
  duration=.16;ts=np.linspace(0,duration,11);bs={}
  for b in base:
   src=animations['run_hold']['bones'][b];p=np.array(next(iter(src['position'].values())));r=np.array(next(iter(src['rotation'].values())));s=np.array(next(iter(src['scale'].values())));f=ts/duration;f=f*f*(3-2*f)
   if not incoming:f=1-f
   bs[b]={'position':keyed(ts,f[:,None]*p),'rotation':keyed(ts,f[:,None]*r),'scale':keyed(ts,1+f[:,None]*(s-1))}
  animations[name]={'loop':False,'animation_length':duration,'bones':bs}
 for name in('idle','jump','land','aim','aim_idle','static_aim','static_bolt_caught','static_over_heating','over_heat'):
  animations[name]={'loop':True,'animation_length':1,'bones':{}}
 # Official walk.exit -> run.entry may transition twice in one trigger. An
 # empty idle loses native recoverChannels on the second transition; explicit
 # identity delta channels preserve the complete moving-bone channel set.
 animations['idle']['bones']={b:{'position':{'0':[0,0,0],'1':[0,0,0]},'rotation':{'0':[0,0,0],'1':[0,0,0]},'scale':{'0':[1,1,1],'1':[1,1,1]}}for b in animations['walk_forward']['bones']}
 animations['put_away']={'loop':False,'animation_length':.25,'bones':{b:{'position':{'0':(m[:3,3]*[16,-16,16]).tolist(),'.25':(m[:3,3]*[16,-16,16]+[0,12,0]).tolist()},'rotation':[0,0,0],'scale':bscale[b].tolist()}for b,m in base.items()}}
 bones=[{'name':'root','pivot':[0,0,0]}]+[{'name':b,'parent':'root','pivot':[0,0,0],'rotation':be[b].tolist()}for b in base]
 bones.extend({'name':s+'hand_pos','parent':s+'hand','pivot':[0,0,0]}for s in('left','right'))
 camera=gun.globals('TORSO_STAND',0,hand)['Camera01'];eye=Q@(camera[:3,3]*.01-origin)
 source_points=np.vstack([(np.c_[p['positions'],np.ones(len(p['positions']))]@base[p['bone']].T)[:,:3]for p in parts]);center=(source_points.min(0)+source_points.max(0))/2;side=Rotation.from_euler('y',85,degrees=True).as_matrix();refit_eye=center+side.T@np.array([0,0,1.7])
 for n in('idle_view','iron_view','scope_pos'):bones.append({'name':n,'pivot':(eye*[16,-16,16]).tolist(),'rotation':[0,0,180]})
 bones.extend([{'name':'refit_view','pivot':(refit_eye*[16,-16,16]).tolist(),'rotation':[0,85,180]},{'name':'camera','pivot':[0,24,0]},{'name':'fixed','parent':'root','pivot':(center*[16,-16,16]).tolist(),'rotation':[0,85,180]},{'name':'ground','parent':'root','pivot':[0,0,0]},{'name':'thirdperson_hand','parent':'root','pivot':(native(hand.globals('TORSO_STAND',0,gun)['Bip01_R_Hand'])[:3,3]*[16,-16,16]).tolist()}])
 save(AS/'animations/hand_of_zeuss2.animation.json',{'format_version':'1.8.0','animations':animations});save(AS/'geo_models/gun/hand_of_zeuss2_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.hand_of_zeuss2','texture_width':1024,'texture_height':1024,'visible_bounds_width':8,'visible_bounds_height':8,'visible_bounds_offset':[0,0,0]},'bones':bones}]})
 calibration={'status':'SOURCE_CAMERA_DEFAULT_FOV_NOT_VIDEO_FITTED','source_to_native_rotation':Q.tolist(),'source_origin_blocks':origin.tolist(),'idle_eye_source_blocks':(camera[:3,3]*.01).tolist(),'idle_eye_native_blocks':eye.tolist(),'source_units_to_blocks':.01,'left_slim_x':-.03125,'right_slim_x':.03125,'arm_transverse_scale':transverse,'refit_eye_native_blocks':refit_eye.tolist(),'refit_rotation_degrees':[0,85,180],'fixed_center_native_blocks':center.tolist(),'fixed_rotation_degrees':[0,85,180],'fitted_vertical_fov_degrees':60,'reference_exact':False,'refit':'Source body bounds centered upright side view; yaw85 avoids Euler singularity; live visual QA required','fixed':'Source body bounds centered upright workbench side view; live visual QA required'};save(AS/'view_calibration.json',calibration)
 report={'status':'FRESH_SOURCE_IMPORTED_OFFLINE_NUMERICAL_ONLY','input_sha256':INPUTS,'source_variant':'hand_of_zeuss2','body_bones':body,'mesh_checks':checks,'material_trace':trace,'atlas':{str(p.relative_to(P)):list(v)for p,v in atlas.items()},'coordinate_contract':{'J_reflection':'Unity mesh/default rig reflected in X (positions, normals, bind, winding); raw ANM is already in reflected source basis and overrides local TRS. Gun and hand tracks merge by exact bone name. Q maps source gun axis to native -Z.','Q':Q.tolist(),'origin_blocks':origin.tolist(),'unit':.01},'raw_ANM':{k:{'bytes_consumed':v.raw['bytes_consumed'],'clip_count':len(v.clips),'clips':{c:a['duration']for c,a in v.clips.items()}}for k,v in(('gun',gun),('hand',hand))},'native_arm_fit':{'forearm_cross_section_widths_blocks':widths,'shared_transverse_scale':transverse,'maximum_cap_contact_error_blocks':max_contact,'fixture':'Vanilla neutral player arm caps only; source hand/old weapon fit excluded.'},'movement_seams':movement_seams,'camera':calibration,'adaptations':['put_away authored .25s lowering; source has no put-away',f"run uses source WALK at its original {gun.clips['TORSO_WALK']['duration']:.9g}s duration; run_start/end are authored .16s cubic envelopes",'movement source endpoint drift removed; first/last boundary velocity averaged over one 60Hz key interval','static_idle holds source stand frame0 for exact additive shoot composition','ADS uses source camera position; no original ADS animation','native cube palms replace source fingers; contact endpoint exact but visible contact needs rendered/live evidence'],'boundary':'No claim of native TaCZ parser, full rendering, video match, live-client or install verification from this source generator.'};save(W/'evidence/source_import.json',report);save(W/'evidence/native_reference_samples.json',{'samples':references,'baseline':{b:m.tolist()for b,m in base.items()},'baseline_scales':{b:s.tolist()for b,s in bscale.items()},'camera':calibration,'slim_callback_translation':{'L':[-.03125,0,0],'R':[.03125,0,0]}})
 report['adaptations'].append('TaCZ official walk.exit -> run.entry may transition twice during one trigger. Explicit identity delta TRS idle channels preserve all moving-bone channels without idle motion or native recoverChannels loss.');save(W/'evidence/source_import.json',report)
 render_previews(parts,AS/'textures/gun/uv/hand_of_zeuss2.png',targets,base,eye)
 print(json.dumps({'meshes':checks,'body':body,'arm_transverse_scale':transverse,'camera':calibration},ensure_ascii=True))

if __name__=='__main__':main()
