"""Export version-preserved original default fire VFX; never edits pack/game."""
from pathlib import Path
import json,gzip,hashlib,shutil,re,struct,math
import numpy as np
from scipy.spatial.transform import Rotation
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
SOURCE=ROOT/'特效待确认_全量目录'
BUNDLE='1451ea9d13199c75337b9893c990c502'
MESH_BUNDLE='16393f7eaa87e51a5a21dffec76f2ce7'
MESH_ID=1586721268957342629
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def readcard(key):return json.loads((SOURCE/'详情'/f'{key}.json').read_text('utf8'))
def mat4(t):
    m=np.eye(4);q=t.get('m_LocalRotation',dict(x=0,y=0,z=0,w=1));s=t.get('m_LocalScale',dict(x=1,y=1,z=1));p=t.get('m_LocalPosition',dict(x=0,y=0,z=0))
    m[:3,:3]=Rotation.from_quat([q[k] for k in 'xyzw']).as_matrix()@np.diag([s[k] for k in 'xyz']);m[:3,3]=[p[k] for k in 'xyz'];return m
def parse_anm(path):
    data=path.read_bytes();off=0
    def rd(fmt):
        nonlocal off
        out=struct.unpack_from('<'+fmt,data,off);off+=struct.calcsize('<'+fmt);return out[0] if len(out)==1 else out
    def string():
        nonlocal off
        n=rd('H');s=data[off:off+n].decode();off+=n;return s
    version=rd('I');clips={}
    for _ in range(rd('I')):
        name=string();blends=[]
        for _ in range(rd('I')):
            blend=string();count=rd('I');duration=rd('f');bones={}
            for _ in range(count):
                bone=string();bones[bone]=[rd('8f') for _ in range(rd('I'))]
            blends.append(dict(blend=blend,duration=duration,bones=bones))
        clips[name]=blends
    assert off==len(data)
    return clips
def keymat(key):
    m=np.eye(4);m[:3,:3]=Rotation.from_quat(key[4:8]).as_matrix();m[:3,3]=key[1:4];return m

def hermite(keys,t):
    if not keys:return 0.
    if t<=keys[0]['time']:return keys[0]['value']
    if t>=keys[-1]['time']:return keys[-1]['value']
    for a,b in zip(keys,keys[1:]):
        if a['time']<=t<=b['time']:
            dt=b['time']-a['time'];u=(t-a['time'])/dt
            if not math.isfinite(a.get('outSlope',0)):return a['value']
            return (2*u**3-3*u*u+1)*a['value']+(u**3-2*u*u+u)*dt*a.get('outSlope',0)+(-2*u**3+3*u*u)*b['value']+(u**3-u*u)*dt*b.get('inSlope',0)
    raise ValueError(t)
def gradient(g,t):
    def lerpkeys(keys):
        if not keys:return None
        if t<=keys[0][0]:return keys[0][1]
        if t>=keys[-1][0]:return keys[-1][1]
        for a,b in zip(keys,keys[1:]):
            if a[0]<=t<=b[0]:
                u=(t-a[0])/(b[0]-a[0]);return (np.array(a[1])*(1-u)+np.array(b[1])*u).tolist()
    c=lerpkeys([(g[f'ctime{i}']/65535,[g[f'key{i}'][k] for k in 'rgb']) for i in range(g.get('m_NumColorKeys',0))])
    a=lerpkeys([(g[f'atime{i}']/65535,g[f'key{i}']['a']) for i in range(g.get('m_NumAlphaKeys',0))])
    return list(c or [1,1,1])+[a if a is not None else 1]
def sampled_tree(data,path=''):
    out={}
    if not isinstance(data,dict):return out
    if 'minMaxState' in data and 'scalar' in data:
        xs=[i/64 for i in range(65)];state=data['minMaxState'];s=data['scalar'];ms=data.get('minScalar',s)
        maxkeys=data.get('maxCurve',{}).get('m_Curve',[]);minkeys=data.get('minCurve',{}).get('m_Curve',[])
        vals=[]
        for t in xs:
            if state==0:lo=hi=s
            elif state==3:lo,hi=ms,s
            elif state==1:lo=hi=s*hermite(maxkeys,t)
            elif state==2:lo,hi=s*hermite(minkeys,t),s*hermite(maxkeys,t)
            else:lo=hi=None
            vals.append([lo,hi])
        out[path]={'kind':'MinMaxCurve','raw_state':state,'time_domain':'normalized [0,1]','times':xs,'min_max':vals,'note':'Hermite samples; raw weighted keys retained in particle_system_raw and authoritative.'}
    elif 'minMaxState' in data and 'maxGradient' in data:
        state=data['minMaxState'];xs=[i/64 for i in range(65)];vals=[]
        for t in xs:
            mc=[data.get('minColor',{}).get(k,1) for k in 'rgba'];xc=[data.get('maxColor',{}).get(k,1) for k in 'rgba']
            if state==0:lo=hi=xc
            elif state==1:lo=hi=gradient(data['maxGradient'],t)
            elif state==2:lo,hi=mc,xc
            elif state==3:lo,hi=gradient(data['minGradient'],t),gradient(data['maxGradient'],t)
            else:lo=hi=gradient(data['maxGradient'],t)
            vals.append([lo,hi])
        out[path]={'kind':'MinMaxGradient','raw_state':state,'times':xs,'min_max_rgba':vals}
    else:
        for k,v in data.items():
            if isinstance(v,dict):out.update(sampled_tree(v,path+'.'+k if path else k))
    return out

def main():
    card=readcard('E009');h=next(x for x in card['original_hierarchies'] if x['bundle']==BUNDLE);nodes=h['nodes']
    gunfile=SOURCE/'原始配置/gun.anm_5fec9b99e973.txt';handfile=SOURCE/'原始配置/hand_man.anm_0e0bb8bd05df.txt'
    gun=parse_anm(gunfile);hand=parse_anm(handfile)
    anim=dict(hand['TORSO_STAND'][0]['bones']);anim.update(gun['TORSO_STAND'][0]['bones'])
    pathnodes={n['hierarchy']:n for n in nodes};byname={n['name']:n for n in nodes}
    # Duplicate leaf names are distinguished by pathID. Parent paths are unique.
    def parent(n):
        p=n['hierarchy'].rsplit('/',1)[0]
        if p.endswith('/[instance]'):p=p[:-len('/[instance]')]
        return pathnodes.get(p)
    parents={n['id']:parent(n) for n in nodes};rawcache={};bindcache={}
    def world(n,animation=False):
        cache=bindcache if animation else rawcache
        if n['id'] in cache:return cache[n['id']]
        pp=parents[n['id']]
        if animation and n['name'] in anim:
            # Animation hierarchy excludes static /gun and /PlayerMesh placeholders.
            loc=keymat(anim[n['name']][0]);w=(world(pp,True) if pp and pp['name'] in anim else np.eye(4))@loc
        elif animation and n['name'] in ('gun','PlayerMesh'):w=np.eye(4)
        else:w=(world(pp,animation) if pp else np.eye(4))@mat4(n.get('transform',{}))
        cache[n['id']]=w;return w
    wa=byname['WeaponAnchor'];b5=byname['Bone005'];root=byname['gun']
    waW=world(wa,True);b5W=world(b5,True);invWA=np.linalg.inv(waW);invB5=np.linalg.inv(b5W)
    texture_manifest={};materials={};shader_refs={};converted=[]
    texdir=HERE/'textures';texdir.mkdir(exist_ok=True)
    for n in nodes:
        rec={k:n.get(k) for k in ['id','name','hierarchy','via','transform']};rec['parent_id']=parents[n['id']]['id'] if parents[n['id']] else None
        rec['local_matrix']=mat4(n.get('transform',{})).tolist();rec['raw_prefab_world']=world(n).tolist();rec['rest_world']=world(n,True).tolist();rec['weapon_anchor_relative_rest']=(invWA@world(n,True)).tolist();rec['bone005_relative_rest']=(invB5@world(n,True)).tolist()
        rec['rest_local_matrix']=keymat(anim[n['name']][0]).tolist() if n['name'] in anim else rec['local_matrix']
        rec['rest_local_source']='TORSO_STAND frame0' if n['name'] in anim else 'prefab Transform'
        rec['components']=n.get('components',[]);rec['material_keys']=[]
        for c in n.get('components',[]):
            if c['type']=='ParticleSystem':rec['particle_system_raw']=c['data'];rec['particle_samples']=sampled_tree(c['data'])
            if c['type'].endswith('Renderer'):rec['renderer_raw']=c['data'];rec['renderer_type']=c['type']
        for m in n.get('materials',[]):
            key=f"{m['cab']}:{m['id']}";rec['material_keys'].append(key)
            if key in materials:continue
            out={k:m.get(k) for k in ['bundle','cab','id','name','paths','properties','shader_pointer','shader_candidates']};out['textures']=[]
            for t in m.get('textures',[]):
                ts=t['sha256'];png=SOURCE/'原图'/f'{ts}.png';dst=texdir/f'{ts}.png'
                if ts not in texture_manifest:
                    assert png.exists(),png
                    shutil.copy2(png,dst);assert sha(dst)==sha(png)
                    texture_manifest[ts]={'source_texture_sha256':ts,'png_file_sha256':sha(dst),'file':'textures/'+dst.name,'source_file':str(png),'source_texture_names':[],'source_paths':[],'size':t.get('size')}
                tm=texture_manifest[ts]
                if t['name'] not in tm['source_texture_names']:tm['source_texture_names'].append(t['name'])
                tm['source_paths']=list(dict.fromkeys(tm['source_paths']+t.get('paths',[])))
                tr={k:t.get(k) for k in ['bundle','cab','id','name','paths','slot','sha256','size','scale','offset']};tr['file']=tm['file']
                if tr not in out['textures']:out['textures'].append(tr)
            materials[key]=out
            for s in m.get('shader_candidates',[]):shader_refs[f"{s['cab']}:{s['id']}"]=s
        converted.append(rec)
    # Native FX mesh remains in Unity coordinates; no OBJ reflection or UV flip.
    env=UnityPy.load(str(SOURCE/'原始资源包'/f'{MESH_BUNDLE}.unity3d'));o=next(o for o in env.objects if o.path_id==MESH_ID);mesh=o.read();mh=MeshHandler(mesh);mh.process()
    md={'bundle':MESH_BUNDLE,'cab':o.assets_file.name,'id':MESH_ID,'name':mesh.m_Name,'vertices':[list(v) for v in mh.m_Vertices],'normals':[list(v) for v in mh.m_Normals],'tangents':[list(v) for v in mh.m_Tangents],'uv0':[list(v) for v in mh.m_UV0],'submesh_triangles':[[list(t) for t in sm] for sm in mh.get_triangles()],'coordinate_note':'Native Unity mesh coordinates and native winding. UV0 unchanged; decoded PNG origins top-left; renderer must choose UV flip once.'}
    md['triangles']=[t for sm in md['submesh_triangles'] for t in sm];md['vertex_count']=len(md['vertices']);md['triangle_count']=len(md['triangles'])
    (HERE/'mesh_1586721268957342629.json').write_text(json.dumps(md,ensure_ascii=False,indent=2),encoding='utf8')
    # Full parsed ShaderLab state and programs from exact shared CAB, preserving blend/depth/cull.
    shaders={};raw_loaded={}
    for key,s in shader_refs.items():
        bp=SOURCE/'原始结构'/f"{s['bundle']}.json.gz"
        if s['bundle'] not in raw_loaded:raw_loaded[s['bundle']]=json.loads(gzip.decompress(bp.read_bytes()))
        for f in raw_loaded[s['bundle']]['files']:
            if f['cab']==s['cab'] and str(s['id']) in f['objects']:
                so=f['objects'][str(s['id'])];shaders[key]={'source':s,'object':so};break
    # Exact source XML relevant to default weapon and slot binding.
    cfg=json.loads((SOURCE/'配置引用索引.json').read_text('utf8'));configs=[]
    for path,entries in cfg.items():
        if not path.startswith('assets/res/assets/weapon/fireofjustice2/') or not path.endswith('.xml'):continue
        ent=next((x for x in entries if x['bundle']==BUNDLE),entries[0]);filename=ent['text'].replace('\\','/').rsplit('/',1)[-1];fp=SOURCE/'原始配置'/filename
        if fp.exists():configs.append({'path':path,'file':filename,'sha256':sha(fp),'text':fp.read_text('utf8',errors='replace')})
    manifest={'schema':'judgment_flame_source_native_body_v1','source_bundle':BUNDLE,'source_bundle_sha256':sha(SOURCE/'原始资源包'/f'{BUNDLE}.unity3d'),'source_detail':'详情/E009.json','source_mesh_bundle_sha256':sha(SOURCE/'原始资源包'/f'{MESH_BUNDLE}.unity3d'),'selection':'1451 Fire_017/Mask_037/noise_010 continuous-flow version; do not merge a0 cache 4x8 sheet variant','coordinate_system':'Native Unity left-handed xyz, quaternions xyzw, 4x4 row-major matrices acting on column vectors; no conversion/reflection applied.','warnings':['Raw serialized skeleton transforms are placeholder duplicates. raw_prefab_world retained only for forensic source fidelity. rest_world uses the exact default gun.anm + hand_man.anm TORSO_STAND frame0 for all animated skeleton nodes.','Mesh-native vertices, UV, winding preserved. In PNG decoder coordinates use exactly one V-axis origin conversion.','Particle raw curves/material shader state are authoritative; samples are assistance, weighted tangents retained raw.','Original mount MonoBehaviour LocalEulerAngles may apply after prefab instantiation; muzzle has (-90,0,180). Do not ignore mount component data.'], 'animation_source':[{'path':str(gunfile),'sha256':sha(gunfile)},{'path':str(handfile),'sha256':sha(handfile)}], 'anchors':{n['name']:{'node_id':n['id'],'hierarchy':n['hierarchy'],'raw_prefab_world':world(n).tolist(),'rest_world':world(n,True).tolist(),'weapon_anchor_relative_rest':(invWA@world(n,True)).tolist()} for n in [root,wa,b5]},'nodes':converted,'materials':materials,'textures':list(texture_manifest.values()),'meshes':{str(MESH_ID):md},'shaders':shaders,'original_default_configs':configs}
    (HERE/'source_native_body.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'output':str(HERE/'source_native_body.json'),'nodes':len(converted),'materials':len(materials),'textures':len(texture_manifest),'mesh_vertices':md['vertex_count'],'mesh_triangles':md['triangle_count'],'shaders':len(shaders),'anchors':manifest['anchors']},indent=2))
if __name__=='__main__':main()
