"""Raise the already-fitted galaxy wing surfaces by 2mm; emit merge patches only."""
from pathlib import Path
import copy,hashlib,json
import numpy as np
W=Path(__file__).resolve().parents[1];P=W.parents[1]
B=W/'baseline_assets/assets/ssjj_zeus'
OLD=P/'work/zeus_fx_motion_20261114'
NAMES={'Particle_duplicate_7','Particle_duplicate_9'}
GUNS=['hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2']
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n',encoding='utf8')

def main():
    history=OLD/'evidence/galaxy_body_motion_data.json';data=read(history)
    provenance={row['name']:row for row in data['wings']}
    source=B/'source_mesh.json';part=next(p for p in read(source)['parts'] if p['bone']=='WeaponAnchor3')
    source_vertices=np.asarray(part['positions']);source_faces=np.asarray(part['triangles'])
    inputs={p.relative_to(W).as_posix():sha(p) for p in [source,Path(__file__)]};overrides={};records=[]
    for gun in GUNS:
        relative='assets/ssjj_zeus/effects/body_'+gun+'_galaxy.json';file=W/'baseline_assets'/relative
        # The previous accepted production is the exact frozen preimage, not a
        # geometry regenerated from old raw art or an older offset.
        assert sha(file)==sha(OLD/'pack'/relative)
        body=read(file);overrides[relative]={};inputs[file.relative_to(W).as_posix()]=sha(file)
        for old in body['emitters']:
            if old['name'] not in NAMES:continue
            row=provenance[old['name']];new=copy.deepcopy(old);geometry=new['geometry']
            pos=np.asarray(geometry['positions']);out=pos.copy();indices=np.asarray(geometry['triangles']);proof=row['triangle_provenance']
            assert len(indices)==len(proof)
            assigned={};sides=set();normals=[];triangles=[]
            for ids,ref in zip(indices,proof):
                tri=source_vertices[source_faces[ref['source_gun_face']]]
                assert np.array_equal(tri,np.asarray(ref['original_gun_triangle']))
                n=np.cross(tri[1]-tri[0],tri[2]-tri[0]);n/=np.linalg.norm(n)
                assert np.max(abs(n-np.asarray(ref['normal'])))<1e-12
                # Each existing clipped triangle moves rigidly; no projection,
                # flattening, remeshing, atlas change or animation rewrite.
                for vid in ids:
                    q=np.round(pos[vid]+n*.002,10)
                    if int(vid) in assigned:assert np.max(abs(q-assigned[int(vid)]))<1e-10
                    assigned[int(vid)]=q;out[vid]=q
                sides.add(int(ref['side']));normals.append(n.tolist());triangles.append(tri.tolist())
            assert len(assigned)==len(pos) and sides=={-1,1}
            geometry['positions']=out.tolist()
            a=copy.deepcopy(old);b=copy.deepcopy(new);a['geometry'].pop('positions');b['geometry'].pop('positions');assert a==b
            for particle in old['particles']:
                assert np.all(np.asarray(particle['frames'])[:,:10]==[0,0,0,1,1,1,0,0,0,1])
            overrides[relative][old['name']]=new
            records.append({'resource':relative,'emitter':old['name'],'triangles':indices.tolist(),
                'source_triangles':triangles,'source_normals':normals,'sides':sorted(sides),
                'source_face_ids':[ref['source_gun_face'] for ref in proof],
                'vertices':len(pos),'normal_offset_before_m':.0005,'normal_offset_after_m':.0025})
    output=W/'qa/galaxy_wing_overrides.json';save(output,{'schema':1,'overrides':overrides})
    proofpath=W/'qa/galaxy_wing_surface_provenance.json';save(proofpath,{'source_history_file':history.relative_to(P).as_posix(),'source_history_sha256':sha(history),'records':records})
    save(W/'evidence/galaxy_wing_gap_build.json',{'passed':True,'production_files_written':False,
        'adaptation':'Only existing clipped triangle positions move 2mm along their exact original finite gun-face normal. Total gap 2.5mm. Geometry topology/UV/shape within each triangle and every animation/timing/color/sprite field stay unchanged.',
        'input_sha256':inputs,'external_provenance_sha256':{history.relative_to(P).as_posix():sha(history)},
        'output_sha256':{p.relative_to(W).as_posix():sha(p)for p in [output,proofpath]}})
    print(json.dumps({'passed':True,'resources':len(overrides),'emitters':len(records),'total_gap_m':.0025}))

if __name__=='__main__':main()
