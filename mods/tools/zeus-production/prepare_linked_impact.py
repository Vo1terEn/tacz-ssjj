"""Remove scene-darkening impact mattes using the original particles and textures.

The supplied standard impact's glow_an is an alpha-blended dark-purple overlay;
the lava impact's yanwu01 is an alpha-blended black overlay. These are real source
material layers, not an opaque PNG export. The user's explicit request to remove
the black background is implemented as an impact-only additive blend adaptation.
No particle, geometry, tint, alpha, curve, texture, UV, sound or timing is replaced.
"""
from pathlib import Path
from prepare import W, read, save, sha

IMPACTS=('hand_of_zeuss1_impact','hand_of_zeuss2_impact','lava_impact')

def main():
    changes=[]
    for name in IMPACTS:
        rel=Path('assets/ssjj_zeus/effects/thunder')/(name+'.json')
        old=read(W/'baseline_assets'/rel)
        for emitter in old['emitters']:
            if emitter['render']=='alpha':
                changes.append({'effect':name,'emitter':emitter['name'],
                    'original_blend':'alpha','output_blend':'additive',
                    'texture':emitter['texture']})
                emitter['render']='additive'
        out=W/'pack'/rel
        out.parent.mkdir(parents=True,exist_ok=True)
        out.write_text(__import__('json').dumps(old,ensure_ascii=False,separators=(',',':'))+'\n','utf8')
    assert [(x['effect'],x['emitter']) for x in changes]==[
        ('hand_of_zeuss1_impact','glow_an'),('hand_of_zeuss2_impact','glow_an'),('lava_impact','yanwu01')]
    save(W/'evidence/linked_impact_adaptation.json',{
        'passed':True,'changes':changes,
        'policy':'Explicit impact-only blend adaptation requested by user to remove black/dark scene overlay; not a claim of unchanged source material blend.',
        'unchanged':'Every original emitter, particle, geometry, UV, lifetime, alpha/RGB curve and PNG byte is retained; no generated artwork or global texture/shader change.',
        'input_sha256':{'scripts/prepare_linked_impact.py':sha(Path(__file__)),**{
            ('baseline_assets/assets/ssjj_zeus/effects/thunder/'+n+'.json'):sha(W/'baseline_assets/assets/ssjj_zeus/effects/thunder'/ (n+'.json')) for n in IMPACTS}},
        'output_sha256':{('pack/assets/ssjj_zeus/effects/thunder/'+n+'.json'):sha(W/'pack/assets/ssjj_zeus/effects/thunder'/(n+'.json')) for n in IMPACTS}})
    print('PASS impact-only blend adaptation; all original particle states and textures retained')

if __name__=='__main__':main()
