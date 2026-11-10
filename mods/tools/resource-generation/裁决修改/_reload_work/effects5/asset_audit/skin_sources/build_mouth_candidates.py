"""Independent own-skin weapon_mouth candidates; never modifies body/runtime resources."""
from pathlib import Path
import json, hashlib
import numpy as np

BASE=Path(__file__).resolve().parent
# Reuse only pure helper declarations. Do not execute the body candidate generator's main loop.
helper_file=BASE/'build_body_candidates.py'
helpers={'__file__':str(helper_file),'__name__':'mouth_candidate_helpers'}
exec(compile(helper_file.read_text('utf8').split('\nallsummary=[]')[0],str(helper_file),'exec'),helpers)
read,sha,dump,trs,override,point,material,one,samples,startcolors,xyz=[helpers[x]for x in ['read','sha','dump','trs','override','point','material','one','samples','startcolors','xyz']]
YUP=helpers['YUP'];SAMPLE=helpers['SAMPLE'];CAT=helpers['CAT']
events=read(BASE/'event_graphs.json');summary=[]
for skin in ['qinglong','frozen','lightning']:
    manifest=read(BASE/skin/'manifest.json');body=read(BASE/skin/'body_runtime_candidate.json')
    entry=next(x for x in manifest['exported_effects']if x['role']=='muzzle')
    source=BASE/entry['file'];effect=read(source)
    root=next(n for n in effect['nodes']if n['parent_id']is None)
    mount=next(m for m in manifest['mounts']if m['role']=='first_person_weapon_and_mounts'and m['node']=='weapon_particle_fire_effect'and m['component']['instance']['m_PathID']==root['id'])
    head=next(x for x in body['mounts']if x['bone']==6)
    A=np.array(body['validation']['sourceToCurrentRigYdown']);ib=np.array(head['ownSkinNativeInverseBind'])
    mountmat=trs(mount['transform']);ov=override(mount['component']);C=YUP@A@ib@mountmat@ov@np.linalg.inv(np.array(root['prefab_world']))
    configs=events[skin]['source_config_files'];logic=BASE/configs['particle_logic.xml'];relative=BASE/configs['particle_relative.xml']
    lines=logic.read_text('utf8').splitlines();fire_lines=[i+1 for i,v in enumerate(lines)if 'IsRightFiring' in v or 'bindingType="SetFire"' in v or 'Name="weapon_particle_fire_effect"'in v]
    out={'schema':'judgment_currentrig_skin_mouth_candidate_v1','skin':skin,'display_name':manifest['label'],'candidateOnly':True,'runtimeUnchanged':True,
        'coordinateSpace':body['coordinateSpace'],'materials':[],'groups':[],'emitters':[],'sourceNodes':[],
        'validation':{'currentRigSHA256':body['validation']['currentRigSHA256'],'sourceToCurrentRigYdown':A.tolist(),'fitRMS':body['validation']['fitRMS'],'nativeBoneOrder':body['validation']['nativeBoneOrder'],'sourceBodyCandidateSHA256':sha(BASE/skin/'body_runtime_candidate.json'),'extraHundredScale':False,'extraRootY180':False},
        'mounts':[{'role':'weapon_mouth','bone':6,'boneName':'Bone005','sourceEffect':source.name,'sourceEffectSHA256':sha(source),'sourceBundleSHA256':effect['source_bundle_sha256'],'sourceRoot':root['transform'],'mount':mount,'ownSkinNativeInverseBind':ib.tolist(),'instanceOverrideTRS':ov.tolist(),'sourcePrefabToCurrentRigYup':C.tolist(),'modelRestMountPosition':point(YUP@A@ib@mountmat@ov,[[0,0,0]])[0].tolist(),'semantics':head['semantics'],'evidenceLimit':head['evidenceLimit']}],
        'sourceActivation':{'role':'weapon_mouth','sourcePredicate':'IsRightFiring','trueBinding':'SetFire(GetConstTrue)','falseBinding':'SetFire(GetConstNo)','logicFile':configs['particle_logic.xml'],'logicSHA256':sha(logic),'logicLineNumbers':fire_lines,'relativeFile':configs['particle_relative.xml'],'relativeSHA256':sha(relative),'firstPersonMount':mount['hierarchy'],'isRestart':mount['component'].get('IsRestart'),'frameTimeRaw':mount['component'].get('FrameTime'),'sourceUnits':'custom 1P gun-local units; own native gun-to-CurrentRig fit, no cm conversion or clone100','consumerPolicy':'One session for each continuous firing activation; do not restart for every shot. False hides event. Reset/hide behavior is a host candidate inference because SetFire method body is not supplied.'},
        'relatedLineEvents':[e for e in events[skin]['events']if e['event_type']=='lineLinkEffect'],
        'separation':'Line-link/projectile events retained only as metadata; this gun-space definition renders only original mounted muzzle nodes. World renderer owns their separate source prefabs.',
        'inferenceNotes':body['curveEvaluationStatus']}
    matids={}
    for n in effect['nodes']:
        W=C@np.array(n['prefab_world']);out['sourceNodes'].append({'id':n['id'],'name':n['name'],'hierarchy':n['hierarchy'],'parent_id':n['parent_id'],'source_active':n['source_active'],'sourceTRS':n['transform'],'sourcePrefabWorld':n['prefab_world'],'currentRestWorld':W.tolist()})
        q=n.get('custom_particle_raw')
        if not q:continue
        mk=n['material_keys'][0]
        if mk not in matids:matids[mk]=len(out['materials']);out['materials'].append(material(effect['materials'][mk]))
        life=one(q['lifeTime']);siz=[one(q['sizeX'if q['uniformScale_']else'size'+a])for a in 'XYZ'];initial=np.mean(siz,axis=1);colors,sizes,uv,alpha=samples(q);clrs=startcolors(q)
        layer={'name':'weapon_mouth/'+n['name'],'sourceNodeId':n['id'],'sourceHierarchy':n['hierarchy'],'sourceCard':effect['effect_card'],'material':matids[mk],'bone':6,'binding':{'bone':6},'position':W[:3,3].tolist(),'center':W[:3,3].tolist(),'axisX':W[:3,0].tolist(),'axisY':W[:3,1].tolist(),'axisZ':W[:3,2].tolist(),'localToModel':W.tolist(),'life':float(np.mean(life)),'lifeRange':life,'max':q['ParticleNumber'],'rate':q['ParticleNumber']/max(float(np.mean(life)),1e-9),'rateIsDerivedCompatibilityFallback':True,'size':initial.tolist(),'startSizeAxesRange':siz,'startColor':[*np.mean(clrs,axis=0).tolist(),float(np.mean(one(q['alpha'])))],'startColorRGBRange':clrs,'startAlphaRange':one(q['alpha']),'colors':colors,'sizes':[[s[0],s[1]]for s in sizes],'sizeAxes':sizes,'uvOffsets':uv,'alphaSamplesRange':alpha,'sheet':[max(q['columns'],1),max(q['rows'],1)]if q['hasSpriteSheetEffector']else[1,1],'sheetConfig':{k:q[k]for k in ['hasSpriteSheetEffector','rows','columns','randomFrame','lifeDuration']},'shape':q['shape'],'shapeLocalToModel':W.tolist(),'billboardType':q['billboardType'],'billboardShader':out['materials'][matids[mk]]['billboardShader'],'RotateAxis':q['RotateAxis'],'speedRange':one(q['velocity']),'speed':float(np.mean(one(q['velocity']))),'rotationRange':one(q['rot']),'rotationVelocityRange':one(q['rotvelocity']),'velocity':xyz(q['direction']['value']).tolist(),'directionRaw':q['direction'],'mouthOnly':True,'sourceIsWorldParticle':q['isWorldParticle'],'source_active':n['source_active'],'enabled':bool(n['source_active']and q.get('m_Enabled',1)and q.get('isShow',1)),'customSchedule':{k:q[k]for k in ['startTime','playTime','nextTime','loop','RandomOnBorn','prewarm','isPlay','isWorldParticle']},'sourceStartTimeSamplesRange':[one(q['startTime'],t)for t in SAMPLE],'activeEffectors':[k for k,v in q.items()if k.startswith('has')and v],'customParticleRaw':q}
        meshkey=next(iter(n['mesh_keys']),None)
        assert meshkey=='unity default resources:10210',(skin,n['name'],meshkey)
        assert q['isWorldParticle']==0,(skin,n['name'],'requires birth-world snapshots')
        layer.update({'sourceMeshKey':meshkey,'builtinGeometry':'Unity builtin Quad10210, original local plane XY, extent+/-0.5','nativeQuadOnly':True})
        out['emitters'].append(layer)
    dest=BASE/skin/'mouth_runtime_candidate.json';dump(dest,out)
    summary.append({'skin':skin,'sourceCard':effect['effect_card'],'file':str(dest.relative_to(BASE)).replace('\\','/'),'sha256':sha(dest),'materials':len(out['materials']),'groups':len(out['groups']),'emitters':len(out['emitters']),'mountRestYup':out['mounts'][0]['modelRestMountPosition'],'lineLinkTypes':[e['source_attributes']for e in out['relatedLineEvents']]})
dump(BASE/'mouth_runtime_candidates_manifest.json',{'schema':'judgment_skin_mouth_candidates_manifest_v1','candidates':summary,'noRuntimeResourcesChanged':True,'helperSourceSHA256':sha(helper_file),'sourceEventGraphsSHA256':sha(BASE/'event_graphs.json')})
print(json.dumps(summary,ensure_ascii=True,indent=2))
