"""Self-contained, native supplied skin effects. Never edits runtime/game resources."""
from pathlib import Path
import json,gzip,hashlib,shutil,sys,collections,math
import numpy as np
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler
from export_native_body import mat4,sampled_tree,parse_anm,keymat

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
MAIN=ROOT/'特效待确认_全量目录';LIGHT=ROOT/'武器皮肤与特效提取/lightning'
OUT=HERE/'skin_sources';OUT.mkdir(exist_ok=True)
for n in ['textures','meshes','shaders','configs']:(OUT/n).mkdir(exist_ok=True)
def read(p):return json.loads(p.read_text('utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,d):p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
def plain(x):
    if x is None:return None
    if hasattr(x,'e00'):return[[float(getattr(x,f'e{r}{c}'))for c in range(4)]for r in range(4)]
    if isinstance(x,(str,int,float,bool)):return x
    if isinstance(x,dict):return{k:plain(v)for k,v in x.items()}
    if isinstance(x,(list,tuple,np.ndarray)):return[plain(v)for v in x]
    if hasattr(x,'__dict__'):return{k:plain(v)for k,v in vars(x).items()}
    return str(x)

raws={};cabs=collections.defaultdict(list);objects=collections.defaultdict(list)
for source in [MAIN,LIGHT]:
 for p in (source/'原始结构').glob('*.json.gz'):
    b=p.name.split('.')[0]
    if b in raws:continue
    d=json.loads(gzip.decompress(p.read_bytes()));raws[b]={'root':source,'data':d,'raw_file':p}
    for f in d['files']:
        cabs[f['cab']].append((b,f))
        for oid,o in f['objects'].items():objects[(f['cab'],int(oid))].append((b,o))
envs={};decoded_mesh={};texture_manifest={};mesh_manifest={};shader_manifest={};unresolved=[];provenance={}
def bundle_file(b):
    rr=raws[b];p=rr['root']/'原始资源包'/f'{b}.unity3d'
    if not p.exists():p=Path(rr['data']['meta']['file'])
    if not p.exists():p=ROOT/'武器皮肤与特效提取/forzen/共享依赖资源包'/f'{b}.unity3d'
    return p
def bundle_meta(b):
    if b not in provenance:
        p=bundle_file(b);provenance[b]={'source_file':str(p),'sha256':sha(p)if p.exists()else raws[b]['data']['meta'].get('sha256'),'raw_structure':str(raws[b]['raw_file'])}
    return provenance[b]
def choose_cab(cab,prefer):return next((x for x in cabs.get(cab,[])if x[0]==prefer),cabs[cab][0]if cab in cabs else None)
def resolve(ptr,cab,b):
    pid=ptr.get('m_PathID',0);fid=ptr.get('m_FileID',0)
    if not pid:return None,None,None
    cf=choose_cab(cab,b)
    target=cab if not fid else cf[1]['externals'][fid-1] if cf and fid<=len(cf[1]['externals'])else None
    candidates=objects.get((target,pid),[])
    item=next((v for v in candidates if v[0]==b),candidates[0]if candidates else None)
    return (target,pid),(item[0]if item else None),(item[1]if item else None)
def mesh(ptr,cab,b,context):
    key,db,ob=resolve(ptr,cab,b)
    if key is None:return None
    mk=f'{key[0]}:{key[1]}'
    if mk in mesh_manifest:return mk
    if ob is None:
        builtin='unity default resources'in str(key[0]) or key[1]in[10202,10206,10207,10208,10209,10210]
        unresolved.append({'kind':'mesh','reference':ptr,'source_cab':cab,'source_bundle':b,'target_cab':key[0],'builtin':builtin,'context':context,'status':'engine builtin geometry'if builtin else'not decoded in supplied index; no invented replacement'})
        return mk
    if ob['type']!='Mesh':return None
    if db not in envs:envs[db]=UnityPy.load(str(bundle_file(db)))
    o=next(o for o in envs[db].objects if o.path_id==key[1]);m=o.read();h=MeshHandler(m);h.process()
    md={'schema':'native_unity_mesh_v1','source_bundle':db,'source_bundle_sha256':bundle_meta(db)['sha256'],'cab':key[0],'id':key[1],'name':m.m_Name,'source_paths':ob.get('paths',[]),'vertices':plain(h.m_Vertices),'normals':plain(h.m_Normals),'tangents':plain(h.m_Tangents),'colors':plain(h.m_Colors),'uv0':plain(h.m_UV0),'uv1':plain(h.m_UV1),'submesh_triangles':[[list(t)for t in sm]for sm in h.get_triangles()],'bone_indices':plain(h.m_BoneIndices),'bone_weights':plain(h.m_BoneWeights),'bind_poses':plain(getattr(m,'m_BindPose',None)),'coordinates':'Native Unity mesh coordinates, UV and triangle order unchanged. No OBJ X reflection or V flip.'}
    md['triangles']=[t for sm in md['submesh_triangles']for t in sm];dest=OUT/'meshes'/f'{db}_{key[1]}.json';dump(dest,md)
    mesh_manifest[mk]={'file':str(dest.relative_to(OUT)).replace('\\','/'),'sha256':sha(dest),'source_bundle':db,'source_id':key[1],'name':m.m_Name,'source_paths':ob.get('paths',[]),'vertex_count':len(md['vertices']or[]),'triangle_count':len(md['triangles'])};return mk
def shader(s):
    key=f"{s['cab']}:{s['id']}"
    if key in shader_manifest:return key
    opts=objects.get((s['cab'],s['id']),[]);item=next((o for o in opts if o[0]==s['bundle']),opts[0]if opts else None)
    if item is None:return key
    b,ob=item;dt=ob.get('data',{});parsed=dt.get('m_ParsedForm',{});name=parsed.get('m_Name',ob.get('name',''))
    kind=('surface_flow'if'uv2'in name.lower()or'liuguang'in name.lower()else'dynamic_mask'if'dynamatic'in name.lower()else'distortion_mask'if'texturedistortion'in name.lower()else'uv_base_model'if'uv_base_model'in name.lower()else'uv_base_mask'if'uv_base_mask'in name.lower()else'refraction'if'refraction'in name.lower()else'particle_sprite')
    desc={'source':s,'source_bundle_sha256':bundle_meta(b)['sha256'],'shader_name':name,'family':kind,'render_pass_states':[{'subshader':i,'pass':j,'state':p.get('m_State')}for i,ss in enumerate(parsed.get('m_SubShaders',[]))for j,p in enumerate(ss.get('m_Passes',[]))],'original_object':ob,'runtime_note':'Original ShaderLab parsed state and compiled programs retained. Do not replace all material tint/blend variants with a skin-wide color.'}
    dest=OUT/'shaders'/f"{b}_{s['id']}.json";dump(dest,desc);shader_manifest[key]={'file':str(dest.relative_to(OUT)).replace('\\','/'),'sha256':sha(dest),'shader_name':name,'family':kind,'source_bundle':b};return key
def material(m,source,preferred):
    mm={k:m.get(k)for k in ['bundle','cab','id','name','paths','properties','shader_pointer']};mm['shader_keys']=[shader(s)for s in m.get('shader_candidates',[])];mm['textures']=[]
    byslot=collections.defaultdict(list)
    for t in m.get('textures',[]):byslot[t['slot']].append(t)
    for slot,tt in byslot.items():
        t=next((t for t in tt if t['bundle']==preferred),next((t for t in tt if t['bundle']==m['bundle']),tt[0]));ts=t['sha256'];png=source/'原图'/f'{ts}.png'
        if not png.exists():png=next((r/'原图'/f'{ts}.png'for r in[MAIN,LIGHT]if(r/'原图'/f'{ts}.png').exists()),png)
        dst=OUT/'textures'/f'{ts}.png'
        if ts not in texture_manifest:
            shutil.copy2(png,dst);texture_manifest[ts]={'file':'textures/'+dst.name,'png_file_sha256':sha(dst),'source_texture_sha256':ts,'source_png':str(png),'source_texture_names':[],'source_paths':[],'size':t.get('size')}
        tr=texture_manifest[ts];tr['source_texture_names']=list(dict.fromkeys(tr['source_texture_names']+[t['name']]));tr['source_paths']=list(dict.fromkeys(tr['source_paths']+t.get('paths',[])))
        mm['textures'].append({**{k:t.get(k)for k in['bundle','cab','id','name','paths','slot','sha256','size','scale','offset']},'file':tr['file'],'all_candidate_bundles':list(dict.fromkeys(x['bundle']for x in tt))})
    bundle_meta(m['bundle']);return mm

def role(c):
    n=c['name'];p=c['path']
    if n=='gun':return'first_person_weapon_and_mounts'
    if 'uv2' in n:return'surface_flow_material'
    if n=='qiangshentx_effect':return'body_continuous'
    if n=='qiangshentx_effect1':return'head_continuous'
    if n=='qiangkouhuohua':return'muzzle'
    if n.startswith('diguan_0'):return'crown_level_'+n[8:9]
    if n.startswith('diguan_baozha'):return'crown_projectile_impact'
    if n.startswith('diguan_dandao'):return'crown_projectile'
    if n.startswith('diguan_chouqu'):return'body_absorb'
    if n.startswith('honglian'):return'skill_red_lotus_mark'
    if n=='jidi_yinji':return'skill_arctic_mark'
    if n.startswith('longxidan'):return'line_attack'
    if n.startswith('event_'):return'impact_event_wrapper'
    if n.startswith('linelink_'):return'line_event_wrapper'
    if n.startswith('move_'):return'projectile_event_wrapper'
    if n.startswith('playerinfo_'):return'player_status_wrapper'
    return'other'

def export_skin(sk,source,bundle,scope_name,label):
    target=OUT/sk;target.mkdir(exist_ok=True);allcards=[read(p)for p in sorted((source/'详情').glob('E*.json'))];cards=[c for c in allcards if f'/skin_fireofjustice_{sk}/fireofjustice2/'in c['card']['path'] and '/scene/'not in c['card']['path']and'/third/'not in c['card']['path']and c['card']['path'].endswith('.prefab')]
    configidx=read(source/'配置引用索引.json');configs=[];anim={};animparts={}
    for path,ents in configidx.items():
        if f'/skin_fireofjustice_{sk}/fireofjustice2/'not in path:continue
        ent=next((e for e in ents if e['bundle']==bundle),ents[0]);sp=source/ent['text'];dest=OUT/'configs'/f'{sk}_{sp.name}';shutil.copy2(sp,dest);row={'source_path':path,'source_bundle':ent['bundle'],'source_sha256':ent.get('sha256'),'file':str(dest.relative_to(OUT)).replace('\\','/'),'sha256':sha(dest)};configs.append(row)
        if path.endswith('/gun.anm.bytes')or path.endswith('/hand_man.anm.bytes'):
            try:dd=parse_anm(sp);animparts[path.rsplit('/',1)[-1]]=dd['TORSO_STAND'][0]['bones'];row['stand_bones']=len(dd['TORSO_STAND'][0]['bones'])
            except Exception as ex:row['animation_parse_error']=str(ex)
    anim=dict(animparts.get('hand_man.anm.bytes',{}));anim.update(animparts.get('gun.anm.bytes',{}))
    entries=[];mounts=[];issues=[]
    for dc in cards:
        card=dc['card'];h=next((h for h in dc['original_hierarchies']if h['bundle']==bundle),dc['original_hierarchies'][0]);b=h['bundle'];nodes=h['nodes'];paths={n['hierarchy']:n for n in nodes};cache={};restcache={};converted=[];materials={};meshkeys=set()
        def parent(n):
            pp=n['hierarchy'].rsplit('/',1)[0]
            if pp.endswith('/[instance]'):pp=pp[:-11]
            return paths.get(pp)
        def world(n,rest=False):
            cc=restcache if rest else cache
            if n['id']in cc:return cc[n['id']]
            pp=parent(n)
            if rest and n['name']in anim:W=(world(pp,True)if pp and pp['name']in anim else np.eye(4))@keymat(anim[n['name']][0])
            elif rest and n['name']in['gun','PlayerMesh']:W=np.eye(4)
            else:W=(world(pp,rest)if pp else np.eye(4))@mat4(n.get('transform',{}))
            cc[n['id']]=W;return W
        for n in nodes:
            pp=parent(n);rec={k:n.get(k)for k in['id','name','hierarchy','via','transform']};rec.update(parent_id=pp['id']if pp else None,local_matrix=mat4(n.get('transform',{})).tolist(),prefab_world=world(n).tolist(),rest_world=world(n,True).tolist(),components=n.get('components',[]),material_keys=[],mesh_keys=[])
            location=next((v for v in objects.get((h['cab'],n['id']),[])if v[0]==b),None)
            if location:rec['source_active']=location[1].get('data',{}).get('m_IsActive')
            for c in n.get('components',[]):
                dt=c.get('data',{});typ=c['type']
                if typ=='ParticleSystem':rec['particle_system_raw']=dt;rec['particle_samples']=sampled_tree(dt)
                if typ.endswith('Renderer'):rec['renderer_type']=typ;rec['renderer_raw']=dt
                if typ=='MonoBehaviour':
                    rec.setdefault('scripts_raw',[]).append(dt)
                    if'ParticleNumber'in dt:rec['custom_particle_raw']=dt
                    if'instance'in dt and dt['instance'].get('m_PathID'):
                        mounts.append({'card':card['number'],'role':role(card),'node':n['name'],'hierarchy':n['hierarchy'],'transform':n['transform'],'component':dt})
                for pk in['m_Mesh','mesh']:
                    if isinstance(dt.get(pk),dict):
                        mk=mesh(dt[pk],c['cab'],c.get('bundle',b),f'{sk}/{card["number"]}/{n["hierarchy"]}/{typ}.{pk}')
                        if mk:rec['mesh_keys'].append(mk);meshkeys.add(mk)
                if typ=='ParticleSystemRenderer'and dt.get('m_RenderMode')==4 and not dt.get('m_Mesh',{}).get('m_PathID'):
                    issues.append({'card':card['number'],'node':n['name'],'id':n['id'],'issue':'mesh renderer mode4 serializes mesh0','resolution':'Retain source raw, no borrowed red-lotus mesh or invented sprite. May be intentionally nonrendering placeholder.'})
            for m in n.get('materials',[]):
                mk=f"{m['cab']}:{m['id']}";rec['material_keys'].append(mk)
                if mk not in materials:materials[mk]=material(m,source,b)
            rec['material_keys']=list(dict.fromkeys(rec['material_keys']));rec['mesh_keys']=list(dict.fromkeys(rec['mesh_keys']));converted.append(rec)
        anchors={n['name']:{'id':n['id'],'hierarchy':n['hierarchy'],'rest_world':world(n,True).tolist()}for n in nodes if n['name']in['WeaponAnchor','Bone005']}
        out={'schema':'judgment_skin_native_source_v1','skin':sk,'display_name':label,'effect_card':card['number'],'role':role(card),'source_path':card['path'],'source_bundle':b,'source_bundle_sha256':bundle_meta(b)['sha256'],'source_card_sha256':sha(source/'详情'/f"{card['number']}.json"),'bindings':card.get('bindings',[]),'nodes':converted,'anchors':anchors,'materials':materials,'meshes':{mk:mesh_manifest.get(mk,{'unresolved':True})for mk in meshkeys},'coordinate_note':'Original Unity coordinates and full hierarchy/TRS. Prefab world includes raw serialized skeleton placeholders; first-person rest_world substitutes this skin gun.anm/hand_man.anm TORSO_STAND when available. Mono LocalEuler/Position/Scala is instance-root override under the mount, not replacement of its parent. Mesh data is in shared meshes/*.json without reflection.'}
        dest=target/f"{card['number']}_{card['name']}.json";dump(dest,out);entries.append({'card':card['number'],'name':card['name'],'role':role(card),'file':str(dest.relative_to(OUT)).replace('\\','/'),'sha256':sha(dest),'source_path':card['path'],'source_bundle':b,'nodes':len(converted),'materials':len(materials),'meshes':len(meshkeys),'custom_particle_nodes':sum('custom_particle_raw'in n for n in converted),'native_particle_nodes':sum('particle_system_raw'in n for n in converted),'source_variants':[{'bundle':hh['bundle'],'node_count':len(hh['nodes'])}for hh in dc['original_hierarchies']]})
        print(sk,card['number'],role(card),len(nodes),'nodes',flush=True)
    manifest={'skin':sk,'label':label,'scope':scope_name,'source_bundle':bundle,'source_bundle_sha256':bundle_meta(bundle)['sha256'],'exported_effects':entries,'configs':configs,'mounts':mounts,'issues':issues,'runtime_unchanged':True};dump(target/'manifest.json',manifest);return manifest

manifests={}
for args in [('qinglong',MAIN,'7677775ec36d993a5e552a6561621b2e','judgfire:qinglong_scope','青龙'),('frozen',MAIN,'32227f411fca91e348c37ecbcc1f2e98','judgfire:frozen_scope','极地（源码 frozen，素材目录别名 forzen）'),('lightning',LIGHT,'51ea9a1ae9cb7c29d283a3a13e4963dc','judgfire:lightning_scope','电光神耀')]:manifests[args[0]]=export_skin(*args)
catalog={'schema':'judgment_skin_source_resource_catalog_v1','resource_base_directory':str(OUT),'textures':texture_manifest,'meshes':mesh_manifest,'shaders':shader_manifest,'source_bundles':provenance,'unresolved_references':unresolved};dump(OUT/'resource_catalog.json',catalog)
if(HERE/'native_shader_math.md').exists():shutil.copy2(HERE/'native_shader_math.md',OUT/'known_shader_math.md')
umbrella={'schema':'judgment_weapon_skin_source_manifest_v1','status':'Assets prepared only. No runtime or game edits; original skin acceptance still precedes other-skin implementation.','skin_ids':[{'id':0,'name':'原版','source':'Existing source_native_body/world export, e01d90 body mount correction'},{'id':1,'name':'青龙','source_manifest':'qinglong/manifest.json'},{'id':2,'name':'极地','source_alias':'frozen; source material directory forzen','source_manifest':'frozen/manifest.json'},{'id':3,'name':'电光神耀','source_manifest':'lightning/manifest.json'}],'identity_evidence':{'WeaponSkins_javap':str(HERE.parent/'runtime_audit/WeaponSkins.javap.txt'),'pack_language':str(ROOT/'_reload_work/pack/assets/judgfire/lang/zh_cn.json'),'warning':'Four actual skins. Never collapse lightning into qinglong/frozen or label frozen as电光神耀.'},'skins':{k:{'manifest':f'{k}/manifest.json','effects':len(v['exported_effects']),'configs':len(v['configs']),'issues':v['issues']}for k,v in manifests.items()},'resource_catalog':'resource_catalog.json','counts':{'textures':len(texture_manifest),'meshes':len(mesh_manifest),'shaders':len(shader_manifest),'source_bundles':len(provenance),'unresolved_mesh_refs':len(unresolved)},'runtime_engine_requirements':['Support original custom MonoBehaviour ParticleNumber/shape/lifetime/size/color/UV sheets and native Unity ParticleSystem separately. Body/head legacy particles are not the original-skin three-mesh continuous material.','Preserve source per-material colors and blend/depth/cull, atlas grids, local hierarchy and mount instance-root overrides.','Resolve native mesh0 as nonrendering or explicitly supported engine builtin only; never silently borrow another effect mesh.','Use event/config references and actual per-skin status ordering; frozen40064 typo is retained as source issue, not silently patched.']};dump(OUT/'manifest.json',umbrella)
print(json.dumps(umbrella['counts'],ensure_ascii=False),flush=True)
