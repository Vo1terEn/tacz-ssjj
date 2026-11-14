"""Overlay only reviewed native config/attribute instruction sites from the current frozen jar."""
from pathlib import Path
import zipfile
from prepare import W, read, save, sha
import build_runtime as build

CLASSES={'cn/fusionarsenal/config/ArsenalConfig.class','cn/judgfire/EnergyScaling.class','cn/ssjjcompat/AttachmentBonusRuntime.class'}
def main():
    t=build.t
    runtime=W/'build/runtime.jar'
    built=read(W/'evidence/runtime_build.json');assert built['passed'] and built['runtime_sha256']==sha(runtime)
    for name,digest in built['input_sha256'].items():assert sha(W/name)==digest,('Stale build',name)
    source=W/'tools/ZeusConfigPatches.java'
    tree=t.LIBRARIES/'org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar'
    cp=t.tool_classpath()+[tree]
    tools=t.compile_java([source],W/'build/config_patch_tools',cp,'config_patch_compile')
    patchdir=W/'build/config_patches';details=W/'evidence/config_patch_methods.json'
    print(t.run_java('ZeusConfigPatches',[build.BASE,patchdir,details],[tools]+cp,'config_patch_apply').strip())
    patches=sorted(patchdir.rglob('*.class'));assert {p.relative_to(patchdir).as_posix() for p in patches}==CLASSES
    jar=W/'build/config_patches.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for p in patches:z.write(p,p.relative_to(patchdir).as_posix())
    runtimecp=[runtime,build.BASE,*t.runtime_classpath()]
    cpfile=W/'build/config_patch_runtime_cp.txt';cpfile.write_text('\n'.join(map(str,runtimecp))+'\n','utf8')
    t.run_java('FreshRuntimeLinks',[jar,cpfile,W/'evidence/config_patch_links.json'],[W/'build/tools']+t.tool_classpath(),'config_patch_links')
    inputs=[Path(__file__),source,tree,runtime,build.BASE,W/'evidence/runtime_build.json']
    report=read(details);assert report['passed']
    save(W/'evidence/config_patch_build.json',{'passed':True,'version':build.VERSION,'classes':sorted(CLASSES),'methods':report['classes'],
      'native_runtime_links':read(W/'evidence/config_patch_links.json'),'baseline_compatibility_fixes_retained':True,
      'input_sha256':{p.relative_to(W).as_posix() if p.is_relative_to(W) else str(p):sha(p) for p in inputs},
      'output_sha256':{'build/config_patches.jar':sha(jar),**{p.relative_to(W).as_posix():sha(p) for p in patches}},'live_client_test':False})
    print('PASS config patch build: exact 3 native classes, all unrelated methods preserved')

if __name__=='__main__':main()
