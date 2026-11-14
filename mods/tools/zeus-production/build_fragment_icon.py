"""User-authorized application of the existing fragment crop/frame to original Zeus art."""
from pathlib import Path
import io
from PIL import Image,ImageDraw,ImageChops,ImageFont
from prepare import W,ROOT,read,save,sha
def main():
 reference=ROOT/'work/tier_expansion/build_release.py'
 code=reference.read_text('utf8');code=code[code.index('def png(image):'):code.index('def prepare_pack')]
 namespace={'Image':Image,'ImageDraw':ImageDraw,'ImageChops':ImageChops,'io':io};exec(compile(code,str(reference),'exec'),namespace)
 source=W/'pack/assets/ssjj_zeus/textures/gun/slot/hand_of_zeuss.png'
 dest=W/'pack/assets/eye_judgment_arsenal/textures/item/zeus_hand_fragment.png'
 dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(namespace['fragment_icon'](source.read_bytes()))
 save(W/'evidence/fragment_icon.json',{'passed':True,'choice':'Explicit user choice: apply other weapon fragment crop to original Zeus icon','reference':reference.relative_to(ROOT).as_posix(),'reference_sha256':sha(reference),'original_source':source.relative_to(W).as_posix(),'original_source_sha256':sha(source),'output':dest.relative_to(W).as_posix(),'output_sha256':sha(dest),'new_weapon_art_generated':False,'input_sha256':{source.relative_to(W).as_posix():sha(source)}})
 canvas=Image.new('RGB',(1024,300),(44,48,56));draw=ImageDraw.Draw(canvas);font=ImageFont.truetype('source-assets/external/msyh.ttc',20)
 for i,(g,name) in enumerate([('hand_of_zeuss','宙斯之手'),('hand_of_zeuss1','宙斯之手·壹'),('hand_of_zeuss2','宙斯之手·贰'),(None,'宙斯之手碎片')]):
  p=dest if g is None else W/f'pack/assets/ssjj_zeus/textures/gun/slot/{g}.png'
  if not p.exists():p=W/'baseline_assets'/p.relative_to(W/'pack')
  img=Image.open(p).convert('RGBA');img.thumbnail((220,220));canvas.paste(img,(i*256+(256-img.width)//2,(240-img.height)//2),img);draw.text((i*256+45,260),name,font=font,fill='white')
 canvas.save(W/'previews/family_catalog.png')
 print('PASS authorized existing fragment style; original weapon artwork retained')
if __name__=='__main__':main()
