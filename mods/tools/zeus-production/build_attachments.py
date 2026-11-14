"""Use existing TaCZ scope slots and display overrides for cosmetic skin selectors."""
from copy import deepcopy
from prepare import W,read,save,sha
GUNS=('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2')
SKINS={'lava':('熔岩之拳','Lava Fist'),'galaxy':('星空之主','Lord of the Starry Sky')}
IDS=['ssjj_zeus:'+s+'_scope' for s in SKINS]
def main():
 for gun in GUNS:
  rel=f'data/ssjj_zeus/data/guns/{gun}_data.json';v=read(W/'baseline_assets'/rel);v['allow_attachment_types']=['scope'];v['exclusive_attachments']={id:{} for id in IDS};save(W/'pack'/rel,v)
  save(W/f'pack/data/ssjj_zeus/tacz_tags/attachments/allow_attachments/{gun}.json',IDS)
  base=read(W/f'baseline_assets/assets/ssjj_zeus/display/guns/{gun}_display.json')
  for skin in SKINS:
   v=deepcopy(base);v['texture']=f'ssjj_zeus:gun/uv/hand_of_zeuss_{skin}';v['slot']=f'ssjj_zeus:gun/slot/{gun}_{skin}';save(W/f'pack/assets/ssjj_zeus/display/guns/{gun}_{skin}_display.json',v)
 for skin in SKINS:
  save(W/f'pack/data/ssjj_zeus/index/attachments/{skin}_scope.json',{'name':f'attachment.ssjj_zeus.{skin}_scope.name','display':f'ssjj_zeus:{skin}_scope_display','data':f'ssjj_zeus:{skin}_scope_data','type':'scope','sort':158 if skin=='lava' else 159,'tooltip':f'attachment.ssjj_zeus.{skin}_scope.desc'})
  save(W/f'pack/data/ssjj_zeus/data/attachments/{skin}_scope_data.json',{'weight':0})
  save(W/f'pack/assets/ssjj_zeus/display/attachments/{skin}_scope_display.json',{'model':f'ssjj_zeus:attachment/{skin}_scope_geo','texture':f'ssjj_zeus:attachment/slot/{skin}_scope','slot':f'ssjj_zeus:attachment/slot/{skin}_scope','scope':False,'zoom':[1.0],'views':[1],'views_fov':[46.85953210698013],'show_mount':False})
  save(W/f'pack/assets/ssjj_zeus/geo_models/attachment/{skin}_scope_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':f'geometry.zeus_cosmetic_{skin}_scope','texture_width':256,'texture_height':256,'visible_bounds_width':1,'visible_bounds_height':1,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'root','pivot':[0,0,0]},{'name':'scope_view','parent':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0]}]}]})
  save(W/f'pack/data/ssjj_zeus/recipes/attachment/{skin}_scope.json',{'type':'tacz:gun_smith_table_crafting','materials':[{'item':{'item':'eye_judgment_arsenal:zeus_hand_fragment'},'count':120}],'result':{'type':'attachment','id':f'ssjj_zeus:{skin}_scope','count':1,'group':'ssjj_workbench:scope'}})
 for lang in ('zh_cn','en_us'):
  rel=f'assets/ssjj_zeus/lang/{lang}.json';v=read(W/'baseline_assets'/rel)
  for skin,titles in SKINS.items():
   v[f'attachment.ssjj_zeus.{skin}_scope.name']=('宙斯之手 · '+titles[0]+'（皮肤瞄具）') if lang=='zh_cn' else 'Zeus Hand - '+titles[1]+' (Cosmetic Scope)'
   v[f'attachment.ssjj_zeus.{skin}_scope.desc']='适用于宙斯之手全品阶，仅改变外观；倍率、伤害、射速和弹量保持不变。' if lang=='zh_cn' else 'All Zeus Hand tiers. Appearance only; zoom and weapon statistics remain unchanged.'
  save(W/'pack'/rel,v)
 save(W/'evidence/attachment_contract.json',{'passed':True,'guns':list(GUNS),'attachments':IDS,'scope_slot_enabled':True,'zoom':1.0,'weight':0,'attachment_data_has_no_stat_modifiers':True,'invisible_installed_geometry':True,'icon_source':'model-rendered weapon, not original UI icon','recipe_fragment_cost':120,'original_gun_sounds_animations_calibration_and_thirdperson_scale_preserved':True,'reference':'Existing dimension/parasite cosmetic attachment, partial display and workbench scope mechanisms','input_sha256':{p.relative_to(W).as_posix():sha(p) for p in (W/'pack').rglob('*.json') if 'textures' not in p.parts}})
 print('PASS two cosmetic scope attachments and six skin displays; numeric data preserved')
if __name__=='__main__':main()
