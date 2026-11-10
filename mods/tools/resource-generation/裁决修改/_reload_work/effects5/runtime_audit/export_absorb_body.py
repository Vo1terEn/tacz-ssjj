"""Independent E009-mounted E015 hit visual data. Never writes the parent body config."""
from pathlib import Path
import json,copy,shutil,hashlib
import numpy as np
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];ASSET=HERE.parent/'asset_audit';OUT=HERE/'absorb_body';OUT.mkdir(exist_ok=True)
def main():
 source=json.loads((ASSET/'source_native_body.json').read_text(encoding='utf8'))
 world=json.loads((HERE/'resources/assets/judgfire/fx/world_native.json').read_text(encoding='utf8'))
 recovered=json.loads((OUT/'8b17c2efc642a62185cd827bcc1eb1b1_3788691243272821142.json').read_text(encoding='utf8'))
 C=np.array(json.loads((ROOT/'_reload_work/audit/conversion_validation.json').read_text(encoding='utf8'))['conversion']);Y=np.diag([1.,-1.,1.,1.]);F=np.diag([-1.,1.,1.,1.]);B=np.array(source['anchors']['WeaponAnchor']['rest_world']);convert=Y@C@B@F@np.linalg.inv(B)
 mats=[];matindex={};groups=[];emitters=[];provenance=[]
 resource=OUT/'resources/assets/judgfire';td=resource/'textures/fx/reset5_absorb';td.mkdir(parents=True,exist_ok=True)
 def material(key):
  if key in matindex:return matindex[key]
  m=copy.deepcopy(next(m for m in world['materials'] if m.get('key')==key));m['source_key']=key;m['alpha']=m.get('source_floats',{}).get('_Alpha',1)
  m['shaderKind']='dynamic_mask' if m['params'][0]==4 else 'simple'
  for i,rl in enumerate(m['textures']):
   old=HERE/'resources/assets'/rl.replace(':','/',1);dst=td/old.name;shutil.copy2(old,dst);m['textures'][i]='judgfire:textures/fx/reset5_absorb/'+old.name
  matindex[key]=len(mats);mats.append(m);return len(mats)-1
 def scalar(v,default=0):
  if not v:return default
  return .5*(v.get('minScalar',v.get('scalar',default))+v.get('scalar',default)) if v.get('minMaxState')==3 else v.get('scalar',default)
 def rng(v,default=0):return [v.get('minScalar',default),v.get('scalar',default)] if v.get('minMaxState')==3 else [scalar(v,default)]*2
 for n in source['nodes']:
  if '/[instance]/diguan_chouqu/' not in n['hierarchy'] or not n.get('particle_system_raw') or not n.get('material_keys'):continue
  raw=n['particle_system_raw'];r=n['renderer_raw'];initial=raw['InitialModule'];samples=n['particle_samples'];W=convert@np.array(n['rest_world']);mi=material(n['material_keys'][0]);size=[scalar(initial.get('startSize'),1)]*3
  if initial.get('size3D'):size=[scalar(initial.get(k),1) for k in ['startSize','startSizeY','startSizeZ']]
  size_ranges=[rng(initial.get(k),1) for k in (['startSize','startSizeY','startSizeZ'] if initial.get('size3D') else ['startSize']*3)];size_min=[a[0] for a in size_ranges];size_max=[a[1] for a in size_ranges]
  cc=samples.get('ColorModule.gradient',{}).get('min_max_rgba',[[[1,1,1,1]]*2]*65) if raw.get('ColorModule',{}).get('enabled') else [[[1,1,1,1]]*2]*65
  colors=[[(a+b)/2 for a,b in zip(pair[0],pair[1])] for pair in cc];sizes=samples.get('SizeModule.curve',{}).get('min_max',[[1,1]]*65) if raw.get('SizeModule',{}).get('enabled') else [[1,1]]*65
  custom=[[sum(samples.get(f'CustomDataModule.vector0_{k}',{}).get('min_max',[[0,0]]*65)[i])/2 for k in range(4)] for i in range(65)] if raw.get('CustomDataModule',{}).get('enabled') else None
  col=initial.get('startColor',{}).get('maxColor',{});start=[col.get(k,1) for k in 'rgba'];euler=[]
  for a in ['startRotationX','startRotationY','startRotation']:euler+=rng(initial.get(a),0) if a=='startRotation' or initial.get('rotation3D') else [0,0]
  angular=None;rm=raw.get('RotationModule') or {}
  if rm.get('enabled'):
   axes=[samples.get('RotationModule.'+a,{}).get('min_max',[[0,0]]*65) if a=='curve' or rm.get('separateAxes') else [[0,0]]*65 for a in ['x','y','curve']];angular=[sum((axes[a][i] for a in range(3)),[]) for i in range(65)]
  common=dict(name='native_absorb_'+n['name'].replace(' ','_'),material=mi,bone=0,life=scalar(initial.get('startLifetime'),1),lifeRange=rng(initial.get('startLifetime'),1),rate=scalar(raw['EmissionModule'].get('rateOverTime')),max=initial['maxNumParticles'],alpha=start[3],startColor=start,colors=colors,sizes=sizes,custom=custom,trigger='hit',hitOnly=True,sizeXYZMin=size_min,sizeXYZ=size_max,sizeRange=rng(initial.get('startSize'),1),speedRange=rng(initial.get('startSpeed')),delay=scalar(raw.get('startDelay')),duration=raw['lengthInSec'],triggerDuration=raw['lengthInSec'],loop=False,initialEuler=euler,angularVelocity=angular,source_node=n['id'],source_hierarchy=n['hierarchy'],source_particle_raw=raw,source_renderer=r,source_rest_world=n['rest_world'],source_static_world_yup=W.tolist())
  if r['m_RenderMode']==4:
   vertices=np.array(recovered['vertices'])*size;vertices=np.c_[vertices,np.ones(len(vertices))]@W[:3].T
   group=dict(**common,nativeLocalVertices=recovered['vertices'],localToModel=W.tolist(),sourceShape=raw.get('ShapeModule'),vertices=vertices.tolist(),uv=recovered['uv0'],faces=recovered['triangles'],vertexColors=recovered.get('colors'),center=W[:3,3].tolist(),particleBasis=W[:3,:3].tolist(),particleScale=[1,1,1],size3D_baked=size,restored_mesh_source='E080 bcb175 explicit pointer3788691243272821142 in shared8b17; matches E088 and f76c explicit mesh exactly')
   groups.append(group)
  else:
   uv=raw.get('UVModule',{});vel=raw.get('VelocityModule',{});shape=raw.get('ShapeModule',{})
   emitter=dict(**common,position=W[:3,3].tolist(),axisX=W[:3,0].tolist(),axisY=W[:3,1].tolist(),axisZ=W[:3,2].tolist(),size=size_max,sizeMin=size_min,speed=scalar(initial.get('startSpeed')),velocity=[scalar(vel.get(a)) for a in 'xyz'] if vel.get('enabled') else [0,0,0],shape=dict(enabled=shape.get('enabled',False),type=shape.get('type',0),radius=shape.get('radius',{}).get('value',0),box=[shape.get('m_Scale',{}).get(k,1) for k in 'xyz'],position=[shape.get('m_Position',{}).get(k,0) for k in 'xyz'],rotation=[shape.get('m_Rotation',{}).get(k,0) for k in 'xyz'],angle=shape.get('angle',0),length=shape.get('length',0)),sheet=[uv.get('tilesX',1),uv.get('tilesY',1)] if uv.get('enabled') else [1,1],renderMode=r['m_RenderMode'],renderAlignment=r.get('m_RenderAlignment',0),rotation=scalar(initial.get('startRotation')),lengthScale=r.get('m_LengthScale',1),velocityScale=r.get('m_VelocityScale',0),mouthOnly=False)
   emitters.append(emitter)
  provenance.append(dict(node=n['name'],source_node=n['id'],life=common['life'],emission_duration=common['duration'],size=size,center=W[:3,3].tolist(),initial_euler_radians_minmax=euler,rotation_module=rm))
 proof=dict(source_gun='E009 source WeaponAnchor/weapon_particle_effect; mount Euler0/position0/scale1',source_quality_graph='particle_logic_56395c0baabd: View/Third visible only EffectLevel&(1<<3)>0; Scene/Lobby hidden',source_event_graph='effectConfig and particleEffectConfig do not contain an absorb/hit trigger',trigger_inference='Existing confirmed Shot.hit and uniquely identified CrownStrike arrival drive a short gun-space absorb effect, consistent with current one-ammo refund; no new gameplay or network packet.',mesh_recovery='E015 current Mesh0; E080 bcb explicit3788691243272821142 recovered from8b17. E015 and E080 PS differ only GO pointer; TRS and MainTexSHA2ddd17ef...same. Recovered63vertices/80triangles matches E088 explicit4696303625222024354 and f76c E080 explicit-4326907822231352779 in positions/UV/faces exactly. One other8b17 same-ID FBX is100x larger and explicitly excluded.',coordinate='Yflip @ C @ WeaponAnchorRest @ F_X @ inverse(WeaponAnchorRest) @ embeddedNodeRest, model units Y-up; apply OriginalMotion bone0 delta and model /16 once. No e01d body90 mount override.',parameters='E009 embedded PS fields exactly equal standalone E015. Initial startSize is a minmax birth range: primary .15..25, shan2 .1..25, shan3 1..5. The previous compact .2/.175/3 values were their means, not another authored variant. Group sizeXYZ and emitter size keep maxima with sizeXYZMin/sizeMin/sizeRange available for one uniform birth random factor. Exact source raw fields remain per node.',provenance=provenance)
 maxlife=lambda n:max(n.get('lifeRange')or[n['life']])
 proof['lifecycle_window']='Window uses maximum authored birth lifetime, not compact mean: shan emitters lifeRange=[1,2] seconds plus emission duration1 => maximum3 seconds after confirmed hit. NativeEmitterClock consumes the per-birth lifetime range.'
 data=dict(version=1,max_life_seconds=max(maxlife(n)for n in groups+emitters),max_trigger_window_seconds=max(maxlife(n)+n['delay']+n['duration'] for n in groups+emitters),coordinate_note=proof['coordinate'],materials=mats,groups=groups,emitters=emitters,proof=proof)
 (OUT/'absorb_native.json').write_text(json.dumps(data,separators=(',',':')),encoding='utf8');(OUT/'absorb_source_proof.json').write_text(json.dumps(proof,indent=2),encoding='utf8')
 print(json.dumps(dict(groups=len(groups),emitters=len(emitters),materials=len(mats),primary_bounds=[np.min(groups[0]['vertices'],0).tolist(),np.max(groups[0]['vertices'],0).tolist()],output=str(OUT/'absorb_native.json')),ensure_ascii=False))
if __name__=='__main__':main()
