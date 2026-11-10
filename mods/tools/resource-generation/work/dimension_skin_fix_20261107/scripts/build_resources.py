import os
from pathlib import Path
import json,copy,shutil
from PIL import Image,ImageDraw,ImageFont
import numpy as np
from baseline import W,ROOT,VERSION,read,save,sha
from cpu_render import render
AS=W/'pack/assets/ssjj_dimension';DATA=W/'pack/data/ssjj_dimension'
GUNS=['dimension_star','dimension_star1','dimension_star2'];SKINS=['frozen','neon','royal']
def main():
 baseline=read(AS/'display/guns/dimension_star2_display.json');geo=read(AS/'geo_models/gun/dimension_star2_geo.json')
 gun_data=read(DATA/'data/guns/dimension_star2_data.json');gun_index=read(DATA/'index/guns/dimension_star2.json')
 views=read(ROOT/'work/dimension_continuity_view_20261107/evidence/native_view_chain/stand_camera_vertices.json')
 baseparts={p['bone']:p for p in read(AS/'source_mesh.json')['parts']if not p.get('render')}
 focal=540/np.tan(np.radians(read(AS/'view_calibration.json')['fitted_vertical_fov_degrees']/2))
 font=ImageFont.truetype(os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),19)
 boards={v:Image.new('RGB',(1920,990),(25,29,39))for v in ['hip','refit','fixed']};icon_source={};report=[]
 for tier,gun in enumerate(GUNS):
  data=copy.deepcopy(gun_data);data['allow_attachment_types']=['scope'];data['exclusive_attachments']={f'ssjj_dimension:{s}_scope':{}for s in SKINS}
  save(DATA/f'data/guns/{gun}_data.json',data)
  save(DATA/f'tacz_tags/attachments/allow_attachments/{gun}.json',[f'ssjj_dimension:{s}_scope'for s in SKINS])
  if tier<2:
   idx=copy.deepcopy(gun_index);idx.update(name=f'gun.ssjj_dimension.{gun}.name',display=f'ssjj_dimension:{gun}_display',data=f'ssjj_dimension:{gun}_data',sort=138+tier,tooltip=f'gun.ssjj_dimension.{gun}.desc');save(DATA/f'index/guns/{gun}.json',idx)
   save(DATA/f'recipes/gun/{gun}.json',{'type':'tacz:gun_smith_table_crafting','materials':[{'item':{'item':item},'count':count}for item,count in [('minecraft:iron_ingot',16+8*tier),('minecraft:gold_ingot',4+2*tier),('minecraft:ender_pearl',2+tier)]],'result':{'type':'gun','id':'ssjj_dimension:'+gun,'count':1}})
  for col,skin in enumerate(['base',*SKINS]):
   key=gun+(''if skin=='base'else'_'+skin)
   if key!='dimension_star2':
    g=copy.deepcopy(geo);g['minecraft:geometry'][0]['description']['identifier']='geometry.'+key;save(AS/f'geo_models/gun/{key}_geo.json',g)
    display=copy.deepcopy(baseline);display.update(model=f'ssjj_dimension:gun/{key}_geo',texture=f'ssjj_dimension:gun/uv/{key}',hud=f'ssjj_dimension:gun/hud/{key}',slot=f'ssjj_dimension:gun/slot/{key}')
    display['muzzle_flash']['texture']=f'ssjj_dimension:flash/{key}';save(AS/f'display/guns/{key}_display.json',display)
   mesh=read(AS/('source_mesh.json'if key=='dimension_star2'else f'source_mesh_{key}.json'))
   body=[p for p in mesh['parts']if not p.get('render')]
   # Source bone/position correspondence permits using the accepted native
   # camera-space dump; skin UVs and source atlas are selected independently.
   for p in body:assert np.max(abs(np.array(p['positions'])-np.array(baseparts[p['bone']]['positions'])))<1e-6,key
   tex=np.asarray(Image.open(AS/f'textures/gun/uv/{key}.png').convert('RGB'));images={}
   for view in boards:
    by={p['bone']:p['camera_space_positions']for p in next(x for x in views if x['view']==view)['parts']}
    im=render([{**p,'positions':by[p['bone']]}for p in body],tex,focal);images[view]=im
    panel=im.resize((480,270),Image.Resampling.LANCZOS)
    if view=='hip':panel=im.crop((900,400,1920,1080)).resize((480,270),Image.Resampling.LANCZOS)
    boards[view].paste(panel,(col*480,tier*310+55));ImageDraw.Draw(boards[view]).text((col*480+12,tier*310+20),['基础版','壹阶','贰阶'][tier]+' / '+skin,font=font,fill='white')
   arr=np.array(images['refit']);mask=np.any(arr!=[25,29,39],axis=2);ys,xs=np.where(mask);assert len(xs)>100
   icon=images['refit'].convert('RGBA');alpha=Image.fromarray((mask*255).astype('uint8'));icon.putalpha(alpha);icon=icon.crop((xs.min(),ys.min(),xs.max()+1,ys.max()+1));icon.thumbnail((470,230),Image.Resampling.LANCZOS)
   tile=Image.new('RGBA',(512,256));tile.alpha_composite(icon,((512-icon.width)//2,(256-icon.height)//2))
   for kind in ['slot','hud']:
    p=AS/f'textures/gun/{kind}/{key}.png';p.parent.mkdir(parents=True,exist_ok=True)
    if key!='dimension_star2':tile.save(p)
   if tier==2:icon_source[skin]=tile
   report.append({'profile':key,'source_body_positions_equal_to_accepted':True,'source_texture_sha256':sha(AS/f'textures/gun/uv/{key}.png'),'shared_accepted_animation':'dimension_star2','geometry_sha256':sha(AS/f'geo_models/gun/{key}_geo.json')})
 for i,skin in enumerate(SKINS):
  save(DATA/f'index/attachments/{skin}_scope.json',{'name':f'attachment.ssjj_dimension.{skin}_scope.name','display':f'ssjj_dimension:{skin}_scope_display','data':f'ssjj_dimension:{skin}_scope_data','type':'scope','sort':140+i})
  save(DATA/f'data/attachments/{skin}_scope_data.json',{'weight':0})
  save(AS/f'display/attachments/{skin}_scope_display.json',{'model':f'ssjj_dimension:attachment/{skin}_scope_geo','texture':f'ssjj_dimension:attachment/slot/{skin}','slot':f'ssjj_dimension:attachment/slot/{skin}','scope':False,'zoom':[1.0],'views':[1],'views_fov':[46.474140767219254],'show_mount':False})
  save(AS/f'geo_models/attachment/{skin}_scope_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.dimension_'+skin+'_scope','texture_width':512,'texture_height':256,'visible_bounds_width':4,'visible_bounds_height':4,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'root','pivot':[0,0,0]},{'name':'scope_view','parent':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0],'cubes':[{'origin':[-6,-3,0],'size':[12,6,0],'uv':{'north':{'uv':[0,0],'uv_size':[512,256]},'south':{'uv':[512,0],'uv_size':[-512,256]}}}]}]}]})
  p=AS/f'textures/attachment/slot/{skin}.png';p.parent.mkdir(parents=True,exist_ok=True);icon_source[skin].save(p)
  save(DATA/f'recipes/attachment/{skin}_scope.json',{'type':'tacz:gun_smith_table_crafting','materials':[{'item':{'item':m},'count':c}for m,c in [('minecraft:gold_ingot',4),('minecraft:glass',2),(['minecraft:packed_ice','minecraft:amethyst_shard','minecraft:lapis_lazuli'][i],4)]],'result':{'type':'attachment','id':f'ssjj_dimension:{skin}_scope','count':1}})
 for lang in ['zh_cn','en_us']:
  loc=read(AS/f'lang/{lang}.json')
  for i,gun in enumerate(GUNS):
   loc[f'gun.ssjj_dimension.{gun}.name']=['次元之星','次元之星·壹','次元之星·贰'][i]if lang=='zh_cn'else['Dimension Star','Dimension Star I','Dimension Star II'][i]
   loc[f'gun.ssjj_dimension.{gun}.desc']='霰弹枪 · 12发12G · 专属瞄准镜切换皮肤 · 右键近战'if lang=='zh_cn'else'12-round shotgun | exclusive scope selects skin | RMB melee'
  for skin in SKINS:loc[f'attachment.ssjj_dimension.{skin}_scope.name']=f'次元之星专属瞄准镜 · {skin}'if lang=='zh_cn'else'Dimension Star exclusive scope · '+skin
  loc['pack.ssjj_dimension.name']='次元之星系列'if lang=='zh_cn'else'Dimension Star family';save(AS/f'lang/{lang}.json',loc)
 for view,board in boards.items():
  ImageDraw.Draw(board).text((12,948),'离线原生视图链投影 / '+view+'；使用各皮肤原始贴图，未模拟游戏光照、手臂与特效。',font=font,fill=(190,205,220));board.save(W/f'previews/family_{view}.png')
 save(W/'evidence/resources.json',{'passed':True,'profiles':report,'exclusive_scopes':SKINS,'right_click':'existing 2-damage melee retained; scopes are skin selectors, no ADS restored','source_tier_stats':'All source variants have 12 rounds and 300ms interval. Other combat parameters inherit accepted tier II adaptation; no new tier damage/skill design.','source_audio':'Base/I/II WAV fire, raise and reload SHA match exactly; reuse existing encoded sounds and events.','recipes':'New base and I use separate provisional workstation recipes; no automatic tier conversion or NBT-destructive upgrade recipe.','native_baseline_view_sha256':sha(ROOT/'work/dimension_continuity_view_20261107/evidence/native_view_chain/stand_camera_vertices.json'),'live_client_test':False})
 print('PASS 3 guns, 12 displays, 3 exclusive scopes, source textures, recipes, localization and offline previews')
if __name__=='__main__':main()
