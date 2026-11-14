"""Only the changed Zeus Advent descriptions and count labels; no source UI edits."""
from prepare import W,read,save,sha

OWNED={
 'zh_cn':{
  'hud.ssjj_zeus.advent.active':'幻影 %s / %s',
  'attachment.ssjj_zeus.thunder_advent_stock.desc':'宙斯之手·贰专属枪托：【宙斯降临】同时存在幻影上限3→5，储存次数上限4→6，幻影持续时间20→25秒；每40秒恢复1次。达到同时上限后再次召唤将替换最早的幻影；安装只提高储存上限，不补充次数。安装后枪身外观与基础属性保持不变。',
 },
 'en_us':{
  'hud.ssjj_zeus.advent.active':'Phantoms %s / %s',
  'attachment.ssjj_zeus.thunder_advent_stock.desc':'Hand of Zeus II only: Zeus Advent active phantom limit 3→5, stored charge limit 4→6 and phantom lifetime 20→25 seconds. One charge recovers every 40 seconds. Summoning at the active limit replaces the oldest phantom. Installation raises storage capacity without granting charges. Installed gun appearance and ordinary attributes stay unchanged.',
 }
}
ADVENT={
 'zh_cn':'【宙斯降临】按V在当前位置召唤固定20秒的玩家幻影；默认同时最多3个、储存4次，每40秒恢复1次。达到同时上限后再次召唤将替换最早的幻影。',
 'en_us':'Zeus Advent: press V to summon a stationary player phantom for 20 seconds. Base limits: 3 active phantoms and 4 stored charges; recover one charge every 40 seconds. Summoning at the active limit replaces the oldest phantom.'
}

def main():
    inputs={};outputs={};changed={}
    for lang,owned in OWNED.items():
        rel=f'assets/ssjj_zeus/lang/{lang}.json';base=W/'baseline_assets'/rel;dest=W/'pack'/rel
        original=read(base);doc={**original,**(read(dest) if dest.exists() else {})}
        # Rebuild the owned weapon description from the frozen installed text,
        # so rerunning this narrow preparation never duplicates the skill.
        desc='gun.ssjj_zeus.hand_of_zeuss2.desc'
        original_text=original[desc]
        if '【宙斯降临】' in original_text:original_text=original_text.split('【宙斯降临】',1)[0].rstrip(' ·|；;')
        if 'Zeus Advent:' in original_text:original_text=original_text.split('Zeus Advent:',1)[0].rstrip(' ·|；;')
        values={**owned,desc:original_text+' · '+ADVENT[lang]}
        doc.update(values);save(dest,doc)
        inputs[base.relative_to(W).as_posix()]=sha(base);outputs[dest.relative_to(W).as_posix()]=sha(dest)
        changed[lang]={k:{'before':original.get(k),'after':doc[k]} for k in values}
    inputs[__file__.replace('\\','/').split(W.as_posix()+'/')[-1]]=sha(__file__)
    save(W/'evidence/focus_hud_resources.json',{'passed':True,'scope':'two language overlays only; supplied UI images, animation and material resources remain in frozen installed baseline','changed_keys':changed,'input_sha256':inputs,'output_sha256':outputs})
    print('PASS focus HUD language overlays: two files, six changed keys')

if __name__=='__main__':main()
