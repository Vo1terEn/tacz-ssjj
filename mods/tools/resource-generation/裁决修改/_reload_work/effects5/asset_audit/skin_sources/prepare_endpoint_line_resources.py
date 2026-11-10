"""Copy only own skin endpoint line JSON+PNG into new line subdirectories."""
from pathlib import Path
import json,hashlib,shutil
BASE=Path(__file__).resolve().parent;RESOURCE=BASE.parents[1]/'resources/assets/judgfire'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
entries=[]
for skin in ['qinglong','frozen','lightning']:
    src=BASE/skin/'line_runtime_candidate.json';d=json.loads(src.read_text('utf8'));textures=[]
    for m in d['materials']:
        p=(src.parent/m['textures'][0]).resolve();assert p.is_relative_to(BASE.resolve())and p.is_file()
        dest=RESOURCE/'textures/fx/skins/line'/p.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);assert sha(p)==sha(dest)
        m['textures'][0]='judgfire:textures/fx/skins/line/'+p.name;textures.append({'source':str(p),'file':str(dest.relative_to(RESOURCE)).replace('\\','/'),'sha256':sha(dest),'textureSources':m['textureSources']})
    d['candidateOnly']=False;d['runtimeUnchanged']=False;d['packaging']={'sourceCandidateSHA256':sha(src),'note':'Exact provided texture copies; actual mesh/UV/raw retained.'}
    output=RESOURCE/'fx/skins/line'/f'{skin}.json';dump(output,d);entries.append({'skin':skin,'file':str(output.relative_to(RESOURCE)).replace('\\','/'),'sha256':sha(output),'textures':textures})
dump(BASE/'line_runtime_resources_manifest.json',{'schema':'judgment_skin_line_runtime_resources_v1','entries':entries})
print(json.dumps([{'skin':e['skin'],'file':e['file'],'materials':len(e['textures'])}for e in entries]))
