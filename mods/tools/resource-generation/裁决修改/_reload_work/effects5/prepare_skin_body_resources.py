"""Package each skin's native candidate and byte-exact provided textures; no recoloring."""
from pathlib import Path
import hashlib,json,shutil
from skin_effect_overrides import apply_skin_body_overrides
HERE=Path(__file__).resolve().parent
SOURCE=HERE/'asset_audit/skin_sources'
RES=HERE/'resources/assets/judgfire'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    configs=[];textures={}
    for skin in ('qinglong','frozen','lightning'):
        path=SOURCE/skin/'body_runtime_candidate.json'
        d=json.loads(path.read_text('utf8'))
        apply_skin_body_overrides(skin,d)
        for m in d['materials']:
            original=m['textures'][0];src=(path.parent/original).resolve()
            assert src.is_relative_to(SOURCE.resolve()) and src.is_file()
            digest=sha(src)
            dst=RES/'textures/fx/skins_body'/(digest+'.png')
            dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dst)
            assert sha(dst)==digest
            m['textures'][0]='judgfire:textures/fx/skins_body/'+dst.name
            textures[digest]={'source':str(src),'source_pixel_key':src.stem,'packaged':str(dst),'sha256':digest}
        d['integration']={'route':'BodyEffects !pixel skin1/2/3','source_candidate_sha256':sha(path),'native_geometry_and_texture_pixels_unchanged':True,'custom_engine_semantics':'Explicit reconstruction; see consumerExtensions/curveEvaluationStatus'}
        d['candidateOnly']=False;d['runtimeUnchanged']=False
        dst=RES/'fx/skins'/(skin+'_body.json');dst.parent.mkdir(parents=True,exist_ok=True)
        dst.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
        configs.append({'skin':skin,'source_sha256':sha(path),'packaged':str(dst),'packaged_sha256':sha(dst),'materials':len(d['materials']),'layers':len(d['groups'])+len(d['emitters'])})
    report={'configs':configs,'unique_provided_png':len(textures),'textures':list(textures.values()),'scope':'Own continuous body/head source per skin; no original-skin recolors or gameplay changes.'}
    (SOURCE/'body_resources_packaged.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'configs':configs,'unique_provided_png':len(textures)},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
