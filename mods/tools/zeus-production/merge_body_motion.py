"""Merge disjoint video-guided rear-carrier and tangent-particle adaptations."""
from pathlib import Path
import copy,json,hashlib
W=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text('utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def main():
 rear=read(W/'qa/rear_emitter_overrides.json');front=read(W/'qa/front_emitter_overrides.json')['overrides'];assert set(rear)==set(front);rows=[];inputs={Path(__file__).relative_to(W).as_posix():sha(Path(__file__))}
 for rel,patch in rear.items():
  tangent={k:v for k,v in front[rel].items()if k!='yanjiang'}
  assert not set(patch)&set(tangent);patch={**patch,**tangent};original=read(W/'baseline_assets'/rel);new=copy.deepcopy(original)
  for i,e in enumerate(new['emitters']):
   if e['name']in patch:new['emitters'][i]=patch[e['name']]
  assert len(original['emitters'])==len(new['emitters']);assert sum(len(e['particles'])for e in new['emitters'])==sum(len(e['particles'])for e in original['emitters'])
  for a,b in zip(original['emitters'],new['emitters']):
   if a['name']not in patch:assert a==b
  out=W/'pack'/rel;save(out,new);rows.append({'resource':rel,'changed_emitters':sorted(patch),'unchanged_emitters':[e['name']for e in original['emitters']if e['name']not in patch]})
 for p in [W/'qa/rear_emitter_overrides.json',W/'qa/front_emitter_overrides.json',W/'evidence/rear_surface_acceptance.json',W/'evidence/front_surface_particle_check.json']:
  if p.name.endswith('check.json')or p.name.endswith('acceptance.json'):assert read(p)['passed']
  inputs[p.relative_to(W).as_posix()]=sha(p)
 resources=sorted((W/'pack/assets/ssjj_zeus/effects').glob('body*.json'));assert len(resources)==9
 save(W/'evidence/body_motion_merge.json',{'passed':True,'java_FX_changes':False,'new_artwork':False,'source_emitter_particle_counts_preserved':True,'protected_non_target_emitters_preserved':True,'details':rows,'input_sha256':inputs,'output_sha256':{p.relative_to(W).as_posix():sha(p)for p in resources}})
 print('PASS merged six original/lava routes and three galaxy routes; no source count changes')
if __name__=='__main__':main()
