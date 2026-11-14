"""Adapt the actual source TORSO_SKILL into the retained TaCZ arm calibration."""
from pathlib import Path
import importlib.util
import numpy as np
from scipy.spatial.transform import Rotation
from prepare import W,ROOT,read,save,sha
def main():
    importer=ROOT/'work/zeus_hand2_20261113/scripts/source_import.py'
    spec=importlib.util.spec_from_file_location('zeus_advent_source_import',importer);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    scene=m.Scene();gun=m.Rig(scene,'gun');hand=m.Rig(scene,'hand_man')
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
            rotation=m.frame(contact-elbow,h[stem+'Hand']);scale=np.array([transverse,np.linalg.norm(contact-elbow)/.75,transverse])
            matrix=np.eye(4);matrix[:3,:3]=m.Q@rotation;matrix[:3,3]=m.Q@(contact-rotation@(scale*np.asarray(caps[f'after_setup_anim_{label}_cap']))-origin)
            mat[label+'hand']=matrix;sc[label+'hand']=scale
        return mat,sc
    rel='assets/ssjj_zeus/animations/hand_of_zeuss2.animation.json';anim=read(W/'baseline_assets'/rel)
    georel='assets/ssjj_zeus/geo_models/gun/hand_of_zeuss2_geo.json';geo=read(W/'baseline_assets'/georel)
    geometry={b['name']:b for b in geo['minecraft:geometry'][0]['bones']};static=anim['animations']['static_idle']['bones'];source_base,scale_base=target('TORSO_STAND',0)
    duration=gun.clips['TORSO_SKILL']['duration'];assert abs(duration-55/30)<1e-5 and abs(hand.clips['TORSO_SKILL']['duration']-duration)<1e-6
    times=sorted({float(np.float32(t)) for t in np.r_[0,duration,np.arange(0,duration,1/60)]});times=[t for i,t in enumerate(times) if not i or t-times[i-1]>1e-6]
    values=[target('TORSO_SKILL',t) for t in times];bones={};references=[]
    def initial(value):return np.asarray(next(iter(value.values())) if isinstance(value,dict) else value,float)
    for bone in source_base:
        current=static[bone];p0=initial(current['position'])/[16,-16,16];s0=initial(current['scale'])
        gr=Rotation.from_euler('xyz',geometry[bone].get('rotation',[0,0,0]),degrees=True).as_matrix()
        r0=gr@Rotation.from_euler('xyz',initial(current['rotation']),degrees=True).as_matrix();align=r0@source_base[bone][:3,:3].T
        ps=np.asarray([p0+align@(v[0][bone][:3,3]-source_base[bone][:3,3]) for v in values])
        rotations=[gr.T@align@v[0][bone][:3,:3] for v in values];rs=m.eulers(rotations)
        scales=np.asarray([s0*v[1][bone]/scale_base[bone] for v in values])
        assert np.isfinite(ps).all() and np.isfinite(rs).all() and np.isfinite(scales).all()
        bones[bone]={'position':m.keyed(times,ps*[16,-16,16]),'rotation':m.keyed(times,rs),'scale':m.keyed(times,scales)}
        references.append({'bone':bone,'samples':len(times),'maximum_position_motion_blocks':float(np.max(np.linalg.norm(ps-p0,axis=1))),
            'existing_stand_position':p0.tolist(),'source_stand_position':source_base[bone][:3,3].tolist(),'calibration_rotation':align.tolist()})
    clip={'loop':False,'animation_length':duration,'bones':bones};anim['animations']['zeus_advent']=clip;save(W/'pack'/rel,anim)
    lua_rel='assets/ssjj_zeus/scripts/hand_of_zeuss2_state_machine.lua';base=(W/'baseline_assets'/lua_rel).read_text('utf8')
    assert base.count('return M')==1
    extra='''-- Zeus Advent uses a supplied source clip on an existing native parallel
-- static track. The ordinary reload/shoot/locomotion state is never replaced.
M.zeus_advent_state = {}
function M.zeus_advent_state.transition(this, context, input)
    local track = context:getTrack(this.STATIC_TRACK_LINE, this.PARALLEL_TRACK_1)
    if input == "zeus_advent" then
        context:runAnimation("zeus_advent", track, false, PLAY_ONCE_STOP, 0.08)
    elseif input == INPUT_PUT_AWAY or input == INPUT_SHOOT or input == INPUT_RELOAD or input == INPUT_INSPECT then
        context:stopAnimation(track)
    end
    return nil
end
local zeus_original_states = M.states
function M:states()
    local states = zeus_original_states(self)
    table.insert(states, self.zeus_advent_state)
    return states
end

return M'''
    out=W/'pack'/lua_rel;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(base.replace('return M',extra),'utf8')
    inputs=[Path(__file__),importer,fit,fixture,W/'baseline_assets'/rel,W/'baseline_assets'/georel,W/'baseline_assets'/lua_rel]
    source_inputs={str(ROOT/n).replace('\\','/'):h for n,h in m.INPUTS.items()}
    save(W/'evidence/advent_animation_source.json',{'passed':True,'source_action':'TORSO_SKILL','source_frames':[204,259],'source_duration':duration,
        'animation_name':'zeus_advent','samples':len(times),'bones':references,'existing_clips_preserved':all(anim['animations'][k]==v for k,v in read(W/'baseline_assets'/rel)['animations'].items()),
        'adaptation':'Original ANM bone motion is retargeted from original stand to the current calibrated stand with a constant per-bone rigid alignment and existing arm-scale ratios. No new hand key poses are authored.',
        'input_sha256':{**{(p.relative_to(W) if p.is_relative_to(W) else p).as_posix():sha(p) for p in inputs},**source_inputs},
        'output_sha256':{relpath:sha(W/relpath) for relpath in ['pack/'+rel,'pack/'+lua_rel]},'live_client_test':False})
    print('PASS original summon animation:',len(times),'samples,',len(bones),'bones,',duration,'seconds')
if __name__=='__main__':main()
