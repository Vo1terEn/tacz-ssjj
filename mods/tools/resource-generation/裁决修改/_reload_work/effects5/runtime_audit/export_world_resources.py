from pathlib import Path
import json,hashlib,shutil
import numpy as np
HERE=Path(__file__).resolve().parent;ASSET=HERE.parent/'asset_audit'
OUT=HERE/'resources/assets/judgfire';TEX=OUT/'textures/fx/default_world';TEX.mkdir(parents=True,exist_ok=True)
sources=[]
prefixes={'fire':'0c1d009845f2','glow':'367d3dd1bae6','trail':'a732cc206ee9','spark':'cff78b6eed5a','mark':'0f2f63b71762'}
names={}
for name,prefix in prefixes.items():
 p=next((ASSET/'textures').glob(prefix+'*.png'));target=TEX/(name+'.png');shutil.copy2(p,target)
 names[name]='judgfire:textures/fx/default_world/'+target.name
 sources.append({'target':str(target.relative_to(OUT)),'source':str(p),'source_texture_sha256':p.stem,'png_file_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'operation':'exact original PNG copy'})
d=json.loads((ASSET/'source_native_e017.json').read_text(encoding='utf8'))
n=next(n for n in d['nodes'] if n['name']=='longling');mesh=d['meshes'][n['mesh_keys'][0]]
v=np.asarray(mesh['vertices']);m=np.asarray(n['prefab_world']);v=v@m[:3,:3].T
# Strip the prefab's 100cm placement offset because caller supplies head position.
# Preserve its native local rotation and aspect, then choose a head-sized display.
v=v-(v.max(0)+v.min(0))/2;scale=.75/(v.max(0)-v.min(0)).max();v*=scale
v[:,2]*=-1 # Unity left-handed Y-up to Minecraft right-handed Y-up, exactly once.
config={'vertices':v.tolist(),'uv':mesh['uv0'],'faces':mesh['triangles'],'texture':names['mark'],'source_effect':'E017','source_mesh_key':n['mesh_keys'][0],'coordinate_note':'Native E017 prefab linear transform preserved; centered at caller target head, scale to .75m largest dimension, Unity Z reflected once. UV V reflected only at emission.'}
(OUT/'fx').mkdir(exist_ok=True);(OUT/'fx/default_world_mesh.json').write_text(json.dumps(config,separators=(',',':')),encoding='utf8')
(HERE/'world_resource_provenance.json').write_text(json.dumps({'textures':sources,'mark_mesh':{'native_vertex_count':len(v),'native_triangle_count':len(mesh['triangles']),'preserved':'native faces and UV0; no invented mesh topology','native_prefab_linear_transform':m[:3,:3].tolist(),'world_display_scale':scale,'largest_display_extent_metres':.75},'note':'Visual reconstruction uses original assets. Cross-engine lighting, noise shader and world-space size still require live game comparison.'},ensure_ascii=False,indent=2),encoding='utf8')
print('Exported 5 exact PNGs and original E017 mark mesh; resources are separate from compiled classes.')
