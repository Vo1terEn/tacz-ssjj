var O=Java.type('org.objectweb.asm.Opcodes');
var Insns=Java.type('org.objectweb.asm.tree.InsnList');
var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
var Field=Java.type('org.objectweb.asm.tree.FieldInsnNode');
var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label=Java.type('org.objectweb.asm.tree.LabelNode');
var Insn=Java.type('org.objectweb.asm.tree.InsnNode');
function blockEyeAim(node) {
    var client=node.name==='com/tacz/guns/client/gameplay/LocalPlayerAim';
    var field=client?'player':'shooter';
    var type=client?'net/minecraft/client/player/LocalPlayer':'net/minecraft/world/entity/LivingEntity';
    var found=false;
    for each(var method in node.methods) {
        if(method.name!=='aim'||method.desc!=='(Z)V')continue;
        found=true;
        for each(var instruction in method.instructions.toArray()) {
            if(instruction.getOpcode()===O.INVOKESTATIC&&instruction.owner==='cn/punishmenteye/AimRules')return node;
        }
        var prefix=new Insns();var pass=new Label();
        prefix.add(new Var(O.ALOAD,0));
        prefix.add(new Field(O.GETFIELD,node.name,field,'L'+type+';'));
        prefix.add(new Var(O.ILOAD,1));
        prefix.add(new Call(O.INVOKESTATIC,'cn/punishmenteye/AimRules','blocksAim','(Lnet/minecraft/world/entity/LivingEntity;Z)Z',false));
        prefix.add(new Jump(O.IFEQ,pass));prefix.add(new Insn(O.RETURN));prefix.add(pass);
        method.instructions.insert(prefix);method.maxStack=Math.max(method.maxStack,2);
    }
    if(!found)throw new Error('Eye of Punishment: unsupported TaCZ aim entry '+node.name);
    return node;
}
function initializeCoreMod() {
    return {
        'client_aim':{'target':{'type':'CLASS','name':'com.tacz.guns.client.gameplay.LocalPlayerAim'},'transformer':blockEyeAim},
        'entity_aim':{'target':{'type':'CLASS','name':'com.tacz.guns.entity.shooter.LivingEntityAim'},'transformer':blockEyeAim}
    };
}
