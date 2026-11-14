"""Export supplied playerStatus 30017 / hand_of_zeuss_dian / gandian only."""
from pathlib import Path
from prepare import W,ROOT,sha,save
import prepare_source_fx as source

def main():
    cat=source.catalog('hand_of_zeuss2')
    record=source.export(cat,'gandian','orb','hand_of_zeuss2_shock')
    assert len(record['traces'])==3 and not record['skipped']
    assert record['source_config']['effectName']=='playerinfo_30017'
    inputs={};outputs={}
    def bind(p):
        p=Path(p).resolve();inputs[(p.relative_to(W) if p.is_relative_to(W) else p).as_posix()]=sha(p)
    for name in ('effectConfig','particleEffectConfig'):
        path=next((cat['rig']/'EmbeddedData').glob(name+'__*.bytes'));content=source.raw(path).decode('utf8')
        assert ('hand_of_zeuss_dian' in content) and ('gandian.uf3d' in content if name=='effectConfig' else 'playerStatus="30017"' in content)
    for key in source.INPUTS:bind(ROOT/key)
    for path in [Path(__file__),W/'scripts/prepare_source_fx.py',Path(source.f.__file__),Path(source.f.s.__file__),Path(source.f.d.__file__)]:bind(path)
    for relative in source.TEXTURES:outputs[relative]=sha(W/relative)
    fx=W/'pack/assets/ssjj_zeus/effects/thunder/hand_of_zeuss2_shock.json';outputs[fx.relative_to(W).as_posix()]=sha(fx)
    for trace in record['traces']:
        floats=dict(trace['material']['fields']['m_SavedProperties']['m_Floats'])
        assert floats['_BlendDst']==1 and floats['_ZTest']==4 and floats['_ZWrite']==0
    save(W/'evidence/shock_fx_resources.json',{'passed':True,'input_sha256':inputs,'output_sha256':outputs,'source_effect':record,'textures':source.TEXTURES,
        'source_identity':'playerStatus 30017 -> hand_of_zeuss_dian -> gandian.uf3d -> original three layers',
        'orange':'Original Particle lifetime curve contains orange (1,0.328758564,0.040681371); PNG/tint/curves unchanged, no recolor.',
        'adaptation':'Original mesh/UV/transform/particle curves/three additive layers and PNG bytes. Per-emitter native source cycles repeat during server-owned 100 ticks. Body dimensions adapt original .6 x 1.8 block humanoid space affinely. Private SSJJ runtime absent; fixed source RNG seed and sampled source curves, not pixel-exact engine reproduction.',
        'live_client_test':False})
    print('PASS original gandian body shock source',len(outputs),'outputs')
if __name__=='__main__':main()
