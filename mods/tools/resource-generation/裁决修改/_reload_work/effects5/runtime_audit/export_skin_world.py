"""Independent three-skin world source compiler. Does not edit pack, original data or routes."""
from pathlib import Path
import json,math,hashlib,shutil,re,ast,struct
import numpy as np
from PIL import Image

HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent/'asset_audit/skin_sources'
DEST=HERE/'skin_world'
RES=DEST/'resources/assets/judgfire'
TD=RES/'textures/fx/skin_world'
CAT=json.loads((SOURCE/'resource_catalog.json').read_text('utf8'))
GRAPH=json.loads((SOURCE/'event_graphs.json').read_text('utf8'))
SAMPLE=np.linspace(0,1,65)
def read(p):return json.loads(p.read_text('utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
def xyz(v):return np.array([v.get(k,0)for k in 'xyz'],float)
def rgba(v):return[v.get(k,1)for k in 'rgba']
def point(m,p):return(np.c_[np.asarray(p,float),np.ones(len(p))]@m.T)[:,:3]

# The existing source-only helpers preserve the exact same custom curve interpretation as
# body candidates. Load only function definitions, never the body export's top-level loop.
defs=ast.parse((SOURCE/'build_body_candidates.py').read_text('utf8'))
keep={'one','col24','lerpcurve','startcolors','samples','trs','override'}
exec(compile(ast.Module(body=[n for n in defs.body if isinstance(n,ast.FunctionDef)and n.name in keep],type_ignores=[]),str(SOURCE/'build_body_candidates.py'),'exec'))
native=ast.parse((SOURCE.parent/'export_native_body.py').read_text('utf8'))
exec(compile(ast.Module(body=[n for n in native.body if isinstance(n,ast.FunctionDef)and n.name in {'hermite','gradient','sampled_tree'}],type_ignores=[]),str(SOURCE.parent/'export_native_body.py'),'exec'))

def curve(c,t=.5):
 if not c:return[0.,0.]
 state=c.get('minMaxState',0);s=c.get('scalar',0);lo=c.get('minScalar',s)
 if state==0:return[s,s]
 if state==3:return[lo,s]
 if state==1:v=s*hermite(c.get('maxCurve',{}).get('m_Curve',[]),t);return[v,v]
 return[s*hermite(c.get('minCurve',{}).get('m_Curve',[]),t),s*hermite(c.get('maxCurve',{}).get('m_Curve',[]),t)]
def curvetable(c,default=1):return[curve(c,t)if c else[default,default]for t in SAMPLE]
def startcol(c):
 if not c:return[[1,1,1,1]]*2
 state=c.get('minMaxState',0);lo=rgba(c.get('minColor',{}));hi=rgba(c.get('maxColor',{}))
 if state==0:return[hi,hi]
 if state==1:v=gradient(c['maxGradient'],0);return[v,v]
 if state==2:return[lo,hi]
 return[gradient(c['minGradient'],0),gradient(c['maxGradient'],0)]

def animation_for(d,C):
 result={};byid={n['id']:n for n in d['nodes']}
 for n in d['nodes']:
  animator=next((c['data']for c in n['components']if c['type']=='Animator'and c['data'].get('m_Enabled')),None)
  if not animator:continue
  dep=next((v for v in CAT['animation_dependencies'].values()if v['type']=='AnimatorController'and v['source_id']==animator['m_Controller']['m_PathID']),None)
  if not dep:continue
  clips=[CAT['animation_dependencies'][k]for k in dep.get('linked_keys',[])if CAT['animation_dependencies'][k]['type']=='AnimationClip']
  if len(clips)!=1:continue
  c=read(SOURCE/clips[0]['file'])['object']['data'];bindings=c['m_ClipBindingConstant']['genericBindings']
  if len(bindings)!=1 or bindings[0]['path']!=0 or bindings[0]['attribute']!=4:raise ValueError('Unhandled source animator '+str(bindings))
  raw=c['m_MuscleClip']['m_Clip']['data']['m_StreamedClip']['data'];blob=struct.pack('<'+'I'*len(raw),*raw);offset=0;frames=[]
  while offset<len(blob):
   t,count=struct.unpack_from('<fI',blob,offset);offset+=8;keys=[]
   for _ in range(count):idx,*coeff=struct.unpack_from('<I4f',blob,offset);offset+=20;keys.append((idx,coeff))
   if math.isfinite(t)and t>=0:frames.append((t,keys))
  duration=c['m_MuscleClip']['m_StopTime'];angles=[]
  for t in np.linspace(0,duration,65):
   latest={}
   for kt,keys in frames:
    if kt>t:break
    for idx,coeff in keys:latest[idx]=(kt,coeff)
   out=[]
   for j in range(3):kt,(a,b,cc,dd)=latest[j];dt=t-kt;out.append(((a*dt+b)*dt+cc)*dt+dd)
   angles.append(out)
  A={'duration':duration,'loop':c['m_MuscleClip']['m_LoopTime'],'eulerSamples':angles,'parent':(C@np.array(byid[n['parent_id']]['prefab_world'])).tolist()if n['parent_id']else C.tolist(),'initialInverse':np.linalg.inv(C@np.array(n['prefab_world'])).tolist(),'localPosition':xyz(n['transform']['m_LocalPosition']).tolist(),'localScale':xyz(n['transform']['m_LocalScale']).tolist(),'sourceController':dep,'sourceClip':clips[0],'binding':bindings}
  for child in d['nodes']:
   ancestor=child
   while ancestor:
    if ancestor['id']==n['id']:result[child['id']]=A;break
    ancestor=byid.get(ancestor['parent_id'])
 return result

def main():
 TD.mkdir(parents=True,exist_ok=True);Image.new('RGBA',(1,1),(255,255,255,255)).save(TD/'white.png')
 white='judgfire:textures/fx/skin_world/white.png';mats=[];indices={};texproof={};effects={};skins={};warnings=[]
 def material(src,key):
  if key in indices:return indices[key]
  floats=dict(src['properties']['m_Floats']);colors=dict(src['properties']['m_Colors']);shader=read(SOURCE/CAT['shaders'][src['shader_keys'][0]]['file']);name=shader['shader_name'];state=shader['render_pass_states'][0]['state']
  def slot(*names):
   t=next((t for s in names for t in src['textures']if t['slot']==s),None)
   if not t:return white,[1,1,0,0],{'wrapU':0,'wrapV':0,'filter':1}
   p=SOURCE/t['file'];hash=sha(p);dest=TD/(hash+'.png');shutil.copy2(p,dest)
   texproof[hash]={'file':str(dest.relative_to(DEST)).replace('\\','/'),'source':str(p.resolve()),'sha256':hash,'sourceTexture':t}
   st=[t['scale']['x'],t['scale']['y'],t['offset']['x'],t['offset']['y']]
   sam=CAT['texture_samplers'].get(f"{t['cab']}:{t['id']}",{});settings=sam.get('settings','')
   def val(n,default):r=re.search(n+r'=(-?\d+)',settings);return int(r.group(1))if r else default
   return'judgfire:textures/fx/skin_world/'+hash+'.png',st,{'wrapU':val('m_WrapU',0),'wrapV':val('m_WrapV',0),'filter':val('m_FilterMode',1)}
  tex=[];sts=[];sam=[]
  for names in [('_MainTex','_Tex'),('_MaskTex','_Model_mask'),('_TurbulenceTex','_NoiseTex'),('_ModelTex',)]:
   t,st,sm=slot(*names);tex.append(t);sts.append(st);sam.append(sm)
  low=name.lower();mode=6 if'uv_base_model'in low else 5 if'refraction_tex'in low else 4 if'dynamatic'in low else 2 if'texturedistortion'in low else 1 if'uv_base'in low else 0
  tint=rgba(colors.get('_MainColor',colors.get('_TintColor',colors.get('_Color',{}))))
  if name in('Particles/SSJJ_NewParticles','Particles/SSJJ/Alpha Blended','Particles/SSJJ/Additive'):
   dye=rgba(colors.get('_DyeingColor',{}));tint=[2*v*(dye[i]if i<3 else 1)for i,v in enumerate(tint)]
   if name!='Particles/SSJJ_NewParticles':tint[3]*=floats.get('_Alpha',1)
  elif name=='Legacy Shaders/Particles/Alpha Blended':tint=[2*v for v in tint]
  def renderprop(s,default):return floats.get(s.get('name',''),s.get('val',default))
  dst=floats.get('DstBlend',floats.get('_BlendDst',renderprop(state.get('rtBlend0',{}).get('destBlend',{}),10)))
  srcblend=renderprop(state.get('rtBlend0',{}).get('srcBlend',{}),5)
  result={'name':src['name'],'sourceKey':key,'shaderName':name,'shaderSource':CAT['shaders'][src['shader_keys'][0]],'source_floats':floats,'source_colors':colors,'sourceTextures':src['textures'],'nativeRenderState':state,'textures':tex,'samplers':sam,'mainST':sts[0],'maskST':sts[1],'noiseST':sts[2],'modelST':sts[3],'modelMaskST':sts[1],'scroll':[floats.get('_MainTexPannerX',floats.get('_MoveX',0)),floats.get('_MainTexPannerY',floats.get('_MoveY',0)),floats.get('_TurbulenceTexPannerX',0),floats.get('_TurbulenceTexPannerY',0)],'tint':tint,'params':[mode,floats.get('_MainTexBrightness',floats.get('_Brightness',1)),floats.get('_MainTexPower',floats.get('_MainTexContrast',1)),floats.get('_UVEffectPower',floats.get('_ForceX',0))],'force':[floats.get('_ForceX',0),floats.get('_ForceY',0)],'heatTime':floats.get('_HeatTime',0),'additive':dst==1,'premultiply':srcblend==1,'sourceBlend':[srcblend,dst],'billboardShader':floats.get('_Billboard',0),'cull':state.get('culling',{}).get('val',0),'zTest':floats.get('_ZTest',4),'zWrite':floats.get('_ZWrite',0)}
  if name in('Legacy Shaders/Particles/Alpha Blended Premultiply','FX/Flare'):warnings.append({'material':key,'shader':name,'note':'Preserved native source; special legacy/premultiply/flare math needs isolated review when a live emitted node uses it.'})
  indices[key]=len(mats);mats.append(result);return len(mats)-1
 for skin in('qinglong','frozen','lightning'):
  manifest=read(SOURCE/skin/'manifest.json');srole={};events=GRAPH[skin]['events']
  for entry in manifest['exported_effects']:
   role=entry['role']
   if role in('body_continuous','head_continuous','first_person_weapon_and_mounts')or role.endswith('_wrapper'):continue
   d=read(SOURCE/entry['file']);root=next(n for n in d['nodes']if n['parent_id']is None);rootworld=np.array(root['prefab_world'])
   ev=next((e for e in events if e.get('source_effect_card')==entry['card']),None)
   mount=ev['wrapper_mounts'][0]if ev and ev.get('wrapper_mounts')else None
   if mount:C=np.diag([.01,.01,.01,1.])@trs(mount['transform'])@override(mount['component'])@np.linalg.inv(rootworld);evidence='XML event + actual same-skin wrapper root assignment'
   elif role=='crown_level_2'and skin=='frozen':
    cm={'LocalEulerAngles':dict(x=0,y=-90,z=0),'LocalPosition':dict(x=0,y=0,z=0),'LocalScala':dict(x=1,y=1,z=1)};C=np.diag([.01,.01,.01,1.])@override(cm)@np.linalg.inv(rootworld);evidence='INFERENCE: XML frozeng typo; use actual frozen level2 prefab with other same-skin crown wrappers Y-90'
   else:C=np.diag([.01,.01,.01,1.]);evidence='No world event for this node; raw prefab root retained, not assigned a fabricated world trigger'
   effect={'skin':skin,'role':role,'card':entry['card'],'sourceFile':entry['file'],'sourceSHA256':sha(SOURCE/entry['file']),'sourceBundle':entry['source_bundle'],'sourceRootTRS':root['transform'],'sourceToWorldMeters':C.tolist(),'mount':mount,'mountEvidence':evidence,'groups':[],'emitters':[],'trails':[],'sourceNodes':[]}
   animations=animation_for(d,C)
   for n in d['nodes']:
    W=C@np.array(n['prefab_world']);effect['sourceNodes'].append({'id':n['id'],'name':n['name'],'hierarchy':n['hierarchy'],'sourceTRS':n['transform'],'worldMeters':W.tolist(),'source_active':n['source_active']})
    if not n.get('material_keys'):continue
    r=n.get('renderer_raw',{});q=n.get('custom_particle_raw');ps=n.get('particle_system_raw');mi=material(d['materials'][n['material_keys'][0]],n['material_keys'][0]);meshkey=next((k for k in n.get('mesh_keys',[])if k in CAT['meshes']),None)
    common={'name':entry['card']+'/'+n['name']+'/'+str(n['id']),'sourceNode':n['id'],'sourceHierarchy':n['hierarchy'],'material':mi,'localToWorld':W.tolist(),'enabled':bool(n['source_active']and r.get('m_Enabled',True)),'sourceRenderer':r,'sourceMeshKey':meshkey,'position':W[:3,3].tolist(),'animation':animations.get(n['id'])}
    if n.get('renderer_type')=='TrailRenderer':effect['trails'].append(common);continue
    if q:
     life=one(q['lifeTime']);sizes=[one(q['sizeX'if q['uniformScale_']else'size'+a])for a in'XYZ'];colors,axes,uv,alpha=samples(q);cr=startcolors(q)
     common.update({'customParticle':True,'life':float(np.mean(life)),'lifeRange':life,'max':q['ParticleNumber'],'customSchedule':{k:q[k]for k in('startTime','playTime','nextTime','loop','RandomOnBorn','prewarm','isPlay','isWorldParticle')},'sourceStartTimeSamplesRange':[one(q['startTime'],t)for t in SAMPLE],'customParticleRaw':q,'startSizeAxesRange':sizes,'startColorRGBRange':cr,'alphaSamplesRange':alpha,'colors':colors,'sizeAxes':axes,'uvOffsets':uv,'speedRange':one(q['velocity']),'rotationRange':one(q['rot']),'rotationVelocityRange':one(q['rotvelocity']),'billboardType':q['billboardType'],'billboardShader':mats[mi]['billboardShader'],'sourceIsWorldParticle':q['isWorldParticle'],'enabled':bool(common['enabled']and q.get('m_Enabled',1)and q.get('isShow',1)),'sheet':[q['columns'],q['rows']]if q['hasSpriteSheetEffector']else[1,1]})
     if q.get('shape',{}).get('shapeType')==1 and min(common['speedRange'])>0 and (role=='muzzle' or (skin=='lightning' and role in ('body_absorb','crown_projectile'))):
      common.update(customConeAxis=1,customConeAxisEvidence='Positive-velocity cone in source root-space bounds proves local +Y; runtime_audit/refraction8/world_cone_rootspace_axis_proof.json')
     # These two custom source families encode their cone along +Z. Their root-space
     # serialized bounds disagree with the +Y family used by body/mouth/E084.
     # Keep source raw untouched; make the source-family interpretation explicit.
     if skin=='lightning' and ((role=='crown_projectile_impact' and n['name'] in ('Particles','smoke','4')) or (role=='skill_red_lotus_mark' and n['name']=='smoke')):
      common.update(customConeAxis=2,customConeAxisEvidence='Source root-space bounds and positive velocity; runtime_audit/refraction8/world_cone_rootspace_axis_proof.json')
    elif ps:
     ini=ps['InitialModule'];s=sampled_tree(ps);em=ps['EmissionModule'];sm=ps['SizeModule'];rot=ps['RotationModule'];sz=[curve(ini.get('startSize'+a if a else'startSize'),0)for a in('', 'Y','Z')]if ini.get('size3D')else[curve(ini['startSize'],0)]*3
     cs=s.get('ColorModule.gradient',{}).get('min_max_rgba',[[[1,1,1,1]]*2]*65)if ps.get('ColorModule',{}).get('enabled')else[[[1,1,1,1]]*2]*65
     velocity=ps.get('VelocityModule',{});axes=[];angular=[]
     for t in SAMPLE:
      axes.append(sum((curve(sm.get(a,sm.get('curve')),t)if sm.get('enabled')else[1,1]for a in('curve','y','z')if True),[]))
      if not sm.get('separateAxes'):axes[-1]=axes[-1][:2]*3
      angular.append(sum((curve(rot.get(a),t)if rot.get('enabled')and(a=='curve'or rot.get('separateAxes'))else[0,0]for a in('x','y','curve')),[]))
     cd=ps.get('CustomDataModule',{});custom=[[float(np.mean(curve(cd.get('vector0_'+str(j)),t)))if cd.get('enabled')else 0 for j in range(4)]for t in SAMPLE]
     initial=[]
     for a in('startRotationX','startRotationY','startRotation'):initial+=curve(ini.get(a),0)if a=='startRotation'or ini.get('rotation3D')else[0,0]
     shape=ps.get('ShapeModule',{});bursts=[]
     if em.get('enabled'):
      for b in em.get('m_Bursts',[]):bursts.append({'time':b.get('time',0),'countRange':curve(b.get('countCurve',{})),'cycles':b.get('cycleCount',1),'repeat':b.get('repeatInterval',0),'probability':b.get('probability',1),'raw':b})
     common.update({'customParticle':False,'life':float(np.mean(curve(ini['startLifetime'],0))),'lifeRange':curve(ini['startLifetime'],0),'max':ini['maxNumParticles'],'rate':float(np.mean(curve(em.get('rateOverTime'))))if em.get('enabled')else 0,'duration':ps['lengthInSec'],'delay':float(np.mean(curve(ps.get('startDelay')))),'loop':ps['looping'],'prewarm':ps.get('prewarm',False),'simulationSpace':ps['moveWithTransform'],'startSizeAxesRange':sz,'startColorRange':startcol(ini.get('startColor')),'colorsRange':cs,'sizeAxes':axes,'initialEuler':initial,'angularVelocity':angular,'custom':custom,'shape':shape,'speedRange':curve(ini['startSpeed'],0),'velocity':[float(np.mean(curve(velocity.get(a))))if velocity.get('enabled')else 0 for a in'xyz'],'renderMode':r.get('m_RenderMode',0),'renderAlignment':r.get('m_RenderAlignment',0),'sortFudge':r.get('m_SortingFudge',0),'lengthScale':r.get('m_LengthScale',1),'velocityScale':r.get('m_VelocityScale',0),'bursts':bursts,'sheet':[ps['UVModule'].get('tilesX',1),ps['UVModule'].get('tilesY',1)]if ps['UVModule'].get('enabled')else[1,1],'sourceParticleRaw':ps})
     uv=ps.get('UVModule',{})
     if uv.get('enabled'):
      assert uv.get('timeMode',0)==0 and uv.get('animationType',0)==0, 'Unsupported native texture-sheet mode'
      common.update(sheetFrameOverTime=curvetable(uv.get('frameOverTime'),0),sheetStartFrame=curvetable(uv.get('startFrame'),0),sheetCycles=uv.get('cycles',1))
    elif meshkey:
     common.update({'customParticle':False,'staticMesh':True,'life':1,'max':1,'rate':0,'sizeAxes':[[1,1]*3]*65,'startSizeAxesRange':[[1,1]]*3,'startColorRange':[[1,1,1,1]]*2,'colorsRange':[[[1,1,1,1]]*2]*65,'initialEuler':[0]*6,'renderMode':4,'renderAlignment':2})
    else:continue
    if meshkey:
     mesh=read(SOURCE/CAT['meshes'][meshkey]['file']);vertex_colors=mesh.get('colors')
     if vertex_colors and max(max(c)for c in vertex_colors)>1:vertex_colors=(np.array(vertex_colors,float)/255).tolist()
     common.update({'nativeLocalVertices':mesh['vertices'],'uv':mesh['uv0'],'uv1':mesh.get('uv1'),'vertexColors':vertex_colors,'faces':mesh['triangles'],'sourceMesh':CAT['meshes'][meshkey],'vertexColorsUsedByShader':not bool(q)})
     effect['groups'].append(common)
    else:
     common['builtinQuad']=bool(q and'unity default resources:10210'in n.get('mesh_keys',[]));effect['emitters'].append(common)
   ident=skin+':'+role;effects[ident]=effect;srole[role]=ident
  skins[skin]={'id':{'qinglong':1,'frozen':2,'lightning':3}[skin],'roles':srole,'eventGraph':GRAPH[skin]}
 out={'schema':'judgment_independent_skin_world_v1','runtimeRoutesUnchanged':True,'coordinateSpace':'Unity centimeter source -> meters; event wrapper root assignment replaces serialized root once; matrices still Unity Y-up, runtime reflects Z once. Actor mounts supplied in Minecraft world coordinates.','customSchedulingInference':'CustomParticleSchedule documented candidate; no custom engine method body supplied. Never synthesize Unity rate from ParticleNumber/life.','skins':skins,'materials':mats,'effects':effects,'warnings':warnings}
 dump(RES/'fx/skin_world_native.json',out)
 summary={'configuration':str((RES/'fx/skin_world_native.json').resolve()),'configSHA256':sha(RES/'fx/skin_world_native.json'),'materials':len(mats),'effects':{k:{a:len(v[a])for a in('groups','emitters','trails')}for k,v in effects.items()},'textures':texproof,'warnings':warnings,'runtimeRoutesUnchanged':True}
 dump(DEST/'skin_world_export_manifest.json',summary);print(json.dumps({k:v for k,v in summary.items()if k not in('textures','warnings')},ensure_ascii=True,indent=2))
if __name__=='__main__':main()
