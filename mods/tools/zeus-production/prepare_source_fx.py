"""Convert supplied Zeus projectile/impact/chain particles and original WAV only.

Private SSJJ particle runtime is absent. Every source component, transform,
material, texture and explicit Minecraft parameter adaptation is recorded.
"""
from pathlib import Path
import hashlib,importlib.util,json,math,subprocess,shutil
import numpy as np
import UnityPy
from scipy.spatial.transform import Rotation
from UnityPy.helpers.MeshHelper import MeshHandler
from prepare import W,ROOT,save,sha

spec=importlib.util.spec_from_file_location('family_particle_math',ROOT/'work/zeus_family_20261114/scripts/import_family_body_fx.py')
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
s,d=f.s,f.d
SRC=ROOT/'宙斯、聚能/宙斯之手';AS=W/'pack/assets/ssjj_zeus';INPUTS={};TEXTURES={};SEED=0x20261114;N=257
def raw(p):
 p=Path(p);b=p.read_bytes();INPUTS[p.relative_to(ROOT).as_posix()]=hashlib.sha256(b).hexdigest();return b
def read(p):return json.loads(raw(p).decode('utf-8-sig'))
def xyz(v):return np.array([v[k]for k in'xyz'],float)
def rgba(v):return np.array([v[k]for k in'rgba'],float)
def compact(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':'),allow_nan=False)+'\n','utf8')
def catalog(gun,skin=False):
 cat='武器皮肤'if skin else'武器';base=next((SRC/'完整源素材'/cat).glob('*__'+gun));rig=base/gun
 ids={o['path_id']:o for o in read(rig/'assembly.json')['objects']};mats={o['path_id']:o for o in read(base/(gun+'$2')/'assembly.json')['objects']}
 bundle=next((SRC/'原始资源包'/('weaponskin'if skin else'weapon')).glob(gun+'$2_*'))
 env=UnityPy.load(raw(bundle));meshes={o.path_id:o for o in env.objects if o.type.name=='Mesh'}
 custom={int(p.stem):read(p)for p in (SRC/f'特效及材质参数/{cat}/{gun}/MonoBehaviour').glob('*.json')}
 ts={i:o['fields']for i,o in ids.items()if o['type']=='Transform'};gt={v['m_GameObject']['m_PathID']:i for i,v in ts.items()}
 return dict(gun=gun,rig=rig,ids=ids,mats=mats,meshes=meshes,custom=custom,ts=ts,gt=gt)
def export(c,group_name,kind,outname):
 ids,cs,ts,gt=c['ids'],c['custom'],c['ts'],c['gt']
 choices=sorted((i,v)for i,v in cs.items()if'ssjjpaList'in v and ids[v['m_GameObject']['m_PathID']]['name']==group_name)
 assert choices,(c['gun'],group_name);gid,group=choices[0]
 configs=[(i,v)for i,v in cs.items()if'instance'in v and v['instance']['m_PathID']==group['m_GameObject']['m_PathID']]
 if not configs:
  linked=[(i,v)for i,v in cs.items()if'instance'in v and any(v['instance']['m_PathID']==g['m_GameObject']['m_PathID']for _,g in choices)]
  if linked:
   configid,conf=sorted(linked)[0];gid,group=next((i,g)for i,g in choices if g['m_GameObject']['m_PathID']==conf['instance']['m_PathID']);configs=[(configid,conf)]
 root=ts[gt[group['m_GameObject']['m_PathID']]];config=None
 if configs:
  configid,config=sorted(configs)[0];e=config['LocalEulerAngles'];q=Rotation.from_euler('zxy',[e['z'],e['x'],e['y']],degrees=True).as_quat();base=s.trs(config['LocalPosition'],q,config['LocalScala'])
 else:base=s.trs(root['m_LocalPosition'],root['m_LocalRotation'],root['m_LocalScale'])
 emitters=[];traces=[];skipped=[];duration=0
 for ref in group['ssjjpaList']:
  oid=ref['m_PathID'];v=cs[oid];name=ids[v['m_GameObject']['m_PathID']]['name'];t=ts[gt[v['m_GameObject']['m_PathID']]]
  # Zero-life/alpha is a private runtime placeholder, never substitute new art.
  if v['lifeTime']['oneDType']==0 and v['lifeTime']['_value']<=0:
   skipped.append({'id':oid,'name':name,'reason':'source zero lifetime and alpha/size; private LineLink placeholder','source_definition':v,'source_transform':t});continue
  mat=c['mats'][v['mater']['m_PathID']];props=mat['fields']['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];floats=dict(props['m_Floats']);colors=dict(props['m_Colors'])
  assert floats.get('_BlendDst',1)in(1,10) and (floats.get('_ZWrite',0)==0 or group_name=='jingti' and floats.get('_ZWrite')==1),(name,floats)
  png=next(c['rig'].rglob('*__'+str(env['m_Texture']['m_PathID'])+'.png'));b=raw(png);h=hashlib.sha256(b).hexdigest();dest=AS/f'textures/effects/{h[:20]}.png';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
  TEXTURES[dest.relative_to(W).as_posix()]={'source':png.relative_to(ROOT).as_posix(),'sha256':h,'conversion':'original exported PNG bytes unchanged'}
  m=s.J@base@s.trs(t['m_LocalPosition'],t['m_LocalRotation'],t['m_LocalScale']);linear=(m@s.J)[:3,:3];scale=np.linalg.norm(linear,axis=0);rot=linear/np.maximum(scale,1e-20);u,_,vh=np.linalg.svd(rot);rot=u@vh
  mode='mesh'if floats.get('_Billboard',1)==0 else'billboard';geometry=None;mid=v['mesh']['m_PathID']
  if mode=='mesh':
   if mid==10210:p=np.array([[-.5,-.5,0],[.5,-.5,0],[-.5,.5,0],[.5,.5,0]]);uv=np.array([[0,0],[1,0],[0,1],[1,1]]);tri=np.array([[0,2,1],[2,3,1]])
   else:
    mh=MeshHandler(c['meshes'][mid].read());mh.process();p=np.asarray(mh.m_Vertices,float);uv=np.asarray(mh.m_UV0,float);tri=np.asarray(mh.get_triangles()[0],int)
   geometry={'positions':(p@s.J[:3,:3]*.01).tolist(),'uv':(uv*[1,-1]+[0,1]).tolist(),'triangles':tri[:,[0,2,1]].tolist()}
  tint=2*rgba(colors['_TintColor'])*rgba(colors['_DyeingColor']);rng=np.random.default_rng(SEED^(oid&0xffffffff));particles=[];maxend=0
  for index in range(v['ParticleNumber']):
   frac=index/max(v['ParticleNumber']-1,1);life=d.oned(v['lifeTime'],frac,rng);birth=d.oned(v['startTime'],frac,rng);assert life>0 and birth>=0
   maxend=max(maxend,birth+life);position=np.zeros(3);direction=np.zeros(3);shape=v['shape'];st=shape['shapeType']
   if v['RandomOnBorn']:
    if st==0:
     lo=np.minimum(xyz(shape['_rectFrom']),xyz(shape['_rectTo']));hi=np.maximum(xyz(shape['_rectFrom']),xyz(shape['_rectTo']));position=rng.uniform(lo,hi);direction=position-(lo+hi)/2
    elif st==1:
     theta=rng.uniform(0,math.tau);radius=shape['_radius']*(1 if shape.get('_shell')else math.sqrt(rng.random()));position=np.array([math.cos(theta)*radius,math.sin(theta)*radius,rng.uniform(0,shape['_length'])]);a=math.radians(shape['_angle']);direction=np.array([math.cos(theta)*math.sin(a),math.sin(theta)*math.sin(a),math.cos(a)])
    elif st==2:
     theta=rng.uniform(0,math.tau);radius=rng.uniform(min(shape['_radiusSmall'],shape['_radiusBig']),max(shape['_radiusSmall'],shape['_radiusBig']));position=np.array([math.cos(theta)*radius,math.sin(theta)*radius,rng.uniform(0,shape['_height'])]);direction=position.copy()
    elif st==3:
     direction=rng.normal(size=3);direction=s.unit(direction);position=direction*rng.uniform(min(shape['_radiusSmall'],shape['_radiusBig']),max(shape['_radiusSmall'],shape['_radiusBig']))
    else:raise ValueError(('unsupported source emission shape',st))
   if not v['directionByShape']:direction=xyz(v['direction']['value'])
   direction=s.unit(direction);speed=d.oned(v['velocity'],frac,rng);angle=d.oned(v['rot'],frac,rng);spin=d.oned(v['rotvelocity'],frac,rng);size=np.array([d.oned(v['size'+a],frac,rng)for a in'XYZ']);size[:]=size[0]if v['uniformScale_']else size
   force=s.unit(xyz(v['_forceDir']))*v['_force']if v['hasForceEffector']else np.zeros(3);alpha=d.oned(v['alpha'],frac,rng);frames=[]
   for phase in np.linspace(0,1,N):
    age=phase*life;local=position+direction*speed*age+.5*force*age*age
    if v['hasGravityEffector']:local+=.5*xyz(v['gravityDir'])*v['gravity']*age*age
    center=(m@np.r_[local,1])[:3]*.01;sz=size.copy()
    if v['hasSizeEffector']:sz*=d.interp(phase,v['_keyFrameLifeTime'],v['_sizeX'])if v['uniformScale_']else np.array([d.interp(phase,v['_keyFrameLifeTime'],v['_size'+a])for a in'XYZ'])
    sz*=scale
    if mode=='mesh':axis=np.zeros(3);axis[int(v['RotateAxis'])]=math.radians(angle+spin*age);q=(Rotation.from_matrix(rot)*Rotation.from_rotvec(s.J[:3,:3]@axis*-1)).as_quat()
    else:sz=np.array([sz[2],sz[1],1])*.01;q=Rotation.from_euler('z',angle+spin*age,degrees=True).as_quat()
    col=np.clip(d.source_curve_color(v,phase)*tint*np.array([1,1,1,alpha]),0,1);us,vs=env['m_Scale']['x'],env['m_Scale']['y'];uo,vo=env['m_Offset']['x'],env['m_Offset']['y']
    if v['hasSpriteSheetEffector']:
     assert not v['randomFrame']and not v['lifeDuration'];rows=max(1,v['rows']);cols=max(1,v['columns']);frame=min(int(phase*rows*cols),rows*cols-1);us/=cols;vs/=rows;uo+=(frame%cols)/cols;vo+=(frame//cols)/rows
    if v['hasUVEffector']:uo+=d.interp(phase,v['_uvkeyFrameLifeTime'],v['_u'],0);vo-=d.interp(phase,v['_uvkeyFrameLifeTime'],v['_v'],0)
    frames.append(np.round(np.r_[center,sz,q,col,us,vs,uo,vo],9).tolist())
   particles.append({'birth':birth,'life':life,'frames':frames})
  duration=max(duration,maxend);em={'name':name,'texture':f'ssjj_zeus:textures/effects/{h[:20]}.png','mode':mode,'render':'additive'if floats.get('_BlendDst',1)==1 else'alpha','duration':maxend if kind=='orb'else 60,'geometry':geometry,'particles':particles}
  if mode=='billboard'and v['billboardType']==1:em['billboard_axis']=s.unit(m[:3,1]).tolist()
  emitters.append(em);traces.append({'name':name,'component_id':oid,'source_definition':v,'source_transform':t,'matrix':m.tolist(),'material':mat,'texture':png.relative_to(ROOT).as_posix(),'source_mesh_id':mid,'output_mode':mode,'source_end':maxend})
 out=AS/f'effects/thunder/{outname}.json';compact(out,{'schema':1,'gun':'ssjj_zeus:'+outname,'bone':'world','emitters':emitters})
 return {'resource':f'ssjj_zeus:effects/thunder/{outname}.json','duration':duration,'source_gun':c['gun'],'group':group_name,'group_id':gid,'source_group_duration':group['duration'],'source_group':group,'source_config':config,'root_transform':root,'traces':traces,'skipped':skipped,'duplicate_group_ids':[i for i,_ in choices],'output_sha256':sha(out)}
def audio():
 events={};reports=[]
 for tier in [1,2]:
  gun='hand_of_zeuss'+str(tier)
  for purpose in ['sec_fire','skill']:
   wav=SRC/f'音效/{gun}/WAV/{gun}_{purpose}.wav';raw(wav);name=f'thunder/{gun}_{purpose}';ogg=AS/f'sounds/{name}.ogg';ogg.parent.mkdir(parents=True,exist_ok=True)
   subprocess.run(['ffmpeg','-nostdin','-y','-v','error','-i',str(wav),'-map_metadata','-1','-c:a','libvorbis','-q:a','5',str(ogg)],check=True)
   events[name]={'sounds':[{'name':'ssjj_zeus:'+name,'stream':False}]};reports.append({'source':wav.relative_to(ROOT).as_posix(),'sha256':sha(wav),'output':ogg.relative_to(W).as_posix(),'output_sha256':sha(ogg),'binding':'adaptation by original sec_fire/skill names; source C# trigger mapping unavailable','conversion':'WAV to Vorbis only; no cutting, remixing, gain, filters or synthesis'})
  # TaCZ has a separate sound loader and directory; same original conversion bytes.
  tc=AS/f'tacz_sounds/{gun}/sec_fire.ogg';tc.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(AS/f'sounds/thunder/{gun}_sec_fire.ogg',tc)
  for suffix in ['', '_lava','_galaxy']:
   rel=f'assets/ssjj_zeus/display/guns/{gun}{suffix}_display.json';path=W/'pack'/rel;source=path if path.exists()else W/'baseline_assets'/rel;display=json.loads(source.read_text('utf8'));display['sounds']['shoot']=f'ssjj_zeus:{gun}/sec_fire';display['sounds']['shoot_3p']=f'ssjj_zeus:{gun}/sec_fire';save(path,display)
 save(AS/'sounds.json',events);return reports
def main():
 cats={g:catalog(g)for g in ['hand_of_zeuss1','hand_of_zeuss2']};lava=catalog('skin_hand_of_zeuss_lava',True);exports={};profiles={}
 for gun,c in cats.items():
  own={}
  for kind,group in [('orb','11850_leiqiu_tuowei'),('impact','3_zidianbaozha_effect_1M'),('chain','shandianlian')]:
   key=gun+'_'+kind;exports[key]=export(c,group,kind,key);own[kind]=exports[key]
  # Tier I has no gandian group; it cannot activate the chain skill in gameplay.
  if gun.endswith('2'):key=gun+'_anchor';exports[key]=export(c,'gandian','anchor',key);own['anchor']=exports[key]
  else:own['anchor']=own['chain']
  profiles[gun]={k:{z:v[z]for z in ['resource','duration']}for k,v in own.items()};profiles[gun]['skill']='ssjj_zeus:thunder/'+gun+'_skill'
  profiles[gun+'_galaxy']=dict(profiles[gun])
 lavaown={}
 for kind,group in [('orb','huoqiu'),('impact','huoqiu_effect_1M'),('chain','huoyanlian'),('anchor','gandian')]:
  key='lava_'+kind;exports[key]=export(lava,group,kind,key);lavaown[kind]={z:exports[key][z]for z in ['resource','duration']}
 for gun in cats:profiles[gun+'_lava']={**lavaown,'skill':'ssjj_zeus:thunder/'+gun+'_skill'}
 save(AS/'effects/thunder/profiles.json',{'profiles':profiles});aud=audio();raw(Path(__file__))
 save(W/'evidence/source_fx_audit.json',{'passed':True,'exports':exports,'profiles':profiles,'textures':TEXTURES,'audio':aud,'source_input_sha256':INPUTS,'adaptations_and_limits':['Source private UserNewParticle DLL and HandOfZeus C# runtime are not supplied. This is source parameter adaptation, not a pixel-exact source engine claim.','Source transforms, original embedded mesh topology/UV, original PNG bytes, tint/additive/depth material and curves retained; Unity external primitive10210 reconstructed as its standard Quad.','Source random ranges sampled with fixed seed; original RNG/distribution unknown. Shapes interpreted as serialized box/cone/cylinder/radial emission bounds, force as acceleration.','One-shot source birth/life retained without repeating; playback lasts until final original particle death rather than truncating at serialized editor duration. Moving orb original per-emitter cycles repeat.','linelink_66/100 has zero-life/zero-alpha white cji placeholder; omitted. Original visible dian01/xian/skin emitters are sampled and their source geometry/texture stretched between gameplay endpoints with one affine mapping. No random arc or invented lightning texture. Exact private LineLink endpoint algorithm unavailable.','lava source exports repeated equivalent-looking groups without public per-tier trigger binding; one original configured group is shared by eligible tiers, no color recolor. Galaxy source contains no projectile/impact/chain/anchor groups: same-tier original standard effects reused.','sec_fire and skill event assignment follows supplied names as explicit Minecraft adaptation; no authoritative source C# audio trigger mapping. Impact has no proven source sound and is silent. No vanilla explosion sound.','Source config tables contain no verified chain attack radius. Gameplay3-block per-hop radius is an adaptation, not a recovered source value.','No live game visual/sound verification is claimed.']})
 print(json.dumps({'passed':True,'exports':len(exports),'profiles':len(profiles),'original_textures':len(TEXTURES),'original_sounds':len(aud),'source_emitters':sum(len(x['traces'])for x in exports.values())}))
if __name__=='__main__':main()
