var O = Java.type('org.objectweb.asm.Opcodes');
var Insns = Java.type('org.objectweb.asm.tree.InsnList');
var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');

function restoreEyeRefitPreview(node) {
    var matches = 0;
    for each (var method in node.methods) {
        if (!method.name.startsWith('lambda$renderFirstPerson$')) continue;
        for each (var previous in method.instructions.toArray()) {
            if (previous instanceof Call && previous.owner === 'cn/punishmenteye/EyeRefitPreview') return node;
        }
        for each (var instruction in method.instructions.toArray()) {
            if (!(instruction instanceof Call) || instruction.owner !== 'com/tacz/guns/client/event/FirstPersonRenderGunEvent'
                    || instruction.name !== 'applyFirstPersonGunTransform') continue;
            // These are the model and stack already loaded as arguments to the native transform.
            var cursor = instruction.getPrevious();
            while (cursor !== null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
            if (cursor === null || cursor.getOpcode() !== O.FLOAD) throw new Error('Eye refit: missing partial-tick argument');
            cursor = cursor.getPrevious();
            while (cursor !== null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
            if (cursor === null || cursor.getOpcode() !== O.ALOAD) throw new Error('Eye refit: missing native model argument');
            var modelSlot = cursor.var;
            var prefix = new Insns();
            prefix.add(new Var(O.ALOAD, modelSlot));
            prefix.add(new Var(O.ALOAD, 1));
            prefix.add(new Call(O.INVOKESTATIC, 'cn/punishmenteye/EyeRefitPreview', 'beforeFirstPerson',
                '(Lcom/tacz/guns/client/model/BedrockAnimatedModel;Lnet/minecraft/world/item/ItemStack;)V', false));
            method.instructions.insertBefore(instruction, prefix);
            method.maxStack += 2;
            matches++;
        }
    }
    if (matches !== 1) throw new Error('Eye refit: unsupported native first-person transform count ' + matches);
    return node;
}

function initializeCoreMod() {
    return {
        'eye_refit_preview': {
            'target': {'type': 'CLASS', 'name': 'com.tacz.guns.client.renderer.item.GunItemRendererWrapper'},
            'transformer': restoreEyeRefitPreview
        }
    };
}
