var O=Java.type('org.objectweb.asm.Opcodes');
var Insns=Java.type('org.objectweb.asm.tree.InsnList');
var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label=Java.type('org.objectweb.asm.tree.LabelNode');
var Insn=Java.type('org.objectweb.asm.tree.InsnNode');
function eyeCosmeticDisplay(node){
 var found=false;
 for each(var method in node.methods){
  if(method.name!=='getGunDisplayId'||method.desc!=='(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/resources/ResourceLocation;')continue;
  found=true;
  for each(var instruction in method.instructions.toArray())if(instruction.getOpcode()===O.INVOKESTATIC&&instruction.owner==='cn/punishmenteye/SkinRules')return node;
  var prefix=new Insns();var original=new Label();
  prefix.add(new Var(O.ALOAD,1));
  prefix.add(new Call(O.INVOKESTATIC,'cn/punishmenteye/SkinRules','displayOverride','(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/resources/ResourceLocation;',false));
  prefix.add(new Insn(O.DUP));prefix.add(new Jump(O.IFNULL,original));prefix.add(new Insn(O.ARETURN));
  prefix.add(original);prefix.add(new Insn(O.POP));
  method.instructions.insert(prefix);method.maxStack=Math.max(method.maxStack,2);
 }
 if(!found)throw new Error('Eye of Punishment: unsupported TaCZ cosmetic-display entry '+node.name);
 return node;
}
function initializeCoreMod(){return {'eye_cosmetic_display':{'target':{'type':'CLASS','name':'com.tacz.guns.api.item.nbt.GunItemDataAccessor'},'transformer':eyeCosmeticDisplay}};}
