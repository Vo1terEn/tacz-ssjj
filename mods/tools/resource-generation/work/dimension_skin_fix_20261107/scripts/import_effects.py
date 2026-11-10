"""Confirmed normal-state mesh effects from Dimension Star II original prefab.

This does not infer skill triggers. Supports source stationary mesh components
and their UV curves; records every omitted moving/random/billboard component.
The three disabled source gun Plane renderers remain disabled.
"""
import json, hashlib
import numpy as np
from PIL import Image
import source_import as s

FX=s.P/'次元之星/特效待确认_全量目录/详情'
BODY_CARDS=('E072','E073'); MUZZLE_CARD='E071'
ALIGN=np.array([[1.,0,0,0],[0,0,-1,0],[0,1,0,0],[0,0,0,1]])

def rgba(v):return np.array([v[k]for k in 'rgba'])
def main():
 c=s.Catalog();source=s.load(s.AS/'source_mesh.json');source['parts']=[p for p in source['parts']if 'fx_source'not in p];trace=[];omitted=[];textures={};new=[]
 for code,bone in zip(BODY_CARDS,('weapon_particle_effect','weapon_particle_effect1')):
  doc=s.load(FX/(code+'.json'));obs={o['id']:o['data']for o in doc['objects']if 'data'in o}
  def pointer(i):return obs[i]if i in obs else c.get(c.find(i))
  def matrix(t):
   own=s.trs(t['m_LocalPosition'],t['m_LocalRotation'],t['m_LocalScale']);parent=t['m_Father']['m_PathID'];return matrix(pointer(parent))@own if parent else own
  for o in doc['objects']:
   v=o.get('data',{})
   if o['type']!='MonoBehaviour'or'ParticleNumber'not in v:continue
   go=pointer(v['m_GameObject']['m_PathID']);name=go['m_Name'];flags=[k for k in v if k.startswith('has')and v[k]]
   eligible=v['billboardType']==4 and not v['RandomOnBorn']and v['velocity']['oneDType']==0 and v['velocity']['_value']==0 and v['lifeTime']['oneDType']==0 and v['lifeTime']['_value']>0 and all(k=='hasUVEffector'for k in flags)
   if not eligible:
    omitted.append({'card':code,'name':name,'component_id':o['id'],'particle_count':v['ParticleNumber'],'flags':flags,'reason':'Dynamic/random/billboard/gradient simulation is not represented by a stationary mesh.'});continue
   assert v['ParticleNumber']==1 and v['loop']and v['m_Enabled']and go['m_IsActive']and v['isShow']
   t=next(pointer(x['component']['m_PathID'])for x in go['m_Component']if'm_LocalPosition'in pointer(x['component']['m_PathID']));m=s.J@ALIGN@matrix(t)
   mesh=s.decode_mesh(c.get(c.find(v['mesh']['m_PathID'])));p=(m@np.c_[mesh['p'],np.ones(len(mesh['p']))].T).T[:,:3]*.01;n=(np.linalg.inv(m[:3,:3]).T@mesh['n'].T).T;length=np.linalg.norm(n,axis=1);n[length<1e-12]=[0,0,1];n/=np.linalg.norm(n,axis=1)[:,None]
   mat=c.get(c.find(v['mater']['m_PathID']));props=mat['m_SavedProperties'];env=dict(props['m_TexEnvs'])['_MainTex'];texp=c.find(env['m_Texture']['m_PathID'],'.png');tb=s.read(texp);h=hashlib.sha256(tb).hexdigest();target=s.AS/f'textures/effects/{h[:20]}.png';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tb);uv=mesh['uv']*[1,-1]+[0,1]
   # UV texture transform is evaluated before runtime normalized offsets.
   uv=uv*[env['m_Scale'][k]for k in'xy']+[env['m_Offset'][k]for k in'xy'];colors=dict(props['m_Colors']);col=2*rgba(colors['_TintColor'])*rgba(colors['_DyeingColor']);col[3]*=v['alpha']['_value']
   blend_dst=dict(props['m_Floats'])['_BlendDst'];assert blend_dst in(1,10),blend_dst
   animation={'duration':v['lifeTime']['_value'],'uv_times':v['_uvkeyFrameLifeTime'],'u':v['_u'],'v':v['_v']}if v['hasUVEffector']else None
   part={'bone':bone,'positions':p.tolist(),'normals':n.tolist(),'uv':uv.tolist(),'triangles':mesh['tri'][:,[0,2,1]].astype(int).tolist(),'render':'additive'if blend_dst==1 else'alpha','texture':f'ssjj_dimension:textures/effects/{h[:20]}.png','color':np.clip(col,0,1).tolist(),'fx_source':{'card':code,'component':o['id'],'name':name},'fx_animation':animation};new.append(part)
   trace.append({'card':code,'name':name,'bone':bone,'component_id':o['id'],'source_mesh':str(c.find(v['mesh']['m_PathID']).relative_to(s.P)),'source_material':str(c.find(v['mater']['m_PathID']).relative_to(s.P)),'source_texture':str(texp.relative_to(s.P)),'source_transform':matrix(t).tolist(),'output_transform':m.tolist(),'aabb_blocks':[p.min(0).tolist(),p.max(0).tolist()],'color':col.tolist(),'uv_animation':animation,'source_depth_write':dict(props['m_Floats']).get('_ZWrite'),'adaptation':'Additive pass depth test preserved, no depth write to avoid occluding the gun; stationary source UV phase uses runtime clock.'})
   textures[str(target.relative_to(s.W))]=h
 source['parts'].extend(new);s.save(s.AS/'source_mesh.json',source)
 # TaCZ muzzle flash may display one texture. Use an original no-sheet
 # source component; all full source emitter curves remain in evidence.
 doc=s.load(FX/(MUZZLE_CARD+'.json'));muzzle=[]
 for o in doc['objects']:
  v=o.get('data',{})
  if'ParticleNumber'not in v:continue
  mat=c.get(c.find(v['mater']['m_PathID']));env=dict(mat['m_SavedProperties']['m_TexEnvs'])['_MainTex'];texp=c.find(env['m_Texture']['m_PathID'],'.png');s.read(texp);muzzle.append({'component_id':o['id'],'texture':str(texp.relative_to(s.P)),'definition':v,'material':mat})
 chosen=muzzle[0];texp=s.P/chosen['texture'];out=s.AS/'textures/flash/dimension_star2.png';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(s.read(texp))
 # Source configuration files are part of reproducible trace.
 for r in c.rows:
  if r['类型']=='TextAsset'and r['原始资源路径'].startswith('assets/res/assets/weapon/nova_shotgun2/effect/'):
   for sp in r['导出文件路径'].split(';'):s.read(s.RAW/sp.replace('\\','/'))
 report={'status':'CONFIRMED_NORMAL_MESH_EFFECTS_IMPORTED_PARTIAL_PARTICLE_SUPPORT','input_sha256':s.INPUTS,'normal_layers':trace,'texture_sha256':textures,'omitted_normal_dynamic_components':omitted,'muzzle':{'card':'E071','selected_component':chosen['component_id'],'original_texture':chosen['texture'],'output':'pack/assets/ssjj_dimension/textures/flash/dimension_star2.png','full_source_components':muzzle,'boundary':'TaCZ single flash texture uses a real normal weak-flash source image; source randomized multi-emitter count, life/size/color/sheet curves are not reproduced by this flash.'},'coordinate_evidence':{'source_root':'Both original normal prefabs rotate 180deg about Y. Source FX X=barrel, Y=up; gun joint X=barrel,Z=up. ALIGN is measured axis permutation; J reflects X once after prefab root matrix.','ALIGN':ALIGN.tolist(),'matrix_api':'Same final positions/normals/UV and UV offset arrays are consumed by production and offline renderer.'},'excluded_conditions':['EffectLevel bit2 high-energy strong muzzle/gaoneng','event100080/100081/100082/100083, status160054, NOVA_SHOTGUN_NOVA_AREA, knife28 are skill/out-of-scope'],'visual_boundary':'Static source alignment and live appearance require native projection and actual-client QA; no exact FX match claimed.'};s.save(s.W/'evidence/source_effects_import.json',report)
 print(json.dumps({'imported_normal_mesh_layers':len(new),'omitted_dynamic_components':len(omitted),'flash':str(out.relative_to(s.W))}))

if __name__=='__main__':main()
