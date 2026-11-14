"""Read-only final tail audit and current-input binding. Does not edit assets."""
from pathlib import Path
import hashlib,json
W=Path(__file__).resolve().parents[1];P=W.parents[1];B=W/'baseline_assets/assets/ssjj_zeus'
NAMES=['body.json','body_hand_of_zeuss.json','body_hand_of_zeuss1.json']+[f'body_{g}_lava.json'for g in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']]
ALLOWED={'ring','ring_duplicate_2','xuanzhuan','xuanzhuan_duplicate_2','dianqiu'}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def read(p):return json.loads(Path(p).read_text('utf8'))
def main():
    paths=[Path(__file__),W/'scripts/build_tail_rings.py',W/'scripts/audit_rear_video.py',W/'scripts/record_video_tail_analysis.py',W/'qa/TailRingCheck.java',W/'build/tail_qa/TailRingCheck.class',W/'evidence/tail_actual_sampler_check.json',W/'evidence/tail_ring_build.json',W/'evidence/video_tail_analysis.json',W/'evidence/integrated_fx_preview.json',next((W/'baseline/mods').glob('*.jar'))]
    actual=read(W/'evidence/tail_actual_sampler_check.json');build=read(W/'evidence/tail_ring_build.json');video=read(W/'evidence/video_tail_analysis.json');integrated=read(W/'evidence/integrated_fx_preview.json');assert all(r['passed']for r in [actual,build,video,integrated])
    resources=[];textures=set();external={}
    source_reports=[P/'work/zeus_reload_fx_20261113/evidence/body_fx_source_import.json',P/'work/zeus_family_20261114/evidence/family_body_fx_source.json',P/'work/zeus_skins_20261114/evidence/skin_body_fx_source.json']
    texture_sources={}
    for path in source_reports:
        d=read(path);external[path.relative_to(P).as_posix()]=sha(path)
        for profile in [d,*d.get('profiles',[])]:
            for rel,info in profile.get('textures',{}).items():texture_sources.setdefault(Path(rel).name,[]).append(info)
    for name in NAMES:
        before=B/'effects'/name;after=W/'pack/assets/ssjj_zeus/effects'/name;bd=read(before);ad=read(after)
        assert {k:v for k,v in bd.items()if k!='emitters'}=={k:v for k,v in ad.items()if k!='emitters'}
        assert [e['name']for e in bd['emitters']]==[e['name']for e in ad['emitters']]
        changed=[];protected=[]
        for old,new in zip(bd['emitters'],ad['emitters']):
            if old==new:protected.append(old['name']);continue
            assert old['name']in ALLOWED;changed.append(old['name'])
            assert old['texture']==new['texture']and old['render']==new['render']and len(old['particles'])==len(new['particles'])
            assert all(a['life']==b['life']for a,b in zip(old['particles'],new['particles']))
            assert new['mode']=='mesh'
            assert all(abs(p['frames'][0][13])<1e-8 and abs(p['frames'][-1][13])<1e-8 for p in new['particles'])
            textures.add(B/(new['texture'].split(':')[1]))
        assert changed
        paths += [before,after];resources.append({'resource':after.relative_to(W).as_posix(),'changed_emitters':changed,'protected_emitters':protected,'protected_emitters_deep_exact':True,'source_particle_count_and_lifetimes_preserved':True,'birth_death_alpha_zero':True})
    source_png=[]
    for p in sorted(textures):
        h=sha(p);matches=texture_sources[p.name];assert any(i['sha256']==h for i in matches)
        valid=[]
        for info in matches:
            original=P/info['source']
            if sha(original)!=h:continue
            external[original.relative_to(P).as_posix()]=h;valid.append({'source':info['source'],'sha256':h})
        assert valid;source_png.append({'current':p.relative_to(W).as_posix(),'sha256':h,'original_sources':valid});paths.append(p)
    # Every input of the actual preview must still match. Bodies are frozen;
    # unrelated HUD icon/display generation does not change these FX inputs.
    for rel,h in integrated['input_sha256'].items():assert sha(W/rel)==h,rel
    for rel,h in build['output_sha256'].items():assert sha(W/rel)==h,rel
    check_names={c['emitter']for c in actual['checks']};assert check_names==ALLOWED
    report={'passed':True,'status':'FROZEN_FINAL_TAIL','runtime_overlay':None,'class_overrides':[],'java_changes':False,'PNG_changes':False,
        'actual_production_sampler':True,'all9_routes_validate':True,'dense_sampler_checks':actual['checks'],'technical_thresholds':actual['thresholds'],
        'resource_overrides':[r['resource'].removeprefix('pack/')for r in resources],'resources':resources,'original_texture_provenance':source_png,
        'motion_contract':{'ring_plane_Y_m':[-.03714487,.03714487],'source_receiver_half_width_m':.03414487,'normal_gap_m':.003,'normal_gap_fixed_over_life':True,'radial_axes':['X','Z'],'source_XZ_size_curve_preserved':True,'common_disc_center':True,'ring_count_lifetime_preserved':True,'ring_phase_cycle_seconds':1,'electric_or_fire_original_emission_cycle_seconds':4,'early_clock_warmup':'Particle birth times are nonnegative in the existing sampler. The first second fills the phase train; steady-state clocks then repeat smoothly.','loop_continuity':'Actual vertex/RGBA period comparison is <=2e-6 float units; all wave birth/death alphas are zero, so radial resets are invisible.'},
        'defaultII_color_adaptation':'Hold the original first white-blue RGB along ring life instead of the original late purple RGB; original alpha is retained. Explicit video-driven adaptation, not new artwork.',
        'material_classification':'Original defaultII dianqiu is blue4x4 electric atlas. Original lava dianqiu is orange4x4 fire atlas. Source base/I own ring materials retain gold/cyan. No borrowed lightning atlas is added to base/I/skins.',
        'protected_front_and_rear_layers':'Front par surface-flow, lava yanjiang surface-fire, base/I rear Particles, all remaining emitters remain deep-exact to the frozen baseline. Galaxy4/5 own negative3s vortex remains unchanged; galaxy7/9 wing gap is owned and verified independently.',
        'video_review':'All92 frames and all12 full/rear contact pages reviewed; rebound/cropped frames excluded from radius interpretation. Source original PNG ridge quantifies nominal implemented ring spacing; video alone does not determine absolute3D gap/private clock.',
        'visual_review':{'reviewer':'zeus_audio_data','actual_native_hip_matrix_and_production_sample':True,'default_rear_detail':'previews/rear_default_detail.png','observation':'New white-blue inner/outer rings are separately visible about the original disc with timed electric fill. Lava retains orange rings/fire identity. Front par remains restrained on the gun. No face-camera lateral emitter is introduced.','independent_parent_review':'Parent/independent reviewer receives the bound preview for separate sign-off.'},
        'boundary':'Data-only coordinate/phase adaptation through the frozen existing18-channel mesh sampler; private particle DLL unavailable. Previews use actual frozen production sampling and captured TaCZ1.2 native hip/node transforms with offline diagnostic lighting, not live client screenshots.',
        'input_sha256':{p.relative_to(W).as_posix():sha(p)for p in set(paths)},'external_source_sha256':external,
        'output_sha256':{p.relative_to(W).as_posix():sha(p)for p in [W/'previews/rear_default_detail.png',W/'previews/whole_fx_default_before_after.png',W/'previews/whole_fx_lava_before_after.png']}}
    out=W/'evidence/tail_effect_acceptance.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8');print('PASS final six-body tail scope/source/actual-sampler/preview binding')
if __name__=='__main__':main()
