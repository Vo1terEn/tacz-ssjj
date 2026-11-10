"""Presentation-only tuning of the frozen source particle cache; no weapon/hand edits."""
import gzip,io,json,struct,zipfile
import numpy as np
import build_rework as b

W=b.WORK
source=b.read(W.parent/'darkcrystal_visual_20261106/evidence/blade_effect_resources.json')['source_layers']
with zipfile.ZipFile(W/'reference/mod.jar') as z:
    raw=gzip.decompress(z.read('assets/ssjj_dark/runtime/blade_effects.bin.gz'))
    manifest=json.loads(z.read('assets/ssjj_dark/runtime/blade_effects.json'))
data=bytearray(raw);offset=16+12+36
assert struct.unpack_from('>4i',raw)==(1,153,12,64)
changes=[]
for index,row in enumerate(source):
    texture,billboard,vertices,particles=struct.unpack_from('>4i',raw,offset);offset+=16
    offset+=24*4+vertices*5*4+particles*2*4
    states=np.frombuffer(data,dtype='>f4',count=particles*64*20,offset=offset).reshape(particles,64,20)
    name=row['path'].split('/')[-1]
    # Unsupported whole-weapon emissive overlays produce detached opaque-looking
    # blade silhouettes in TaCZ. Keep the original cache/mesh for reversibility,
    # but suppress only those layers rather than moving the physical weapon.
    if name.startswith(('quanshen','jinxian')):gain,size=0.,1.;role='detached_blade_overlay'
    elif name.startswith('glow'):gain,size=.22,.68;role='core_glow'
    elif name.startswith('shine'):gain,size=.55,.75;role='spark'
    elif name.startswith('fire'):gain,size=.55,1.;role='flame'
    elif name.startswith(('smoke','qi','huijing')):gain,size=.45,.80;role='ambient'
    else:gain,size=.6,.85 if billboard!=4 else 1.;role='flow'
    states[:,:,15]*=gain
    for axis in (0,1,2,4,5,6,8,9,10):states[:,:,axis]*=size
    changes.append(dict(index=index,variant=row['variant'],path=row['path'],role=role,alpha_gain=gain,size_gain=size))
    offset+=particles*64*20*4
assert len(changes)==153
dest=W/'resources/mod/assets/ssjj_dark/runtime';dest.mkdir(parents=True,exist_ok=True)
(dest/'blade_effects.bin.gz').write_bytes(gzip.compress(data,compresslevel=9,mtime=0))
manifest['presentation_tuning']={'version':b.VERSION,'layers':changes,'scope':'Only effect opacity and particle local size; no emitter transforms, weapon geometry, hands or camera changes.'}
b.save(dest/'blade_effects.json',manifest)
b.save(W/'evidence/fx_balance_settings.json',{'passed':True,'layers':changes,'original_uncompressed_sha256':__import__('hashlib').sha256(raw).hexdigest(),'tuned_uncompressed_sha256':__import__('hashlib').sha256(data).hexdigest()})
print('TUNED',len(changes),'body effect layers')
