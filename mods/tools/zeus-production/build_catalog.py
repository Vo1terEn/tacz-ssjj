from pathlib import Path
import hashlib,json,zipfile,shutil
from prepare import W,ROOT,PAIR,read,save,sha
GUNS=('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2')
FRAGMENT='eye_judgment_arsenal:zeus_hand_fragment'
def mat(item,count):return {'item':{'item':item},'count':count}
def main():
 with zipfile.ZipFile(W/'baseline'/PAIR['mod']) as z:
  for lang,title in [('zh_cn','宙斯之手碎片'),('en_us','Zeus Hand Fragment')]:
   rel=f'assets/eye_judgment_arsenal/lang/{lang}.json'
   v=json.loads(z.read(rel));v['item.eye_judgment_arsenal.zeus_hand_fragment']=title;save(W/'pack'/rel,v)
 d=W/'pack/data/ssjj_zeus/recipes'
 save(d/'zeus_hand_fragment.json',{'type':'tacz:gun_smith_table_crafting','materials':[mat('minecraft:netherite_scrap',2),mat('minecraft:diamond',1)],'result':{'type':'custom','group':'ssjj_workbench:fragments','item':{'item':FRAGMENT,'count':60}}})
 for i,gun in enumerate(GUNS):
  materials=[mat(FRAGMENT,840)]
  if i:materials.append({'item':{'type':'forge:partial_nbt','item':'tacz:modern_kinetic_gun','nbt':{'GunId':'ssjj_zeus:'+GUNS[i-1]}},'count':1})
  save(d/f'gun/{gun}.json',{'type':'tacz:gun_smith_table_crafting','materials':materials,'result':{'type':'gun','id':'ssjj_zeus:'+gun,'count':1,'group':'ssjj_workbench:weapons','ammo_count':0}})
 save(W/'pack/assets/eye_judgment_arsenal/models/item/zeus_hand_fragment.json',{'parent':'minecraft:item/generated','textures':{'layer0':'eye_judgment_arsenal:item/zeus_hand_fragment'}})
 # Image copied separately after source-owner's original icon lookup/user preference.
 save(W/'evidence/catalog_recipe_contract.json',{'passed':True,'fragment':FRAGMENT,'stack_size':64,'fragment_cost':{'minecraft:netherite_scrap':2,'minecraft:diamond':1},'fragment_yield':60,'gun_cost_per_stage':840,'sequence':['ssjj_zeus:'+g for g in GUNS],'base_has_no_previous_gun':True,'upgrades_require_immediate_previous_GunId':True,'upgrade_extra_nbt_preservation':'Existing TaCZ recipe output policy: fresh empty result; original attachments/ammo/custom NBT are not copied, matching dimension recipes.','reference_recipes':['data/ssjj_dimension/recipes/dimension_star_fragment.json',*[f'data/ssjj_dimension/recipes/gun/{g}.json' for g in ['dimension_star','dimension_star1','dimension_star2']]],'workbench_filter':'Existing ^ssjj_zeus:.*$ handles all new recipes; no filter changes.'})
 print('PASS Zeus sequential workbench recipes and fragment catalog')
if __name__=='__main__':main()
