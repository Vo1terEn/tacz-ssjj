"""Fresh Parasite II mesh import. Inputs are original 本体 assets only.

Coordinate contract: each emitted point is in its glTF joint's local XYZ frame,
in blocks (one source centimetre = .01 blocks). No Bedrock /16 or Y inversion
is hidden here. The animation importer supplies native transforms for the same
four named joints. Source triangle winding and UV conversion are checked against
the independent Unity Mesh JSON before any runtime resource is written.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
from pathlib import Path
import struct

import numpy as np
from PIL import Image

PROJECT = Path(__file__).resolve().parents[3]
WORK = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / '寄生者/本体'
RIG = SOURCE / '骨骼动画/parasitic_sniper2'
ASSETS = WORK / 'pack/assets/ssjjparasite2'
REPORTS = WORK / 'reports'
J3 = np.diag([-1., 1., 1.])
J4 = np.diag([-1., 1., 1., 1.])
INPUTS: dict[str, str] = {}


def read_bytes(path: Path) -> bytes:
    resolved = path.resolve()
    if not resolved.is_relative_to(SOURCE.resolve()):
        raise ValueError('Only original 本体 source inputs are allowed: ' + str(path))
    value = resolved.read_bytes()
    INPUTS[str(resolved.relative_to(PROJECT)).replace('\\', '/')] = hashlib.sha256(value).hexdigest()
    return value


def load(path: Path):
    return json.loads(read_bytes(path).decode('utf-8-sig'))


def save(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False,
                               separators=(',', ':')) + '\n', encoding='utf8')


def normalized(v):
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), 1e-30)


def quaternion_matrix(q):
    x, y, z, w = normalized(np.array(q, dtype=float))
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
                     [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
                     [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])


def matrix_trs(translation, rotation, scale=(1, 1, 1)):
    result = np.eye(4)
    result[:3, :3] = quaternion_matrix(rotation) @ np.diag(scale)
    result[:3, 3] = translation
    return result


def slerp(q0, q1, fraction):
    q0, q1 = normalized(np.array(q0, float)), normalized(np.array(q1, float))
    dot = float(q0 @ q1)
    if dot < 0:
        q1, dot = -q1, -dot
    if dot > .9995:
        return normalized(q0 + fraction*(q1-q0))
    angle = math.acos(np.clip(dot, -1, 1))
    return (math.sin((1-fraction)*angle)*q0 + math.sin(fraction*angle)*q1) / math.sin(angle)


def sample_track(track, time):
    frames = track['keyframes']
    times = [frame['time'] for frame in frames]
    if len(frames) == 1 or time <= times[0]:
        frame = frames[0]
        return np.array(frame['translation']), np.array(frame['rotation_xyzw'])
    if time >= times[-1]:
        frame = frames[-1]
        return np.array(frame['translation']), np.array(frame['rotation_xyzw'])
    right = int(np.searchsorted(times, time, side='right'))
    left = right-1
    f = (time-times[left])/(times[right]-times[left])
    a, b = frames[left], frames[right]
    return ((1-f)*np.array(a['translation'])+f*np.array(b['translation']),
            slerp(a['rotation_xyzw'], b['rotation_xyzw'], f))


class Glb:
    """glTF 2 accessor reader; supports interleaved and normalized streams."""
    def __init__(self, path):
        raw = read_bytes(Path(path))
        magic, version, length = struct.unpack_from('<III', raw)
        assert (magic, version, length) == (0x46546c67, 2, len(raw))
        offset, self.binary, self.doc = 12, b'', None
        while offset < len(raw):
            size, kind = struct.unpack_from('<II', raw, offset)
            chunk = raw[offset+8:offset+8+size]
            offset += 8+size
            if kind == 0x4e4f534a:
                self.doc = json.loads(chunk)
            elif kind == 0x004e4942:
                self.binary = chunk
        assert self.doc is not None
        self.nodes = self.doc['nodes']
        self.parents = {child: i for i, node in enumerate(self.nodes)
                        for child in node.get('children', [])}

    def accessor(self, index):
        a = self.doc['accessors'][index]
        assert 'sparse' not in a, 'Sparse accessors require explicit handling'
        view = self.doc['bufferViews'][a['bufferView']]
        dtype = np.dtype({5120:'i1', 5121:'u1', 5122:'<i2', 5123:'<u2',
                          5125:'<u4', 5126:'<f4'}[a['componentType']])
        width = {'SCALAR':1, 'VEC2':2, 'VEC3':3, 'VEC4':4, 'MAT4':16}[a['type']]
        stride = view.get('byteStride', width*dtype.itemsize)
        result = np.ndarray((a['count'], width), dtype=dtype, buffer=self.binary,
                            offset=view.get('byteOffset', 0)+a.get('byteOffset', 0),
                            strides=(stride, dtype.itemsize)).copy()
        if a.get('normalized'):
            result = result.astype(float)/np.iinfo(dtype).max
            if dtype.kind == 'i':
                result = np.maximum(result, -1)
        return result


class Catalog:
    def __init__(self):
        text = read_bytes(SOURCE/'资源索引.csv').decode('utf-8-sig')
        self.rows = list(csv.DictReader(text.splitlines()))
        self.by_type = {}
        self.by_id = {}
        self.cache = {}
        for row in self.rows:
            for name in row['导出文件路径'].split(';'):
                name = name.replace('\\', '/')
                if not name.startswith('导出素材/parasitic_sniper2/'):
                    continue
                path = SOURCE/name
                if path.suffix.lower() == '.json':
                    self.by_type.setdefault(row['类型'], []).append(path)
                try:
                    ident = int(path.stem.rsplit('__', 1)[1])
                except (ValueError, IndexError):
                    continue
                self.by_id.setdefault(ident, []).append(path)

    def json(self, path):
        if path not in self.cache:
            self.cache[path] = load(path)
        return self.cache[path]

    def find(self, ident, suffix='.json', bundle=None):
        paths = [p for p in self.by_id[int(ident)] if p.suffix.lower() == suffix
                 and (bundle is None or p.relative_to(SOURCE).parts[2] == bundle)]
        assert len(paths) == 1, (ident, paths)
        return paths[0]

    def ancestor_names(self, renderer_path):
        renderer = self.json(renderer_path)
        bundle = renderer_path.relative_to(SOURCE).parts[2]
        obj = self.json(self.find(renderer['m_GameObject']['m_PathID'], bundle=bundle))
        transforms = [self.find(c['component']['m_PathID'], bundle=bundle)
                      for c in obj['m_Component']]
        transform = next(self.json(p) for p in transforms if 'm_LocalPosition' in self.json(p))
        names = []
        seen = set()
        while True:
            names.append(self.json(self.find(transform['m_GameObject']['m_PathID'], bundle=bundle))['m_Name'])
            parent = transform['m_Father']['m_PathID']
            if parent == 0:
                break
            assert parent not in seen
            seen.add(parent)
            transform = self.json(self.find(parent, bundle=bundle))
        return names


def decode_unity_mesh(mesh):
    data = mesh['m_VertexData']
    count = data['m_VertexCount']
    raw = bytes.fromhex(data['m_DataSize']['__bytes_hex__'])
    channels = data['m_Channels']
    formats = {0:('<f4', 4), 1:('<f2', 2), 2:('u1', 1), 3:('i1', 1),
               4:('<u2', 2), 5:('<i2', 2), 6:('u1', 1), 7:('i1', 1),
               8:('<u2', 2), 9:('<i2', 2), 10:('<u4', 4), 11:('<i4', 4)}
    strides = {}
    for c in channels:
        dim = c['dimension'] & 15
        if dim:
            strides[c['stream']] = max(strides.get(c['stream'], 0),
                                      c['offset']+dim*formats[c['format']][1])
    starts, cursor = {}, 0
    for stream, stride in sorted(strides.items()):
        cursor = (cursor+15)//16*16
        starts[stream] = cursor
        cursor += count*stride
    assert cursor == len(raw), (cursor, len(raw))

    def channel(index):
        c = channels[index]
        dimension = c['dimension'] & 15
        if not dimension:
            return None
        dtype, size = formats[c['format']]
        result = np.ndarray((count, dimension), dtype=dtype, buffer=raw,
                            offset=starts[c['stream']]+c['offset'],
                            strides=(strides[c['stream']], size)).copy()
        if c['format'] in (2, 3, 4, 5):
            result = np.maximum(result.astype(float)/np.iinfo(np.dtype(dtype)).max, -1)
        return result
    indices = np.frombuffer(bytes(mesh['m_IndexBuffer']),
                            dtype='<u2' if mesh['m_IndexFormat'] == 0 else '<u4').reshape(-1, 3)
    bind = np.array([[[b[f'e{i}{j}'] for j in range(4)] for i in range(4)]
                     for b in mesh['m_BindPose']])
    return {'positions':channel(0), 'normals':channel(1), 'uv':channel(4),
            'joints':channel(13), 'weights':channel(12), 'triangles':indices, 'bind':bind}


def compare_glb_animation(glb, source_animation):
    rows = []
    for clip in source_animation['clips']:
        assert len(clip['blends']) == 1
        gltf_clip = next(x for x in glb.doc['animations'] if x['name'] == clip['name'])
        errors = []
        for track in clip['blends'][0]['tracks']:
            node = next(i for i, n in enumerate(glb.nodes) if n['name'] == track['bone'])
            for field, source_field in [('translation', 'translation'), ('rotation', 'rotation_xyzw')]:
                channel = next(c for c in gltf_clip['channels']
                               if c['target'] == {'node':node, 'path':field})
                sampler = gltf_clip['samplers'][channel['sampler']]
                times, values = glb.accessor(sampler['input'])[:, 0], glb.accessor(sampler['output'])
                expected = np.array([key[source_field] for key in track['keyframes']])
                if field == 'translation':
                    expected *= np.array([-1, 1, 1])
                else:
                    expected *= np.array([1, -1, -1, 1])
                time_error = np.max(abs(times-np.array([k['time'] for k in track['keyframes']])))
                value_error = np.max(abs(values-expected))
                assert time_error < 1e-7 and value_error < 1e-7
                errors.append(float(max(time_error, value_error)))
        rows.append({'clip':clip['name'], 'channels':len(errors), 'maximum_component_error':max(errors)})
    bind = glb.accessor(glb.doc['skins'][0]['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
    stand = next(c for c in source_animation['clips'] if c['name'] == 'TORSO_STAND')
    tracks = {t['bone']:t for t in stand['blends'][0]['tracks']}
    consistency = []
    for slot, node in enumerate(glb.doc['skins'][0]['joints']):
        name = glb.nodes[node]['name']
        if name == 'WeaponAnchor2':
            continue  # Root anchor's local pose is relative to the hand, not bind identity.
        t, q = sample_track(tracks[name], 0)
        direct = matrix_trs(t, q)
        target = np.linalg.inv(bind[slot])
        direct_error = float(np.max(abs(direct-target)))
        reflected_error = float(np.max(abs(J4 @ direct @ J4-target)))
        assert direct_error < 2e-4 and reflected_error > 1
        consistency.append({'bone':name, 'ANM_direct_bind_error_source_units':direct_error,
                            'ANM_reflected_bind_error_source_units':reflected_error})
    return {'status':'PASS', 'result':'GLB samplers numerically reflect the ANM JSON. The ANM header says Unity, but direct ANM stand matrices agree with glTF bind locals; reflecting them introduces centimetre-scale errors. Use original ANM curves directly after full hierarchy validation.',
            'ANM_header_coordinate_label_is_not_accepted_as_ground_truth':True,
            'direct_ANM_bind_consistency':consistency,
            'glTF_translation':'[-Unity.x, Unity.y, Unity.z]',
            'glTF_quaternion_xyzw':'[Unity.x, -Unity.y, -Unity.z, Unity.w]', 'clips':rows}


def import_mesh(glb, catalog):
    outputs, trace, comparisons, source_meshes = [], [], [], []
    for node_index, node in enumerate(glb.nodes):
        if 'mesh' not in node:
            continue
        mesh = glb.doc['meshes'][node['mesh']]
        mesh_paths = [p for p in catalog.by_type['Mesh']
                      if catalog.json(p).get('m_Name') == mesh['name']]
        assert len(mesh_paths) == 1
        unity_path = mesh_paths[0]
        unity = catalog.json(unity_path)
        mesh_id = int(unity_path.stem.rsplit('__', 1)[1])
        renderer_paths = [p for p in catalog.by_type['SkinnedMeshRenderer']
                          if catalog.json(p)['m_Mesh']['m_PathID'] == mesh_id
                          and catalog.ancestor_names(p)[-1] == glb.nodes[0]['name']]
        assert renderer_paths
        # Dependency bundle and battle-prefab bundle contain equivalent copies
        # of the imported gun object. Prefer the contextual battle instance,
        # whose mesh reference points into that dependency, while recording all
        # candidates. Prefab overrides may disable dependency effect planes.
        flags = []
        for candidate in renderer_paths:
            data = catalog.json(candidate)
            cbundle = candidate.relative_to(SOURCE).parts[2]
            go = catalog.json(catalog.find(data['m_GameObject']['m_PathID'], bundle=cbundle))
            flags.append((bool(data['m_Enabled']), bool(go['m_IsActive']),
                          tuple(m['m_PathID'] for m in data['m_Materials'])))
        assert all(flag[2] == flags[0][2] for flag in flags)
        contextual = [p for p in renderer_paths if catalog.json(p)['m_Mesh']['m_FileID'] != 0]
        assert len(contextual) <= 1
        renderer_path = contextual[0] if contextual else sorted(renderer_paths)[0]
        renderer = catalog.json(renderer_path)
        bundle = renderer_path.relative_to(SOURCE).parts[2]
        object_path = catalog.find(renderer['m_GameObject']['m_PathID'], bundle=bundle)
        object_data = catalog.json(object_path)
        enabled = bool(renderer['m_Enabled'] and object_data['m_IsActive'])
        skin = glb.doc['skins'][node['skin']]
        ibm = glb.accessor(skin['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
        decoded = decode_unity_mesh(unity)
        assert len(mesh['primitives']) == 1
        primitive = mesh['primitives'][0]
        attributes = {key:glb.accessor(index) for key, index in primitive['attributes'].items()}
        positions, normals, uv = attributes['POSITION'], attributes['NORMAL'], attributes['TEXCOORD_0']
        triangles = glb.accessor(primitive['indices']).reshape(-1, 3).astype(int)
        joints, weights = attributes['JOINTS_0'], attributes['WEIGHTS_0']
        assert np.max(abs(weights.sum(axis=1)-1)) < 1e-7
        assert np.all((weights > 1e-7).sum(axis=1) == 1), 'This importer only emits proven rigid vertices'
        bone_slots = joints[np.arange(len(joints)), np.argmax(weights, axis=1)].astype(int)
        assert np.all(bone_slots[triangles] == bone_slots[triangles[:, :1]]), 'Cross-joint triangles need real skinning'
        p_error = float(np.max(abs(positions-decoded['positions'] @ J3)))
        n_error = float(np.max(abs(normals-decoded['normals'] @ J3)))
        uv_error = float(np.max(abs(uv-decoded['uv']*np.array([1, -1])-np.array([0, 1]))))
        ibm_error = float(np.max(abs(ibm-J4 @ decoded['bind'] @ J4)))
        assert max(p_error, n_error, uv_error, ibm_error) < 1e-6
        assert np.array_equal(triangles, decoded['triangles'][:, [0, 2, 1]])
        assert np.array_equal(bone_slots, decoded['joints'][:, 0])
        comparisons.append({'mesh':mesh['name'], 'vertices':len(positions), 'triangles':len(triangles),
                            'position_error':p_error, 'normal_error':n_error, 'uv_error':uv_error,
                            'inverse_bind_error':ibm_error, 'indices_reflection_verified':True,
                            'single_bone_weight_1_verified':True})
        material_paths = [catalog.find(item['m_PathID']) for item in renderer['m_Materials']]
        assert len(material_paths) == 1
        material = catalog.json(material_paths[0])
        tex_env = dict(material['m_SavedProperties']['m_TexEnvs'])['_MainTex']
        texture_path = catalog.find(tex_env['m_Texture']['m_PathID'], suffix='.png')
        trace.append({'mesh':mesh['name'], 'mesh_json':str(unity_path.relative_to(PROJECT)),
                      'renderer_json':str(renderer_path.relative_to(PROJECT)),
                      'renderer_candidates':[{'path':str(p.relative_to(PROJECT)), 'enabled':flag[0],
                                              'game_object_active':flag[1]} for p, flag in zip(renderer_paths, flags)],
                      'game_object_json':str(object_path.relative_to(PROJECT)),
                      'renderer_enabled':renderer['m_Enabled'], 'game_object_active':object_data['m_IsActive'],
                      'included':enabled, 'material_json':str(material_paths[0].relative_to(PROJECT)),
                      'main_texture':str(texture_path.relative_to(PROJECT)),
                      'source_material_properties':material['m_SavedProperties']})
        if not enabled:
            continue
        for slot, node_id in enumerate(skin['joints']):
            selected_faces = triangles[bone_slots[triangles[:, 0]] == slot]
            if not len(selected_faces):
                continue
            selected = np.unique(selected_faces)
            remap = np.full(len(positions), -1, dtype=int)
            remap[selected] = np.arange(len(selected))
            local = (ibm[slot] @ np.c_[positions[selected], np.ones(len(selected))].T).T[:, :3] * .01
            local_normals = normalized((np.linalg.inv(ibm[slot, :3, :3]).T @ normals[selected].T).T)
            bone_name = glb.nodes[node_id]['name']
            part = {'bone':bone_name, 'positions':local.tolist(), 'normals':local_normals.tolist(),
                    'uv':uv[selected].tolist(), 'triangles':remap[selected_faces].tolist()}
            outputs.append(part)
            source_meshes.append({'bone':bone_name, 'slot':slot, 'gltf_node':node_id,
                                  'source_mesh':unity, 'source_decoded':decoded,
                                  'source_vertices':selected, 'part':part})
        assert material['m_SavedProperties']['m_Floats']
        for name, value in material['m_SavedProperties']['m_Floats']:
            if name in ('_CullAlpha', '_CullAlphaEnable'):
                assert value == 0
        texture_bytes = read_bytes(texture_path)
        import io
        original = Image.open(io.BytesIO(texture_bytes)).convert('RGBA')
        rgba = np.array(original)
        alpha_values, alpha_counts = np.unique(rgba[:, :, 3], return_counts=True)
        opaque = original.copy()
        opaque.putalpha(255)
        assert np.array_equal(np.array(opaque)[:, :, :3], rgba[:, :, :3])
        target = ASSETS/'textures/gun/uv/parasitic_sniper2.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        opaque.save(target)
        texture_report = {'source':str(texture_path.relative_to(PROJECT)),
                          'source_sha256':hashlib.sha256(texture_bytes).hexdigest(),
                          'output_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
                          'size':list(original.size), 'source_alpha_histogram':dict(zip(map(str, alpha_values), map(int, alpha_counts))),
                          'output_alpha':255, 'rgb_byte_identical':True,
                          'reason':'Original material disables alpha clipping; native opaque texture restores constant alpha only.'}
    save(ASSETS/'source_mesh.json', {'parts':outputs})
    return outputs, source_meshes, trace, comparisons, texture_report


def independent_lbs(source_meshes, animation):
    """Original Unity bind matrices/vertices vs exported rigid joint locals.

    Every original keyframe and a midpoint between adjacent keys is tested.
    Using the original animated joint-local matrix on each side deliberately
    factors out camera/parent placement, which the animation verifier tests.
    """
    maximum = 0.
    point_comparisons = 0
    rows = []
    for clip in animation['clips']:
        tracks = {t['bone']:t for t in clip['blends'][0]['tracks']}
        clip_max = 0.
        for item in source_meshes:
            track = tracks[item['bone']]
            times = sorted({key['time'] for key in track['keyframes']})
            times = sorted(set(times+[(a+b)/2 for a,b in zip(times, times[1:])]))
            decoded, indices = item['source_decoded'], item['source_vertices']
            unity_points = np.c_[decoded['positions'][indices], np.ones(len(indices))]
            unity_bind = decoded['bind'][item['slot']]
            runtime_points = np.c_[item['part']['positions'], np.ones(len(indices))]
            for time in times:
                t, q = sample_track(track, time)
                # Stand-vs-bind evidence establishes the ANM values already in
                # the glTF-compatible frame. Convert them back for the wholly
                # original Unity vertex/bind side of this independent check.
                M = matrix_trs(t, q)
                U = J4 @ M @ J4
                lhs = (J4 @ U @ unity_bind @ unity_points.T).T[:, :3] * .01
                G = M.copy()
                G[:3, 3] *= .01
                rhs = (G @ runtime_points.T).T[:, :3]
                error = float(np.max(np.linalg.norm(lhs-rhs, axis=1)))
                clip_max = max(clip_max, error)
                point_comparisons += len(indices)
        maximum = max(maximum, clip_max)
        rows.append({'clip':clip['name'], 'maximum_vertex_error_blocks':clip_max})
    assert maximum < 1e-6, maximum
    return {'status':'PASS', 'method':'Independent original Unity vertices/bind matrices vs freshly emitted glTF rigid locals, driven by source ANM joint transforms in the empirically verified glTF-compatible frame.',
            'scope':'Joint-local skinning equivalence; full parent hierarchy, native animation traversal and camera placement are separate checks.',
            'vertex_comparisons':point_comparisons, 'maximum_vertex_error_blocks':maximum, 'clips':rows}


def raster_preview(parts, animation, texture_path):
    """Orthographic, texture-sampled source model preview; no game claims."""
    stand = next(c for c in animation['clips'] if c['name'] == 'TORSO_STAND')
    tracks = {t['bone']:t for t in stand['blends'][0]['tracks']}
    positions, normals, uvs, triangles = [], [], [], []
    for part in parts:
        if part['bone'] == 'WeaponAnchor2':
            matrix = np.eye(4)
        else:
            t, q = sample_track(tracks[part['bone']], 0)
            matrix = matrix_trs(t, q)
            matrix[:3, 3] *= .01
        offset = len(positions)
        p = np.array(part['positions'])
        positions.extend((matrix @ np.c_[p, np.ones(len(p))].T).T[:, :3])
        normals.extend((matrix[:3, :3] @ np.array(part['normals']).T).T)
        uvs.extend(part['uv'])
        triangles.extend(np.array(part['triangles'])+offset)
    positions, normals, uvs, triangles = map(np.array, (positions, normals, uvs, triangles))
    # Source anchor's +X is the muzzle direction; +Z points toward the backplate.
    # This explicitly labelled display view is independent of the native pose.
    view = np.array([[1., 0., 0.], [0., .20, .979795897], [0., -.979795897, .20]])
    points = positions @ view.T
    ns = normalized(normals @ view.T)
    width, height = 1400, 540
    lo, hi = points[:, :2].min(0), points[:, :2].max(0)
    scale = min((width-100)/(hi[0]-lo[0]), (height-90)/(hi[1]-lo[1]))
    screen = points.copy()
    screen[:, 0] = (points[:, 0]-(lo[0]+hi[0])/2)*scale+width/2
    screen[:, 1] = height/2-(points[:, 1]-(lo[1]+hi[1])/2)*scale
    pixels = np.zeros((height, width, 4), dtype=np.uint8)
    depth = np.full((height, width), -np.inf)
    texture = np.array(Image.open(texture_path).convert('RGBA'))
    light = normalized(np.array([-.3, .6, 1.]))
    for face in triangles:
        p = screen[face]
        xmin, ymin = np.maximum(np.floor(p[:, :2].min(0)).astype(int), [0, 0])
        xmax, ymax = np.minimum(np.ceil(p[:, :2].max(0)).astype(int), [width-1, height-1])
        if xmin > xmax or ymin > ymax:
            continue
        xx, yy = np.meshgrid(np.arange(xmin, xmax+1)+.5, np.arange(ymin, ymax+1)+.5)
        denom = (p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
        if abs(denom) < 1e-10:
            continue
        a = ((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/denom
        b = ((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/denom
        c = 1-a-b
        z = a*p[0,2]+b*p[1,2]+c*p[2,2]
        zbuf = depth[ymin:ymax+1, xmin:xmax+1]
        mask = (a >= -1e-8)&(b >= -1e-8)&(c >= -1e-8)&(z > zbuf)
        if not mask.any():
            continue
        uv = a[...,None]*uvs[face[0]]+b[...,None]*uvs[face[1]]+c[...,None]*uvs[face[2]]
        tx = np.clip((uv[...,0]*texture.shape[1]).astype(int), 0, texture.shape[1]-1)
        ty = np.clip((uv[...,1]*texture.shape[0]).astype(int), 0, texture.shape[0]-1)
        normal = normalized(a[...,None]*ns[face[0]]+b[...,None]*ns[face[1]]+c[...,None]*ns[face[2]])
        illumination = .68+.32*np.maximum(0, normal @ light)
        color = texture[ty,tx].copy()
        color[:,:,:3] = np.clip(color[:,:,:3]*illumination[...,None], 0, 255).astype(np.uint8)
        patch = pixels[ymin:ymax+1, xmin:xmax+1]
        patch[mask] = color[mask]
        zbuf[mask] = z[mask]
    image = Image.fromarray(pixels)
    preview_path = WORK/'previews/source_model_stand.png'
    preview_path.parent.mkdir(parents=True, exist_ok=True)
    image.save(preview_path)
    bbox = image.getbbox()
    cutout = image.crop(bbox)
    for kind, size in [('slot', (256, 128)), ('hud', (256, 128))]:
        icon = Image.new('RGBA', size)
        scaled = cutout.copy()
        scaled.thumbnail((size[0]-10, size[1]-10), Image.Resampling.LANCZOS)
        icon.alpha_composite(scaled, ((size[0]-scaled.width)//2, (size[1]-scaled.height)//2))
        target = ASSETS/f'textures/gun/{kind}/parasitic_sniper2.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        icon.save(target)
    return {'path':str(preview_path.relative_to(PROJECT)), 'view_matrix':view.tolist(),
            'description':'Fresh texture-sampled orthographic source stand; no native game camera or gameplay validation.',
            'opaque_pixels':int((pixels[:,:,3] > 0).sum())}


def main():
    glb = Glb(RIG/'可导入模型_GLB/view/gun.glb')
    animation = load(RIG/'解析动画_JSON/gun.animations.json')
    coordinate_evidence = compare_glb_animation(glb, animation)
    parts, source_meshes, trace, comparisons, texture = import_mesh(glb, Catalog())
    lbs = independent_lbs(source_meshes, animation)
    preview = raster_preview(parts, animation, ASSETS/'textures/gun/uv/parasitic_sniper2.png')
    report = {'status':'PASS', 'fresh_build':True, 'namespace':'ssjjparasite2',
              'source_read_scope':'寄生者/本体 only; no previous work/build/generated files read',
              'coordinate_contract':{'positions':'.01 * glTF_inverseBindMatrix * glTF_POSITION; XYZ unchanged, in blocks',
                                     'normals':'normalize(inverse(transpose(glTF_IBM_3x3)) * glTF_NORMAL)',
                                     'uv':'Original GLB UV; independently equals (Unity.u, 1-Unity.v)',
                                     'triangles':'Original GLB winding; independently equals Unity (a,c,b)',
                                     'runtime':'Native bone TRS acts directly on these points. No further /16, handedness flip, scale, or Y inversion.'},
              'parts':[{'bone':p['bone'], 'vertices':len(p['positions']), 'triangles':len(p['triangles']),
                        'local_min_blocks':np.array(p['positions']).min(0).tolist(),
                        'local_max_blocks':np.array(p['positions']).max(0).tolist()} for p in parts],
              'triangles':sum(len(p['triangles']) for p in parts), 'vertices':sum(len(p['positions']) for p in parts),
              'material_trace':trace, 'independent_unity_mesh_comparisons':comparisons,
              'glb_animation_coordinate_evidence':coordinate_evidence, 'independent_lbs':lbs,
              'texture':texture, 'preview':preview, 'input_sha256':dict(sorted(INPUTS.items()))}
    save(REPORTS/'source_import.json', report)
    print(json.dumps({'status':report['status'], 'parts':len(parts), 'vertices':report['vertices'],
                      'triangles':report['triangles'], 'lbs_error_blocks':lbs['maximum_vertex_error_blocks'],
                      'input_files':len(INPUTS)}, ensure_ascii=True))


if __name__ == '__main__':
    main()
