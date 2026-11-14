"""Refresh only proven audio-event dependencies of unchanged native geometry."""
from copy import deepcopy
from pathlib import Path
from prepare import W, read, save, sha


def main():
    baseline = W/'baseline_assets/assets/ssjj_zeus/animations/hand_of_zeuss2.animation.json'
    original = read(baseline)
    rows = {}
    for gun in ('hand_of_zeuss', 'hand_of_zeuss1'):
        relative = f'pack/assets/ssjj_zeus/animations/{gun}.animation.json'
        candidate = read(W/relative)
        expected = deepcopy(original)
        changes = []
        for clip in ('draw', 'reload_empty', 'reload_tactical'):
            event = expected['animations'][clip]['sound_effects']['0.0']
            old = event['effect']
            new = old.replace('ssjj_zeus:hand_of_zeuss2/', 'ssjj_zeus:'+gun+'/')
            assert old != new
            event['effect'] = new
            changes.append({'path': f'animations/{clip}/sound_effects/0.0/effect', 'before': old, 'after': new})
        assert expected == candidate, ('Changed pose/time/non-sound field', gun)
        native_path = W/f'evidence/thirdperson_native_{gun}.json'
        preview_path = W/f'evidence/thirdperson_preview_{gun}.json'
        native, preview = read(native_path), read(preview_path)
        for rel, digest in preview['native_dumps_sha256'].items():
            assert sha(W/rel) == digest, ('Native geometry changed', rel)
        for rel, digest in preview['output_sha256'].items():
            assert sha(W/rel) == digest, ('Preview image changed', rel)
        current = sha(W/relative)
        old_digest = native['input_sha256'][relative]
        assert old_digest in (sha(baseline), current)
        note = {'source_animation_sha256': sha(baseline), 'current_animation_sha256': current,
                'exact_changes': changes, 'all_TRS_duration_event_times_other_fields_equal': True,
                'native_geometry_and_preview_pixels_unchanged': True,
                'reason': 'This static third-person fixture does not evaluate animation or sound events. Only three audio event IDs changed; all transforms/timing remain exact.',
                'refresh_script_sha256': sha(Path(__file__))}
        native['input_sha256'][relative] = current
        native['sound_event_binding_refresh'] = note
        save(native_path, native)
        preview['input_sha256'][relative] = current
        preview['native_report_sha256'] = sha(native_path)
        preview['sound_event_binding_refresh'] = note
        save(preview_path, preview)
        rows[gun] = note
    save(W/'evidence/thirdperson_sound_binding_refresh.json', {'passed': True, 'guns': rows,
         'input_sha256': {'scripts/refresh_thirdperson_sound_bindings.py': sha(Path(__file__)),
                          **{f'pack/assets/ssjj_zeus/animations/{gun}.animation.json': sha(W/f'pack/assets/ssjj_zeus/animations/{gun}.animation.json') for gun in rows}},
         'native_reexported': False, 'preview_rerendered': False})
    print('PASS exact three sound-ID changes per new tier; native/preview unchanged')


if __name__ == '__main__':
    main()
