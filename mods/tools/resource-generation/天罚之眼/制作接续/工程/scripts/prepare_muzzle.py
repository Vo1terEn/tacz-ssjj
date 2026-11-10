"""Rebuild the powered muzzle from the source E014 meshes, UVs and textures."""
from pathlib import Path
import json,hashlib,runpy,shutil
E=Path(__file__).resolve().parents[1];P=E.parent;R=P.parent
AS=E/'gunpack/assets/eyeofpunishment';W=P/'work/muzzle_reference'
runpy.run_path(str(E/'scripts/prepare_muzzle_native.py'),run_name='__main__')
data=json.loads((W/'native_definition.json').read_text('utf-8'))
passes=[{k:v for k,v in p.items() if k!='source'} for p in data['passes']]
emitters=[{k:v for k,v in p.items() if k!='source'} for p in data['emitters']]
(AS/'rig/muzzle_cavity.json').write_text(json.dumps({'passes':passes,'emitters':emitters},ensure_ascii=False,indent=2),'utf-8')
for p in passes+emitters:shutil.copy2(W/(p['texture']+'.png'),AS/'textures/fx'/(p['texture']+'.png'))
shader=R/'本体/共享依赖素材/shared/8851d5fcc4/结构数据_JSON/Shader/Shader__2053726950777528071.json'
evidence={
 'version':'1.15.0','effect':'E014 / leiting_qiangti_2','coordinates':data['coordinates'],
 'layers':[{k:v for k,v in p.items() if k not in ['mesh','source']}|{'mesh':p['source']['mesh'],'source_texture':p['source']['texture'],'source_particle':p['source']['particle'],'source_material':p['source']['material']} for p in data['passes']],
 'shader':{'source':str(shader.relative_to(R)),'sha256':hashlib.sha256(shader.read_bytes()).hexdigest(),'name':'Particles/SSJJ_NewParticles','blend_src':5,'blend_dst':'material _BlendDst: di/s=10, wen=1','cull':0,'ztest':4,'zwrite':0,'polygon_offset':[-1,-1],'color_mask':'RGB','fragment':'2 * vertexRGBA * TintColor(.5,.5,.5,.5) * textureRGBA; RGB *= DyeingColor(1,1,1). No discard, no premultiplication, no arbitrary gain.'},
 'binding':json.loads((W/'source_binding_evidence.json').read_text('utf-8')) if (W/'source_binding_evidence.json').exists() else {},
 'emitters':data['emitters'],
 'implementation':'Original 28-triangle mesh per layer, original UVs/texture bytes, original di/s/wen blending and tint. Source variable .3-.5s s lifetime sampled deterministically; all12 cells and source black/white gradient; wen U -1/3 per second. Skill-relative synchronized clock. Receiver depth submitted once before native layers, no forced far-bar redraw.',
 'verification_limitations':['Minecraft and the reference game have different camera, fog and color pipelines; offline checks do not establish pixel identity.','Source attachment root180Y is excluded to match existing approved weapon coordinates, backed by forward mesh bounds and source weapon anchor.','In-game first/third-person visual acceptance remains required.']
}
(P/'文档/灰白枪口特效来源与挂点.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),'utf-8')
print('E014 source muzzle: 3 x28 triangles; unchanged source textures; native alpha/additive layers')
