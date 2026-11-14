"""Compile/remap only three existing class families; never package/install."""
from pathlib import Path
import importlib.util,json,zipfile
W=Path(__file__).resolve().parents[1];P=W.parents[1]
spec=importlib.util.spec_from_file_location('existing_toolchain',P/'work/zeus_hand2_20261113/scripts/build_runtime.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);t=module.t
t.ROOT=W;t.BUILD=W/'build/fx';t.EVIDENCE=W/'evidence';t.VERSION='1.1.47-zeus2fx1'
BASE=next((W/'baseline/mods').glob('*.jar'))
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n','utf8')
def build():
    sources=sorted((W/'src').rglob('*.java'));assert len(sources)==3
    old=t.named_classpath;t.named_classpath=lambda:old()+[BASE]
    named=t.compile_named(sources,destination=t.BUILD/'named',label='fx_production_compile')
    toolsrc=[P/'work/dimension_star_20261107/tools'/f for f in ('FreshRemap.java','FreshRuntimeLinks.java')]
    tools=t.compile_java(toolsrc,t.BUILD/'tools',t.tool_classpath(),'fx_compile_tools')
    namedcp=t.BUILD/'named_classpath.txt';runtimecp=t.BUILD/'runtime_classpath.txt'
    namedcp.write_text('\n'.join(str(p)for p in t.named_classpath())+'\n','utf8')
    runtimecp.write_text('\n'.join(str(p)for p in t.runtime_classpath()+[BASE])+'\n','utf8')
    runtime=t.BUILD/'runtime.jar';remap=W/'evidence/body_fx_remap.json';links=W/'evidence/body_fx_runtime_links.json'
    t.run_java('FreshRemap',[t.OFFICIAL_MAPPING,t.MERGED_MAPPING,named,runtime,namedcp,remap],[tools]+t.tool_classpath(),'fx_remap')
    t.run_java('FreshRuntimeLinks',[runtime,runtimecp,links],[tools]+t.tool_classpath(),'fx_runtime_links')
    namedjar=t.BUILD/'named.jar'
    with zipfile.ZipFile(namedjar,'w',zipfile.ZIP_DEFLATED)as z:
        for p in sorted(named.rglob('*.class')):z.write(p,p.relative_to(named).as_posix())
    with zipfile.ZipFile(runtime)as z:classes=sorted(z.namelist())
    allowed=('cn/ssjjzeus/client/ZeusMeshRenderer','cn/ssjjdimension/client/DimensionParticles','cn/ssjjdimension/client/DimensionParticleEffects')
    assert all(any(n==p+'.class'or n.startswith(p+'$')for p in allowed)for n in classes)
    report={'passed':True,'java':'17','runtime_overlay':runtime.relative_to(W).as_posix(),'runtime_overlay_sha256':t.sha256(runtime),'named_overlay':namedjar.relative_to(W).as_posix(),'class_overrides':classes,'input_sha256':{**{p.relative_to(P).as_posix():t.sha256(p)for p in sources+toolsrc},BASE.relative_to(W).as_posix():t.sha256(BASE),'actual_tacz':t.sha256(module.actual_tacz())},'actual_tacz':str(module.actual_tacz()),'remap_report':remap.relative_to(W).as_posix(),'runtime_links':json.loads(links.read_text('utf8'))}
    save(W/'evidence/body_fx_build.json',report)
    print(json.dumps({'passed':True,'classes':len(classes),'runtime_overlay':report['runtime_overlay'],'links':report['runtime_links']}))
    return report
if __name__=='__main__':build()
