var O = Java.type('org.objectweb.asm.Opcodes');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Field = Java.type('org.objectweb.asm.tree.FieldInsnNode');
var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var Insn = Java.type('org.objectweb.asm.tree.InsnNode');
var Instructions = Java.type('org.objectweb.asm.tree.InsnList');
var BRIDGE = 'cn/ssjjcompat/DescriptionLines';
var BOUND = '(Ljava/util/List;IILnet/minecraft/world/item/ItemStack;)I';

function description(node) {
    var method = null;
    for (var i = 0; i < node.methods.size(); i++) {
        var candidate = node.methods.get(i);
        if (candidate.name === 'getText' && candidate.desc === '()V') method = candidate;
    }
    if (method === null) throw new Error('SSJJRPG: missing gun description method');

    var calls = [];
    var bridges = 0;
    var old = method.instructions.toArray();
    for (var j = 0; j < old.length; j++) {
        var call = old[j];
        if (!(call instanceof Call)) continue;
        if (call.owner === BRIDGE && call.name === 'upperBound' && call.desc === BOUND) bridges++;
        if (call.owner === 'java/util/List' && call.name === 'subList' && call.desc === '(II)Ljava/util/List;') calls.push(call);
    }
    if (calls.length !== 1 || bridges > 1) {
        throw new Error('SSJJRPG: unsupported gun description truncation layout: ' + calls.length + '/' + bridges);
    }
    if (bridges === 1) return node;

    // Leave the original INVOKEINTERFACE List.subList in place. GunsmithLib's
    // breakTooltipLineLimit WrapOperation must still be able to find that call.
    var endSlot = method.maxLocals;
    var arguments = new Instructions();
    arguments.add(new Var(O.ISTORE, endSlot));
    // Stack suffix: list, from -> list, from, list, from, end, gun.
    arguments.add(new Insn(O.DUP2));
    arguments.add(new Var(O.ILOAD, endSlot));
    arguments.add(new Var(O.ALOAD, 0));
    arguments.add(new Field(O.GETFIELD, node.name, 'gun', 'Lnet/minecraft/world/item/ItemStack;'));
    arguments.add(new Call(O.INVOKESTATIC, BRIDGE, 'upperBound', BOUND, false));
    method.instructions.insertBefore(calls[0], arguments);
    method.maxLocals += 1;
    method.maxStack += 3;
    return node;
}

function initializeCoreMod() {
    return {
        'ssjj_parasite_full_description': {
            target: {type: 'CLASS', name: 'com.tacz.guns.client.tooltip.ClientGunTooltip'},
            transformer: description
        }
    };
}
