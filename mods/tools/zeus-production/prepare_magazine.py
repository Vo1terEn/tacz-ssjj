"""Extract the real tier-II animated drum as the Thunder Energy skill magazine.

Only packaging/adaptation of supplied source geometry and UV is performed. No
invented mesh/texture, FX/audio files, production runtime or game files are made.
"""
from pathlib import Path
import copy
import numpy as np
from PIL import Image
from prepare import W,read,save,sha

BASE=W/'baseline_assets';PACK=W/'pack';ID='ssjj_zeus:thunder_energy_mag'

def extract(part):
    p=np.asarray(part['positions']);n=np.asarray(part['normals'])
    low=p.min(0);high=p.max(0);center=(low+high)/2;scale=.75/max(high-low)
    points=(p-center)*scale;points=points[:,[0,2,1]];points[:,1]*=-1
    normals=n[:,[0,2,1]].copy();normals[:,1]*=-1
    result={'bone':'item_extract','positions':points.tolist(),'normals':normals.tolist(),
        'uv':copy.deepcopy(part['uv']),'triangles':copy.deepcopy(part['triangles'])}
    record={'source_joint':'Dummy01','source_min':low.tolist(),'source_max':high.tolist(),
        'source_center':center.tolist(),'uniform_scale':float(scale),
        'item_axes':'(x,y,z) -> (x,-z,y); rigid right-handed reorientation and recenter/scale only',
        'triangle_count':len(part['triangles']),'vertex_count':len(part['positions']),
        'item_min':points.min(0).tolist(),'item_max':points.max(0).tolist(),
        'method':'Complete original animated reload drum, no clipped/cap/replacement triangles or new UV'}
    return result,record

def render(part,texture,size=256):
    points=np.asarray(part['positions']);uv=np.asarray(part['uv'])
    yaw=np.deg2rad(-24);pitch=np.deg2rad(9)
    ry=np.array([[np.cos(yaw),0,np.sin(yaw)],[0,1,0],[-np.sin(yaw),0,np.cos(yaw)]])
    rx=np.array([[1,0,0],[0,np.cos(pitch),-np.sin(pitch)],[0,np.sin(pitch),np.cos(pitch)]])
    points=points@(rx@ry).T;xy=points[:,:2]*270+[size/2,size/2]
    depth=np.full((size,size),-1e8);out=np.zeros((size,size,4),np.uint8)
    for face in part['triangles']:
        p=xy[face];a,b,c=p
        l,t=np.maximum(0,np.floor(p.min(0)).astype(int));r,bottom=np.minimum([size-1,size-1],np.ceil(p.max(0)).astype(int))
        if r<l or bottom<t:continue
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9:continue
        yy,xx=np.mgrid[t:bottom+1,l:r+1];xx=xx+.5;yy=yy+.5
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den
        weights=np.stack([wa,wb,1-wa-wb],axis=-1);z=weights@points[face,2];tuv=weights@uv[face]
        tx=np.clip((tuv[:,:,0]*(texture.shape[1]-1)).astype(int),0,texture.shape[1]-1)
        ty=np.clip((tuv[:,:,1]*(texture.shape[0]-1)).astype(int),0,texture.shape[0]-1);rgba=texture[ty,tx]
        mask=(weights.min(-1)>=-1e-7)&(z>depth[t:bottom+1,l:r+1])&(rgba[:,:,3]>0)
        out[t:bottom+1,l:r+1][mask]=rgba[mask];depth[t:bottom+1,l:r+1][mask]=z[mask]
    return Image.fromarray(out)

def main():
    outputs=[]
    def write(rel,data):
        path=PACK/rel;save(path,data);outputs.append(path)
    meshrel='assets/ssjj_zeus/source_mesh.json';texrel='assets/ssjj_zeus/textures/gun/uv/hand_of_zeuss2.png'
    mesh=read(BASE/meshrel);source=next(p for p in mesh['parts'] if p['bone']=='Dummy01')
    part,record=extract(source)
    write('assets/ssjj_zeus/source_attachment/thunder_energy_mag.json',{'parts':[part],'source':record})
    icon=render(part,np.asarray(Image.open(BASE/texrel).convert('RGBA')))
    iconpath=PACK/'assets/ssjj_zeus/textures/attachment/slot/thunder_energy_mag.png'
    iconpath.parent.mkdir(parents=True,exist_ok=True);icon.save(iconpath);outputs.append(iconpath)
    write('assets/ssjj_zeus/geo_models/attachment/thunder_energy_mag_geo.json',{
        'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.thunder_energy_mag',
        'texture_width':1024,'texture_height':1024,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,0,0]},
        'bones':[{'name':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0]}]}]})
    write('assets/ssjj_zeus/display/attachments/thunder_energy_mag_display.json',{
        'model':'ssjj_zeus:attachment/thunder_energy_mag_geo','texture':'ssjj_zeus:gun/uv/hand_of_zeuss2',
        'slot':'ssjj_zeus:attachment/slot/thunder_energy_mag','show_muzzle':True,'show_mount':False})
    write('data/ssjj_zeus/index/attachments/thunder_energy_mag.json',{
        'name':'attachment.ssjj_zeus.thunder_energy_mag.name','tooltip':'attachment.ssjj_zeus.thunder_energy_mag.desc',
        'display':'ssjj_zeus:thunder_energy_mag_display','data':'ssjj_zeus:thunder_energy_mag_data','type':'extended_mag','sort':152})
    write('data/ssjj_zeus/data/attachments/thunder_energy_mag_data.json',{'weight':0,'extended_mag_level':0})
    write('data/ssjj_zeus/recipes/attachment/thunder_energy_mag.json',{
        'type':'tacz:gun_smith_table_crafting','materials':[
            {'item':{'item':'minecraft:copper_ingot'},'count':8},
            {'item':{'item':'minecraft:redstone'},'count':12},
            {'item':{'item':'minecraft:amethyst_shard'},'count':4},
            {'item':{'item':'minecraft:diamond'},'count':1}],
        'result':{'type':'attachment','id':ID,'count':1,'group':'ssjj_workbench:extended_mag'}})
    gunrel='data/ssjj_zeus/data/guns/hand_of_zeuss2_data.json';gun=read(BASE/gunrel)
    gun['allow_attachment_types']=list(dict.fromkeys(gun['allow_attachment_types']+['extended_mag']))
    write(gunrel,gun)
    tagrel='data/ssjj_zeus/tacz_tags/attachments/allow_attachments/hand_of_zeuss2.json'
    write(tagrel,list(dict.fromkeys(read(BASE/tagrel)+[ID])))
    translations={
        'zh_cn':{
            'attachment.ssjj_zeus.thunder_energy_mag.name':'雷霆聚能弹匣',
            'attachment.ssjj_zeus.thunder_energy_mag.desc':'宙斯之手·贰专属弹匣：每颗雷球有效命中充能50%；充满时，闪电链先电击各命中目标，再各自额外传导2个目标，每跳最多3格，伤害为雷球伤害的50%。不改变弹匣容量。',
            'gun.ssjj_zeus.hand_of_zeuss2.desc':'二阶雷球霰弹枪 · 12发 · 60 RPM · 雷霆能量：每颗雷球有效命中充能25%；充满时，闪电链先电击各命中目标，再各自额外传导1个目标，每跳最多3格，伤害为雷球伤害的30%',
            'skill.ssjj_zeus.thunder_energy.name':'雷霆能量'},
        'en_us':{
            'attachment.ssjj_zeus.thunder_energy_mag.name':'Thunder Energy Magazine',
            'attachment.ssjj_zeus.thunder_energy_mag.desc':'Hand of Zeus II only: each successful orb charges 50%. At full charge, each hit victim is shocked, then its own chain reaches 2 additional targets, up to 3 blocks per hop; chain damage is 50% of orb damage. Magazine capacity is unchanged.',
            'gun.ssjj_zeus.hand_of_zeuss2.desc':'Tier II thunder-orb shotgun · 12 rounds · 60 RPM · Thunder Energy: each successful orb charges 25%; at full charge, each hit victim is shocked, then its own chain reaches 1 additional target, up to 3 blocks per hop, at 30% orb damage',
            'skill.ssjj_zeus.thunder_energy.name':'Thunder Energy'}}
    for lang,values in translations.items():
        rel=f'assets/ssjj_zeus/lang/{lang}.json';doc=read(BASE/rel)
        if (PACK/rel).exists():doc.update(read(PACK/rel))
        doc.update(values);write(rel,doc)
    checks=[]
    def check(name,value):
        assert value,name;checks.append({'name':name,'passed':True})
    animationrel='assets/ssjj_zeus/animations/hand_of_zeuss2.animation.json';animation=read(BASE/animationrel)
    for reload in ['reload_empty','reload_tactical']:
        check('drum is the actual animated reload part: '+reload,'Dummy01' in animation['animations'][reload]['bones'])
    check('all original drum triangles retained',len(part['triangles'])==576 and part['triangles']==source['triangles'])
    check('UV coordinates retained exactly',part['uv']==source['uv'])
    check('only uniform rigid item adaptation',np.allclose(np.asarray(record['item_min'])+record['item_max'],[0,0,0]) and abs(max(np.asarray(record['item_max'])-record['item_min'])-.75)<1e-8)
    check('transparent nonempty source-rendered icon',icon.size==(256,256) and icon.getbbox() is not None and icon.getpixel((0,0))[3]==0)
    check('skill magazine no damage/rpm/capacity modifiers',read(PACK/'data/ssjj_zeus/data/attachments/thunder_energy_mag_data.json')=={'weight':0,'extended_mag_level':0})
    old=read(BASE/gunrel);new=read(PACK/gunrel)
    check('tier II only enables extended_mag',new['allow_attachment_types']==old['allow_attachment_types']+['extended_mag'] and {k:v for k,v in new.items() if k!='allow_attachment_types'}=={k:v for k,v in old.items() if k!='allow_attachment_types'})
    check('tier II retains skin and muzzle whitelist',read(PACK/tagrel)==read(BASE/tagrel)+[ID])
    for other in ['hand_of_zeuss','hand_of_zeuss1']:
        check(other+' rejects magazine',ID not in read(BASE/f'data/ssjj_zeus/tacz_tags/attachments/allow_attachments/{other}.json') and 'extended_mag' not in read(BASE/f'data/ssjj_zeus/data/guns/{other}_data.json')['allow_attachment_types'])
        check(other+' has no gun-data override',not (PACK/f'data/ssjj_zeus/data/guns/{other}_data.json').exists())
    for lang,values in translations.items():
        old=read(BASE/f'assets/ssjj_zeus/lang/{lang}.json');new=read(PACK/f'assets/ssjj_zeus/lang/{lang}.json')
        check(lang+' preserves every unrelated existing translation',all(new.get(k)==v for k,v in old.items() if k not in values))
    recipe=read(PACK/'data/ssjj_zeus/recipes/attachment/thunder_energy_mag.json')
    check('one magazine in extended-mag workbench category',recipe['result']=={'type':'attachment','id':ID,'count':1,'group':'ssjj_workbench:extended_mag'})
    check('no new FX/audio resource generated',all('/effects/' not in p.as_posix() and '/sounds/' not in p.as_posix() for p in outputs))
    inputs=[Path(__file__),BASE/meshrel,BASE/texrel,BASE/animationrel,BASE/gunrel,BASE/tagrel]
    save(W/'evidence/magazine_resource_check.json',{'passed':True,'attachment':ID,'slot':'extended_mag','eligible_guns':['ssjj_zeus:hand_of_zeuss2'],
        'source':record,'texture':'ssjj_zeus:gun/uv/hand_of_zeuss2','installed_appearance':'The original complete gun already renders the same animated drum; suppress the standalone extracted mesh while mounted to avoid duplicate surfaces.',
        'bonuses':{'charge_percent':50,'chain_damage_fraction':.5,'additional_targets':2,'range_per_hop_blocks':3},'recipe':recipe,
        'input_sha256':{p.relative_to(W).as_posix():sha(p) for p in inputs},'output_sha256':{p.relative_to(W).as_posix():sha(p) for p in outputs},'checks':checks,'live_client_test':False})
    print('PASS source drum magazine resources: '+str(len(outputs))+' outputs, '+str(len(checks))+' checks')
if __name__=='__main__':main()
