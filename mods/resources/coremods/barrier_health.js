// Pinned to the project's Minecraft 1.20.1 / Forge 47 runtime. Both naming domains
// are checked so the exact shipped script can also be tested on the named jars.
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var TryCatchBlockNode = Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
var HOOK = 'cn/judgfire/BarrierHealth';
var LIVING = 'Lnet/minecraft/world/entity/LivingEntity;';
var SOURCE = 'Lnet/minecraft/world/damagesource/DamageSource;';
var ACCESSOR = 'Lnet/minecraft/network/syncher/EntityDataAccessor;';

function call(name, descriptor) {
    return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, name, descriptor, false);
}

function wrap(method, health, restore, overriding) {
    var prefix = new InsnList();
    prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
    if (restore) {
        prefix.add(call('beginRestore', '(' + LIVING + ')V'));
    } else if (health) {
        prefix.add(new VarInsnNode(Opcodes.FLOAD, 1));
        prefix.add(call(overriding ? 'beginOverrideHealth' : 'beginHealth', '(' + LIVING + 'F)F'));
        prefix.add(new VarInsnNode(Opcodes.FSTORE, 1));
    } else {
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(call((method.name === 'actuallyHurt' || method.name === 'm_6475_') ?
            'enterCommit' : 'enterDamage', '(' + LIVING + SOURCE + ')V'));
    }
    var start = new LabelNode();
    prefix.add(start);
    var exit = restore ? 'endRestore' : health ? 'endHealth' : 'exitDamage';
    var original = method.instructions.toArray();
    for each (var insn in original) {
        var op = insn.getOpcode();
        if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)
            method.instructions.insertBefore(insn, call(exit, '()V'));
    }
    method.instructions.insert(prefix);
    var end = new LabelNode();
    var handler = new LabelNode();
    method.instructions.add(end);
    method.instructions.add(handler);
    // Keep the Throwable on the operand stack across a zero-argument cleanup call.
    method.instructions.add(call(exit, '()V'));
    method.instructions.add(new InsnNode(Opcodes.ATHROW));
    method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    method.maxStack = Math.max(method.maxStack, 3);
}

function already(node) {
    for each (var m in node.methods) for each (var i in m.instructions.toArray())
        if (i.getOpcode() === Opcodes.INVOKESTATIC && i.owner === HOOK) return true;
    return false;
}

function livingTransform(node) {
    if (already(node)) return node;
    var hurtCount = 0, healthCount = 0, registered = 0, readCount = 0;
    for each (var method in node.methods) {
        if (((method.name === 'readAdditionalSaveData' || method.name === 'm_7378_') &&
             method.desc === '(Lnet/minecraft/nbt/CompoundTag;)V') ||
            ((method.name === 'restoreFrom' || method.name === 'm_9015_') &&
             method.desc === '(Lnet/minecraft/server/level/ServerPlayer;Z)V')) wrap(method, false, true);
        if (((method.name === 'hurt' || method.name === 'm_6469_') && method.desc === '(' + SOURCE + 'F)Z') ||
            ((method.name === 'actuallyHurt' || method.name === 'm_6475_') && method.desc === '(' + SOURCE + 'F)V')) {
            wrap(method, false);
            hurtCount++;
        }
        if ((method.name === 'setHealth' || method.name === 'm_21153_') && method.desc === '(F)V') {
            wrap(method, true);
            healthCount++;
        }
        if ((method.name === 'getHealth' || method.name === 'm_21223_') && method.desc === '()F') {
            readCount++;
            for each (var ret in method.instructions.toArray()) {
                if (ret.getOpcode() !== Opcodes.FRETURN) continue;
                var finish = new InsnList();
                finish.add(new VarInsnNode(Opcodes.ALOAD, 0));
                finish.add(new InsnNode(Opcodes.SWAP));
                finish.add(call('readHealth', '(' + LIVING + 'F)F'));
                method.instructions.insertBefore(ret, finish);
            }
            method.maxStack = Math.max(method.maxStack, 2);
        }
        if (method.name === '<clinit>') {
            for each (var insn in method.instructions.toArray()) {
                if (insn.getOpcode() === Opcodes.PUTSTATIC && insn.desc === ACCESSOR &&
                    (insn.name === 'DATA_HEALTH_ID' || insn.name === 'f_20961_')) {
                    var list = new InsnList();
                    list.add(new FieldInsnNode(Opcodes.GETSTATIC, insn.owner, insn.name, insn.desc));
                    list.add(call('registerHealth', '(Ljava/lang/Object;)V'));
                    method.instructions.insert(insn, list);
                    registered++;
                }
            }
        }
    }
    if (node.name === 'net/minecraft/world/entity/LivingEntity' &&
        (hurtCount !== 2 || healthCount !== 1 || registered !== 1 || readCount !== 1))
        throw new Error('Barrier health: unexpected LivingEntity layout; refusing an incomplete patch');
    return node;
}

// Optional known virtual setter: damage is reported before a native pool write, and the
// default infinite-health branch intentionally never delegates to LivingEntity.setHealth.
function dummyTransform(node) {
    if (already(node)) return node;
    var count=0;
    for each (var method in node.methods) {
        if ((method.name === 'setHealth' || method.name === 'm_21153_') && method.desc === '(F)V') {
            wrap(method,true,false,true);
            count++;
        }
    }
    if(count!==1) throw new Error('Barrier health: unexpected optional TargetDummy setter layout');
    return node;
}

function dataTransform(node) {
    if (already(node)) return node;
    var ownerField = null, getter = null, count = 0;
    for each (var field in node.fields)
        if (field.desc === 'Lnet/minecraft/world/entity/Entity;' && (field.access & Opcodes.ACC_STATIC) === 0)
            ownerField = field.name;
    if (ownerField === null) throw new Error('Barrier health: missing entity owner');
    for each (var method in node.methods)
        if (method.desc === '(' + ACCESSOR + ')Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;') getter = method;
    if (getter === null) throw new Error('Barrier health: missing data lookup');
    // Rebind replacement items at lookup without trusting their unobserved value as a new baseline.
    var temporary = getter.maxLocals++;
    for each (var ret in getter.instructions.toArray()) {
        if (ret.getOpcode() !== Opcodes.ARETURN) continue;
        var bind = new InsnList();
        bind.add(new VarInsnNode(Opcodes.ASTORE, temporary));
        bind.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bind.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, ownerField, 'Lnet/minecraft/world/entity/Entity;'));
        bind.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bind.add(new VarInsnNode(Opcodes.ALOAD, temporary));
        bind.add(call('registerData', '(Lnet/minecraft/world/entity/Entity;' + ACCESSOR + 'Ljava/lang/Object;)V'));
        bind.add(new VarInsnNode(Opcodes.ALOAD, temporary));
        getter.instructions.insertBefore(ret, bind);
    }
    getter.maxStack = Math.max(getter.maxStack, 3);
    for each (var method in node.methods) {
        if ((method.name !== 'createDataItem' && method.name !== 'm_135385_') ||
            method.desc !== '(' + ACCESSOR + 'Ljava/lang/Object;)V') continue;
        for each (var insn in method.instructions.toArray()) {
            if (insn.getOpcode() !== Opcodes.RETURN) continue;
            var list = new InsnList();
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
            list.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, ownerField, 'Lnet/minecraft/world/entity/Entity;'));
            list.add(new VarInsnNode(Opcodes.ALOAD, 1));
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
            list.add(new VarInsnNode(Opcodes.ALOAD, 1));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, getter.name, getter.desc, false));
            list.add(call('registerData', '(Lnet/minecraft/world/entity/Entity;' + ACCESSOR + 'Ljava/lang/Object;)V'));
            method.instructions.insertBefore(insn, list);
        }
        method.maxStack = Math.max(method.maxStack, 4);
        count++;
    }
    if (count !== 1) throw new Error('Barrier health: missing health data registration');
    return node;
}

function itemTransform(node) {
    if (already(node)) return node;
    var count = 0, reads = 0;
    for each (var method in node.methods) {
        if ((method.name === 'getValue' || method.name === 'm_135403_') && method.desc === '()Ljava/lang/Object;') {
            reads++;
            for each (var ret in method.instructions.toArray()) {
                if (ret.getOpcode() !== Opcodes.ARETURN) continue;
                var finish = new InsnList();
                finish.add(new VarInsnNode(Opcodes.ALOAD, 0));
                finish.add(new InsnNode(Opcodes.SWAP));
                finish.add(call('readData', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;'));
                method.instructions.insertBefore(ret, finish);
            }
            method.maxStack = Math.max(method.maxStack, 2);
        }
        if ((method.name !== 'setValue' && method.name !== 'm_135397_') || method.desc !== '(Ljava/lang/Object;)V') continue;
        var list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(call('dataHealth', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;'));
        list.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.insert(list);
        method.maxStack = Math.max(method.maxStack, 2);
        count++;
    }
    if (count !== 1 || reads !== 1) throw new Error('Barrier health: missing data item setter/getter');
    return node;
}

function initializeCoreMod() {
    return {
        'living': { 'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.entity.LivingEntity' }, 'transformer': livingTransform },
        'player': { 'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.entity.player.Player' }, 'transformer': livingTransform },
        'server_player': { 'target': { 'type': 'CLASS', 'name': 'net.minecraft.server.level.ServerPlayer' }, 'transformer': livingTransform },
        'target_dummy': { 'target': { 'type': 'CLASS', 'name': 'net.mehvahdjukaar.dummmmmmy.common.TargetDummyEntity' }, 'transformer': dummyTransform },
        'health_data': { 'target': { 'type': 'CLASS', 'name': 'net.minecraft.network.syncher.SynchedEntityData' }, 'transformer': dataTransform },
        'health_item': { 'target': { 'type': 'CLASS', 'name': 'net.minecraft.network.syncher.SynchedEntityData$DataItem' }, 'transformer': itemTransform }
    };
}
