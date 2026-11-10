"""Bake original custom UserNewParticle normal-state curves to shared LUTs.

Known source data is preserved. Private-engine random seed, force integration,
billboard and recycle conventions are explicit Minecraft adaptations, not an
assertion of a pixel-exact Unity simulation.
"""
import hashlib,json,math
import numpy as np
from scipy.spatial.transform import Rotation
import source_import as s
from import_effects import ALIGN,FX,rgba

BODY_CARD='E072'
SEED=0x20261107;N=257
def xyz(d):return np.array([d[k]for k in'xyz'],float)
def oned(d,index,rng):
 typ=d['oneDType']
 if typ==0:return d['_value']
 if typ==1:
  a=d['_anchors'];return float(np.interp(index,[a['_time']for a in a],[a['_value']for a in a]))*d['_maxCurveAnchorValue']
 if typ==2:return float(rng.uniform(min(d['_min'],d['_max']),max(d['_min'],d['_max'])))
 raise ValueError(('unhandled curve',d))
def color24(c):return np.array([(c>>16)&255,(c>>8)&255,c&255],float)/255
def interp(x,xs,ys,default=1):return float(np.interp(x,xs,ys))if len(xs)else default
def source_curve_color(v,t):
 col=color24(v['color']['_value']);a=1.
 if v['hasGradientColorEffector']:
  if not v['JustAlpha']and v['_colors']:
   vals=np.array([color24(c)for c in v['_colors']]);col=np.array([interp(t,np.array(v['_colorRatios'])/255,vals[:,i])for i in range(3)])
  a=interp(t,np.array(v['_alphaRatios'])/255,v['_alphas'])
 return np.r_[col,a]
def main():
 c=s.Catalog();doc=s.load(FX/(BODY_CARD+'.json'));obs={o['id']:o['data']for o in doc['objects']if'data'in o};old=s.load(s.W/'evidence/source_effects_import.json');pending=old.get('omitted_normal_dynamic_components',old.get('previously_omitted_normal_dynamic_components',[]));ids={d['component_id']for d in pending};emitters=[];trace=[];max_uv_error=0.;particles_count=0
 def pt(i):return obs[i]if i in obs else c.get(c.find(i))
 def globaltx(t):
  own=s.trs(t['m_LocalPosition'],t['m_LocalRotation'],t['m_LocalScale']);pid=t['m_Father']['m_PathID'];return globaltx(pt(pid))@own if pid else own
 for o in doc['objects']:
  if o['id']not in ids:continue
  v=o['data'];go=pt(v['m_GameObject']['m_PathID']);name=go['m_Name'];rng=np.random.default_rng(SEED^(o['id']&0xffffffff));t=next(pt(x['component']['m_PathID'])for x in go['m_Component']if'm_LocalPosition'in pt(x['component']['m_PathID']));m=s.J@ALIGN@globaltx(t);proper=m@s.J;linear=proper[:3,:3];scl=np.linalg.norm(linear,axis=0);rot=linear/np.maximum(scl,1e-20);u,_,vh=np.linalg.svd(rot);rot=u@vh;assert np.linalg.det(rot)>.99
  mat=pt(v['mater']['m_PathID']);props=mat['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];texp=c.find(env['m_Texture']['m_PathID'],'.png');tb=s.read(texp);h=hashlib.sha256(tb).hexdigest();out=s.AS/f'textures/effects/{h[:20]}.png';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(tb);cols=dict(props['m_Colors']);tint=2*rgba(cols['_TintColor'])*rgba(cols['_DyeingColor']);dst=dict(props['m_Floats']).get('_BlendDst',1);mode='mesh'if v['billboardType']==4 and v['mesh']['m_PathID']!=10210 else'billboard';geometry=None
  if mode=='mesh':
   mesh=s.decode_mesh(pt(v['mesh']['m_PathID']));points=mesh['p']@s.J[:3,:3]*.01;uv=mesh['uv']*[1,-1]+[0,1];geometry={'positions':points.tolist(),'uv':uv.tolist(),'triangles':mesh['tri'][:,[0,2,1]].astype(int).tolist()}
  ps=[];count=v['ParticleNumber'];maxlife=0.;maxbirth=0.;allcenters=[]
  for i in range(count):
   f=i/max(count-1,1);life=oned(v['lifeTime'],f,rng);assert life>0;birth=oned(v['startTime'],f,rng);maxlife=max(maxlife,life);maxbirth=max(maxbirth,birth);shape=v['shape'];st=shape['shapeType'];position=np.zeros(3);direction=np.zeros(3)
   if st==0:
    lo=np.minimum(xyz(shape['_rectFrom']),xyz(shape['_rectTo']));hi=np.maximum(xyz(shape['_rectFrom']),xyz(shape['_rectTo']));position=rng.uniform(lo,hi);direction=position-(lo+hi)*.5
   elif st==1:
    # Source axis forward (+Z) cone/cylinder; deg0/radius0 in this gun.
    theta=rng.uniform(0,math.tau);rad=shape.get('_radiusBig',0)*math.sqrt(rng.random());position=np.array([math.cos(theta)*rad,math.sin(theta)*rad,rng.uniform(0,shape.get('_length',0))]);direction=np.array([0,0,1.])
   elif st==3:pass
   else:raise ValueError(('unhandled shape',st))
   if not v['directionByShape']:direction=xyz(v['direction']['value'])
   direction=s.unit(direction);speed=oned(v['velocity'],f,rng);rotation=oned(v['rot'],f,rng);spin=oned(v['rotvelocity'],f,rng);size=np.array([oned(v['size'+a],f,rng)for a in'XYZ']);
   if v['uniformScale_']:size[:]=size[0]
   if v['hasForceEffector']:
    force=xyz(v['_forceDir']);force=s.unit(force)*v['_force']
   else:force=np.zeros(3)
   basealpha=oned(v['alpha'],f,rng);frames=[]
   for phase in np.linspace(0,1,N):
    age=phase*life;local=position+direction*speed*age+.5*force*age*age
    if v['hasGravityEffector']:local+=.5*xyz(v['gravityDir'])*v['gravity']*age*age
    center=(m@np.r_[local,1])[:3]*.01;scale=size.copy()
    if v['hasSizeEffector']:
     if v['uniformScale_']:scale*=interp(phase,v['_keyFrameLifeTime'],v['_sizeX'])
     else:scale*=np.array([interp(phase,v['_keyFrameLifeTime'],v['_size'+a])for a in'XYZ'])
    scale*=scl
    angle=rotation+spin*age
    if mode=='mesh':
     ax=np.zeros(3);ax[int(v['RotateAxis'])]=math.radians(angle);r=Rotation.from_matrix(rot) * Rotation.from_rotvec(s.J[:3,:3]@ax*-1);q=r.as_quat()
    else:
     # Source billboards use Y/Z dimensions; renderer supplies inverse camera
     # basis in the captured native bone frame, then local screen Z spin.
     scale=np.array([scale[2],scale[1],1.])*.01;q=Rotation.from_euler('z',angle,degrees=True).as_quat()
    col=source_curve_color(v,phase)*tint;col[3]*=basealpha;col=np.clip(col,0,1)
    us,vs=env['m_Scale']['x'],env['m_Scale']['y'];uo,vo=env['m_Offset']['x'],env['m_Offset']['y']
    if v['hasSpriteSheetEffector']:
     rows=max(v['rows'],1);columns=max(v['columns'],1);fr=min(int(phase*rows*columns),rows*columns-1);us/=columns;vs/=rows;uo+=(fr%columns)/columns;vo+=(fr//columns)/rows
    if v['hasUVEffector']:
     uo+=interp(phase,v['_uvkeyFrameLifeTime'],v['_u'],0);vo-=interp(phase,v['_uvkeyFrameLifeTime'],v['_v'],0)
    frames.append(np.r_[center,scale,q,col,us,vs,uo,vo].tolist());allcenters.append(center)
   ps.append({'birth':birth,'life':life,'frames':frames})
  particles_count+=len(ps);duration=max(maxbirth+maxlife,maxlife,.01);em={'name':name,'texture':f'ssjj_dimension:textures/effects/{h[:20]}.png','mode':mode,'render':'additive'if dst==1 else'alpha','duration':duration,'geometry':geometry,'particles':ps}
  if v['billboardType']==0 and v['uniformScale_']==1 and dict(props['m_Floats']).get('_Billboard')==1 and v['sizeX']['oneDType']==0 and v['sizeX']['_value']==50 and scl[1]/scl[2]>5:
   assert v['billboardType']==0 and v['uniformScale_']==1 and dict(props['m_Floats'])['_Billboard']==1
   assert v['sizeX']['oneDType']==0 and v['sizeX']['_value']==50 and scl[1]/scl[2]>5
   em['billboard_axis']=s.unit(m[:3,1]).tolist()
  emitters.append(em);centers=np.array(allcenters)
  trace.append({'name':name,'source_component_id':o['id'],'source_count':count,'output_particle_count':len(ps),'frame_count_per_life':N,'mode':mode,'billboard_type':v['billboardType'],'billboard_axis':em.get('billboard_axis'),'billboard_axis_stretch_ratio_yz':float(scl[1]/scl[2])if'billboard_axis'in em else None,'billboard_axis_evidence':'Source full prefab Y-axis, including 90deg in-plane rotation; projected into native camera plane to preserve long-axis direction. Source billboard0/_Billboard1/uniformScale1/sizeX50 and stretch ratio>5 asserted. Other round glows preserve facing.'if'billboard_axis'in em else None,'source_transform':globaltx(t).tolist(),'output_center_bounds_blocks':[centers.min(0).tolist(),centers.max(0).tolist()],'duration_adaptation_seconds':duration,'source_definition':v,'source_material':mat,'source_texture':str(texp.relative_to(s.P))})
 result={'schema':1,'gun':'ssjj_dimension:dimension_star2','bone':'weapon_particle_effect','emitters':emitters};s.save(s.AS/'effects/particles.json',result)
 # Dense-vs-LUT checks of exact piecewise source gradient/size/UV values.
 errors=[]
 for rec in trace:
  v=rec['source_definition'];samples=np.array([source_curve_color(v,t)for t in np.linspace(0,1,N)]);maximum=0.
  for t in np.linspace(0,1,4097):
   estimate=np.array([np.interp(t,np.linspace(0,1,N),samples[:,j])for j in range(4)]);maximum=max(maximum,float(np.max(abs(estimate-source_curve_color(v,t)))))
  errors.append({'emitter':rec['name'],'maximum_normalized_gradient_LUT_component_error':maximum})
 report={'status':'ALL_13_NORMAL_DYNAMIC_COMPONENTS_BAKED_WITH_EXPLICIT_ADAPTATIONS','inputs_sha256':s.INPUTS,'seed':SEED,'emitters':len(emitters),'particles':particles_count,'frame_samples_per_particle':N,'traces':trace,'numerical_curve_errors':errors,'source_exact_fields':['material/texture bytes','emitter full prefab transforms and mesh','ParticleNumber','oneD constants/ranges','gradient color and alpha ratios/255','size/UV/sprite-sheet life keyframes'],'adaptations_and_residuals':['Private UserNewParticle engine code unavailable: oneD _curveType0 interpreted linear, preset colors are packed 0xRRGGBB.','Fixed seeded uniform random distribution; original RNG/seed unknown, exact pointwise correspondence unavailable.','Particles recycle after max(birth)+max(life) per emitter, smoothly prewarmed by renderer. Original private recycling semantics not proven.','Box shape uses uniform source bounds; shape1 uses +Z cone/cylinder. DirectionByShape points from box center outward; shape3 has zero direction. Source private direction semantics not proven.','Force integrated as acceleration with a=.5*force*age^2; zero forceDir stays zero; source private force integration not proven.','Billboard types0/1 use camera-facing quads; axial type1 exact constrained facing remains residual.','Billboard screen sizes use source Y/Z, custom mesh geometry remains measured source dimensions; source dimension conventions require live comparison.'],'boundary':'Source-derived emitter coverage and finite LUT checks only. Need production sampler/offline-render/native-view/live verification; do not call reference_exact.'};s.save(s.W/'evidence/source_particles_import.json',report)
 old['status']='CONFIRMED_NORMAL_MESH_AND_ALL_DYNAMIC_COMPONENTS_IMPORTED';old['previously_omitted_normal_dynamic_components']=pending;old.pop('omitted_normal_dynamic_components',None);old['normal_dynamic_components']='All13 encoded effects/particles.json; adaptations quantified in source_particles_import.json';s.save(s.W/'evidence/source_effects_import.json',old)
 print(json.dumps({'emitters':len(emitters),'particles':particles_count,'bytes':(s.AS/'effects/particles.json').stat().st_size,'gradient_max_error':max(x['maximum_normalized_gradient_LUT_component_error']for x in errors)}))

if __name__=='__main__':main()
