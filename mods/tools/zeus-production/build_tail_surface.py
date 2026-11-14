"""Original rear-disc surfaces carry original ring/electric/fire PNG motion.

No geometry expands away from the gun. Original radial ring growth is expressed
as UV scale about the source texture center on finite original gun triangles.
"""
from pathlib import Path
import copy,hashlib,json
import numpy as np
from scipy.spatial.transform import Rotation
W=Path(__file__).resolve().parents[1];P=W.parents[1];B=W/'baseline_assets/assets/ssjj_zeus'
ORIGINAL=P/'work/zeus_fx_motion_20261114/baseline_assets/assets/ssjj_zeus/effects'
NAMES=['body.json','body_hand_of_zeuss.json','body_hand_of_zeuss1.json']+[f'body_{g}_lava.json'for g in ['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']]
RING={'ring','ring_duplicate_2','xuanzhuan','xuanzhuan_duplicate_2'}
RADIUS=.030;OFFSET=.0005
def read(p):return json.loads(Path(p).read_text('utf8'))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def clip(poly,normal,bound):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        av=float(a[2:5]@normal-bound);bv=float(b[2:5]@normal-bound);ina=av<=1e-13;inb=bv<=1e-13
        if ina:out.append(a)
        if ina!=inb:out.append(a+(b-a)*av/(av-bv))
    return out
def adapt(baseline,original,vertices,faces):
    e=copy.deepcopy(baseline);isring=e['name']in RING;source=original['particles'][0]
    # Source blue inner disc has16.768mm radius. A source0.30-life electric
    # quad puts its atlas arcs inside that disc; the old0.5-life footprint
    # placed the arcs on the rim instead. Original tint/alpha is not increased.
    refindex=256 if isring else 77;radius=RADIUS if isring else .016
    ref=np.array(source['frames'][refindex]);quad=np.array(original['geometry']['positions'])
    world=Rotation.from_quat(ref[6:10]).apply(quad*ref[3:6]);side=0
    if np.ptp(world[:,1])<1e-6:side=int(np.sign(ref[1]))
    center=np.array([-.13137,0.,.05862]if isring else[-.13192,0.,.05773]);world+=center
    affine=np.linalg.lstsq(np.c_[world[:,0],world[:,2],np.ones(len(world))],np.array(original['geometry']['uv']),rcond=None)[0]
    positions=[];uv=[];triangles=[];provenance=[]
    for fi,inds in enumerate(faces):
        tri=vertices[inds];normal=np.cross(tri[1]-tri[0],tri[2]-tri[0]);norm=np.linalg.norm(normal)
        if norm<1e-12:continue
        normal/=norm
        if abs(normal[1])<.25 or normal[1]*np.mean(tri[:,1])<.005:continue
        if side and np.sign(normal[1])!=side:continue
        if np.any(np.min(tri[:,[0,2]],axis=0)>center[[0,2]]+radius)or np.any(np.max(tri[:,[0,2]],axis=0)<center[[0,2]]-radius):continue
        tex=np.c_[tri[:,0],tri[:,2],np.ones(3)]@affine
        poly=[np.r_[tex[i],tri[i],np.eye(3)[i]]for i in range(3)]
        # Intersection of64 half-planes is a finite inscribed source-disc
        # carrier. Every generated triangle stays in one original finite face.
        for angle in np.linspace(0,2*np.pi,64,endpoint=False):
            n=np.array([np.cos(angle),0,np.sin(angle)])
            if poly:poly=clip(poly,n,float(center@n+radius*np.cos(np.pi/64)))
        for k in range(1,len(poly)-1):
            points=[poly[0],poly[k],poly[k+1]];xyz=np.array([p[2:5]for p in points])
            if np.linalg.norm(np.cross(xyz[1]-xyz[0],xyz[2]-xyz[0]))<1e-14:continue
            start=len(positions)
            for p in points:positions.append(np.round(p[2:5]+normal*OFFSET,10).tolist());uv.append(np.round(p[:2],10).tolist())
            triangles.append([start,start+1,start+2]);provenance.append({'geometry_triangle':len(triangles)-1,'source_triangle':int(fi),'source_indices':inds.tolist(),'normal':normal.tolist(),'barycentric':[p[5:8].tolist()for p in points],'side':int(np.sign(normal[1]))})
    assert positions,e['name'];e['geometry']={'positions':positions,'uv':uv,'triangles':triangles};e['mode']='mesh'
    uvbounds=[]
    for particle,old in zip(e['particles'],original['particles']):
        # Restore authored source ring emission spacing instead of the former
        # continuous3-wave stack. Source count and1s life stay unchanged.
        if isring:particle['birth']=old['birth']
        for f,s in zip(particle['frames'],old['frames']):
            f[:10]=[0.,0.,0.,1.,1.,1.,0.,0.,0.,1.]
            k=ref[3]/s[3]if isring else 1.-.25*(s[3]/max(old['frames'][-1][3],1e-12))
            us,vs,uo,vo=s[14:18];f[14:18]=[us*k,vs*k,uo+us*(1-k)/2,vo-vs*(1-k)/2]
            # Relative-to-cell UV must stay in[0,1]; this prevents wrapping to
            # repeated ring copies or adjacent4x4 sprite cells.
            local=np.asarray(uv)*k+(1-k)/2;uvbounds.append([float(local.min()),float(local.max())])
    if isring:e['duration']=original['duration']
    assert min(x[0]for x in uvbounds)>-1e-7 and max(x[1]for x in uvbounds)<1+1e-7
    trace={'name':e['name'],'center_XZ_m':center[[0,2]].tolist(),'radius_m':radius,'normal_offset_m':OFFSET,'source_side':side,'triangles':provenance,'triangle_count':len(triangles),'XZ_to_source_UV':affine.tolist(),'reference_source_phase':refindex/256,'UV_within_original_texture_or_sprite_cell':[min(x[0]for x in uvbounds),max(x[1]for x in uvbounds)],'source_count_life_preserved':True,'fixed_unit_TRS':True,'duration':e['duration'],'births':[p['birth']for p in e['particles']]}
    return e,trace
def main():
    mesh=B/'source_mesh.json';part=next(p for p in read(mesh)['parts']if p['bone']=='WeaponAnchor3');vertices=np.array(part['positions']);faces=np.array(part['triangles']);inputs={Path(__file__).relative_to(W).as_posix():sha(__file__),mesh.relative_to(W).as_posix():sha(mesh)};external={};resources={};outputs={}
    for name in NAMES:
        before=B/'effects'/name;original=ORIGINAL/name;body=read(before);sources={e['name']:e for e in read(original)['emitters']};traces={}
        for i,e in enumerate(body['emitters']):
            if e['name']in RING|{'dianqiu'}:body['emitters'][i],traces[e['name']]=adapt(e,sources[e['name']],vertices,faces)
        out=W/'pack/assets/ssjj_zeus/effects'/name;save(out,body);resources[out.relative_to(W).as_posix()]=traces;outputs[out.relative_to(W).as_posix()]=sha(out);inputs[before.relative_to(W).as_posix()]=sha(before);external[original.relative_to(P).as_posix()]=sha(original)
    report={'passed':True,'source_mesh':mesh.relative_to(W).as_posix(),'source_bone':'WeaponAnchor3','resources':resources,'normal_offset_m':OFFSET,'ring_radius_m':RADIUS,'electric_inner_disc_radius_m':.016,'adapter':'Original finite disc/bevel faces: rings within30mm, electric/fire in16mm inner disc. Source texture mapping plus original ring radial growth in UV; electric/fire uses source0.30078125-life footprint and original16-cell timing with restrained25% centered UV expansion. The carrying gun triangles remain fixed. Original source ring birth/duration restored to avoid a continuous three-ring white stack. Current baseline RGB/alpha unchanged; no brightness increase.','limits':'Private particle DLL unavailable. Serialized per-axis sizeY=.8 is a useful source-plane clue, not proof of private uniformScale semantics. Surface choice and electric fixed footprint are explicit video-driven adaptation.','input_sha256':inputs,'external_source_sha256':external,'output_sha256':outputs}
    save(W/'evidence/tail_surface_provenance.json',report);print('PASS original finite rear-disc carriers:',sum(len(v)for v in resources.values()),'tail layers')
if __name__=='__main__':main()
