// Keep the original start checks, client packet and flight-tick method.
var O = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var STACK = 'Lnet/minecraft/world/item/ItemStack;';
var LIVING = 'Lnet/minecraft/world/entity/LivingEntity;';
var SLOT = 'Lnet/minecraft/world/entity/EquipmentSlot;';
var HOOK = 'cn/ssjjares/AresWingFlight';

function patch(node, named, srg, descriptor) {
    for each (var method in node.methods) {
        if (method.desc !== descriptor || (method.name !== named && method.name !== srg)) continue;
        var chest = null;
        var checks = 0;
        for each (var insn in method.instructions.toArray()) {
            if (insn.getOpcode() === O.INVOKESTATIC && insn.owner === HOOK) return node;
            if (insn.getOpcode() === O.GETSTATIC && insn.name === 'CHEST' && insn.desc === SLOT) {
                var next = insn.getNext();
                while (next !== null && next.getOpcode() < 0) next = next.getNext();
                if (next !== null && next.getOpcode() === O.INVOKEVIRTUAL && next.desc === '(' + SLOT + ')' + STACK) {
                    var store = next.getNext();
                    while (store !== null && store.getOpcode() < 0) store = store.getNext();
                    if (store !== null && store.getOpcode() === O.ASTORE) chest = store;
                }
            }
            if (insn.getOpcode() === O.INVOKEVIRTUAL && insn.owner === 'net/minecraft/world/item/ItemStack'
                && insn.name === 'canElytraFly' && insn.desc === '(' + LIVING + ')Z') checks++;
        }
        if (chest === null || checks !== 1) throw new Error('Unexpected elytra provider in ' + node.name);
        var call = new InsnList();
        // Preserve the original stack assignment before selecting an alternate provider.
        call.add(new VarInsnNode(O.ALOAD, chest.var));
        call.add(new VarInsnNode(O.ALOAD, 0));
        call.add(new MethodInsnNode(O.INVOKESTATIC, HOOK, 'vanillaFlightStack', '(' + STACK + LIVING + ')' + STACK, false));
        call.add(new VarInsnNode(O.ASTORE, chest.var));
        method.instructions.insert(chest, call);
        method.maxStack = Math.max(method.maxStack, 3);
        return node;
    }
    throw new Error('Missing elytra method in ' + node.name);
}

function initializeCoreMod() {
    return {
        'start': {'target': {'type': 'CLASS', 'name': 'net.minecraft.world.entity.player.Player'},
            'transformer': function(node) { return patch(node, 'tryToStartFallFlying', 'm_36319_', '()Z'); }},
        'tick': {'target': {'type': 'CLASS', 'name': 'net.minecraft.world.entity.LivingEntity'},
            'transformer': function(node) { return patch(node, 'updateFallFlying', 'm_21323_', '()V'); }}
    };
}
