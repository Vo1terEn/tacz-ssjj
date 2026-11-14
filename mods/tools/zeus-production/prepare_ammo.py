"""Add Zeus-only ammunition and replace tier I/II pellet clouds with one orb.

The source icon is the first Thunderstorm frame from the original Zeus material;
the compatible 12g UV is recolored from the retained astral-shell UV. This script
only writes the current workspace overlay, not delivery or the game instance.
"""
from pathlib import Path
import colorsys,copy,re
from PIL import Image
from prepare import W,ROOT,read,save,sha

BASE=W/'baseline_assets'
PACK=W/'pack'
AMMO='ssjj_zeus:zeus_thunder'
GUNS=('hand_of_zeuss','hand_of_zeuss1','hand_of_zeuss2')
SOURCE=ROOT/'宙斯、聚能/宙斯之手/完整源素材/武器/acg_hand_of_zeuss__acg_hand_of_zeuss/acg_hand_of_zeuss/Textures/Thunderstorm__1898446970839807564.png'

def write(rel,data):
    path=PACK/rel
    save(path,data)
    return path

def png(rel,im):
    path=PACK/rel;path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
    return path

def main():
    outputs=[];aggregations={};inputs={'scripts/prepare_ammo.py':sha(Path(__file__))}
    for gun in GUNS:
        rel=f'data/ssjj_zeus/data/guns/{gun}_data.json'
        old=read(BASE/rel);new=copy.deepcopy(old);new['ammo']=AMMO
        inputs['baseline_assets/'+rel]=sha(BASE/rel)
        if gun!='hand_of_zeuss':
            count=old['bullet']['bullet_amount'];bullet=new['bullet']
            new['rpm']=60;bullet['bullet_amount']=1
            # TaCZ shoot calls applyShotgunDamageSpread(count): the configured
            # damage already represents the sum of all pellets, not each pellet.
            # Keep 28 (28/7 * 7), likewise distance damage 28/21/14.
            bullet['extra_damage']['head_shot_multiplier']=1
            bullet.update({'speed':20,'life':5,'gravity':0,'friction':0})
            new['inaccuracy']={key:0 for key in old['inaccuracy']}
            new['allow_attachment_types']=list(dict.fromkeys(old['allow_attachment_types']+['muzzle']))
            aggregations[gun]={'original_pellets':count,'original_configured_damage':old['bullet']['damage'],
                'original_per_pellet_damage':old['bullet']['damage']/count,
                'native_damage_spread':'EntityKineticBullet.applyShotgunDamageSpread(7) divides configured damage by seven per pellet',
                'orb_damage':bullet['damage'],'original_distance_damage':old['bullet']['extra_damage']['damage_adjust'],
                'orb_distance_damage':bullet['extra_damage']['damage_adjust'],'rpm':new['rpm']}
        outputs.append(write(rel,new))

    outputs.append(write('data/ssjj_zeus/index/ammo/zeus_thunder.json',{
        'name':'ammo.ssjj_zeus.zeus_thunder.name','display':'ssjj_zeus:zeus_thunder_display',
        'stack_size':64,'tooltip':'ammo.ssjj_zeus.zeus_thunder.desc','sort':111}))
    outputs.append(write('assets/ssjj_zeus/display/ammo/zeus_thunder_display.json',{
        'model':'tacz:ammo/12g','texture':'ssjj_zeus:ammo/uv/zeus_thunder',
        'slot':'ssjj_zeus:ammo/slot/zeus_thunder','tracer_color':'#63DCFF',
        'shell':{'model':'tacz:shell/12g_shell','texture':'tacz:shell/12g_shell'},
        'transform':{'scale':{'thirdperson':[0.6,0.6,0.6],'ground':[0.6,0.6,0.6],'fixed':[1.2,1.2,1.2]}}}))
    outputs.append(write('data/ssjj_zeus/recipes/ammo/zeus_thunder.json',{
        'type':'tacz:gun_smith_table_crafting','materials':[
            {'item':{'item':'minecraft:copper_ingot'},'count':1},
            {'item':{'item':'minecraft:redstone'},'count':1},
            {'item':{'item':'minecraft:amethyst_shard'},'count':1}],
        'result':{'type':'ammo','id':AMMO,'count':30,'group':'ssjj_workbench:ammo'}}))

    localized={
        'zh_cn':{
            'ammo.ssjj_zeus.zeus_thunder.name':'宙斯之雷',
            'ammo.ssjj_zeus.zeus_thunder.desc':'宙斯之手全品阶专属弹药；壹阶、贰阶发射雷球，60 RPM，命中爆炸，范围 1×1×1。',
            'attachment.ssjj_zeus.thunder_burst.name':'雷霆爆裂',
            'attachment.ssjj_zeus.thunder_burst.desc':'宙斯之手壹阶、贰阶专属枪口：爆炸范围1.5×1.5×1.5，伤害×1.5；每命中一个有效目标回复1发子弹，至弹匣容量上限。',
            'gun.ssjj_zeus.hand_of_zeuss.desc':'基础霰弹枪 · 12发 · 每发7枚弹丸 · 宙斯之雷专属弹药',
            'gun.ssjj_zeus.hand_of_zeuss1.desc':'一阶雷球霰弹枪 · 12发 · 60 RPM · 单发28基础伤害 · 命中爆炸，范围1×1×1',
            'gun.ssjj_zeus.hand_of_zeuss2.desc':'二阶雷球霰弹枪 · 12发 · 60 RPM · 单发28基础伤害 · 命中爆炸，范围1×1×1'},
        'en_us':{
            'ammo.ssjj_zeus.zeus_thunder.name':'Zeus Thunder',
            'ammo.ssjj_zeus.zeus_thunder.desc':'Exclusive to all Hand of Zeus tiers. Tiers I/II fire one thunder orb at 60 RPM; impact blast: 1 x 1 x 1.',
            'attachment.ssjj_zeus.thunder_burst.name':'Thunder Burst',
            'attachment.ssjj_zeus.thunder_burst.desc':'Hand of Zeus I/II only. Blast: 1.5 x 1.5 x 1.5; damage x1.5. Each valid victim hit restores one round, up to magazine capacity.',
            'gun.ssjj_zeus.hand_of_zeuss.desc':'12-round shotgun · 7 pellets per shot · Zeus Thunder ammunition',
            'gun.ssjj_zeus.hand_of_zeuss1.desc':'Tier I thunder-orb shotgun · 12 rounds · 60 RPM · 28 base damage · 1 x 1 x 1 impact blast',
            'gun.ssjj_zeus.hand_of_zeuss2.desc':'Tier II thunder-orb shotgun · 12 rounds · 60 RPM · 28 base damage · 1 x 1 x 1 impact blast'}}
    for lang,updates in localized.items():
        rel=f'assets/ssjj_zeus/lang/{lang}.json';data=read(BASE/rel);data.update(updates)
        outputs.append(write(rel,data));inputs['baseline_assets/'+rel]=sha(BASE/rel)

    # Original source is a 3x2 atlas. The first 341x256 tile has transparent edges.
    with Image.open(SOURCE) as source:
        tile=source.convert('RGBA').crop((0,0,341,256));tile=tile.crop(tile.getbbox())
        tile.thumbnail((112,112),Image.Resampling.LANCZOS)
        icon=Image.new('RGBA',(128,128));icon.alpha_composite(tile,((128-tile.width)//2,(128-tile.height)//2))
    outputs.append(png('assets/ssjj_zeus/textures/ammo/slot/zeus_thunder.png',icon))
    uvrel='assets/ssjj_dimension/textures/ammo/uv/astral_shell.png';inputs['baseline_assets/'+uvrel]=sha(BASE/uvrel)
    with Image.open(BASE/uvrel) as image:
        uv=image.convert('RGBA');pixels=[]
        for r,g,b,a in [uv.getpixel((x,y)) for y in range(uv.height) for x in range(uv.width)]:
            h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
            # Shift purple shell markings to electrical cyan, preserve neutral/brass parts.
            if .61<h<.9 and s>.1:
                r,g,b=[round(c*255) for c in colorsys.hsv_to_rgb(.53,s,v)]
            pixels.append((r,g,b,a))
        uv.putdata(pixels)
    outputs.append(png('assets/ssjj_zeus/textures/ammo/uv/zeus_thunder.png',uv))

    tests=[]
    def check(name,condition):
        assert condition,name;tests.append({'name':name,'passed':True})
    for gun in GUNS:
        rel=f'data/ssjj_zeus/data/guns/{gun}_data.json';old=read(BASE/rel);new=read(PACK/rel)
        check(gun+' exclusive ammo',new['ammo']==AMMO)
        if gun=='hand_of_zeuss':
            old['ammo']=AMMO;check('base tier only changes ammunition',new==old)
        else:
            count=old['bullet']['bullet_amount']
            check(gun+' single projectile at 60 RPM',new['rpm']==60 and new['bullet']['bullet_amount']==1)
            check(gun+' preserves actual total pellet damage',new['bullet']['damage']==old['bullet']['damage']/count*count)
            check(gun+' preserves total pellet damage at every distance',all(a['distance']==b['distance'] and b['damage']==a['damage']/count*count for a,b in zip(old['bullet']['extra_damage']['damage_adjust'],new['bullet']['extra_damage']['damage_adjust'])))
            check(gun+' no gravity or friction',new['bullet']['gravity']==new['bullet']['friction']==0)
            check(gun+' follows crosshair without spread',all(value==0 for value in new['inaccuracy'].values()))
            check(gun+' no direct-hit headshot multiplier',new['bullet']['extra_damage']['head_shot_multiplier']==1)
            check(gun+' allows muzzle slot',new['allow_attachment_types']==list(dict.fromkeys(old['allow_attachment_types']+['muzzle'])))
            check(gun+' other gun data preserved',{k:v for k,v in old.items() if k not in ('ammo','rpm','bullet','inaccuracy','allow_attachment_types')}=={k:v for k,v in new.items() if k not in ('ammo','rpm','bullet','inaccuracy','allow_attachment_types')})
            check(gun+' other bullet properties preserved',{k:v for k,v in old['bullet'].items() if k not in ('bullet_amount','extra_damage','speed','life','gravity','friction')}=={k:v for k,v in new['bullet'].items() if k not in ('bullet_amount','extra_damage','speed','life','gravity','friction')})
    recipe=read(PACK/'data/ssjj_zeus/recipes/ammo/zeus_thunder.json')
    check('craft thirty ammo',recipe['result']=={'type':'ammo','id':AMMO,'count':30,'group':'ssjj_workbench:ammo'})
    filters=read(BASE/'data/ssjj_workbench/recipe_filters/rongguang.json')
    check('visible on Rongguang workbench',any(re.match(pattern,'ssjj_zeus:zeus_thunder') for pattern in filters['whitelist']) and not any(re.match(pattern,'ssjj_zeus:zeus_thunder') for pattern in filters['blacklist']))
    changed_keys=set(localized['zh_cn'])
    for lang in localized:
        rel=f'assets/ssjj_zeus/lang/{lang}.json';old=read(BASE/rel);new=read(PACK/rel)
        check(lang+' preserves unrelated translations',all(k in new and new[k]==v for k,v in old.items() if k not in changed_keys))
        check(lang+' no existing translation removed',set(old)<=set(new))
    check('orb icon nonempty transparent 128px',icon.size==(128,128) and icon.getbbox() is not None and icon.getpixel((0,0))[3]==0)
    check('compatible native UV dimensions preserved',uv.size==Image.open(BASE/uvrel).size)
    check('all output JSON parses',all(isinstance(read(p),dict) for p in outputs if p.suffix=='.json'))
    allguns=list((BASE/'data').glob('*/data/guns/*.json'))
    check('no other gun accepts exclusive ammo',not [str(p.relative_to(BASE)) for p in allguns if not p.stem.startswith('hand_of_zeuss') and read(p).get('ammo')==AMMO])
    report={'passed':True,'ammo':AMMO,'craft':recipe,'gun_aggregation':aggregations,
        'icon_source':{'path':str(SOURCE.relative_to(ROOT)),'sha256':sha(SOURCE),'atlas_grid':[3,2],'tile':[0,0,341,256],'no_ai_generation':True},
        'model':'Retained native tacz:ammo/12g with compatible astral-shell UV recolored cyan',
        'input_sha256':inputs,'output_sha256':{str(p.relative_to(W)):sha(p) for p in outputs},'checks':tests,'live_client_test':False}
    save(W/'evidence/ammo_resource_check.json',report)
    print('PASS Zeus Thunder resources: '+str(len(outputs))+' outputs, '+str(len(tests))+' checks')
if __name__=='__main__':main()
