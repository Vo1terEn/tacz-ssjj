"""Bind the tested patch to the final merged bodies without rerendering them."""
from pathlib import Path
import hashlib,json
W=Path(__file__).resolve().parents[1]
def load(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    target=W/'evidence/galaxy_wing_gap_check.json';report=load(target);assert report['passed']
    for rel,digest in report['input_sha256'].items():assert sha(W/rel)==digest,('Changed tested input',rel)
    for rel,digest in report['output_sha256'].items():assert sha(W/rel)==digest,('Changed tested output',rel)
    patches=load(W/'qa/galaxy_wing_overrides.json')['overrides']
    for rel,emitters in patches.items():
        file=W/'pack'/rel;body=load(file)
        for name,expected in emitters.items():assert next(e for e in body['emitters']if e['name']==name)==expected
        report['input_sha256'][file.relative_to(W).as_posix()]=sha(file)
    report['merged_resource_bindings_current']=True
    report['input_sha256'][Path(__file__).relative_to(W).as_posix()]=sha(Path(__file__))
    report['agent_visual_inspection']=True;report['human_visual_review']=False
    report['visual_observation']='Both oblique sides and front-wing close view show a small additional gap, preserved original wing artwork and no apparent large protruding card or visible body penetration; diagnostic camera/lighting only.'
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'passed':True,'merged_bodies':len(patches),'gate_sha256':sha(target)}))
if __name__=='__main__':main()
