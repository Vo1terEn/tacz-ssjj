var O=Java.type('org.objectweb.asm.Opcodes');
var List=Java.type('org.objectweb.asm.tree.InsnList');
var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
var Insn=Java.type('org.objectweb.asm.tree.InsnNode');
var Method=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label=Java.type('org.objectweb.asm.tree.LabelNode');
function initializeCoreMod(){return {'messages':{'target':{'type':'CLASS','name':'net.minecraft.world.damagesource.DamageSource'},'transformer':function(node){
 var count=0;
 for each(var m in node.methods)if((m.name==='getLocalizedDeathMessage'||m.name==='m_6157_')&&m.desc==='(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/network/chat/Component;'){
  var p=new List(),fallback=new Label();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));
  p.add(new Method(O.INVOKESTATIC,'cn/ssjjcompat/CombatMessages','message','(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/network/chat/Component;',false));
  p.add(new Insn(O.DUP));p.add(new Jump(O.IFNULL,fallback));p.add(new Insn(O.ARETURN));p.add(fallback);p.add(new Insn(O.POP));m.instructions.insert(p);m.maxStack=Math.max(m.maxStack,2);count++;
 }
 if(count!==1)throw new Error('Unsupported damage message layout');return node;
}}};}
