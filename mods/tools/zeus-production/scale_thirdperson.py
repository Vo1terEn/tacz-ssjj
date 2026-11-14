"""Enlarge an existing TaCZ third-person presentation around its real grip point.

TaCZ1.2 BonesItem has no JSON scale. Use the existing display scale and
compensate the positioning-node pivot; other presentation contexts stay equal.
"""
from pathlib import Path
from copy import deepcopy
import argparse
from prepare import W, read, save, sha

FACTOR = 1.25
# Existing source black pistol-grip surface point, in native rootless blocks.
GRIP_NATIVE = [.035225859, -.040466507, .171297665]
GRIP_GEO = [GRIP_NATIVE[0] * 16, -GRIP_NATIVE[1] * 16, GRIP_NATIVE[2] * 16]


def scale_existing_thirdperson(geo, display, factor=FACTOR, anchor_geo=GRIP_GEO):
    """Return copies. anchor_geo is a root-model point in Bedrock pixel axes.

    For uniform k, native positioning is k*(point-pivot). Thus the compensated
    pivot q-(q-p)/k keeps semantic point q at exactly its previous hand position.
    This handles an existing positive uniform display scale as a relative factor.
    """
    assert factor > 0
    result_geo, result_display = deepcopy(geo), deepcopy(display)
    node = next(b for b in result_geo['minecraft:geometry'][0]['bones']
                if b['name'] == 'thirdperson_hand')
    assert node.get('parent') == 'root', 'Anchor coordinates require the current root locator'
    root = next(b for b in result_geo['minecraft:geometry'][0]['bones'] if b['name'] == 'root')
    assert root.get('pivot', [0, 0, 0]) == [0, 0, 0] and not root.get('rotation')
    original_pivot = node['pivot']
    node['pivot'] = [q - (q - p) / factor for p, q in zip(original_pivot, anchor_geo)]
    scale = result_display.setdefault('transform', {}).setdefault('scale', {})
    previous = scale.get('thirdperson', [1., 1., 1.])
    assert len(previous) == 3 and previous[0] > 0 and len(set(previous)) == 1
    scale['thirdperson'] = [float(x) * factor for x in previous]
    probe_geo = deepcopy(result_geo)
    next(b for b in probe_geo['minecraft:geometry'][0]['bones']
         if b['name'] == 'thirdperson_hand')['pivot'] = original_pivot
    probe_display = deepcopy(result_display)
    if 'thirdperson' in display.get('transform', {}).get('scale', {}):
        probe_display['transform']['scale']['thirdperson'] = previous
    else:
        probe_display['transform']['scale'].pop('thirdperson')
    assert probe_geo == geo and probe_display == display
    return result_geo, result_display


def apply(gun='hand_of_zeuss2', source_root=None, factor=FACTOR):
    source_root = Path(source_root) if source_root else W/'baseline_assets'
    geo_rel = f'assets/ssjj_zeus/geo_models/gun/{gun}_geo.json'
    display_rel = f'assets/ssjj_zeus/display/guns/{gun}_display.json'
    if source_root.resolve() == (W/'pack').resolve():
        unscaled = W/'qa/thirdperson_unscaled'
        # New family tiers have no frozen archive entry. Preserve their imported
        # 1.00x inputs once, and use them on repeat to avoid compounded scaling.
        if not (unscaled/geo_rel).exists():
            assert read(source_root/display_rel)['transform']['scale']['thirdperson'] == [1, 1, 1]
            for rel in (geo_rel, display_rel):
                source = source_root/rel
                target = unscaled/rel
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
        source_root = unscaled
    geo, display = read(source_root/geo_rel), read(source_root/display_rel)
    source_digests = {p.relative_to(W).as_posix(): sha(p)
                      for p in (source_root/geo_rel, source_root/display_rel)}
    new_geo, new_display = scale_existing_thirdperson(geo, display, factor)
    save(W/'pack'/geo_rel, new_geo)
    save(W/'pack'/display_rel, new_display)
    record = {'passed': True, 'gun': gun, 'relative_factor': factor,
              'grip_source_native_blocks': GRIP_NATIVE, 'grip_anchor_geo_pixels': GRIP_GEO,
              'source_sha256_before_scaling': source_digests,
              'output_sha256': {('pack/'+p): sha(W/'pack'/p) for p in (geo_rel, display_rel)},
              'changed_fields': ['geo.thirdperson_hand.pivot', 'display.transform.scale.thirdperson'],
              'source_script_sha256': sha(Path(__file__)),
              'native_validation_required': True}
    save(W/f'evidence/thirdperson_scale_{gun}.json', record)
    print('PASS scoped third-person scale data', gun, new_display['transform']['scale']['thirdperson'])
    return record


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--gun', default='hand_of_zeuss2')
    p.add_argument('--source-root', type=Path)
    p.add_argument('--factor', default=FACTOR, type=float)
    args = p.parse_args()
    apply(args.gun, args.source_root, args.factor)
