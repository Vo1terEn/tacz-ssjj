"""Bake the original tier-I/tier-II native-skin continuous high-energy gun effects.

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
ALIGN=np.array([[1.,0,0,0],[0,0,-1.,0],[0,1.,0,0],[0,0,0,1.]])
CARDS={'tier1_active':'E039','tier1_active_p3':'E040','tier1_frozen_active':'E143','tier1_frozen_active_p3':'E144','tier1_neon_active':'E231','tier1_neon_active_p3':'E232','tier1_royal_active':'E319','tier1_royal_active_p3':'E320','tier2_active':'E075','tier2_active_p3':'E076','tier2_frozen_active':'E167','tier2_frozen_active_p3':'E168','tier2_neon_active':'E255','tier2_neon_active_p3':'E256','tier2_royal_active':'E343','tier2_royal_active_p3':'E344'}
DISABLED_CARDS={'start':'E060','start_gun':'E061'}
SEED=0x2026110817
N=129


def sha(raw):return hashlib.sha256(raw).hexdigest()
def save(path,value):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(value,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')


def build():
 inputs={};textures={};records=[];outputs={}
 for profile_kind,code in CARDS.items():
  tier=int(profile_kind[4]);kind='active_p3'if profile_kind.endswith('_p3')else'active'
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
   target=AS/f'textures/effects/activation/{digest[:20]}.png';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(image);textures[target.relative_to(W/'resources').as_posix()]=digest
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
   emitters.append({'name':name,'texture':f'ssjj_dimension:textures/effects/activation/{digest[:20]}.png','render':'additive'if blend==1 else'alpha','mode':('mesh'if v['billboardType']==4 else'camera_mesh')if geometry else('velocity'if v['billboardType']==1 else'billboard'),'loop':bool(v['loop']),'period':period,'rows':v['rows']if v['hasSpriteSheetEffector']else 0,'columns':v['columns']if v['hasSpriteSheetEffector']else 0,'geometry':geometry,'particles':particles})
   bounds=np.array(centers)
   traces.append({'name':name,'component':obj['id'],'source_transform':tx(t).tolist(),'output_bounds_blocks':[bounds.min(0).tolist(),bounds.max(0).tolist()],'source_definition':v,'source_material':mat,'texture_sha256':digest})
  data={'schema':1,'gun':f'ssjj_dimension:dimension_star{tier}','bone':'weapon_particle_effect','kind':kind,'duration':max(maxend,controller['duration']),'emitters':emitters}
  out=AS/f'effects/activation/{profile_kind}.json';save(out,data);outputs[out.relative_to(W/'resources').as_posix()]=sha(out.read_bytes())
  records.append({'profile':profile_kind,'kind':kind,'card':code,'source_prefab':doc['card']['path'],'source_controller_duration':controller['duration'],'emitters':len(emitters),'particles':sum(len(e['particles'])for e in emitters),'traces':traces})
 disabled=[]
 for kind,code in DISABLED_CARDS.items():
  path=SOURCE/f'详情/{code}.json';raw=path.read_bytes();doc=json.loads(raw)
  inputs[path.relative_to(ROOT).as_posix()]=sha(raw)
  disabled.append({'kind':kind,'card':code,'source_prefab':doc['card']['path'],'reason':'User removed the instantaneous activation-current layers; not baked, loaded or selected by the runtime renderer.'})
 video=ROOT/'次元之星/次元之星-开启技能-1.mp4';inputs[video.relative_to(ROOT).as_posix()]=sha(video.read_bytes())
 save(W/'evidence/source_activation_effects.json',{
  'passed':True,
  'scope':'Original continuous high-energy body effects for both tiers and frozen/neon/royal skins. E060/E061 instantaneous activation-current layers remain disabled.',
  'input_sha256':inputs,'texture_sha256':textures,'profile_sha256':outputs,'records':records,'disabled_source_cards':disabled,
  'runtime_selected_kinds':['active','active_p3'],'instantaneous_activation_current_enabled':False,
  'coordinate_mapping':{'bone':'weapon_particle_effect','ALIGN':ALIGN.tolist(),'mapping':'Identical to accepted normal gun effects: J @ ALIGN @ full prefab transform; mesh positions J *.01; no field radius fit or world anchor.'},
  'adaptations':[
   'Private UserNewParticle source runtime unavailable; deterministic source-range random sampling and piecewise-linear source curves.',
   'The two continuous profiles retain the installed baseline output bytes. E060/E061 are excluded from generation and runtime selection; preexisting package resources may remain unused.',
   'Individual source loop flags are retained. After the initial birth delay, each particle recycles at its own source lifetime, following the prior decoded UserNewParticle clock in work/tier_expansion/body11/src/cn/punishmenteye/MuzzleParticles.java.',
   'Source box/cone(+Y)/cylinder/sphere branches follow the prior decoded UserNewParticle adapter; exact random samples remain deterministic replacements.',
   'Source forceDir is retained without normalization and force displacement is .5*forceDir*force*t^2; gravity is gravityDir*gravity*t^2 (unused by these cards).',
   'Source rotation fields are radians; per-axis life size curves are retained even for uniform initial scale. Billboard1 is velocity-aligned in the camera plane using LUT derivatives; exact mixed-space native camera construction remains a residual.',
   'Source sprite-sheet indices are selected discretely by runtime. All emitted PNG bytes are from the source material files; no generated texture substitutes.',
   'Gun callbacks follow native bone animations and player hands; no replacement activation hand animation is introduced.'
  ],'reference_video':'次元之星/次元之星-开启技能-1.mp4','live_client_test':False})
 print('PASS all tier/skin source activation: '+str(sum(r['emitters']for r in records))+' emitters, '+str(sum(r['particles']for r in records))+' particles')


if __name__=='__main__':build()
