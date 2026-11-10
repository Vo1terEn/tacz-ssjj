"""Augment exact current native custom classes; never rebuild old classes from stale sources."""
from build import *

def patch_entries(entries):
 work=W/'build/attachment_bonus_patch';source=work/'before';out=work/'runtime'
 names=['cn/fusionarsenal/EyeAccessoryEnergy.class','cn/ssjjparasite2/client/ParasiteCannon.class','cn/punishmenteye/NofaPower.class']
 for n in names:save(source/n,entries[n])
 cp=[*t.tool_classpath(),t.LIBRARIES/'org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar',t.LIBRARIES/'org/ow2/asm/asm-analysis/9.9.1/asm-analysis-9.9.1.jar']
 classes=t.compile_java([W/'tools/AttachmentBonusPatch.java'],work/'tools',cp,'compile_attachment_bonus_patch')
 t.run_java('AttachmentBonusPatch',[source,out,W/'evidence/attachment_bonus_patch.json'],[classes,*cp],'attachment_bonus_patch')
 for n in names:entries[n]=(out/n).read_bytes()
 return json.loads((W/'evidence/attachment_bonus_patch.json').read_text('utf8'))
