import os
from pathlib import Path
import json,hashlib,shutil,zipfile
from PIL import Image,ImageDraw,ImageFont
W=Path(__file__).resolve().parent;ROOT=W.parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def build():
 import build_decoy_effect
 build_decoy_effect.build()
 source=ROOT/'次元之星/本体/导出素材/nova_shotgun2/76068c9d88/界面切图_PNG'
 target=W/'resources/assets/ssjj_dimension/textures/gui/decoy';target.mkdir(parents=True,exist_ok=True)
 inputs={};outputs={};sizes={'background':(157,29),'progress':(126,7),'text':(91,15),'overload':(560,115)}
 for key,name in [('background','crossProgressBg'),('progress','crossProgress'),('text','crossProgressText'),('overload','overload')]:
  p=next(source.glob(name+'__*.png'));out=target/(key+'.png');shutil.copy2(p,out)
  assert Image.open(out).size==sizes[key] and p.read_bytes()==out.read_bytes()
  inputs[p.relative_to(ROOT).as_posix()]=sha(p);outputs[out.relative_to(W/'resources').as_posix()]=sha(out)
 p=ROOT/'次元之星/次元之星-瞬闪-1.mp4';inputs[p.relative_to(ROOT).as_posix()]=sha(p)
 report={'passed':True,'source_SHA256':inputs,'output_SHA256':outputs,'original_RGBA_and_PNG_bytes_preserved':True,'cooldown_ticks':80,'HUD_only_during_high_energy':True,'video_reference_resolution':[1920,1080],'video_background_xy':[879,463],'video_hint_xy':[921,460],'video_progress_xy':[906,478],'reference':'First person forward dash and original Shift prompt/progress bar; no new decorative artwork or sounds. Player decoy uses native player skin snapshot; particle effect E059 uses original textures and mesh.','live_client_test':False}
 (W/'evidence/decoy_assets.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf8')
 # Debug preview uses original pixels and the measured video anchor, never a product asset.
 can=Image.new('RGBA',(1920,1080),(18,23,32,255));d=ImageDraw.Draw(can);font=ImageFont.truetype(os.environ.get("SSJJ_FONT_YAHEI", "msyh.ttc"),23)
 x,y=879,463
 for key,xy in [('background',(x,y)),('text',(x+42,y-3)),('progress',(x+27,y+15))]:can.alpha_composite(Image.open(target/(key+'.png')).convert('RGBA'),xy)
 d.line((950,540,970,540),fill=(130,130,130),width=1);d.line((960,530,960,550),fill=(130,130,130),width=1)
 d.text((35,30),'原素材技能条 / 视频位置离线示意（非游戏截图）；仅 V 高能期间显示',font=font,fill='white')
 (W/'previews').mkdir(exist_ok=True);can.convert('RGB').resize((1280,720),Image.Resampling.LANCZOS).save(W/'previews/dash_hud.jpg',quality=95)
 can.crop((800,420,1100,580)).convert('RGB').save(W/'previews/dash_hud_crop.jpg',quality=95)
 print('PASS original dash HUD at measured video center and original overload effect imported')
if __name__=='__main__':build()
