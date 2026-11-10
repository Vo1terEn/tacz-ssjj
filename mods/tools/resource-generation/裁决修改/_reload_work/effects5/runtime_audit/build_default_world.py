import os
from pathlib import Path
import subprocess,shutil,zipfile,json,hashlib
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
AUTHOR=Path(os.environ.get("SSJJ_ADDON_BUILD", "external-runtime/addon_build"))
LIB=Path(os.environ.get("SSJJ_LIBRARIES", "external-runtime/libraries"));MOD=next((ROOT/'换弹动作修订版/mods').glob('*reload4.jar'))
ASM=[p for p in (LIB/'org/ow2/asm').rglob('*.jar') if p.parent.name=='9.9.1']
REST=[p for p in LIB.rglob('*.jar') if not any(k in p.as_posix() for k in ('/net/minecraft/','/net/minecraftforge/forge/','natives-','/org/ow2/asm/'))]
NAMED=[AUTHOR/n for n in ('patched_mc-named.jar','mc-named.jar','forge-named.jar','tacz-named.jar')]
def run(args,name):
 path=HERE/(name+'.args');path.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args[1:]),encoding='utf8' if 'javac' in Path(args[0]).name else 'gb18030');r=subprocess.run([args[0],'@'+str(path)],capture_output=True,text=True,encoding='utf8',errors='replace');(HERE/(name+'.log')).write_text(r.stdout+r.stderr,encoding='utf8');assert r.returncode==0,r.stdout+r.stderr;print(r.stdout);return r.stdout
def main():
 subprocess.run([shutil.which('python'),str(HERE/'export_native_world.py')],check=True)
 classes=HERE/'default_world_named_classes'
 assert classes.resolve().parent==HERE.resolve()
 if classes.exists():shutil.rmtree(classes)
 classes.mkdir()
 cp=';'.join(map(str,[HERE/'stub_classes',MOD,*NAMED,*ASM,*REST]))
 run([shutil.which('javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*[str(HERE/'src/cn/judgfire'/name) for name in ('DefaultWorldFlames.java','NativeWorldFlames.java','WorldFlameRenderType.java','NativeParticleShape.java','SkinWorldFlames.java','SkinWorldRenderType.java')],*[str(HERE.parent/'src/cn/judgfire'/name)for name in('NativeEmitterClock.java','CustomParticleSchedule.java','CustomParticleTransform.java')]],'compile_default_world')
 with zipfile.ZipFile(HERE/'default-world-named.jar','w') as z:
  for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
 toolcp=';'.join(map(str,[AUTHOR,*ASM,*list((LIB/'net/minecraftforge/srgutils').rglob('*.jar'))]))
 maps=[LIB/'de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412-mappings-merged.txt',LIB/'net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-mappings.txt']
 runtime=HERE/'default-world-runtime.jar'
 run([shutil.which('java'),'-cp',toolcp,'RuntimeRemap',*map(str,maps),'reverse',str(HERE/'default-world-named.jar'),*map(str,NAMED),str(runtime)],'remap_default_world')
 out=HERE/'default_world_classes'
 assert out.resolve().parent==HERE.resolve()
 if out.exists():shutil.rmtree(out)
 out.mkdir()
 with zipfile.ZipFile(runtime) as z:
  for n in z.namelist():
   p=out/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
 report={'artifact':str(runtime),'sha256':hashlib.sha256(runtime.read_bytes()).hexdigest(),'classes':sorted(str(p.relative_to(out)) for p in out.rglob('*.class')),'resources':str(HERE/'resources'),'source':[str(HERE/'src/cn/judgfire'/name) for name in ('DefaultWorldFlames.java','NativeWorldFlames.java','WorldFlameRenderType.java')],'configuration':'POSITION_COLOR_TEX / registered world_native GLSL; material native alpha or additive; LEQUAL / COLOR_WRITE / source material culling; refraction samples copied main color texture','camera_transform':'caller has already translated -camera once; no repeated camera subtraction','required_hooks':['laser','impact','mark','crown','strike','burst'],'optional_reload':'DefaultWorldFlames.reload(ResourceManager); mark otherwise loads once lazily','visual_validation':'Offline source-node projection only. No further game launch permitted for this iteration; do not label projections as GL frames or frame-exact acceptance.','source_alternatives':{'body':'Complete 1451 E001 3mesh+4bodyPS; instance Euler0; source pure-red material6 and fire alpha1','impact':'a966 same-default-family source selected by native shape/color vs reference comparison'},'known_reconstruction_limits':['CrownStrike packet has no owner id; current route checks local held stack.','Native mode0/1/2/4/5/6 math ported from source DXBC; updated shader compiled/linked on isolated hidden GL3.2 driver, not Minecraft.','Crown uses actor feet + native Y=2.8. Projectile starts at uniquely identified native crown center, otherwise preserves packet.start; original arrival duration retained.','E011/12/13 full fireball/core/glow/refraction/real ring mesh retained; E016 restored same-family old mesh matches E081 vertices/triangles/PS/TRS; E014 selects genuine same-family a966 source (4 mesh layers/6 emitters), restores vertex COLOR and initial XYZ rotation as well as native curves; E017 mark/trails retained.']}
 (HERE/'default_world_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
 print('Default world renderer runtime classes and separate resources ready for parent review.')
if __name__=='__main__':main()
