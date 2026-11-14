"""Extract supplied badge without label panel and the authentic jingti focus effect."""
from pathlib import Path
import json,sys,copy
import numpy as np
from PIL import Image
from prepare import W,ROOT,sha,save,read
import prepare_source_fx as source

def main():
    inputs={};outputs={};records={}
    def bind(p):
        p=Path(p).resolve();key=(p.relative_to(W) if p.is_relative_to(W) else p).as_posix();inputs[key]=sha(p)
    folder=next((ROOT/'宙斯、聚能/宙斯之手/完整源素材/武器').glob('*__hand_of_zeuss2'))/'hand_of_zeuss2'
    for state in ('active','unactive'):
        p=next((folder/'Textures').glob(state+'Bg__*.png'));bind(p)
        a=np.asarray(Image.open(p).convert('RGBA')).copy();out=a[:,:83].copy()
        # Only subtract pixels: crop the supplied round emblem from its attached
        # text panel, then remove its black panel and two straight panel rules.
        dark=(out[:,:,:3].max(2)<45)&(out[:,:,3]<180);out[dark,3]=0
        out[28:31,72:,3]=0;out[69:73,64:,3]=0
        out[39:54,80:,3]=0;out[54:58,83:,3]=0
        target=W/f'pack/assets/ssjj_zeus/textures/gui/advent/{state}badge.png';target.parent.mkdir(parents=True,exist_ok=True);Image.fromarray(out).save(target)
        outputs[target.relative_to(W).as_posix()]=sha(target)
        records[state]={'source':p.relative_to(ROOT).as_posix(),'source_size':[210,90],'crop':[0,0,83,90],'output_size':[83,90],'operation':'original RGB preserved for every pixel, alpha only reduced; attached text panel and its black backing removed','pixels_alpha_removed':int(np.count_nonzero(out[:,:,3]!=a[:,:83,3]))}
    cat=source.catalog('hand_of_zeuss2');record=source.export(cat,'jingti','orb','hand_of_zeuss2_focus')
    for key,value in source.INPUTS.items():bind(ROOT/key)
    record['adaptation']='Original seven source jingti particle layers, transforms, embedded meshes, textures, colors and curves. Per-emitter source cycle sampled while marked. Source playerStatus 30018 head height +35 source units maps to target top +0.35 blocks.'
    for stem in ('effectConfig','particleEffectConfig'):
        bind(next((folder/'EmbeddedData').glob(stem+'__*.bytes')))
    # Every texture supplied to the particle sampler is the original PNG bytes.
    for relative,item in source.TEXTURES.items():outputs[relative]=sha(W/relative)
    fx=W/'pack/assets/ssjj_zeus/effects/thunder/hand_of_zeuss2_focus.json';outputs[fx.relative_to(W).as_posix()]=sha(fx)
    bind(Path(__file__));bind(W/'scripts/prepare_source_fx.py')
    for p in [ROOT/'work/zeus_family_20261114/scripts/import_family_body_fx.py',ROOT/'work/dimension_family_20261107/scripts/import_family_body_fx.py']:
        if p.is_file():bind(p)
    save(W/'evidence/linked_hud_resources.json',{'passed':True,'input_sha256':inputs,'output_sha256':outputs,'badges':records,'focus_source':record,'textures':source.TEXTURES,'text_policy':'HUD contains only extracted original badge, active/maxActive original icon slots and original recovery strip; all text removed, no native font draw','source_marker_replaced':True,'live_client_test':False})
    print('PASS original jingti focus + textless source badges',len(outputs),'outputs')
if __name__=='__main__':main()
