"""Merge disjoint video-tail, galaxy-wing and native HUD resource changes."""
from pathlib import Path
import copy,json,hashlib,sys
from prepare import W,VERSION,read,save,sha
GUNS=('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2')
def compact(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def main():
 patchfile=W/'qa/galaxy_wing_overrides.json';patches=read(patchfile)['overrides']
 allowed={
 'assets/ssjj_zeus/effects/body_hand_of_zeuss.json':['xuanzhuan','xuanzhuan_duplicate_2'],
 'assets/ssjj_zeus/effects/body_hand_of_zeuss1.json':['ring','ring_duplicate_2'],
 'assets/ssjj_zeus/effects/body.json':['ring','dianqiu'],
 **{f'assets/ssjj_zeus/effects/body_{g}_lava.json':['ring','dianqiu']for g in GUNS},
 **{f'assets/ssjj_zeus/effects/body_{g}_galaxy.json':['Particle_duplicate_7','Particle_duplicate_9']for g in GUNS}}
 inputs={Path(__file__).relative_to(W).as_posix():sha(Path(__file__)),patchfile.relative_to(W).as_posix():sha(patchfile)}
 for rel,patch in patches.items():
  assert rel in allowed and set(patch)==set(allowed[rel]);old=read(W/'baseline_assets'/rel);new=copy.deepcopy(old)
  for i,e in enumerate(new['emitters']):
   if e['name']in patch:new['emitters'][i]=patch[e['name']]
  compact(W/'pack'/rel,new)
 if '--effects-only' in sys.argv:
  print('READY nine effect routes for actual sampler previews; final HUD merge not yet signed');return
 tail=read(W/'evidence/tail_effect_acceptance.json');assert tail['passed']
 hud=read(W/'evidence/hud_icon_acceptance.json');assert hud['passed']
 details=[]
 for rel,names in allowed.items():
  a=read(W/'baseline_assets'/rel);b=read(W/'pack'/rel)
  assert {k:v for k,v in a.items()if k!='emitters'}=={k:v for k,v in b.items()if k!='emitters'}
  assert [e['name']for e in a['emitters']]==[e['name']for e in b['emitters']]
  actual=[]
  for x,y in zip(a['emitters'],b['emitters']):
   if x==y:continue
   actual.append(x['name']);assert x['name']in names,(rel,x['name'])
   for field in ('name','texture','render'):assert x[field]==y[field]
   assert len(x['particles'])==len(y['particles'])
   for p,q in zip(x['particles'],y['particles']):assert p['life']==q['life']and len(p['frames'])==len(q['frames'])
  assert set(actual)==set(names),(rel,'Missing intended change',actual,names)
  details.append({'resource':rel,'changed_emitters':actual,'protected_emitters':[e['name']for e in a['emitters']if e['name']not in names]})
  inputs['baseline_assets/'+rel]=sha(W/'baseline_assets'/rel)
 displays=[];hud_textures=[]
 for g in GUNS:
  for suffix in ('','_lava','_galaxy'):
   rel=f'assets/ssjj_zeus/display/guns/{g}{suffix}_display.json';old=read(W/'baseline_assets'/rel);new=read(W/'pack'/rel);expected=copy.deepcopy(old);expected['hud']=f'ssjj_zeus:gun/hud/{g}{suffix}_model';assert new==expected
   displays.append(rel);inputs['baseline_assets/'+rel]=sha(W/'baseline_assets'/rel)
   hudrel=f'assets/ssjj_zeus/textures/gun/hud/{g}{suffix}_model.png';assert (W/'pack'/hudrel).is_file()and not(W/'baseline_assets'/hudrel).exists();hud_textures.append(hudrel)
 resources={p.relative_to(W/'pack').as_posix()for p in (W/'pack').rglob('*')if p.is_file()};assert resources==set(displays)|set(allowed)|set(hud_textures)
 allow={'schema_version':1,'baseline_version':read(W/'manifests/baseline.json')['version'],'target_version':VERSION,'display_resources':displays,'hud_textures':hud_textures,'body_emitters':allowed,'body_metadata_fields':[]}
 save(W/'manifests/change_allowlist.json',allow)
 inputs['manifests/change_allowlist.json']=sha(W/'manifests/change_allowlist.json')
 for f in ['tail_effect_acceptance.json','hud_icon_acceptance.json']:inputs['evidence/'+f]=sha(W/'evidence'/f)
 save(W/'evidence/resource_merge.json',{'passed':True,'runtime_overlay':None,'class_overrides':[],'baseline_PNGs_unchanged':True,'new_horizontal_model_HUD_PNG_count':9,'protected_gameplay_camera_hands_audio_recipes_scopes':True,'details':details,'input_sha256':inputs,'output_sha256':{'pack/'+rel:sha(W/'pack'/rel)for rel in sorted(resources)}})
 print('PASS twenty-seven resource-only overrides merged; all non-target emitters and all Java protected')
if __name__=='__main__':main()
