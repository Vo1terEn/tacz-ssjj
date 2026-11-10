"""Bake original lingyu_xialuo / lingyu_baozha and source skill-bar assets.

This is a source-derived Minecraft adaptation, not a Unity renderer replay.
No old generated gun geometry or named Java classes are dependencies.
"""
import os
from pathlib import Path
import json,math,hashlib,zipfile
import numpy as np
from scipy.spatial.transform import Rotation
from PIL import Image,ImageDraw,ImageFont

W=Path(__file__).resolve().parent
ROOT=W.parents[1]
SOURCE=ROOT/'次元之星/特效待确认_全量目录'
AS=W/'resources/assets/ssjj_dimension'
CARDS={'tier1':('E029','E028'),'tier1_frozen':('E135','E134'),'tier1_neon':('E223','E222'),'tier1_royal':('E311','E310'),'tier2':('E063','E062'),'tier2_frozen':('E159','E158'),'tier2_neon':('E247','E246'),'tier2_royal':('E335','E334')}
J=np.diag([-1.,1.,1.,1.]);N=65;SEED=0x20261108

def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def xyz(d):return np.array([d[k]for k in 'xyz'],float)
def unit(v):return v/max(np.linalg.norm(v),1e-20)
def rgb(v):return np.array([(v>>16)&255,(v>>8)&255,v&255],float)/255
def rgba(v):return np.array([v[k]for k in'rgba'])
def interp(t,x,y,default=1):return float(np.interp(t,x,y))if len(x)else default
def one(d,f,rng):
 t=d['oneDType']
 if t==0:return d['_value']
 if t==1:
  anchors=d['_anchors'];return (anchors[0]['_value']if anchors and f>=anchors[-1]['_time']else interp(f,[a['_time']for a in anchors],[a['_value']for a in anchors]))*d['_maxCurveAnchorValue']
 if t==2:return rng.uniform(min(d['_min'],d['_max']),max(d['_min'],d['_max']))
 raise ValueError(('unsupported source curve',d))
def mesh(d):
 data=d['m_VertexData'];channels=data['m_Channels'];count=data['m_VertexCount'];raw=bytes.fromhex(data['m_DataSize']['__bytes_hex__'])
 formats={0:'<f4',1:'<f2',2:'u1',3:'i1',4:'<u2',5:'<i2',6:'u1',7:'i1',8:'<u2',9:'<i2',10:'<u4',11:'<i4'};strides={}
 for c in channels:
  n=c['dimension']&15
  if n:strides[c['stream']]=max(strides.get(c['stream'],0),c['offset']+n*np.dtype(formats[c['format']]).itemsize)
 starts={};offset=0
 for s,size in sorted(strides.items()):offset=(offset+15)//16*16;starts[s]=offset;offset+=count*size
 assert offset==len(raw)
 def ch(i):
  c=channels[i];dt=np.dtype(formats[c['format']]);return np.ndarray((count,c['dimension']&15),dtype=dt,buffer=raw,offset=starts[c['stream']]+c['offset'],strides=(strides[c['stream']],dt.itemsize)).copy()
 positions=ch(0)@J[:3,:3]*.01;uv=ch(4)*[1,-1]+[0,1]
 faces=np.frombuffer(bytes(d['m_IndexBuffer']),dtype='<u2'if d['m_IndexFormat']==0 else'<u4').reshape(-1,3)[:,[0,2,1]]
 return {'positions':positions.tolist(),'uv':uv.tolist(),'triangles':faces.astype(int).tolist()}
def build():
 records=[];inputs={};textures={};profiles={}
 # The restoration is deliberately limited to tier2's original/default art.
 # Keep previous tier1 and skin-compatible tier2 tables byte-for-byte available.
 legacy={}
 with zipfile.ZipFile(W/'baseline/mod.jar')as archive:
  for name in archive.namelist():
   prefix='assets/ssjj_dimension/'
   if name.startswith(prefix+'textures/effects/field/')and name.endswith('.png'):
    target=W/'resources'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(name))
  for tier in ['tier1','tier2']:
   for kind in ['field','impact']:
    old=f'assets/ssjj_dimension/effects/field/{tier}_{kind}.json';new=old.replace(tier+'_',tier+'_legacy_');old=new if new in archive.namelist()else old;raw=archive.read(old);target=W/'resources'/new;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);legacy[new]={'source':old,'sha256':hashlib.sha256(raw).hexdigest()}
 for profile,codes in CARDS.items():
  for kind,code in zip(['field','impact'],codes):
   p=SOURCE/f'详情/{code}.json';raw=p.read_bytes();inputs[str(p.relative_to(ROOT))]=hashlib.sha256(raw).hexdigest();doc=json.loads(raw)
   obs={o['id']:o['data']for o in doc['objects']if'data'in o};names={o['id']:o['name']for o in doc['objects']}
   def tx(t):
    m=np.eye(4);m[:3,:3]=Rotation.from_quat([t['m_LocalRotation'][k]for k in'xyzw']).as_matrix()@np.diag(xyz(t['m_LocalScale']));m[:3,3]=xyz(t['m_LocalPosition']);parent=t['m_Father']['m_PathID']
    return tx(obs[parent])@m if parent else m
   emitters=[];traces=[];maxend=0;controller=next(v for v in obs.values()if 'ssjjpaList'in v)
   texture_by_id={s['id']:t for t in doc['card']['textures']for s in t['sources']}
   for o in doc['objects']:
    v=o.get('data',{})
    if 'ParticleNumber'not in v:continue
    go=obs[v['m_GameObject']['m_PathID']];name=go['m_Name'];rng=np.random.default_rng(SEED^(o['id']&0xffffffff))
    t=next(obs[x['component']['m_PathID']]for x in go['m_Component']if'm_LocalPosition'in obs[x['component']['m_PathID']])
    m=J@tx(t);proper=m@J;linear=proper[:3,:3];scale=np.linalg.norm(linear,axis=0);rotation=linear/np.maximum(scale,1e-20);u,_,vh=np.linalg.svd(rotation);rotation=u@vh
    mat=obs[v['mater']['m_PathID']];props=mat['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];source_tex=texture_by_id[env['m_Texture']['m_PathID']]
    texfile=SOURCE/source_tex['image'];texbytes=texfile.read_bytes();digest=hashlib.sha256(texbytes).hexdigest();inputs[str(texfile.relative_to(ROOT))]=digest
    target=AS/f'textures/effects/field/{digest[:20]}.png';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(texbytes);textures[target.relative_to(W/'resources').as_posix()]=digest
    tint=2*rgba(dict(props['m_Colors'])['_TintColor'])*rgba(dict(props['m_Colors'])['_DyeingColor']);blend=dict(props['m_Floats']).get('_BlendDst',1)
    geometry=None
    if v['billboardType']==4 and v['mesh']['m_PathID']==10210:
     # Unity built-in Quad is one unit in XY. Preserve the prefab's floor-facing
     # rotation instead of erroneously treating this mesh as a camera billboard.
     geometry={'positions':[[-.005,-.005,0],[.005,-.005,0],[.005,.005,0],[-.005,.005,0]],'uv':[[0,1],[1,1],[1,0],[0,0]],'triangles':[[0,1,2],[0,2,3]]}
    elif v['mesh']['m_PathID']!=10210:
     meshid=v['mesh']['m_PathID']
     if meshid in obs:meshdata=obs[meshid]
     else:
      # The detail card intentionally omits binary mesh payload; resolve its exact PathID in original exports.
      candidates=sorted((ROOT/'次元之星').rglob(f'*__{meshid}.json'))
      assert candidates,('Missing original mesh',meshid)
      meshes=[(mp,mp.read_bytes())for mp in candidates]
      assert len({hashlib.sha256(raw).hexdigest()for _,raw in meshes})==1,('Conflicting source mesh PathID',meshid)
      mp,meshraw=meshes[0];inputs[str(mp.relative_to(ROOT))]=hashlib.sha256(meshraw).hexdigest();meshdata=json.loads(meshraw)
     geometry=mesh(meshdata)
    particles=[];bounds=[]
    for i in range(v['ParticleNumber']):
     f=(i+1)/max(v['ParticleNumber'],1);birth=one(v['startTime'],f,rng);life=one(v['lifeTime'],f,rng);assert birth>=0 and life>0
     maxend=max(maxend,birth+life);sh=v['shape'];position=np.zeros(3);direction=np.zeros(3)
     if sh['shapeType']==0:
      lo=xyz(sh['_rectFrom']);hi=xyz(sh['_rectTo']);position=rng.uniform(np.minimum(lo,hi),np.maximum(lo,hi));direction=unit(position)
     elif sh['shapeType']==2:
      theta=rng.uniform(0,math.tau);rad=rng.uniform(sh['_radiusSmall'],sh['_radiusBig']);position=np.array([-math.sin(theta)*rad,rng.uniform(-sh['_height'],sh['_height']),math.cos(theta)*rad]);direction=np.array([0.,0.,1.])
     elif sh['shapeType']==3:
      # Source spherical shell/ring shape. Fixed random seed replaces private engine RNG.
      theta=rng.uniform(0,math.tau);latitude=rng.uniform(math.pi/2,math.pi*1.5);rad=rng.uniform(min(sh['_radiusSmall'],sh['_radiusBig']),max(sh['_radiusSmall'],sh['_radiusBig']));position=np.array([-rad*math.cos(latitude)*math.sin(theta),rad*math.sin(latitude),rad*math.cos(latitude)*math.cos(theta)]);direction=unit(position)
      # A zero-radius source point has no outward shape normal. Giving it an
      # arbitrary unit vector creates spurious multi-block travel in neon's chanrao.
      if rad<1e-8:direction=np.zeros(3)
     elif sh['shapeType']==1:
      theta=rng.uniform(0,math.tau);rad=sh['_radius']if sh['_shell']else sh['_radius']*rng.random();direction=unit(np.array([-math.sin(theta)*sh['sinAngle'],sh['cosAngle'],math.cos(theta)*sh['sinAngle']]));position=np.array([-math.sin(theta)*rad,0,math.cos(theta)*rad]);
      if sh['_volum']and abs(direction[1])>1e-8:position+=direction*(sh['_length']/direction[1]*rng.random())
     else:raise ValueError(('source shape',sh))
     if not v['directionByShape']:direction=xyz(v['direction']['value'])
     speed=one(v['velocity'],f,rng);base_rot=one(v['rot'],f,rng);spin=one(v['rotvelocity'],f,rng);size=np.array([one(v['size'+a],f,rng)for a in'XYZ']);basealpha=one(v['alpha'],f,rng)
     if v['uniformScale_']:size[:]=size[0]
     coldef=v['color'];col=rgb(coldef['_value'])if coldef['colorType']==0 else rgb(coldef['_color0'])*(1-f)+rgb(coldef['_color1'])*f
     frames=[]
     for phase in np.linspace(0,1,N):
      age=phase*life;local=position+direction*speed*age
      if v['hasForceEffector']:local+=.5*xyz(v['_forceDir'])*v['_force']*age*age
      if v['hasGravityEffector']:
       src_rotation=J[:3,:3]@rotation@J[:3,:3];local+=Rotation.from_matrix(src_rotation).inv().apply(xyz(v['gravityDir']))*v['gravity']*age*age
      center=(m@np.r_[local,1])[:3]*.01;sizes=size.copy()
      if v['hasSizeEffector']:
       # uniformScale_ applies to the initial size only. The source deliberately
       # stretches Y up to50 while X/Z shrink; collapsing these removes rain.
       sizes*=np.array([interp(phase,v['_keyFrameLifeTime'],v['_size'+a])for a in'XYZ'])
      sizes*=scale;angle=base_rot+spin*age
      if geometry and v['billboardType']==4:
       axis=np.zeros(3);axis[int(v['RotateAxis'])]=angle;q=(Rotation.from_matrix(rotation)*Rotation.from_rotvec(J[:3,:3]@axis*-1)).as_quat()
      elif geometry:q=Rotation.from_euler('z',angle).as_quat()
      else:sizes=np.array([sizes[2],sizes[1],1.])*.01;q=Rotation.from_euler('z',angle).as_quat()
      color=col.copy();alpha=basealpha
      if v['hasGradientColorEffector']:
       if not v['JustAlpha']and v['_colors']:
        vals=np.array([rgb(c)for c in v['_colors']]);color=np.array([interp(phase,np.array(v['_colorRatios'])/256,vals[:,j])for j in range(3)])
       alpha*=interp(phase,np.array(v['_alphaRatios'])/256,v['_alphas'])
      color=np.clip(np.r_[color,alpha]*tint,0,1)
      us,vs=env['m_Scale']['x'],env['m_Scale']['y'];uo,vo=env['m_Offset']['x'],env['m_Offset']['y']
      if v['hasSpriteSheetEffector']:
       rows=max(v['rows'],1);columns=max(v['columns'],1);us/=columns;vs/=rows
      if v['hasUVEffector']:uo+=interp(phase,v['_uvkeyFrameLifeTime'],v['_u'],0);vo-=interp(phase,v['_uvkeyFrameLifeTime'],v['_v'],0)
      frames.append(np.r_[center,sizes,q,color,us,vs,uo,vo].round(7).tolist());bounds.append(center)
     spin_axis=np.zeros(3);spin_axis[int(v['RotateAxis'])]=1;spin_axis=J[:3,:3]@spin_axis*-1
     particles.append({'birth':birth,'life':life,'frames':frames,'spinRate':spin,'spinAxis':spin_axis.tolist()})
    boundary='fazheng03_' in (names.get(env['m_Texture']['m_PathID'])or'')
    period=max(p['life']for p in particles)if kind=='field'else max(p['birth']+p['life']for p in particles)
    emitters.append({'name':name,'texture':f'ssjj_dimension:textures/effects/field/{digest[:20]}.png','render':'additive'if blend==1 else'alpha','mesh':geometry is not None,'billboard':v['billboardType'],'boundary':boundary,'period':period,'rows':v['rows']if v['hasSpriteSheetEffector']else 0,'columns':v['columns']if v['hasSpriteSheetEffector']else 0,'geometry':geometry,'particles':particles})
    traces.append({'name':name,'component':o['id'],'texture_sha256':digest,'source_catalog_texture_digest':source_tex['sha256'],'source_texture_name':names.get(env['m_Texture']['m_PathID']), 'source_mesh_mode':v['billboardType'],'particle_count':v['ParticleNumber'],'source_transform':tx(t).tolist(),'source_definition':v,'source_material':mat})
   data={'schema':1,'card':code,'restored':True,'loop':kind=='field','prewarm':1.25 if kind=='field'else 0,'sourceRadius':1.35,'duration':max(controller['duration'],maxend),'emitters':emitters}
   profiles[profile+'_'+kind]=data;save(AS/f'effects/field/{profile}_{kind}.json',data)
   records.append({'profile':profile,'kind':kind,'card':code,'source_prefab':doc['card']['path'],'source_bundle':doc['card']['occurrences'],'source_controller_duration':controller['duration'],'cycle_or_end_seconds':data['duration'],'emitters':traces})
 report={'passed':True,'source_cards':CARDS,'legacy_profiles':legacy,'input_sha256':inputs,'texture_sha256':textures,'records':records,
  'material_mapping':'Original textures copied byte-for-byte. Source _BlendDst1 -> SrcAlpha/One additive, _BlendDst10 -> alpha blending; fullbright, linear filtering, LEQUAL depth test, no depth write.',
  'coordinate_mapping':'Full prefab transforms, X reflection exactly once, source cm to Minecraft blocks via0.01. Entire source effect including mesh sampled in camera-local axes and rotated toward camera. Local particles attach to camera-facing surface of interpolated target body. Each mark scaled0.46, each burst0.45; up to5 independent layers in a compact0.24-block-radius ring. Persistent mark local depth compressed to5% so source emitters stay on the chest surface rather than sinking inside opaque target geometry; explosion source volume retained.',
  'retained':'All original emitters, texture materials, counts, lifetime/start/size/color/UV/sheet curves and source mesh geometry.',
  'adaptations':['Private UserNewParticle implementation unavailable; deterministic source-range sampling and piecewise-linear curves approximate the private runtime.','Source meshType4 including built-in Quad retains prefab rotation. Billboard0/1 face camera.','Shape cone uses source radius/length and sin/cos angle; sphere/cylinder/box use source ranges; exact private distributions unproven.','Gravity direction normalized before applying source scalar, matching vector direction semantics; private gravity convention unproven.','Source colorType2/3 uses birth-index interpolation between packed RGB endpoints.','Fields repeat each source emitter at its maximum birth+life to keep source looped ground circles visible throughout the requested3s; impacts remain one-shot.','The three original ground-circle Quad layers define a1.35-block radius; non-boundary decoration batches are radially fitted inside that circle at runtime.','Sprite sheets use discrete original rows/columns frames at runtime, separate from interpolated UV curves.','HUD originals copied byte-for-byte; source C prompt covered at runtime and actual configurable V binding rendered as text. Source layout bar ratio adapted to active15s / cooldown5s.','This is a source-derived Minecraft effect, not a pixel-exact Unity simulation.'], 'live_client_test':False}
 hud=build_hud();report['hud']=hud;report['hud_layout']={'source_canvas':[210,90],'source_bitmap':[210,90],'sprite_export_bitmap':[210,72],'sprite_crop_rect':[0,10,210,82],'bitmap_display_mapping':'Use original Texture2D PNG210x90 matching both ui_layout and uitextureconfig display210x90. The separately exported Sprite210x72 is exactly Texture2D.crop(0,10,210,82), with identical alpha and all visible RGBA pixels (transparent RGB padding differs). Preserve complete original canvas without stretching the trimmed Sprite.','source_bar':[77,61,125,7],'source_anchor':{'bottom':56,'right':386},'minecraft_anchor':'left of native TaCZ ammo HUD: right125, bottom24 GUI pixels; proportional scale capped0.65','source_prompt':'C','runtime_prompt':'actual configurable V binding','active_seconds':15,'cooldown_seconds':5};report['counts']={'prefabs':len(profiles),'textures':len(textures),'emitters':sum(len(d['emitters'])for d in profiles.values()),'particles':sum(len(e['particles'])for d in profiles.values()for e in d['emitters']),'hud_png':6};report['coordinate_mapping']='Full source prefab transforms and mesh X reflection, cm to blocks via0.01. Fixed cast point in world coordinates. Field3s, source circles remain horizontal, source ground radius1.35 maps to tier-I radius3 and tier-II radius5.';report['source_radius_blocks']=measure_radius(profiles)
 report['hud_chinese_material']={'confirmed':True,'original_text':'按C键 开启次元之力','sources':{tier:{name:info['source']for name,info in files.items()if name in ['activeBg','unactiveBg']}for tier,files in hud.items()},'reuse':'Original Chinese activeBg/unactiveBg Texture2D PNGs copied byte-for-byte; no generated replacement art. Previously approved baked V glyph and every surrounding source pixel are preserved from the frozen baseline.','search_scope':'Base tiers and frozen/neon/royal UI exports; all available activeBg/unactiveBg assets share the same corresponding original PNG hashes.','other_candidates':'crossProgressText is the Chinese Shift blink prompt; ui_jy_nova_shotgun*_name identifies the Chinese weapon name, not this skill. No better independent 次元领域 skill-name PNG found in exported UI assets.'}
 report['preview']={'font':'Microsoft YaHei','font_source':os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),'hud_font_pixels':12,'caption_font_pixels':14,'language':'zh_CN','headers':['次元领域 · 就绪','次元领域 · 高能','次元领域 · 冷却'],'prompts':['按 V 键开启高能','左键引爆 12.8秒 2/3','3.2秒后可用'],'boundary':'CPU triangle composite for source/layout inspection, not a game screenshot or Unity pixel-exact replay. Windows font is only used for the preview; production HUD uses Minecraft font and actual configured key.'}
 report['restoration']={'scope':'Both tiers and all three native skins; former legacy resources retained unused','field_radius':{'tier1':3,'tier2':5},'impact_radius':{'tier1':3,'tier2':5},'xz_mapping':'source ground1.35 to authoritative per-area radius, uniform XYZ mapping retains the video/source silhouette','root_causes':['Ignored5 real jiguang meshes because billboardType1 was mistaken for a standard Quad.','uniformScale applied to lifetime X/Y/Z curves collapsed50/30 Y streaks.','Cone was emitted alongZ instead of sourceY.','Source rot and rotvelocity radians were treated as degrees.','Emitter max birth+life looping introduced gaps; native loops each particle by life.','Per-batch radial fitting flattened source decoration.','Gravity direction was normalized and incorrectly halved.'],'native_semantic_reference':'work/tier_expansion/body11/src/cn/punishmenteye/MuzzleParticles.java:clock,shape,velocity,particleToView,velocityRotation (prior implementation checked against source DLL)','video':'次元之星/次元之星-次元领域-1.mp4','video_sha256':hashlib.sha256((ROOT/'次元之星/次元之星-次元领域-1.mp4').read_bytes()).hexdigest(),'video_frames':303,'video_fps':30,'phase_adaptation':'Field starts at source phase1.25 for immediately visible staggered rain as seen in the reference; each particle then repeats by its own life. Seeded emission values repeat deterministically; private engine per-rebirth randomness is not replayed.'}
 report['adaptations']=['Original E063/E062 textures, custom meshes, source counts and particle curves retained.','Source cone axisY, linear radius and volumetric length, gravity raw direction with source-world inverse emitter rotation, and radians follow the existing DLL-checked adapter.','Billboard1 custom meshes retain real geometry and face the camera around the world velocityY axis; ordinary billboard0 remains camera-facing.','Per-particle life clocks loop fields; ground spin continues across rebirth. Source startDelay is prewarmed1.25s for reference-video immediate visibility.','Uniform XYZ mapping preserves source/video proportions as ground radius1.35 expands to the per-packet tier-I radius3 or tier-II radius5. Impact uses the same server field center/radius/cylinder (tier-I radius3 and tier-II radius5 for original and all skins).','No radial fit or removal of source rain, flare or cylinder layers. Natural luminous decoration can spill beyond the ground/collision circle.','Random range sampling is deterministic rather than private engine RNG; velocity-aligned billboard frame approximates native mixed-space camera up around the vertical motion axis.','This is a source-derived Minecraft adaptation and CPU inspection reference, not Unity pixel-exact or live-game acceptance.']
 report['coordinate_mapping']='Source mesh and transform centimeters to blocks0.01 with X reflection. Built-inQuad is preserved; all custom mesh IDs are resolved even forbillboard1. New field uniformly scales ground1.35 to packet tier-I radius3 or tier-II radius5, preserving original source XYZ proportions. Legacy profiles retain old sampler.'
 report['preview']={'file':'evidence/二阶原版领域还原预览.png','frames_seconds':[.05,.45,1.35,2.65],'font':os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),'boundary':'CPU composite only, not a game screenshot; source video keyframes stored separately. HUD preview is provided by build_hud.'}
 report['visual_gameplay_boundary']={'ground_visual_and_damage_radius':{'tier1':3,'tier2':5},'damage_vertical_below':.5,'damage_vertical_above':3,'visual_policy':'Original luminous pillars, rain and flares retain full source proportions after uniform scaling and are decorative. They can extend far above the3-block gameplay cylinder or beyond the authoritative ground boundary; luminous spill does not apply damage or marks. Measured visible vertex maxima are in field_geometry_check.json.','impact':'Accepted left-click impact and field share the exact server center, radius and cylinder: tier-I radius3, tier-II radius5 including skins, below0.5 and above3.'}
 report['adaptations'].append('Frozen kulou01/kulou02 retain original skull textures, source placement and travel; private nonzero camera-space oscillator is retained in source trace but not replayed. No replacement oscillation or artwork is invented.')
 save(W/'evidence/source_field_effects.json',report);preview(profiles,hud)
 print('PASS source field effects: 16 source prefabs + 4 preserved legacy profiles, '+str(len(textures))+' copied textures, '+str(sum(len(d['emitters'])for d in profiles.values()))+' emitters')
 return report

def sample_state(p,seconds,duration):
 age=seconds-p['birth']
 if age<0 or age>=p['life']:return None
 x=age/p['life']*(N-1);a=int(x);b=min(a+1,N-1);f=x-a
 return np.array(p['frames'][a])*(1-f)+np.array(p['frames'][b])*f

def measure_radius(profiles):
 radii={}
 for name,d in profiles.items():
  if not name.endswith('_field'):continue
  ground=[];allpoints=[]
  for em in d['emitters']:
   if not em['mesh']:continue
   for pt in em['particles']:
    for frame in pt['frames']:
     if frame[13]<.05:continue
     points=Rotation.from_quat(frame[6:10]).apply(np.array(em['geometry']['positions'])*frame[3:6])+frame[:3]
     allpoints.extend(points.tolist())
     if em['boundary']:ground.extend(points.tolist())
  assert ground,('Source ground circle missing',name)
  ground=np.array(ground);allpoints=np.array(allpoints)
  radii[name]={'ground_quad_corner_radius':float(np.linalg.norm(ground[:,[0,2]],axis=1).max()),'ground_circle_outer_radius':1.35,'derivation':'source Quad diameter=300*0.9*0.01=2.7, circle radius=1.35; transparent Quad corners are not circular range','all_source_mesh_outer_radius_before_fit':float(np.linalg.norm(allpoints[:,[0,2]],axis=1).max()),'vertical_min':float(allpoints[:,1].min()),'vertical_max':float(allpoints[:,1].max())}
 return radii

def build_hud():
 # The approved baseline already contains the requested E-to-V glyph patch.
 # Keep those exact existing pixels; this change only adapts weapon skin effects.
 from zipfile import ZipFile
 result=json.loads((W/'reference/approved_hud.json').read_text('utf8'))
 with ZipFile(W/'baseline/mod.jar') as baseline:
  for tier in result.values():
   for info in tier.values():
    if 'path' not in info:continue
    raw=baseline.read(info['path']);assert hashlib.sha256(raw).hexdigest()==info['sha256']
    target=W/'resources'/info['path'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
 return result

def preview(profiles,hud):
 """Source geometry timeline reference; world-XZ radius5, uncompressed rain."""
 font=ImageFont.truetype(os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),14)
 im=Image.new('RGB',(1200,1040),'#101923');draw=ImageDraw.Draw(im);draw.text((16,5),'二阶原版次元领域／原素材光柱、流星雨、旋转还原／电脑合成参考，非游戏截图',font=font,fill='white')
 angle=math.radians(55);view=Rotation.from_euler('x',55,degrees=True).as_matrix();right=np.array([1,0,0]);up=np.array([0,math.cos(angle),-math.sin(angle)]);px=12;texcache={}
 def raster(canvas,points,uv,triangles,texture,color,additive):
  projected=points@view.T;xy=projected[:,:2]*[px,-px]+[290,350]
  tex=texcache.setdefault(texture,np.array(Image.open(W/'resources/assets'/texture.replace(':','/')).convert('RGBA')).astype(float)/255)
  for face in triangles:
   a,b,c=xy[face];low=np.floor(np.minimum(np.minimum(a,b),c)).astype(int);high=np.ceil(np.maximum(np.maximum(a,b),c)).astype(int)
   x0,y0=np.maximum(low,0);x1,y1=np.minimum(high,[579,459]);
   if x1<x0 or y1<y0:continue
   den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
   if abs(den)<1e-8:continue
   yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
   fa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;fb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;fc=1-fa-fb;inside=(fa>=0)&(fb>=0)&(fc>=0)
   coords=fa[...,None]*uv[face[0]]+fb[...,None]*uv[face[1]]+fc[...,None]*uv[face[2]];tx=(coords[...,0]%1)*(tex.shape[1]-1);ty=(coords[...,1]%1)*(tex.shape[0]-1);ix=tx.astype(int);iy=ty.astype(int);fx=(tx-ix)[...,None];fy=(ty-iy)[...,None]
   rgba=(tex[iy,ix]*(1-fx)+tex[iy,np.minimum(ix+1,tex.shape[1]-1)]*fx)*(1-fy)+(tex[np.minimum(iy+1,tex.shape[0]-1),ix]*(1-fx)+tex[np.minimum(iy+1,tex.shape[0]-1),np.minimum(ix+1,tex.shape[1]-1)]*fx)*fy
   rgba*=color;alpha=rgba[...,3:4]*inside[...,None];region=canvas[y0:y1+1,x0:x1+1];region[:]=np.clip(region+rgba[...,:3]*alpha if additive else region*(1-alpha)+rgba[...,:3]*alpha,0,1)
 for cell,elapsed in enumerate([.05,.45,1.35,2.65]):
  d=profiles['tier2_field'];seconds=elapsed+d['prewarm'];canvas=np.zeros((460,580,3),float);canvas[:]=np.array([16,25,35])/255
  for em in d['emitters']:
   for particle in em['particles']:
    period=particle['life'];first=max(0,math.floor((seconds-particle['birth']-particle['life'])/period)+1);last=math.floor((seconds-particle['birth'])/period)
    for cycle in range(first,last+1):
     st=sample_state(particle,seconds-cycle*period,d['duration'])
     if st is None or st[13]<.0001:continue
     q=Rotation.from_quat(st[6:10]);q=q*Rotation.from_rotvec(np.array(particle['spinAxis'])*particle['spinRate']*period*cycle)
     if em['mesh']:
      geom=em['geometry'];local_points=q.apply(np.array(geom['positions'])*st[3:6]);points=(local_points@np.array([right,[0,1,0],np.cross(right,[0,1,0])])if em['billboard']==1 else local_points@np.array([right,up,np.cross(right,up)])if em['billboard']==0 else local_points)+st[:3];uv=np.array(geom['uv']);triangles=np.array(geom['triangles'])
     else:
      rr=right;uu=np.array([0,1,0])if em['billboard']==1 else up;rx=q.apply([1,0,0]);uy=q.apply([0,1,0]);r=(rr*rx[0]+uu*rx[1])*st[3]*.5;u=(rr*uy[0]+uu*uy[1])*st[4]*.5
      points=np.array([st[:3]+a*r+b*u for a,b in[(-1,-1),(1,-1),(1,1),(-1,1)]]);uv=np.array([[0,1],[1,1],[1,0],[0,0]]);triangles=np.array([[0,1,2],[0,2,3]])
     points*=5/d['sourceRadius']
     us,vs,uo,vo=st[14:]
     if em['rows']and em['columns']:
      phase=(seconds-particle['birth']-cycle*period)/particle['life'];frame=min(int(phase*em['rows']*em['columns']),em['rows']*em['columns']-1);uo+=(frame%em['columns'])/em['columns'];vo+=(frame//em['columns'])/em['rows']
     raster(canvas,points,uv*[us,vs]+[uo,-vo],triangles,em['texture'],st[10:14],em['render']=='additive')
  x=10+(cell%2)*600;y=42+(cell//2)*480;im.paste(Image.fromarray(np.uint8(canvas*255)),(x,y));draw.text((x+10,y-19),f'施放后{elapsed:.2f}秒／平面半径5格／源雨层保持竖轴与原比例高度',font=font,fill='#d3e7ff')
 draw.text((16,1015),'复用原E063全部33发射器175粒子；不径向压缩流星，不新增替代光柱；所有皮肤按原素材，领域贴地。',font=font,fill='#c7d8e8')
 im.save(W/'evidence/二阶原版领域还原预览.png');im.save(W/'evidence/次元领域源素材预览.png')
if __name__=='__main__':build()
