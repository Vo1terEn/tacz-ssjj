from pathlib import Path
import hashlib,json,zipfile,shutil
W=Path(__file__).resolve().parents[1]
ROOT=W.parents[1]
GAME=Path('dependencies/minecraft/versions/1.20.1-Forge_47.4.28')
VERSION='1.1.49'
PAIR={'mod':'mods/[SSJJRPG]生死狙击冒险枪包.jar','pack':'tacz/[SSJJRPG]生死狙击冒险枪包.zip'}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def read(p):return json.loads(Path(p).read_text('utf8'))
def save(p,v):
 p=Path(p);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2,allow_nan=False)+'\n','utf8')
def main():
 assert not (W/'manifests/baseline.json').exists()
 baseline={'version':read(ROOT/'交付/修改清单及校验.json')['version'],'target_version':VERSION,'pair':{},'delivery_pair':{}}
 comparison={'passed':True,'reason':'TaCZ 1.2 converted wrapped delivery gunpack into a root-layout installed gunpack; independently freeze both raw preimages','assets_identical':False}
 for kind,rel in PAIR.items():
  actual=GAME/rel;delivery=ROOT/'交付'/rel
  for base,source in [('baseline',actual),('baseline_delivery',delivery)]:
   target=W/base/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);assert sha(target)==sha(source)
  baseline['pair'][kind]={'path':rel,'sha256':sha(actual),'bytes':actual.stat().st_size}
  baseline['delivery_pair'][kind]={'path':rel,'sha256':sha(delivery),'bytes':delivery.stat().st_size}
  if kind=='mod':assert sha(actual)==sha(delivery),'Unreviewed runtime disagreement'
  else:
   with zipfile.ZipFile(actual) as installed,zipfile.ZipFile(delivery) as original:
    assert installed.testzip() is None and original.testzip() is None
    def assets(z):
     result={}
     for n in z.namelist():
      if n.endswith('/'):continue
      if n.startswith('assets/'):key=n
      elif '/assets/' in n:key='assets/'+n.split('/assets/',1)[1]
      else:continue
      data=z.read(n);assert key not in result or result[key]==data
      result[key]=data
     return result
    current=assets(installed);previous=assets(original);assert current==previous,'Gunpack asset content changed outside known layout conversion'
    old_hashes={hashlib.sha256(original.read(n)).hexdigest() for n in original.namelist() if not n.endswith('/')}
    flattened=[n for n in installed.namelist() if n.startswith('data/') and not n.endswith('/')]
    assert all(hashlib.sha256(installed.read(n)).hexdigest() in old_hashes for n in flattened),'Unknown converted gameplay data'
    comparison.update({'assets_identical':True,'asset_files':len(current),'flattened_data_files_matched_to_original':len(flattened),'raw_instance_sha256':sha(actual),'raw_delivery_sha256':sha(delivery)})
    assert len(installed.namelist())==len(set(installed.namelist()))
    for n in installed.namelist():
     if n.startswith(('assets/','data/')) and not n.endswith('/'):
      out=W/'baseline_assets'/n;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(installed.read(n))
 for d in ('pack','src','qa','tools','evidence','previews','scripts'):(W/d).mkdir(exist_ok=True)
 save(W/'evidence/baseline_conversion.json',comparison)
 save(W/'manifests/baseline.json',baseline)
 print('PASS frozen canonical installed baseline and independently preserved raw delivery '+baseline['version'])
if __name__=='__main__':main()
