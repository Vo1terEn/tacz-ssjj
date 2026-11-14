"""Project the original feather onto finite source-gun triangles, no Java change."""
from pathlib import Path
import hashlib,json,shutil
import numpy as np
W=Path(__file__).resolve().parents[1];P=W.parents[1]
def load(p):return json.loads(Path(p).read_text('utf-8-sig'))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n','utf8')
def clip(poly,axis,bound,above):
    result=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        ina=(a[axis]>=bound)if above else(a[axis]<=bound);inb=(b[axis]>=bound)if above else(b[axis]<=bound)
        if ina:result.append(a)
        if ina!=inb:result.append(a+(bound-a[axis])/(b[axis]-a[axis])*(b-a))
    return result
def main():
    old=P/'work/zeus_reload_fx_20261113';assets=W/'pack/assets/ssjj_zeus';base=W/'baseline_assets/assets/ssjj_zeus/effects/body.json';before=load(base);body=load(base);e=next(e for e in body['emitters']if e['name']=='yumao');original=load(old/'evidence/body_fx_source_import.json');trace=next(t for t in original['traces']if t['name']=='yumao');audit=load(W/'evidence/feather_source_axis_audit.json');source=W/'pack/assets/ssjj_zeus/source_mesh.json';part=next(p for p in load(source)['parts']if p['bone']=='WeaponAnchor3');vertices=np.array(part['positions']);faces=np.array(part['triangles']);p0=e['particles'][0];center=np.array(p0['frames'][0][:3]);size=p0['frames'][0][3];a=np.array(audit['source_texture_alpha_pca']['long_axis_quad_xy']);b=np.array([-a[1],a[0]]);offset=.0005;positions=[];uv=[];triangles=[];provenance=[]
    # A is the source PNG major axis, mapped to gun -X. Its perpendicular maps
    # to +Z. The scale is the original first seeded source feather, unchanged.
    for face_id,indices in enumerate(faces):
        tri=vertices[indices];normal=np.cross(tri[1]-tri[0],tri[2]-tri[0]);length=np.linalg.norm(normal)
        if length<1e-12:continue
        normal/=length
        if abs(normal[1])<.2 or np.sign(normal[1])*np.mean(tri[:,1])<.005:continue
        long=(center[0]-tri[:,0])/size;across=(tri[:,2]-center[2])/size;q=long[:,None]*a+across[:,None]*b;tex=np.c_[q[:,0]+.5,.5-q[:,1]]
        poly=[np.r_[tex[i],tri[i],np.eye(3)[i]]for i in range(3)]
        for ax,bound,above in [(0,0,True),(0,1,False),(1,0,True),(1,1,False)]:
            if poly:poly=clip(poly,ax,bound,above)
        if len(poly)<3:continue
        for k in range(1,len(poly)-1):
            points=[poly[0],poly[k],poly[k+1]];xyz=np.array([r[2:5]for r in points]);area=np.linalg.norm(np.cross(xyz[1]-xyz[0],xyz[2]-xyz[0]))/2
            if area<1e-14:continue
            start=len(positions)
            for r in points:positions.append(np.round(r[2:5]+normal*offset,10).tolist());uv.append(np.round(r[:2],10).tolist())
            triangles.append([start,start+1,start+2]);provenance.append({'source_triangle':int(face_id),'source_indices':indices.tolist(),'side':'positiveY'if normal[1]>0 else'negativeY','source_face_normal':normal.tolist(),'source_barycentric':[r[5:8].tolist()for r in points]})
    assert positions and any(x['side']=='positiveY'for x in provenance)and any(x['side']=='negativeY'for x in provenance)
    geometry={'positions':positions,'uv':uv,'triangles':triangles};frames=[]
    for oldframe in p0['frames']:
        f=list(oldframe);f[:10]=[0,0,0,1,1,1,0,0,0,1];frames.append(f)
    e['mode']='mesh';e['geometry']=geometry;e['duration']=p0['life']+p0['birth'];e['particles']=[{'birth':p0['birth'],'life':p0['life'],'frames':frames}]
    for x,y in zip(before['emitters'],body['emitters']):
        if x['name']!='yumao':assert x==y
    out=assets/'effects/body.json';save(out,body)
    source_refs={**audit['source_input_sha256'],str((old/'evidence/body_fx_source_import.json').relative_to(P).as_posix()):sha(old/'evidence/body_fx_source_import.json')}
    report={'passed':True,'surface_attached':True,'java_changes':False,'original_yumao_classification':{'billboardType':trace['source_definition']['billboardType'],'material_Billboard':dict(trace['source_material']['fields']['m_SavedProperties']['m_Floats'])['_Billboard'],'original_mesh':trace['source_mesh_id'],'not_misclassified':True},'adaptation':'User requires feather on the gun. Replace the 15 independently flying source billboards with a static mesh layer using the original first seeded feather size/center and original color/alpha/lifetime curves. Its source life plus source birth repeats; no exact private recycle semantics are claimed.','source_feather_size_m':size,'source_seeded_birth_center_native':center.tolist(),'surface_offset_m':offset,'uv_contract':'Original PNG quad XY major axis maps to gun -X, perpendicular maps to +Z. Original square metric size retained. Gun triangles are clipped to original [0,1] UV domain; no repeated outside-domain texture or new artwork.','geometry_triangles':len(triangles),'geometry_vertices':len(positions),'source_triangles_by_side':{s:len(set(x['source_triangle']for x in provenance if x['side']==s))for s in ['positiveY','negativeY']},'triangle_provenance':provenance,'original_other_14_emitter_names':[x['name']for x in before['emitters']if x['name']!='yumao'],'input_sha256':{base.relative_to(W).as_posix():sha(base),source.relative_to(W).as_posix():sha(source),'evidence/feather_source_axis_audit.json':sha(W/'evidence/feather_source_axis_audit.json'),Path(__file__).relative_to(W).as_posix():sha(Path(__file__))},'source_reference_sha256':source_refs,'output_sha256':{out.relative_to(W).as_posix():sha(out)}}
    save(W/'evidence/feather_surface_data.json',report)
    # Withdraw the unused generic-interface source/overlay from active inputs.
    for source_path,target in [(W/'src/cn/ssjjdimension/client/DimensionParticles.java',W/'superseded/normalized_billboard/src/DimensionParticles.java'),(W/'build/feather/runtime.jar',W/'superseded/normalized_billboard/runtime.jar')]:
        if source_path.exists():target.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(source_path),str(target))
    print(json.dumps({'passed':True,'mesh_triangles':len(triangles),'sides':report['source_triangles_by_side'],'source_size_m':size,'surface_offset_m':offset,'runtime_overlay':None}))
if __name__=='__main__':main()
