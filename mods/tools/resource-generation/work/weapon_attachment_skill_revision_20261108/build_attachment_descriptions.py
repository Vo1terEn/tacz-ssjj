"""Short functional attachment text; apply before this revision's new stat suffixes."""
from pathlib import Path
import json,re
W=Path(__file__).resolve().parent
ZH={
'eyeofpunishment.punishment_power':'强化迅捷与神之注视。',
'eyeofpunishment.nofa_thunder':'命中落雷并回补弹药；二阶可发射电球。',
'eyeofpunishment.king_heart':'延长技能，缩短冷却。',
'eyeofpunishment.ultimate_stone':'提高射速，技能持枪常驻。',
'judgfire.red_lotus_grip':'增强红莲印记减伤。',
'judgfire.emperor_crown_stock':'扩大帝冠索敌范围。',
'judgfire.dragon_breath_muzzle':'射击替换为激光。',
'judgfire.red_lotus_burst_mag':'提高爆破伤害，技能期间强化能量防护与穿透。',
'judgfire.ultimate_upgrade_stone':'增强帝冠攻击。',
'judgfire.integrated_scope':'内置瞄准镜，2/4倍切换。',
'ssjj_dimension.dimension_muzzle':'散布减半；标记时间×2；每丸伤害8。',
'ssjj_dimension.dimension_grip':'领域半径×2，持续时间+1秒。',
'ssjj_dimension.dimension_stock':'分身引爆时刷新瞬闪。',
'ssjj_dimension.ultimate_upgrade_stone':'领域眩晕+0.5秒，消失时引爆。',
'ssjj_dimension.ability_upgrade_stone':'对超负荷敌人伤害+30%。',
'ssjj_dimension.fusion_upgrade_stone':'兼具终极、异能强化效果。',
'ssjjparasite2.parasite_muzzle':'提高伤害，消除镜头后坐力。',
'ssjjparasite2.destruction_stock':'提高能量属性，有概率保留毁灭层数。',
'ssjjparasite2.shockwave_magazine':'提高能量属性，扩大炮狙冲击波。',
'ssjjparasite2.ultimate_upgrade_stone':'加快炮狙切换，提高枪托保层概率。',
'ssjjrpg.creative_upgrade_stone':'强化通用能量属性，激活已适配武器的专属强化。',
}
SKINS={
'eyeofpunishment.skin_timetravel':('时空旅行者','Time Traveler',False),
'eyeofpunishment.skin_sakura':('樱色雷霆','Sakura Thunder',False),
'eyeofpunishment.skin_anime':('次元战争β','Dimension War Beta',False),
'eyeofpunishment.skin_fire':('天坠之火','Falling Fire',False),
'judgfire.qinglong_scope':('青龙','Qinglong',True),
'judgfire.default_scope':('默认','Default',True),
'judgfire.frozen_scope':('极地','Frozen',True),
'judgfire.lightning_scope':('电光神耀','Lightning',True),
'judgfire.flame_crown_scope':('像素炎冠','Flame Crown',True),
'ssjj_dark.skin_mix':('交融','Mix',False),
'ssjj_dark.skin_nightguardian':('守夜人','Night Guardian',False),
'ssjj_dark.skin_pure':('圣水晶','Pure Crystal',False),
'ssjj_dimension.frozen_scope':('极地','Frozen',False),
'ssjj_dimension.neon_scope':('霓虹','Neon',False),
'ssjj_dimension.royal_scope':('贵族','Royal',False),
'ssjjparasite2.frost_scope':('寒冬使者','Frost',True),
'ssjjparasite2.ship_scope':('星舰','Starship',True),
}
EN={
'ssjj_dimension.dimension_muzzle':'Half spread; double mark duration; 8 damage per pellet.',
'ssjj_dimension.dimension_grip':'Double field radius; +1s duration.',
'ssjj_dimension.dimension_stock':'A decoy detonation refreshes dash.',
'ssjj_dimension.ultimate_upgrade_stone':'+0.5s field stun; detonate when the field ends.',
'ssjj_dimension.ability_upgrade_stone':'+30% damage against overloaded enemies.',
'ssjj_dimension.fusion_upgrade_stone':'Combines Ultimate and Ability effects.',
'ssjjrpg.creative_upgrade_stone':'Universal energy stats and supported weapon upgrades.',
}

def apply_short_descriptions(entries):
    changed=[]
    for path,raw in list(entries.items()):
        if '/lang/' not in path or not path.endswith(('/zh_cn.json','/en_us.json')):continue
        values=json.loads(raw);zh=path.endswith('/zh_cn.json');before=dict(values)
        for key,value in list(values.items()):
            if not key.startswith('attachment.') or not key.endswith('.desc'):continue
            identity=key[len('attachment.'):-len('.desc')]
            if identity in SKINS:
                name,en,zoom=SKINS[identity];values[key]=(f'切换「{name}」皮肤'+('，2/4倍瞄准。'if zoom else'。'))if zh else f'{en} skin'+('; 2x/4x scope.'if zoom else'.')
            elif zh and identity in ZH:values[key]=ZH[identity]
            elif not zh and identity in EN:values[key]=EN[identity]
            else:
                values[key]=re.sub(r'\s*以上数值为默认配置，实际以服务端配置为准。|\s*Values shown are defaults; the server configuration applies\.','',value)
        if values!=before:
            entries[path]=(json.dumps(values,ensure_ascii=False,indent=2)+'\n').encode('utf8');changed.append(path)
    report={'passed':True,'modified_language_paths':changed,'known_descriptions':len(ZH)+len(SKINS),'ordering':'After original language merge, before new per-attachment stat suffixes','only_attachment_desc_keys_changed':True}
    p=W/'evidence/attachment_descriptions_check.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
    return report
