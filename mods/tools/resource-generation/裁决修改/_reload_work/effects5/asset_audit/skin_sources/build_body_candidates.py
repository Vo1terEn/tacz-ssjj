"""Prepare only independent current-rig candidates. Does not write pack/runtime/game."""
from pathlib import Path
import json, math, hashlib, re, sys
import numpy as np

BASE=Path(__file__).resolve().parent
sys.path.insert(0,str(BASE.parents[1]))
from skin_effect_overrides import apply_skin_body_overrides
ROOT=BASE.parents[3]
PACK=ROOT/'_reload_work/pack/assets/judgfire'
CAT=json.loads((BASE/'resource_catalog.json').read_text('utf8'))
SAMPLE=np.linspace(0,1,65)
YUP=np.diag([1.,-1.,1.,1.])
BONES=['WeaponAnchor','Point003','Point002','Point001','Bone008','Bone007','Bone005','Bone004','Bone003','Bone002','Bone001']
def read(p):return json.loads(p.read_text('utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,d):p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
def xyz(v):return np.array([v.get(a,0)for a in 'xyz'],float)
def rgba(v):return [v.get(a,1)for a in 'rgba']
def trs(t):
 p=xyz(t['m_LocalPosition']);q=t['m_LocalRotation'];x,y,z,w=[q[a]for a in 'xyzw'];s=xyz(t['m_LocalScale'])
 r=np.array([[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w],[2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w],[2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y]])
 m=np.eye(4);m[:3,:3]=r@np.diag(s);m[:3,3]=p;return m
def override(c):
 x,y,z=np.deg2rad(xyz(c['LocalEulerAngles']));cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
 rx=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]);ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]);rz=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
 m=np.eye(4);m[:3,:3]=ry@rx@rz@np.diag(xyz(c['LocalScala']));m[:3,3]=xyz(c['LocalPosition']);return m
def point(m,p):return(np.c_[np.array(p,float),np.ones(len(p))]@m.T)[:,:3]
def col24(n):return [((int(n)>>s)&255)/255 for s in [16,8,0]]
def lerpcurve(tt,v,t,default=1):
 if len(tt)==0 or len(v)==0:return default
 # Serialized duplicated keys are preserved separately. Samples use right-continuity.
 tt=np.asarray(tt,float);v=np.asarray(v,float);n=min(len(tt),len(v));return float(np.interp(t,tt[:n],v[:n]))
def one(d,t=0.5):
 k=d.get('oneDType',0)
 if k==0:v=float(d.get('_value',0));return[v,v]
 if k==2:return[float(d.get('_min',0)),float(d.get('_max',0))]
 a=d.get('_anchors',[]);v=lerpcurve([v['_time']for v in a],[v['_value']for v in a],t,0)*d.get('_maxCurveAnchorValue',1);return[v,v]
def startcolors(q):
 c=q['color'];typ=c.get('colorType',0)
 if typ==2:return[col24(c['_color0']),col24(c['_color1'])]
 if typ==1 and c.get('_colors'):return[col24(c['_colors'][0]),col24(c['_colors'][-1])]
 c=col24(c.get('_value',16777215));return[c,c]
def samples(q):
 cs=[];ss=[];uv=[];aa=[];cc=[]
 for t in SAMPLE:
  rgb=np.ones(3);a=1
  if q.get('hasGradientColorEffector'):
   if not q.get('JustAlpha'):
    rgb=np.array([lerpcurve(np.array(q['_colorRatios'])/255,[col24(c)[i]for c in q['_colors']],t,1)for i in range(3)])
   a*=lerpcurve([v/255 for v in q['_alphaRatios']],q['_alphas'],t,1)
  if q.get('hasAlphaEffector'):a*=lerpcurve(q['_alphakeyFrameLifeTime'],q['_alphalst'],t,1)
  if q.get('hasColorEffector'):rgb*=np.array([lerpcurve(q['_colorkeyFrameLifeTime'],q[k],t,1)for k in ['_colorr','_colorg','_colorb']])
  cs.append([*rgb.tolist(),a]);aa.append(one(q['alpha'],t))
  v=[lerpcurve(q['_keyFrameLifeTime'],q['_size'+axis],t,1)if q.get('hasSizeEffector')else 1 for axis in 'XYZ'];ss.append([x for a in v for x in [a,a]])
  uv.append([lerpcurve(q['_uvkeyFrameLifeTime'],q[k],t,0)if q.get('hasUVEffector')else 0 for k in ['_u','_v']])
 return cs,ss,uv,aa
def meshdata(k):return read(BASE/CAT['meshes'][k]['file'])
def material(m):
 floats=dict(m['properties']['m_Floats']);colors=dict(m['properties']['m_Colors']);tt=m['textures'];main=next((t for t in tt if t['slot']=='_MainTex'),None)
 shader=read(BASE/CAT['shaders'][m['shader_keys'][0]]['file']);known=shader['shader_name']=='Particles/SSJJ_NewParticles'
 tint=rgba(colors.get('_TintColor',{}));dye=rgba(colors.get('_DyeingColor',{}))
 # Pixel shader factor 2 is baked once, like current mode0 native preprocessing.
 rt=[2*tint[i]*dye[i]if i<3 else 2*tint[i] for i in range(4)]
 state=shader['render_pass_states'][0]['state']
 samplers={f"{t['cab']}:{t['id']}":CAT['texture_samplers'].get(f"{t['cab']}:{t['id']}")for t in tt}
 for key,s in list(samplers.items()):
  if s:
   s=dict(s);settings=s.get('settings','');samplers[key]=s
   s['wrapUVW']=[int(re.search(r'm_Wrap'+a+r'=(-?\d+)',settings).group(1))if re.search(r'm_Wrap'+a+r'=(-?\d+)',settings)else None for a in 'UVW']
   s['filterMode']=int(re.search(r'm_FilterMode=(-?\d+)',settings).group(1))if re.search(r'm_FilterMode=(-?\d+)',settings)else None
 return {'name':m['name'],'sourceKey':f"{m['cab']}:{m['id']}",'shaderTag':'ssjj_newparticles'if known else'UNIMPLEMENTED_SOURCE_SHADER','shaderName':shader['shader_name'],'shaderSourceFile':'../'+CAT['shaders'][m['shader_keys'][0]]['file'],'pixelMath':'out=2*particleColor*TintColor*tex; out.rgb*=DyeingColor.rgb'if known else'pending precise shader decode','textures':['../'+main['file']if main else None,None,None],'textureSources':tt,'mainST':[main['scale']['x'],main['scale']['y'],main['offset']['x'],main['offset']['y']]if main else[1,1,0,0],'maskST':[1,1,0,0],'noiseST':[1,1,0,0],'scroll':[0,0,0,0],'tint':rt,'tintIncludesPixelFactor2':True,'params':[0,1,1,0],'additive':floats.get('_BlendDst',1)==1,'billboardShader':floats.get('_Billboard',0),'sourceFloats':floats,'sourceColors':colors,'renderState':{'srcBlend':5,'dstBlend':floats.get('_BlendDst',1),'cull':state['culling']['val'],'zTest':floats.get('_ZTest',4),'zWrite':floats.get('_ZWrite',0),'colorMask':state['rtBlend0']['colMask']['val'],'offsetFactor':state['offsetFactor']['val'],'offsetUnits':state['offsetUnits']['val']},'samplers':samplers,'nativeRenderState':state}

allsummary=[]
for skin in ['qinglong','frozen','lightning']:
 manifest=read(BASE/skin/'manifest.json');gunfile=next((BASE/skin).glob('*_gun.json'));gun=read(gunfile)
 gunmeshnode=next(n for n in gun['nodes']if n['name']=='uv2_liuguang');gm=meshdata(gunmeshnode['mesh_keys'][0]);rigfile=PACK/'rig'/f'{skin}_skin.json';rig=read(rigfile)
 src=np.array(gm['vertices']);dst=np.array(rig['vertices']);fit=np.linalg.lstsq(np.c_[src,np.ones(len(src))],dst,rcond=None)[0].T;A=np.eye(4);A[:3,:]=fit;error=np.linalg.norm(point(A,src)-dst,axis=1)
 trnames={c['id']:n['name']for n in gun['nodes']for c in n['components']if c['type']=='Transform'}
 renderer=next(c['data']for c in gunmeshnode['components']if c['type']=='SkinnedMeshRenderer');order=[trnames[b['m_PathID']]for b in renderer['m_Bones']];assert order==BONES
 wi=np.zeros((len(src),11));wr=wi.copy()
 for i in range(len(src)):
  for b,w in zip(gm['bone_indices'][i],gm['bone_weights'][i]):wi[i,b]+=w
  for b,w in zip(rig['indices'][i],rig['weights'][i]):wr[i,b]+=w
 changed=np.where(np.any(abs(wi-wr)>1e-6,axis=1))[0]
 bind=np.array(gm['bind_poses']);out={'schema':'judgment_currentrig_skin_body_candidate_v1','skin':skin,'display_name':manifest['label'],'candidateOnly':True,'runtimeUnchanged':True,'coordinateSpace':'CurrentRig model units Y-up; triangles/UV0/UV1 retain source order; no extra OBJ X reflection. Animation deltas applied after this rest bake.','materials':[],'groups':[],'emitters':[],'sourceNodes':[],'mounts':[],'validation':{'gunMeshSource':CAT['meshes'][gunmeshnode['mesh_keys'][0]],'currentRigFile':str(rigfile.resolve()),'currentRigSHA256':sha(rigfile),'vertexCount':len(src),'sourceToCurrentRigYdown':A.tolist(),'yUp':YUP.tolist(),'fitRMS':float(np.sqrt(np.mean(error**2))),'fitMax':float(error.max()),'nativeBoneOrder':order,'currentRuntimeBoneOrder':BONES,'boneOrderExact':True,'sourceVsCurrentWeightsChangedVertices':len(changed),'sourceVsCurrentWeightsMaxError':float(abs(wi-wr).max()),'weightDifferenceNote':'Current rig neck weights were revised previously; name/order/rest-mesh fit remains exact. FX body rigid binds0, head rigid binds6 use own native inverseBind, not source vertex weights or original-skin static mappings.','changedWeightVertexIds':changed.tolist()},'consumerExtensions':['custom schedule with source startTime/playTime/prewarm/loop, not Unity nativePS rate synthesis','per-axis initial size and lifetime scale; apply in native particle local frame before localToModel','per-particle color, alpha, rotation/rotvelocity and exact sheet/UV effectors','custom shape/direction/force/gravity/orbit/attract/oscillation preserved without replacing by glyphs','_Billboard source shader camera orientation only once; retain builtinQuad identity separately from custom billboardType'],'curveEvaluationStatus':{'serializedCurves':'complete original raw in every layer.customParticleRaw; numeric anchors, curveType and duplicate keys retained','samples65':'piecewise-linear right-continuous interpretation of serialized keys; custom engine source code absent. Keep raw authoritative; do not claim verified custom interpolation/effect order.','startTime':'65 source-value samples retained; samples are not asserted birth random semantics. ParticleNumber,RandomOnBorn,loop,playTime,nextTime,prewarm exact.'}}
 matids={}
 for role,bone,mountname,pattern in [('body_continuous',0,'weapon_particle_effect','*qiangshentx_effect.json'),('head_continuous',6,'weapon_particle_effect1','*qiangshentx_effect1.json')]:
  f=next((BASE/skin).glob(pattern));effect=read(f);root=next(n for n in effect['nodes']if n['parent_id']is None);rootmat=np.array(root['prefab_world'])
  mount=next(m for m in manifest['mounts']if m['role']=='first_person_weapon_and_mounts'and m['node']==mountname and m['component']['instance']['m_PathID']==root['id'])
  invbind=np.linalg.inv(bind[bone]);mountmat=trs(mount['transform']);ov=override(mount['component']);C=YUP@A@invbind@mountmat@ov@np.linalg.inv(rootmat)
  audit={'role':role,'bone':bone,'boneName':BONES[bone],'sourceEffect':f.name,'sourceEffectSHA256':sha(f),'sourceRoot':root['transform'],'mount':mount,'ownSkinNativeInverseBind':invbind.tolist(),'mountLocal':mountmat.tolist(),'instanceOverrideTRS':ov.tolist(),'sourcePrefabToCurrentRigYup':C.tolist(),'semantics':'Assign root local position/euler/scale from instance declared override, replacing serialized root TRS once. C removes serialized root before applying override; do not append root Ry180.','evidenceLimit':'PaticlePrefabInstancecs method body not supplied; assignment is the explicit candidate policy inferred from LocalEulerAngles/LocalPosition/LocalScala declarations. Raw root+override preserved for audit.','modelRestMountPosition':point(YUP@A@invbind@mountmat@ov,[[0,0,0]])[0].tolist()};out['mounts'].append(audit)
  for n in effect['nodes']:
   W=C@np.array(n['prefab_world']);out['sourceNodes'].append({'id':n['id'],'role':role,'name':n['name'],'hierarchy':n['hierarchy'],'parent_id':n['parent_id'],'source_active':n['source_active'],'sourceTRS':n['transform'],'sourcePrefabWorld':n['prefab_world'],'currentRestWorld':W.tolist()})
   if not(q:=n.get('custom_particle_raw')):continue
   mk=n['material_keys'][0]
   if mk not in matids:matids[mk]=len(out['materials']);out['materials'].append(material(effect['materials'][mk]))
   life=one(q['lifeTime']);siz=[one(q['sizeX'if q['uniformScale_']else'size'+a])for a in 'XYZ'];initial=np.mean(siz,axis=1);colors,sizes,uv,alpha=samples(q);clrs=startcolors(q)
   common={'name':role+'/'+n['name'],'sourceNodeId':n['id'],'sourceHierarchy':n['hierarchy'],'sourceCard':effect['effect_card'],'material':matids[mk],'bone':bone,'binding':{'bone':bone},'position':W[:3,3].tolist(),'center':W[:3,3].tolist(),'axisX':W[:3,0].tolist(),'axisY':W[:3,1].tolist(),'axisZ':W[:3,2].tolist(),'localToModel':W.tolist(),'life':float(np.mean(life)),'lifeRange':life,'max':q['ParticleNumber'],'rate':q['ParticleNumber']/max(float(np.mean(life)),1e-9),'rateIsDerivedCompatibilityFallback':True,'size':initial.tolist(),'startSizeAxesRange':siz,'startColor':[*np.mean(clrs,axis=0).tolist(),float(np.mean(one(q['alpha'])))],'startColorRGBRange':clrs,'startAlphaRange':one(q['alpha']),'colors':colors,'sizes':[[s[0],s[1]]for s in sizes],'sizeAxes':sizes,'uvOffsets':uv,'alphaSamplesRange':alpha,'sheet':[max(q['columns'],1),max(q['rows'],1)]if q['hasSpriteSheetEffector']else[1,1],'sheetConfig':{k:q[k]for k in ['hasSpriteSheetEffector','rows','columns','randomFrame','lifeDuration']},'shape':q['shape'],'shapeLocalToModel':W.tolist(),'billboardType':q['billboardType'],'billboardShader':out['materials'][matids[mk]]['billboardShader'],'RotateAxis':q['RotateAxis'],'speedRange':one(q['velocity']),'speed':float(np.mean(one(q['velocity']))),'rotationRange':one(q['rot']),'rotationVelocityRange':one(q['rotvelocity']),'velocity':xyz(q['direction']['value']).tolist(),'directionRaw':q['direction'],'mouthOnly':False,'sourceIsWorldParticle':q['isWorldParticle'],'source_active':n['source_active'],'enabled':bool(n['source_active']and q.get('m_Enabled',1)and q.get('isShow',1)),'customSchedule':{k:q[k]for k in ['startTime','playTime','nextTime','loop','RandomOnBorn','prewarm','isPlay','isWorldParticle']},'sourceStartTimeSamplesRange':[one(q['startTime'],t)for t in SAMPLE],'activeEffectors':[k for k,v in q.items()if k.startswith('has')and v],'customParticleRaw':q}
   meshkey=next(iter(n['mesh_keys']),None)
   if meshkey in CAT['meshes']:
    md=meshdata(meshkey);local=np.array(md['vertices']);vertices=point(W,local*initial)
    common.update({'vertices':vertices.tolist(),'uv':[u[:2]for u in md['uv0']],'uv1':[u[:2]for u in md['uv1']]if md['uv1']else None,'faces':md['triangles'],'sourceMeshKey':meshkey,'sourceMeshFile':'../'+CAT['meshes'][meshkey]['file'],'sourceMeshSHA256':CAT['meshes'][meshkey]['sha256'],'nativeLocalVertices':md['vertices'],'nativeVertexColors':md['colors'],'vertexColorsUsedByShader':False,'verticesInitialSizeBaked':True,'particleScale':1,'alpha':1,'determinant':float(np.linalg.det(W[:3,:3])),'triangleOrder':'source unchanged; source CullOff so no winding correction needed','bounds':{'min':vertices.min(axis=0).tolist(),'max':vertices.max(axis=0).tolist()}});out['groups'].append(common)
   else:
    assert meshkey=='unity default resources:10210',(skin,n['name'],meshkey)
    common.update({'sourceMeshKey':meshkey,'builtinGeometry':'Unity builtin Quad10210, original local plane XY, extent+/-0.5','nativeQuadOnly':True});out['emitters'].append(common)
 out['validation']['counts']={'materials':len(out['materials']),'groups':len(out['groups']),'emitters':len(out['emitters']),'bodyLayers':sum(x['bone']==0 for a in ['groups','emitters']for x in out[a]),'headLayers':sum(x['bone']==6 for a in ['groups','emitters']for x in out[a])}
 apply_skin_body_overrides(skin,out)
 dest=BASE/skin/'body_runtime_candidate.json';dump(dest,out);allsummary.append({'skin':skin,'file':str(dest.relative_to(BASE)).replace('\\','/'),'sha256':sha(dest),'counts':out['validation']['counts'],'fitRMS':out['validation']['fitRMS'],'weightChangedVertices':len(changed),'rootSemantics':out['mounts'][0]['semantics']})
dump(BASE/'body_runtime_candidates_manifest.json',{'schema':'judgment_skin_body_candidates_manifest_v1','runtimeUnchanged':True,'candidates':allsummary,'mathematics':'skin-specific own source native5879 mesh fits own currentRig mesh; own inverseBind0/6 then source mount+assigned instance root override; output Yflip once','sourceResourceCatalog':'resource_catalog.json','shaderMath':'custom_particle_shader_math.md'})
print(json.dumps(allsummary,ensure_ascii=True,indent=2))
