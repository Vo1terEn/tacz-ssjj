"""Cut the actual tier-II front barrel mesh for the Thunder Burst attachment.

Clipping and software icon rasterization adapt the established original-triangle
workflow from work/dimension_attachments_20261108/build_attachment_assets.py.
No replacement geometry, invented texture, global cache modifiers or game writes.
The gun already contains this muzzle geometry; the functional item renderer only
draws the standalone attachment to avoid overlapping surfaces when installed.
"""
from pathlib import Path
import numpy as np
from PIL import Image
from prepare import W,read,save,sha

BASE=W/'baseline_assets';PACK=W/'pack';ID='ssjj_zeus:thunder_burst'
BOUNDS=[[.30,.475493],[-.034145,.034145],[-.078413,.148671]]

def clip(poly,axis,limit,keep_above):
    result=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        aa=a[axis]>=limit if keep_above else a[axis]<=limit
        bb=b[axis]>=limit if keep_above else b[axis]<=limit
        if aa:result.append(a)
        if aa!=bb:result.append(a+(b-a)*((limit-a[axis])/(b[axis]-a[axis])))
    return result

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
    low=data[:,:3].min(0);high=data[:,:3].max(0);center=(low+high)/2
    scale=.75/max(high-low)
    points=(data[:,:3]-center)*scale;points=points[:,[0,2,1]];points[:,1]*=-1
    normals=data[:,3:6][:,[0,2,1]];normals[:,1]*=-1
    normals/=np.maximum(np.linalg.norm(normals,axis=1,keepdims=True),1e-12)
    result={'bone':'item_extract','positions':points.tolist(),'normals':normals.tolist(),
        'uv':data[:,6:8].tolist(),'triangles':faces}
    record={'source_bounds':BOUNDS,'clipped_source_min':low.tolist(),'clipped_source_max':high.tolist(),
        'source_center':center.tolist(),'uniform_scale':float(scale),'source_axes':{'barrel':'+X','thickness':'Y','up':'+Z'},
        'item_axes':'(x,y,z) -> (x,-z,y); right-handed rotation; recentered before uniform scale',
        'item_min':points.min(0).tolist(),'item_max':points.max(0).tolist(),
        'triangle_count':len(faces),'vertex_count':len(rows),
        'method':'Sutherland-Hodgman clipping of existing WeaponAnchor3 triangles, UV/normals linearly interpolated; no replacement/cap geometry'}
    return result,record

def render(part,texture,size=256):
    # Stable oblique view: front opening and source barrel shell are both visible.
    points=np.asarray(part['positions']);normals=np.asarray(part['normals']);uv=np.asarray(part['uv'])
    yaw=np.deg2rad(-24);pitch=np.deg2rad(9)
    ry=np.array([[np.cos(yaw),0,np.sin(yaw)],[0,1,0],[-np.sin(yaw),0,np.cos(yaw)]])
    rx=np.array([[1,0,0],[0,np.cos(pitch),-np.sin(pitch)],[0,np.sin(pitch),np.cos(pitch)]])
    matrix=rx@ry;points=points@matrix.T;normals=normals@matrix.T
    xy=points[:,:2]*270+[size/2,size/2];depth=np.full((size,size),-1e8);out=np.zeros((size,size,4),np.uint8)
    for face in part['triangles']:
        p=xy[face];a,b,c=p
        l,t=np.maximum(0,np.floor(p.min(0)).astype(int));r,bottom=np.minimum([size-1,size-1],np.ceil(p.max(0)).astype(int))
        if r<l or bottom<t:continue
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9:continue
        yy,xx=np.mgrid[t:bottom+1,l:r+1];xx=xx+.5;yy=yy+.5
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den
        weights=np.stack([wa,wb,1-wa-wb],axis=-1);z=weights@points[face,2]
        tuv=weights@uv[face]
        tx=np.clip((tuv[:,:,0]*(texture.shape[1]-1)).astype(int),0,texture.shape[1]-1)
        ty=np.clip((tuv[:,:,1]*(texture.shape[0]-1)).astype(int),0,texture.shape[0]-1)
        rgba=texture[ty,tx]
        mask=(weights.min(-1)>=-1e-7)&(z>depth[t:bottom+1,l:r+1])&(rgba[:,:,3]>0)
        out[t:bottom+1,l:r+1][mask]=rgba[mask];depth[t:bottom+1,l:r+1][mask]=z[mask]
    return Image.fromarray(out)

def main():
    outputs=[]
    def write(rel,data):
        path=PACK/rel;save(path,data);outputs.append(path)
    meshrel='assets/ssjj_zeus/source_mesh.json';texture='assets/ssjj_zeus/textures/gun/uv/hand_of_zeuss2.png'
    mesh=read(BASE/meshrel);source=next(p for p in mesh['parts'] if p['bone']=='WeaponAnchor3')
    part,record=extract(source)
    write('assets/ssjj_zeus/source_attachment/thunder_burst.json',{'parts':[part],'source':record})
    icon=render(part,np.asarray(Image.open(BASE/texture).convert('RGBA')))
    iconpath=PACK/'assets/ssjj_zeus/textures/attachment/slot/thunder_burst.png';iconpath.parent.mkdir(parents=True,exist_ok=True);icon.save(iconpath);outputs.append(iconpath)
    write('assets/ssjj_zeus/geo_models/attachment/thunder_burst_geo.json',{
        'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.thunder_burst',
        'texture_width':1024,'texture_height':1024,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,0,0]},
        'bones':[{'name':'root','pivot':[0,0,0]},{'name':'item_extract','parent':'root','pivot':[0,0,0]}]}]})
    write('assets/ssjj_zeus/display/attachments/thunder_burst_display.json',{
        'model':'ssjj_zeus:attachment/thunder_burst_geo','texture':'ssjj_zeus:gun/uv/hand_of_zeuss2',
        'slot':'ssjj_zeus:attachment/slot/thunder_burst','show_muzzle':True,'show_mount':False})
    write('data/ssjj_zeus/index/attachments/thunder_burst.json',{
        'name':'attachment.ssjj_zeus.thunder_burst.name','tooltip':'attachment.ssjj_zeus.thunder_burst.desc',
        'display':'ssjj_zeus:thunder_burst_display','data':'ssjj_zeus:thunder_burst_data','type':'muzzle','sort':151})
    # All bonuses are resolved once by the server at shot creation/impact.
    write('data/ssjj_zeus/data/attachments/thunder_burst_data.json',{'weight':0})
    write('data/ssjj_zeus/recipes/attachment/thunder_burst.json',{
        'type':'tacz:gun_smith_table_crafting','materials':[
        {'item':{'item':'minecraft:copper_ingot'},'count':8},
        {'item':{'item':'minecraft:redstone'},'count':8},
        {'item':{'item':'minecraft:amethyst_shard'},'count':4},
        {'item':{'item':'minecraft:diamond'},'count':1}],
        'result':{'type':'attachment','id':ID,'count':1,'group':'ssjj_workbench:muzzle'}})
    for gun in ('hand_of_zeuss1','hand_of_zeuss2'):
        rel=f'data/ssjj_zeus/tacz_tags/attachments/allow_attachments/{gun}.json'
        old=read(BASE/rel);write(rel,list(dict.fromkeys(old+[ID])))

    checks=[]
    def check(name,condition):
        assert condition,name;checks.append({'name':name,'passed':True})
    check('source is actual tier II WeaponAnchor3',source['bone']=='WeaponAnchor3' and len(source['triangles'])==3986)
    check('source triangles clipped rather than substitute mesh',len(part['triangles'])>30 and record['clipped_source_min'][0]>=.3-1e-8)
    check('item centered with maximum extent 0.75',np.allclose(np.asarray(record['item_min'])+np.asarray(record['item_max']),[0,0,0]) and abs(max(np.asarray(record['item_max'])-np.asarray(record['item_min']))-.75)<1e-8)
    check('matching original texture dimensions',Image.open(BASE/texture).size==(1024,1024))
    check('icon visible and transparent',icon.size==(256,256) and icon.getbbox() is not None and icon.getpixel((0,0))[3]==0)
    check('native modifier contains no damage/recoil/range bonus',read(PACK/'data/ssjj_zeus/data/attachments/thunder_burst_data.json')=={'weight':0})
    for gun in ('hand_of_zeuss1','hand_of_zeuss2'):
        rel=f'data/ssjj_zeus/tacz_tags/attachments/allow_attachments/{gun}.json'
        check(gun+' tag only adds dedicated muzzle',read(PACK/rel)==list(dict.fromkeys(read(BASE/rel)+[ID])) )
        check(gun+' enables muzzle slot','muzzle' in read(PACK/f'data/ssjj_zeus/data/guns/{gun}_data.json')['allow_attachment_types'])
    check('base tier rejects dedicated muzzle',ID not in read(BASE/'data/ssjj_zeus/tacz_tags/attachments/allow_attachments/hand_of_zeuss.json') and 'muzzle' not in read(PACK/'data/ssjj_zeus/data/guns/hand_of_zeuss_data.json')['allow_attachment_types'])
    alltags=list((BASE/'data').glob('*/tacz_tags/attachments/allow_attachments/*.json'))
    check('no other family accepts dedicated muzzle',not any(ID in read(path) for path in alltags))
    check('Rongguang muzzle crafting tab',read(PACK/'data/ssjj_zeus/recipes/attachment/thunder_burst.json')['result']['group']=='ssjj_workbench:muzzle')
    check('all json outputs parse',all(read(path) is not None for path in outputs if path.suffix=='.json'))
    save(W/'evidence/muzzle_resource_check.json',{
        'passed':True,'attachment':ID,'source_cut':record,
        'installed_appearance':'Original complete Zeus body already contains this barrel shell; standalone source mesh only is drawn to avoid z-fighting.',
        'runtime_binding_required':'ZeusThunderAttachmentRenderer binds item_extract functional renderer; no gun-mounted duplicate',
        'data_bonus_multiplier':1,'server_bonus_multiplier':1.5,'server_blast_dimensions':[1.5,1.5,1.5],
        'server_ammo_refund':'One per unique valid victim, capped at current magazine capacity',
        'input_sha256':{'scripts/prepare_muzzle.py':sha(Path(__file__)),'baseline_assets/'+meshrel:sha(BASE/meshrel),'baseline_assets/'+texture:sha(BASE/texture)},
        'output_sha256':{str(path.relative_to(W)):sha(path) for path in outputs},'checks':checks,'live_client_test':False})
    print('PASS Thunder Burst original muzzle cut: '+str(record['triangle_count'])+' triangles, '+str(len(checks))+' checks')
if __name__=='__main__':main()
