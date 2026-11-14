"""Route the existing Zeus II recipe to Rongguang using three resource overrides."""
from pathlib import Path
from copy import deepcopy
from zipfile import ZipFile
import hashlib
import json
import re

W = Path(__file__).resolve().parents[1]
GUN = 'ssjj_zeus:hand_of_zeuss2'
GROUP = 'ssjj_workbench:weapons'
PATTERN = '^ssjj_zeus:.*$'
RECIPE = 'data/ssjj_zeus/recipes/gun/hand_of_zeuss2.json'
BENCH_FILTER = 'data/ssjj_workbench/recipe_filters/rongguang.json'
DEFAULT_FILTER = 'data/tacz/recipe_filters/default.json'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def file_digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main():
    frozen = json.loads((W / 'manifests/baseline.json').read_text('utf-8'))
    inputs = {}
    for kind in ('mod', 'pack'):
        entry = frozen['pair'][kind]
        path = W / 'baseline' / entry['path']
        actual = file_digest(path)
        if actual != entry['sha256']:
            raise AssertionError(f'Frozen {kind} archive changed: {path}')
        inputs[kind] = {'path': str(path.resolve()), 'sha256': actual}

    with ZipFile(inputs['pack']['path']) as source:
        old_raw = {name: source.read(name) for name in (RECIPE, BENCH_FILTER, DEFAULT_FILTER)}
        recipe, bench, default = (json.loads(old_raw[name]) for name in (RECIPE, BENCH_FILTER, DEFAULT_FILTER))
        if recipe.get('type') != 'tacz:gun_smith_table_crafting' or recipe['result'].get('type') != 'gun' or recipe['result'].get('id') != GUN:
            raise AssertionError('Frozen recipe does not identify the existing Zeus II gun')
        existing_guns = []
        for name in source.namelist():
            if name.startswith('data/') and '/recipes/' in name and name.endswith('.json'):
                value = json.loads(source.read(name))
                if value.get('type') == 'tacz:gun_smith_table_crafting' and value.get('result', {}).get('type') == 'gun':
                    existing_guns.append(value['result']['id'])
        if len(existing_guns) != len(set(existing_guns)) or existing_guns.count(GUN) != 1:
            raise AssertionError('Gun recipe IDs are not unique in the frozen pack')

    updated_recipe = deepcopy(recipe)
    updated_recipe['result']['group'] = GROUP
    updated_bench, updated_default = deepcopy(bench), deepcopy(default)
    for value, key in ((updated_bench, 'whitelist'), (updated_default, 'blacklist')):
        patterns = value.setdefault(key, [])
        if PATTERN not in patterns:
            patterns.append(PATTERN)
    identifier = 'ssjj_zeus:gun/hand_of_zeuss2'
    if not any(re.fullmatch(pattern, identifier) for pattern in updated_bench['whitelist']):
        raise AssertionError('Rongguang filter does not admit the Zeus recipe ID')
    if any(re.fullmatch(pattern, identifier) for pattern in updated_bench.get('blacklist', [])):
        raise AssertionError('Rongguang blacklist rejects the Zeus recipe ID')
    if not any(re.fullmatch(pattern, identifier) for pattern in updated_default['blacklist']):
        raise AssertionError('Default workbench must exclude the routed recipe')

    changed = {}
    for name, value in ((RECIPE, updated_recipe), (BENCH_FILTER, updated_bench), (DEFAULT_FILTER, updated_default)):
        raw = encode(value)
        path = W / 'pack' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
        changed[name] = {'baseline_sha256': digest(old_raw[name]), 'overlay_sha256': digest(raw)}

    report = {
        'passed': True,
        'status': 'EXISTING_RECIPE_ROUTED_TO_RONGGUANG_WEAPONS',
        'gun_id': GUN,
        'recipe_id': identifier,
        'group': GROUP,
        'entry_method': 'Existing TaCZ gun_smith_table_crafting result.group and native recipe filters',
        'baseline': inputs,
        'overrides': changed,
        'retained_materials': recipe['materials'],
        'retained_result_without_group': {key: value for key, value in recipe['result'].items() if key != 'group'},
        'existing_gun_recipe_count': len(existing_guns),
        'added_recipe_count': 0,
        'tabs_changed': False,
        'java_changed': False,
        'view_resources_written': False,
        'live_client_test': False,
        'instance_modified': False,
        'boundary': 'Resource overlay generation only; independent workbench_check.py validates effective resources and may inspect packaged pair.',
    }
    path = W / 'evidence/workbench_integration.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode(report))
    print('PASS: routed the existing Zeus II recipe to Rongguang; three resource overrides, original costs retained')


if __name__ == '__main__':
    main()
