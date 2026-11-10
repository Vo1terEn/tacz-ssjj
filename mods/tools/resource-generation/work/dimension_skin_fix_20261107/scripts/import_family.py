"""Import all tier/skin source meshes and normal effects without changing accepted poses."""
from pathlib import Path
import json,shutil,contextlib,hashlib
import source_import as s
import import_effects as fx
import build_particles as particles
from baseline import W,ROOT,save,sha,read

AS=W/'pack/assets/ssjj_dimension'
SKINS=['base','frozen','neon','royal']
CARDS={'base':[(9,10,11),(35,36,37),(71,72,73)],'frozen':[(95,116,117),(95,140,141),(95,164,165)],'neon':[(183,204,205),(183,228,229),(183,252,253)],'royal':[(271,292,293),(271,316,317),(271,340,341)]}
def rig(raw,prefix,tier):
 return raw/'骨骼动画'/prefix/('nova_shotgun'+tier) if prefix.startswith('skin_') else raw/'骨骼动画'/prefix
def main():
 reference=ROOT/'次元之星/本体/骨骼动画/nova_shotgun2'
 ref=s.Glb(reference/'可导入模型_GLB/view/gun.glb')
 refnodes=[n for n in ref.doc['nodes']if'mesh'not in n]
 refclips={kind:{c['name']:c for c in s.parse_anm(reference/f'原生动画与动作配置/view/{kind}.anm.bytes')['clips']}for kind in ['gun','hand_man']}
 records=[];inputs={}
 for skin in SKINS:
  for i,tier in enumerate(['','1','2']):
   profile='dimension_star'+tier+(''if skin=='base'else'_'+skin)
   raw=ROOT/'次元之星'/('本体'if skin=='base'else'皮肤/'+skin)
   prefix='nova_shotgun'+tier if skin=='base'else'skin_nova_shotgun_'+skin
   source_rig=rig(raw,prefix,tier)
   s.RAW=raw;s.CATALOG_PREFIX=prefix;s.RIG=source_rig;s.INPUTS={}
   g=s.Glb(source_rig/'可导入模型_GLB/view/gun.glb')
   assert [n for n in g.doc['nodes']if'mesh'not in n]==refnodes,profile+' different skeleton'
   equality={kind:{c['name']:c for c in s.parse_anm(source_rig/f'原生动画与动作配置/view/{kind}.anm.bytes')['clips']}==refclips[kind]for kind in refclips}
   assert all(equality.values()),profile+' different source animation'
   if profile=='dimension_star2':
    records.append({'profile':profile,'preserved_accepted_baseline':True,'source_skeleton_equal':True,'decoded_source_animations_equal':equality});inputs.update({k:v for k,v in s.INPUTS.items()if k.startswith("次元之星/")});continue
   temp=W/'generated/imports'/profile;temp.mkdir(parents=True,exist_ok=True)
   s.W=temp;s.AS=temp/'assets';cat=s.Catalog()
   sourcepath=('assets/res/assets/weapon/'+prefix if skin=='base'else'assets/res/assets/weaponskin/'+prefix+'/nova_shotgun'+tier)+'/view/gun.prefab'
   roots=[]
   for p in cat.types['AssetBundle']:
    if p.suffix!='.json':continue
    for path,entry in cat.get(p).get('m_Container',[]):
     if path==sourcepath:roots.append((p.relative_to(raw).parts[2],entry['asset']['m_PathID']))
   assert len(roots)==1,(profile,sourcepath,roots)
   def selected_root(rp):
    r=cat.get(rp);bundle=rp.relative_to(raw).parts[2];go=cat.get(cat.find(r['m_GameObject']['m_PathID'],bundle=bundle))
    t=next(cat.get(cat.find(c['component']['m_PathID'],bundle=bundle))for c in go['m_Component']if'm_LocalPosition'in cat.get(cat.find(c['component']['m_PathID'],bundle=bundle)))
    while t['m_Father']['m_PathID']:t=cat.get(cat.find(t['m_Father']['m_PathID'],bundle=bundle))
    return 'gun'if(bundle,t['m_GameObject']['m_PathID'])==roots[0]else'excluded'
   cat.root=selected_root
   parts,trace,checks,atlas=s.import_mesh(g,cat)
   # A shared mesh can be referenced by several source tier prefabs. All
   # matching gun renderers must agree on active state and material.
   for rec in trace:
    meshid=int(Path(rec['mesh_json']).stem.rsplit('__',1)[1])
    rs=[cat.get(p)for p in cat.types['SkinnedMeshRenderer']if p.suffix=='.json'and cat.get(p)['m_Mesh']['m_PathID']==meshid and cat.root(p)=='gun']
    signatures={(bool(r['m_Enabled']),tuple(m['m_PathID']for m in r['m_Materials']))for r in rs}
    assert len(signatures)==1,(profile,rec['mesh'],signatures)
   muzzle,a,b=CARDS[skin][i];fx.BODY_CARDS=(f'E{a:03d}',f'E{b:03d}');fx.MUZZLE_CARD=f'E{muzzle:03d}';particles.BODY_CARD=f'E{a:03d}'
   with (temp/'import.log').open('w',encoding='utf8')as log,contextlib.redirect_stdout(log):
    fx.main();particles.main()
   mesh=read(s.AS/'source_mesh.json');save(AS/f'source_mesh_{profile}.json',mesh)
   target=AS/f'textures/gun/uv/{profile}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(s.AS/'textures/gun/uv/dimension_star2.png',target)
   particle_data=read(s.AS/'effects/particles.json');particle_data['gun']='ssjj_dimension:dimension_star'+tier
   save(AS/f'effects/particles_{profile}.json',particle_data)
   shutil.copy2(s.AS/'textures/flash/dimension_star2.png',AS/f'textures/flash/{profile}.png')
   for p in(s.AS/'textures/effects').glob('*.png'):shutil.copy2(p,AS/'textures/effects'/p.name)
   imported=read(temp/'evidence/source_effects_import.json');dynamic=read(temp/'evidence/source_particles_import.json')
   record={'profile':profile,'source_rig':str(source_rig.relative_to(ROOT)),'source_prefab':sourcepath,'source_prefab_root':roots[0],'source_skeleton_equal':True,'decoded_source_animations_equal':equality,'mesh_checks':checks,'materials':trace,'atlas':{str(p.relative_to(ROOT)):v for p,v in atlas.items()},'effect_cards':fx.BODY_CARDS,'muzzle_card':fx.MUZZLE_CARD,'normal_mesh_layers':imported['normal_layers'],'particle_emitters':dynamic['emitters'],'particle_count':dynamic['particles'],'particle_residuals':dynamic['adaptations_and_residuals'],'mesh_sha256':sha(AS/f'source_mesh_{profile}.json'),'texture_sha256':sha(target),'input_sha256':{k:v for k,v in s.INPUTS.items()if k.startswith('次元之星/')}}
   save(W/f'evidence/source_{profile}.json',record);records.append(record);inputs.update({k:v for k,v in s.INPUTS.items()if k.startswith("次元之星/")})
   print(profile,'parts',len(mesh['parts']),'emitters',dynamic['emitters'],flush=True)
 save(W/'evidence/source_family.json',{'passed':True,'profiles':records,'input_sha256':inputs,'accepted_pose_policy':'All 12 decoded raw animation clip sets and default skeletons equal the accepted tier II source; reuse accepted animation, grip, camera and third-person correction.','boundary':'Original meshes, texture and normal effect profiles imported. Private-engine particle conventions retain documented adaptations. No full-client pass claimed.'})
 print('PASS twelve tier/skin source mappings and exact animation/skeleton equivalence')
if __name__=='__main__':main()
