"""Blockbench free-mesh and OBJ from the exact runtime mesh; no triangle conversion."""
from pathlib import Path
import json,base64,uuid
from PIL import Image
E=Path(__file__).resolve().parents[1];P=E.parent;O=E/'模型';O.mkdir(exist_ok=True)
AS=E/'gunpack/assets/eyeofpunishment';mesh=json.loads((AS/'rig/weapon_mesh.json').read_text(encoding='utf-8'));tex=AS/'textures/gun/uv/notdistinguish.png';width,height=Image.open(tex).size
uid=lambda s:str(uuid.uuid5(uuid.NAMESPACE_URL,'eyeofpunishment/'+s))
geo=json.loads((AS/'geo_models/gun/notdistinguish_geo.json').read_text(encoding='utf-8'))['minecraft:geometry'][0];pivots={b['name']:b['pivot'] for b in geo['bones']}
elements=[];lines=['# Exact runtime mesh, TaCZ model pixels; no animations in OBJ','mtllib notdistinguish2.mtl'];offset=0
for part in mesh['parts']:
 name=part['bone'];vertices=[[x+y for x,y in zip(v,pivots[name])] for v in part['vertices']];uv=part['uv'];faces=part['faces']
 elements.append({'name':name,'type':'mesh','uuid':uid(name),'origin':[0,0,0],'rotation':[0,0,0],'vertices':{str(i):p for i,p in enumerate(vertices)},'faces':{str(i):{'vertices':[str(v) for v in f],'uv':{str(v):[uv[v][0]*width,uv[v][1]*height] for v in f},'texture':0} for i,f in enumerate(faces)}})
 lines+=['v '+' '.join(map(str,p)) for p in vertices];lines+=['vt '+str(p[0])+' '+str(1-p[1]) for p in uv];lines+=['vn '+' '.join(map(str,p)) for p in part['normals']];lines+=['g '+name,'usemtl source_base'];lines+=['f '+' '.join(f'{v+offset+1}/{v+offset+1}/{v+offset+1}' for v in f) for f in faces];offset+=len(vertices)
doc={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'天罚之眼_贰_运行网格','model_identifier':'notdistinguish2','resolution':{'width':width,'height':height},'elements':elements,'outliner':[{'name':'root','uuid':uid('root'),'origin':[0,0,0],'children':[x['uuid'] for x in elements]}],'textures':[{'name':'notdistinguish2.png','id':'0','uuid':uid('texture'),'width':width,'height':height,'uv_width':width,'uv_height':height,'source':'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode()}]}
(O/'天罚之眼_贰_可编辑.bbmodel').write_text(json.dumps(doc,ensure_ascii=False),encoding='utf-8');(O/'notdistinguish2.obj').write_text('\n'.join(lines),encoding='utf-8');(O/'notdistinguish2.mtl').write_text('newmtl source_base\nKd 1 1 1\nmap_Kd notdistinguish2.png\n',encoding='utf-8');(O/'notdistinguish2.png').write_bytes(tex.read_bytes())
(O/'说明.md').write_text('此BBMODEL与OBJ直接来自本轮运行网格，保留五个刚性分组和原UV；供形状/贴图编辑，不包含TaCZ动作。动作唯一可编辑源在gunpack的动画JSON，源骨骼/蒙皮/ANM从项目本体目录只读引用。修改模型须同步运行rig与可编辑骨骼，不要把BBMODEL当成自动构建输入。',encoding='utf-8')
print('Editable mesh exported')
