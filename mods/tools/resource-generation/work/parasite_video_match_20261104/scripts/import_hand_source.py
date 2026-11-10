"""Inspect/import the original Parasite II hand mesh; never alter production.

Only 寄生者/本体 is read as asset input.  GLB streams are independently
checked against original Unity Mesh bytes, renderer bone IDs, and bind poses.
The extracted geometry retains all four source weights and all finger bones.
The declared material is traced, not assumed to be the original game's skin.
"""
from __future__ import annotations
import csv,hashlib,io,json,struct
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from scipy.spatial.transform import Rotation,Slerp

WORK=Path(__file__).resolve().parents[1]
PROJECT=WORK.parents[1]
RAW=PROJECT/'寄生者/本体'
RIG=RAW/'骨骼动画/parasitic_sniper2'
INPUTS={}
J=np.diag([-1.,1.,1.,1.])

def raw_bytes(path):
    path=Path(path).resolve()
    assert path.is_relative_to(RAW.resolve()),path
    data=path.read_bytes();INPUTS[path.relative_to(PROJECT).as_posix()]=hashlib.sha256(data).hexdigest()
    return data

def load(path):return json.loads(raw_bytes(path).decode('utf-8-sig'))
def save(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

class Catalog:
    def __init__(self):
        self.rows=list(csv.DictReader(raw_bytes(RAW/'资源索引.csv').decode('utf-8-sig').splitlines()))
        self.by_id={};self.types={};self.cache={}
        for row in self.rows:
            for name in row['导出文件路径'].split(';'):
                path=RAW/name.replace('\\','/')
                if not path.is_relative_to(RAW/'导出素材/parasitic_sniper2'):continue
                self.types.setdefault(row['类型'],set()).add(path)
                try:key=int(path.stem.rsplit('__',1)[1])
                except ValueError:continue
                self.by_id.setdefault(key,[]).append(path)
    def find(self,key,suffix='.json',bundle=None):
        paths=[p for p in self.by_id[int(key)] if p.suffix.lower()==suffix and (bundle is None or p.relative_to(RAW).parts[2]==bundle)]
        assert len(paths)==1,(key,paths)
        return paths[0]
    def read(self,path):
        if path not in self.cache:self.cache[path]=load(path)
        return self.cache[path]

class Glb:
    def __init__(self,path):
        data=raw_bytes(path);magic,version,length=struct.unpack_from('<III',data)
        assert (magic,version,length)==(0x46546c67,2,len(data))
        offset=12;self.binary=b''
        while offset<len(data):
            size,kind=struct.unpack_from('<II',data,offset);part=data[offset+8:offset+8+size];offset+=8+size
            if kind==0x4e4f534a:self.doc=json.loads(part)
            elif kind==0x004e4942:self.binary=part
    def accessor(self,index):
        a=self.doc['accessors'][index];assert 'sparse' not in a
        view=self.doc['bufferViews'][a['bufferView']]
        dtype=np.dtype({5120:'i1',5121:'u1',5122:'<i2',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']])
        n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
        result=np.ndarray((a['count'],n),dtype=dtype,buffer=self.binary,offset=view.get('byteOffset',0)+a.get('byteOffset',0),strides=(view.get('byteStride',n*dtype.itemsize),dtype.itemsize)).copy()
        if a.get('normalized'):result=np.maximum(result.astype(float)/np.iinfo(dtype).max,-1)
        return result

def unity_mesh(mesh):
    data=mesh['m_VertexData'];channels=data['m_Channels'];count=data['m_VertexCount']
    raw=bytes.fromhex(data['m_DataSize']['__bytes_hex__'])
    formats={0:'<f4',1:'<f2',2:'u1',3:'i1',4:'<u2',5:'<i2',6:'u1',7:'i1',8:'<u2',9:'<i2',10:'<u4',11:'<i4'}
    strides={}
    for c in channels:
        n=c['dimension']&15
        if n:strides[c['stream']]=max(strides.get(c['stream'],0),c['offset']+n*np.dtype(formats[c['format']]).itemsize)
    starts={};cursor=0
    for stream,stride in sorted(strides.items()):
        cursor=(cursor+15)//16*16;starts[stream]=cursor;cursor+=stride*count
    assert cursor==len(raw)
    def channel(i):
        c=channels[i];dtype=np.dtype(formats[c['format']]);n=c['dimension']&15
        result=np.ndarray((count,n),dtype=dtype,buffer=raw,offset=starts[c['stream']]+c['offset'],strides=(strides[c['stream']],dtype.itemsize)).copy()
        if c['format'] in (2,3,4,5):result=np.maximum(result.astype(float)/np.iinfo(dtype).max,-1)
        return result
    bind=np.array([[[b[f'e{i}{j}'] for j in range(4)]for i in range(4)]for b in mesh['m_BindPose']])
    return {'positions':channel(0),'normals':channel(1),'uv':channel(4),'weights':channel(12),'joints':channel(13),
            'triangles':np.frombuffer(bytes(mesh['m_IndexBuffer']),dtype='<u2' if mesh['m_IndexFormat']==0 else '<u4').reshape(-1,3),'bind':bind}

def parse_anm(path):
    data=raw_bytes(path);offset=0
    def num(fmt):
        nonlocal offset
        v=struct.unpack_from('<'+fmt,data,offset);offset+=struct.calcsize('<'+fmt);return v[0] if len(v)==1 else v
    def string():
        nonlocal offset
        size=num('H');v=data[offset:offset+size].decode('utf8');offset+=size;return v
    version=num('i');clips=[]
    for _ in range(num('i')):
        clip={'name':string(),'blends':[]}
        for _ in range(num('i')):
            blend={'name':string(),'tracks':[]};tracks=num('i');blend['duration']=num('f')
            for _ in range(tracks):
                track={'bone':string(),'keyframes':[]}
                for _ in range(num('i')):
                    f=num('8f');track['keyframes'].append({'time':f[0],'translation':list(f[1:4]),'rotation_xyzw':list(f[4:8])})
                blend['tracks'].append(track)
            clip['blends'].append(blend)
        clips.append(clip)
    assert offset==len(data)
    return {'version':version,'bytes_consumed':offset,'clips':clips}

def trs(t,q,s=(1,1,1)):
    if isinstance(t,dict):t=[t[a]for a in 'xyz']
    if isinstance(q,dict):q=[q[a]for a in 'xyzw']
    if isinstance(s,dict):s=[s[a]for a in 'xyz']
    m=np.eye(4);m[:3,:3]=Rotation.from_quat(q).as_matrix()@np.diag(s);m[:3,3]=t;return m

class HandRig:
    def __init__(self,catalog,bundle='271d4bbb72'):
        folder=RAW/f'导出素材/parasitic_sniper2/{bundle}/结构数据_JSON'
        self.objects={int(p.stem.rsplit('__',1)[1]):catalog.read(p)for p in (folder/'GameObject').glob('*.json')}
        all_t={int(p.stem.rsplit('__',1)[1]):catalog.read(p)for p in (folder/'Transform').glob('*.json')}
        root=next(k for k,t in all_t.items()if self.objects[t['m_GameObject']['m_PathID']]['m_Name']=='hand_man')
        chosen=set()
        def descend(k):
            chosen.add(k)
            for child in all_t[k]['m_Children']:
                if child['m_PathID'] in all_t:descend(child['m_PathID'])
        descend(root)
        self.transforms={k:all_t[k]for k in chosen};self.names={k:self.objects[t['m_GameObject']['m_PathID']]['m_Name']for k,t in self.transforms.items()}
        self.by_name={v:k for k,v in self.names.items()};assert len(self.by_name)==len(self.names)
        self.defaults={k:J@trs(t['m_LocalPosition'],t['m_LocalRotation'],t['m_LocalScale'])@J for k,t in self.transforms.items()}
        binary=parse_anm(RIG/'原生动画与动作配置/view/hand_man.anm.bytes');parsed=load(RIG/'解析动画_JSON/hand_man.animations.json')
        assert binary['clips']==parsed['clips']
        self.animations={clip['name']:clip['blends'][0]for clip in binary['clips']}
        self.tracks={}
        for name,clip in self.animations.items():
            self.tracks[name]={}
            for t in clip['tracks']:
                frames=t['keyframes'];times=np.array([f['time']for f in frames]);p=np.array([f['translation']for f in frames]);q=np.array([f['rotation_xyzw']for f in frames])
                self.tracks[name][t['bone']]=(times,p,q,Slerp(times,Rotation.from_quat(q)) if len(times)>1 else None)
    def globals(self,clip,time):
        cache={}
        def visit(key):
            if key in cache:return cache[key]
            m=self.defaults[key];name=self.names[key]
            if name in self.tracks[clip]:
                times,p,q,s=self.tracks[clip][name];t=float(np.clip(time,times[0],times[-1]));translation=np.array([np.interp(t,times,p[:,i])for i in range(3)])
                m=trs(translation,s(t).as_quat() if s is not None else q[0])
            parent=self.transforms[key]['m_Father']['m_PathID'];cache[key]=(visit(parent)@m if parent in self.transforms else m);return cache[key]
        for key in self.transforms:visit(key)
        return {self.names[key]:m for key,m in cache.items()}

def lbs(positions,joints,weights,bind,globals_by_name,bones):
    skin=np.array([globals_by_name[name]@bind[i]for i,name in enumerate(bones)])
    p=np.column_stack((positions,np.ones(len(positions))))
    return sum(weights[:,i,None]*np.einsum('vij,vj->vi',skin[joints[:,i]],p)[:,:3]for i in range(4))

def png_content_catalog():
    """Include every PNG by content, including opaque and alpha-only images."""
    files=sorted(RAW.rglob('*.png'));catalog=[];sheets=[]
    for start in range(0,len(files),40):
        sheet=Image.new('RGB',(1280,900),(20,24,32));draw=ImageDraw.Draw(sheet)
        for offset,path in enumerate(files[start:start+40]):
            data=raw_bytes(path);im=Image.open(io.BytesIO(data)).convert('RGBA')
            number=start+offset;col=offset%8;row=offset//8;origin=(col*160,row*180)
            checker=Image.new('RGBA',im.size,(95,99,108,255));dc=ImageDraw.Draw(checker)
            for y in range(0,im.height,24):
                for x in range(0,im.width,24):
                    if (x//24+y//24)%2:dc.rectangle((x,y,x+23,y+23),fill=(140,144,152,255))
            checker.alpha_composite(im);thumb=checker.convert('RGB');thumb.thumbnail((152,146));sheet.paste(thumb,(origin[0]+(160-thumb.width)//2,origin[1]+(146-thumb.height)//2))
            label=f'{number:03} {path.stem.split("__")[0]}'
            draw.text((origin[0]+4,origin[1]+148),label[:24],fill='white');draw.text((origin[0]+4,origin[1]+162),f'{im.width} x {im.height}',fill=(180,190,205))
            catalog.append({'index':number,'path':path.relative_to(PROJECT).as_posix(),'sha256':hashlib.sha256(data).hexdigest(),'size':list(im.size),'decoded_rgba_sha256':hashlib.sha256(im.tobytes()).hexdigest(),'alpha_range':list(im.getchannel('A').getextrema())})
        target=WORK/f'previews/all_png_content_{start//40:02}.png';target.parent.mkdir(parents=True,exist_ok=True);sheet.save(target);sheets.append(target.relative_to(WORK).as_posix())
    report={'status':'CATALOGED_ALL_ORIGINAL_PNG_CONTENT','count':len(files),'files':catalog,'contact_sheets':sheets,'boundary':'All images are decoded and shown over checkerboards. Visual review is a separate step; filenames do not prove presence or absence of a hand skin.'}
    save(WORK/'evidence/all_original_png_content.json',report);return report

def hand_reference_preview(rig,positions,joints,weights,bind,bones,triangles):
    g=rig.globals('TORSO_STAND',0);basis=np.array([[0.,-1,0,0],[0,0,1,0],[-1,0,0,0]])@np.linalg.inv(g['WeaponAnchor2'])
    original=lbs(positions,joints,weights,bind,g,bones);vertices=(original@basis[:3,:3].T+basis[:3,3])*.01
    joints_native={name:(basis@value)[:3,3]*.01 for name,value in g.items()}
    output=Image.new('RGB',(1600,800),(24,29,39))
    for panel,eye in enumerate((np.array([-.8,.4,1.]),np.array([.8,.2,.7]))):
        center=(vertices.min(0)+vertices.max(0))*.5;forward=center-eye;forward/=np.linalg.norm(forward);right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
        camera=np.array([right,up,forward]);p=(vertices-center)@camera.T
        scale=min(700/np.ptp(p[:,0]),650/np.ptp(p[:,1]));xy=p[:,:2]*[scale,-scale]+[panel*800+400,430]
        draw=ImageDraw.Draw(output)
        for triangle in sorted(triangles,key=lambda face:-float(p[face,2].mean())):
            t=vertices[triangle];normal=np.cross(t[1]-t[0],t[2]-t[0]);normal/=max(np.linalg.norm(normal),1e-20);light=.55+.45*abs(float(normal@np.array([.3,.7,.64])));color=int(190*light)
            draw.polygon([tuple(v)for v in xy[triangle]],fill=(color,color,color+min(8,255-color)))
        for key,name in rig.names.items():
            if not name.startswith(('Bip01_L_','Bip01_R_')):continue
            parent=rig.transforms[key]['m_Father']['m_PathID']
            if parent not in rig.names:continue
            parent_name=rig.names[parent]
            if not parent_name.startswith(('Bip01_L_','Bip01_R_')):continue
            q=(np.array([joints_native[name],joints_native[parent_name]])-center)@camera.T;q=q[:,:2]*[scale,-scale]+[panel*800+400,430]
            draw.line([tuple(v)for v in q],fill=(70,190,255)if '_L_'in name else(255,155,75),width=2)
        draw.text((panel*800+20,20),'ORIGINAL HAND MESH + SOURCE STAND JOINTS',fill='white')
        draw.text((panel*800+20,42),'Neutral material only; original video skin has not been identified',fill=(200,210,225))
    path=WORK/'previews/source_hands_stand_untextured.png';output.save(path);return path

def main():
    catalog=Catalog();glb_path=RIG/'可导入模型_GLB/view/hand_man.glb';glb=Glb(glb_path);d=glb.doc
    assert len(d['meshes'])==len(d['skins'])==1
    primitive=d['meshes'][0]['primitives'][0];skin=d['skins'][0]
    streams={key:glb.accessor(value)for key,value in primitive['attributes'].items()}
    p=streams['POSITION'].astype(float);n=streams['NORMAL'].astype(float);uv=streams['TEXCOORD_0'].astype(float);j=streams['JOINTS_0'].astype(int);w=streams['WEIGHTS_0'].astype(float)
    triangles=glb.accessor(primitive['indices']).reshape(-1,3).astype(int)
    ibm=glb.accessor(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1).astype(float)
    bones=[d['nodes'][i]['name']for i in skin['joints']]
    mesh_path=next(x for x in catalog.types['Mesh']if x.name=='PlayerSkin__6420939380718130901.json');unity=unity_mesh(catalog.read(mesh_path))
    renderers=[path for path in catalog.types['SkinnedMeshRenderer']if path.suffix=='.json' and catalog.read(path).get('m_Mesh',{}).get('m_PathID')==6420939380718130901]
    assert len(renderers)==1;renderer_path=renderers[0];renderer=catalog.read(renderer_path);bundle=renderer_path.relative_to(RAW).parts[2]
    raw_bones=[]
    for b in renderer['m_Bones']:
        t=catalog.read(catalog.find(b['m_PathID'],bundle=bundle));obj=catalog.read(catalog.find(t['m_GameObject']['m_PathID'],bundle=bundle));raw_bones.append(obj['m_Name'])
    assert bones==raw_bones
    errors={'positions':float(abs(p-unity['positions']@J[:3,:3]).max()),'normals':float(abs(n-unity['normals']@J[:3,:3]).max()),
            'uv':float(abs(uv-unity['uv']*[1,-1]-[0,1]).max()),'weights':float(abs(w-unity['weights']).max()),'inverse_bind_matrices':float(abs(ibm-J@unity['bind']@J).max())}
    assert all(e<1e-7 for key,e in errors.items() if key!='weights') and errors['weights']<2e-7,errors
    assert np.array_equal(j,unity['joints']) and np.array_equal(triangles,unity['triangles'][:,[0,2,1]])
    assert np.max(abs(w.sum(axis=1)-1))<1e-6 and np.all(w>=0)
    # The GLB exporter changes 66 weight components by at most one/two float
    # ULPs. Runtime output uses the exact original Unity weight stream instead.
    glb_weights=w.copy();w=unity['weights'].astype(float)
    material_path=catalog.find(renderer['m_Materials'][0]['m_PathID'],bundle=bundle);material=catalog.read(material_path)
    tex=dict(material['m_SavedProperties']['m_TexEnvs'])['_MainTex'];texture_path=catalog.find(tex['m_Texture']['m_PathID'],suffix='.png');texture=Image.open(io.BytesIO(raw_bytes(texture_path))).convert('RGBA')
    slots=[]
    for i,name in enumerate(bones):slots.append({'bone':name,'positive_weight_vertices':int(np.count_nonzero(np.any((j==i)&(w>0),axis=1))),'total_vertex_weight':float(np.where(j==i,w,0).sum())})
    local_p=np.einsum('vaij,vj->vai',ibm[j],np.column_stack((p,np.ones(len(p)))))[:,:,:3]*.01
    local_n=np.einsum('vaij,vj->vai',np.linalg.inv(ibm[:,:3,:3]).transpose(0,2,1)[j],n)
    out=WORK/'generated/hand_source.json'
    geometry={'mesh':'PlayerSkin','unit':'positions and inverse_bind_matrices retain original source centimetres; bindlocal_positions are explicitly blocks (.01 cm); normals are dimensionless','coordinate_frame':'original glTF compatible frame; no native camera/basis adaptation baked into mesh',
        'skinning':'Runtime sum_i joint_weights_i * native_joint_global_blocks[bone_i] * homogeneous(bindlocal_positions[vertex][slot]); native normal matrices act on bindlocal_normals before final weighted normalization',
        'weight_source':'exact original Unity Mesh float32 stream; GLB exporter has minor float rounding differences',
        'bone_names':bones,'positions':p.tolist(),'normals':n.tolist(),'uv':uv.tolist(),'joint_indices':j.tolist(),'joint_weights':w.tolist(),'inverse_bind_matrices':ibm.tolist(),'triangles':triangles.tolist(),
        'bindlocal_positions':local_p.tolist(),'bindlocal_normals':local_n.tolist()}
    save(out,geometry)
    rig=HandRig(catalog);checks=[];maximum=0.;vertices=0
    for clip,animation in rig.animations.items():
        times=sorted({float(f['time'])for t in animation['tracks']for f in t['keyframes']});times=sorted(set(times+[.5*(a+b)for a,b in zip(times,times[1:])]))
        error=0.
        for t in times:
            globals_=rig.globals(clip,t);actual=lbs(p,j,w,ibm,globals_,bones)
            # Independent Unity-space equation, reflected only after skinning.
            unity_globals={name:J@value@J for name,value in globals_.items()}
            expected=lbs(unity['positions'],unity['joints'],unity['weights'],unity['bind'],unity_globals,raw_bones)@J[:3,:3]
            e=float(abs(actual-expected).max());error=max(error,e);maximum=max(maximum,e);vertices+=len(p)
        checks.append({'clip':clip,'sampled_original_keys_and_midpoints':len(times),'maximum_vertex_error_source_cm':error})
    assert maximum<1e-5
    preview=WORK/'previews';preview.mkdir(parents=True,exist_ok=True)
    layout=Image.new('RGB',(1024,1024),(28,31,38));overlay=texture.convert('RGB').resize((1024,1024));dl=ImageDraw.Draw(layout);do=ImageDraw.Draw(overlay)
    for tri in triangles:
        xy=[tuple(v)for v in uv[tri]*1023];dl.line(xy+[xy[0]],fill=(153,217,255),width=1);do.line(xy+[xy[0]],fill=(255,65,223),width=1)
    layout.save(preview/'hand_uv_layout.png');overlay.save(preview/'hand_uv_on_declared_material.png')
    reference=hand_reference_preview(rig,p,j,w,ibm,bones,triangles)
    pngs=png_content_catalog()
    active={r['bone']for r in slots if r['positive_weight_vertices']}
    report={'status':'PASS_ORIGINAL_HAND_GEOMETRY','production_modified':False,'input_sha256':INPUTS,'new_importer_source_sha256':sha(Path(__file__)),
        'source_glb':glb_path.relative_to(PROJECT).as_posix(),'mesh_vertices':len(p),'mesh_triangles':len(triangles),'skin_bones':len(bones),'used_bones':len(active),'maximum_influences_per_vertex':int((w>0).sum(axis=1).max()),
        'influence_histogram':{str(i):int(np.count_nonzero((w>0).sum(axis=1)==i))for i in range(1,5)},'maximum_weight_sum_error':float(abs(w.sum(axis=1)-1).max()),'bone_usage':slots,
        'independent_glb_vs_original_unity':{'maximum_component_errors':errors,'glb_weight_components_different_from_raw':int(np.count_nonzero(glb_weights!=w)),'runtime_output_uses_exact_raw_weights':True,'joint_order_matches_renderer_bone_ids':True,'joint_indices_exact':True,'reflected_triangle_winding_exact':True,'uv_rule':'(Unity.u,1-Unity.v)'},
        'independent_lbs':{'equation':'Unity original vertices and bind matrices in Unity space, then J reflection, versus GLB vertices and IBM in glTF-compatible ANM frame','vertex_comparisons':vertices,'maximum_vertex_error_source_cm':maximum,'clips':checks},
        'material_lineage':{'renderer':renderer_path.relative_to(PROJECT).as_posix(),'material':material_path.relative_to(PROJECT).as_posix(),'material_name':material['m_Name'],'texture':texture_path.relative_to(PROJECT).as_posix(),'texture_size':list(texture.size),'texture_transform':tex,'glb_material_count':len(d.get('materials',[])),'glb_image_count':len(d.get('images',[])),'original_properties':material['m_SavedProperties'],
            'evidence_boundary':'The declared material resolves to the gun atlas. It is not evidence of the original video player/hand skin; a runtime material replacement is possible but is not proved by these asset files. All source PNGs receive content previews for separate visual review; filename alone is not used to exclude a possible skin.'},
        'render_recommendation':{'geometry':'Keep all original weights and finger joints; CPU LBS is 2230 vertices with <=4 influences each, then emit triangles as degenerate quads into the existing supplied VertexConsumer.',
            'bones':'Native model can carry flattened source hand bone poses in blocks; evaluate all weighted joints in the same actual animation instance/time as the gun. Never apply the current vanilla arm-contact retargeting to these source hand bones.',
            'texture':'Resolve video hand skin first. Put resolved skin and gun textures in one explicit atlas with remapped UV, avoiding material switches or a custom buffer.',
            'visibility':'Functional render callback returns unless ItemDisplayContext.firstPerson() && model.getRenderHand(). TaCZ refit disables renderHand. Replace lefthand_pos/righthand_pos callbacks with per-gun no-op to suppress vanilla cube arms without changing global player rendering.',
            'forbidden_operations':'No GL/RenderSystem/shader/render target/custom buffer/batch flush calls.'},
        'png_content_catalog':{'count':pngs['count'],'report':'evidence/all_original_png_content.json','report_sha256':sha(WORK/'evidence/all_original_png_content.json'),'visual_review_complete':False},
        'outputs':{'mesh':out.relative_to(WORK).as_posix(),'mesh_sha256':sha(out),'uv_layout':'previews/hand_uv_layout.png','declared_material_uv_overlay':'previews/hand_uv_on_declared_material.png','neutral_source_hand_reference':reference.relative_to(WORK).as_posix()}}
    save(WORK/'evidence/source_hand_import.json',report)
    print(json.dumps({k:report[k]for k in ('status','mesh_vertices','mesh_triangles','skin_bones','used_bones','influence_histogram','maximum_weight_sum_error','outputs')},ensure_ascii=False))

if __name__=='__main__':main()
