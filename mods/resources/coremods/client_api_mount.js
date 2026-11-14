var O = Java.type('org.objectweb.asm.Opcodes');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var List = Java.type('org.objectweb.asm.tree.InsnList');
var API = 'cn/fusionarsenal/compat/TaCZClientCompat';

function preserveMountVisibility(node) {
    // TaCZ 1.1.8+ already implement the per-attachment flag in their native predicate.
    for (var f = 0; f < node.fields.size(); f++) {
        var field = node.fields.get(f);
        if (field.name === 'renderMount' && field.desc === 'Z') return node;
    }
    var matches = 0, returns = 0;
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        if (method.name !== 'lambda$new$9' || method.desc !== '(Lnet/minecraft/world/item/ItemStack;)Z'
            || (method.access & O.ACC_STATIC) === 0) continue;
        matches++;
        var instructions = method.instructions.toArray();
        for (var j = 0; j < instructions.length; j++) {
            var instruction = instructions[j];
            if (instruction instanceof Call && instruction.owner === API && instruction.name === 'mountVisible') return node;
        }
        for (var j = 0; j < instructions.length; j++) if (instructions[j].getOpcode() === O.IRETURN) {
            var hook = new List();
            hook.add(new Var(O.ALOAD, 0));
            hook.add(new Call(O.INVOKESTATIC, API, 'mountVisible', '(ZLnet/minecraft/world/item/ItemStack;)Z', false));
            method.instructions.insertBefore(instructions[j], hook);
            method.maxStack = Math.max(method.maxStack, 2);
            returns++;
        }
    }
    if (matches !== 1 || returns !== 1) throw new Error('SSJJRPG: unsupported legacy mount predicate: ' + matches + '/' + returns);
    return node;
}

function initializeCoreMod() {
    return {'ssjj_client_api_mount': {
        target: {type: 'CLASS', name: 'com.tacz.guns.client.model.BedrockGunModel'},
        transformer: preserveMountVisibility
    }};
}
