var O=Java.type('org.objectweb.asm.Opcodes');
var L=Java.type('org.objectweb.asm.tree.InsnList');
var V=Java.type('org.objectweb.asm.tree.VarInsnNode');
var C=Java.type('org.objectweb.asm.tree.MethodInsnNode');
function dimensionCache(node){
 var changed=0,existing=0;
 for(var j=0;j<node.methods.size();j++){
  var m=node.methods.get(j);if(m.name!=='eval'||m.desc!=='(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;)V')continue;
  for(var it=m.instructions.iterator();it.hasNext();){var n=it.next();if(n instanceof C&&n.owner==='cn/ssjjdimension/DimensionNativeStats'&&n.name==='cache')existing++;}
  if(existing===1)return node;
  for(var it=m.instructions.iterator();it.hasNext();){var n=it.next();if(n.getOpcode()!==O.RETURN)continue;
   var p=new L();p.add(new V(O.ALOAD,0));p.add(new V(O.ALOAD,1));p.add(new C(O.INVOKESTATIC,'cn/ssjjdimension/DimensionNativeStats','cache','(Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;Lnet/minecraft/world/item/ItemStack;)V',false));m.instructions.insertBefore(n,p);changed++;
  }m.maxStack=Math.max(m.maxStack,2);
 }
 if(changed!==1||existing!==0)throw new Error('Unsupported Dimension native cache layout: '+changed+'/'+existing);
 return node;
}
function initializeCoreMod(){return {dimension_native_config:{target:{type:'CLASS',name:'com.tacz.guns.resource.modifier.AttachmentCacheProperty'},transformer:dimensionCache}};}
