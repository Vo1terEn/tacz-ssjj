"""Disable Zeus world-ADS magnification with its existing display field only."""
import json
import re
from prepare import W, VERSION, read, save, sha

DISPLAY = 'assets/ssjj_zeus/display/guns/hand_of_zeuss2_display.json'


def main():
    target = W/'pack'/DISPLAY
    baseline = W/'baseline_assets'/DISPLAY
    original = target.read_text('utf8')
    before = json.loads(original)
    source = read(baseline)
    assert source['iron_zoom'] == 1.2
    assert before['iron_zoom'] in (1.0, 1.2)
    assert before['zoom_model_fov'] == source['zoom_model_fov']
    before_sha = sha(target)
    pattern = r'("iron_zoom"\s*:\s*)(?:1\.2|1\.0|1)(?=\s*[,}])'
    updated, count = re.subn(pattern, lambda match: match[1]+'1.0', original)
    assert count == 1
    expected = dict(before, iron_zoom=1.0)
    assert json.loads(updated) == expected
    target.write_text(updated, 'utf8', newline='')
    save(W/'evidence/ads_data_change.json', {
        'passed': True, 'version': VERSION,
        'resource': DISPLAY, 'field': 'iron_zoom',
        'baseline_value': source['iron_zoom'], 'candidate_value': expected['iron_zoom'],
        'preserved_zoom_model_fov': source['zoom_model_fov'],
        'only_field_changed_by_this_script': before == expected or
            {key for key in before if before[key] != expected[key]} == {'iron_zoom'},
        'baseline_display_sha256': sha(baseline), 'before_display_sha256': before_sha,
        'input_sha256': {'pack/'+DISPLAY: sha(target)},
        'resource_files_written': ['pack/'+DISPLAY],
        'production_java_changes': 0, 'configuration_writes': 0,
        'instance_or_delivery_writes': 0,
        'boundary': 'Only this gun existing iron_zoom display field changes. Native parser and world-FOV QA are separate.'
    })
    print('PASS Zeus iron_zoom 1.2 -> 1.0; original model FOV and every other display field retained')


if __name__ == '__main__':
    main()
