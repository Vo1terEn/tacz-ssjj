"""Index original icon pixels for the native smithing-panel presentation bridge."""
from pathlib import Path
import io,json,zipfile,hashlib
from PIL import Image
W=Path(__file__).resolve().parent
NAMESPACES={'eyeofpunishment','judgfire','ssjj_dark','ssjj_dimension','ssjjparasite2','ssjjrpg','eye_judgment_arsenal'}
def build():
 entries={}
 for filename in ('mod.jar','gunpack.zip'):
  with zipfile.ZipFile(W/'baseline'/filename) as z:entries.update({n:z.read(n) for n in z.namelist() if not n.endswith('/')})
 entries.update({p.relative_to(W/'resources').as_posix():p.read_bytes() for p in (W/'resources').rglob('*') if p.is_file()})
 textures={};items={};sources={}
 for n,value in sorted(entries.items()):
  a=n.split('/')
  if len(a)<4 or a[0]!='assets' or a[1] not in NAMESPACES:continue
  if a[2]=='textures' and n.endswith('.png'):
   image=Image.open(io.BytesIO(value)).convert('RGBA');box=image.getchannel('A').getbbox()
   if box is None:continue
   x,y,r,b=box;textures[a[1]+':'+ '/'.join(a[2:])]=[image.width,image.height,x,y,r-x,b-y];sources[n]=hashlib.sha256(value).hexdigest()
  if a[2:4]==['models','item'] and n.endswith('.json'):
   model=json.loads(value);texture=model.get('textures',{}).get('layer0')
   if texture:
    ns,p=texture.split(':',1) if ':' in texture else ('minecraft',texture)
    items[a[1]+':'+ '/'.join(a[4:])[:-5]]=ns+':textures/'+p+'.png'
 calibration=json.loads((W/'reference/native_ground_calibration.json').read_text('utf8'));assert calibration['source_native_report_sha256']==hashlib.sha256((W/'reference/native_ground_source_frames_1.2.0.json').read_bytes()).hexdigest();assert calibration['baseline_pack_sha256']==hashlib.sha256((W/'baseline/gunpack.zip').read_bytes()).hexdigest()
 ground={}
 for display,offset in calibration['profiles'].items():
  ns,path=display.split(':',1);raw=json.loads(entries[f'assets/{ns}/display/guns/{path}.json']);slot=raw['slot'];sns,sp=slot.split(':',1) if ':' in slot else(ns,slot);key=sns+':textures/'+sp+'.png';assert key not in ground or ground[key]==offset;ground[key]=offset
 raw_frames=json.loads((W/'reference/native_ground_source_frames_1.2.0.json').read_text('utf8'))
 preview_guns={}
 for frame in raw_frames['profiles']:
  display=frame['display'];ns,path=display.split(':',1);raw=json.loads(entries[f'assets/{ns}/display/guns/{path}.json']);slot=raw['slot'];sns,sp=slot.split(':',1) if ':' in slot else(ns,slot);key=sns+':textures/'+sp+'.png'
  body=frame['native_ground_body'];extent=[b-a for a,b in zip(body['min'],body['max'])];radius=sum((v*.5)**2 for v in extent)**.5;assert radius>0
  preview_guns[key]=[.52/radius,-(.01+extent[1]*.5)]
 data={'preview_guns':preview_guns,'ground':ground,'schema':2,'boundary':'Native rotating 3D smithing preview; source body sphere fit preserves all angles. Inventory icons only remain in hand/ground contexts for non-guns.','textures':textures,'items':items}
 out=W/'resources/assets/ssjjrpg/item_presentation.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 evidence=W/'evidence/item_rendering_assets.json';evidence.parent.mkdir(parents=True,exist_ok=True);evidence.write_text(json.dumps({'passed':True,'textures':len(textures),'generated_item_icons':items,'original_texture_sha256':sources},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 print('PASS item presentation original-icon bounds',len(textures))
if __name__=='__main__':build()
