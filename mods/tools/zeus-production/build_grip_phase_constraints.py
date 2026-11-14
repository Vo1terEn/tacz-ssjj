"""Derive attachment/release constraints from original ANM evidence, not pack edits."""
from pathlib import Path
import json,hashlib
import numpy as np
W=Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text('utf8'))
motion=read(W/'evidence/source_grip_motion.json');surface=read(W/'evidence/grip_surface_measurements.json');body=read(W/'evidence/source_body_native.json')
def part_for_triangle(index):
    base=0
    for p in body:
        count=len(p['triangles'])
        if base<=index<base+count:return {'bone':p['bone'],'part_triangle_index':index-base,'combined_triangle_index':index}
        base+=count
    raise IndexError(index)
def intervals(samples,side,threshold):
    # Piecewise linear threshold crossings, used as phase guides only.
    ts=np.array([p['time'] for p in samples]);v=np.array([p['delta_from_stand_contact_blocks'][side] for p in samples])
    if len(ts)<2 or ts[-1]==0:return {'attached':[[0.,float(ts[-1])]],'release':[]}
    boundaries=[float(ts[0])]
    for i in range(len(ts)-1):
        if (v[i]>threshold)!=(v[i+1]>threshold):boundaries.append(float(ts[i]+(threshold-v[i])/(v[i+1]-v[i])*(ts[i+1]-ts[i])))
    boundaries.append(float(ts[-1]));r={'attached':[],'release':[]}
    for a,b in zip(boundaries,boundaries[1:]):
        state='release' if np.interp((a+b)/2,ts,v)>threshold else 'attached';r[state].append([a,b])
    return r
targets={
    'left':{'semantic':'Fore-end underside, supporting hand; do not target receiver side or the magazine.','rootless_surface_point':surface['left_support_reference']['point'],'outward_normal':surface['left_support_reference']['outward_normal'],**part_for_triangle(1803),'preferred_contact_rootless_region':{'X':[-.065,.055],'Y':[-.045,.015],'Z':[-.21,-.07]},'alternative_front_finger_surface':surface['queries']['source_left_finger_roots']['point']},
    'right':{'semantic':'Black rear pistol-grip side/lower area; source fingers curl to grip while source palm mean lies farther back near silver stock strut. Do not use palm mean as final grip anchor.','rootless_surface_point':surface['semantic_grip_targets']['right_black_grip_mid']['point'],'outward_normal':surface['semantic_grip_targets']['right_black_grip_mid']['outward_normal'],**part_for_triangle(916),'preferred_contact_rootless_region':{'X':[.010,.070],'Y':[-.085,.005],'Z':[.12,.195]},'upper_surface':surface['semantic_grip_targets']['right_black_grip_upper']['point'],'lower_surface':surface['semantic_grip_targets']['right_black_grip_lower']['point']}}
mapping={'static_idle':'TORSO_STAND','draw':'TORSO_RAISE','shoot':'TORSO_ATTACK','reload_empty':'TORSO_RELOAD','reload_tactical':'TORSO_RELOAD','inspect':'TORSO_SHOWGUN','inspect_empty':'TORSO_SHOWGUN'}
mapping.update({n:'TORSO_WALK' for n in ('walk','run','aim_walk','aim_run','walk_forward','walk_backward','walk_sideway','walk_aiming','run_hold')})
clips={}
for name,clip in mapping.items():
    m=motion['clips'][clip];duration=m['duration'];out={'source_clip':clip,'source_duration_seconds':duration,'scope':'source kinematic phase guide; retain original gun tracks and original clip timing/events','sides':{}}
    for side,letter in [('left','L'),('right','R')]:
        phases=intervals(m['samples'],letter,.04);strong=intervals(m['samples'],letter,.08)
        out['sides'][side]={'maximum_source_contact_displacement_from_stand_gun_anchor_blocks':m['maximum_contact_displacement_gun_anchor'][letter],'attached_intervals_seconds':phases['attached'],'release_or_reposition_intervals_seconds':phases['release'],'strong_release_over_08_blocks_intervals_seconds':strong['release'],'attached_target':side,'attached_instruction':'Keep actual final four skin-pixel palm plus sleeve in contact with the designated moving gun surface, no full-arm/sleeve triangle intersections; do not attach only a cap point.','release_instruction':'Preserve source intentional hand path and orientation trend. Avoid full-arm/sleeve collisions, but do not attract or hard-lock the hand back onto the grip. Blend correction continuously through the source phase boundaries.'}
        if name=='static_idle':
            out['sides'][side]['attached_intervals_seconds']=[[0.,1/30]]
            out['sides'][side]['loop_behavior']='Held source stand frame0 for all time while static_idle is active.'
    if clip=='TORSO_RELOAD':out['notes']='Left hand remains the supporting hand despite modest .03258-block source drift. Right hand releases the grip to reload. Do not swap roles or force both onto grip throughout reload.'
    if clip=='TORSO_SHOWGUN':out['notes']='Right hand stays attached throughout the source inspection (only .00350-block positional drift); left hand intentionally releases/repositions. Correct right hand twist with the animated gun, not a fixed world point.'
    if clip=='TORSO_RAISE':out['notes']='Right hand stays attached. Left early motion includes arrival/reposition phases; the 4cm intervals are approximate kinematic guides, not authored release events.'
    clips[name]=out
for name in ('run_start','run_end','put_away'):
    clips[name]={'source_clip':None,'source_mechanism':'Existing authored source-pose lowering or WALK envelope; preserve authored clip duration and blend semantics.','sides':{s:{'attached_intervals':'entire authored clip, because the original source holder pose is carried together with gun','attached_target':s,'release_or_reposition_intervals_seconds':[]} for s in ('left','right')},'notes':'Do not replace explicit identity/additive recovery channels with empty bones.'}
for name in ('idle','jump','land','aim','aim_idle','static_aim','static_bolt_caught','static_over_heating','over_heat'):
    clips[name]={'source_clip':None,'source_mechanism':'Existing empty or identity additive overlay, no independent original hand motion.','sides':{s:{'attached_intervals':'inherit the active source primary/static pose and its release state','attached_target':s,'release_or_reposition_intervals_seconds':'inherit; do not reset hand correction to zero'} for s in ('left','right')},'notes':'These neutral channels must remain compatible with base/overlay blending and native recovery semantics.'}
report={'status':'SOURCE_ANM_PHASE_CONSTRAINTS_NOT_RUNTIME_PASS','coordinate_contract':'Semantic surface points are native rootless stand pose; bind to named source body bone and transform with its actual per-frame matrix. Do not use them as fixed camera/world points. Existing native model adds +1.5Y before view; use consistent space in QA.','phase_method':{'source':'source_grip_motion.json, original raw ANM sampled at30Hz','metric':'norm of original wrist/finger-midpoint contact relative to animated WeaponAnchor3 versus source stand','release_threshold_blocks':.04,'strong_release_threshold_blocks':.08,'threshold_crossings':'piecewise linear approximation between30Hz samples','boundary':'Positional phase guides are not exact events or proof that source humanoid/MC cuboid volumes are collision-free. Preserve original hand orientation and timing; inspect phase boundaries with denser samples.'},'semantic_targets':targets,'animation_constraints':clips,'unchanged_channels_required':['all original gun-body bone tracks','clip durations/loop flags','sound_effects and events','Lua/gameplay tracks','mesh/UV/textures'],'safety_of_data':'No pack or production output written. Full arm and sleeve finite-volume checks required for default and slim; attached contact gap must be measured from actual palm/sleeve surface, not its center.','input_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [W/'evidence/source_grip_motion.json',W/'evidence/grip_surface_measurements.json',W/'evidence/source_body_native.json']}}
out=W/'evidence/grip_phase_constraints.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
print(json.dumps({n:{s:{k:v for k,v in c['sides'][s].items() if k in ('attached_intervals_seconds','release_or_reposition_intervals_seconds')} for s in ('left','right')} for n,c in clips.items() if n in ('draw','shoot','reload_empty','inspect')},indent=2))
