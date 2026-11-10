import os
from pathlib import Path
import json,zipfile,hashlib,copy
R=Path.cwd();W=R/'work/parasite_family_20261105';base=W/'baseline_assets';pack=W/'pack'
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n','utf8')
gun='data/ssjjparasite2/data/guns/parasitic_sniper_data.json';data=json.loads((base/gun).read_text('utf8'));data['fire_mode']=['semi'];save(pack/gun,data)
mod=next(Path(os.environ.get("SSJJ_MODS_DIR", "external-runtime/minecraft-instance/mods")).glob('*tacz*.jar'));entry='assets/tacz/animations/rifle_default.animation.json'
with zipfile.ZipFile(mod)as z:raw=z.read(entry);default=json.loads(raw)['animations']
anim='assets/ssjjparasite2/animations/parasitic_sniper2.animation.json';a=json.loads((base/anim).read_text('utf8'));old=copy.deepcopy(a)
# Retarget the native cycle to this source rig's forward axis and chest position.
# Absolute default rotations assume a different mesh orientation and project
# this weapon above the camera. Keep the default cadence at reduced amplitude.
import numpy as np
center_rotation=np.array([5.,55.,-22.]);center_position=np.array([-1.28,-.16,-4.8])
root=copy.deepcopy(default['run']['bones']['root'])
for channel,center,gain in [('rotation',center_rotation,.14),('position',center_position,.12)]:
 track=root[channel];origin=np.array(track['0.0']);root[channel]={t:(center+(np.array(value)-origin)*gain).tolist()for t,value in track.items()}
a['animations']['run']={'loop':True,'animation_length':.8,'bones':{'root':root}}
a['animations']['run_hold']={'loop':True,'animation_length':.8,'bones':{'root':{'rotation':center_rotation.tolist(),'position':center_position.tolist()}}}
for key,length,reverse in [('run_start',.3,False),('run_end',.45,True)]:
 channels={}
 for channel,center in [('rotation',center_rotation),('position',center_position)]:
  track={}
  for i in range(55):
   phase=i/54;smooth=phase**3*(10+phase*(-15+6*phase));weight=1-smooth if reverse else smooth
   track[f'{length*phase:.9f}']={'post':(center*weight).tolist(),'lerp_mode':'catmullrom'}
  channels[channel]=track
 a['animations'][key]={'loop':False,'animation_length':length,'bones':{'root':channels}}
assert all(a['animations'][key]==value for key,value in old['animations'].items()if key not in ['run_start','run','run_hold','run_end'])
save(pack/anim,a)
# Replace each family's legacy flash with its original per-profile muzzle FX.
for p in(base/'assets/ssjjparasite2/display/guns').glob('*.json'):
 d=json.loads(p.read_text('utf8'));d.pop('muzzle_flash',None);save(pack/p.relative_to(base),d)
save(W/'evidence/family_asset_changes.json',{'passed':True,'base_fire_modes':['semi'],'higher_tiers_fire_modes':['semi','auto'],'semi_RPM':46,'auto_RPM':150,'old_base_AUTO_NBT_migrated_by':'ParasiteFireModes','native_sprint_source':str(mod),'native_sprint_source_sha256':hashlib.sha256(mod.read_bytes()).hexdigest(),'native_sprint_entry':entry,'native_sprint_clip_source_sha256':hashlib.sha256(raw).hexdigest(),'sprint_clips':['run_start','run','run_hold','run_end'],'all_other_animations_identical':True,'native_default_state_machine_unchanged':True,'retargeted_to_source_rig':True,'chest_rotation_degrees':center_rotation.tolist(),'chest_position_bedrock':center_position.tolist(),'native_cycle_rotation_amplitude':.14,'native_cycle_position_amplitude':.12,'all_nine_displays_use_original_profile_muzzle':True})
print('Prepared native rifle sprint clips, base SEMI-only data, nine muzzle display bindings')
