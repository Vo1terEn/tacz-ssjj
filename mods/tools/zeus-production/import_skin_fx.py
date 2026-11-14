"""Restore original Zeus lava/galaxy cosmetic gun-body effects only.

Source transforms/materials/curves are converted to the existing shared
18-channel particle LUT format. No artwork or other weapon assets are used.
"""
from pathlib import Path
import hashlib, importlib.util, json, math
from types import SimpleNamespace
import UnityPy
import numpy as np
from scipy.spatial.transform import Rotation
from UnityPy.helpers.MeshHelper import MeshHandler

W=Path(__file__).resolve().parents[1];P=W.parents[1];AS=W/'pack/assets/ssjj_zeus'
sp=importlib.util.spec_from_file_location('zeus_source',P/'work/zeus_hand2_20261113/scripts/source_import.py')
s=importlib.util.module_from_spec(sp);sp.loader.exec_module(s)
dp=importlib.util.spec_from_file_location('source_curve_mechanism',P/'work/dimension_star_20261107/scripts/build_particles.py')
# Reuse source-parameter math only; its Dimension main/import/output is never run.
import sys
sys.path.insert(0,str(P/'work/dimension_star_20261107/scripts'))
d=importlib.util.module_from_spec(dp);dp.loader.exec_module(d)
SEED=0x20261114;SAMPLES=257
SRC=P/"宙斯、聚能/宙斯之手";INPUTS={}
def read(p):
    p=Path(p);b=p.read_bytes();INPUTS[p.relative_to(P).as_posix()]=hashlib.sha256(b).hexdigest();return b
def load(p):return json.loads(read(p).decode("utf-8-sig"))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def xyz(v):return np.array([v[k]for k in'xyz'],float)
def rgba(v):return np.array([v[k]for k in'rgba'],float)

def import_one(skin,key):
    gun="hand_of_zeuss"
    base=SRC/'完整源素材/武器皮肤'/f'{skin}__{skin}';rig=base/skin;meshdir=base/(skin+'$2')
    doc=load(rig/'assembly.json');ids={o['path_id']:o for o in doc['objects']}
    ts={i:o['fields']for i,o in ids.items()if o['type']=='Transform'};goT={v['m_GameObject']['m_PathID']:i for i,v in ts.items()}
    meshdoc=load(meshdir/'assembly.json');bundle=next((SRC/'原始资源包/weaponskin').glob(skin+'$2_*'));envraw=UnityPy.load(read(bundle));prefabs=UnityPy.load(read(next((SRC/'原始资源包/weaponskin').glob(skin+'_*'))));body_go=next(o.path_id for path,o in prefabs.container.items()if path.endswith('/'+gun+'/effect/qiangshentexiao/prefab/qiangshentexiao.prefab'))
    scene=SimpleNamespace(ids={o['path_id']:o for o in meshdoc['objects']},mesh_objects={o.path_id:o for o in envraw.objects if o.type.name=='Mesh'})
    custom={int(p.stem):load(p)for p in (SRC/f'特效及材质参数/武器皮肤/{skin}/MonoBehaviour').glob('*.json')}
    root_id,root_component=next((i,v)for i,v in custom.items()if 'ssjjpaList'in v and v['m_GameObject']['m_PathID']==body_go)
    def anchored(v):
        if 'instance'not in v or v['instance']['m_PathID']!=root_component['m_GameObject']['m_PathID']:return False
        t=ts[goT[v['m_GameObject']['m_PathID']]];parent=ts[t['m_Father']['m_PathID']]
        return ids[parent['m_GameObject']['m_PathID']]['name']=='WeaponAnchor3'
    choices=[(i,v)for i,v in custom.items()if anchored(v)];assert len(choices)==1,choices
    config_id,config=choices[0];anchor=ts[goT[config['m_GameObject']['m_PathID']]]
    assert config['instance']['m_PathID']==root_component['m_GameObject']['m_PathID']
    e=config['LocalEulerAngles'];rotation=Rotation.from_euler('zxy',[e['z'],e['x'],e['y']],degrees=True)
    configured=s.trs(config['LocalPosition'],rotation.as_quat(),config['LocalScala'])
    anchor_m=s.trs(anchor['m_LocalPosition'],anchor['m_LocalRotation'],anchor['m_LocalScale'])
    emitter_ids=[v['m_PathID']for v in root_component['ssjjpaList']];assert len(emitter_ids)==(12 if key=='lava'else 9)
    traces=[];emitters=[];texture_map={};gradient_errors=[]
    for oid in emitter_ids:
        v=custom[oid];go=ids[v['m_GameObject']['m_PathID']];name=go['name'];t=ts[goT[go['path_id']]]
        assert t['m_Father']['m_PathID']==goT[root_component['m_GameObject']['m_PathID']]
        assert v['loop']and v['m_Enabled']and v['isShow']and go['fields']['m_IsActive']and not v['isWorldParticle']
        mat=scene.ids[v['mater']['m_PathID']];props=mat['fields']['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];floats=dict(props['m_Floats'])
        assert floats['_BlendDst']in[1,10] and floats['_ZTest']==4 and floats['_ZWrite']==0
        assert (v['billboardType']==0 and floats['_Billboard']==1)or(v['billboardType']==4 and floats['_Billboard']==0)
        tint=2*rgba(dict(props['m_Colors'])['_TintColor'])*rgba(dict(props['m_Colors'])['_DyeingColor'])
        matches=list(rig.rglob('*__'+str(env['m_Texture']['m_PathID'])+'.png'));assert len(matches)==1,(name,matches)
        texture=matches[0];b=read(texture);h=hashlib.sha256(b).hexdigest();out=AS/f'textures/effects/{h[:20]}.png';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(b)
        texture_map[out.relative_to(W).as_posix()]={'source':texture.relative_to(P).as_posix(),'sha256':h,'conversion':'unchanged original exported PNG bytes'}
        m=s.J@anchor_m@configured@s.trs(t['m_LocalPosition'],t['m_LocalRotation'],t['m_LocalScale']);proper=m@s.J;linear=proper[:3,:3];scale=np.linalg.norm(linear,axis=0);rot=linear/scale
        assert np.max(abs(rot.T@rot-np.eye(3)))<1e-6 and np.linalg.det(rot)>.999999
        mode='mesh'if v['billboardType']==4 else'billboard';geometry=None
        if mode=='mesh':
            mid=v['mesh']['m_PathID']
            if mid==10210:
                # Unity source built-in Quad (external primitive, no embedded Mesh).
                # Standard authored 1x1 XY primitive, not a borrowed weapon visual.
                source_p=np.array([[-.5,-.5,0],[.5,-.5,0],[-.5,.5,0],[.5,.5,0]])
                source_uv=np.array([[0,0],[1,0],[0,1],[1,1]])
                source_tri=np.array([[0,2,1],[2,3,1]])
            else:
                obj=scene.mesh_objects[mid].read();mh=MeshHandler(obj);mh.process();source_p=np.asarray(mh.m_Vertices,float);source_uv=np.asarray(mh.m_UV0,float);source_tri=np.asarray(mh.get_triangles()[0],int)
            pp=source_p@s.J[:3,:3]*.01;uv=source_uv*[1,-1]+[0,1]
            geometry={'positions':pp.tolist(),'uv':uv.tolist(),'triangles':source_tri[:,[0,2,1]].tolist()}
        rng=np.random.default_rng(SEED^(oid&0xffffffff));particles=[];centers=[];maxlife=maxbirth=0
        for index in range(v['ParticleNumber']):
            fraction=index/max(v['ParticleNumber']-1,1);life=d.oned(v['lifeTime'],fraction,rng);birth=d.oned(v['startTime'],fraction,rng)
            assert 0<life<=60 and birth>=0;maxlife=max(maxlife,life);maxbirth=max(maxbirth,birth)
            position=np.zeros(3);direction=np.zeros(3);shape=v['shape']
            # RandomOnBorn=0 source mesh emitters are exactly at their authored
            # transform, despite stale cone cache fields in serialized shapes.
            if v['RandomOnBorn']:
                if shape['shapeType']==0:
                    lo=np.minimum(xyz(shape['_rectFrom']),xyz(shape['_rectTo']));hi=np.maximum(xyz(shape['_rectFrom']),xyz(shape['_rectTo']));position=rng.uniform(lo,hi);direction=position-(lo+hi)/2
                elif shape['shapeType']==1:
                    assert shape['_radiusSmall']==0
                    theta=rng.uniform(0,math.tau);radius=shape['_radius']*(1. if shape.get('_shell')else math.sqrt(rng.random()))
                    position=np.array([math.cos(theta)*radius,math.sin(theta)*radius,rng.uniform(0,shape['_length'])])
                    halfangle=math.radians(shape['_angle']);direction=np.array([math.cos(theta)*math.sin(halfangle),math.sin(theta)*math.sin(halfangle),math.cos(halfangle)])
                elif shape['shapeType']!=3:raise ValueError(('Unsupported source shape',shape))
            if not v['directionByShape']:direction=xyz(v['direction']['value'])
            direction=s.unit(direction);speed=d.oned(v['velocity'],fraction,rng);spin=d.oned(v['rotvelocity'],fraction,rng);angle=d.oned(v['rot'],fraction,rng)
            size=np.array([d.oned(v['size'+a],fraction,rng)for a in'XYZ']);size[:]=size[0]if v['uniformScale_']else size
            force=s.unit(xyz(v['_forceDir']))*v['_force']if v['hasForceEffector']else np.zeros(3);alpha=d.oned(v['alpha'],fraction,rng);frames=[]
            for phase in np.linspace(0,1,SAMPLES):
                age=phase*life;local=position+direction*speed*age+.5*force*age*age
                if v['hasGravityEffector']:local+=.5*xyz(v['gravityDir'])*v['gravity']*age*age
                center=(m@np.r_[local,1])[:3]*.01;current_size=size.copy()
                if v['hasSizeEffector']:
                    if v['uniformScale_']:current_size*=d.interp(phase,v['_keyFrameLifeTime'],v['_sizeX'])
                    else:current_size*=np.array([d.interp(phase,v['_keyFrameLifeTime'],v['_size'+a])for a in'XYZ'])
                current_size*=scale;current_angle=angle+spin*age
                if mode=='mesh':
                    axis=np.zeros(3);axis[int(v['RotateAxis'])]=math.radians(current_angle);q=(Rotation.from_matrix(rot)*Rotation.from_rotvec(s.J[:3,:3]@axis*-1)).as_quat()
                else:
                    current_size=np.array([current_size[2],current_size[1],1.])*.01;q=Rotation.from_euler('z',current_angle,degrees=True).as_quat()
                col=np.clip(d.source_curve_color(v,phase)*tint*np.array([1,1,1,alpha]),0,1)
                us,vs=env['m_Scale']['x'],env['m_Scale']['y'];uo,vo=env['m_Offset']['x'],env['m_Offset']['y']
                if v['hasSpriteSheetEffector']:
                    assert not v['randomFrame']and not v['lifeDuration'];rows=max(1,v['rows']);columns=max(1,v['columns']);frame=min(int(phase*rows*columns),rows*columns-1);us/=columns;vs/=rows;uo+=(frame%columns)/columns;vo-=(frame//columns)/rows
                if v['hasUVEffector']:uo+=d.interp(phase,v['_uvkeyFrameLifeTime'],v['_u'],0);vo-=d.interp(phase,v['_uvkeyFrameLifeTime'],v['_v'],0)
                frames.append(np.round(np.r_[center,current_size,q,col,us,vs,uo,vo],10).tolist());centers.append(center)
            particles.append({'birth':birth,'life':life,'frames':frames})
        duration=max(maxlife+maxbirth,.01)
        emitters.append({'name':name,'texture':f'ssjj_zeus:textures/effects/{h[:20]}.png','mode':mode,'render':'additive'if floats['_BlendDst']==1 else'alpha','duration':duration,'geometry':geometry,'particles':particles})
        samples=np.array([d.source_curve_color(v,t)for t in np.linspace(0,1,SAMPLES)]);err=0
        for phase in np.linspace(0,1,4097):err=max(err,float(np.max(abs(np.array([np.interp(phase,np.linspace(0,1,SAMPLES),samples[:,j])for j in range(4)])-d.source_curve_color(v,phase)))))
        gradient_errors.append({'name':name,'max_rgba_component_error':err})
        centers=np.array(centers);traces.append({'name':name,'component_id':oid,'source_transform_id':goT[go['path_id']],'source_definition':v,'source_material':mat,'source_mesh_id':v['mesh']['m_PathID'],'source_texture':texture.relative_to(P).as_posix(),'output_texture':out.relative_to(W).as_posix(),'source_transform':t,'output_local_transform':m.tolist(),'center_bounds_m':[centers.min(0).tolist(),centers.max(0).tolist()],'count':len(particles),'duration_adaptation':duration,'mode':mode})
    output=AS/f'effects/body_{gun}_{key}.json';save(output,{'schema':1,'gun':'ssjj_zeus:'+gun,'bone':'WeaponAnchor3','emitters':emitters})
    return {'gun':gun,'skin_key':key,'skin_source_id':skin,'source_config_id':config_id,'source_config':config,'root_component_id':root_id,'root_component':root_component,'emitters':len(emitters),'particles':sum(len(e['particles'])for e in emitters),'frame_samples_per_particle':SAMPLES,'seed':SEED,'traces':traces,'textures':texture_map,'output_sha256':{output.relative_to(W).as_posix():sha(output),**{k:v['sha256']for k,v in texture_map.items()}},'coordinate_contract':{'source_anchor_transform':anchor,'config_local_euler_ZXY_degrees':config['LocalEulerAngles'],'configured_prefab_matrix':configured.tolist(),'conversion':'Explicit EffectConfig ZXY replaces prefab authoring rotation, source anchor included, reflect X once and convert100 source units to1 block. Original custom topology/UV preserved.','native_bone':'WeaponAnchor3'},'curve_checks':gradient_errors,'classification':'Lava yumao uses original fire09_8 4x4 orange fire sprite, not a feather; glow_yumao is generic glow. Names do not identify image content.'if key=='lava'else'No yumao emitter; original galaxy body layers.'}

def main():
    reports=[import_one('skin_hand_of_zeuss_lava','lava'),import_one('skin_hand_of_zeuss_galaxy_king','galaxy')]
    outputs={}
    for report in reports:
        key=report['skin_key'];original=AS/f'effects/body_hand_of_zeuss_{key}.json';body=json.loads(original.read_text('utf8'))
        for gun in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']:
            body['gun']='ssjj_zeus:'+gun;out=AS/f'effects/body_{gun}_{key}.json';save(out,body);outputs[out.relative_to(W).as_posix()]=sha(out)
        outputs.update({rel:v['sha256']for rel,v in report['textures'].items()})
    read(Path(__file__));read(P/'work/dimension_star_20261107/scripts/build_particles.py');read(P/'work/zeus_hand2_20261113/scripts/source_import.py')
    report={'passed':True,'profiles':reports,'source_input_sha256':INPUTS,'output_sha256':outputs,'scope':'Only each original skin qiangshentexiao body layer. No muzzle, skill, projectile, status, hit, sound or gameplay change. Lava yumao is orange fire09_8 sprite imagery, not a feather, and remains its original fire appearance; no misleading name-based surface conversion.','adaptations_and_limits':['Private UserNewParticle DLL unavailable; source curves use existing converter and257 samples; seeded source random ranges and original parameter-driven motion, no exact private simulation claim.','Source builtin10210 if referenced is standard1x1 XY Quad conversion; no borrowed artwork. Cone uses authored angle/radius/shell/length, exact private shell distribution unproven.','Existing18-channel mesh/billboard shared sampler, material tint/source additive-or-alpha/depth retained.','Existing recycle max(start)+max(life), gravity/force acceleration0.5*a*t^2. Sprite timing is uniformly normalized over life because private DLL clock is unavailable. Native renderer V uses subtraction, so authored positive PNG row offsets are encoded negative; scales remain1/columns,1/rows. LUT linearly interpolates across adjacent samples near frame boundaries.'], 'interface':'Existing scoped DimensionParticles Data mesh/billboard only, no new Java API.'}
    save(W/'evidence/skin_body_fx_source.json',report)
    print(json.dumps({'passed':True,'profiles':[{k:r[k]for k in ['skin_key','emitters','particles']}for r in reports]}))
if __name__=='__main__':main()
