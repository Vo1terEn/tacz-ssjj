"""Build Dimension Star II from its original GLB, Unity JSON and raw ANM.

No old weapon pose, camera calibration or generated gun resource is input.
Uses measured vanilla arm-cap geometry only; cube arm lengths/width are
recomputed from this source. All input SHA and numerical limits are recorded.
"""
from __future__ import annotations
import csv, hashlib, json, struct, warnings, sys, importlib.util
from pathlib import Path
import numpy as np
from PIL import Image
from scipy.spatial.transform import Rotation, Slerp

W=Path(__file__).resolve().parents[1]; P=W.parents[1]
RAW=P/'次元之星/本体'; RIG=RAW/'骨骼动画/nova_shotgun2'
CATALOG_PREFIX='nova_shotgun2'
AS=W/'pack/assets/ssjj_dimension'; INPUTS={}; J=np.diag([-1.,1.,1.,1.])
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

class Glb:
 def __init__(self,p):
  b=read(p);assert struct.unpack_from('<III',b)==(0x46546c67,2,len(b));o=12
  while o<len(b):
   n,k=struct.unpack_from('<II',b,o);part=b[o+8:o+8+n];o+=n+8
   if k==0x4e4f534a:self.doc=json.loads(part)
   elif k==0x004e4942:self.bin=part
 def arr(self,i):
  a=self.doc['accessors'][i];assert 'sparse'not in a;v=self.doc['bufferViews'][a['bufferView']]
  dt=np.dtype({5120:'i1',5121:'u1',5122:'<i2',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']]);n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
  z=np.ndarray((a['count'],n),dtype=dt,buffer=self.bin,offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',n*dt.itemsize),dt.itemsize)).copy()
  return np.maximum(z.astype(float)/np.iinfo(dt).max,-1)if a.get('normalized')else z

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

class Catalog:
 def __init__(self):
  self.rows=list(csv.DictReader(read(RAW/'资源索引.csv').decode('utf-8-sig').splitlines()));self.ids={};self.types={};self.cache={}
  for row in self.rows:
   for s in row['导出文件路径'].split(';'):
    p=RAW/s.replace('\\','/')
    if not p.is_relative_to(RAW/('导出素材/'+CATALOG_PREFIX)):continue
    self.types.setdefault(row['类型'],[]).append(p)
    try:i=int(p.stem.rsplit('__',1)[1])
    except ValueError:continue
    self.ids.setdefault(i,[]).append(p)
 def get(self,p):
  if p not in self.cache:self.cache[p]=load(p)
  return self.cache[p]
 def find(self,i,suffix='.json',bundle=None):
  ps=[p for p in self.ids[i]if p.suffix.lower()==suffix and(bundle is None or p.relative_to(RAW).parts[2]==bundle)]
  assert len(ps)==1,(i,ps);return ps[0]
 def root(self,rp):
  r=self.get(rp);bundle=rp.relative_to(RAW).parts[2];go=self.get(self.find(r['m_GameObject']['m_PathID'],bundle=bundle))
  t=next(self.get(self.find(c['component']['m_PathID'],bundle=bundle))for c in go['m_Component']if 'm_LocalPosition'in self.get(self.find(c['component']['m_PathID'],bundle=bundle)))
  while t['m_Father']['m_PathID']:t=self.get(self.find(t['m_Father']['m_PathID'],bundle=bundle))
  return self.get(self.find(t['m_GameObject']['m_PathID'],bundle=bundle))['m_Name']

class Rig:
 def __init__(self,g,kind):
  self.g=g;self.d=g.doc;self.nodes=self.d['nodes'];self.parents={c:i for i,n in enumerate(self.nodes)for c in n.get('children',[])};self.names={n['name']:i for i,n in enumerate(self.nodes)if 'mesh'not in n}
  self.defaults={i:trs(n.get('translation',[0,0,0]),n.get('rotation',[0,0,0,1]),n.get('scale',[1,1,1]))for i,n in enumerate(self.nodes)if 'mesh'not in n}
  raw=parse_anm(RIG/f'原生动画与动作配置/view/{kind}.anm.bytes');parsed=load(RIG/f'解析动画_JSON/{kind}.animations.json');assert raw['clips']==parsed['clips']
  self.raw=raw;self.clips={c['name']:c['blends'][0]for c in raw['clips']};self.tracks={}
  for c,a in self.clips.items():
   self.tracks[c]={}
   for t in a['tracks']:
    fs=t['keyframes'];times=np.array([f['time']for f in fs]);qs=np.array([f['rotation_xyzw']for f in fs]);qs/=np.linalg.norm(qs,axis=1)[:,None]
    assert np.all(np.diff(times)>0);self.tracks[c][t['bone']]=(times,np.array([f['translation']for f in fs]),qs,Slerp(times,Rotation.from_quat(qs))if len(times)>1 else None)
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
  return {self.nodes[i]['name']:visit(i)for i in local}

def decode_mesh(m):
 d=m['m_VertexData'];cs=d['m_Channels'];count=d['m_VertexCount'];b=bytes.fromhex(d['m_DataSize']['__bytes_hex__']);formats={0:'<f4',1:'<f2',2:'u1',3:'i1',4:'<u2',5:'<i2',6:'u1',7:'i1',8:'<u2',9:'<i2',10:'<u4',11:'<i4'};strides={}
 for c in cs:
  n=c['dimension']&15
  if n:strides[c['stream']]=max(strides.get(c['stream'],0),c['offset']+n*np.dtype(formats[c['format']]).itemsize)
 starts={};cur=0
 for s,sz in sorted(strides.items()):cur=(cur+15)//16*16;starts[s]=cur;cur+=count*sz
 assert cur==len(b)
 def ch(i):
  c=cs[i];dt=np.dtype(formats[c['format']]);n=c['dimension']&15;return np.ndarray((count,n),dtype=dt,buffer=b,offset=starts[c['stream']]+c['offset'],strides=(strides[c['stream']],dt.itemsize)).copy()
 return {'p':ch(0),'n':ch(1),'uv':ch(4),'w':ch(12),'j':ch(13),'tri':np.frombuffer(bytes(m['m_IndexBuffer']),dtype='<u2'if m['m_IndexFormat']==0 else'<u4').reshape(-1,3),'bind':np.array([[[v[f'e{i}{j}']for j in range(4)]for i in range(4)]for v in m['m_BindPose']])}

def import_mesh(glb,cat):
 records=[];parts=[];textures={};comparisons=[]
 for node in glb.doc['nodes']:
  if 'mesh'not in node:continue
  mesh=glb.doc['meshes'][node['mesh']];probe=glb.arr(mesh['primitives'][0]['attributes']['POSITION']);paths=[p for p in cat.types['Mesh']if p.suffix=='.json'and cat.get(p)['m_Name']==mesh['name'] and cat.get(p)['m_VertexData']['m_VertexCount']==len(probe)]
  paths=[p for p in paths if np.max(abs(decode_mesh(cat.get(p))['p']@J[:3,:3]-probe))<1e-5]
  paths=[p for p in paths if any(cat.get(r)['m_Mesh']['m_PathID']==int(p.stem.rsplit('__',1)[1]) and cat.root(r)=='gun' for r in cat.types['SkinnedMeshRenderer']if r.suffix=='.json')];assert len(paths)==1,(mesh['name'],paths)
  mp=paths[0];mid=int(mp.stem.rsplit('__',1)[1]);rs=[p for p in cat.types['SkinnedMeshRenderer']if p.suffix=='.json'and cat.get(p)['m_Mesh']['m_PathID']==mid and cat.root(p)=='gun'];assert rs
  rs=sorted(rs,key=lambda p:cat.get(p)['m_Mesh']['m_FileID']==0);rp=rs[0];renderer=cat.get(rp);bundle=rp.relative_to(RAW).parts[2];go=cat.get(cat.find(renderer['m_GameObject']['m_PathID'],bundle=bundle));active=bool(renderer['m_Enabled']and go['m_IsActive'])
  matp=cat.find(renderer['m_Materials'][0]['m_PathID']);mat=cat.get(matp);tex=dict(mat['m_SavedProperties']['m_TexEnvs'])['_MainTex'];tp=cat.find(tex['m_Texture']['m_PathID'],'.png')
  rec={'mesh':mesh['name'],'mesh_json':mp.relative_to(P).as_posix(),'renderer':rp.relative_to(P).as_posix(),'active':active,'material':matp.relative_to(P).as_posix(),'texture':tp.relative_to(P).as_posix(),'properties':mat['m_SavedProperties']};records.append(rec)
  prim=mesh['primitives'][0];a={k:glb.arr(v)for k,v in prim['attributes'].items()};skin=glb.doc['skins'][node['skin']];ibm=glb.arr(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1);tri=glb.arr(prim['indices']).reshape(-1,3).astype(int);u=decode_mesh(cat.get(mp))
  errors={'position':float(np.max(abs(a['POSITION']-u['p']@J[:3,:3]))),'normal':float(np.max(abs(a['NORMAL']-u['n']@J[:3,:3]))),'uv':float(np.max(abs(a['TEXCOORD_0']-(u['uv']*[1,-1]+[0,1])))),'bind':float(np.max(abs(ibm-J@u['bind']@J)))}
  assert max(errors.values())<1e-5 and np.array_equal(tri,u['tri'][:,[0,2,1]])
  weights=a['WEIGHTS_0'];rigid_vertices=np.count_nonzero(weights>1e-7,axis=1)==1;slots=a['JOINTS_0'][np.arange(len(weights)),np.argmax(weights,axis=1)].astype(int)
  assert np.all(rigid_vertices)and np.max(abs(weights.sum(1)-1))<1e-7,'Real weighted skinning required'
  assert np.all(slots[tri]==slots[tri[:,:1]]),'Cross-joint triangles require skinning'
  comparisons.append({'mesh':mesh['name'],'vertices':len(weights),'triangles':len(tri),'mixed_weight_vertices':int(np.count_nonzero(~rigid_vertices)),'errors':errors,'joints':[glb.doc['nodes'][i]['name']for i in skin['joints']]})
  if not active:continue
  if tp not in textures:textures[tp]=Image.open(__import__('io').BytesIO(read(tp))).convert('RGBA')
  for slot,ni in enumerate(skin['joints']):
   faces=tri[slots[tri[:,0]]==slot]
   if not len(faces):continue
   selected=np.unique(faces);remap=np.full(len(weights),-1);remap[selected]=np.arange(len(selected));pos=(ibm[slot]@np.c_[a['POSITION'][selected],np.ones(len(selected))].T).T[:,:3]*.01;norm=(np.linalg.inv(ibm[slot,:3,:3]).T@a['NORMAL'][selected].T).T;norm/=np.linalg.norm(norm,axis=1)[:,None]
   parts.append({'bone':glb.doc['nodes'][ni]['name'],'positions':pos.tolist(),'normals':norm.tolist(),'uv':a['TEXCOORD_0'][selected].astype(float).tolist(),'triangles':remap[faces].astype(int).tolist(),'_texture':tp})
 # Single atlas: exact source RGB; alpha opaque only where source material uses
 # alpha for non-transparency shader data. Main body uses opaque game material.
 width=max(im.width for im in textures.values());height=sum(im.height for im in textures.values());atlas=Image.new('RGBA',(width,height));offset=0;placements={}
 for path,im in textures.items():atlas.paste(im,(0,offset));placements[path]=(0,offset,im.width,im.height);offset+=im.height
 for part in parts:
  tp=part.pop('_texture');x,y,w,h=placements[tp];uv=np.array(part['uv']);uv=(uv*[w,h]+[x,y])/[width,height];part['uv']=uv.tolist()
 out=AS/'textures/gun/uv/dimension_star2.png';out.parent.mkdir(parents=True,exist_ok=True);atlas.save(out)
 save(AS/'source_mesh.json',{'parts':parts});return parts,records,comparisons,placements

def eulers(ms):
 with warnings.catch_warnings():warnings.simplefilter('ignore');raw=Rotation.from_matrix(np.asarray(ms)).as_euler('xyz',degrees=True)
 out=[raw[0]]
 for a in raw[1:]:
  cs=[b+360*np.round((out[-1]-b)/360)for b in(a,np.array([a[0]+180,180-a[1],a[2]+180]))];out.append(min(cs,key=lambda b:np.linalg.norm(b-out[-1])))
 return np.array(out)
def keyed(ts,vs):return {format(float(t),'.9g'):np.round(v,8).tolist()for t,v in zip(ts,vs)}

def main():
 cat=Catalog();gg=Glb(RIG/'可导入模型_GLB/view/gun.glb');hg=Glb(RIG/'可导入模型_GLB/view/hand_man.glb');gun=Rig(gg,'gun');hand=Rig(hg,'hand_man')
 parts,trace,checks,atlas=import_mesh(gg,cat);body=sorted({p['bone']for p in parts}|{'weapon_particle_effect','weapon_particle_effect1'});origin=gun.globals('TORSO_STAND',0,hand)['WeaponAnchor'][:3,3]*.01
 def native(m):
  r=rigid(m);r[:3,:3]=Q@r[:3,:3];r[:3,3]=Q@(m[:3,3]*.01-origin);return r
 # Width from original forearm mesh cross sections, not an old gun's fit.
 hp=hg.doc['meshes'][0]['primitives'][0];hskin=hg.doc['skins'][0];hj=hg.arr(hp['attributes']['JOINTS_0']).astype(int);hw=hg.arr(hp['attributes']['WEIGHTS_0']);hpos=hg.arr(hp['attributes']['POSITION']);hbind=hg.arr(hskin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
 widths=[]
 for side in('L','R'):
  slot=next(i for i,n in enumerate(hskin['joints'])if hg.doc['nodes'][n]['name']==f'Bip01_{side}_Forearm');selected=np.any((hj==slot)&(hw>.5),axis=1);loc=(hbind[slot]@np.c_[hpos[selected],np.ones(np.count_nonzero(selected))].T).T[:,:3]*.01
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
  duration=gun.clips[source]['duration'];times=sorted({float(np.float32(t))for t in np.r_[0,duration,np.arange(0,duration,1/60)]});times=[t for i,t in enumerate(times)if not i or t-times[i-1]>1e-6];values=[targets(source,t)for t in times];bones={}
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
 for n in('idle_view','iron_view','scope_pos'):bones.append({'name':n,'pivot':(eye*[16,-16,16]).tolist(),'rotation':[0,0,180]})
 bones.extend([{'name':'refit_view','pivot':[-32,0,0],'rotation':[0,90,180]},{'name':'camera','pivot':[0,24,0]},{'name':'fixed','parent':'root','pivot':[0,0,0],'rotation':[0,90,0]},{'name':'ground','parent':'root','pivot':[0,0,0]},{'name':'thirdperson_hand','parent':'root','pivot':(native(hand.globals('TORSO_STAND',0,gun)['Bip01_R_Hand'])[:3,3]*[16,-16,16]).tolist()}])
 save(AS/'animations/dimension_star2.animation.json',{'format_version':'1.8.0','animations':animations});save(AS/'geo_models/gun/dimension_star2_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.dimension_star2','texture_width':1024,'texture_height':1024,'visible_bounds_width':8,'visible_bounds_height':8,'visible_bounds_offset':[0,0,0]},'bones':bones}]})
 calibration={'status':'SOURCE_CAMERA_DEFAULT_FOV_NOT_VIDEO_FITTED','source_to_native_rotation':Q.tolist(),'source_origin_blocks':origin.tolist(),'idle_eye_source_blocks':(camera[:3,3]*.01).tolist(),'idle_eye_native_blocks':eye.tolist(),'source_units_to_blocks':.01,'left_slim_x':-.03125,'right_slim_x':.03125,'arm_transverse_scale':transverse,'refit':'Independent side view initial placement; offline projection and live visual QA required','fixed':'Independent workbench 90-degree side transform; live visual QA required'};save(AS/'view_calibration.json',calibration)
 report={'status':'FRESH_SOURCE_IMPORTED_OFFLINE_NUMERICAL_ONLY','input_sha256':INPUTS,'source_variant':'nova_shotgun2','body_bones':body,'mesh_checks':checks,'material_trace':trace,'atlas':{str(p.relative_to(P)):list(v)for p,v in atlas.items()},'coordinate_contract':{'J_reflection':'GLB already reflects Unity X; mesh/IBM/normal/UV/winding independently match Unity mesh JSON; raw ANM overrides default local matrices without reflection, matching source bind frame. Q maps source gun direction to native -Z.','Q':Q.tolist(),'origin_blocks':origin.tolist(),'unit':.01},'raw_ANM':{k:{'bytes_consumed':v.raw['bytes_consumed'],'clip_count':len(v.clips),'clips':{c:a['duration']for c,a in v.clips.items()}}for k,v in(('gun',gun),('hand',hand))},'native_arm_fit':{'forearm_cross_section_widths_blocks':widths,'shared_transverse_scale':transverse,'maximum_cap_contact_error_blocks':max_contact,'fixture':'Vanilla neutral player arm caps only; source hand/old weapon fit excluded.'},'movement_seams':movement_seams,'camera':calibration,'adaptations':['put_away authored .25s lowering; source has no put-away','run uses source WALK at original .8s speed; run_start/end are authored .16s cubic envelopes','movement source endpoint drift removed; first/last boundary velocity averaged over one 60Hz key interval','static_idle holds source stand frame0 for exact additive shoot composition','ADS uses source camera position; no original ADS animation','native cube palms replace source fingers; contact endpoint exact but visible contact needs rendered/live evidence'],'boundary':'No claim of native TaCZ parser, full rendering, video match, live-client or install verification from this source generator.'};save(W/'evidence/source_import.json',report);save(W/'evidence/native_reference_samples.json',{'samples':references,'baseline':{b:m.tolist()for b,m in base.items()},'baseline_scales':{b:s.tolist()for b,s in bscale.items()},'camera':calibration,'slim_callback_translation':{'L':[-.03125,0,0],'R':[.03125,0,0]}})
 report['adaptations'].append('TaCZ official walk.exit -> run.entry may transition twice during one trigger. Explicit identity delta TRS idle channels preserve all seven moving-bone channels without idle motion or native recoverChannels loss.');save(W/'evidence/source_import.json',report)
 print(json.dumps({'meshes':checks,'body':body,'arm_transverse_scale':transverse,'camera':calibration},ensure_ascii=True))

if __name__=='__main__':main()
