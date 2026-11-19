var O=Java.type('org.objectweb.asm.Opcodes');
var V=Java.type('org.objectweb.asm.tree.VarInsnNode');
var C=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var L=Java.type('org.objectweb.asm.tree.InsnList');
var OWNER='cn/ssjjcompat/WorkbenchHandFilter';
var DESC='(Lcom/tacz/guns/client/gui/GunSmithTableScreen;)V';
function handFilter(node){
 var methods=[];
 for(var i=0;i<node.methods.size();i++){
  var m=node.methods.get(i);if(m.name==='classifyRecipes'&&m.desc==='()V')methods.push(m);
 }
 if(methods.length!==1)throw new Error('SSJJRPG: unsupported workbench classification '+methods.length);
 var method=methods[0],old=method.instructions.toArray(),before=0,after=0,returns=0;
 for(var j=0;j<old.length;j++){
  var n=old[j];if(n.getOpcode()===O.RETURN)returns++;
  if(n instanceof C&&n.owner===OWNER&&n.desc===DESC){if(n.name==='beforeClassify')before++;if(n.name==='afterClassify')after++;}
 }
 if(before||after){if(before===1&&after===returns)return node;throw new Error('SSJJRPG: partial workbench hook');}
 if(returns===0)throw new Error('SSJJRPG: missing workbench return');
 var entry=new L();entry.add(new V(O.ALOAD,0));entry.add(new C(O.INVOKESTATIC,OWNER,'beforeClassify',DESC,false));method.instructions.insert(entry);
 for(var k=0;k<old.length;k++)if(old[k].getOpcode()===O.RETURN){
  var exit=new L();exit.add(new V(O.ALOAD,0));exit.add(new C(O.INVOKESTATIC,OWNER,'afterClassify',DESC,false));method.instructions.insertBefore(old[k],exit);
 }
 method.maxStack=Math.max(method.maxStack,1);return node;
}
function aresArmor(node){
 var methods=[];
 for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(m.name==='isSuitableForMainHand'&&m.desc==='(Lcom/tacz/guns/crafting/GunSmithTableRecipe;)Z')methods.push(m);}
 if(methods.length!==1)throw new Error('SSJJRPG: unsupported armor workbench predicate '+methods.length);
 var m=methods[0],existing=0;for(var i=0;i<m.instructions.size();i++){var ins=m.instructions.get(i);if(ins instanceof C&&ins.owner==='cn/ssjjares/client/AresWorkbenchFilter'&&ins.name==='accepts')existing++;}
 if(existing===1)return node;if(existing)throw new Error('SSJJRPG: duplicate armor predicate');
 var J=Java.type('org.objectweb.asm.tree.JumpInsnNode'),N=Java.type('org.objectweb.asm.tree.InsnNode'),B=Java.type('org.objectweb.asm.tree.LabelNode'),F=Java.type('org.objectweb.asm.tree.FrameNode');
 var go=new B(),entry=new L();entry.add(new V(O.ALOAD,0));entry.add(new V(O.ALOAD,1));entry.add(new C(O.INVOKESTATIC,'cn/ssjjares/client/AresWorkbenchFilter','accepts','(Lcom/tacz/guns/client/gui/GunSmithTableScreen;Lcom/tacz/guns/crafting/GunSmithTableRecipe;)Z',false));entry.add(new J(O.IFEQ,go));entry.add(new N(O.ICONST_1));entry.add(new N(O.IRETURN));entry.add(go);entry.add(new F(O.F_SAME,0,null,0,null));m.instructions.insert(entry);m.maxStack=Math.max(m.maxStack,2);return node;
}
function initializeCoreMod(){return {ssjj_workbench_hand_filter:{target:{type:'CLASS',name:'com.tacz.guns.client.gui.GunSmithTableScreen'},transformer:function(node){return aresArmor(handFilter(node));}}};}
