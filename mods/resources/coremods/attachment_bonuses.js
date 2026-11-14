var O=Java.type('org.objectweb.asm.Opcodes');
var L=Java.type('org.objectweb.asm.tree.InsnList');
var V=Java.type('org.objectweb.asm.tree.VarInsnNode');
var C=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var BRIDGE='cn/ssjjcompat/AttachmentBonusRuntime';
function bonusCache(node){
 var matches=0,changes=0;
 for(var j=0;j<node.methods.size();j++){
  var m=node.methods.get(j);if(m.name!=='eval'||m.desc!=='(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;)V')continue;matches++;
  for(var it=m.instructions.iterator();it.hasNext();){var n=it.next();if(n instanceof C&&n.owner===BRIDGE&&n.name==='cache')return node;}
  var insns=m.instructions.toArray();for(var k=0;k<insns.length;k++)if(insns[k].getOpcode()===O.RETURN){
   var p=new L();p.add(new V(O.ALOAD,0));p.add(new V(O.ALOAD,1));p.add(new C(O.INVOKESTATIC,BRIDGE,'cache','(Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;Lnet/minecraft/world/item/ItemStack;)V',false));m.instructions.insertBefore(insns[k],p);changes++;
  }m.maxStack=Math.max(m.maxStack,2);
 }
 if(matches!==1||changes!==1)throw new Error('Unsupported ordinary attachment cache shape '+matches+'/'+changes);return node;
}
function initializeCoreMod(){return {ordinary_attachment_cache:{target:{type:'CLASS',name:'com.tacz.guns.resource.modifier.AttachmentCacheProperty'},transformer:bonusCache}};}
