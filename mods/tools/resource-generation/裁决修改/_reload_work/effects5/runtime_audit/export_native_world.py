"""Compact the exact world source export; keep native meshes, PS curves, material slots and clip."""
from pathlib import Path
import json
import shutil
import copy
import numpy as np
import subprocess
import sys
import math
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler
import ast

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / 'asset_audit/world_native'
OUTPUT = HERE / 'resources'


def main():
    # Source curve sampling helpers only; never execute another exporter's main entry point.
    helper_tree=ast.parse((HERE.parent/'asset_audit/export_native_body.py').read_text('utf8'))
    helper_scope=dict(np=np,math=math)
    exec(compile(ast.Module(body=[n for n in helper_tree.body if isinstance(n,ast.FunctionDef)and n.name in {'hermite','gradient','sampled_tree'}],type_ignores=[]),'native_source_curves','exec'),helper_scope)
    native_samples=helper_scope['sampled_tree']
    mesh_bundles = {}
    mesh_color_cache = {}
    mesh_color_audit = []
    def vertex_colors(mesh):
        if mesh.get('colors'):
            return mesh['colors']
        bundle = mesh.get('bundle', mesh.get('source_bundle'))
        mesh_id = mesh.get('id')
        if not bundle or mesh_id is None:
            return None
        key = (bundle, mesh_id)
        if key not in mesh_color_cache:
            if bundle not in mesh_bundles:
                mesh_bundles[bundle] = UnityPy.load(str(HERE.parents[2] / '特效待确认_全量目录/原始资源包' / (bundle + '.unity3d')))
            obj = next(obj for obj in mesh_bundles[bundle].objects if obj.path_id == mesh_id)
            handler = MeshHandler(obj.read());handler.process()
            values = np.asarray(handler.m_Colors, dtype=float) / 255 if handler.m_Colors is not None else None
            mesh_color_cache[key] = values.tolist() if values is not None else None
            mesh_color_audit.append({'bundle': bundle, 'mesh_id': mesh_id,
                                     'vertex_count': len(handler.m_Vertices),
                                     'color_count': len(values) if values is not None else 0,
                                     'alpha_range': [float(values[:, 3].min()), float(values[:, 3].max())] if values is not None else None})
        return mesh_color_cache[key]
    subprocess.run([sys.executable, str(HERE / 'build_extra_world.py')], check=True, stdout=subprocess.DEVNULL)
    shutil.copytree(SOURCE / 'resources', OUTPUT, dirs_exist_ok=True)
    data = json.loads((SOURCE / 'world_runtime_config.json').read_text(encoding='utf8'))
    projectile = SOURCE.parent / 'crown_position_sources/projectile_native'
    extra = json.loads((projectile / 'world_runtime_config.json').read_text(encoding='utf8'))
    offset = len(data['materials'])
    data['materials'].extend(extra['materials'])
    for key, effect in extra['effects'].items():
        for kind in ('groups', 'emitters', 'trails'):
            for node in effect[kind]:
                node['material'] += offset
        data['effects'][key] = effect
    data['source_event_mount'] = extra['source_event_mount']
    data['sizes_semantics'] = extra['sizes_semantics']
    shutil.copytree(projectile / 'resources', OUTPUT, dirs_exist_ok=True)
    custom_source = json.loads((SOURCE.parent / 'source_native_e010.json').read_text(encoding='utf8'))
    custom_config = json.loads((HERE / 'extra_world/world_runtime_config.json').read_text(encoding='utf8'))
    custom_offset = len(data['materials'])
    data['materials'].extend(custom_config['materials'])
    material_ids = {m['key']: i + custom_offset for i, m in enumerate(custom_config['materials'])}
    shutil.copytree(HERE / 'extra_world/resources', OUTPUT, dirs_exist_ok=True)
    custom_effect = dict(groups=[], emitters=[], trails=[], source_path=custom_source['source_path'],
                         original_nodes=custom_source['nodes'], controller_duration=.5)
    def scalar(v, r=.5):
        if v.get('oneDType') == 2:
            return v.get('_min', 0) * (1-r) + v.get('_max', 0) * r
        if v.get('oneDType') == 1 and v.get('_anchors'):
            return float(np.interp(r, [a['_time'] for a in v['_anchors']], [a['_value'] for a in v['_anchors']]))
        return v.get('_value', 0)
    def packed_rgb(value):
        return [(value >> shift & 255) / 255 for shift in (16, 8, 0)]
    for source_node in custom_source['nodes']:
        if not source_node.get('material_keys'):
            continue
        raw = source_node['scripts_raw'][0]
        transform = np.diag([.01, .01, .01, 1]) @ np.array(source_node['prefab_world'])
        color = packed_rgb(raw['color']['_value'])
        phases = np.linspace(0, 1, 65)
        colors = []
        sizes = []
        for phase in phases:
            alpha = scalar(raw['alpha'])
            if raw.get('hasGradientColorEffector'):
                alpha *= float(np.interp(phase, np.array(raw['_alphaRatios']) / 255, raw['_alphas']))
            colors.append(color + [alpha])
            axis_sizes = [float(np.interp(phase, raw['_keyFrameLifeTime'], raw['_size' + axis]))
                          if raw.get('hasSizeEffector') else 1 for axis in 'XYZ']
            sizes.append([value for axis in axis_sizes for value in (axis, axis)])
        for ordinal in range(raw['ParticleNumber']):
            random = math.sin((ordinal + 1) * 93.3) * 41413.1 % 1
            initial_size = [scalar(raw['sizeX'])] * 3 if raw.get('uniformScale_') else [scalar(raw['size' + axis]) for axis in 'XYZ']
            common = dict(name='E010_' + source_node['name'] + '_' + str(ordinal), material=material_ids[source_node['material_keys'][0]],
                          source_node=source_node['id'], life=scalar(raw['lifeTime']), rate=0, max=1, alpha=1,
                          center=transform[:3, 3].tolist(), colors=colors, startColor=[1, 1, 1, 1],
                          sizes=[[1, 1]] * 65, sizeAxes=sizes, custom=None, duration=.5, loop=False,
                          renderMode=0, renderAlignment=2, staticMesh=False, bursts=[dict(time=0, count=1, cycles=1, repeat=0)],
                          delay=.5 * scalar(raw['startTime'], ordinal / max(1, raw['ParticleNumber']-1)),
                          startTime_mapping={'mode':'normalized controller duration','duration_seconds':.5,'evidence':'controller duration .5 and curve domain0..1','inference':'runtime custom particle scheduler unavailable; duration-normalized birth chosen from source controller/video envelope'},
                          initialRotation=math.radians(scalar(raw['rot'], random)),
                          sheet=[raw.get('columns', 1), raw.get('rows', 1)] if raw.get('hasSpriteSheetEffector') else [1, 1],
                          source_custom_raw=raw)
            mesh = next((custom_source['meshes'][key] for key in source_node.get('mesh_keys', []) if key in custom_source['meshes']), None)
            if mesh or raw['billboardType'] == 4:
                native_vertices = np.array(mesh['vertices'] if mesh else [[-.5, -.5, 0], [.5, -.5, 0], [.5, .5, 0], [-.5, .5, 0]])
                vertices = native_vertices * initial_size
                vertices = vertices @ transform[:3, :3].T + transform[:3, 3]
                custom_effect['groups'].append(dict(**common, vertices=vertices.tolist(),
                                                    uv=mesh['uv0'] if mesh else [[0, 0], [1, 0], [1, 1], [0, 1]],
                                                    faces=mesh['triangles'] if mesh else [[0, 1, 2], [0, 2, 3]],
                                                    vertexColors=vertex_colors(mesh) if mesh else None,
                                                    particleBasis=transform[:3, :3].tolist()))
            else:
                common.update(position=transform[:3, 3].tolist(), axisX=transform[:3, 0].tolist(),
                              axisY=transform[:3, 1].tolist(), axisZ=transform[:3, 2].tolist(), size=initial_size,
                              speed=0, velocity=[0, 0, 0], rotation=common['initialRotation'], shape=None,
                              lengthScale=1, velocityScale=0)
                custom_effect['emitters'].append(common)
    data['effects']['E010'] = custom_effect
    # The video matches the older same-default-skin explosion family, not the
    # later pink crescent revision. Keep its genuine nodes and source material values.
    impact_source = HERE / 'impact_candidate_a966/world_native'
    impact_config = json.loads((impact_source / 'world_runtime_config.json').read_text(encoding='utf8'))
    impact_offset = len(data['materials'])
    data['materials'].extend(impact_config['materials'])
    for kind in ('groups', 'emitters', 'trails'):
        for node in impact_config['effects']['E014'][kind]:
            node['material'] += impact_offset
    data['effects']['E014'] = impact_config['effects']['E014']
    data['effect_variants'] = {'E014': {'bundle': 'a966338b5f6d4fa67b63f5952eb0e3a9',
                                      'selection': 'Original default-family event100072; source-vs-video offline comparison.'}}
    shutil.copytree(impact_source / 'resources', OUTPUT, dirs_exist_ok=True)
    # The current bundle leaves longling's mesh pointer zero. The old same-family
    # fireofjustice2 mesh is proven against E081: identical PS/TRS, positions and faces.
    recovered_path = SOURCE.parent / 'missing_projectile_mesh/d25ffcb6a8b9ddd0afc0ae81abf12212_-5338070969293119380.json'
    recovered = json.loads(recovered_path.read_text(encoding='utf8'))
    for key in ('E016', 'E029'):
        effect = data['effects'][key]
        particle = next(n for n in effect['emitters'] if '_longling_' in n['name'])
        group = copy.deepcopy(particle)
        basis = np.column_stack([particle['axisX'], particle['axisY'], particle['axisZ']])
        vertices = np.array(recovered['vertices']) * np.array(particle['size'])
        group.update(vertices=(vertices @ basis.T + particle['position']).tolist(),
                     uv=recovered['uv0'], faces=recovered['triangles'], vertexColors=vertex_colors(recovered),
                     restored_mesh_source=str(recovered_path),
                     source_mesh_id=recovered['id'], source_mesh_bundle=recovered['source_bundle'])
        group['particleBasis'] = basis.tolist()
        effect['groups'].append(group)
        effect['emitters'].remove(particle)
    # Preserve distinct size modules when Unity enables separate axes.
    for key, effect in data['effects'].items():
        original_path = (HERE / 'impact_candidate_a966/source_native_e014.json') if key == 'E014' else SOURCE.parent / ('source_native_' + key.lower() + '.json')
        if not original_path.exists():
            continue
        original = json.loads(original_path.read_text(encoding='utf8'))
        nodes = {n['id']: n for n in original['nodes']}
        for node in effect['groups'] + effect['emitters']:
            source_node = nodes.get(node.get('source_node'), {})
            raw = source_node.get('particle_system_raw') or {}
            if 'vertices' in node:
                mesh = next((original.get('meshes', {}).get(k) for k in source_node.get('mesh_keys', [])
                             if k in original.get('meshes', {})), None)
                if mesh and not node.get('vertexColors'):
                    node['vertexColors'] = vertex_colors(mesh)
            if 'vertices' in node and source_node.get('prefab_world'):
                node['particleBasis'] = (np.array(source_node['prefab_world'])[:3, :3] * .01).tolist()
            initial = raw.get('InitialModule') or {}
            if initial:
                sampled=native_samples(raw)
                uv=raw.get('UVModule',{})
                if uv.get('enabled'):
                    assert uv.get('timeMode',0)==0 and uv.get('animationType',0)==0, 'Unsupported native texture-sheet mode'
                    node['sheetFrameOverTime']=sampled['UVModule.frameOverTime']['min_max']
                    node['sheetStartFrame']=sampled['UVModule.startFrame']['min_max']
                    node['sheetCycles']=uv.get('cycles',1)
                node['nativeParticle']=True
                node['prewarm']=bool(raw.get('prewarm'))
                node['lifeRange']=sampled['InitialModule.startLifetime']['min_max'][0]
                node['initialSizeRange']=[sampled['InitialModule.'+axis]['min_max'][0] for axis in ('startSize','startSizeY','startSizeZ')] if initial.get('size3D') else [sampled['InitialModule.startSize']['min_max'][0]]*3
                node['initialSizeBaked']=node.get('size3D_baked',node.get('size',[sum(a)/2 for a in node['initialSizeRange']]))
                node['startColorRange']=sampled['InitialModule.startColor']['min_max_rgba'][0]
                if raw.get('ColorModule',{}).get('enabled'):
                    node['colorRanges']=sampled['ColorModule.gradient']['min_max_rgba']
                if 'speed'in node:
                    node['speedRange']=sampled['InitialModule.startSpeed']['min_max'][0]
                if raw.get('VelocityModule',{}).get('enabled'):
                    axes=[sampled.get('VelocityModule.'+axis,{}).get('min_max',[[0,0]]*65) for axis in 'xyz']
                    node['velocityRanges']=[sum((axis[i]for axis in axes),[])for i in range(65)]
                if node.get('shape'):
                    sh=raw.get('ShapeModule',{})
                    node['shape'].update(radiusThickness=sh.get('radiusThickness',1),length=sh.get('length',0),arc=sh.get('arc',{}).get('value',360))
                ranges = []
                for axis in ('startRotationX', 'startRotationY', 'startRotation'):
                    rotation = initial.get(axis) or {}
                    value = rotation.get('scalar', 0)
                    low = rotation.get('minScalar', value) if rotation.get('minMaxState') == 3 else value
                    if axis != 'startRotation' and not initial.get('rotation3D'):
                        low = value = 0
                    ranges.extend([low, value])
                node['initialEuler'] = ranges
            rotation_module = raw.get('RotationModule') or {}
            if rotation_module.get('enabled'):
                samples = source_node.get('particle_samples', {})
                angular = []
                for axis in ('x', 'y', 'curve'):
                    if axis != 'curve' and not rotation_module.get('separateAxes'):
                        angular.append([[0, 0]] * 65)
                    else:
                        angular.append(samples.get('RotationModule.' + axis, {}).get('min_max', [[0, 0]] * 65))
                node['angularVelocity'] = [sum((angular[axis][i] for axis in range(3)), []) for i in range(65)]
            if (raw.get('SizeModule') or {}).get('separateAxes'):
                samples = source_node['particle_samples']
                axes = [samples.get('SizeModule.' + axis, samples.get('SizeModule.curve', {})).get('min_max', [[1, 1]] * 65)
                        for axis in ('x', 'y', 'z')]
                node['sizeAxes'] = [sum((axes[axis][i] for axis in range(3)), []) for i in range(65)]
    for effect in data['effects'].values():
        effect.pop('original_nodes', None)
        for kind in ('groups', 'emitters'):
            for node in effect[kind]:
                if node.get('source_custom_raw'):
                    continue
                raw = node.pop('source_particle_raw', None) or {}
                renderer = node.get('source_renderer') or {}
                node['sortFudge'] = renderer.get('m_SortingFudge', 0)
                emission = raw.get('EmissionModule', {})
                node['bursts'] = [
                    dict(time=b['time'], count=b['countCurve'].get('scalar', 1),
                         countRange=helper_scope['sampled_tree'](b['countCurve'])['']['min_max'][0],probability=b.get('probability',1),
                         cycles=b.get('cycleCount', 1), repeat=b.get('repeatInterval', 0))
                    for b in emission.get('m_Bursts', [])
                ]
                node['delay'] = raw.get('startDelay', {}).get('scalar', 0)
                node['staticMesh'] = not bool(raw)
                node['simulationSpace'] = raw.get('moveWithTransform', 0)
                for name in ('source_renderer', 'sheetRaw', 'emissionBursts', 'rotationOverLifetime'):
                    node.pop(name, None)
    for material in data['materials']:
        material['source_colors'] = {
            name: value for name, value in material.get('source_colors', {}).items()
            if name == '_Model_Color'
        }
    animation = json.loads((SOURCE / 'e017_longling_animation.json').read_text(encoding='utf8'))
    data['mesh_vertex_color_audit'] = mesh_color_audit
    data['animation'] = dict(initial=animation['initial_prefab_world_m'],
                             parent=animation['parent_world_m'], duration=10,
                             euler=[-90.00000762939453, 0, 36])
    target = OUTPUT / 'assets/judgfire/fx/world_native.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, separators=(',', ':'), allow_nan=False), encoding='utf8')
    print('Exported original world native meshes, particles, shader slots, trails and E017 animation.')


if __name__ == '__main__':
    main()
