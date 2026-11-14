"""Extract the supplied tier-II foregrip. No invented geometry, texture or normal-stat bonus."""
from pathlib import Path
import re
import numpy as np
from PIL import Image,ImageDraw
from prepare import W,read,save,sha
from prepare_magazine import render

BASE=W/'baseline_assets';PACK=W/'pack';ID='ssjj_zeus:thunder_shock_grip'
BOUNDS=[[.12,.35],[-.034145,.034145],[-.078413,.015]]

def clip(poly,axis,limit,above):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        aa=a[axis]>=limit if above else a[axis]<=limit
        bb=b[axis]>=limit if above else b[axis]<=limit
        if aa:out.append(a)
        if aa!=bb:out.append(a+(b-a)*((limit-a[axis])/(b[axis]-a[axis])))
    return out

def extract(part):
    original=np.concatenate([part['positions'],part['normals'],part['uv']],axis=1);rows=[];faces=[]
    for face in part['triangles']:
        poly=[original[i].copy() for i in face]
        for axis,(low,high) in enumerate(BOUNDS):
            poly=clip(poly,axis,low,True) if poly else []
            poly=clip(poly,axis,high,False) if poly else []
        if len(poly)<3:continue
        offset=len(rows);rows.extend(poly)
        faces.extend([[offset,offset+i,offset+i+1] for i in range(1,len(poly)-1)])
    data=np.asarray(rows);assert len(faces)>30
    low=data[:,:3].min(0);high=data[:,:3].max(0);center=(low+high)/2;scale=.75/max(high-low)
    p=(data[:,:3]-center)*scale;p=p[:,[0,2,1]];p[:,1]*=-1
    n=data[:,3:6][:,[0,2,1]];n[:,1]*=-1
    n/=np.maximum(np.linalg.norm(n,axis=1,keepdims=True),1e-12)
    return {'bone':'item_extract','positions':p.tolist(),'normals':n.tolist(),'uv':data[:,6:8].tolist(),'triangles':faces},{
        'source_joint':'WeaponAnchor3','source_bounds':BOUNDS,
        'clipped_source_min':low.tolist(),'clipped_source_max':high.tolist(),
        'source_center':center.tolist(),'uniform_scale':float(scale),'triangle_count':len(faces),
        'item_axes':'(x,y,z) -> (x,-z,y), recenter and uniform scale',
        'method':'Clip original tier-II black/gold lower forward grip triangles; interpolate original UV/normals at cuts. No cap or replacement triangles.'}

def main():
    outputs=[];inputs=[Path(__file__),W/'scripts/prepare_magazine.py']
    def write(rel,data):
        p=PACK/rel;save(p,data);outputs.append(p)
    meshrel='assets/ssjj_zeus/source_mesh.json';texrel='assets/ssjj_zeus/textures/gun/uv/hand_of_zeuss2.png'
    inputs.extend([BASE/meshrel,BASE/texrel]);mesh=read(BASE/meshrel)
    source=next(p for p in mesh['parts'] if p['bone']=='WeaponAnchor3')
    part,record=extract(source)
    write('assets/ssjj_zeus/source_attachment/thunder_shock_grip.json',{'parts':[part],'source':record})
    icon=render(part,np.asarray(Image.open(BASE/texrel).convert('RGBA')))
    p=PACK/'assets/ssjj_zeus/textures/attachment/slot/thunder_shock_grip.png'
    p.parent.mkdir(parents=True,exist_ok=True);icon.save(p);outputs.append(p)
    write('assets/ssjj_zeus/geo_models/attachment/thunder_shock_grip_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.thunder_shock_grip','texture_width':1024,'texture_height':1024,
            'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,0,0]},
        'bones':[{'name':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0]}]}]})
    write('assets/ssjj_zeus/display/attachments/thunder_shock_grip_display.json',{
        'model':'ssjj_zeus:attachment/thunder_shock_grip_geo','texture':'ssjj_zeus:gun/uv/hand_of_zeuss2',
        'slot':'ssjj_zeus:attachment/slot/thunder_shock_grip','show_muzzle':True,'show_mount':False})
    write('data/ssjj_zeus/index/attachments/thunder_shock_grip.json',{
        'name':'attachment.ssjj_zeus.thunder_shock_grip.name','tooltip':'attachment.ssjj_zeus.thunder_shock_grip.desc',
        'display':'ssjj_zeus:thunder_shock_grip_display','data':'ssjj_zeus:thunder_shock_grip_data','type':'grip','sort':154})
    write('data/ssjj_zeus/data/attachments/thunder_shock_grip_data.json',{'weight':0})
    recipe={'type':'tacz:gun_smith_table_crafting','materials':[
        {'item':{'item':'minecraft:copper_ingot'},'count':8},
        {'item':{'item':'minecraft:gold_ingot'},'count':4},
        {'item':{'item':'minecraft:redstone'},'count':8},
        {'item':{'item':'minecraft:amethyst_shard'},'count':4}],
        'result':{'type':'attachment','id':ID,'count':1,'group':'ssjj_workbench:grip'}}
    write('data/ssjj_zeus/recipes/attachment/thunder_shock_grip.json',recipe)
    gunrel='data/ssjj_zeus/data/guns/hand_of_zeuss2_data.json'
    tagrel='data/ssjj_zeus/tacz_tags/attachments/allow_attachments/hand_of_zeuss2.json'
    inputs.extend([BASE/gunrel,BASE/tagrel]);gun=read(BASE/gunrel)
    gun['allow_attachment_types']=list(dict.fromkeys(gun['allow_attachment_types']+['grip']));write(gunrel,gun)
    write(tagrel,list(dict.fromkeys(read(BASE/tagrel)+[ID])))
    translations={
        'zh_cn':{
            'attachment.ssjj_zeus.thunder_shock_grip.name':'雷霆感电握把',
            'attachment.ssjj_zeus.thunder_shock_grip.desc':'宙斯之手·贰专属握把：【感电】增伤15%→30%。仅强化感电增伤，不改变枪体外观和基础属性。',
            'skill.ssjj_zeus.shock.name':'感电',
            'skill.ssjj_zeus.shock.desc':'【雷霆能量】闪电链命中后施加5秒感电，受到宙斯之手的伤害提高15%（专属握把30%），重复命中刷新持续时间，不叠加。闪电链命中已感电目标时，回复【宙斯降临】20%恢复进度（8秒）；首次施加不回复。'},
        'en_us':{
            'attachment.ssjj_zeus.thunder_shock_grip.name':'Thunder Shock Grip',
            'attachment.ssjj_zeus.thunder_shock_grip.desc':'Hand of Zeus II only: increase the Electrified damage bonus from 15% to 30%. Installed appearance and ordinary attributes stay unchanged.',
            'skill.ssjj_zeus.shock.name':'Electrified',
            'skill.ssjj_zeus.shock.desc':'Thunder Energy chains apply Electrified for 5 seconds: Hand of Zeus damage increases by 15% (30% with the grip). Further hits refresh duration without stacking. A chain hit on an already Electrified target restores 20% of Zeus Advent recovery progress (8 seconds); initial application grants no recovery.'}}
    for lang,values in translations.items():
        rel=f'assets/ssjj_zeus/lang/{lang}.json';inputs.append(BASE/rel);doc=read(BASE/rel)
        if (PACK/rel).exists():doc.update(read(PACK/rel))
        doc.update(values);write(rel,doc)
    checks=[]
    def check(name,value):
        assert value,name;checks.append({'name':name,'passed':True})
    check('real source foregrip triangles',len(part['triangles'])>30)
    check('source clipping stays within grip',all(record['clipped_source_min'][i]>=BOUNDS[i][0]-1e-9 and record['clipped_source_max'][i]<=BOUNDS[i][1]+1e-9 for i in range(3)))
    check('tier-II only adds GRIP slot',gun['allow_attachment_types']==read(BASE/gunrel)['allow_attachment_types']+['grip'])
    check('only tier-II allowlist adds grip',read(PACK/tagrel)==read(BASE/tagrel)+[ID])
    check('gun ordinary fields preserved',{k:v for k,v in gun.items() if k!='allow_attachment_types'}=={k:v for k,v in read(BASE/gunrel).items() if k!='allow_attachment_types'})
    check('skill-only grip has no ordinary modifiers',read(PACK/'data/ssjj_zeus/data/attachments/thunder_shock_grip_data.json')=={'weight':0})
    filterrel='data/ssjj_workbench/recipe_filters/rongguang.json';benchrel='data/ssjj_workbench/data/blocks/rongguang.json'
    inputs.extend([BASE/filterrel,BASE/benchrel]);recipe_filter=read(BASE/filterrel);recipe_id='ssjj_zeus:attachment/thunder_shock_grip'
    check('existing workbench filter admits the new recipe',any(re.fullmatch(pattern,recipe_id) for pattern in recipe_filter['whitelist']) and not any(re.fullmatch(pattern,recipe_id) for pattern in recipe_filter['blacklist']))
    check('existing workbench provides GRIP category',any(tab['id']=='ssjj_workbench:grip' for tab in read(BASE/benchrel)['tabs']))
    for other in ['hand_of_zeuss','hand_of_zeuss1']:
        for rel in [f'data/ssjj_zeus/data/guns/{other}_data.json',f'data/ssjj_zeus/tacz_tags/attachments/allow_attachments/{other}.json']:inputs.append(BASE/rel)
        check(other+' rejects grip','grip' not in read(BASE/f'data/ssjj_zeus/data/guns/{other}_data.json')['allow_attachment_types'] and ID not in read(BASE/f'data/ssjj_zeus/tacz_tags/attachments/allow_attachments/{other}.json'))
        check(other+' has no gun data override',not (PACK/f'data/ssjj_zeus/data/guns/{other}_data.json').exists())
    for lang,values in translations.items():
        old=read(BASE/f'assets/ssjj_zeus/lang/{lang}.json');new=read(PACK/f'assets/ssjj_zeus/lang/{lang}.json')
        check(lang+' unrelated text is preserved',all(new.get(k)==v for k,v in old.items() if k not in values))
    check('source icon nonempty and transparent',icon.getbbox() is not None and icon.getpixel((0,0))[3]==0)
    texture=np.asarray(Image.open(BASE/texrel).convert('RGBA'))
    palette={tuple(v) for v in texture.reshape(-1,4)}
    check('every icon pixel is an unchanged source texture sample',all(tuple(v) in palette for v in np.asarray(icon).reshape(-1,4) if v[3]>0))
    preview=Image.new('RGBA',(840,330),(28,33,40,255));draw=ImageDraw.Draw(preview)
    draw.text((14,12),'Original tier-II source foregrip / no replacement triangles or recoloring',fill='white')
    for j,background in enumerate([(170,190,210,255),(235,220,165,255),(35,40,48,255)]):
        preview.paste(background,(10+j*275,45,10+j*275+256,301))
        preview.alpha_composite(icon,(10+j*275,45))
    draw.text((14,309),'Native GRIP slot; 172 original clipped triangles; mounted extract emits zero vertices',fill='white')
    previewpath=W/'previews/shock_grip_source_preview.png';previewpath.parent.mkdir(exist_ok=True);preview.save(previewpath);outputs.append(previewpath)
    record_path=W/'evidence/shock_grip_resources.json'
    save(record_path,{'passed':True,'attachment':ID,'slot':'grip','eligible_guns':['ssjj_zeus:hand_of_zeuss2'],
        'source':record,'recipe':recipe,'translations':translations,'checks':checks,
        'installed_appearance':'The complete original gun already contains this foregrip. The extracted standalone mesh is suppressed while mounted.',
        'input_sha256':{p.relative_to(W).as_posix():sha(p) for p in inputs},
        'output_sha256':{p.relative_to(W).as_posix():sha(p) for p in outputs},'live_client_test':False})
    print('PASS source foregrip resources:',len(outputs),'outputs,',len(checks),'checks,',len(part['triangles']),'triangles')
if __name__=='__main__':main()
