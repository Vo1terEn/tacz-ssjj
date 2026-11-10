"""Build three exclusive cosmetic scopes and nine native TaCZ display variants.

Inputs are the frozen 1.1.49 resource pair and the user's original mesh/texture
exports. This script owns only build/asset_{mod,pack}_overlay and its evidence.
It copies no previous Java, LRT implementation, or third-party state machine.
"""
import os
from pathlib import Path
from zipfile import ZipFile
import argparse
import copy
import gzip
import json
import shutil
import struct
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import atlas_baker as bake

ROOT=Path(__file__).resolve().parents[1]
WORKSPACE=ROOT.parents[1]
IDS=('darkdiamond','darkdiamond1','darkdiamond2')
TITLES=('黯水晶-绝','黯水晶-绝·壹','黯水晶-绝·贰')
MESH_IDS=('-2120587258229831796','2274883771299243205','2275037573275446717')
THEMES=('mix','nightguardian','pure')
ZH={'mix':'交融','nightguardian':'守夜人','pure':'圣水晶'}
EN={'mix':'Fusion','nightguardian':'Night Watcher','pure':'Holy Crystal'}
BUILD=ROOT/'build'
EVIDENCE=ROOT/'evidence'

def dump(path,value):bake.dump(path,value)
def read(archive,path):return json.loads(archive.read(path))
def source_texture(folder,code,resolution):
    paths=[path for path in folder.glob(code+'__*.png') if Image.open(path).size==(resolution,resolution)]
    assert len(paths)==1,(folder,paths)
    return paths[0]
def png_write(path,array):
    path.parent.mkdir(parents=True,exist_ok=True)
    Image.fromarray(array).save(path)
def clean_owned(path):
    path.resolve().relative_to(BUILD.resolve())
    if path.exists():shutil.rmtree(path)
    path.mkdir(parents=True)
def refresh_labels(pack,pack_out):
    for locale in ('zh_cn','en_us'):
        member=f'assets/ssjj_dark/lang/{locale}.json';lang=read(pack,member)
        chinese=locale=='zh_cn'
        for code in IDS:
            lang[f'gun.ssjj_dark.{code}.desc']=('默认自动 / 左键轻斩 / 右键重斩 / 专属瞄具槽切换皮肤，拆卸恢复原貌' if chinese else
                'AUTO by default / Left: light slash / Right: heavy slash / Cosmetic scope selects skin; remove to restore default')+(
                ' / V 黯晶变身' if chinese and code==IDS[2] else ' / V: Dark Crystal transformation' if code==IDS[2] else '')
        for theme in THEMES:
            scope='skin_'+theme
            lang[f'attachment.ssjj_dark.{scope}.name']=('黯水晶专属皮肤 · '+ZH[theme] if chinese else 'Dark Crystal Skin · '+EN[theme])
            lang[f'attachment.ssjj_dark.{scope}.desc']=('仅黯水晶三阶可用；装入瞄具槽切换外观，拆卸恢复当前品阶默认皮肤；不提供开镜或属性加成。制作：120黯水晶碎片。' if chinese else
                'For all three Dark Crystal grades only. Install in the scope slot to change appearance; remove to restore the grade default. No aiming or stat bonuses. Cost: 120 Dark Crystal Fragments.')
        dump(pack_out/member,lang)
    preview=Image.new('RGB',(1200,900),'#171c29');draw=ImageDraw.Draw(preview)
    font=ImageFont.truetype(os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),27)
    small=ImageFont.truetype(os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),18)
    cells=[('默认 · 基础',bake.png(pack.read('assets/ssjj_dark/textures/gun/slot/darkdiamond.png'))),
        ('默认 · 壹阶',bake.png(pack.read('assets/ssjj_dark/textures/gun/slot/darkdiamond1.png'))),
        ('默认 · 贰阶',bake.png(pack.read('assets/ssjj_dark/textures/gun/slot/darkdiamond2.png')))]
    cells += [('皮肤 · '+ZH[theme],bake.png((pack_out/f'assets/ssjj_dark/textures/gun/slot/darkdiamond_{theme}.png').read_bytes())) for theme in THEMES]
    for i,(title,array) in enumerate(cells):
        x=20+(i%3)*400;y=24+(i//3)*430
        draw.rounded_rectangle((x,y,x+360,y+400),radius=16,fill='#232b3e',outline='#3b4864',width=2)
        im=Image.fromarray(array).resize((330,330),Image.Resampling.LANCZOS)
        preview.paste(im,(x+15,y+30),im)
        draw.text((x+20,y+358),title,font=font,fill='#eef2ff')
    draw.text((28,868),'原素材 UV 重烘焙刀体预览；皮肤瞄具适用三阶，拆卸恢复当前品阶默认外观。非游戏截图。',font=small,fill='#b9c6dd')
    preview.save(EVIDENCE/'dark_skin_preview.png')

def main(args):
    pack_out=BUILD/'asset_pack_overlay';mod_out=BUILD/'asset_mod_overlay'
    for path in (pack_out,mod_out):clean_owned(path)
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    theme_rows=[];display_rows=[];mesh_rows=[]
    with ZipFile(args.pack) as pack,ZipFile(args.mod) as mod:
        geos={code:read(pack,f'assets/ssjj_dark/geo_models/gun/{code}_geo.json') for code in IDS}
        meshes={};params={};base_sources={}
        for code,title,mesh in zip(IDS,TITLES,MESH_IDS):
            obj=next((args.source/'武器模型/原版'/title/'战斗').rglob('asjj_p1__'+mesh+'.obj'))
            vertices,uv,faces=bake.load_obj(obj);meshes[code]=(vertices,uv,faces)
            params[code]=[bake.triangle_params(vertices,uv,face) for face in faces]
            source=source_texture(args.source/'皮肤贴图/原版'/title/'战斗'/code,code,512)
            rgba=bake.png(source.read_bytes());base_sources[code]=rgba
            atlas,newgeo,rects,stats=bake.bake(rgba,params[code],geos[code])
            old=bake.png(pack.read(f'assets/ssjj_dark/textures/gun/{code}.png'))
            assert np.array_equal(atlas,old) and newgeo==geos[code],f'Frozen source/UV atlas reconstruction mismatch: {code}'
            mesh_rows.append({'id':code,'source_obj':str(obj),'source_obj_sha256':bake.sha(obj.read_bytes()),
                'vertices':len(vertices),'triangles':len(faces),'frozen_149_atlas_reconstruction_exact':True})
            gun=read(pack,f'data/ssjj_dark/data/guns/{code}_data.json')
            gun['fire_mode']=['auto'];gun['allow_attachment_types']=['scope']
            dump(pack_out/f'data/ssjj_dark/data/guns/{code}_data.json',gun)
            dump(pack_out/f'data/ssjj_dark/tacz_tags/attachments/allow_attachments/{code}.json',
                ['ssjj_dark:skin_'+theme for theme in THEMES])
        # All runtime third-person meshes preserve the source asjj_p3 UVs.
        binary=gzip.decompress(mod.read('assets/ssjj_dark/runtime/weapon.bin.gz'))
        version,count,face_count=struct.unpack('>3i',binary[:12])
        assert version==1 and len(binary)==12+count*20+face_count*12
        runtime_uv=np.frombuffer(binary,dtype='>f4',offset=12+count*12,count=count*2).reshape(-1,2)
        original_p3=next((args.source/'武器模型/原版'/TITLES[2]/'战斗').rglob('asjj_p3__-3174609672311506482.obj'))
        _,p3_uv,_=bake.load_obj(original_p3)
        assert runtime_uv.shape==p3_uv.shape and np.max(np.abs(runtime_uv-p3_uv))<1e-7
        for theme in THEMES:
            source_id='skin_darkdiamond_'+theme
            texture=source_texture(args.source/'皮肤贴图/皮肤'/source_id/'战斗'/source_id,source_id,1024)
            texture_bytes=texture.read_bytes();rgba=bake.png(texture_bytes)
            assert rgba.shape[:2]==(1024,1024)
            sources=[]
            for obj in sorted((args.source/'武器模型/皮肤'/source_id/'战斗').rglob('asjj_p1__*.obj')):
                smesh=bake.load_obj(obj)
                assert all(all(np.array_equal(a,b) for a,b in zip(smesh,meshes[code])) for code in IDS),f'Skin source mesh/UV differs: {obj}'
                sources.append({'path':str(obj),'sha256':bake.sha(obj.read_bytes()),'vertices_uv_faces_exact_match_all_grades':True})
            assert len(sources)==3
            # Skin packages contain p3 copies for the three grades, too.
            p3_records=[]
            for obj in sorted((args.source/'武器模型/皮肤'/source_id/'战斗').rglob('asjj_p3__*.obj')):
                _,suv,_=bake.load_obj(obj)
                assert suv.shape==p3_uv.shape and np.array_equal(suv,p3_uv)
                p3_records.append(str(obj))
            assert p3_records
            first_atlas=None;first_slot=None
            for code in IDS:
                atlas,geo,rects,stats=bake.bake(rgba,params[code],geos[code])
                if first_atlas is None:first_atlas=atlas
                else:assert np.array_equal(first_atlas,atlas)
                dump(pack_out/f'assets/ssjj_dark/geo_models/gun/{code}_{theme}_geo.json',geo)
                display=read(pack,f'assets/ssjj_dark/display/guns/{code}_display.json')
                display.update({'model':f'ssjj_dark:gun/{code}_{theme}_geo','texture':f'ssjj_dark:gun/skin_{theme}',
                    'slot':f'ssjj_dark:gun/slot/{code}_{theme}','hud':f'ssjj_dark:gun/slot/{code}_{theme}',
                    'show_crosshair':False,'iron_zoom':1,'zoom_model_fov':70})
                dump(pack_out/f'assets/ssjj_dark/display/guns/{code}_{theme}_display.json',display)
                cubes=[c for bone in geos[code]['minecraft:geometry'][0]['bones'] for c in bone.get('cubes',[])]
                old_rects=[(*c['uv']['north']['uv'],*c['uv']['north']['uv_size']) for c in cubes]
                rendered,comparison=bake.render_three(params[code],rgba,
                    bake.png(pack.read(f'assets/ssjj_dark/textures/gun/{code}.png')),old_rects,atlas,rects)
                # Native-UV reference versus new high-density atlas, excluding
                # old default-theme comparison because it is a different image.
                assert comparison['new_rgb_mae_vs_source']<2.0
                slot=rendered[-1].resize((256,256),Image.Resampling.LANCZOS)
                slot_path=pack_out/f'assets/ssjj_dark/textures/gun/slot/{code}_{theme}.png'
                slot_path.parent.mkdir(parents=True,exist_ok=True);slot.save(slot_path)
                if first_slot is None:first_slot=slot
                third=mod_out/f'assets/ssjj_dark/textures/thirdperson/{code}_{theme}.png'
                third.parent.mkdir(parents=True,exist_ok=True);third.write_bytes(texture_bytes)
                display_rows.append({'gun_id':'ssjj_dark:'+code,'scope_id':'ssjj_dark:skin_'+theme,
                    'display_id':f'ssjj_dark:{code}_{theme}_display','default_display':f'ssjj_dark:{code}_display',
                    'model':display['model'],'texture':display['texture'],'animation':display['animation'],
                    'thirdperson_texture':f'ssjj_dark:textures/thirdperson/{code}_{theme}.png',
                    'source_uv_cpu_rgb_mae':comparison['new_rgb_mae_vs_source'],'bake':stats})
                if code==IDS[0]:
                    sheet=Image.new('RGB',(1520,800),'#222630');draw=ImageDraw.Draw(sheet)
                    for i,im in enumerate((rendered[0],rendered[-1])):
                        sheet.paste(im,(i*760,30),im)
                        draw.text((i*760+12,8),theme+(' | original 1024 UV' if i==0 else ' | rebaked atlas'),fill='white')
                    draw.text((12,785),'CPU source-UV readback; not a game screenshot.',fill='white')
                    sheet.save(EVIDENCE/f'skin_{theme}_uv_quality.png')
            png_write(pack_out/f'assets/ssjj_dark/textures/gun/skin_{theme}.png',first_atlas)
            scope='skin_'+theme
            icon=pack_out/f'assets/ssjj_dark/textures/attachment/slot/{scope}.png'
            icon.parent.mkdir(parents=True,exist_ok=True);first_slot.save(icon)
            dump(pack_out/f'data/ssjj_dark/index/attachments/{scope}.json',{
                'name':f'attachment.ssjj_dark.{scope}.name','tooltip':f'attachment.ssjj_dark.{scope}.desc',
                'display':f'ssjj_dark:{scope}_display','data':f'ssjj_dark:{scope}_data','type':'scope','sort':24200+THEMES.index(theme)})
            dump(pack_out/f'data/ssjj_dark/data/attachments/{scope}_data.json',{'weight':0})
            dump(pack_out/f'assets/ssjj_dark/display/attachments/{scope}_display.json',{
                'slot':f'ssjj_dark:attachment/slot/{scope}','model':'ssjj_dark:attachment/cosmetic_scope_geo',
                'texture':'ssjj_dark:attachment/cosmetic_scope','scope':False,'sight':False,'zoom':[1.0],
                'sounds':{'install':'tacz:attachments/attachment_general_a','uninstall':'tacz:attachments/attachment_general_uninstall'}})
            dump(pack_out/f'data/ssjj_dark/recipes/attachments/{scope}.json',{
                'type':'tacz:gun_smith_table_crafting','materials':[{'item':{'item':'ssjj_dark:darkcrystal_fragment'},'count':120}],
                'result':{'type':'attachment','id':'ssjj_dark:'+scope,'count':1}})
            theme_rows.append({'theme':theme,'source_main_texture':str(texture),'source_sha256':bake.sha(texture_bytes),
                'localized_name':{'zh_cn':ZH[theme],'en_us':EN[theme]},
                'source_resolution':[1024,1024],'source_p1_meshes':sources,'source_p3_meshes':p3_records,
                'atlas_resolution':list(first_atlas.shape[1::-1]),'shared_across_grades':True})
        dump(pack_out/'assets/ssjj_dark/geo_models/attachment/cosmetic_scope_geo.json',{
            'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.darkcrystal_cosmetic_scope',
                'texture_width':16,'texture_height':16,'visible_bounds_width':2,'visible_bounds_height':2,
                'visible_bounds_offset':[0,0,0]},'bones':[{'name':'scope','pivot':[0,0,0]}]}]})
        png_write(pack_out/'assets/ssjj_dark/textures/attachment/cosmetic_scope.png',np.zeros((16,16,4),dtype=np.uint8))
        refresh_labels(pack,pack_out)
    outputs={tree.name:{p.relative_to(tree).as_posix():bake.sha(p.read_bytes()) for p in sorted(tree.rglob('*')) if p.is_file()}
        for tree in (mod_out,pack_out)}
    report={'input_pack':str(args.pack),'input_pack_sha256':bake.sha(args.pack.read_bytes()),
        'input_mod':str(args.mod),'input_mod_sha256':bake.sha(args.mod.read_bytes()),'gun_ids':['ssjj_dark:'+code for code in IDS],
        'scope_ids':['ssjj_dark:skin_'+theme for theme in THEMES],'native_display_api':'IGun.setGunDisplayId(ItemStack,ResourceLocation)',
        'skin_scopes_are_cosmetic_only':True,'native_attachment_skin_is_not_gun_skin':True,
        'default_fire_mode':'auto','allowed_attachment_types':['scope'],'scope_recipe_cost':120,
        'default_mesh_source_mapping':mesh_rows,'themes':theme_rows,'display_variants':display_rows,'outputs':outputs,
        'runtime_uv':{'source':str(original_p3),'vertices':count,'triangles':face_count,'max_error':float(np.max(np.abs(runtime_uv-p3_uv)))},
        'unchanged_original_gun_ids':True,'java_or_lrt_copied':False,'game_started':False,
        'assembly_order':'Overlay these resources after the frozen reference; root owns new Java and packaging.'}
    dump(EVIDENCE/'skin_resources.json',report)
    print('READY: 3 cosmetic scopes, 9 displays, 3 native-UV 1024 themes; no Java or packaging changes')

def labels_only(args):
    pack_out=BUILD/'asset_pack_overlay';mod_out=BUILD/'asset_mod_overlay'
    before={p.relative_to(BUILD).as_posix():bake.sha(p.read_bytes())
        for tree in (mod_out,pack_out) for p in tree.rglob('*') if p.is_file()}
    with ZipFile(args.pack) as pack:
        refresh_labels(pack,pack_out)
    after={p.relative_to(BUILD).as_posix():bake.sha(p.read_bytes())
        for tree in (mod_out,pack_out) for p in tree.rglob('*') if p.is_file()}
    changed=[key for key in sorted(before.keys()|after.keys()) if before.get(key)!=after.get(key)]
    allowed={'asset_pack_overlay/assets/ssjj_dark/lang/zh_cn.json','asset_pack_overlay/assets/ssjj_dark/lang/en_us.json'}
    assert set(changed)<=allowed,changed
    report=json.loads((EVIDENCE/'skin_resources.json').read_text('utf8'))
    for row in report['themes']:
        row['localized_name']={'zh_cn':ZH[row['theme']],'en_us':EN[row['theme']]}
    report['outputs']={tree.name:{p.relative_to(tree).as_posix():bake.sha(p.read_bytes())
        for p in sorted(tree.rglob('*')) if p.is_file()} for tree in (mod_out,pack_out)}
    report['label_refresh']={'changed_overlay_members':changed,'all_non_language_resources_unchanged':True,
        'zh_cn_names':ZH,'en_us_names':EN,'preview':'evidence/dark_skin_preview.png'}
    dump(EVIDENCE/'skin_resources.json',report)
    print('LABELS REFRESHED: Fusion / Night Watcher / Holy Crystal; models and textures unchanged')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack',type=Path,default=ROOT/'reference/pack.zip')
    parser.add_argument('--mod',type=Path,default=ROOT/'reference/mod.jar')
    parser.add_argument('--source',type=Path,default=WORKSPACE/'黯水晶')
    parser.add_argument('--labels-only',action='store_true',help='Refresh names/translations/contact sheet without rebaking assets')
    args=parser.parse_args()
    labels_only(args) if args.labels_only else main(args)
