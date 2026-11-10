"""Rebuild gun curves and measured vanilla cube arms from original ANM.

No converted animation/geometry from the previous package is an input.
Camera calibration is a separate explicit JSON input and can be applied later.
"""
from __future__ import annotations
import argparse,json,warnings
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation
from import_hand_source import WORK,PROJECT,INPUTS,save,sha
from source_animation import UnityRig
from fit_native_arm_scale import frame_from_direction

AS=WORK/'pack/assets/ssjjparasite2'
BODY=['Dummy001','Dummy002','Dummy02','WeaponAnchor2']
FREQUENCY=120
DEFAULT_Q=np.array([[0.,0,1],[0,1,0],[-1,0,0]])
FIXTURE=WORK/'evidence/native_hand_scale/neutral_player_arm_pose.json'
FIT=WORK/'evidence/native_arm_fit.json'
TARGETS=WORK/'generated/native_arm_fit_targets.json'

def distinct_native_times(values):
    result=[]
    for t in sorted({float(np.float32(t))for t in values}):
        if not result or t-result[-1]>1e-6:result.append(t)
    return np.array(result)

def continuous_euler(matrices):
    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        raw=Rotation.from_matrix(np.asarray(matrices)[:,:3,:3]).as_euler('xyz',degrees=True)
    output=[raw[0]]
    for a in raw[1:]:
        candidates=[]
        for b in (a,np.array([a[0]+180,180-a[1],a[2]+180])):
            candidates.append(b+360*np.round((output[-1]-b)/360))
        output.append(min(candidates,key=lambda b:np.linalg.norm(b-output[-1])))
    return np.array(output)

def keyed(times,values):
    return {format(t,'.9g'):np.round(v,9).tolist()for t,v in zip(times,values)}

def tracks_hash(obj):
    import hashlib
    tracks={k:{p:v for p,v in a.items()if p!='sound_effects'}for k,a in obj['animations'].items()}
    return hashlib.sha256(json.dumps(tracks,sort_keys=True,separators=(',',':')).encode()).hexdigest()

class Conversion:
    def __init__(self,camera_config=None):
        self.gun=UnityRig();self.hand=UnityRig('hand_man')
        self.fit=json.loads(FIT.read_text('utf8'));assert self.fit['status']=='PASS_OFFLINE_NATIVE_ARM_FIT'
        self.fit_targets=json.loads(TARGETS.read_text('utf8'))
        assert sha(TARGETS)==self.fit['outputs']['targets_sha256']
        self.transverse=self.fit['fit']['shared_square_transverse_scale'];assert self.transverse==.41
        self.fixture=json.loads(FIXTURE.read_text('utf8'))
        assert self.fixture['status']=='PASS_ACTUAL_NEUTRAL_PLAYER_ARM_POSE'
        self.config=camera_config or {}
        self.Q=np.array(self.config.get('source_to_native_rotation',DEFAULT_Q))
        assert np.max(abs(self.Q.T@self.Q-np.eye(3)))<1e-7 and abs(np.linalg.det(self.Q)-1)<1e-7
        self.origin=np.array(self.config.get('source_origin_blocks',self.gun.globals('TORSO_STAND',0)['WeaponAnchor2'][:3,3]*.01))
        self.t=-self.Q@self.origin
        self.max_rigid_projection=0.
        self.max_contact=0.;self.max_elbow=0.
    def native(self,m):
        out=np.eye(4);out[:3,3]=self.Q@(m[:3,3]*.01)+self.t
        raw=self.Q@m[:3,:3];u,_,v=np.linalg.svd(raw);out[:3,:3]=u@v
        self.max_rigid_projection=max(self.max_rigid_projection,float(abs(raw-out[:3,:3]).max()))
        return out
    def source_point(self,p):return self.Q@np.asarray(p)+self.t
    def targets(self,clip,time):
        g=self.gun.globals(clip,time);h=self.hand.globals(clip,time)
        result={bone:self.native(g[bone])for bone in BODY}
        scales={bone:np.ones(3)for bone in BODY}
        socket='weapon_left_particle_fire_effect'
        result['muzzle_flash']=self.native(g[socket]if socket in g else h[socket])
        result['muzzle_flash'][:3,:3]=result['WeaponAnchor2'][:3,:3]@Rotation.from_euler('y',-90,degrees=True).as_matrix()
        scales['muzzle_flash']=np.ones(3)
        landmarks={}
        for side,label in (('L','left'),('R','right')):
            stem=f'Bip01_{side}_'
            elbow=h[stem+'Forearm'][:3,3]*.01;wrist=h[stem+'Hand'][:3,3]*.01
            finger_base=np.mean([h[stem+f'Finger{i}'][:3,3]for i in range(1,5)],0)*.01
            contact=.5*(wrist+finger_base)
            r_source=frame_from_direction(contact-elbow,h[stem+'Hand'])
            scale=np.array([self.transverse,np.linalg.norm(contact-elbow)/.75,self.transverse])
            cap=np.array(self.fixture['variants']['default'][f'after_setup_anim_{label}_cap'])
            translation=contact-r_source@np.diag(scale)@cap
            m=np.eye(4);m[:3,:3]=self.Q@r_source;m[:3,3]=self.source_point(translation)
            result[label+'hand']=m;scales[label+'hand']=scale
            C=self.source_point(contact);E=self.source_point(elbow)
            self.max_contact=max(self.max_contact,float(np.linalg.norm(m[:3,3]+m[:3,:3]@(scale*cap)-C)))
            self.max_elbow=max(self.max_elbow,float(np.linalg.norm(m[:3,3]+m[:3,:3]@(scale*(cap+[0,.75,0]))-E)))
            landmarks[label]={'contact':C.tolist(),'elbow':E.tolist(),'wrist':self.source_point(wrist).tolist()}
        return result,scales,landmarks
    def verify_fitted_targets(self):
        maximum=0.
        for sample in self.fit_targets['samples']:
            targets,scales,_=self.targets(sample['clip'],sample['time_seconds'])
            for side,label in (('L','left'),('R','right')):
                expected=sample['arms'][side];actual=targets[label+'hand']
                maximum=max(maximum,float(abs(actual[:3,:3]-self.Q@expected['rotation_columns']).max()),
                    float(abs(actual[:3,3]-self.source_point(expected['translation'])).max()),
                    float(abs(scales[label+'hand']-expected['scale']).max()))
        # The independent hand importer resets animated local scale to 1;
        # this producer retains original Transform scale (float32 values can
        # differ from 1 by ~1e-7). Keep that measured boundary visible.
        assert maximum<1e-6,maximum
        return maximum

def positioning_nodes(c):
    config=c.config
    raw_camera=c.gun.globals('TORSO_STAND',0)['Camera01'][:3,3]*.01
    idle_source=np.array(config.get('idle_eye_source_blocks',config.get('eye_source_blocks',raw_camera)))
    idle=c.source_point(idle_source)
    iron_source=config.get('iron_eye_source_blocks',idle_source)
    iron=c.source_point(iron_source)
    # Native positioning top-level inverse adds its own +1.5 model origin.
    # These pivots contain eye only, never an additional 24px Y offset.
    nodes=[]
    for name,eye in (('idle_view',idle),('iron_view',iron),('scope_pos',iron)):
        nodes.append({'name':name,'pivot':(eye*[16,-16,16]).tolist(),'rotation':config.get(name+'_rotation_degrees',[0,0,180])})
    refit=np.array(config.get('refit_eye_native_blocks',[-1.6,.05,-.23]))
    nodes.append({'name':'refit_view','pivot':(refit*[16,-16,16]).tolist(),'rotation':config.get('refit_rotation_degrees',[0,85,180])})
    return nodes,{'status':'CALIBRATION_CONFIG_APPLIED'if config else'CAMERA_CALIBRATION_PENDING',
        'source_to_native_rotation':c.Q.tolist(),'source_origin_blocks':c.origin.tolist(),
        'idle_eye_source_blocks':idle_source.tolist(),'idle_eye_native_blocks':idle.tolist(),
        'iron_eye_source_blocks':np.asarray(iron_source).tolist(),'iron_eye_native_blocks':iron.tolist(),
        'refit_eye_native_blocks':refit.tolist(),
        'pivot_rule':'Top-level eye_blocks*[16,-16,16], no extra 24px Y. Actual native positioning inverse and all four MathUtil blend calls require independent QA.',
        'camera_boundary':'No ordinary inverse preview is claimed to verify the actual native full positioning chain; final camera config and video action phase are independent QA.'}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--camera-config',type=Path)
    args=parser.parse_args();config=json.loads(args.camera_config.read_text('utf8'))if args.camera_config else None
    c=Conversion(config);fit_error=c.verify_fitted_targets();shared=c.gun.verify_shared_tracks()
    source_curve_coverage=[]
    for source in c.gun.animations['gun']:
        original_times=np.array(c.gun.times(source))
        all_times=np.unique(np.r_[original_times,.5*(original_times[:-1]+original_times[1:])])
        anchor_error=max(float(abs(c.gun.globals(source,float(t))['WeaponAnchor2']-c.hand.globals(source,float(t))['WeaponAnchor2']).max())for t in all_times)
        assert anchor_error<1e-8,(source,anchor_error)
        source_curve_coverage.append({'clip':source,'original_keys_and_midpoints':len(all_times),
            'gun_vs_complete_character_anchor_global_error_source_cm':anchor_error,
            'original_track_key_counts':{kind:{track['bone']:len(track['keyframes'])for track in c.gun.animations[kind][source]['tracks']}for kind in ('gun','hand_man')}})
    baseline,base_scale,_=c.targets('TORSO_STAND',0)
    base_euler={k:continuous_euler([m])[0]for k,m in baseline.items()}
    animations={};references=[];cache={}
    clips={'static_idle':('TORSO_STAND',1.),'draw':('TORSO_RAISE',None),'shoot':('TORSO_ATTACK',None),
        'reload_empty':('TORSO_RELOAD',None),'reload_tactical':('TORSO_RELOAD',None),
        'inspect':('TORSO_SHOWGUN',None),'inspect_empty':('TORSO_SHOWGUN',None)}
    time_axes={}
    for name,(source,custom_duration)in clips.items():
        duration=custom_duration or c.gun.animations['gun'][source]['duration'];cache_key=(source,duration)
        keys=np.array(c.gun.times(source))
        if cache_key not in cache:
            times=distinct_native_times(np.r_[0,duration,keys[keys<=duration],np.arange(0,duration,1/FREQUENCY)])
            values=[c.targets(source,float(t))for t in times]
            relative={bone:continuous_euler([np.linalg.inv(baseline[bone])@value[0][bone]for value in values])for bone in baseline}
            cache[cache_key]=(times,values,relative)
        times,values,relative=cache[cache_key];bones={}
        for bone in baseline:
            p=np.array([value[0][bone][:3,3]for value in values]);scale=np.array([value[1][bone]for value in values]);e=relative[bone]
            if name=='shoot':p-=baseline[bone][:3,3];scale/=base_scale[bone]
            p=p*[16,-16,16]
            if name=='static_idle':bones[bone]={'position':p[0].tolist(),'rotation':e[0].tolist(),'scale':scale[0].tolist()}
            else:bones[bone]={'position':keyed(times,p),'rotation':keyed(times,e),'scale':keyed(times,scale)}
        animations[name]={'loop':name=='static_idle','animation_length':duration,'bones':bones}
        checks=np.unique(np.r_[0,duration,keys[keys<=duration],.5*(keys[:-1]+keys[1:]),np.arange(.5/FREQUENCY,duration,1/FREQUENCY)])
        for time in checks:
            if time>duration:continue
            matrices,scales,landmarks=c.targets(source,float(time))
            references.append({'animation':name,'source':source,'time':float(time),
                'matrices':{k:v.tolist()for k,v in matrices.items()},'scales':{k:v.tolist()for k,v in scales.items()},'landmarks':landmarks})
    for name in ('idle','walk','run','jump','land','aim','aim_idle','aim_walk','aim_run','static_aim','static_bolt_caught','static_over_heating','over_heat'):
        animations[name]={'loop':True,'animation_length':1,'bones':{}}
    put={}
    for bone,m in baseline.items():
        p=m[:3,3]*[16,-16,16]
        put[bone]={'position':{'0':p.tolist(),'.25':(p+[0,12,0]).tolist()},'rotation':[0,0,0],'scale':base_scale[bone].tolist()}
    animations['put_away']={'loop':False,'animation_length':.25,'bones':put}
    for time in np.linspace(0,.25,31):
        matrices={bone:m.copy()for bone,m in baseline.items()}
        for m in matrices.values():m[1,3]-=float(time)*3
        references.append({'animation':'put_away','source':'ADAPTED_PUT_AWAY','time':float(time),
            'matrices':{k:v.tolist()for k,v in matrices.items()},'scales':{k:v.tolist()for k,v in base_scale.items()}})
    for name,animation in animations.items():
        maximum=0;step=None
        for bone in animation['bones'].values():
            for channel in ('position','rotation','scale'):
                if isinstance(bone.get(channel),dict):
                    times=np.array([float(np.float32(t))for t in bone[channel]])
                    assert np.all(np.diff(times)>0),(name,channel,'float32 duplicate time')
                    maximum=max(maximum,len(times))
                    if len(times)>1:step=min(step or 1.,float(np.diff(times).min()))
        time_axes[name]={'maximum_keys_per_channel':maximum,'minimum_float32_step':step}
    document={'format_version':'1.8.0','animations':animations}
    animation_path=AS/'animations/parasitic_sniper2.animation.json';save(animation_path,document)
    bones=[{'name':'root','pivot':[0,0,0]}]
    bones.extend({'name':bone,'parent':'root','pivot':[0,0,0],'rotation':base_euler[bone].tolist()}for bone in baseline)
    bones.extend({'name':side+'hand_pos','parent':side+'hand','pivot':[0,0,0]}for side in ('left','right'))
    nodes,positioning=positioning_nodes(c);bones.extend(nodes)
    bones.append({'name':'camera','pivot':[0,24,0]})
    grip=c.native(c.hand.globals('TORSO_STAND',0)['Bip01_R_Hand'])[:3,3]
    for name,point in (('thirdperson_hand',grip),('ground',[0,0,0]),('fixed',[0,0,0])):
        bones.append({'name':name,'parent':'root','pivot':(np.array(point)*[16,-16,16]).tolist()})
    geometry={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.parasitic_sniper2',
        'texture_width':1024,'texture_height':1024,'visible_bounds_width':8,'visible_bounds_height':8,'visible_bounds_offset':[0,0,0]},'bones':bones}]}
    geometry_path=AS/'geo_models/gun/parasitic_sniper2_geo.json';save(geometry_path,geometry)
    reference_path=WORK/'evidence/native_reference_samples.json'
    save(reference_path,{'samples':references,'body_bones':BODY,'baseline':{k:v.tolist()for k,v in baseline.items()},
        'baseline_scales':{k:v.tolist()for k,v in base_scale.items()},'camera':positioning,
        'slim_callback_translation':c.fit['fit']['slim_wrapper_before_original_native_callback']})
    local_inputs={path.relative_to(PROJECT).as_posix():sha(path)for path in (FIT,FIXTURE,TARGETS,Path(__file__),WORK/'scripts/source_animation.py',WORK/'scripts/fit_native_arm_scale.py',WORK/'scripts/import_hand_source.py')}
    if args.camera_config:local_inputs[args.camera_config.resolve().relative_to(PROJECT).as_posix()]=sha(args.camera_config)
    report={'status':'BUILT_FRESH_RAW_ANM_SCALED_NATIVE_ARMS','production_skin':'Unchanged native player skin/sleeve callbacks; original hand/finger geometry excluded.',
        'input_sha256':{**INPUTS,**local_inputs},'raw_binary_verification':c.gun.binary_verification,
        'shared_track_verification_all_original_clips':shared,
        'raw_source_curve_coverage_all_original_clips':source_curve_coverage,
        'coordinate_contract':{'source':'Original ANM TRS in glTF-compatible frame; raw Unity Transform defaults J@M@J.',
            'source_to_native_rotation':c.Q.tolist(),'source_origin_blocks':c.origin.tolist(),'unit':'Original centimetres times .01 blocks.',
            'init_rotation':'Geo init R=stand rotation; each animation Radditional=Rstand^-1 * Rtarget.',
            'additive':'Static idle additional rotation is identity. Shoot position=target-idle and scale=target/idle; idle and shoot compose to the absolute source target.',
            'geometry_nodes':'Four gun mesh joints, two scaled native cube-arm joints and one native muzzle flash socket; no source hand mesh/finger bones in pack.'},
        'hands':{'fit_report_sha256':sha(FIT),'full_591_pose_fit_matrix_error':fit_error,
            'transverse_scale':c.transverse,'length':'Dynamic Sy=distance(source palm contact,source elbow)/.75',
            'maximum_distal_contact_error_blocks':c.max_contact,'maximum_proximal_elbow_error_blocks':c.max_elbow,
            'actual_setup_anim_cap_fixture_sha256':sha(FIXTURE),
            'slim_local_callback_translation':c.fit['fit']['slim_wrapper_before_original_native_callback'],
            'shoot_scale':'X/Z exactly1; Y=dynamic length target/idle. All absolute primary actions include scale.'},
        'sampling':{'bake_hz':FREQUENCY,'independent_source_reference_frames':len(references),
            'max_discarded_scale_or_shear_component':c.max_rigid_projection,
            'time_rule':'Canonical float32 times deduplicated within1e-6;9 significant digit roundtrip; every position/rotation/scale axis strictly ascending.',
            'native_time_axes':time_axes},'positioning':positioning,
        'adaptations':['put_away is an authored .25s lowering because the original ANM has no put-away clip.',
            'Vanilla cube skin replaces source sleeve/hand/finger geometry by explicit user choice.',
            'Camera is a separate calibration input; pending defaults are not final video positioning evidence.'],
        'validation_boundary':'Producer raw-source/contact checks only. Native parser/interpolation, actual camera transform chain, video frame phase and visible silhouettes require independent QA.',
        'outputs':{'animation':animation_path.relative_to(WORK).as_posix(),'animation_sha256':sha(animation_path),
            'animation_tracks_sha256':tracks_hash(document),'geo':geometry_path.relative_to(WORK).as_posix(),'geo_sha256':sha(geometry_path),
            'reference_samples':reference_path.relative_to(WORK).as_posix(),'reference_samples_sha256':sha(reference_path)}}
    save(WORK/'evidence/animation_build.json',report)
    print(json.dumps({k:report[k]for k in ('status','hands','sampling','outputs')},ensure_ascii=False))

if __name__=='__main__':main()
