"""Bake only the original tier-II default continuous high-energy gun effects.

Source textures and meshes are retained. Private particle distributions/integration
are explicitly adapted; this does not generate replacement decorative artwork.
"""
from pathlib import Path
import hashlib,json,math
import numpy as np
from scipy.spatial.transform import Rotation
from build_effects import xyz,unit,rgb,rgba,interp,one,mesh

W=Path(__file__).resolve().parent
ROOT=W.parents[1]
SOURCE=ROOT/'次元之星/特效待确认_全量目录'
AS=W/'resources/assets/ssjj_dimension'
J=np.diag([-1.,1.,1.,1.])
# Same measured FX-to-gun joint mapping as the accepted ordinary body effect.
ALIGN=np.eye(4)
CARDS={'overload':'E059'}
DISABLED_CARDS={'start':'E060','start_gun':'E061'}
SEED=0x2026110817
N=129


def sha(raw):return hashlib.sha256(raw).hexdigest()
def save(path,value):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(value,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')


def build():
 inputs={};textures={};records=[];outputs={}
 for kind,code in CARDS.items():
  path=SOURCE/f'详情/{code}.json';raw=path.read_bytes();inputs[path.relative_to(ROOT).as_posix()]=sha(raw);doc=json.loads(raw)
  obs={o['id']:o['data']for o in doc['objects']if'data'in o}
  def tx(t):
   own=np.eye(4);own[:3,:3]=Rotation.from_quat([t['m_LocalRotation'][k]for k in'xyzw']).as_matrix()@np.diag(xyz(t['m_LocalScale']));own[:3,3]=xyz(t['m_LocalPosition']);pid=t['m_Father']['m_PathID']
   return tx(obs[pid])@own if pid else own
  texids={s['id']:t for t in doc['card']['textures']for s in t['sources']}
  controller=next(v for v in obs.values()if'ssjjpaList'in v);emitters=[];traces=[];maxend=0
  for obj in doc['objects']:
   v=obj.get('data',{})
   if'ParticleNumber'not in v:continue
   go=obs[v['m_GameObject']['m_PathID']];name=go['m_Name'];rng=np.random.default_rng(SEED^(obj['id']&0xffffffff))
   t=next(obs[x['component']['m_PathID']]for x in go['m_Component']if'm_LocalPosition'in obs[x['component']['m_PathID']]);matrix=J@ALIGN@tx(t);proper=matrix@J
   scale=np.linalg.norm(proper[:3,:3],axis=0);u,_,vh=np.linalg.svd(proper[:3,:3]/np.maximum(scale,1e-20));rotation=u@vh
   mat=obs[v['mater']['m_PathID']];props=mat['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];tex=texids[env['m_Texture']['m_PathID']]
   original=SOURCE/tex['image'];image=original.read_bytes();digest=sha(image);inputs[original.relative_to(ROOT).as_posix()]=digest
   target=AS/f'textures/effects/decoy/{digest[:20]}.png';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(image);textures[target.relative_to(W/'resources').as_posix()]=digest
   tint=2*rgba(dict(props['m_Colors'])['_TintColor'])*rgba(dict(props['m_Colors'])['_DyeingColor']);blend=dict(props['m_Floats']).get('_BlendDst',1)
   geometry=None
   if v['billboardType']==4 or v['mesh']['m_PathID']!=10210:
    mid=v['mesh']['m_PathID']
    if mid==10210:geometry={'positions':[[-.005,-.005,0],[.005,-.005,0],[.005,.005,0],[-.005,.005,0]],'uv':[[0,1],[1,1],[1,0],[0,0]],'triangles':[[0,1,2],[0,2,3]]}
    else:
     if mid in obs:md=obs[mid]
     else:
      candidates=sorted((ROOT/'次元之星').rglob(f'*__{mid}.json'));assert candidates,('Missing original activation mesh',mid)
      blobs=[(p,p.read_bytes())for p in candidates];assert len({sha(b)for _,b in blobs})==1,('Conflicting original mesh',mid)
      mp,mb=blobs[0];inputs[mp.relative_to(ROOT).as_posix()]=sha(mb);md=json.loads(mb)
     geometry=mesh(md)
   particles=[];centers=[]
   for index in range(v['ParticleNumber']):
    f=index/max(v['ParticleNumber']-1,1);birth=one(v['startTime'],f,rng);life=one(v['lifeTime'],f,rng);assert birth>=0 and life>0
    sh=v['shape'];position=np.zeros(3);direction=np.zeros(3)
    if sh['shapeType']==0:
     lo=xyz(sh['_rectFrom']);hi=xyz(sh['_rectTo']);position=lo+(hi-lo)*rng.random(3);direction=unit(position)
    elif sh['shapeType']==1:
     angle=rng.uniform(0,math.tau);rad=sh['_radius']*(1 if sh['_shell']else rng.random());direction=unit(np.array([-sh['sinAngle']*math.sin(angle),sh['cosAngle'],sh['sinAngle']*math.cos(angle)]));position=np.array([-rad*math.sin(angle),0,rad*math.cos(angle)])
     if sh['_volum']:
      assert abs(direction[1])>1e-8
      position+=direction*sh['_length']/direction[1]*rng.random()
    elif sh['shapeType']==2:
     angle=rng.uniform(0,math.tau);rad=rng.uniform(sh['_radiusSmall'],sh['_radiusBig']);position=np.array([-math.sin(angle)*rad,rng.uniform(-sh['_height'],sh['_height']),math.cos(angle)*rad]);direction=np.array([0.,0.,1.])
    elif sh['shapeType']==3:
     angle=rng.uniform(0,math.tau);latitude=rng.uniform(math.pi/2,math.pi*1.5);rad=rng.uniform(min(sh['_radiusSmall'],sh['_radiusBig']),max(sh['_radiusSmall'],sh['_radiusBig']));position=rad*np.array([-math.cos(latitude)*math.sin(angle),math.sin(latitude),math.cos(latitude)*math.cos(angle)]);direction=unit(position)
    else:raise ValueError(('Unknown original activation shape',sh))
    if not v['directionByShape']:direction=xyz(v['direction']['value'])
    direction=unit(direction)
    assert not v['hasOscillatorEffector'] and not v['hasAccelerateEffector'],('Unexpected non-continuous effector',code,name)
    speed=one(v['velocity'],f,rng);angle=one(v['rot'],f,rng);spin=one(v['rotvelocity'],f,rng);sizes=np.array([one(v['size'+a],f,rng)for a in'XYZ']);alpha=one(v['alpha'],f,rng)
    if v['uniformScale_']:sizes[:]=sizes[0]
    coldef=v['color'];color0=rgb(coldef['_value'])if coldef['colorType']==0 else rgb(coldef['_color0'])*(1-f)+rgb(coldef['_color1'])*f
    frames=[]
    for phase in np.linspace(0,1,N):
     age=phase*life;point=position+direction*speed*age
     if v['hasForceEffector']:point+=.5*xyz(v['_forceDir'])*v['_force']*age*age
     if v['hasGravityEffector']:point+=xyz(v['gravityDir'])*v['gravity']*age*age
     center=(matrix@np.r_[point,1])[:3]*.01;size=sizes.copy()
     if v['hasSizeEffector']:size*=np.array([interp(phase,v['_keyFrameLifeTime'],v['_size'+a])for a in'XYZ'])
     size*=scale
     if geometry:
      axis=np.zeros(3);axis[int(v['RotateAxis'])]=angle+spin*age;q=((Rotation.from_matrix(rotation)if v['billboardType']==4 else Rotation.identity())*Rotation.from_rotvec(J[:3,:3]@axis*-1)).as_quat()
     else:size=np.array([size[0],size[1],1.])*.01;q=Rotation.from_euler('z',angle+spin*age).as_quat()
     color=color0.copy();a=alpha
     if v['hasGradientColorEffector']:
      if not v['JustAlpha']and v['_colors']:
       colors=np.array([rgb(c)for c in v['_colors']]);color=np.array([interp(phase,np.array(v['_colorRatios'])/255,colors[:,j])for j in range(3)])
      a*=interp(phase,np.array(v['_alphaRatios'])/255,v['_alphas'])
     rgba_out=np.clip(np.r_[color,a]*tint,0,1)
     us,vs=env['m_Scale']['x'],env['m_Scale']['y'];uo,vo=env['m_Offset']['x'],env['m_Offset']['y']
     if v['hasSpriteSheetEffector']:us/=max(1,v['columns']);vs/=max(1,v['rows'])
     if v['hasUVEffector']:uo+=interp(phase,v['_uvkeyFrameLifeTime'],v['_u'],0);vo-=interp(phase,v['_uvkeyFrameLifeTime'],v['_v'],0)
     frames.append(np.r_[center,size,q,rgba_out,us,vs,uo,vo].round(7).tolist());centers.append(center)
    particles.append({'birth':birth,'life':life,'frames':frames});maxend=max(maxend,birth+life)
   period=max(p['birth']+p['life']for p in particles)
   emitters.append({'name':name,'texture':f'ssjj_dimension:textures/effects/decoy/{digest[:20]}.png','render':'additive'if blend==1 else'alpha','mode':('mesh'if v['billboardType']==4 else'camera_mesh')if geometry else('velocity'if v['billboardType']==1 else'billboard'),'loop':bool(v['loop']),'period':period,'rows':v['rows']if v['hasSpriteSheetEffector']else 0,'columns':v['columns']if v['hasSpriteSheetEffector']else 0,'geometry':geometry,'particles':particles})
   bounds=np.array(centers)
   traces.append({'name':name,'component':obj['id'],'source_transform':tx(t).tolist(),'output_bounds_blocks':[bounds.min(0).tolist(),bounds.max(0).tolist()],'source_definition':v,'source_material':mat,'texture_sha256':digest})
  data={'schema':1,'gun':'ssjj_dimension:dimension_star2','bone':'weapon_particle_effect','kind':kind,'duration':max(maxend,controller['duration']),'emitters':emitters}
  out=AS/f'effects/decoy/{kind}.json';save(out,data);outputs[out.relative_to(W/'resources').as_posix()]=sha(out.read_bytes())
  records.append({'kind':kind,'card':code,'source_prefab':doc['card']['path'],'source_controller_duration':controller['duration'],'emitters':len(emitters),'particles':sum(len(e['particles'])for e in emitters),'traces':traces})

 save(W/'evidence/decoy_effect_source.json', {'passed':True,'input_sha256':inputs,'texture_sha256':textures,'profiles':outputs,'records':records,'coordinate_basis':'Original world-space Unity transforms reflected on X, units *.01; no field-radius fit','live_client_test':False})
