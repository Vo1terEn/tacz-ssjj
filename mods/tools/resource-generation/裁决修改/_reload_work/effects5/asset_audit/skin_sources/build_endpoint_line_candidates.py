"""Own source line geometry mounted at each skin's proven mouth, with explicit endpoint policy."""
from pathlib import Path
import json,numpy as np
BASE=Path(__file__).resolve().parent
h={'__file__':str(BASE/'build_body_candidates.py'),'__name__':'line_candidate_helpers'}
exec(compile((BASE/'build_body_candidates.py').read_text('utf8').split('\nallsummary=[]')[0],str(BASE/'build_body_candidates.py'),'exec'),h)
read,sha,dump,material,point,one,samples=[h[k]for k in ['read','sha','dump','material','point','one','samples']]
CAT=h['CAT'];summary=[]
for skin in ['qinglong','frozen','lightning']:
    mouth=read(BASE/skin/'mouth_runtime_candidate.json');event=mouth['relatedLineEvents'][0]
    source=BASE/event['source_effect_file'];effect=read(source);root=next(n for n in effect['nodes']if n['parent_id']is None)
    mount=mouth['mounts'][0]
    # Existing mouth mapping removes the identically serialized source root once. Recompute
    # explicitly for this source root so future root version changes cannot accumulate Ry180.
    mouthEffect=read(BASE/skin/mount['sourceEffect']);mouthRoot=next(n for n in mouthEffect['nodes']if n['parent_id']is None)
    C=np.array(mount['sourcePrefabToCurrentRigYup'])@np.array(mouthRoot['prefab_world'])@np.linalg.inv(np.array(root['prefab_world']))
    out={'schema':'judgment_currentrig_skin_endpoint_line_v1','skin':skin,'display_name':mouth['display_name'],'candidateOnly':True,'runtimeUnchanged':True,'coordinateSpace':mouth['coordinateSpace'],'materials':[],'groups':[],'emitters':[],'sourceNodes':[],
         'sourceEvent':event,'sourceEffectSHA256':sha(source),'sourceBundleSHA256':effect['source_bundle_sha256'],'sourceRoot':root['transform'],'mounts':[mount],
         'placementPolicy':{'source':'Own lineLink source prefab and texture/mesh/curve; own source weapon mouth rest mount, head6 animated once','inferredBridge':'Source lineLink host method was not supplied. Mount the source line root at this skin weapon mouth, replace longitudinal authored extent with confirmed endpoint distance times its normalized source longitudinal size curve; retain transverse size, own UV/atlas/mesh and source spin. No fixed ray length. Source wrapper TRS is retained in sourceEvent, not appended as a second Y-rotation.','shortDistance':'Transverse radius clamped to endpoint distance/4, matching original endpoint bridge; longitudinal vertices clamped to mouth..endpoint. Close/behind endpoints omitted.','glow':'True source glow nodes stay at mouth; never stretched into a fake beam.','muzzleSourceCandidateSHA256':sha(BASE/skin/'mouth_runtime_candidate.json')}}
    matids={}
    for n in effect['nodes']:
        W=C@np.array(n['prefab_world']);out['sourceNodes'].append({'id':n['id'],'name':n['name'],'hierarchy':n['hierarchy'],'parent_id':n['parent_id'],'source_active':n['source_active'],'sourceTRS':n['transform'],'sourcePrefabWorld':n['prefab_world'],'currentRestWorld':W.tolist()})
        q=n.get('custom_particle_raw')
        if not q:continue
        mk=n['material_keys'][0]
        if mk not in matids:matids[mk]=len(out['materials']);out['materials'].append(material(effect['materials'][mk]))
        life=one(q['lifeTime']);sizeAxes=[one(q['sizeX'if q['uniformScale_']else'size'+a])for a in 'XYZ'];colors,sizes,uv,alpha=samples(q)
        layer={'name':'weapon_line/'+n['name'],'sourceNodeId':n['id'],'sourceHierarchy':n['hierarchy'],'sourceCard':effect['effect_card'],'material':matids[mk],'bone':6,'binding':{'bone':6},'position':W[:3,3].tolist(),'axisX':W[:3,0].tolist(),'axisY':W[:3,1].tolist(),'axisZ':W[:3,2].tolist(),'localToModel':W.tolist(),'life':float(np.mean(life)),'lifeRange':life,'max':q['ParticleNumber'],'size':np.mean(sizeAxes,axis=1).tolist(),'colors':colors,'sizeAxes':sizes,'uvOffsets':uv,'alphaSamplesRange':alpha,'sheet':[max(q['columns'],1),max(q['rows'],1)]if q['hasSpriteSheetEffector']else[1,1],'billboardType':q['billboardType'],'billboardShader':out['materials'][matids[mk]]['billboardShader'],'RotateAxis':q['RotateAxis'],'sourceIsWorldParticle':q['isWorldParticle'],'source_active':n['source_active'],'enabled':bool(n['source_active']and q.get('m_Enabled',1)and q.get('isShow',1)),'customSchedule':{k:q[k]for k in ['startTime','playTime','nextTime','loop','RandomOnBorn','prewarm','isPlay','isWorldParticle']},'sourceStartTimeSamplesRange':[one(q['startTime'],t)for t in h['SAMPLE']],'customParticleRaw':q}
        meshkey=next(iter(n['mesh_keys']),None)
        if meshkey in CAT['meshes']:
            md=read(BASE/CAT['meshes'][meshkey]['file']);local=np.array(md['vertices']);layer.update({'nativeLocalVertices':md['vertices'],'vertices':point(W,local*np.mean(sizeAxes,axis=1)).tolist(),'uv':[x[:2]for x in md['uv0']],'uv1':[x[:2]for x in md['uv1']]if md['uv1']else None,'faces':md['triangles'],'sourceMeshKey':meshkey,'sourceMeshSHA256':CAT['meshes'][meshkey]['sha256'],'sourceMeshFile':'../'+CAT['meshes'][meshkey]['file'],'nativeVertexColors':md['colors'],'vertexColorsUsedByShader':False})
            axis=0;sign=-1 if n['name']=='sanhuan'else 1;positive=(local[:,axis]*sign);extent=float(positive.max());mode='mesh';half=False
            assert positive.min()>=-1e-6
        else:
            assert meshkey=='unity default resources:10210'
            layer.update({'sourceMeshKey':meshkey,'nativeQuadOnly':True});axis=1;sign=1;extent=.5
            mode='quad'if q['hasSpriteSheetEffector']and q['rows']==4 and q['columns']==4 else 'glow';half=mode=='quad'
        curve=np.array(sizes)[:,axis*2];peak=float(curve.max())
        maxExtent=extent*float(np.mean(sizeAxes[axis]))*peak*float(np.linalg.norm(W[:3,axis]))
        localForOrigin=local if meshkey in CAT['meshes']else np.array([[-.5,-.5,0],[.5,-.5,0],[.5,.5,0],[-.5,.5,0]])
        originWeights=np.linalg.lstsq(np.c_[localForOrigin,np.ones(len(localForOrigin))].T,np.array([0.,0.,0.,1.]),rcond=None)[0]
        assert np.linalg.norm(originWeights@np.c_[localForOrigin,np.ones(len(localForOrigin))]-[0,0,0,1])<1e-8
        layer['linePlacement']={'mode':mode,'longitudinalAxis':axis,'longitudinalSign':sign,'nativePositiveExtent':extent,'sourcePeakSizeCurve':peak,'modelExtentAtSourcePeak':maxExtent,'positiveLocalYHalfAndV':half,'transverseRadiusDistanceCap':.25,'modelRestMuzzle':mount['modelRestMountPosition'],'sourcePrefixedRole':'weapon_line','nativeOriginWeights':originWeights.tolist(),'modelSourceForward':(W[:3,axis]*sign/np.linalg.norm(W[:3,axis])).tolist()}
        assert q['isWorldParticle']==0
        out['groups'if meshkey in CAT['meshes']else'emitters'].append(layer)
    dest=BASE/skin/'line_runtime_candidate.json';dump(dest,out)
    summary.append({'skin':skin,'sourceCard':effect['effect_card'],'lineLinkType':event['source_attributes']['lineLinkType'],'file':str(dest.relative_to(BASE)).replace('\\','/'),'sha256':sha(dest),'layers':[{k:x[k]for k in ['name','sourceMeshKey','linePlacement']}for kind in ['groups','emitters']for x in out[kind]]})
dump(BASE/'line_runtime_candidates_manifest.json',{'schema':'judgment_skin_line_candidates_v1','runtimeUnchanged':True,'candidates':summary})
print(json.dumps([{k:x[k]for k in ['skin','sourceCard','lineLinkType','file']}for x in summary]))
