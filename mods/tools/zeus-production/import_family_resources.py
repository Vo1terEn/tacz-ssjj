"""Zeus base/I source proof and resources; no recipe, Java or installed-file writes."""
from pathlib import Path
import copy,csv,hashlib,json,struct,shutil,wave
import numpy as np
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler
W=Path(__file__).resolve().parents[1];P=W.parents[1];RAW=P/'宙斯、聚能/宙斯之手';B=W/'baseline_assets';AS=W/'pack/assets/ssjj_zeus';DATA=W/'pack/data/ssjj_zeus';INPUTS={};OUTPUTS={}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def read(p):p=Path(p);b=p.read_bytes();INPUTS[p.relative_to(P).as_posix()]=hashlib.sha256(b).hexdigest();return b
def load(p):return json.loads(read(p).decode('utf-8-sig'))
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2,allow_nan=False)+'\n','utf8');OUTPUTS[p.relative_to(W).as_posix()]=sha(p)
def cp(a,b):b.parent.mkdir(parents=True,exist_ok=True);b.write_bytes(read(a));OUTPUTS[b.relative_to(W).as_posix()]=sha(b)
def root(gun):return next((RAW/'完整源素材/武器').glob('*__'+gun))
def canonical_rig(doc):
    ids={o['path_id']:o for o in doc['objects']};ts={i:o['fields']for i,o in ids.items()if o['type']=='Transform'}
    def name(i):return ids[ts[i]['m_GameObject']['m_PathID']]['name']
    def path(i):return (path(ts[i]['m_Father']['m_PathID'])+'/'if ts[i]['m_Father']['m_PathID']in ts else'')+name(i)
    return {path(i):{k:t[k]for k in ['m_LocalPosition','m_LocalRotation','m_LocalScale']}for i,t in ts.items()if path(i).split('/')[0]in ['gun','hand_man']},ids,ts,path,name
def parse_anm(p):
    data=read(p);o=0
    def n(fmt):
        nonlocal o
        v=struct.unpack_from('<'+fmt,data,o);o+=struct.calcsize('<'+fmt);return v[0]if len(v)==1 else v
    def st():
        nonlocal o
        size=n('H');v=data[o:o+size].decode('utf8');o+=size;return v
    assert n('i')==0;clips={}
    for _ in range(n('i')):
        clip=st();blends=[]
        for _ in range(n('i')):
            label=st();count=n('i');duration=n('f');tracks={}
            for _ in range(count):bone=st();tracks[bone]=np.array([n('8f')for _ in range(n('i'))])
            blends.append({'name':label,'duration':duration,'tracks':tracks})
        clips[clip]=blends
    assert o==len(data);return clips
def source_profile(gun):
    base=root(gun);rig=base/gun;meshes=base/(gun+'$2');rdoc=load(rig/'assembly.json');mdoc=load(meshes/'assembly.json');cr,ri,rt,rpath,rname=canonical_rig(rdoc);cm,ids,ts,path,name=canonical_rig(mdoc);bundle=next((RAW/'原始资源包/weapon').glob(gun+'$2_*'));env=UnityPy.load(read(bundle));meshobjects={o.path_id:o for o in env.objects if o.type.name=='Mesh'};selected={};records=[]
    for obj in mdoc['objects']:
        if obj['type']!='SkinnedMeshRenderer':continue
        r=obj['fields'];go=ids[r['m_GameObject']['m_PathID']];ti=next(c['component']['m_PathID']for c in go['fields']['m_Component']if c['component']['m_PathID']in ts);node=path(ti)
        if node not in ['gun/zszs_p1','hand_man/hand']:continue
        mid=r['m_Mesh']['m_PathID'];source=meshobjects[mid].read();h=MeshHandler(source);h.process();indices=np.asarray(h.m_BoneIndices,int);weights=np.asarray(h.m_BoneWeights,float)if h.m_BoneWeights is not None and len(h.m_BoneWeights)>0 else np.ones_like(indices,dtype=float);arrays={'positions':np.asarray(h.m_Vertices,float),'normals':np.asarray(h.m_Normals,float),'uv':np.asarray(h.m_UV0,float),'bone_indices':indices,'bone_weights':weights,'triangles':np.asarray(h.get_triangles()[0],int),'inverse_bind':np.array([[[getattr(v,f'e{a}{b}')for b in range(4)]for a in range(4)]for v in source.m_BindPose])};selected[node]=arrays
        mat=ids[r['m_Materials'][0]['m_PathID']];texid=dict(mat['fields']['m_SavedProperties']['m_TexEnvs'])['_MainTex']['m_Texture']['m_PathID'];texture=next((rig/'Textures').glob('*__'+str(texid)+'.png'));read(texture)
        records.append({'node':node,'mesh_name':source.m_Name,'mesh_path_id':mid,'bone_names':[name(b['m_PathID'])for b in r['m_Bones']],'material':mat['name'],'texture':texture.relative_to(P).as_posix(),'texture_sha256':sha(texture),'streams':{k:{'shape':list(v.shape),'sha256':hashlib.sha256(v.tobytes()).hexdigest()}for k,v in arrays.items()}})
    anms={kind:parse_anm(next((rig/'EmbeddedData').glob(kind+'.anm__*.bytes')))for kind in ['gun','hand_man']};return {'gun':gun,'rig':rig,'meshes':selected,'rig_defaults':cr,'mesh_defaults':cm,'anms':anms,'records':records,'rig_source':rig/'assembly.json','mesh_source':meshes/'assembly.json'}
def prove_profiles():
    profiles={gun:source_profile(gun)for gun in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']};ref=profiles['hand_of_zeuss2'];proof=[]
    for gun,p in profiles.items():
        assert p['rig_defaults']==ref['rig_defaults']and p['mesh_defaults']==ref['mesh_defaults'];differences={}
        for node,arrays in p['meshes'].items():
            differences[node]={}
            for key,array in arrays.items():
                other=ref['meshes'][node][key];assert array.shape==other.shape and np.array_equal(array,other),(gun,node,key);differences[node][key]=float(np.max(abs(array-other)))if array.size else 0.
        anim=[]
        for kind,a in p['anms'].items():
            b=ref['anms'][kind];assert a.keys()==b.keys();compared=0
            for clip in a:
                assert len(a[clip])==len(b[clip])
                for x,y in zip(a[clip],b[clip]):
                    assert x['duration']==y['duration']and x['tracks'].keys()==y['tracks'].keys()
                    for bone,values in x['tracks'].items():assert np.array_equal(values,y['tracks'][bone]),(gun,kind,clip,bone);compared+=values.size
            anim.append({'kind':kind,'clips':len(a),'compared_float_components':compared,'maximum_absolute_difference':0,'durations':{c:v[0]['duration']for c,v in a.items()}})
        proof.append({'gun':gun,'mesh_records':p['records'],'mesh_max_absolute_difference_vs_II':differences,'rig_node_count':len(p['rig_defaults']),'mesh_rig_node_count':len(p['mesh_defaults']),'rig_and_mesh_defaults_exactly_equal_II':True,'raw_animation_comparison':anim})
    return profiles,proof
def main():
    profiles,proof=prove_profiles();template='hand_of_zeuss2';notes=[];source_icons={};table=P/'宙斯、聚能/配置参考/Weapon.csv';rows=list(csv.DictReader(read(table).decode('utf-8-sig').splitlines()));refrow=next(r for r in rows if r['Name']==template)
    for gun,cn,en in [('hand_of_zeuss','宙斯之手','Hand of Zeus'),('hand_of_zeuss1','宙斯之手·壹','Hand of Zeus I')]:
        p=profiles[gun];atlas=P/next(r for r in p['records']if r['node']=='gun/zszs_p1')['texture'];cp(atlas,AS/f'textures/gun/uv/{gun}.png');icon=next((p['rig']/'Textures').glob('ui_jy_'+gun+'__*.png'))
        for kind in ['slot','hud']:cp(icon,AS/f'textures/gun/{kind}/{gun}.png')
        source_icons[gun]={'source':icon.relative_to(P).as_posix(),'source_sha256':sha(icon),'slot':f'pack/assets/ssjj_zeus/textures/gun/slot/{gun}.png','hud':f'pack/assets/ssjj_zeus/textures/gun/hud/{gun}.png','operations':'byte-for-byte original main gun icon, no drawing/crop/composite'}
        cp(B/'assets/ssjj_zeus/source_mesh.json',AS/f'source_mesh_{gun}.json');cp(B/'assets/ssjj_zeus/view_calibration.json',AS/f'view_calibration_{gun}.json')
        animation=load(B/f'assets/ssjj_zeus/animations/{template}.animation.json')
        for clip in animation['animations'].values():
            for effect in clip.get('sound_effects',{}).values():effect['effect']=effect['effect'].replace(template+'/',gun+'/')
        save(AS/f'animations/{gun}.animation.json',animation)
        geo=load(B/f'assets/ssjj_zeus/geo_models/gun/{template}_geo.json');geo['minecraft:geometry'][0]['description']['identifier']='geometry.'+gun;save(AS/f'geo_models/gun/{gun}_geo.json',geo)
        display=load(B/f'assets/ssjj_zeus/display/guns/{template}_display.json');display=json.loads(json.dumps(display).replace(template,gun));display['state_machine']='ssjj_zeus:hand_of_zeuss2_state_machine';save(AS/f'display/guns/{gun}_display.json',display)
        # All five corresponding original WAV files are byte-identical between
        # tiers; reuse their frozen TaCZ transcoding, retaining per-gun keys.
        for event,source_name in [('draw','raise'),('shoot','fire'),('shoot_3p','fire'),('reload','reload')]:
            wav=RAW/f'音效/{gun}/WAV/{gun}_{source_name}.wav';wav2=RAW/f'音效/{template}/WAV/{template}_{source_name}.wav';assert read(wav)==read(wav2);cp(B/f'assets/ssjj_zeus/tacz_sounds/{template}/{event}.ogg',AS/f'tacz_sounds/{gun}/{event}.ogg')
        data=load(B/f'data/ssjj_zeus/data/guns/{template}_data.json');row=next(r for r in rows if r['Name']==gun);excluded=['Name','StringName','ThirdPersonModel']
        rowdiff={k:[v,refrow[k]]for k,v in row.items()if v!=refrow[k]};assert all(k in ['Name','StringName','SceneWeaponName']for k in rowdiff),rowdiff
        save(DATA/f'data/guns/{gun}_data.json',data);index=load(B/f'data/ssjj_zeus/index/guns/{template}.json');index=json.loads(json.dumps(index).replace(template,gun));index['sort']=148 if gun=='hand_of_zeuss'else 149;save(DATA/f'index/guns/{gun}.json',index)
        notes.append({'gun':gun,'original_Weapon_csv_row':row,'row_differences_vs_II':rowdiff,'native_data_reused_after_source_equal_proof':True,'source_fields':'12 rounds, 7 pellets, 330ms attack interval, 3.3s reload, 1.0s draw. TaCZ integer RPM182 yields329ms; 1ms faster, ~0.303%.','MC_adaptation':'Remaining baseline TaCZ damage/recoil/speed/inaccuracy fields are preserved MC adaptation values, not a claim of source tier damage. No invented tier multipliers.','source_icon':source_icons[gun],'audio':'Own tier WAV hashes equal tier II; reused current frozen conversion under per-tier sound keys. Unbound skill/secondary WAVs are not newly activated.'})
    for lang in ['zh_cn','en_us']:
        current=AS/f'lang/{lang}.json';doc=load(current)if current.exists()else load(B/f'assets/ssjj_zeus/lang/{lang}.json')
        for gun,cn,en in [('hand_of_zeuss','宙斯之手','Hand of Zeus'),('hand_of_zeuss1','宙斯之手·壹','Hand of Zeus I')]:doc[f'gun.ssjj_zeus.{gun}.name']=cn if lang=='zh_cn'else en;doc[f'gun.ssjj_zeus.{gun}.desc']=('基础霰弹枪 · 12发 · 每发7枚弹丸'if gun=='hand_of_zeuss'else'一阶霰弹枪 · 12发 · 每发7枚弹丸')if lang=='zh_cn'else'12-round shotgun · 7 pellets per shell'
        doc['pack.ssjj_zeus.name']='宙斯之手'if lang=='zh_cn'else'Hand of Zeus';doc['pack.ssjj_zeus.desc']='宙斯之手基础、壹阶与贰阶'if lang=='zh_cn'else'Hand of Zeus base, I and II';save(current,doc)
    save(W/'evidence/family_source_equivalence.json',{'passed':True,'basis':'Decoded original Unity mesh streams/bind poses/default rigs and every raw ANM float, compared by clip and bone rather than byte order.','profiles':proof,'reused_baseline_scope':'Current corrected final mesh, grip/reload animation, camera calibration and existing state machine are retained because the original geometry, rig and all source animation values are identical. Tier appearance uses own original atlas/icon and own original body FX.','source_inputs_sha256':INPUTS})
    save(W/'evidence/family_resource_import.json',{'passed':True,'guns':['hand_of_zeuss','hand_of_zeuss1'],'profiles':notes,'icons':source_icons,'new_bindings':0,'skills_unchanged_unbound':True,'source_input_sha256':INPUTS,'output_sha256':OUTPUTS,'owner_boundary':'Root owns recipes/fragments and runtime agent will apply third-person scale to these generated geo/display after import. Do not rerun import after that adjustment without reapplying it.'});print(json.dumps({'passed':True,'output_resources':len(OUTPUTS),'mesh_rig_ANM_equal':True,'icons':source_icons},ensure_ascii=True))
if __name__=='__main__':main()
