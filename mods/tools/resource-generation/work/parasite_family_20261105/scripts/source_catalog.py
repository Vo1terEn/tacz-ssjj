from pathlib import Path
import csv,json,hashlib
def original_reader(root,inputs):
 allowed=[(root/'寄生者'/x).resolve()for x in ['本体','皮肤/寒冬使者_frost','皮肤/星舰']]
 def read(p):
  p=p.resolve();assert any(p.is_relative_to(base)for base in allowed),'Only original weapon/skin source files are allowed'
  raw=p.read_bytes();inputs[p.relative_to(root).as_posix()]=hashlib.sha256(raw).hexdigest();return raw
 return read
class VariantCatalog:
 def __init__(self,root,read_bytes):
  self.root=root;self.read_bytes=read_bytes;self.by_id={};self.cache={};self.scope=''
  for base,prefix in [('本体','parasitic_sniper'),('皮肤/寒冬使者_frost','skin_parasitic_sniper_frost'),('皮肤/星舰','skin_parasitic_sniper_ship')]:
   directory=root/'寄生者'/base
   for row in csv.DictReader(read_bytes(directory/'资源索引.csv').decode('utf-8-sig').splitlines()):
    for n in row['导出文件路径'].split(';'):
     n=n.replace('\\','/')
     if not n.startswith('导出素材/'+prefix):continue
     p=directory/n
     try:ident=int(p.stem.rsplit('__',1)[1])
     except(ValueError,IndexError):continue
     self.by_id.setdefault(ident,[]).append(p)
 def find(self,ident,suffix='.json',bundle=None):
  paths=list(dict.fromkeys(p for p in self.by_id[int(ident)]if p.suffix.lower()==suffix))
  scoped=[p for p in paths if(bundle or self.scope)in p.parts]
  if scoped:paths=scoped
  assert paths,(ident,suffix,self.scope)
  if len(paths)>1:assert len({hashlib.sha256(self.read_bytes(p)).hexdigest()for p in paths})==1,('Ambiguous source pointer',ident,paths)
  return paths[0]
 def json(self,p):
  if p not in self.cache:self.cache[p]=json.loads(self.read_bytes(p).decode('utf-8-sig'))
  return self.cache[p]
