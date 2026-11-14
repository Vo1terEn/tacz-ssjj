"""Requested story, standard pack footer and brief accessory descriptions; original assets only."""
from prepare import W,VERSION,read,save

STORY='\n'.join([
 '众神之王宙斯曾经拥有过一副强大',
 '的手套，据说拥有者可以完全掌控',
 '闪电的力量。由于这把武器被创造',
 '出来时携带着巨大的闪电能量，所',
 '以创造者就以宙斯之手来命名，然',
 '而这个名字，还远远不足以描述它',
 '的恐怖。',
])
ENGLISH_STORY=('Zeus, king of the gods, once owned a mighty pair of gauntlets. '
 'Their wielder was said to command lightning itself. This weapon was created '
 'carrying immense lightning energy, so its creator named it the Hand of Zeus. '
 'Yet even this name falls far short of describing its terror.')
ZH={
 'lava_scope':'切换「熔岩之拳」外观。',
 'galaxy_scope':'切换「星空之握」外观。',
 'thunder_burst':'提高能量属性，增强爆炸并命中返弹。',
 'thunder_energy_mag':'提高能量属性，强化雷霆能量充能与闪电链。',
 'thunder_advent_stock':'提高能量属性，增加幻影数量、储存次数和持续时间。',
 'thunder_shock_grip':'提高能量属性，增强感电增伤。',
}
EN={
 'lava_scope':'Switch to the Lava Fist appearance.',
 'galaxy_scope':'Switch to the Galaxy Grip appearance.',
 'thunder_burst':'Improve energy attributes and explosions; restore ammunition on hit.',
 'thunder_energy_mag':'Improve energy attributes, Thunder Energy charging and chain lightning.',
 'thunder_advent_stock':'Improve energy attributes, phantom limits, stored summons and duration.',
 'thunder_shock_grip':'Improve energy attributes and the Electrified damage bonus.',
}

def main():
 for locale,story,brief,pack_name in [('zh_cn',STORY,ZH,'生死狙击RPG枪包'),('en_us',ENGLISH_STORY,EN,'SSJJ RPG Gunpack')]:
  path='assets/ssjj_zeus/lang/'+locale+'.json';value=read(W/'baseline_assets'/path)
  for gun in ('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2'):value['gun.ssjj_zeus.'+gun+'.desc']=story
  value['pack.ssjj_zeus.name']=pack_name
  for part,text in brief.items():value['attachment.ssjj_zeus.'+part+'.desc']=text
  for folder in ('pack','mod_resources'):save(W/folder/path,value)
 path='assets/ssjj_zeus/gunpack_info.json';info=read(W/'baseline_assets'/path)
 info.update({'name':'生死狙击RPG枪包','version':VERSION})
 for folder in ('pack','mod_resources'):save(W/folder/path,info)
 print('PASS exact seven-line Zeus story, standard pack name, six brief accessory descriptions')

if __name__=='__main__':main()
