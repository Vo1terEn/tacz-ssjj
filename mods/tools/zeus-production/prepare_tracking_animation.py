"""Retarget the supplied summon using actual native distal caps, not hand-root deltas."""
from pathlib import Path
import importlib.util
import numpy as np
from scipy.spatial.transform import Rotation
from prepare import W,ROOT,read,save,sha
def main():
    importer=ROOT/'work/zeus_hand2_20261113/scripts/source_import.py'
    spec=importlib.util.spec_from_file_location('zeus_advent_source_import',importer);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    scene=m.Scene();gun=m.Rig(scene,'gun');hand=m.Rig(scene,'hand_man')
    actions=next((m.RIG/'Animations').glob('hand.DAE.action__*.actions.json'))
    source_action=next(a for a in read(actions)['actions'] if a['name']=='TORSO_SKILL')
    assert [int(source_action['start']),int(source_action['end'])]==[204,259]
    origin=gun.globals('TORSO_STAND',0,hand)['WeaponAnchor3'][:3,3]*.01
    fit=ROOT/'work/zeus_hand2_20261113/evidence/source_import.json';transverse=read(fit)['native_arm_fit']['shared_transverse_scale']
    fixture=ROOT/'work/parasite_video_match_20261104/evidence/native_hand_scale/neutral_player_arm_pose.json';caps=read(fixture)['variants']['default']
    mesh=read(W/'baseline_assets/assets/ssjj_zeus/source_mesh.json');body=sorted({p['bone'] for p in mesh['parts']})
    def native(matrix):
        out=m.rigid(matrix);out[:3,:3]=m.Q@out[:3,:3];out[:3,3]=m.Q@(matrix[:3,3]*.01-origin);return out
    def target(clip,t):
        g=gun.globals(clip,t,hand);h=hand.globals(clip,t,gun);mat={b:native(g[b]) for b in body};sc={b:np.ones(3) for b in body}
        mat['muzzle_flash']=native(g['weapon_particle_fire_effect']);sc['muzzle_flash']=np.ones(3)
        for side,label in [('L','left'),('R','right')]:
            stem=f'Bip01_{side}_';elbow=h[stem+'Forearm'][:3,3]*.01;wrist=h[stem+'Hand'][:3,3]*.01
            fingers=np.mean([h[stem+f'Finger{i}'][:3,3] for i in range(1,5)],axis=0)*.01;contact=(wrist+fingers)*.5
            if clip=='TORSO_SKILL':
                # A vanilla arm cuboid includes its hand. Its distal cap is
                # the end of that hand, not the source palm centre. The supplied
                # distal finger joints provide the original open-hand extent.
                contact=np.mean([h[stem+f'Finger{i}2'][:3,3] for i in range(1,5)],axis=0)*.01
            rotation=m.frame(contact-elbow,h[stem+'Hand']);scale=np.array([transverse,np.linalg.norm(contact-elbow)/.75,transverse])
            matrix=np.eye(4);matrix[:3,:3]=m.Q@rotation;matrix[:3,3]=m.Q@(contact-rotation@(scale*np.asarray(caps[f'after_setup_anim_{label}_cap']))-origin)
            mat[label+'hand']=matrix;sc[label+'hand']=scale
        return mat,sc
    rel='assets/ssjj_zeus/animations/hand_of_zeuss2.animation.json';anim=read(W/'baseline_assets'/rel)
    georel='assets/ssjj_zeus/geo_models/gun/hand_of_zeuss2_geo.json';geo=read(W/'baseline_assets'/georel)
    geometry={b['name']:b for b in geo['minecraft:geometry'][0]['bones']};static=anim['animations']['static_idle']['bones'];source_base,scale_base=target('TORSO_STAND',0)
    duration=gun.clips['TORSO_SKILL']['duration'];assert abs(duration-55/30)<1e-5 and abs(hand.clips['TORSO_SKILL']['duration']-duration)<1e-6
    times=sorted({float(np.float32(t)) for t in np.r_[0,duration,np.arange(0,duration,1/60)]});times=[t for i,t in enumerate(times) if not i or t-times[i-1]>1e-6]
    values=[target('TORSO_SKILL',t) for t in times];bones={};references=[];expected={str(t):{} for t in times}
    def initial(value):return np.asarray(next(iter(value.values())) if isinstance(value,dict) else value,float)
    anchor='WeaponAnchor3';anchor_static=static[anchor]
    anchor_geo=Rotation.from_euler('xyz',geometry[anchor].get('rotation',[0,0,0]),degrees=True).as_matrix()
    anchor_rest=anchor_geo@Rotation.from_euler('xyz',initial(anchor_static['rotation']),degrees=True).as_matrix()
    rig_align=anchor_rest@source_base[anchor][:3,:3].T
    def smooth(t):t=np.clip(t,0,1);return t*t*(3-2*t)
    weights=np.asarray([smooth(t/.12)*smooth((duration-t)/.18) for t in times])
    old_clip=anim['animations']['zeus_advent']
    for bone in source_base:
        current=static[bone];p0=initial(current['position'])/[16,-16,16];s0=initial(current['scale'])
        gr=Rotation.from_euler('xyz',geometry[bone].get('rotation',[0,0,0]),degrees=True).as_matrix()
        r0=gr@Rotation.from_euler('xyz',initial(current['rotation']),degrees=True).as_matrix()
        world_rotations=[rig_align@v[0][bone][:3,:3]@source_base[bone][:3,:3].T@rig_align.T for v in values]
        rotations=[gr.T@Rotation.from_rotvec(w*Rotation.from_matrix(delta).as_rotvec()).as_matrix()@r0 for w,delta in zip(weights,world_rotations)];rs=m.eulers(rotations)
        scales=np.asarray([s0*(1+w*(v[1][bone]/scale_base[bone]-1)) for w,v in zip(weights,values)])
        old_ps=p0+weights[:,None]*np.asarray([rig_align@(v[0][bone][:3,3]-source_base[bone][:3,3]) for v in values])
        source_contacts=[];desired_contacts=[]
        if bone in ('lefthand','righthand'):
            label=bone[:-4];cap=np.asarray(caps[f'after_setup_anim_{label}_cap'])
            # Static-idle grip fitting is specific to closed hands holding the
            # gun. Its corrective rotation/root offset must not be carried into
            # the supplied open-hand skill. Use the original elbow->distal-hand frame
            # and original elbow-to-distal-hand length during the skill; only the boundary
            # envelope mixes to the existing calibrated native idle.
            source_rotations=[rig_align@v[0][bone][:3,:3] for v in values]
            rotations=[gr.T@Rotation.from_rotvec(w*Rotation.from_matrix(rot@r0.T).as_rotvec()).as_matrix()@r0 for w,rot in zip(weights,source_rotations)]
            rs=m.eulers(rotations)
            scales=np.asarray([s0+w*(v[1][bone]-s0) for w,v in zip(weights,values)])
            unweighted=[]
            for v,rotation in zip(values,source_rotations):
                source_contact=rig_align@(v[0][bone][:3,3]+v[0][bone][:3,:3]@(v[1][bone]*cap))
                desired_contact=source_contact
                unweighted.append(desired_contact-rotation@(v[1][bone]*cap))
                source_contacts.append(source_contact.tolist());desired_contacts.append(desired_contact.tolist())
            ps=p0+weights[:,None]*(np.asarray(unweighted)-p0)
        else:
            ps=old_ps
        for i,t in enumerate(times):
            full=np.eye(4);full[:3,:3]=(gr@rotations[i])@np.diag(scales[i]);full[:3,3]=ps[i]+[0,1.5,0]
            row={'native_full_matrix':full.tolist(),'envelope_weight':float(weights[i])}
            if source_contacts:
                row.update({'source_contact_native':source_contacts[i],'retargeted_contact_native':desired_contacts[i],
                  'native_cap_local':cap.tolist(),'old_hand_root_position_native':old_ps[i].tolist()})
            expected[str(t)][bone]=row
        assert np.isfinite(ps).all() and np.isfinite(rs).all() and np.isfinite(scales).all()
        bones[bone]={'position':m.keyed(times,ps*[16,-16,16]),'rotation':m.keyed(times,rs),'scale':m.keyed(times,scales)}
        references.append({'bone':bone,'samples':len(times),'maximum_position_motion_blocks':float(np.max(np.linalg.norm(ps-p0,axis=1))),
            'existing_stand_position':p0.tolist(),'source_stand_position':source_base[bone][:3,3].tolist(),'shared_rig_rotation':rig_align.tolist(),
            'start_position_error':float(np.linalg.norm(ps[0]-p0)),'end_position_error':float(np.linalg.norm(ps[-1]-p0)),
            'start_rotation_error':float(np.max(np.abs(rotations[0]-gr.T@r0))),'end_rotation_error':float(np.max(np.abs(rotations[-1]-gr.T@r0))),
            'hand_root_correction_max_blocks':float(np.max(np.linalg.norm(ps-old_ps,axis=1))), 'finite_native_cap_constraint':bone in ('lefthand','righthand')})
    clip={'loop':False,'animation_length':duration,'bones':bones};anim['animations']['zeus_advent']=clip;save(W/'pack'/rel,anim)
    lua_rel='assets/ssjj_zeus/scripts/hand_of_zeuss2_state_machine.lua';base=(W/'baseline_assets'/lua_rel).read_text('utf8')
    assert base.count('M.zeus_advent_state = {}')==1
    start=base.index('    if input == "zeus_advent" then',base.index('M.zeus_advent_state = {}'))
    end=base.index('    return nil',start)
    base=base[:start]+'''    if input == "zeus_advent" then
        if context:isStopped(track) then
            context:runAnimation("zeus_advent", track, false, PLAY_ONCE_STOP, 0.12)
        end
    elseif input == INPUT_PUT_AWAY then
        context:stopAnimation(track)
    end
'''+base[end:]
    out=W/'pack'/lua_rel;out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(base,'utf8')
    save(W/'evidence/tracking_animation_reference.json',{'times':times,'duration':duration,'expected':expected,'calibrated_rig_rotation':rig_align.tolist(),'native_root_translation':[0,1.5,0]})
    inputs=[Path(__file__),importer,actions,fit,fixture,W/'baseline_assets'/rel,W/'baseline_assets'/georel,W/'baseline_assets'/lua_rel]
    source_inputs={str(ROOT/n).replace('\\','/'):h for n,h in m.INPUTS.items()}
    save(W/'evidence/tracking_animation_source.json',{'passed':True,'source_action':'TORSO_SKILL','source_frames':[204,259],'source_duration':duration,'raw_source_action':source_action,
        'animation_name':'zeus_advent','samples':len(times),'bones':references,'existing_clips_preserved':all(anim['animations'][k]==v for k,v in read(W/'baseline_assets'/rel)['animations'].items() if k!='zeus_advent'),
        'changed_from_installed_advent':clip!=old_clip,'entry_blend_seconds':.12,'exit_envelope_seconds':.18,
        'adaptation':'Original ANM world motion in one shared calibrated rig frame. Original source elbow-to-distal-finger orientations and lengths are used for the open-hand action. Solve native roots from exact finite native distal caps at the original source distal four-finger-joint centroid. Static-idle closed-grip correction does not leak into the open-hand middle. Only 0.12s entry and 0.18s exit envelopes blend to unchanged idle. No authored hand key poses, effects or audio.',
        'input_sha256':{**{(p.relative_to(W) if p.is_relative_to(W) else p).as_posix():sha(p) for p in inputs},**source_inputs},
        'output_sha256':{relpath:sha(W/relpath) for relpath in ['pack/'+rel,'pack/'+lua_rel,'evidence/tracking_animation_reference.json']},'live_client_test':False})
    save(W/'evidence/focus_animation_source.json',read(W/'evidence/tracking_animation_source.json'))
    print('PASS original summon animation:',len(times),'samples,',len(bones),'bones,',duration,'seconds')
if __name__=='__main__':main()
