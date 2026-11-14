"""Import the two original cosmetic atlases after proving mesh/ANM reuse."""
from pathlib import Path
import hashlib,importlib.util,json,copy
import numpy as np
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler
W=Path(__file__).resolve().parents[1];P=W.parents[1];R=P/'宙斯、聚能/宙斯之手';A=W/'pack/assets/ssjj_zeus'
sp=importlib.util.spec_from_file_location('family_source_math',P/'work/zeus_family_20261114/scripts/import_family_resources.py');f=importlib.util.module_from_spec(sp);sp.loader.exec_module(f)
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def read(p):return f.read(p)
def load(p):return json.loads(read(p).decode('utf-8-sig'))
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2,allow_nan=False)+'\n','utf8')
def main():
    ref=f.source_profile('hand_of_zeuss2');reports=[]
    for skin,key in [('skin_hand_of_zeuss_lava','lava'),('skin_hand_of_zeuss_galaxy_king','galaxy')]:
        base=R/'完整源素材/武器皮肤'/f'{skin}__{skin}';rig=base/skin;meshdir=base/(skin+'$2');doc=load(rig/'assembly.json');ids={o['path_id']:o for o in doc['objects']};mids={o['path_id']:o for o in load(meshdir/'assembly.json')['objects']};ts={i:o['fields']for i,o in ids.items()if o['type']=='Transform'};got={v['m_GameObject']['m_PathID']:i for i,v in ts.items()}
        env=UnityPy.load(read(next((R/'原始资源包/weaponskin').glob(skin+'_*'))));me=UnityPy.load(read(next((R/'原始资源包/weaponskin').glob(skin+'$2_*'))));meshes={o.path_id:o for o in me.objects if o.type.name=='Mesh'};custom={int(p.stem):load(p)for p in (R/f'特效及材质参数/武器皮肤/{skin}/MonoBehaviour').glob('*.json')}
        def ancestor(i):
            while ts[i]['m_Father']['m_PathID']in ts:i=ts[i]['m_Father']['m_PathID']
            return ts[i]['m_GameObject']['m_PathID']
        def source_geometry_signature(mid):
            if mid not in meshes:return 'source_builtin_'+str(mid)
            h=MeshHandler(meshes[mid].read());h.process();return hashlib.sha256(repr([h.m_Vertices,h.m_UV0,h.get_triangles()]).encode()).hexdigest()
        tiers=[];canonical_fx=[];atlas_paths=[]
        for gun in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']:
            pref=next((path,obj)for path,obj in env.container.items()if path.endswith('/'+gun+'/view/gun.prefab'));rootgo=pref[1].path_id
            renderer=next(o for o in doc['objects']if o['type']=='SkinnedMeshRenderer'and ids[o['fields']['m_GameObject']['m_PathID']]['name']=='zszs_p1'and ancestor(got[o['fields']['m_GameObject']['m_PathID']])==rootgo);v=renderer['fields'];mesh=meshes[v['m_Mesh']['m_PathID']].read();h=MeshHandler(mesh);h.process();indices=np.asarray(h.m_BoneIndices,int);weights=np.asarray(h.m_BoneWeights,float)if h.m_BoneWeights is not None and len(h.m_BoneWeights)>0 else np.ones_like(indices,dtype=float)
            arrays={'positions':np.asarray(h.m_Vertices,float),'normals':np.asarray(h.m_Normals,float),'uv':np.asarray(h.m_UV0,float),'bone_indices':indices,'bone_weights':weights,'triangles':np.asarray(h.get_triangles()[0],int),'inverse_bind':np.array([[[getattr(b,f'e{x}{y}')for y in range(4)]for x in range(4)]for b in mesh.m_BindPose])}
            for name,array in arrays.items():assert np.array_equal(array,ref['meshes']['gun/zszs_p1'][name]),(skin,gun,name)
            mat=mids[v['m_Materials'][0]['m_PathID']];tid=dict(mat['fields']['m_SavedProperties']['m_TexEnvs'])['_MainTex']['m_Texture']['m_PathID'];texture=next((rig/'Textures').glob('*__'+str(tid)+'.png'));read(texture);atlas_paths.append(texture)
            animations=[]
            for kind in ['gun','hand_man']:
                anmasset=next(obj for path,obj in env.container.items()if path.endswith('/'+gun+'/view/'+kind+'.anm.bytes'));file=next((rig/'EmbeddedData').glob(kind+'.anm__'+str(anmasset.path_id)+'.bytes'));anm=f.parse_anm(file);bf=ref['anms'][kind];assert anm.keys()==bf.keys();compared=0
                for clip,blends in anm.items():
                    assert len(blends)==len(bf[clip])
                    for x,y in zip(blends,bf[clip]):
                        assert x['duration']==y['duration']and x['tracks'].keys()==y['tracks'].keys()
                        for bone,array in x['tracks'].items():assert np.array_equal(array,y['tracks'][bone]);compared+=array.size
                animations.append({'kind':kind,'source':file.relative_to(P).as_posix(),'source_sha256':sha(file),'clips':len(anm),'compared_float_components':compared,'max_difference':0})
            fxgo=next(obj.path_id for path,obj in env.container.items()if path.endswith('/'+gun+'/effect/qiangshentexiao/prefab/qiangshentexiao.prefab'));rid,root=next((i,v)for i,v in custom.items()if 'ssjjpaList'in v and v['m_GameObject']['m_PathID']==fxgo);records=[]
            for ptr in root['ssjjpaList']:
                emitter=custom[ptr['m_PathID']];material=mids[emitter['mater']['m_PathID']];pr=material['fields']['m_SavedProperties'];envs=[]
                for texname,data in pr['m_TexEnvs']:
                    data=copy.deepcopy(data);tid=data['m_Texture']['m_PathID'];png=list((rig/'Textures').glob('*__'+str(tid)+'.png'));data['m_Texture']=hashlib.sha256(read(png[0])).hexdigest()if png else str(tid);envs.append([texname,data])
                params={k:v for k,v in emitter.items()if k not in ['m_GameObject','m_Script','mater','mesh']};t=ts[got[emitter['m_GameObject']['m_PathID']]]
                records.append({'name':ids[emitter['m_GameObject']['m_PathID']]['name'],'parameters':params,'transform':{k:t[k]for k in ['m_LocalPosition','m_LocalRotation','m_LocalScale']},'material':{'texenv':envs,'floats':pr['m_Floats'],'colors':pr['m_Colors']},'mesh_signature':source_geometry_signature(emitter['mesh']['m_PathID'])})
            rt=ts[got[fxgo]];canonical={'emitters':records,'root_transform':{k:rt[k]for k in ['m_LocalPosition','m_LocalRotation','m_LocalScale']}};canonical_fx.append(canonical);assert canonical==canonical_fx[0],(skin,gun,'FX differs')
            tiers.append({'gun':gun,'source_prefab':pref[0],'source_prefab_GO':rootgo,'source_mesh_id':v['m_Mesh']['m_PathID'],'mesh_vertex_count':len(arrays['positions']),'mesh_triangle_count':len(arrays['triangles']),'all_mesh_streams_and_inverse_bind_exact_original_II':True,'maximum_mesh_difference':0,'animations':animations,'atlas':texture.relative_to(P).as_posix(),'atlas_sha256':sha(texture),'body_root_component':rid,'body_emitter_count':len(records),'body_source_particle_count':sum(x['parameters']['ParticleNumber']for x in records),'body_parameters_textures_geometry_TRS_exact_same_skin_base':True})
        assert all(p.read_bytes()==atlas_paths[0].read_bytes()for p in atlas_paths)
        out=A/f'textures/gun/uv/hand_of_zeuss_{key}.png';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(read(atlas_paths[0]))
        reports.append({'skin_key':key,'source_id':skin,'tiers':tiers,'source_audio_clip_count':sum(o.type.name=='AudioClip'for o in env.objects),'atlas_output':out.relative_to(W).as_posix(),'atlas_sha256':sha(out),'reused_resources':'Current per-tier final source_mesh/geo/cal/animation; no duplicate skin mesh, no sound or gameplay changes.','source_body_emitters':[{'name':x['name'],'count':x['parameters']['ParticleNumber'],'billboardType':x['parameters']['billboardType']}for x in canonical_fx[0]['emitters']]})
    read(Path(__file__));read(P/'work/zeus_family_20261114/scripts/import_family_resources.py');read(P/'宙斯、聚能/配置参考/WeaponSkinInfo.csv')
    save(W/'evidence/skin_source_equivalence.json',{'passed':True,'profiles':reports,'source_input_sha256':f.INPUTS,'identity_basis':'Raw container-prefab IDs identify all6 tier models; decoded positions/normals/UV/bone indices/weights/topology/bind poses plus every raw ANM float compared. Skin-specific body parameters/material PNG hashes/geometry/TRS canonicalized and compared across3tiers.','audio_policy':'No AudioClip in either source skin bundle; retain each original gun sound. Skin changes visuals only.'})
    print(json.dumps({'passed':True,'profiles':len(reports),'raw_tier_mesh_ANM_max_difference':0,'own_atlases':2}))
if __name__=='__main__':main()
