var O = Java.type('org.objectweb.asm.Opcodes');
var Insns = Java.type('org.objectweb.asm.tree.InsnList');
var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label = Java.type('org.objectweb.asm.tree.LabelNode');
var Insn = Java.type('org.objectweb.asm.tree.InsnNode');

function reserveSkill(node) {
    var zoom = node.name.endsWith('/ZoomKey');
    var name = zoom ? 'onZoomKeyPress' : 'onMeleeKeyPress';
    var modern = zoom ? 'onZoomPress' : 'onMeleePress';
    var found = false;
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        // New releases route these through GunInputHandler; patch that route once.
        if (method.name === modern && method.desc === '()Z') found = true;
        if (method.name !== name || method.desc !== '(Lnet/minecraftforge/client/event/InputEvent$Key;)V') continue;
        found = true;
        var prefix = new Insns(), pass = new Label();
        prefix.add(new Var(O.ALOAD, 0));
        prefix.add(new Call(O.INVOKESTATIC, 'cn/judgfire/JudgmentSkillKey', 'reserved', method.desc.replace(')V', ')Z'), false));
        prefix.add(new Jump(O.IFEQ, pass));
        prefix.add(new Insn(O.RETURN));
        prefix.add(pass);
        method.instructions.insert(prefix);
        method.maxStack = Math.max(method.maxStack, 1);
    }
    if (!found) throw new Error('Judgment Flame: unsupported skill-key entry ' + node.name);
    return node;
}

function reserveRouter(node) {
    var count = 0;
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        if (method.name !== 'onPress' || method.desc !== '(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V') continue;
        var instructions = method.instructions.toArray();
        for (var j = 0; j < instructions.length; j++) {
            var call = instructions[j];
            if (!(call instanceof Call) || call.owner !== 'java/lang/Runnable' || call.name !== 'run' || call.desc !== '()V') continue;
            var action = call.getPrevious(), local = action.getPrevious();
            if (!(action instanceof Call) || action.name !== 'action' || action.desc !== '()Ljava/lang/Runnable;'
                || !(local instanceof Var) || local.getOpcode() !== O.ALOAD)
                throw new Error('Judgment Flame: unsupported router binding layout');
            var prefix = new Insns();
            prefix.add(new Var(O.ALOAD, 0));
            prefix.add(new Var(O.ALOAD, local.var));
            prefix.add(new Call(O.INVOKEVIRTUAL, action.owner, 'keyMapping', '()Lnet/minecraft/client/KeyMapping;', false));
            method.instructions.insertBefore(call, prefix);
            call.setOpcode(O.INVOKESTATIC);
            call.owner = 'cn/ssjjcompat/SkillInputCompat';
            call.name = 'run';
            call.desc = '(Ljava/lang/Runnable;Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V';
            call.itf = false;
            method.maxStack = Math.max(method.maxStack, 3);
            count++;
        }
    }
    if (count !== 1) throw new Error('Judgment Flame: unsupported centralized input router');
    return node;
}
function initializeCoreMod() {
    return {
        'judgment_zoom_skill_key': {target: {type: 'CLASS', name: 'com.tacz.guns.client.input.ZoomKey'}, transformer: reserveSkill},
        'judgment_melee_skill_key': {target: {type: 'CLASS', name: 'com.tacz.guns.client.input.MeleeKey'}, transformer: reserveSkill},
        'judgment_input_router': {target: {type: 'CLASS', name: 'com.tacz.guns.client.input.GunInputHandler'}, transformer: reserveRouter}
    };
}
