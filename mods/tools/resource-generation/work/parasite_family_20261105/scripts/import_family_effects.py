"""Fresh conversion of original Unity FX meshes, materials, curves and particles.

No old Parasite FX implementation is an input. Static body geometry is kept in
the source prefab's frame and then reflected/scaled into WeaponAnchor2 blocks.
Unity's customType=22 binding stores the texture ST component in bits28..31,
and CRC32(property) in bits0..27. This is validated before decoding UV tracks.
"""
from pathlib import Path
import sys,json,hashlib,struct,zlib,shutil
import numpy as np
from PIL import Image,ImageDraw
R=Path.cwd();W=R/'work/parasite_family_20261105';A=W/'pack/assets/ssjjparasite2'
sys.path.insert(0,str(R/'work/parasite_video_match_20261104/scripts'))
import import_gun_source as s
from source_catalog import VariantCatalog,original_reader
s.read_bytes=original_reader(R,s.INPUTS)
c=VariantCatalog(R,s.read_bytes);trace=[];textures={}
# Original FX prefab is Y-up, whereas the source gun's WeaponAnchor2 bind is
# Z-up. Align the two frames before attachment; X remains the barrel axis.
FX_TO_WEAPON=np.array([[1.,0,0,0],[0,0,-1,0],[0,1,0,0],[0,0,0,1]])
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def xyz(v):return [v[k]for k in 'xyz']
def rgba(v):return np.array([v[k]for k in 'rgba'])
def pointer(i):return c.json(c.find(i))
def components(go):return [pointer(x['component']['m_PathID'])for x in go['m_Component']]
def txmat(d):return s.matrix_trs(xyz(d['m_LocalPosition']),[d['m_LocalRotation'][k]for k in 'xyzw'],xyz(d['m_LocalScale']))
def globaltx(t):return (globaltx(pointer(t['m_Father']['m_PathID']))if t['m_Father']['m_PathID']else np.eye(4))@txmat(t)
def material(i):
 m=pointer(i);props=m['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];tid=env['m_Texture']['m_PathID'];p=c.find(tid,suffix='.png');raw=s.read_bytes(p);h=hashlib.sha256(raw).hexdigest();name='textures/effects/'+h[:20]+'.png';out=A/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(raw);im=Image.open(p).convert('RGBA');textures[name]={'source':str(p.relative_to(R)),'sha256':h,'size':list(im.size),'rgb_and_alpha_original':True}
 colors=dict(props['m_Colors']);col=2*rgba(colors.get('_TintColor',dict(r=.5,g=.5,b=.5,a=.5)))*rgba(colors.get('_DyeingColor',dict(r=1,g=1,b=1,a=1)));col[3]*=dict(props['m_Floats']).get('_Alpha',1)
 return {'texture':'ssjjparasite2:'+name,'color':col.tolist(),'st':[env['m_Scale']['x'],env['m_Scale']['y'],env['m_Offset']['x'],env['m_Offset']['y']]}
def mesh(i):
 p=c.find(i);d=s.decode_unity_mesh(c.json(p));assert d['positions']is not None and d['uv']is not None
 return {'positions':d['positions'].tolist(),'uv':d['uv'].tolist(),'triangles':d['triangles'].tolist()}
def lut_curve(v,n=65,default=1):
 if v is None:return [default]*n
 mode=v['minMaxState'];keys=v['maxCurve']['m_Curve']
 if mode in [0,3]or not keys:return [v['scalar']if mode!=3 else(v['scalar']+v['minScalar'])/2]*n
 def f(t):
  if t<=keys[0]['time']:return keys[0]['value']
  if t>=keys[-1]['time']:return keys[-1]['value']
  b=next(j for j,k in enumerate(keys)if k['time']>t);a=keys[b-1];b=keys[b];dt=b['time']-a['time'];x=(t-a['time'])/dt
  return(2*x**3-3*x*x+1)*a['value']+(x**3-2*x*x+x)*dt*a['outSlope']+(-2*x**3+3*x*x)*b['value']+(x**3-x*x)*dt*b['inSlope']
 return [f(x)*v['scalar']for x in np.linspace(0,1,n)]
def bounds(v,default=0):
 if not v:return [default,default]
 if v['minMaxState']==3:return [v['minScalar'],v['scalar']]
 assert v['minMaxState']==0, 'New source curve mode must be supported explicitly'
 return [v['scalar'],v['scalar']]
def color_lut(v):
 if not v or not v.get('enabled'):return [[1,1,1,1]]*65
 g=v['gradient'];assert g['minMaxState']in [0,1]
 if g['minMaxState']==0:return [rgba(g['maxColor']).tolist()]*65
 g=g['maxGradient'];cs=[(g[f'ctime{i}']/65535,rgba(g[f'key{i}'])[:3])for i in range(g['m_NumColorKeys'])];als=[(g[f'atime{i}']/65535,g[f'key{i}']['a'])for i in range(g['m_NumAlphaKeys'])]
 return [[float(np.interp(t,[x[0]for x in cs],[x[1][j]for x in cs]))for j in range(3)]+[float(np.interp(t,[x[0]for x in als],[x[1]for x in als]))]for t in np.linspace(0,1,65)]
def uv_animation(anim):
 controller=pointer(anim['m_Controller']['m_PathID']);clips=controller['m_AnimationClips'];assert len(clips)==1
 clip=pointer(clips[0]['m_PathID']);m=clip['m_MuscleClip'];data=m['m_Clip']['data'];stream=data['m_StreamedClip'];words=stream['data'];tracks={};at=0
 def fl(x):return struct.unpack('<f',struct.pack('<I',x))[0]
 while at<len(words):
  time=fl(words[at]);cnt=words[at+1];at+=2
  for j in range(cnt):
   idx=words[at];co=[fl(x)for x in words[at+1:at+5]];at+=5
   if np.isfinite(time)and time>=0:tracks.setdefault(idx,[]).append([time,*co])
 assert data['m_DenseClip']['m_CurveCount']==0
 const=data['m_ConstantClip']['data'];cc=stream['curveCount'];out=[]
 for i,b in enumerate(clip['m_ClipBindingConstant']['genericBindings']):
  assert b['customType']==22 and b['typeID']==23 and (b['attribute']&0xfffffff)==(zlib.crc32(b'_MainTex_ST')&0xfffffff)
  component=b['attribute']>>28;assert component in range(4)
  out.append({'component':component,'keys':tracks.get(i,[]),'constant':const[i-cc]if i>=cc else 0})
 return {'duration':m['m_StopTime'],'tracks':out}
def build(code,kind):
 detail=R/f'寄生者/特效待确认_全量目录/详情/{code}.json';doc=json.loads(detail.read_text('utf8'));c.scope=doc['card']['occurrences'][0]['bundle'][:10];layers=[];emitters=[]
 for o in doc['objects']:
  if o['type']not in ['MeshRenderer','ParticleSystem']:continue
  v=pointer(o['id']);assert v==o['data'],'Effect catalogue differs from original Unity object'
  go=pointer(v['m_GameObject']['m_PathID']);comp=components(go);t=next(z for z in comp if 'm_LocalPosition'in z);matrix=globaltx(t)
  if kind in ['body','muzzle']:matrix=FX_TO_WEAPON@matrix
  if not go['m_IsActive']:continue
  if o['type']=='MeshRenderer':
   if not v['m_Enabled']:continue
   mf=next(z for z in comp if 'm_Mesh'in z and len(z)==2);geom=mesh(mf['m_Mesh']['m_PathID']);p=np.array(geom['positions']);p=(s.J4@matrix@np.c_[p,np.ones(len(p))].T).T[:,:3]*.01
   geom['positions']=p.tolist();geom['triangles']=np.array(geom['triangles'])[:,[0,2,1]].tolist();geom['uv']=(np.array(geom['uv'])*[1,-1]+[0,1]).tolist()
   mat=material(v['m_Materials'][0]['m_PathID']);anim=next((z for z in comp if 'm_Controller'in z),None);layers.append(dict(name=go['m_Name'],geometry=geom,material=mat,animation=uv_animation(anim)if anim else None))
  else:
   em=v.get('EmissionModule',{});psr=next(z for z in comp if 'm_RenderMode'in z)
   if not em.get('enabled')or not psr['m_Enabled']:continue
   init=v['InitialModule'];mid=psr['m_Materials'][0]['m_PathID'];assert mid
   supported={'InitialModule','EmissionModule','ShapeModule','ColorModule','SizeModule','UVModule','RotationModule','ForceModule'}
   unsupported=[k for k,x in v.items()if k.endswith('Module')and isinstance(x,dict)and x.get('enabled')and k not in supported]
   assert not unsupported,(go['m_Name'],unsupported)
   # Keep complete source transforms in centimetres for mesh particles; the
   # runtime reflects/scales each simulated point once at the output boundary.
   rate=em['rateOverTime']['scalar'];shape=v.get('ShapeModule',{});size=v.get('SizeModule',{});uv=v.get('UVModule',{});rot=v.get('RotationModule',{})
   particle_mesh=mesh(psr['m_Mesh']['m_PathID'])if psr['m_RenderMode']==4 else None
   if particle_mesh:particle_mesh['uv']=(np.array(particle_mesh['uv'])*[1,-1]+[0,1]).tolist()
   bursts=[]
   for b in em['m_Bursts']:
    assert b['countCurve']['minMaxState']==0 and b['probability']==1
    bursts.append({'time':b['time'],'count':round(b['countCurve']['scalar']),'cycles':b['cycleCount'],'repeat':b['repeatInterval']})
   force=v.get('ForceModule',{});acc=np.array([0.,0,0])
   if force.get('enabled'):
    assert not force['randomizePerFrame']and all(force[k]['minMaxState']==0 for k in 'xyz')
    acc=np.array([force[k]['scalar']for k in 'xyz']);basis=(FX_TO_WEAPON[:3,:3]if kind in ['body','muzzle']else np.eye(3))if force['inWorldSpace']else matrix[:3,:3]/np.linalg.norm(matrix[:3,:3],axis=0);acc=s.J3@basis@acc*.01
   emitters.append({'name':go['m_Name'],'transform':matrix.tolist(),'duration':v['lengthInSec'],'delay':v['startDelay']['scalar'],'rate':rate,'maxParticles':min(init['maxNumParticles'],64),'lifetime':bounds(init['startLifetime']),'speed':bounds(init['startSpeed']),'size':bounds(init['startSize']),'rotation':bounds(init['startRotation']),'acceleration':acc.tolist(),'initialColor':rgba(init['startColor']['maxColor']).tolist(),'color':color_lut(v.get('ColorModule')),'sizeCurve':lut_curve(size.get('curve'))if size.get('enabled')else[1]*65,'sizeYCurve':lut_curve(size.get('y',size.get('curve')))if size.get('enabled')else[1]*65,'sizeZCurve':lut_curve(size.get('z',size.get('curve')))if size.get('enabled')else[1]*65,'spin':lut_curve(rot.get('curve'),default=0)if rot.get('enabled')else[0]*65,'mode':psr['m_RenderMode'],'lengthScale':psr.get('m_LengthScale',1),'velocityScale':psr.get('m_VelocityScale',0),'shape':{'enabled':bool(shape.get('enabled')),'type':shape.get('type',0),'radius':shape.get('radius',{}).get('value',0),'angle':shape.get('angle',0),'length':shape.get('length',0),'scale':xyz(shape.get('m_Scale',dict(x=1,y=1,z=1))),'position':xyz(shape.get('m_Position',dict(x=0,y=0,z=0))),'rotation':xyz(shape.get('m_Rotation',dict(x=0,y=0,z=0))),'thickness':shape.get('radiusThickness',1),'spherical':shape.get('sphericalDirectionAmount',0)},'uv':{'enabled':bool(uv.get('enabled')),'x':uv.get('tilesX',1),'y':uv.get('tilesY',1),'cycles':uv.get('cycles',1),'frame':lut_curve(uv.get('frameOverTime'),default=0)},'material':material(mid),'geometry':particle_mesh,'bursts':bursts,'rotationX':bounds(init.get('startRotationX'))if init.get('rotation3D')else[0,0],'rotationY':bounds(init.get('startRotationY'))if init.get('rotation3D')else[0,0]})
 trace.append({'effect':kind,'source_card':code,'static_layers':len(layers),'particle_emitters':len(emitters),'particle_modes':[p['mode']for p in emitters]})
 return {'layers':layers,'emitters':emitters}
profiles={}
empty=lambda:dict(layers=[],emitters=[])
mapping=[('parasitic_sniper','E029','E028',None),('parasitic_sniper1','E038','E036',None),('parasitic_sniper2','E008','E001','E002'),('parasitic_sniper_frost','E073','E060','E066'),('parasitic_sniper1_frost','E087','E057','E080'),('parasitic_sniper2_frost','E101','E058','E094'),('parasitic_sniper_ship','E132','E124',None),('parasitic_sniper1_ship','E142','E122',None),('parasitic_sniper2_ship','E152','E123','E002')]
for key,body,muzzle,impact in mapping:
 gun=key.removesuffix('_frost').removesuffix('_ship')
 profiles[key]=dict(schema=1,units='blocks',gun='ssjjparasite2:'+gun,bone='WeaponAnchor2',body=build(body,'body'),muzzle=build(muzzle,'muzzle'),impact=build(impact,'impact')if impact else empty(),muzzleLocal=[.887,0,.007])
save(A/'effects/family.json',dict(profiles=profiles))
save(W/'evidence/source_effects_import.json',dict(passed=True,inputs_sha256=s.INPUTS,textures=textures,effects=trace,profile_sources=[dict(key=k,body=b,muzzle=m,impact=i)for k,b,m,i in mapping],original_RGBA_bytes=True,conversion='Original per-tier/per-skin prefab geometry, transforms, UV curves and particles. Original normal-state FX only; no invented high-energy skill triggers. Box/cone emission and local force adapted to the gun frame.'))
print('Imported',len(profiles),'profiles with',len(textures),'original effect textures')
