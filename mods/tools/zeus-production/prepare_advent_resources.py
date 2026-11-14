"""Source-derived stock and original Zeus skill UI; no invented artwork."""
from pathlib import Path
import importlib.util,copy,shutil
import numpy as np
from PIL import Image
from prepare import W,ROOT,read,save,sha
from prepare_magazine import render
BASE=W/'baseline_assets';PACK=W/'pack';ID='ssjj_zeus:thunder_advent_stock'
BOUNDS=[[-.475493,-.30],[-.034145,.034145],[-.078413,.148671]]
def clip(poly,axis,limit,above):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        aa=a[axis]>=limit if above else a[axis]<=limit;bb=b[axis]>=limit if above else b[axis]<=limit
        if aa:out.append(a)
        if aa!=bb:out.append(a+(b-a)*((limit-a[axis])/(b[axis]-a[axis])))
    return out
def extract(part):
    original=np.concatenate([part['positions'],part['normals'],part['uv']],axis=1);rows=[];faces=[]
    for face in part['triangles']:
        poly=[original[i].copy() for i in face]
        for axis,(low,high) in enumerate(BOUNDS):
            poly=clip(poly,axis,low,True) if poly else [];poly=clip(poly,axis,high,False) if poly else []
        if len(poly)<3:continue
        offset=len(rows);rows.extend(poly);faces.extend([[offset,offset+i,offset+i+1] for i in range(1,len(poly)-1)])
    data=np.asarray(rows);assert len(faces)>30
    low=data[:,:3].min(0);high=data[:,:3].max(0);center=(low+high)/2;scale=.75/max(high-low)
    p=(data[:,:3]-center)*scale;p=p[:,[0,2,1]];p[:,1]*=-1
    n=data[:,3:6][:,[0,2,1]];n[:,1]*=-1;n/=np.maximum(np.linalg.norm(n,axis=1,keepdims=True),1e-12)
    return {'bone':'item_extract','positions':p.tolist(),'normals':n.tolist(),'uv':data[:,6:8].tolist(),'triangles':faces},{
        'source_joint':'WeaponAnchor3','source_bounds':BOUNDS,'clipped_source_min':low.tolist(),'clipped_source_max':high.tolist(),
        'uniform_scale':float(scale),'triangle_count':len(faces),'item_axes':'(x,y,z) -> (x,-z,y), recenter and uniform scale',
        'method':'Clip original rear gun shell triangles; interpolate source UV/normals at cuts. No new cap or replacement geometry.'}
def main():
    outputs=[];inputs=[Path(__file__),W/'scripts/prepare_magazine.py']
    def write(rel,data):
        p=PACK/rel;save(p,data);outputs.append(p)
    meshrel='assets/ssjj_zeus/source_mesh.json';texrel='assets/ssjj_zeus/textures/gun/uv/hand_of_zeuss2.png'
    inputs.extend([BASE/meshrel,BASE/texrel]);source=next(p for p in read(BASE/meshrel)['parts'] if p['bone']=='WeaponAnchor3')
    part,record=extract(source);write('assets/ssjj_zeus/source_attachment/thunder_advent_stock.json',{'parts':[part],'source':record})
    icon=render(part,np.asarray(Image.open(BASE/texrel).convert('RGBA')))
    p=PACK/'assets/ssjj_zeus/textures/attachment/slot/thunder_advent_stock.png';p.parent.mkdir(parents=True,exist_ok=True);icon.save(p);outputs.append(p)
    write('assets/ssjj_zeus/geo_models/attachment/thunder_advent_stock_geo.json',{'format_version':'1.12.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.thunder_advent_stock','texture_width':1024,'texture_height':1024,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,0,0]},
        'bones':[{'name':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0]}]}]})
    write('assets/ssjj_zeus/display/attachments/thunder_advent_stock_display.json',{'model':'ssjj_zeus:attachment/thunder_advent_stock_geo','texture':'ssjj_zeus:gun/uv/hand_of_zeuss2',
        'slot':'ssjj_zeus:attachment/slot/thunder_advent_stock','show_muzzle':True,'show_mount':False})
    write('data/ssjj_zeus/index/attachments/thunder_advent_stock.json',{'name':'attachment.ssjj_zeus.thunder_advent_stock.name','tooltip':'attachment.ssjj_zeus.thunder_advent_stock.desc',
        'display':'ssjj_zeus:thunder_advent_stock_display','data':'ssjj_zeus:thunder_advent_stock_data','type':'stock','sort':153})
    write('data/ssjj_zeus/data/attachments/thunder_advent_stock_data.json',{'weight':0})
    write('data/ssjj_zeus/recipes/attachment/thunder_advent_stock.json',{'type':'tacz:gun_smith_table_crafting','materials':[
        {'item':{'item':'minecraft:copper_ingot'},'count':12},{'item':{'item':'minecraft:gold_ingot'},'count':4},
        {'item':{'item':'minecraft:amethyst_shard'},'count':4},{'item':{'item':'minecraft:diamond'},'count':2}],
        'result':{'type':'attachment','id':ID,'count':1,'group':'ssjj_workbench:stock'}})
    gunrel='data/ssjj_zeus/data/guns/hand_of_zeuss2_data.json';tagrel='data/ssjj_zeus/tacz_tags/attachments/allow_attachments/hand_of_zeuss2.json'
    for rel in [gunrel,tagrel]:inputs.append(BASE/rel)
    gun=read(BASE/gunrel);gun['allow_attachment_types']=list(dict.fromkeys(gun['allow_attachment_types']+['stock']));write(gunrel,gun)
    write(tagrel,list(dict.fromkeys(read(BASE/tagrel)+[ID])))
    translations={
        'zh_cn':{'attachment.ssjj_zeus.thunder_advent_stock.name':'雷霆神降枪托',
            'attachment.ssjj_zeus.thunder_advent_stock.desc':'宙斯之手·贰专属枪托：【宙斯降临】同时存在幻影上限3→5，储存次数上限4→6。安装后枪身外观与基础属性保持不变。',
            'skill.ssjj_zeus.advent.name':'宙斯降临','hud.ssjj_zeus.advent.active':'幻影 %s/%s',
            'key.ssjj_zeus.advent':'宙斯降临','key.categories.ssjj_zeus':'宙斯之手'},
        'en_us':{'attachment.ssjj_zeus.thunder_advent_stock.name':'Thunder Advent Stock',
            'attachment.ssjj_zeus.thunder_advent_stock.desc':'Hand of Zeus II only: Zeus Advent phantom limit 3→5 and stored charge limit 4→6. Installed gun appearance and ordinary attributes stay unchanged.',
            'skill.ssjj_zeus.advent.name':'Zeus Advent','hud.ssjj_zeus.advent.active':'Phantoms %s/%s',
            'key.ssjj_zeus.advent':'Zeus Advent','key.categories.ssjj_zeus':'Hand of Zeus'}}
    for lang,values in translations.items():
        rel=f'assets/ssjj_zeus/lang/{lang}.json';doc=read(BASE/rel)
        if (PACK/rel).exists():doc.update(read(PACK/rel))
        doc.update(values);write(rel,doc)
    root=ROOT/'宙斯、聚能/宙斯之手/完整源素材/武器';folder=next(p for p in root.iterdir() if p.name.endswith('__hand_of_zeuss2'))/'hand_of_zeuss2'
    copies={}
    for key in ['activeBg','unactiveBg','activeIcon','unactiveIcon','normalProgress']:
        source=next((folder/'Textures').glob(key+'__*.png'));dest=PACK/f'assets/ssjj_zeus/textures/gui/advent/{key.lower()}.png'
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest);inputs.append(source);outputs.append(dest)
        copies[key]={'source':source.relative_to(ROOT).as_posix(),'sha256':sha(source),'size':list(Image.open(source).size),'byte_identical':sha(source)==sha(dest)}
    # A supplied lightning sheet is reused as an animated translucent second skin;
    # orange/yellow draw tint implements the requested layer without painting pixels.
    lightning=next((folder/'Textures').glob('FX_lightning_006_TEX_KJS_3__*.png'))
    dest=PACK/'assets/ssjj_zeus/textures/advent/source_lightning.png';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(lightning,dest);inputs.append(lightning);outputs.append(dest)
    copies['lightning']={'source':lightning.relative_to(ROOT).as_posix(),'sha256':sha(lightning),'size':list(Image.open(lightning).size),'byte_identical':sha(lightning)==sha(dest)}
    material=ROOT/'宙斯、聚能/宙斯之手/特效及材质参数/武器/hand_of_zeuss2$2/Material/8448836225604903188.json';inputs.append(material)
    original_material=read(material);props=original_material['m_SavedProperties']
    assert dict(props['m_TexEnvs'])['_MainTex']['m_Texture']['m_PathID']==-4981562211789921920
    layouts=[next((folder/'EmbeddedData').glob(stem+'__*.bytes')) for stem in ['ui_layout','uitextureconfig']];inputs.extend(layouts)
    write('assets/ssjj_zeus/advent/source_layout.json',{'frame':[210,90],'progress':[76,62,125,7],'charge_icons':{'first':[76,4],'size':[22,22],'spacing':22},
        'key_overlay':{'source_E_x':96,'source_E_y':41,'width':8,'height':13,'replacement':'ZeusAdventKeys.skillKeyLabel()'},
        'original_layout':layouts[0].relative_to(ROOT).as_posix(),'original_atlas':layouts[1].relative_to(ROOT).as_posix()})
    checks=[]
    def check(name,v):assert v,name;checks.append({'name':name,'passed':True})
    check('source rear model has real UV triangles',len(part['triangles'])>30 and record['clipped_source_max'][0]<=-.30+1e-8)
    check('tier II only enables stock',gun['allow_attachment_types']==read(BASE/gunrel)['allow_attachment_types']+['stock'])
    check('tier II only adds stock whitelist',read(PACK/tagrel)==read(BASE/tagrel)+[ID])
    check('skill-only stock no ordinary modifiers',read(PACK/'data/ssjj_zeus/data/attachments/thunder_advent_stock_data.json')=={'weight':0})
    for other in ['hand_of_zeuss','hand_of_zeuss1']:
        check(other+' rejects stock','stock' not in read(BASE/f'data/ssjj_zeus/data/guns/{other}_data.json')['allow_attachment_types'])
    check('every original UI/lightning image byte preserved',all(v['byte_identical'] for v in copies.values()))
    save(W/'evidence/advent_resource_check.json',{'passed':True,'attachment':ID,'source_stock':record,'source_images':copies,'checks':checks,
        'phantom_material':{'source':material.relative_to(ROOT).as_posix(),'name':original_material['m_Name'],'properties':props,
            'adaptation':'No complete source phantom body was provided: render the native player skin/model and held source Zeus gun, with this original additive/depth-tested lightning bitmap over the native player UV. Requested orange/yellow tint and native energySwirl UV motion adapt it to Minecraft; this is not an original full-body phantom shader reconstruction.'},
        'bonuses':{'max_active':5,'max_charges':6},'key_adaptation':'No UI raster is edited: reuse original empty background pixels in the E area at render time and draw native mapped key text.',
        'input_sha256':{(p.relative_to(W) if p.is_relative_to(W) else p).as_posix():sha(p) for p in inputs},
        'output_sha256':{p.relative_to(W).as_posix():sha(p) for p in outputs},'live_client_test':False})
    print('PASS source stock / original skill UI:',len(outputs),'outputs,',len(checks),'checks')
if __name__=='__main__':main()
