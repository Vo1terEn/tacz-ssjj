"""Describe the real native configured bonuses without changing attachment appearance/data."""
import json
from prepare import W, read, save, sha

IDS=('thunder_burst','thunder_energy_mag','thunder_advent_stock','thunder_shock_grip')
def main():
    outputs={};inputs=[__import__('pathlib').Path(__file__)]
    for language in ('zh_cn','en_us'):
        rel=f'assets/ssjj_zeus/lang/{language}.json';source=W/'baseline_assets'/rel;inputs.append(source)
        original=read(source);result=dict(original)
        for part in IDS:
            key=f'attachment.ssjj_zeus.{part}.desc';text=result[key]
            if language=='zh_cn':
                text=text.replace('不改变枪体外观和基础属性。','不改变枪体外观。').replace('枪体外观与其他属性保持不变。','枪体外观保持不变。').replace('安装后枪身外观与基础属性保持不变。','安装后枪身外观保持不变。').replace('仅强化感电增伤，','强化感电增伤，')
                text+=' 默认每件能量抗性+250、能量抗性穿透+250、能量强度+250，可在全局配置中修改；仅合法安装并持枪时生效，瞄准镜不计。'
            else:
                text=text.replace('appearance and base stats','appearance').replace('other stats remain unchanged','appearance remains unchanged').replace('Installed gun appearance and ordinary attributes stay unchanged.','Installed gun appearance stays unchanged.').replace('Installed appearance and ordinary attributes stay unchanged.','Installed appearance stays unchanged.')
                text+=' Default per part: +250 energy resistance, +250 energy penetration, +250 energy strength, configurable globally. Requires a legal installation and holding the gun; scopes are excluded.'
            result[key]=text
        assert {k for k in result if result[k]!=original[k]}=={f'attachment.ssjj_zeus.{v}.desc' for v in IDS}
        for folder in ('pack','mod_resources'):
            path=W/folder/rel;save(path,result);outputs[path.relative_to(W).as_posix()]=sha(path)
    save(W/'evidence/attachment_attribute_resources.json',{'passed':True,'exclusive_non_scope_ids':list(IDS),
       'input_sha256':{p.relative_to(W).as_posix():sha(p) for p in inputs},'output_sha256':outputs})
    print('PASS four exclusive-part descriptions; default bonuses labeled configurable; appearance/data unchanged')
if __name__=='__main__':main()
