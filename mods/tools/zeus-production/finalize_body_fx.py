"""Freeze source-only body FX overlay and exact QA input hashes for packaging."""
from pathlib import Path
import hashlib,json,zipfile
W=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads(p.read_text('utf8'))
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n','utf8')
def main():
    source=load(W/'evidence/body_fx_source_import.json');build=load(W/'evidence/body_fx_build.json');native=load(W/'evidence/body_fx_native_check.json');preview=load(W/'evidence/body_fx_preview.json');bytecode=load(W/'evidence/body_fx_bytecode_equivalence.json')
    assert all(x['passed']for x in (source,build,native,preview,bytecode))
    assert source['emitters']==15 and source['particles']==63 and len(source['textures'])==11
    for out,entry in source['textures'].items():
        assert sha(W/out)==entry['sha256']==sha(W.parents[1]/entry['source'])
    for rel,h in source['output_sha256'].items():assert sha(W/rel)==h
    for rel,h in native['input_sha256'].items():assert sha(W/rel)==h,rel
    for rel,h in preview['input_sha256'].items():assert sha(W/rel)==h,rel
    for rel,h in preview['output_sha256'].items():assert sha(W/rel)==h,rel
    overlay=build['runtime_overlay'];classes=build['class_overrides']
    with zipfile.ZipFile(W/overlay)as z:assert z.testzip()is None and sorted(z.namelist())==classes
    resources=sorted(['assets/ssjj_zeus/effects/body.json']+[str(Path(x).relative_to('pack')).replace('\\','/')for x in source['textures']])
    families=('cn/ssjjzeus/client/ZeusMeshRenderer','cn/ssjjdimension/client/DimensionParticles','cn/ssjjdimension/client/DimensionParticleEffects')
    assert all(any(n==p+'.class'or n.startswith(p+'$')for p in families)for n in classes)
    evidence=['body_fx_source_import.json','body_fx_alignment_source_audit.json','body_fx_source_reuse_inputs.json','body_fx_build.json','body_fx_remap.json','body_fx_runtime_links.json','body_fx_native_check.json','body_fx_bytecode_equivalence.json','body_fx_qa_remap.json','body_fx_preview.json','body_fx_preview_inputs.json','body_fx_production_samples.json','body_fx_api_audit.md']
    scripts=['import_body_fx.py','build_body_fx.py','check_body_fx.py','check_body_fx_bytecode.py','preview_body_fx.py','finalize_body_fx.py']
    paths=[W/overlay,*[W/'pack'/n for n in resources],*[p for p in (W/'src').rglob('*.java')],*[W/'scripts'/n for n in scripts],W/'qa/BodyFxCheck.java',W/'qa/BodyFxBytecodeCheck.java',W/'qa/audit_body_alignment.py',*[W/'evidence'/n for n in evidence],*[W/n for n in preview['output_sha256']]]
    inputs={p.relative_to(W).as_posix():sha(p)for p in paths}
    implementation={'passed':True,'runtime_overlay':overlay,'runtime_overlay_sha256':sha(W/overlay),'class_overrides':classes,'resource_overrides':resources,'source_references_sha256':source['input_sha256'],'source_textures':source['textures'],'existing_interfaces':{'sampler':'DimensionParticles scoped validator overload; ParticleFrames unchanged','draw':'DimensionParticleEffects Data defer overload; DimensionEffectRenderType unchanged','entry':'ZeusMeshRenderer existing unique resource callback and WeaponAnchor3 functional renderer'},'added_hook_or_renderer':False,'input_sha256':inputs}
    save(W/'evidence/body_fx_implementation.json',implementation)
    acceptance={'passed':True,'scope':'Zeus II original constant gun-body qianshen_Lv2 only','compile_actual_1_2_api_link':True,'source_15_emitters_63_particles_11_original_pngs':True,'source_anchor_verified':True,'existing_Forge_callbacks_executed_once':True,'actual_native_queue_per_draw_data_capture':True,'legacy_default_api_equivalence':{'old_profiles':native['shared_legacy_equivalence']['profiles'],'bit_identical_sampler_draw_cases':native['shared_legacy_equivalence']['draw_command_cases'],'unchanged_invalid_scope_cases':native['shared_legacy_equivalence']['invalid_scope_cases'],'exact_old_shared_methods_including_deferred_GPU_math':len(bytecode['exact_original_method_instruction_equivalence'])},'visual_review':{'passed':True,'image':'previews/body_fx_actual.png','time_preview':'previews/body_fx_time.gif','observed':'Original gold gun-body/wing/scroll layers track the gun, front feathers originate at their authored emitter, blue/gold ring remains on its source body region. Same native framing and source base gun preserved. Parent independently reviewed actual/timeline/comparison and accepted.','live_client_test':False},'clock_and_lifecycle':'Existing monotonic global render clock; source-derived per-emitter max(start)+max(life) cycles. Draw only through existing rendered model callback, no persistent entity or new event hook. Reload clears Zeus data before reading. Each queued draw captures its own Data.','limitations':source['adaptations_and_limits'],'live_client_test':False,'source_exact':False,'input_sha256':{**inputs,'evidence/body_fx_implementation.json':sha(W/'evidence/body_fx_implementation.json')}}
    save(W/'evidence/body_fx_acceptance.json',acceptance)
    print(json.dumps({'passed':True,'classes':len(classes),'resources':len(resources),'runtime_overlay':overlay,'implementation':'evidence/body_fx_implementation.json','acceptance':'evidence/body_fx_acceptance.json'}))
if __name__=='__main__':main()
