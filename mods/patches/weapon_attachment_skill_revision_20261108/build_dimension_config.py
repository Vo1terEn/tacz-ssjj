"""Preserve both TOML preimages; append to the sole existing Forge spec and config UI."""
from pathlib import Path
import json,hashlib,zipfile
import tomli
from build import W,t

def sha(raw):return hashlib.sha256(raw).hexdigest()
def flat(values,prefix=""):
 result={}
 for key,value in values.items():
  if isinstance(value,dict):result.update(flat(value,prefix+key+"."))
  else:result[prefix+key]=value
 return result

def build():
 existing_options=json.loads((W/'reference/dimension_config_options.json').read_text('utf8'))
 options=json.loads((W/'reference/attachment_bonus_options.json').read_text('utf8'))
 all_options=existing_options+options
 report={'passed':True,'requires_restart':True,'added_options':len(options),'new_keys':[o['key']for o in options],'files':{}}
 for target in ['delivery','instance']:
  source=W/'baseline'/f'config_{target}.toml';before=source.read_bytes();old=flat(tomli.loads(before.decode('utf-8-sig')))
  assert all(o['key'] in old for o in existing_options),'Current baseline must contain the previously approved Dimension settings'
  assert all(o['key'] not in old for o in options),'New attachment settings already exist in frozen baseline'
  suffix='\n\n# 四把武器普通配件额外加成（排除瞄准镜和激光槽）\n# 修改后重启游戏或服务器；服务端玩法配置同步到客户端。\n[attachment_bonus]\n'
  for option in options:
   assert option['key'].startswith('attachment_bonus.')
   suffix+=f"# {option['comment']}；范围 {option['min']}～{option['max']}\n{option['key'].split('.')[1]} = {option['default']}\n"
  after=before+suffix.encode('utf8');now=flat(tomli.loads(after.decode('utf-8-sig')))
  assert after.startswith(before) and len(now)==len(old)+len(options)
  assert all(now[key]==value and type(now[key]) is type(value) for key,value in old.items())
  assert all(now[o['key']]==o['default'] and type(now[o['key']]) is type(o['default']) for o in options)
  path=W/'candidate_config'/target/'ssjjrpg.toml';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(after)
  report['files'][target]={'source':str(source),'target':str(path),'before_sha256':sha(before),'after_sha256':sha(after),'before_keys':len(old),'after_keys':len(now),'original_byte_prefix_preserved':True,'all_original_keys_types_values_preserved':True}
 with zipfile.ZipFile(W/'baseline/mod.jar')as z:schema=json.loads(z.read('assets/eye_judgment_arsenal/config/screen.json'))
 old_schema_keys=[e['key']for section in schema['sections']for group in section['groups']for e in group['entries']]
 assert all(o['key'] in old_schema_keys for o in existing_options)
 assert not any(o['key'] in old_schema_keys for o in options)
 schema['sections'].append({'title':'普通配件额外加成','groups':[{'title':'各武器每件加成（不计瞄准镜和激光槽）','entries':[{'key':o['key'],'label':o['comment'],'tooltip':[o['comment'],'修改后重启游戏或服务器；服务端配置同步客户端。','配置项：'+o['key']],'min':o['min'],'max':o['max'],'type':'int' if type(o['default']) is int else 'double'}for o in options]}]})
 path=W/'resources/assets/eye_judgment_arsenal/config/screen.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n','utf8')
 (W/'evidence/config_migration_check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
 return report

def patch_config_class(entries):
 """Call after final mod entries merge; inject into those exact current class bytes."""
 name='cn/fusionarsenal/config/ArsenalConfig.class';work=W/'build/dimension_config_patch';work.mkdir(parents=True,exist_ok=True)
 source=work/'ArsenalConfig.before.class';source.write_bytes(entries[name]);output=work/name
 cp=[*t.tool_classpath(),t.LIBRARIES/'org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar',t.LIBRARIES/'org/ow2/asm/asm-analysis/9.9.1/asm-analysis-9.9.1.jar']
 classes=t.compile_java([W/'tools/DimensionConfigBootstrap.java'],work/'tools',cp,'compile_dimension_config_bootstrap')
 t.run_java('DimensionConfigBootstrap',[source,output,W/'evidence/config_bootstrap.json'],[classes,*cp],'dimension_config_bootstrap')
 entries[name]=output.read_bytes()
 return json.loads((W/'evidence/config_bootstrap.json').read_text('utf8'))
def prepare_named_config(output=None):
 """Actual baseline config package translated for headless QA, with identical define injection.
 Default output can be prepended to all QA classpaths; no source reconstruction.
 """
 from build import b
 work=W/'build/dimension_config_qa';work.mkdir(parents=True,exist_ok=True)
 subset=work/'config_runtime.jar';named=work/'config_named.jar'
 with zipfile.ZipFile(W/'baseline/mod.jar')as source,zipfile.ZipFile(subset,'w')as target:
  for name in source.namelist():
   if name.startswith('cn/fusionarsenal/config/')and name.endswith('.class'):target.writestr(name,source.read(name))
 cp=[*t.tool_classpath(),t.LIBRARIES/'org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar',t.LIBRARIES/'org/ow2/asm/asm-analysis/9.9.1/asm-analysis-9.9.1.jar']
 tools=t.compile_java([W/'tools/FreshRemap.java',W/'tools/DimensionConfigBootstrap.java',W/'qa/CreativePrepareNamed.java'],work/'tools',cp,'compile_config_named_tools')
 runtime_cp=t.path_list(work/'runtime_cp.txt',b.runtime_classpath()+[W/'baseline/mod.jar'])
 t.run_java('CreativePrepareNamed',[t.OFFICIAL_MAPPING,t.MERGED_MAPPING,subset,named,runtime_cp,W/'evidence/config_qa_named_translation.json'],[tools,*cp],'config_qa_named_translation')
 destination=Path(output)if output else work/'named'
 with zipfile.ZipFile(named)as source:
  for name in source.namelist():
   path=destination/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(source.read(name))
 name='cn/fusionarsenal/config/ArsenalConfig.class';original=destination/name;before=work/'ArsenalConfig.named.before.class';before.write_bytes(original.read_bytes())
 t.run_java('DimensionConfigBootstrap',[before,original,W/'evidence/config_qa_named_bootstrap.json'],[tools,*cp],'config_qa_named_bootstrap')
 return destination

if __name__=='__main__':
 build()
 print('PASS preserve original TOML bytes and append Dimension settings')
