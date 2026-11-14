"""Attach original galaxy wings to finite gun faces and close vortex rotation."""
from pathlib import Path
import copy,hashlib,json
import numpy as np
from scipy.spatial.transform import Rotation
W=Path(__file__).resolve().parents[1];P=W.parents[1];A=W/'pack/assets/ssjj_zeus';B=W/'baseline_assets/assets/ssjj_zeus'
GUNS=['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2'];WINGS={'Particle_duplicate_7','Particle_duplicate_9'};VORTEX={'Particle_duplicate_4','Particle_duplicate_5'}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def load(p):return json.loads(Path(p).read_text('utf-8-sig'))
def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':'),allow_nan=False)+'\n','utf8')
def cross(a,b):return a[0]*b[1]-a[1]*b[0]
def clip(poly,a,b,sign):
    out=[]
    for p,q in zip(poly,poly[1:]+poly[:1]):
        x=sign*cross(b-a,p[[0,2]]-a);y=sign*cross(b-a,q[[0,2]]-a);ip=x>=-1e-12;iq=y>=-1e-12
        if ip:out.append(p)
        if ip!=iq:out.append(p+x/(x-y)*(q-p))
    return out
def attach(emitter,part):
    vertices=np.asarray(part['positions']);faces=np.asarray(part['triangles']);gun=vertices[faces];normal=np.cross(gun[:,1]-gun[:,0],gun[:,2]-gun[:,0]);normal/=np.maximum(np.linalg.norm(normal,axis=1,keepdims=True),1e-15);gxz=gun[:,:,[0,2]];lo=gxz.min(1);hi=gxz.max(1)
    particle=emitter['particles'][0];frame=np.asarray(particle['frames'][0]);uv=np.asarray(emitter['geometry']['uv']);world=Rotation.from_quat(frame[6:10]).apply(np.asarray(emitter['geometry']['positions'])*frame[3:6])+frame[:3];positions=[];tex=[];triangles=[];provenance=[]
    for source_face,ids in enumerate(emitter['geometry']['triangles']):
        src=world[ids];a=src[:,[0,2]];matrix=np.stack((a[1]-a[0],a[2]-a[0]),1);det=np.linalg.det(matrix)
        if abs(det)<1e-12:continue
        side=np.sign(src[:,1].mean());use=np.where((normal[:,1]*side>.2)&(gun[:,:,1].mean(1)*side>.003)&np.all(hi>=a.min(0),axis=1)&np.all(lo<=a.max(0),axis=1))[0];inv=np.linalg.inv(matrix)
        for fid in use:
            poly=list(gun[fid].copy())
            for j in range(3):
                if poly:poly=clip(poly,a[j],a[(j+1)%3],np.sign(det))
            for k in range(1,len(poly)-1):
                out=np.asarray([poly[0],poly[k],poly[k+1]])
                if np.linalg.norm(np.cross(out[1]-out[0],out[2]-out[0]))<1e-12:continue
                ab=(out[:,[0,2]]-a[0])@inv.T;bary=np.c_[1-ab.sum(1),ab];uvout=bary@uv[ids];start=len(positions);positions.extend(np.round(out+normal[fid]*.0005,10).tolist());tex.extend(np.round(uvout,10).tolist());triangles.append([start,start+1,start+2]);provenance.append({'source_gun_face':int(fid),'source_wing_face':source_face,'side':int(side),'normal':normal[fid].tolist(),'original_gun_triangle':gun[fid].tolist()})
    assert positions and {-1,1}=={p['side']for p in provenance}
    emitter['geometry']={'positions':positions,'uv':tex,'triangles':triangles}
    for p in emitter['particles']:
        for f in p['frames']:f[:10]=[0,0,0,1,1,1,0,0,0,1]
    return {'name':emitter['name'],'source_native_bounds_m':[world.min(0).tolist(),world.max(0).tolist()],'surface_native_bounds_m':[np.min(positions,0).tolist(),np.max(positions,0).tolist()],'surface_offset_m':.0005,'geometry_triangles':len(triangles),'original_gun_faces':len({p['source_gun_face']for p in provenance}),'triangle_provenance':provenance,'uv_policy':'Original CB atlas UV transferred barycentrically from original wing projected X/Z triangles; clipped to finite actual gun faces on the same side, no texture replacement or repeat extension.'}
def main():
    part=next(p for p in load(B/'source_mesh.json')['parts']if p['bone']=='WeaponAnchor3');before=load(B/'effects/body_hand_of_zeuss_galaxy.json');body=copy.deepcopy(before);wings=[]
    for e in body['emitters']:
        if e['name']in WINGS:wings.append(attach(e,part))
        elif e['name']in VORTEX:
            assert len(e['particles'])==1
            for p in e['particles']:
                assert p['life']==3.0;q0=Rotation.from_quat(p['frames'][0][6:10])
                for phase,f in zip(np.linspace(0,1,len(p['frames'])),p['frames']):f[6:10]=np.round((q0*Rotation.from_euler('z',360*phase,degrees=True)).as_quat(),10).tolist()
            e['duration']=3.0
    unchanged=[]
    for old,new in zip(before['emitters'],body['emitters']):
        if old['name']not in WINGS|VORTEX:assert old==new;unchanged.append(old['name'])
        else:
            a=copy.deepcopy(old);b=copy.deepcopy(new)
            if old['name']in WINGS:
                a.pop('geometry');b.pop('geometry')
                for p in a['particles']:
                    for f in p['frames']:f[:10]=[0,0,0,1,1,1,0,0,0,1]
            else:
                a['duration']=b['duration']
                for p,q in zip(a['particles'],b['particles']):
                    for f,g in zip(p['frames'],q['frames']):f[6:10]=g[6:10]
            assert a==b,'Unexpected changed field'
    inputs=[Path(__file__),B/'source_mesh.json'];outputs={}
    for gun in GUNS:
        original=B/f'effects/body_{gun}_galaxy.json';check=load(original);check['gun']=before['gun'];assert check==before;inputs.append(original);body['gun']='ssjj_zeus:'+gun;out=A/f'effects/body_{gun}_galaxy.json';save(out,body);outputs[out.relative_to(W).as_posix()]=sha(out)
    report={'passed':True,'wings':wings,'unchanged_other_5_emitters':unchanged,'vortex_names':sorted(VORTEX),'vortex_adapter':{'source_rotvelocity':-1,'old_interpretation':'degrees/s, only3degrees in source3second life','new_source_direction_degrees_per_second':-120,'native_reflected_local_Z_degrees_per_second':120,'period_seconds':3,'birth_unchanged':True,'life_unchanged':True,'duration':3,'cycle_closure':'One full negative source turn per unchanged3second life. Reflect-X coordinate conversion reverses native local-Z sign. duration=life eliminates previous10ms recycling gap.','boundary':'Explicit user-requested visual adaptation, not recovery of unavailable private engine units.'},'source_image_identity':{'wing':'cb_7/cb_9 original gold wing atlas e8342627e3dc0b36c309','vortex':'xuanwo_0007_4/5 original orange swirl atlas1e8f31497b7d881f8cd0'},'input_sha256':{p.relative_to(W).as_posix():sha(p)for p in inputs},'output_sha256':outputs,'java_changes':False,'source_png_changes':False}
    save(W/'evidence/galaxy_body_motion_data.json',report);print(json.dumps({'passed':True,'wing_triangles':[x['geometry_triangles']for x in wings],'vortex_period_seconds':3,'resources':len(outputs)}))
if __name__=='__main__':main()
