"""Extract only original looping qiangshen weapon-body effects.

No attack/skill/trail dependency is used. Source packages, material properties,
local poses, emitter parameters and mesh/texture hashes are retained as evidence.
The small deterministic CPU cache is decoded during resource reload, not render.
"""
from pathlib import Path
from collections import Counter
from functools import lru_cache
import argparse
import gzip
import hashlib
import io
import json
import math
import struct
import sys
from zipfile import ZipFile
import numpy as np
from PIL import Image
from scipy.spatial.transform import Rotation
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parents[1]
SOURCE = WORKSPACE / '黯水晶/武器特效/原始资源包'
OUT = ROOT / 'build/blade_asset_mod_overlay/assets/ssjj_dark'
EVIDENCE = ROOT / 'evidence'
GUNS = ('darkdiamond', 'darkdiamond1', 'darkdiamond2')
SKINS = ('mix', 'nightguardian', 'pure')

def sha(data): return hashlib.sha256(data).hexdigest()
def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n', 'utf8')
def vector(value, axes='xyz'): return np.array([value.get(k, 0) for k in axes], dtype=float)
def matrix(position, rotation, scale):
    result = np.eye(4)
    result[:3, :3] = Rotation.from_quat(rotation).as_matrix() @ np.diag(scale)
    result[:3, 3] = position
    return result

class Scene:
    def __init__(self, code):
        folder = 'weaponskin' if code.startswith('skin_') else 'weapon'
        self.paths = sorted(path for path in (SOURCE / folder).glob(code + '*') if path.name[len(code):].startswith(('_', '$')))
        assert len(self.paths) == 2, (code, self.paths)
        self.objects = {obj.path_id: obj for path in self.paths for obj in UnityPy.load(str(path)).objects}
        self.transforms = {key: obj.parse_as_dict() for key, obj in self.objects.items() if obj.type.name == 'Transform'}
        self.names = {key: obj.peek_name() for key, obj in self.objects.items() if obj.type.name == 'GameObject'}
        self.go_transform = {data['m_GameObject']['m_PathID']: key for key, data in self.transforms.items()}
        self.attachments = [obj.parse_as_dict() for obj in self.objects.values() if obj.type.name == 'MonoBehaviour' and 'instance' in obj.parse_as_dict()]
        self.systems = [(key, obj.parse_as_dict()) for key, obj in self.objects.items() if obj.type.name == 'MonoBehaviour' and 'ParticleNumber' in obj.parse_as_dict()]
        self.code = code
        self.container = {name: value['asset']['m_PathID'] for obj in self.objects.values() if obj.type.name == 'AssetBundle' for name,value in obj.parse_as_dict()['m_Container']}
    def chain(self, node):
        result = []
        while node in self.transforms:
            data = self.transforms[node]
            result.append(self.names.get(data['m_GameObject']['m_PathID'], '?'))
            node = data['m_Father']['m_PathID']
        return '/'.join(reversed(result))
    def path(self, data): return self.chain(self.go_transform[data['m_GameObject']['m_PathID']])
    @lru_cache(None)
    def world(self, node):
        data = self.transforms[node]
        local = matrix(vector(data['m_LocalPosition']), vector(data['m_LocalRotation'], 'xyzw'), vector(data['m_LocalScale']))
        parent = data['m_Father']['m_PathID']
        return (self.world(parent) if parent in self.transforms else np.eye(4)) @ local
    def loop_systems(self): return [(key, data) for key, data in self.systems if data.get('loop') and self.path(data).startswith('qiangshen')]
    def root(self, node):
        while self.transforms[node]['m_Father']['m_PathID'] in self.transforms:
            node = self.transforms[node]['m_Father']['m_PathID']
        return node
    def prefab(self, grade, usage):
        found = [value for key,value in self.container.items() if f'/{grade}/{usage}/' in key and key.endswith('/' + ('gun' if usage == 'view' else 'model') + '.prefab')]
        assert len(found) == 1, (grade,usage,found)
        return self.go_transform[found[0]]
    def effect(self, grade):
        found = [(key,value) for key,value in self.container.items() if f'/{grade}/effect/qiangshen' in key and key.endswith('.prefab')]
        assert len(found) == 1, (grade,found)
        return found[0][0], self.go_transform[found[0][1]]
    def mesh_renderer(self, root, name):
        found = []
        for obj in self.objects.values():
            if obj.type.name != 'SkinnedMeshRenderer': continue
            data = obj.parse_as_dict(); mesh = self.objects.get(data['m_Mesh']['m_PathID'])
            if mesh and mesh.peek_name() == name and self.root(self.go_transform[data['m_GameObject']['m_PathID']]) == root: found.append((data,mesh))
        assert len(found) == 1, (self.code,name,found)
        return found[0]

def sample(keys, values, age, fallback=1):
    if not keys or not values: return fallback
    length = min(len(keys),len(values)); pairs = sorted(zip(keys[:length],values[:length]))
    # The original custom script stores color ramps in [0,255], size/UV in [0,1].
    extent = max(key for key,_ in pairs); time = age * (255 if extent > 1.01 else 1)
    # Duplicate timestamps are serialized sentinels; the last value is active.
    pairs = dict(pairs)
    ordered = sorted(pairs)
    return float(np.interp(time,ordered,[pairs[key] for key in ordered]))

def scalar(data, age, random):
    kind = data.get('oneDType',0)
    if kind == 2: return data.get('_min',0)*(1-random)+data.get('_max',0)*random
    if kind == 1:
        keys=data.get('_anchors',[])
        return sample([x['_time'] for x in keys],[x['_value'] for x in keys],age,data.get('_value',0))*data.get('_maxCurveAnchorValue',1)
    return data.get('_value',0)

def rgb(value): return np.array([(int(value)>>16)&255,(int(value)>>8)&255,int(value)&255],float)/255
def color(data,age,random):
    kind=data.get('colorType',0)
    if kind==2:return rgb(data.get('_color0',0))*(1-random)+rgb(data.get('_color1',0))*random
    if kind==1:
        values=[rgb(v) for v in data.get('_colors',[])];keys=data.get('_colorRatios',[])
        if not values:return np.ones(3)
        return np.array([sample(keys,[v[k] for v in values],age) for k in range(3)])
    return rgb(data.get('_value',0xffffff))

def original_mesh(scene, mesh_id):
    if mesh_id == 10210: # Unity's built-in Quad; no invented effect geometry.
        return np.array([[-.5,-.5,0],[.5,-.5,0],[.5,.5,0],[-.5,.5,0]]), np.array([[0,1,2],[0,2,3]]), np.array([[0,0],[1,0],[1,1],[0,1]])
    obj=scene.objects[mesh_id]; assert obj.type.name=='Mesh'
    helper=MeshHandler(obj.parse_as_object());helper.process()
    return np.array(helper.m_Vertices,float),np.array(helper.m_IndexBuffer,int).reshape(-1,3),np.array(helper.m_UV0,float)[:,:2]

def attachment_matrix(scene, grade, usage, effect_root):
    root=scene.prefab(grade,usage)
    candidates=[data for data in scene.attachments if data['instance']['m_PathID']==scene.transforms[effect_root]['m_GameObject']['m_PathID'] and scene.root(scene.go_transform[data['m_GameObject']['m_PathID']])==root]
    assert len(candidates)==1,(scene.code,grade,usage,len(candidates))
    attachment=candidates[0]; renderer, mesh=scene.mesh_renderer(root,'asjj_p1' if usage=='view' else 'asjj_p3')
    assert len(renderer['m_Bones'])==1
    bone=renderer['m_Bones'][0]['m_PathID'];hook=scene.go_transform[attachment['m_GameObject']['m_PathID']]
    local=matrix(vector(attachment['LocalPosition']),Rotation.from_euler('zxy',vector(attachment['LocalEulerAngles'])[[2,0,1]],degrees=True).as_quat(),vector(attachment['LocalScala']))
    return np.linalg.inv(scene.world(bone)) @ scene.world(hook) @ local @ np.linalg.inv(scene.world(effect_root)), mesh

def states(data, material, seed):
    count=int(data['ParticleNumber']); assert 0<count<=64
    random=np.random.default_rng(seed & 0xffffffff)
    tint=dict(material['m_SavedProperties']['m_Colors']).get('_TintColor',{'r':.5,'g':.5,'b':.5,'a':.5})
    tint=np.array([tint.get(key,.5)*2 for key in 'rgba'])
    result=[]; lifetimes=[]
    for particle in range(count):
        rr=random.random(12);life=max(.05,scalar(data['lifeTime'],0,rr[0]));lifetimes.append([life,particle/count])
        shape=data['shape']; shape_type=shape['shapeType']
        if shape_type==0:
            origin=vector(shape.get('_rectFrom',{}))*(1-rr[1:4])+vector(shape.get('_rectTo',{}))*rr[1:4]
        elif shape_type==1:
            angle=rr[1]*2*math.pi;length=shape.get('_length',0)*rr[2]
            radius=shape.get('_radiusSmall',0)+(shape.get('_radiusBig',0)-shape.get('_radiusSmall',0))*rr[2]
            origin=np.array([math.cos(angle)*radius,math.sin(angle)*radius,length])
        elif shape_type==2:
            direction=rr[1:4]*2-1;direction/=max(np.linalg.norm(direction),1e-9)
            origin=direction*shape.get('_radius',0)*rr[4]**(1/3)
        else:origin=np.zeros(3)
        dd=data.get('direction',{});direction=vector(dd.get('value',{}))
        if dd.get('threeDType')==4:
            direction=rr[5:8]*2-1;direction/=max(np.linalg.norm(direction),1e-9)
        velocity=scalar(data.get('velocity',{}),0,rr[8]);rotation=scalar(data.get('rot',{}),0,rr[9]);speed=scalar(data.get('rotvelocity',{}),0,rr[10])
        scale=np.array([scalar(data['size'+axis],0,rr[(9+k)%12]) for k,axis in enumerate('XYZ')])
        particle_states=[]
        for frame in range(64):
            age=frame/63; seconds=age*life;position=origin+direction*velocity*seconds
            size=scale.copy()
            if data.get('hasSizeEffector'):
                size*=np.array([sample(data['_keyFrameLifeTime'],data['_size'+axis],age) for axis in 'XYZ'])
            if data.get('hasGravityEffector'):position+=vector(data['gravityDir'])*data.get('gravity',0)*seconds**2*.5
            if data.get('hasForceEffector'):position+=vector(data['_forceDir'])*data.get('_force',0)*seconds**2*.5
            if data.get('hasOrbitEffector'):
                angle=math.radians(data.get('_Orbitdegree',0))*seconds
                position+=np.array([math.cos(angle),math.sin(angle),0])*data.get('_Orbitradius',0)
            angles=np.zeros(3);angles[min(2,int(data.get('RotateAxis',2)))]=rotation+speed*seconds
            transform=matrix(position,Rotation.from_euler('xyz',angles,degrees=True).as_quat(),size)
            rgba=np.r_[color(data['color'],age,rr[11]),scalar(data['alpha'],age,rr[0])]*tint
            if data.get('hasGradientColorEffector'):
                values=[rgb(v) for v in data['_colors']]
                if values:rgba[:3]*=np.array([sample(data['_colorRatios'],[v[k] for v in values],age) for k in range(3)])
                rgba[3]*=sample(data['_alphaRatios'],data['_alphas'],age)
            if data.get('hasAlphaEffector'):rgba[3]*=sample(data['_alphakeyFrameLifeTime'],data['_alphalst'],age)
            if data.get('hasColorEffector'):rgba[:3]*=[sample(data['_colorkeyFrameLifeTime'],data['_color'+axis],age) for axis in 'rgb']
            uv=np.array([0.,0.,1.,1.])
            if data.get('hasUVEffector'):
                uv[:2]=[sample(data['_uvkeyFrameLifeTime'],data['_'+axis],age,0) for axis in ['u','v']]
            if data.get('hasSpriteSheetEffector'):
                rows=max(1,data['rows']);cols=max(1,data['columns']);cells=max(1,data.get('realCount',0) or rows*cols)
                cell=min(cells-1,int(age*cells)); uv[:2]+=[cell%cols/cols,cell//cols/rows];uv[2:]=[1/cols,1/rows]
            particle_states.append(np.r_[transform[:3].reshape(-1),np.clip(rgba,0,1),uv])
        result.append(particle_states)
    return np.array(lifetimes,dtype=np.float32),np.array(result,dtype=np.float32)

def build():
    OUT.mkdir(parents=True,exist_ok=True);scenes={code:Scene(code) for code in (*GUNS,*('skin_darkdiamond_'+s for s in SKINS))}
    textures={};texture_rows=[];layers=[];variants=[];mesh_rows=[]
    with ZipFile(ROOT/'reference/mod.jar') as archive:
        runtime=gzip.decompress(archive.read('assets/ssjj_dark/runtime/weapon.bin.gz'))
        version,vertex_count,face_count=struct.unpack('>3i',runtime[:12]);assert version==1
        p3=np.frombuffer(runtime,dtype='>f4',count=vertex_count*3,offset=12).reshape(-1,3)*.01
    # Three widely separated source points reconstruct a stable rigid frame.
    a=int(np.argmin(p3[:,2]));b=int(np.argmax(np.linalg.norm(p3-p3[a],axis=1)))
    c=int(np.argmax(np.linalg.norm(np.cross(p3-p3[a],p3[b]-p3[a]),axis=1)))
    reference=p3[[a,b,c]];assert np.linalg.norm(np.cross(reference[1]-reference[0],reference[2]-reference[0]))>.001
    for grade,gun in enumerate(GUNS):
        for skin_index,skin in enumerate(('',*SKINS)):
            code='skin_darkdiamond_'+skin if skin else gun;scene=scenes[code]
            source_prefab,effect_root=scene.effect(gun)
            native_map,p1_mesh=attachment_matrix(scene,gun,'view',effect_root)
            avatar_map,p3_mesh=attachment_matrix(scene,gun,'third',effect_root)
            # Bind-space handedness matches the frozen, existing native blade geometry.
            native_units=np.diag([-.01,.01,.01,1]);avatar_units=np.diag([.01,.01,.01,1])
            native_map=native_units@native_map;avatar_map=avatar_units@avatar_map
            indices=[]
            selected=[(key,data) for key,data in scene.loop_systems() if scene.root(scene.go_transform[data['m_GameObject']['m_PathID']])==effect_root]
            assert selected
            for key,data in selected:
                material_id=data['mater']['m_PathID'];material=scene.objects[material_id].parse_as_dict()
                tex_envs=dict(material['m_SavedProperties']['m_TexEnvs']);main=tex_envs['_MainTex'];tid=main['m_Texture']['m_PathID'];texture=scene.objects[tid].parse_as_object().image.convert('RGBA')
                raw=texture.tobytes();identity=(texture.size,sha(raw))
                if identity not in textures:
                    texture_index=len(textures);textures[identity]=texture_index;name=f'blade_effects/t_{texture_index:02}.png';target=OUT/'textures'/name;target.parent.mkdir(parents=True,exist_ok=True);texture.save(target)
                    texture_rows.append({'id':'ssjj_dark:textures/'+name,'source_name':scene.objects[tid].peek_name(),'source_texture_id':tid,'size':list(texture.size),'rgba_sha256':sha(raw),'png_sha256':sha(target.read_bytes()),'processing':'Original decoded RGBA, unchanged pixel values'})
                texture_index=textures[identity]
                mesh_id=data['mesh']['m_PathID'];vertices,faces,uv=original_mesh(scene,mesh_id)
                # QUADS consumer: duplicate each triangle's third corner, preserving source UVs.
                corners=faces[:,[0,1,2,2]].reshape(-1)
                geometry=np.c_[vertices[corners],uv[corners]].astype(np.float32)
                system_world=scene.world(scene.go_transform[data['m_GameObject']['m_PathID']])
                native=(native_map@system_world)[:3];avatar=(avatar_map@system_world)[:3]
                life,table=states(data,material,key)
                layer={'texture':texture_index,'billboard':int(data['billboardType']),'native':native.astype(np.float32),'avatar':avatar.astype(np.float32),'geometry':geometry,'life':life,'states':table}
                indices.append(len(layers));layers.append(layer)
                mesh_rows.append({'variant':gun+('_'+skin if skin else ''),'source_prefab':source_prefab,'source_system_id':key,'path':scene.path(data),'material':scene.objects[material_id].peek_name(),'material_id':material_id,'material_properties':material['m_SavedProperties'],'source_mesh_id':mesh_id,'source_mesh_name':'Unity builtin Quad' if mesh_id==10210 else scene.objects[mesh_id].peek_name(),'source_vertices':len(vertices),'triangles':len(faces),'source_vertex_sha256':sha(np.asarray(vertices,dtype='>f4').tobytes()),'particles':len(life),'loop':data['loop'],'native_matrix':native.tolist(),'avatar_matrix':avatar.tolist()})
            vertices_per_draw=sum(len(layers[i]['geometry'])*len(layers[i]['life']) for i in indices)
            variants.append({'id':gun+('_'+skin if skin else ''),'grade':grade,'skin':skin_index,'source_prefab':source_prefab,'layers':indices,'vertices_per_draw':vertices_per_draw})
            print('BUILD',variants[-1]['id'],len(indices),'layers',vertices_per_draw,'vertices',flush=True)
    target=OUT/'runtime/blade_effects.bin.gz';target.parent.mkdir(parents=True,exist_ok=True)
    binary=io.BytesIO()
    def ints(*values):binary.write(struct.pack('>'+str(len(values))+'i',*values))
    def floats(value):binary.write(np.asarray(value,dtype='>f4').tobytes())
    ints(1,len(layers),len(variants),64);ints(a,b,c);floats(reference)
    for layer in layers:
        ints(layer['texture'],layer['billboard'],len(layer['geometry']),len(layer['life']))
        for key in ['native','avatar','geometry','life','states']:floats(layer[key])
    for variant in variants:ints(variant['grade'],variant['skin'],len(variant['layers']),*variant['layers'])
    raw=binary.getvalue();target.write_bytes(gzip.compress(raw,compresslevel=9,mtime=0))
    manifest={'layer_blends':[int(dict(row['material_properties']['m_Floats'])['_BlendDst']) for row in mesh_rows],'version':1,'phase_samples':64,'state_stride':20,'textures':texture_rows,'variants':variants,'reference_indices':[a,b,c],'reference_vertices':reference.tolist(),'native_units':'original binding space X reflection and 0.01 blocks/source-unit, exactly as existing native blade','avatar_units':'original asjj_p3 binding space at 0.01 blocks/source-unit; three-point rigid frame follows actual skill weaponPose','limits':['Original qiangshen custom-particle script parameters are deterministically sampled at 64 lifetime phases. Script source was not supplied; ramps/random emission are a compatible interpretation, not an assertion of original Unity engine equivalence.','Original source materials use additive custom shaders; TaCZ integration adapts them to additive fullbright rendering. Source textures, packed color palettes, local transform, mesh and loop ownership are preserved.','No attack trails, baofa or zhanshi skill particles are substituted for weapon-body effects.']}
    dump(OUT/'runtime/blade_effects.json',manifest)
    report={'passed':True,'textures':len(texture_rows),'layers':len(layers),'variants':variants,'source_layers':mesh_rows,'binary_uncompressed_bytes':len(raw),'binary_compressed_bytes':target.stat().st_size,'binary_sha256':sha(target.read_bytes()),'runtime_cached_bytes':sum(layer['states'].nbytes+layer['geometry'].nbytes+layer['life'].nbytes+96 for layer in layers),'reference_p3_points':[a,b,c],'game_started':False,'gpu_test':False,'limits':manifest['limits']}
    dump(EVIDENCE/'blade_effect_resources.json',report)

def audit():
    result = []
    for code in (*GUNS, *('skin_darkdiamond_' + skin for skin in SKINS)):
        scene = Scene(code)
        looped = scene.loop_systems()
        roots = Counter(scene.path(data).split('/')[0] for _, data in looped)
        print(code, dict(roots), flush=True)
        attachments = [data for data in scene.attachments if scene.names.get(data['instance']['m_PathID'], '').startswith('qiangshen')]
        result.append({'code': code, 'packages': [{'path': str(p.relative_to(WORKSPACE)), 'sha256': sha(p.read_bytes())} for p in scene.paths],
            'loop_roots': dict(roots), 'attachments': attachments,
            'systems': [{'id': key, 'path': scene.path(data), 'material_id': data['mater']['m_PathID'],
                'material': scene.objects[data['mater']['m_PathID']].peek_name(), 'mesh_id': data['mesh']['m_PathID'],
                'particles': data['ParticleNumber'], 'billboard_type': data['billboardType'], 'loop': data['loop'], 'prewarm': data.get('prewarm'),
                'local_transform': scene.transforms[scene.go_transform[data['m_GameObject']['m_PathID']]], 'parameters': data} for key, data in looped]})
    dump(EVIDENCE / 'blade_source_audit.json', {'source_only': True, 'families': result, 'excluded_roots': ['baofa', 'zhanshi', 'daoguang', 'skill', 'trail'], 'runtime_verified': False})
    return result

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf8')
    parser=argparse.ArgumentParser();parser.add_argument('--audit-only',action='store_true');args=parser.parse_args()
    audit()
    if not args.audit_only:build()
