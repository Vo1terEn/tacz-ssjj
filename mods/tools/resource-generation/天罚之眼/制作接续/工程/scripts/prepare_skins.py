"""Build four scope-selected displays solely from original weapon-skin assets.

The stage-2 GLB streams must exactly match the approved base weapon before its
converted rig is reused. Texture RGB and original inventory art are preserved.
This script creates display resources only; item/gun IDs and gameplay are owned
by the base gun. Original effect bindings are exported for the native FX port.
"""
from pathlib import Path
import copy
import csv
import hashlib
import json
import shutil
import struct
import sys
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image, ImageDraw

E = Path(__file__).resolve().parents[1]
P, R = E.parent, E.parent.parent
AS = E / 'gunpack/assets/eyeofpunishment'
WORK = P / 'work/skins'
SKINS = {
    'timetravel': ('时空旅行者', '时空旅行者', 4262052944826027888),
    'sakura': ('樱色雷霆_sakura', '樱色雷霆', 1767641784065370747),
    'anime': ('anime', '动漫', -7588228818748552515),
    'fire': ('fire', '火焰', 9169088939794474366),
}


def load(path):
    return json.loads(path.read_text('utf-8-sig'))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', 'utf-8')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rel(path):
    return path.relative_to(R).as_posix()


def exported(folder, row, suffix):
    paths = [folder / x for x in row['导出文件路径'].split(';') if x]
    return next(p for p in paths if p.suffix.lower() == suffix)


def stage2(rows, kind, suffix):
    """Select the original battle-view stage-2 asset, excluding lobby variants."""
    matches = [row for row in rows if row['类型'] == kind
               and '/assets/weaponskin/' in row['原始资源路径']
               and row['原始资源路径'].endswith('/notdistinguish2/' + suffix)]
    assert len(matches) == 1, (kind, suffix, len(matches))
    return matches[0]


def glb_streams(path):
    raw = path.read_bytes()
    size = struct.unpack_from('<I', raw, 12)[0]
    document = json.loads(raw[20:20 + size])
    binary = raw[size + 28:]
    primitive = max(document['meshes'], key=lambda m:
        document['accessors'][m['primitives'][0]['indices']]['count'])['primitives'][0]
    component = {5121: 'u1', 5123: '<u2', 5125: '<u4', 5126: '<f4'}
    dimensions = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}

    def accessor(index):
        a = document['accessors'][index]
        view = document['bufferViews'][a['bufferView']]
        dtype = np.dtype(component[a['componentType']])
        width = dimensions[a['type']]
        offset = view.get('byteOffset', 0) + a.get('byteOffset', 0)
        if view.get('byteStride', width * dtype.itemsize) != width * dtype.itemsize:
            return np.ndarray((a['count'], width), dtype, binary, offset,
                              (view['byteStride'], dtype.itemsize)).copy()
        return np.frombuffer(binary, dtype, a['count'] * width, offset).reshape(-1, width)

    streams = {name: accessor(index) for name, index in primitive['attributes'].items()}
    streams['indices'] = accessor(primitive['indices'])
    return streams


def fx_bindings(folder, rows, effect_root=None):
    if effect_root is None:
        relative_row = stage2(rows, 'TextAsset', 'effect/particle_relative.xml')
        effect_root = relative_row['原始资源路径'].rsplit('/', 1)[0]
    else:
        relative_row = next(row for row in rows if row['类型']=='TextAsset' and
                            row['原始资源路径'].lower()==(effect_root+'/particle_relative.xml').lower())
    logic_row = next(row for row in rows if row['类型']=='TextAsset' and
                     row['原始资源路径'].lower()==(effect_root+'/particle_logic.xml').lower())
    relative_path = exported(folder, relative_row, '.xml')
    logic_path = exported(folder, logic_row, '.xml')
    relative = ET.parse(relative_path).getroot()
    logic = ET.parse(logic_path).getroot()
    objects = {}
    for row in rows:
        if row['类型'] in ('GameObject', 'Transform', 'MonoBehaviour', 'MeshRenderer',
                           'MeshFilter', 'ParticleSystem', 'ParticleSystemRenderer', 'MonoScript'):
            path = exported(folder, row, '.json')
            objects[(row['来源资源包'][:10], int(path.stem.rsplit('__', 1)[1]))] = (path, row)

    def obj(bundle, reference):
        # Tree references are local. Material/mesh PPtrs retain their file IDs.
        item = objects.get((bundle, reference.get('m_PathID', 0)))
        return (load(item[0]), item[0], item[1]) if item else (None, None, None)

    def subtree(bundle, root):
        result = []
        seen = set()

        def visit(go_id):
            if go_id in seen:
                return
            seen.add(go_id)
            go, go_path, _ = obj(bundle, {'m_PathID': go_id})
            if not go:
                return
            node = {'name': go['m_Name'], 'gameObjectPathId': go_id,
                    'active': go['m_IsActive'], 'source': rel(go_path), 'components': []}
            children = []
            for item in go['m_Component']:
                value, path, row = obj(bundle, item['component'])
                if value is None:
                    continue
                component = {'type': row['类型'], 'pathId': item['component']['m_PathID'],
                             'source': rel(path)}
                if row['类型'] == 'Transform':
                    component['position'] = value['m_LocalPosition']
                    component['rotation'] = value['m_LocalRotation']
                    component['scale'] = value['m_LocalScale']
                    children = value.get('m_Children', [])
                elif row['类型'] == 'MonoBehaviour':
                    script, _, _ = obj(bundle, value['m_Script'])
                    component['script'] = script.get('m_ClassName') if script else None
                    if 'ParticleNumber' in value:
                        component['particle'] = {k: value[k] for k in (
                            'ParticleNumber', 'billboardType', 'mesh', 'mater',
                            'loop', 'lifeTime', 'startTime', 'sizeX', 'sizeY', 'sizeZ',
                            'uniformScale_', 'hasSpriteSheetEffector', 'rows', 'columns')
                            if k in value}
                    elif 'ssjjpaList' in value:
                        component['group'] = {k: value[k] for k in ('ssjjpaList', 'duration', 'autoPlay')}
                elif row['类型'] in ('MeshFilter', 'MeshRenderer'):
                    component['renderer'] = {k: v for k, v in value.items()
                                             if k in ('m_Mesh', 'm_Materials', 'm_Enabled')}
                node['components'].append(component)
            result.append(node)
            for reference in children:
                child, _, _ = obj(bundle, reference)
                if child:
                    visit(child['m_GameObject']['m_PathID'])

        visit(root)
        return result

    effects = []
    for group in relative:
        for item in group:
            name = item.attrib['name'].removesuffix('.uf3d')
            matching = [row for row in rows if row['类型'] == 'GameObject'
                        and row['原始资源路径'].lower()==
                        (effect_root+f'/{name}/prefab/{name}.prefab').lower()]
            assert len(matching) == 1, (folder, name, matching)
            row = matching[0]
            path = exported(folder, row, '.json')
            root_id = int(path.stem.rsplit('__', 1)[1])
            nodes = subtree(row['来源资源包'][:10], root_id)
            original_logic = next((x for x in logic.findall('logic')
                                   if x.find('ParticleTitleNode').get('Name') in
                                   (name, group.tag)), None)
            effects.append({'name': name, 'binding': group.tag,
                            'originalPath': row['原始资源路径'],
                            'bundle': row['来源资源包'], 'rootPathId': root_id,
                            'source': rel(path), 'nodes': nodes,
                            'logic': ET.tostring(original_logic, encoding='unicode')
                            if original_logic is not None else None})
    # Native group names differ on the base gun. Resolve their semantic stage
    # from the source EffectLevel expressions rather than renaming prefab IDs.
    for effect in effects:
        if effect['binding']=='weapon_particle_fire_effect':
            effect['stage']='fire'
            continue
        expressions=ET.fromstring(effect['logic']).findall('.//Expression')
        descriptions={x.get('Des') for x in expressions}
        if 'a&b>c' in descriptions:
            effect['stage']='qiangsheng'
        elif 'a&b==c' in descriptions:
            effect['stage']='qiangsheng01'
        else:
            bits={int(v.get('Input')) for x in expressions for v in x.findall('Value')
                  if v.get('Sign')=='c' and v.get('DataProvider')=='GetConstData'}
            assert len(bits)==1,(effect['name'],bits)
            effect['stage']={1:'qiangti',2:'qiangti01',3:'qiangti_2'}[bits.pop()]
    world = {'effect_root': effect_root, 'effects': [], 'bindings': []}
    config_rows = {Path(row['原始资源路径']).name.lower(): row for row in rows
                   if row['类型']=='TextAsset' and row['原始资源路径'].rsplit('/',1)[0]==effect_root}
    if 'effectconfig.xml' in config_rows and 'particleeffectconfig.xml' in config_rows:
        config_path=exported(folder,config_rows['effectconfig.xml'],'.xml')
        binding_path=exported(folder,config_rows['particleeffectconfig.xml'],'.xml')
        world.update(config=rel(config_path),particle_config=rel(binding_path))
        world['bindings']=[{'type':item.tag,**item.attrib} for item in ET.parse(binding_path).getroot()]
        for item in ET.parse(config_path).getroot():
            name=Path(item.get('url')).stem
            matching=[row for row in rows if row['类型']=='GameObject' and
                      row['原始资源路径'].lower()==(effect_root+f'/{name}/prefab/{name}.prefab').lower()]
            assert len(matching)==1,(folder,effect_root,name,matching)
            row=matching[0];path=exported(folder,row,'.json');root_id=int(path.stem.rsplit('__',1)[1])
            world['effects'].append({'name':name,'binding':item.get('name'),'originalPath':row['原始资源路径'],
                                     'bundle':row['来源资源包'],'rootPathId':root_id,'source':rel(path),
                                     'nodes':subtree(row['来源资源包'][:10],root_id),
                                     'contexts':[b for b in world['bindings'] if b.get('effectName')==item.get('name')]})
    # Timetravel's bit16 prefab contains the same native pink petal assets as
    # Sakura. Its original bit1 prefab is the blue data/ice/gold body effect.
    # The project has two states, so keep that native blue group in both states.
    time_blue='/skin_notdistinguish_timetravel/' in effect_root
    return {'relative': rel(relative_path), 'logic': rel(logic_path), 'effect_root':effect_root,
            'effects': effects, 'world':world,
            'projectStages':{'normal':['qiangsheng' if time_blue else 'qiangsheng01'],
                             'active':['qiangsheng' if time_blue else 'qiangsheng01','qiangti_2'],
                             'fire':['fire']},
            'stateNote': 'Visibility is specified by original EffectLevel expressions in logic. '
                         'qiangsheng bit0; qiangti bit1; qiangti01 bit2; qiangti_2 bit3; '
                         'qiangsheng01 uses bit4 and bit0. Do not infer skill state solely from names.'}


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    base_geo_path = AS / 'geo_models/gun/notdistinguish_geo.json'
    base_rig_path = AS / 'rig/weapon_mesh.json'
    base_display = load(AS / 'display/guns/notdistinguish_display.json')
    base_glb = R / '本体/骨骼动画/notdistinguish2/可导入模型_GLB/view/gun.glb'
    approved = glb_streams(base_glb)
    manifest = {'version': 1, 'selection': 'scope', 'gunId': 'eyeofpunishment:notdistinguish',
                'skins': {}}
    all_fx = {}
    cards = []
    for key, (directory, label, icon_id) in SKINS.items():
        folder = R / '皮肤' / directory
        prefix = 'skin_notdistinguish_' + key
        with (folder / '资源索引.csv').open(encoding='utf-8-sig', newline='') as stream:
            rows = list(csv.DictReader(stream))
        material_row = stage2(rows, 'Material', 'view/materials/notdistinguish2.mat')
        material_path = exported(folder, material_row, '.json')
        material = load(material_path)
        properties = material['m_SavedProperties']
        texture_id = dict(properties['m_TexEnvs'])['_MainTex']['m_Texture']['m_PathID']
        texture_paths = list((folder / '导出素材' / prefix).glob(f'*/贴图_PNG/*__{texture_id}.png'))
        icon_paths = list((folder / '导出素材' / prefix).glob(f'*/贴图_PNG/*__{icon_id}.png'))
        assert len(texture_paths) == len(icon_paths) == 1
        source_texture, source_icon = texture_paths[0], icon_paths[0]
        floats = dict(properties['m_Floats'])
        assert floats['_CullAlphaEnable'] == 0 and floats['_ZWrite'] == 1
        skin_glb = folder / f'骨骼动画/{prefix}/notdistinguish2/可导入模型_GLB/view/gun.glb'
        candidate = glb_streams(skin_glb)
        assert set(candidate) == set(approved), key
        identical = {name: bool(np.array_equal(approved[name], candidate[name])) for name in approved}
        assert all(identical.values()), (key, identical)
        stem = 'notdistinguish_' + key
        geo = load(base_geo_path)
        geo['minecraft:geometry'][0]['description']['identifier'] = 'geometry.' + stem
        # A separate final root is visited after the complete native gun tree.
        # It flushes opaque skin body vertices before submitting source FX at
        # the receiver pose captured during the same model traversal.
        bones = geo['minecraft:geometry'][0]['bones']
        assert not any(bone['name'] == 'skin_fx_end' for bone in bones)
        bones.append({'name': 'skin_fx_end', 'pivot': [0, 0, 0]})
        write(AS / f'geo_models/gun/{stem}_geo.json', geo)
        rig_path = AS / f'rig/{stem}.json'
        shutil.copy2(base_rig_path, rig_path)
        texture_path = AS / f'textures/gun/uv/{stem}.png'
        texture_path.parent.mkdir(parents=True, exist_ok=True)
        texture = Image.open(source_texture).convert('RGBA')
        source_rgb = np.array(texture)[:, :, :3].copy()
        texture.putalpha(255)
        texture.save(texture_path)
        assert np.array_equal(np.array(Image.open(texture_path))[:, :, :3], source_rgb)
        for location in ('hud', 'slot'):
            target = AS / f'textures/gun/{location}/{stem}.png'
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_icon, target)
            assert sha(target) == sha(source_icon)
        display = copy.deepcopy(base_display)
        display.update(model=f'eyeofpunishment:gun/{stem}_geo',
                       texture=f'eyeofpunishment:gun/uv/{stem}',
                       hud=f'eyeofpunishment:gun/hud/{stem}',
                       slot=f'eyeofpunishment:gun/slot/{stem}')
        # The matching native fire definition is rendered on the actual fire
        # event. Missing native muzzle_flash parses to null, avoiding a second
        # generic flash over the skin's original source particles.
        display.pop('muzzle_flash', None)
        write(AS / f'display/guns/{stem}_display.json', display)
        effects = fx_bindings(folder, rows)
        all_fx[key] = effects
        manifest['skins'][key] = {
            'label': label, 'scopeId': f'eyeofpunishment:skin_{key}',
            'display': f'eyeofpunishment:{stem}_display', 'model': display['model'],
            'texture': display['texture'], 'mesh': f'eyeofpunishment:rig/{stem}.json',
            'hud': display['hud'], 'slot': display['slot'],
            'source': {'material': rel(material_path), 'materialOriginalPath': material_row['原始资源路径'],
                       'texture': rel(source_texture), 'texturePathId': texture_id,
                       'textureSha256': sha(source_texture), 'outputTextureSha256': sha(texture_path),
                       'textureConversion': 'RGB unchanged; alpha255 matches original opaque '
                                            'material CullAlphaEnable0/ZWrite1.',
                       'icon': rel(source_icon), 'iconPathId': icon_id, 'iconSha256': sha(source_icon),
                       'iconSize': list(Image.open(source_icon).size),
                       'glb': rel(skin_glb), 'glbSha256': sha(skin_glb),
                       'baseGlb': rel(base_glb), 'streamsExactlyEqualToBase': identical,
                       'vertices': len(candidate['POSITION']), 'triangles': len(candidate['indices']) // 3,
                       'approvedRigSha256': sha(base_rig_path), 'outputRigSha256': sha(rig_path),
                       'approvedGeoSha256': sha(base_geo_path)},
            'effects': {'relative': effects['relative'], 'logic': effects['logic'],
                        'prefabs': [{k: effect[k] for k in ('name', 'binding', 'bundle', 'rootPathId', 'source')}
                                    for effect in effects['effects']]}}
        card = Image.new('RGBA', (450, 140), '#252434')
        ImageDraw.Draw(card).text((12, 8), key, fill='white')
        icon = Image.open(source_icon).convert('RGBA')
        icon.thumbnail((420, 100))
        card.alpha_composite(icon, ((450 - icon.width) // 2, 34))
        cards.append(card)
    write(AS / 'rig/skins.json', manifest)
    write(WORK / 'skin_sources.json', manifest)
    base_folder=R/'本体'
    with (base_folder/'资源索引.csv').open(encoding='utf-8-sig',newline='') as stream:
        base_rows=list(csv.DictReader(stream))
    all_fx['base']=fx_bindings(base_folder,base_rows,'assets/res/assets/weapon/notdistinguish2/effect')
    # Base E014 skill FX and its firing effect already have independent runtime
    # paths. Export only its missing original ambient, never a second E014.
    all_fx['base']['projectStages']={'normal':['qiangsheng01'],'active':['qiangsheng01']}
    write(WORK / 'fx_bindings.json', all_fx)
    write(WORK / 'fx_native/world_bindings.json', {key:value['world'] for key,value in all_fx.items()})
    sheet = Image.new('RGBA', (900, 280))
    for i, card in enumerate(cards):
        sheet.alpha_composite(card, (i % 2 * 450, i // 2 * 140))
    sheet.convert('RGB').save(WORK / 'original_skin_icons.png')
    print('Prepared four original scope-selected skin displays; all six mesh streams match base.')
    print('FX binding evidence: ' + str(WORK / 'fx_bindings.json'))


def prepare_fx_bindings():
    """Rebuild source FX evidence without rewriting body/display resources."""
    all_fx={}
    for key,(directory,_,_) in SKINS.items():
        folder=R/'皮肤'/directory
        with (folder/'资源索引.csv').open(encoding='utf-8-sig',newline='') as stream:
            rows=list(csv.DictReader(stream))
        all_fx[key]=fx_bindings(folder,rows)
    folder=R/'本体'
    with (folder/'资源索引.csv').open(encoding='utf-8-sig',newline='') as stream:
        rows=list(csv.DictReader(stream))
    all_fx['base']=fx_bindings(folder,rows,'assets/res/assets/weapon/notdistinguish2/effect')
    all_fx['base']['projectStages']={'normal':['qiangsheng01'],'active':['qiangsheng01']}
    write(WORK/'fx_bindings.json',all_fx)
    write(WORK/'fx_native/world_bindings.json',{key:value['world'] for key,value in all_fx.items()})
    print('Prepared four skins plus base source FX bindings, including unique world effect configs.')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    if '--fx-only' in sys.argv:prepare_fx_bindings()
    else:main()
