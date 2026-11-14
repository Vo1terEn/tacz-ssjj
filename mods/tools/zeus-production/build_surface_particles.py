"""Emit mergeable particle-only overrides; never writes pack resources.

One source particle remains one particle. A small quad travels monotonically
along a true finite WeaponAnchor3 face, rather than leaving the weapon in air.
Source PNG, atlas frames, RGBA, birth/life, duration and sample count stay exact.
"""
from __future__ import annotations
import copy
import hashlib
import json
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation

W = Path(__file__).resolve().parents[1]
B = W / 'baseline_assets/assets/ssjj_zeus'
GAP = .0005
EDGE = .00015
Q = np.array([[-.5,-.5,0],[.5,-.5,0],[.5,.5,0],[-.5,.5,0]])
UV = [[0,1],[1,1],[1,0],[0,0]]

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p): return json.loads(p.read_text(encoding='utf-8'))
def save(p,d):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')

def mesh_for(file):
    d=read(file)
    part=next(x for x in d['parts'] if x['bone']=='WeaponAnchor3')
    v=np.asarray(part['positions']).reshape(-1,3)
    return v[np.asarray(part['triangles']).reshape(-1,3)]

def face_data(tri,idx):
    n=np.cross(tri[1]-tri[0],tri[2]-tri[0]); area=np.linalg.norm(n)/2
    if area<1e-8:return None
    n/=2*area
    u=np.array([1.,0,0]);u-=n*np.dot(u,n);u/=np.linalg.norm(u)
    v=np.cross(n,u);r=np.column_stack([u,v,n])
    # Incenter maximizes clearance on narrow decorative facets.
    sides=np.linalg.norm(tri[[1,2,0]]-tri[[2,0,1]],axis=1)
    center=(tri*sides[:,None]).sum(0)/sides.sum()
    t=(tri-center)@r[:,:2]
    edges=np.roll(t,-1,axis=0)-t
    inward=np.column_stack([-edges[:,1],edges[:,0]])
    inward/=np.linalg.norm(inward,axis=1)[:,None]
    margin=-np.einsum('ij,ij->i',inward,t)
    return {'id':int(idx),'tri':tri,'normal':n,'rotation':r,'center':center,
            'inward':inward,'margin':margin,'area':float(area)}

def outermost(points,triangles,side):
    """True source geometry may occlude a decorative side facet: reject it."""
    a=triangles[:,0][:,[0,2]];e1=triangles[:,1][:,[0,2]]-a;e2=triangles[:,2][:,[0,2]]-a
    det=e1[:,0]*e2[:,1]-e1[:,1]*e2[:,0]
    good=abs(det)>1e-12
    for p in points:
        d=p[[0,2]]-a
        b=np.divide(d[:,0]*e2[:,1]-d[:,1]*e2[:,0],det,out=np.zeros_like(det),where=good)
        c=np.divide(e1[:,0]*d[:,1]-e1[:,1]*d[:,0],det,out=np.zeros_like(det),where=good)
        hit=good&(b>=-1e-7)&(c>=-1e-7)&(b+c<=1+1e-7)
        y=triangles[:,0,1]+b*(triangles[:,1,1]-triangles[:,0,1])+c*(triangles[:,2,1]-triangles[:,0,1])
        if np.any(hit & (side*(y-p[1])>1e-6)):return False
    return True

def faces(triangles,region):
    out=[]
    for idx,tri in enumerate(triangles):
        c=tri.mean(0)
        if region=='front':ok=tri[:,0].min()>.31 and c[0]<.46 and -.04<c[2]<.115
        else:ok=-.195<c[0]<-.075 and -.015<c[2]<.125
        if not ok:continue
        f=face_data(tri,idx)
        if f is None or abs(f['normal'][1])<.8 or f['area']<.00008:continue
        if not outermost([f['center']+f['normal']*GAP],triangles,np.sign(f['normal'][1])):continue
        out.append(f)
    if not out:raise RuntimeError('No finite exterior carrier faces for '+region)
    return sorted(out,key=lambda f:-f['area'])

def motion_fit(face,width,height,triangles):
    n=face['inward'];margin=face['margin']
    # Reserve a visible tangent path and keep all source-size variations in bounds.
    for factor in [1.,.85,.7,.55,.4,.3,.22,.16,.1]:
        support=(abs(n[:,0])*width+abs(n[:,1])*height)*factor/2+EDGE
        rem=margin-support
        if rem.min()<0:continue
        lo=-1.;hi=1.
        for a,b in zip(n[:,0],rem):
            if a>1e-10:lo=max(lo,-b/a)
            elif a<-1e-10:hi=min(hi,-b/a)
        if hi-lo<.002:continue
        lo2=lo*.8;hi2=hi*.8
        points=[]
        for d in [lo2,hi2]:
            center=face['center']+face['rotation'][:,0]*d+face['normal']*GAP
            points.extend(Q@np.diag([width*factor,height*factor,1.])@face['rotation'].T+center)
        if outermost(points,triangles,np.sign(face['normal'][1])):
            return factor,lo2,hi2
    return None

def convert(emitter,triangles,region):
    e=copy.deepcopy(emitter);e['mode']='mesh';e.pop('billboard_axis',None)
    e['geometry']={'positions':Q.tolist(),'uv':UV,'triangles':[[0,1,2],[0,2,3]]}
    candidates=faces(triangles,region);records=[]
    for k,(old,p) in enumerate(zip(emitter['particles'],e['particles'])):
        source=np.asarray(old['frames']);width=float(source[:,3].max());height=float(source[:,4].max())
        # Keep the source's intentionally paired rear sides; other emitters cover both sides.
        side=(1 if 'duplicate_2' not in e['name'] else -1) if e['name'].startswith('Particles') else (1 if k%2==0 else -1)
        choices=[]
        for f in candidates:
            if np.sign(f['normal'][1])!=side:continue
            fit=motion_fit(f,width,height,triangles)
            if fit is not None:choices.append((f,fit))
        if not choices:raise RuntimeError(f'No finite face for {e["name"]} particle {k}')
        # Prefer the largest viable carriers, then distribute particles along the shell.
        choices=choices[:min(8,len(choices))]
        f,(factor,low,high)=choices[(k//2)%len(choices)]
        r=f['rotation'];q=Rotation.from_matrix(r).as_quat();n=f['normal'];frames=[]
        for j,s in enumerate(source):
            phase=j/(len(source)-1)
            # One pass from aft to muzzle, with source alpha fading at death before loop reset.
            d=low+(high-low)*(.1+.8*phase)
            center=f['center']+r[:,0]*d+n*GAP
            a=s.copy();a[:3]=center;a[3:6]=[s[3]*factor,s[4]*factor,1.];a[6:10]=q
            frames.append(a.tolist())
        p['frames']=frames
        records.append({'particle':k,'source_face':f['id'],'size_factor':factor,'normal':n.tolist(),
                        'tangent':r[:,0].tolist(),'tangent_displacement_blocks':float((high-low)*.8),
                        'face':f['tri'].tolist(),'source_max_size':[width,height]})
    return e,records

def validate(old,new,records):
    maximum_plane_error=0.;minimum_bary=1.;displacements=[];corners=0
    assert len(old['particles'])==len(new['particles'])
    assert {k:v for k,v in old.items() if k not in ['mode','geometry','billboard_axis','particles']}=={k:v for k,v in new.items() if k not in ['mode','geometry','billboard_axis','particles']}
    for a,b,meta in zip(old['particles'],new['particles'],records):
        assert {k:v for k,v in a.items() if k!='frames'}=={k:v for k,v in b.items() if k!='frames'}
        src=np.asarray(a['frames']);s=np.asarray(b['frames']);assert np.array_equal(src[:,10:],s[:,10:])
        assert len(src)==len(s)
        tri=np.array(meta['face']);normal=np.array(meta['normal']);r=Rotation.from_quat(s[0,6:10]).as_matrix()
        verts=np.einsum('fvc,dc->fvd',Q[None,:,:]*s[:,None,3:6],r)+s[:,None,:3]
        delta=verts-tri[0];height=delta@normal;projected=verts-height[:,:,None]*normal
        maximum_plane_error=max(maximum_plane_error,float(abs(height-GAP).max()))
        x=tri[1]-tri[0];y=tri[2]-tri[0];g=np.linalg.inv(np.array([[x@x,x@y],[x@y,y@y]]))
        rhs=np.stack([(projected-tri[0])@x,(projected-tri[0])@y],axis=-1)
        bc=rhs@g.T;uvw=np.concatenate([1-bc.sum(-1,keepdims=True),bc],axis=-1)
        minimum_bary=min(minimum_bary,float(uvw.min()));corners+=len(s)*4
        displacements.append(float(np.linalg.norm(s[-1,:3]-s[0,:3])))
        assert np.isfinite(s).all() and float(uvw.min())>=-1e-6
        assert abs(height-GAP).max()<1e-8 and displacements[-1]>.001
    return {'passed':True,'particles':len(records),'frames':sum(len(p['frames']) for p in new['particles']),
            'all_quad_corners_checked':corners,'normal_gap_blocks':GAP,'maximum_plane_error':maximum_plane_error,
            'minimum_finite_triangle_barycentric':minimum_bary,'tangent_displacement_range_blocks':[min(displacements),max(displacements)],
            'source_texture_timing_rgba_uv_exact':True,'no_new_emitters':True}

def main():
    paths={'body.json':{'par':'front'},'body_hand_of_zeuss.json':{'Particles':'rear','Particles_duplicate_2':'rear'},
           'body_hand_of_zeuss1.json':{'Particles':'rear','Particles_duplicate_2':'rear'}}
    for gun in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']:
        paths['body_'+gun+'_lava.json']={'par':'front','yanjiang':'rear'}
    out={};checks={};inputs={};metadata={}
    for file,names in paths.items():
        body=B/'effects'/file;doc=read(body)
        mesh='source_mesh_hand_of_zeuss1.json' if file=='body_hand_of_zeuss1.json' else 'source_mesh_hand_of_zeuss.json' if file=='body_hand_of_zeuss.json' else 'source_mesh.json'
        triangles=mesh_for(B/mesh);key='assets/ssjj_zeus/effects/'+file;out[key]={};checks[key]={};metadata[key]={}
        for e in doc['emitters']:
            if e['name'] not in names:continue
            new,record=convert(e,triangles,names[e['name']]);out[key][e['name']]=new
            checks[key][e['name']]=validate(e,new,record);metadata[key][e['name']]=record
            texture=B/e['texture'].split(':',1)[1];inputs[texture.relative_to(W).as_posix()]=sha(texture)
        assert set(out[key])==set(names)
        for p in [body,B/mesh]:inputs[p.relative_to(W).as_posix()]=sha(p)
    target=W/'qa/front_emitter_overrides.json'
    save(target,{'schema':1,'overrides':out})
    save(W/'evidence/front_surface_particle_details.json',metadata)
    inputs[Path(__file__).relative_to(W).as_posix()]=sha(Path(__file__))
    inputs[target.relative_to(W).as_posix()]=sha(target)
    report={'passed':True,'boundary':'CPU exact finite-face TRS QA of mergeable overrides; actual production sampler and integrated visual QA pending.',
            'input_sha256':inputs,'resources':checks,'total_particles':sum(c['particles'] for v in checks.values() for c in v.values()),
            'emitter_count_unchanged':True,'production_resources_written':False}
    save(W/'evidence/front_surface_particle_check.json',report)
    print(json.dumps({'passed':True,'resources':len(out),'emitters':sum(len(v) for v in out.values()),'particles':report['total_particles'],'output':str(target)},ensure_ascii=False))

if __name__=='__main__':main()
