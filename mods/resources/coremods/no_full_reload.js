var O=Java.type('org.objectweb.asm.Opcodes');
var Insns=Java.type('org.objectweb.asm.tree.InsnList');
var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
var Field=Java.type('org.objectweb.asm.tree.FieldInsnNode');
var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label=Java.type('org.objectweb.asm.tree.LabelNode');
var Insn=Java.type('org.objectweb.asm.tree.InsnNode');

function blockEyeFullReload(node) {
    var playerDesc='Lnet/minecraft/client/player/LocalPlayer;';
    var foundField=false;
    for each(var field in node.fields) {
        if(field.name==='player'&&field.desc===playerDesc)foundField=true;
    }
    if(!foundField)throw new Error('Eye of Punishment: unsupported TaCZ reload player field '+node.name);
    var found=false;
    for each(var method in node.methods) {
        if(method.name!=='reload'||method.desc!=='()V')continue;
        found=true;
        for each(var instruction in method.instructions.toArray()) {
            if(instruction.getOpcode()===O.INVOKESTATIC&&instruction.owner==='cn/punishmenteye/ReloadRules'&&instruction.name==='blocksReload')return node;
        }
        var prefix=new Insns();var pass=new Label();
        prefix.add(new Var(O.ALOAD,0));
        prefix.add(new Field(O.GETFIELD,node.name,'player',playerDesc));
        prefix.add(new Call(O.INVOKESTATIC,'cn/punishmenteye/ReloadRules','blocksReload','(Lnet/minecraft/world/entity/LivingEntity;)Z',false));
        prefix.add(new Jump(O.IFEQ,pass));prefix.add(new Insn(O.RETURN));prefix.add(pass);
        method.instructions.insert(prefix);method.maxStack=Math.max(method.maxStack,1);
    }
    if(!found)throw new Error('Eye of Punishment: unsupported TaCZ reload entry '+node.name);
    return node;
}

function initializeCoreMod() {
    return {
        'client_full_reload':{'target':{'type':'CLASS','name':'com.tacz.guns.client.gameplay.LocalPlayerReload'},'transformer':blockEyeFullReload}
    };
}
