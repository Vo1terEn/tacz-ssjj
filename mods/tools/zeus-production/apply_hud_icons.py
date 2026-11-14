"""Select a separate horizontal model HUD render while preserving native inventory icons."""
from pathlib import Path
from prepare import W,read,save,sha

def main():
 changes={}
 for p in sorted((W/'baseline_assets/assets/ssjj_zeus/display/guns').glob('hand_of_zeuss*_display.json')):
  before=read(p);after=dict(before);after['hud']='ssjj_zeus:gun/hud/'+p.stem.removesuffix('_display')+'_model'
  rel=p.relative_to(W/'baseline_assets');target=W/'pack'/rel;save(target,after)
  assert {k for k in before if before[k]!=after[k]}=={'hud'}
  changes[rel.as_posix()]={'before_hud':before['hud'],'after_hud':after['hud'],'slot':before['slot'],'sha256':sha(target)}
 assert len(changes)==9
 save(W/'evidence/hud_icon_implementation.json',{'passed':True,'runtime_overlay':None,'class_overrides':[],'resource_overrides':list(changes),'field_scope':'hud only selects new horizontal model render; slot refs and all original slot PNG bytes retained','changes':changes})
 print('PASS nine HUD refs select their own horizontal model renders; original inventory refs retained')
if __name__=='__main__':main()
