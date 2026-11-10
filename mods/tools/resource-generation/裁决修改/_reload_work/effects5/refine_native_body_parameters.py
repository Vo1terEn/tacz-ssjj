"""Add active native birth ranges; preserve reviewed geometry and calibration ratios."""
from pathlib import Path
import json,hashlib
HERE=Path(__file__).resolve().parent
P=HERE/'resources/assets/judgfire/fx/default_native.json'
def main():
    d=json.loads(P.read_text(encoding='utf8'))
    report=json.loads((HERE/'asset_audit/native_body_runtime_gaps.json').read_text(encoding='utf8'))
    by_name={e['name']:e for e in d['emitters']}
    for r in report['inventory']:
        e=by_name[r['name']];s=r['source_active']
        for key,target in [('lifetime','lifeRange'),('speed','speedRange'),('size','sizeRange'),('rotationZ','rotationRange')]:
            e[target]=s[key]['active_endpoints']
        e['prewarm']=s['prewarm'];e['loop']=s['loop'];e['duration']=s['duration'];e['localSpace']=s['simulationSpace']==0
        e['velocityRanges']=[v['active_endpoints'] for v in s['velocity']['xyz']] if s['velocity']['enabled'] else [[0,0]]*3
        colors=s['startColor']['active_endpoints']
        if len(colors)==2:
            source_max=colors[0];ratio=[e['startColor'][i]/source_max[i] if source_max[i] else 0 for i in range(4)]
            e['startColorRange']=[[c[i]*ratio[i] for i in range(4)] for c in colors]
        else:e.pop('startColorRange',None)
        for key in ['radiusThickness','sphericalDirectionAmount','randomDirectionAmount']:
            e['shape'][key]=s['shape'].get(key,1 if key=='radiusThickness' else 0)
    src=json.loads((HERE/'asset_audit/source_native_body.json').read_text(encoding='utf8'))
    particles={n['id']:c['data'] for n in src['nodes'] for c in n['components'] if c['type']=='ParticleSystem'}
    for g in d['groups']:
        source=particles.get(g.get('source_node'))
        if source is not None:g['prewarm']=source['prewarm']
    d['native_birth_revision']={'revision':1,'source':'asset_audit/native_body_runtime_gaps.json; native E001/E002/E015 active InitialModule/ShapeModule/VelocityModule values','behavior':'Native rate births retain individual sampled lifetimes, separate stable random property channels, source prewarm, loop/duration and max live cap. World-space body_sui retains birth world position/velocity.','random_sequence':'Deterministic reconstruction; original Unity RNG seed/state and subframe spawn transform interpolation are unavailable. World birth snapshots are captured at the first rendered sample, with velocity backdating.','deferred':'Unity internal NoiseModule and LimitVelocity implementation for E015 glow remains an explicit fidelity limitation. No invented sine noise is substituted.'}
    d['hit_absorption']['max_trigger_window_seconds']=3
    P.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    print(json.dumps({'body_sha256':hashlib.sha256(P.read_bytes()).hexdigest(),'native_emitters_updated':len(report['inventory'])},indent=2))
if __name__=='__main__':main()
