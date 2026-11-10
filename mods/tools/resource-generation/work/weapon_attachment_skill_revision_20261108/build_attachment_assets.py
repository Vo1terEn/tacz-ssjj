"""Dedicated parts cut from the approved original gun triangles; native TaCZ assets."""
from pathlib import Path
from zipfile import ZipFile
import json,hashlib,io,colorsys
import numpy as np
from PIL import Image,ImageDraw,ImageFont

W=Path(__file__).resolve().parent;ROOT=W.parents[1];R=W/'resources'
N='ssjj_dimension';GUNS=['dimension_star','dimension_star1','dimension_star2']
PARTS={'dimension_muzzle':'muzzle','dimension_grip':'grip','dimension_stock':'stock'}
STONES=['ultimate_upgrade_stone','ability_upgrade_stone','fusion_upgrade_stone']
BOUNDS={'dimension_muzzle':[[-.08,1.40],[-.10,.10],[-.20,.25]],'dimension_grip':[[-.24,.50],[-.10,.10],[-.20,.13]],'dimension_stock':[[-.46,.28],[-.10,.10],[-.02,.30]]}
def sha(raw):return hashlib.sha256(raw).hexdigest()
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n','utf8')
def put(path,raw):p=R/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
def clip(poly,axis,limit,keep_above):
 result=[]
 for a,b in zip(poly,poly[1:]+poly[:1]):
  aa=(a[axis]>=limit)if keep_above else(a[axis]<=limit);bb=(b[axis]>=limit)if keep_above else(b[axis]<=limit)
  if aa:result.append(a)
  if aa!=bb:result.append(a+(b-a)*((limit-a[axis])/(b[axis]-a[axis])))
 return result
def extract(part,bounds):
 original=np.concatenate([part['positions'],part['normals'],part['uv']],axis=1);rows=[];faces=[]
 for face in part['triangles']:
  poly=[original[i].copy()for i in face]
  for axis,(low,high)in enumerate(bounds):
   poly=clip(poly,axis,low,True)if poly else[];poly=clip(poly,axis,high,False)if poly else[]
  if len(poly)<3:continue
  offset=len(rows);rows.extend(poly)
  faces.extend([[offset,offset+i,offset+i+1]for i in range(1,len(poly)-1)])
 data=np.array(rows);assert len(faces)>10
 center=(data[:,:3].min(0)+data[:,:3].max(0))/2;scale=.75/max(np.ptp(data[:,:3],axis=0))
 # Source X is barrel, Z is up, Y is thickness. A native item render uses Y down.
 p=(data[:,:3]-center)*scale;p=p[:,[0,2,1]];p[:,1]*=-1
 n=data[:,3:6][:,[0,2,1]];n[:,1]*=-1;n/=np.maximum(np.linalg.norm(n,axis=1,keepdims=True),1e-12)
 return {'bone':'item_extract','positions':p.tolist(),'normals':n.tolist(),'uv':data[:,6:8].tolist(),'triangles':faces}, {'source_bounds':bounds,'source_center':center.tolist(),'uniform_scale':scale,'triangle_count':len(faces),'vertex_count':len(rows),'operation':'Sutherland-Hodgman clip of original triangles with linearly interpolated UV/normals; recenter and uniform scale; no replacement geometry'}
def render(part,texture,size=256):
 a=np.array(part['positions']);uv=np.array(part['uv']);xy=a[:,:2]*[290,290]+[size/2,size/2]
 depth=np.full((size,size),-1e8);out=np.zeros((size,size,4),np.uint8)
 for face in part['triangles']:
  p=xy[face];aa,b,c=p;low=np.maximum(0,np.floor(p.min(0)).astype(int));high=np.minimum([size-1,size-1],np.ceil(p.max(0)).astype(int));l,t=low;r,bottom=high
  if r<l or bottom<t:continue
  den=(b[1]-c[1])*(aa[0]-c[0])+(c[0]-b[0])*(aa[1]-c[1])
  if abs(den)<1e-9:continue
  yy,xx=np.mgrid[t:bottom+1,l:r+1];xx=xx+.5;yy=yy+.5
  l0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;l1=((c[1]-aa[1])*(xx-c[0])+(aa[0]-c[0])*(yy-c[1]))/den;l2=1-l0-l1
  weights=np.stack([l0,l1,l2],axis=-1);z=weights@a[face,2];mask=(weights.min(-1)>=-1e-7)&(z>depth[t:bottom+1,l:r+1]);tuv=weights@uv[face]
  tx=np.clip((tuv[:,:,0]*(texture.shape[1]-1)).astype(int),0,texture.shape[1]-1);ty=np.clip((tuv[:,:,1]*(texture.shape[0]-1)).astype(int),0,texture.shape[0]-1)
  out[t:bottom+1,l:r+1][mask]=texture[ty,tx][mask];depth[t:bottom+1,l:r+1][mask]=z[mask]
 return Image.fromarray(out)
def recolor(image,hue):
 a=np.asarray(image).copy();count=0
 # Red/pink low-saturation pixels form the existing background/rays. The stone
 # itself is neutral gray/white; alpha and every non-red pixel remain exact.
 for y in range(a.shape[0]):
  for x in range(a.shape[1]):
   r,g,b,alpha=map(int,a[y,x]);h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
   if alpha and s>.12 and(r>g*1.08)and(r>b*1.05)and(h<.10 or h>.94):
    rgb=colorsys.hsv_to_rgb(hue,s,v);a[y,x,:3]=[round(c*255)for c in rgb];count+=1
 return Image.fromarray(a),count
def build():
 records=[];changed=[];languages={locale:{}for locale in ['zh_cn','en_us']}
 with ZipFile(W/'baseline/gunpack.zip')as z:
  meshpath='assets/ssjj_dimension/source_mesh.json';texturepath='assets/ssjj_dimension/textures/gun/uv/dimension_star2.png'
  source=json.loads(z.read(meshpath));part=next(p for p in source['parts']if p['bone']=='WeaponAnchor'and not p.get('render'))
  texraw=z.read(texturepath);texture=np.asarray(Image.open(io.BytesIO(texraw)).convert('RGBA'));put('assets/ssjj_dimension/textures/attachment/extracted_original_gun.png',texraw)
  labels={'dimension_muzzle':('次元之星 · 次元枪口','Dimension Star · Dimension Muzzle','散布再次降低50%；标记持续时间翻倍；弹丸基础伤害3→8。','Halves spread again, doubles mark duration, and raises base pellet damage from 3 to 8.'),'dimension_grip':('次元之星 · 次元握把','Dimension Star · Dimension Grip','一阶及以上；次元领域半径翻倍，持续时间增加1秒。','Tier I or II. Doubles field radius and extends field duration by one second.'),'dimension_stock':('次元之星 · 次元枪托','Dimension Star · Dimension Stock','二阶专属；瞬闪分身触发引爆时立即刷新瞬闪冷却。','Tier II. A detonating dash decoy immediately refreshes dash cooldown.'),'ultimate_upgrade_stone':('次元之星 · 终极强化石','Dimension Star · Ultimate Upgrade Stone','二阶专属；领域眩晕增加0.5秒，领域消失时额外高能引爆一次。','Tier II. Adds 0.5s field stun; a disappearing field detonates once more.'),'ability_upgrade_stone':('次元之星 · 异能强化石','Dimension Star · Ability Upgrade Stone','二阶专属；使用本武器对超负荷敌人造成的伤害提高30%。','Tier II. This weapon deals 30% more damage to overloaded enemies.'),'fusion_upgrade_stone':('次元之星 · 融合强化石','Dimension Star · Fusion Upgrade Stone','二阶专属；同时拥有次元之星终极强化石和异能强化石效果。','Tier II. Combines this weapon\'s Ultimate and Ability Upgrade Stone effects.')}
  for name,kind in PARTS.items():
   selected,record=extract(part,BOUNDS[name]);record.update({'id':N+':'+name,'source_archive_sha256':sha((W/'baseline/gunpack.zip').read_bytes()),'source_mesh':meshpath,'source_mesh_sha256':sha(z.read(meshpath)),'source_texture':texturepath,'source_texture_sha256':sha(texraw)})
   oldmesh=json.loads(z.read(f'assets/{N}/source_attachment/{name}.json'))['parts'][0];record['previous_triangle_count']=len(oldmesh['triangles']);record['expanded_triangle_count']=record['triangle_count'];record['source_triangle_expansion_ratio']=record['triangle_count']/max(1,len(oldmesh['triangles']));assert record['source_triangle_expansion_ratio']>1.5
   meshfile=f'assets/{N}/source_attachment/{name}.json';write(R/meshfile,{'parts':[selected]});record['mesh_output']=meshfile;record['mesh_output_sha256']=sha((R/meshfile).read_bytes())
   icon=render(selected,texture);path=f'assets/{N}/textures/attachment/slot/{name}.png';icon.save(R/path if(R/path).parent.exists()else str((R/path).parent.mkdir(parents=True,exist_ok=True)or R/path));record['icon_output']=path;record['icon_sha256']=sha((R/path).read_bytes());records.append(record)
   data={'weight':0}
   if kind=='muzzle':data.update({'damage':{'multiplier':8/3},**{key:{'multiplier':.5}for key in ['inaccuracy','aim_inaccuracy','sneak_inaccuracy','lie_inaccuracy']}})
   write(R/f'data/{N}/data/attachments/{name}_data.json',data)
   write(R/f'assets/{N}/geo_models/attachment/{name}_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+name,'texture_width':1024,'texture_height':1024,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0]}]}]})
   write(R/f'assets/{N}/display/attachments/{name}_display.json',{'model':f'{N}:attachment/{name}_geo','texture':f'{N}:attachment/extracted_original_gun','slot':f'{N}:attachment/slot/{name}','show_muzzle':True,'show_mount':False})
   recipe=json.loads(z.read('data/ssjj_dimension/recipes/attachment/frozen_scope.json'));recipe['result'].update({'id':N+':'+name,'group':'ssjj_workbench:'+kind});write(R/f'data/{N}/recipes/attachment/{name}.json',recipe)
  stonepath='assets/judgfire/textures/attachment/slot/ultimate_upgrade_stone.png';stone=Image.open(io.BytesIO(z.read(stonepath))).convert('RGBA')
  for name,hue in [('ultimate_upgrade_stone',None),('ability_upgrade_stone',1/6),('fusion_upgrade_stone',1/3)]:
   path=R/f'assets/{N}/textures/attachment/slot/{name}.png';path.parent.mkdir(parents=True,exist_ok=True)
   if hue is None:path.write_bytes(z.read(stonepath));n=0
   else:image,n=recolor(stone,hue);image.save(path)
   records.append({'id':N+':'+name,'source_icon':stonepath,'source_sha256':sha(z.read(stonepath)),'output':path.relative_to(R).as_posix(),'sha256':sha(path.read_bytes()),'hue':hue,'changed_red_background_pixels':n,'alpha_preserved':True,'non_red_pixels_preserved':True})
   write(R/f'data/{N}/data/attachments/{name}_data.json',{'weight':0})
   write(R/f'assets/{N}/display/attachments/{name}_display.json',{'model':f'{N}:attachment/dimension_stone_empty_geo','texture':f'{N}:attachment/slot/{name}','slot':f'{N}:attachment/slot/{name}','show_muzzle':True,'show_mount':False})
   recipe=json.loads(z.read('data/judgfire/recipes/ultimate_upgrade_stone.json'));recipe['result']['id']=N+':'+name
   if name.startswith('fusion'):recipe['materials']=[{'item':{'type':'forge:partial_nbt','item':'tacz:attachment','nbt':{'AttachmentId':N+':'+s}},'count':1}for s in STONES[:2]]+[{'item':{'item':'minecraft:nether_star'},'count':1}]
   write(R/f'data/{N}/recipes/attachment/{name}.json',recipe)
  write(R/f'assets/{N}/geo_models/attachment/dimension_stone_empty_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.dimension_stone_empty','texture_width':128,'texture_height':128,'visible_bounds_width':1,'visible_bounds_height':1,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'root','pivot':[0,0,0]}]}]})
  for order,name in enumerate([*PARTS,*STONES]):
   write(R/f'data/{N}/index/attachments/{name}.json',{'name':f'attachment.{N}.{name}.name','tooltip':f'attachment.{N}.{name}.desc','display':f'{N}:{name}_display','data':f'{N}:{name}_data','type':PARTS.get(name,'laser'),'sort':160+order})
   for locale,title,desc in [('zh_cn',labels[name][0],labels[name][2]),('en_us',labels[name][1],labels[name][3])]:languages[locale].update({f'attachment.{N}.{name}.name':title,f'attachment.{N}.{name}.desc':desc})
  for tier,gun in enumerate(GUNS):
   path=f'data/{N}/data/guns/{gun}_data.json';data=json.loads(z.read(path));data['allow_attachment_types']=['scope','muzzle']+(['grip']if tier>=1 else[])+(['stock','laser']if tier>=2 else[])
   allowed=[N+':'+s+'_scope'for s in ['frozen','neon','royal']]+[N+':dimension_muzzle']+([N+':dimension_grip']if tier>=1 else[])+([N+':dimension_stock']+[N+':'+s for s in STONES]+['ssjjrpg:creative_upgrade_stone']if tier>=2 else[])
   # Native attachment modifiers must remain effective; do not shadow muzzle data.
   data['exclusive_attachments']={k:v for k,v in data.get('exclusive_attachments',{}).items()if k in allowed and k.endswith('_scope')}
   write(R/path,data);changed.append(path);tag=f'data/{N}/tacz_tags/attachments/allow_attachments/{gun}.json';write(R/tag,allowed);changed.append(tag)
  for s in ['frozen','neon','royal']:
   path=f'data/{N}/recipes/attachment/{s}_scope.json';assert json.loads(z.read(path))['materials'][0]['count']==120
  write(W/'attachment_language.json',languages)
  write(W/'evidence/source_attachment_assets.json',{'passed':True,'records':records,'source_gun_archive_sha256':sha((W/'baseline/gunpack.zip').read_bytes()),'base_damage_total':30,'pellets':10,'base_damage_per_pellet':3,'muzzle_damage_total':80,'muzzle_damage_per_pellet':8,'scope_recipes_already_120_fragments':True,'modified_existing_data':changed,'allowed_existing_json_fields':{'gun_data':['allow_attachment_types','exclusive_attachments'],'tags':['whole-array']},'no_existing_gun_model_uv_texture_or_sound_changed':True,'dedicated_part_meshes_and_icons_expanded':True,'live_client_test':False})
  contact=Image.new('RGBA',(768,512),(25,29,39,255));draw=ImageDraw.Draw(contact)
  for i,name in enumerate([*PARTS,*STONES]):
   icon=Image.open(R/f'assets/{N}/textures/attachment/slot/{name}.png').convert('RGBA');icon.thumbnail((224,224));x=(i%3)*256+(256-icon.width)//2;y=(i//3)*256+15;contact.alpha_composite(icon,(x,y));draw.text(((i%3)*256+8,(i//3)*256+240),name,fill='white')
  (W/'previews').mkdir(exist_ok=True);contact.save(W/'previews/attachment_contact.png')
  comparison=Image.new('RGBA',(768,540),(25,29,39,255));draw=ImageDraw.Draw(comparison)
  for i,name in enumerate(PARTS):
   for row,raw in enumerate([z.read(f'assets/{N}/textures/attachment/slot/{name}.png'),(R/f'assets/{N}/textures/attachment/slot/{name}.png').read_bytes()]):
    icon=Image.open(io.BytesIO(raw)).convert('RGBA');icon.thumbnail((240,224));comparison.alpha_composite(icon,(i*256+(256-icon.width)//2,row*270+20));draw.text((i*256+8,row*270+250),name+(' before'if row==0 else' expanded original source'),fill='white')
  comparison.save(W/'previews/attachment_source_expansion.png')
 print('PASS six dedicated attachment assets, original extracted triangle models and recipes')
if __name__=='__main__':build()
