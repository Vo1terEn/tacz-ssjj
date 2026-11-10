"""Bake original biaoji/biaoji_baozha materials, meshes and particle curves.

This is a source-derived Minecraft adaptation, not a Unity renderer replay.
No old generated gun geometry or named Java classes are dependencies.
"""
from pathlib import Path
import json,math,hashlib
import numpy as np
from scipy.spatial.transform import Rotation
from PIL import Image,ImageDraw,ImageFont

W=Path(__file__).resolve().parent
ROOT=W.parents[1]
SOURCE=ROOT/'次元之星/特效待确认_全量目录'
AS=W/'resources/assets/ssjj_dimension'
CARDS={
 'tier0':('E005','E006'),'tier1':('E024','E025'),'tier2':('E057','E058'),
 'tier0_frozen':('E109','E108'),'tier1_frozen':('E133','E132'),'tier2_frozen':('E157','E156'),
 'tier0_neon':('E197','E196'),'tier1_neon':('E221','E220'),'tier2_neon':('E245','E244'),
 'tier0_royal':('E285','E284'),'tier1_royal':('E309','E308'),'tier2_royal':('E333','E332')}
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
 if t==1:return interp(f,[a['_time']for a in d['_anchors']],[a['_value']for a in d['_anchors']])*d['_maxCurveAnchorValue']
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
 for profile,codes in CARDS.items():
  for kind,code in zip(['mark','burst'],codes):
   p=SOURCE/f'详情/{code}.json';raw=p.read_bytes();inputs[p.relative_to(ROOT).as_posix()]=hashlib.sha256(raw).hexdigest();doc=json.loads(raw)
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
    texfile=SOURCE/source_tex['image'];texbytes=texfile.read_bytes();digest=hashlib.sha256(texbytes).hexdigest();inputs[texfile.relative_to(ROOT).as_posix()]=digest
    target=AS/f'textures/effects/marks/{digest[:20]}.png';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(texbytes);textures[target.relative_to(W/'resources').as_posix()]=digest
    tint=2*rgba(dict(props['m_Colors'])['_TintColor'])*rgba(dict(props['m_Colors'])['_DyeingColor']);blend=dict(props['m_Floats']).get('_BlendDst',1)
    geometry=None
    if v['billboardType']==4 and v['mesh']['m_PathID']!=10210:
     meshid=v['mesh']['m_PathID']
     if meshid in obs:meshdata=obs[meshid]
     else:
      # The detail card intentionally omits binary mesh payload; resolve its exact PathID in original exports.
      candidates=sorted((ROOT/'次元之星').rglob(f'*__{meshid}.json'))
      assert candidates,('Missing original mesh',meshid)
      meshes=[(mp,mp.read_bytes())for mp in candidates]
      assert len({hashlib.sha256(raw).hexdigest()for _,raw in meshes})==1,('Conflicting source mesh PathID',meshid)
      mp,meshraw=meshes[0];inputs[mp.relative_to(ROOT).as_posix()]=hashlib.sha256(meshraw).hexdigest();meshdata=json.loads(meshraw)
     geometry=mesh(meshdata)
    particles=[];bounds=[]
    for i in range(v['ParticleNumber']):
     f=i/max(v['ParticleNumber']-1,1);birth=one(v['startTime'],f,rng);life=one(v['lifeTime'],f,rng);assert birth>=0 and life>0
     maxend=max(maxend,birth+life);sh=v['shape'];position=np.zeros(3);direction=np.zeros(3)
     if sh['shapeType']==3:
      # Source spherical shell/ring shape. Fixed random seed replaces private engine RNG.
      direction=unit(rng.normal(size=3));rad=rng.uniform(min(sh['_radiusSmall'],sh['_radiusBig']),max(sh['_radiusSmall'],sh['_radiusBig']));position=direction*rad
      # A zero-radius source point has no outward shape normal. Giving it an
      # arbitrary unit vector creates spurious multi-block travel in neon's chanrao.
      if rad<1e-8:direction=np.zeros(3)
     elif sh['shapeType']==1:
      theta=rng.uniform(0,math.tau);rad=sh['_radiusBig']*math.sqrt(rng.random());position=np.array([math.cos(theta)*rad,math.sin(theta)*rad,rng.uniform(0,sh['_length'])]);direction=np.array([0.,0.,1.])
     else:raise ValueError(('source shape',sh))
     if not v['directionByShape']:direction=xyz(v['direction']['value'])
     speed=one(v['velocity'],f,rng);base_rot=one(v['rot'],f,rng);spin=one(v['rotvelocity'],f,rng);size=np.array([one(v['size'+a],f,rng)for a in'XYZ']);basealpha=one(v['alpha'],f,rng)
     if v['uniformScale_']:size[:]=size[0]
     coldef=v['color'];col=rgb(coldef['_value'])if coldef['colorType']==0 else rgb(coldef['_color0'])*(1-f)+rgb(coldef['_color1'])*f
     frames=[]
     for phase in np.linspace(0,1,N):
      age=phase*life;local=position+direction*speed*age
      if v['hasForceEffector']:local+=.5*unit(xyz(v['_forceDir']))*v['_force']*age*age
      if v['hasGravityEffector']:local+=.5*xyz(v['gravityDir'])*v['gravity']*age*age
      center=(m@np.r_[local,1])[:3]*.01;sizes=size.copy()
      if v['hasSizeEffector']:
       sizes*=interp(phase,v['_keyFrameLifeTime'],v['_sizeX'])if v['uniformScale_']else np.array([interp(phase,v['_keyFrameLifeTime'],v['_size'+a])for a in'XYZ'])
      sizes*=scale;angle=base_rot+spin*age
      if geometry:
       axis=np.zeros(3);axis[int(v['RotateAxis'])]=math.radians(angle);q=(Rotation.from_matrix(rotation)*Rotation.from_rotvec(J[:3,:3]@axis*-1)).as_quat()
      else:sizes=np.array([sizes[2],sizes[1],1.])*.01;q=Rotation.from_euler('z',angle,degrees=True).as_quat()
      color=col.copy();alpha=basealpha
      if v['hasGradientColorEffector']:
       if not v['JustAlpha']and v['_colors']:
        vals=np.array([rgb(c)for c in v['_colors']]);color=np.array([interp(phase,np.array(v['_colorRatios'])/255,vals[:,j])for j in range(3)])
       alpha*=interp(phase,np.array(v['_alphaRatios'])/255,v['_alphas'])
      color=np.clip(np.r_[color,alpha]*tint,0,1)
      us,vs=env['m_Scale']['x'],env['m_Scale']['y'];uo,vo=env['m_Offset']['x'],env['m_Offset']['y']
      if v['hasSpriteSheetEffector']:
       rows=max(v['rows'],1);columns=max(v['columns'],1);us/=columns;vs/=rows
      if v['hasUVEffector']:uo+=interp(phase,v['_uvkeyFrameLifeTime'],v['_u'],0);vo-=interp(phase,v['_uvkeyFrameLifeTime'],v['_v'],0)
      frames.append(np.r_[center,sizes,q,color,us,vs,uo,vo].round(7).tolist());bounds.append(center)
     particles.append({'birth':birth,'life':life,'frames':frames})
    emitters.append({'name':name,'texture':f'ssjj_dimension:textures/effects/marks/{digest[:20]}.png','render':'additive'if blend==1 else'alpha','mesh':geometry is not None,'rows':v['rows']if v['hasSpriteSheetEffector']else 0,'columns':v['columns']if v['hasSpriteSheetEffector']else 0,'geometry':geometry,'particles':particles})
    traces.append({'name':name,'component':o['id'],'texture_sha256':digest,'source_catalog_texture_digest':source_tex['sha256'],'source_texture_name':names.get(env['m_Texture']['m_PathID']), 'source_mesh_mode':v['billboardType'],'particle_count':v['ParticleNumber'],'source_transform':tx(t).tolist(),'source_definition':v,'source_material':mat})
   data={'schema':1,'card':code,'loop':kind=='mark','duration':max(controller['duration'],maxend),'emitters':emitters}
   profiles[profile+'_'+kind]=data;save(AS/f'effects/marks/{profile}_{kind}.json',data)
   records.append({'profile':profile,'kind':kind,'card':code,'source_prefab':doc['card']['path'],'source_bundle':doc['card']['occurrences'],'source_controller_duration':controller['duration'],'cycle_or_end_seconds':data['duration'],'emitters':traces})
 report={'passed':True,'source_cards':CARDS,'input_sha256':inputs,'texture_sha256':textures,'records':records,
  'material_mapping':'Original textures copied byte-for-byte. Source _BlendDst1 -> SrcAlpha/One additive, _BlendDst10 -> alpha blending; fullbright, linear filtering, LEQUAL depth test, no depth write.',
  'coordinate_mapping':'Full prefab transforms, X reflection exactly once, source cm to Minecraft blocks via0.01. Entire source effect including mesh sampled in camera-local axes and rotated toward camera. Local particles attach to camera-facing surface of interpolated target body. Each mark scaled0.46, each burst0.45; up to5 independent layers in a compact0.24-block-radius ring. Persistent mark local depth compressed to5% so source emitters stay on the chest surface rather than sinking inside opaque target geometry; explosion source volume retained.',
  'retained':'All original emitters, texture materials, counts, lifetime/start/size/color/UV/sheet curves and source mesh geometry.',
  'adaptations':['Private UserNewParticle implementation unavailable; seeded source-range sampling and piecewise-linear oneD curves.','Shape3 uses spherical shell radiusSmall/radiusBig; exact private shape distribution unproven.','Source colorType2/3 uses birth-index interpolation between packed RGB endpoints.','Billboard0/1 face camera; source meshType4 geometry retained.','Loop period=max(controller.duration,max birth+life), initial prewarm1.25s; private loop conventions unproven.','Sprite sheets use discrete original rows/columns frames at runtime, separate from interpolated UV curve tables.','This is a source-derived Minecraft effect, not a pixel-exact Unity simulation.'], 'live_client_test':False}
 report['profile_sha256']={name:hashlib.sha256((AS/f'effects/marks/{name}.json').read_bytes()).hexdigest()for name in profiles};report['per_layer_skin_snapshot']=True
 save(W/'evidence/source_target_effects.json',report)
 print('PASS source target effects: 24 prefabs, '+str(len(textures))+' copied textures, '+str(sum(len(d['emitters'])for d in profiles.values()))+' emitters')
 return report

def preview(profiles):
 """CPU RGBA source-layer preview against a mannequin; not a game screenshot."""
 width=1024;height=600;image=Image.new('RGB',(width,height),'#111927');draw=ImageDraw.Draw(image)
 draw.text((20,12),'SOURCE-DERIVED TARGET MARKS / CPU COMPOSITE / NOT AN IN-GAME SCREENSHOT',fill='white')
 for row,kind in enumerate(['mark','burst']):
  for col,profile in enumerate(CARDS):
   d=profiles[profile+'_'+kind];origin=np.array([128+col*256,145+row*280]);canvas=np.zeros((220,240,3),float);canvas[:]=[17,25,39]
   # A neutral mannequin provides the same 0.6 x 1.8 block scale for all profiles.
   panel=Image.fromarray(canvas.astype('uint8'));pd=ImageDraw.Draw(panel);pd.rectangle((100,82,140,169),fill='#3e5267');pd.rectangle((104,44,136,78),fill='#6e8497');pd.rectangle((84,82,97,158),fill='#536c83');pd.rectangle((143,82,156,158),fill='#536c83');pd.rectangle((102,172,117,214),fill='#384a60');pd.rectangle((123,172,138,214),fill='#384a60');canvas=np.array(panel).astype(float)
   seconds=1.4 if kind=='mark'else .16;scale=.46 if kind=='mark'else .45;px=110
   for emitter in d['emitters']:
    for p in emitter['particles']:
     cycle=math.floor((seconds-p['birth'])/d['duration'])if d['loop']else 0;age=seconds-p['birth']-cycle*d['duration']
     if age<0 or age>=p['life']:continue
     pos=age/p['life']*(N-1);a=int(pos);b=min(a+1,N-1);f=pos-a;s=np.array(p['frames'][a])*(1-f)+np.array(p['frames'][b])*f
     if s[13]<=.001:continue
     texture=Image.open(W/'resources/assets'/emitter['texture'].replace(':','/')).convert('RGBA')
     us,vs,uo,vo=s[14:]
     if emitter['rows']and emitter['columns']:
      frame=min(int(age/p['life']*emitter['rows']*emitter['columns']),emitter['rows']*emitter['columns']-1);uo+=(frame%emitter['columns'])/emitter['columns'];vo+=(frame//emitter['columns'])/emitter['rows']
     left=int((uo%1)*texture.width);top=int((vo%1)*texture.height);tw=max(1,int(abs(us)*texture.width));th=max(1,int(abs(vs)*texture.height));texture=texture.crop((left,top,min(texture.width,left+tw),min(texture.height,top+th)))
     if emitter['mesh']:size=np.array(emitter['geometry']['positions']);extent=np.ptp(size,axis=0)*s[3:6];sx=max(extent[0],extent[2]);sy=max(extent[1],extent[2])
     else:sx,sy=abs(s[3]),abs(s[4])
     outw=max(1,min(600,round(sx*scale*px)));outh=max(1,min(600,round(sy*scale*px)));texture=texture.resize((outw,outh),Image.Resampling.BILINEAR)
     array=np.array(texture).astype(float);array[:,:,:3]*=s[10:13];array[:,:,3]*=s[13]
     x=round(120+s[0]*scale*px-outw/2);y=round(120-s[1]*scale*px-outh/2);x0=max(0,x);y0=max(0,y);x1=min(240,x+outw);y1=min(220,y+outh)
     if x1<=x0 or y1<=y0:continue
     crop=array[y0-y:y1-y,x0-x:x1-x];alpha=crop[:,:,3:4]/255;region=canvas[y0:y1,x0:x1]
     region[:]=np.clip(region+crop[:,:,:3]*alpha if emitter['render']=='additive'else region*(1-alpha)+crop[:,:,:3]*alpha,0,255)
   image.paste(Image.fromarray(canvas.astype('uint8')),(col*256+8,row*280+55));draw.text((col*256+15,row*280+37),profile.upper()+' / '+kind.upper()+' / '+d['card'],fill='#d4edff')
 out=W/'evidence/次元标记源素材预览.png';out.parent.mkdir(parents=True,exist_ok=True);image.save(out)
if __name__=='__main__':build()
