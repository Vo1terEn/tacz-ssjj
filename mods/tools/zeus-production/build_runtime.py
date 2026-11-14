from pathlib import Path
import importlib.util,zipfile
from prepare import W,ROOT,VERSION,PAIR,read,save,sha
spec=importlib.util.spec_from_file_location('zeus_tooling',ROOT/'work/zeus_hand2_20261113/scripts/build_runtime.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);t=module.t
t.ROOT=W;t.BUILD=W/'build';t.EVIDENCE=W/'evidence';t.VERSION=VERSION
BASE=W/'baseline'/PAIR['mod']
def build():
 sources=sorted((W/'src').rglob('*.java'))
 namedcp=t.named_classpath()+[BASE]
 named=t.compile_named(sources,destination=W/'build/named',extra_classpath=[BASE],label='production_compile')
 tools=t.compile_java([ROOT/'work/dimension_star_20261107/tools'/n for n in ('FreshRemap.java','FreshRuntimeLinks.java')]+list((W/'tools').glob('*.java')),W/'build/tools',t.tool_classpath()+[t.LIBRARIES/'org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar'],'tools_compile')
 namedfile=W/'build/named_classpath.txt';namedfile.write_text('\n'.join(map(str,namedcp))+'\n','utf8')
 runtimecp=t.runtime_classpath()+[BASE];runtimefile=W/'build/runtime_classpath.txt';runtimefile.write_text('\n'.join(map(str,runtimecp))+'\n','utf8')
 runtime=W/'build/runtime.jar'
 t.run_java('FreshRemap',[t.OFFICIAL_MAPPING,t.MERGED_MAPPING,named,runtime,namedfile,W/'evidence/runtime_remap.json'],[tools]+t.tool_classpath(),'remap')
 t.run_java('FreshRuntimeLinks',[runtime,runtimefile,W/'evidence/runtime_links.json'],[tools]+t.tool_classpath(),'runtime_links')
 # The installed Advent baseline already contains both verified hooks. Keep one copy of each.
 with zipfile.ZipFile(BASE) as base:
  for name in ('cn/punishmenteye/EyeCrosshair.class','cn/judgfire/JudgmentSkillKey.class'):
   target=W/'build/bootstrap'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(base.read(name))
 bootstrap=W/'build/bootstrap/cn/punishmenteye/EyeCrosshair.class'
 combined=W/'build/runtime_with_hooks.jar'
 with zipfile.ZipFile(combined,'w',zipfile.ZIP_DEFLATED) as out:
  with zipfile.ZipFile(runtime) as z:
   for name in z.namelist():out.writestr(name,z.read(name))
  for p in sorted((W/'build/bootstrap').rglob('*.class')):out.write(p,p.relative_to(W/'build/bootstrap').as_posix())
 t.run_java('FreshRuntimeLinks',[combined,runtimefile,W/'evidence/runtime_hook_links.json'],[tools]+t.tool_classpath(),'runtime_hook_links')
 with zipfile.ZipFile(W/'build/named.jar','w',zipfile.ZIP_DEFLATED) as z:
  for p in sorted(named.rglob('*.class')):z.write(p,p.relative_to(named).as_posix())
 with zipfile.ZipFile(runtime) as z:classes=sorted(z.namelist())
 save(W/'evidence/runtime_build.json',{'passed':True,'version':VERSION,'classes':classes,'runtime_sha256':sha(runtime),'bootstrap_sha256':sha(bootstrap),'hooks_sha256':{p.relative_to(W/'build/bootstrap').as_posix():sha(p) for p in sorted((W/'build/bootstrap').rglob('*.class'))},'input_sha256':{p.relative_to(W).as_posix():sha(p) for p in sources+[Path(__file__),*sorted((W/'tools').glob('*.java'))]},'runtime_links':read(W/'evidence/runtime_links.json'),'hook_links':read(W/'evidence/runtime_hook_links.json'),'live_client_test':False})
 print('PASS Java17 compile/remap/actual TaCZ runtime links')
if __name__=='__main__':build()
