"""Restore source concentric rear rings through the existing 18-channel mesh API.

Only six default/lava bodies are written. Front particles, surface fire and the
three independent galaxy vortices are byte-for-byte semantic protected.
"""
from pathlib import Path
import copy, hashlib, json
import numpy as np
from scipy.spatial.transform import Rotation

W = Path(__file__).resolve().parents[1]
P = W.parents[1]
ORIGINAL = P / 'work/zeus_fx_motion_20261114/baseline_assets/assets/ssjj_zeus/effects'
B = W / 'baseline_assets/assets/ssjj_zeus'
SIDE = .03414487 + .003

def read(p): return json.loads(Path(p).read_text('utf8'))
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p, v):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(v, ensure_ascii=False, allow_nan=False, separators=(',', ':')) + '\n', 'utf8')

def adapt(original, is_ring, white_blue=False):
    e = copy.deepcopy(original)
    e['mode'] = 'mesh'
    traces = []
    # Baking the source fixed quaternion into the source quad preserves its
    # authored UV orientation. Native Y is now a fixed disc-normal coordinate.
    # Relative source random roll is retained as rotation about fixed native Y.
    q0 = np.asarray(e['particles'][0]['frames'][0][6:10])
    points = Rotation.from_quat(q0).apply(e['geometry']['positions'])
    source_sides = np.sign(points[:, 1])
    if np.ptp(points[:, 1]) < 1e-6:
        source_sides[:] = np.sign(e['particles'][0]['frames'][0][1])
    assert np.all(source_sides != 0), e['name']
    points[:, 1] = source_sides * SIDE
    e['geometry']['positions'] = np.round(points, 10).tolist()
    center = [-.13192, 0., .05773] if len(set(source_sides)) == 2 else [-.13137, 0., .05862]
    for pi, particle in enumerate(e['particles']):
        old = copy.deepcopy(particle)
        relative = Rotation.from_quat(old['frames'][0][6:10]) * Rotation.from_quat(q0).inv()
        rm = relative.as_matrix()
        relative_y = Rotation.from_euler('y', np.arctan2(rm[0,2],rm[0,0])).as_quat().tolist()
        for f in particle['frames']:
            f[:3] = center
            # Source X/Z radial curve is retained. The source shared uniform
            # size route previously enlarged plane separation on Y as well.
            f[4] = 1.
            f[6:10] = relative_y
            if white_blue:
                f[10:13] = original['particles'][pi]['frames'][0][10:13]
        if is_ring:
            # Same count and 1s lifetime, with equal phases over a closed 1s
            # cycle: concentric waves stay present instead of a 2s dead half.
            assert abs(particle['life'] - 1.) < 1e-6
            particle['birth'] = pi / len(e['particles'])
        traces.append({'particle': pi, 'source_birth': old['birth'], 'birth': particle['birth'], 'life': particle['life'],
                       'original_radial_size_exact': all(a[3] == b[3] and a[5] == b[5] for a,b in zip(old['frames'],particle['frames'])),
                       'source_alpha_and_uv_exact': all(a[13:18] == b[13:18] for a,b in zip(old['frames'],particle['frames']))})
    if is_ring: e['duration'] = 1.
    return e, {'name': e['name'], 'original_count':len(original['particles']), 'count':len(e['particles']),
               'fixed_plane_Y_m': sorted(set(np.round(points[:,1],8))), 'source_side_bound_m':.03414487,
               'minimum_disc_normal_gap_m':.003, 'center_XZ_m':[center[0],center[2]],
               'source_geometry_uv_indices_exact':e['geometry']['uv']==original['geometry']['uv'] and e['geometry']['triangles']==original['geometry']['triangles'],
               'source_texture_render_exact':e['texture']==original['texture'] and e['render']==original['render'],
               'duration':e['duration'], 'first_source_white_blue_color_held':white_blue, 'particles':traces}

def main():
    names = ['body.json','body_hand_of_zeuss.json','body_hand_of_zeuss1.json'] + [f'body_{g}_lava.json' for g in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']]
    ring_names={'ring','ring_duplicate_2','xuanzhuan','xuanzhuan_duplicate_2'}
    reports=[]; overrides={}; inputs={Path(__file__).relative_to(W).as_posix():sha(__file__)}; external={}
    for name in names:
        baseline=B/'effects'/name; source=ORIGINAL/name; body=read(baseline); old=read(source)
        originals={e['name']:e for e in old['emitters']}; changed=[]; patch={}
        for i,e in enumerate(body['emitters']):
            if e['name'] not in ring_names | {'dianqiu'}: continue
            converted, trace=adapt(originals[e['name']],e['name'] in ring_names,name=='body.json' and e['name']=='ring')
            body['emitters'][i]=converted; patch[e['name']]=converted; changed.append(trace)
        assert changed
        protected=[e['name'] for e in read(baseline)['emitters'] if e['name'] not in patch]
        assert all(e==original for e,original in zip(body['emitters'],read(baseline)['emitters']) if e['name'] in protected)
        out=W/'pack/assets/ssjj_zeus/effects'/name; save(out,body)
        overrides['assets/ssjj_zeus/effects/'+name]=patch
        inputs[baseline.relative_to(W).as_posix()]=sha(baseline);external[source.relative_to(P).as_posix()]=sha(source)
        reports.append({'resource':out.relative_to(W).as_posix(),'emitters':changed,'protected_emitters':protected,'protected_emitters_deep_exact':True})
    for p in [P/'work/zeus_reload_fx_20261113/evidence/body_fx_source_import.json',P/'work/zeus_family_20261114/evidence/family_body_fx_source.json',P/'work/zeus_skins_20261114/evidence/skin_body_fx_source.json']:
        external[p.relative_to(P).as_posix()]=sha(p)
    patch=W/'qa/tail_emitter_overrides.json';save(patch,overrides)
    save(W/'evidence/tail_ring_build.json',{'passed':True,'java_changes':False,'PNG_changes':False,'profiles':reports,
        'galaxy_tail_unchanged':'Own source xuanwo_0007 vortex textures retain the approved negative3s whole-circle route. No default electric/fire texture is borrowed.',
        'classification':'Default II dianqiu is original blue electric4x4 atlas; lava dianqiu is original orange burning4x4 atlas. Base/I have their own ring texture and original gold/cyan source colors; no new lightning artwork is added.',
        'adaptation':'User/video driven fixed Y +/-37.14487mm, 3mm from source receiver side bound. Retain source X/Z growth, texture/UV/alpha/lifetime/count. Recenter source random Z jitter for concentricity; equally phase ring births over1s. DefaultII ring holds original initial white-blue RGB instead of late source purple. No private engine-unit equivalence claimed.',
        'input_sha256':inputs,'external_source_sha256':external,'output_sha256':{p.relative_to(W).as_posix():sha(p) for p in [patch,*[W/r['resource']for r in reports]]}})
    print('PASS six default/lava tail bodies; source ring count/life/radial curve preserved; fixed Y')
if __name__=='__main__':main()
