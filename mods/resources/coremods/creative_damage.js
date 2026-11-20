// Targeted Minecraft 1.20.1 / Forge 47 hooks. The Java hooks are transparent unless
// this exact server-side attack belongs to a legally installed creative stone.
var CD_O = Java.type('org.objectweb.asm.Opcodes');
var CD_Type = Java.type('org.objectweb.asm.Type');
var CD_List = Java.type('org.objectweb.asm.tree.InsnList');
var CD_Insn = Java.type('org.objectweb.asm.tree.InsnNode');
var CD_Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var CD_Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var CD_Cast = Java.type('org.objectweb.asm.tree.TypeInsnNode');
var CD_Jump = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var CD_Label = Java.type('org.objectweb.asm.tree.LabelNode');
var CD_HOOK = 'cn/fusionarsenal/CreativeDamage';
var CD_LIVING = 'Lnet/minecraft/world/entity/LivingEntity;';
var CD_ENTITY = 'Lnet/minecraft/world/entity/Entity;';
var CD_SOURCE = 'Lnet/minecraft/world/damagesource/DamageSource;';
var CD_BULLET = 'Lcom/tacz/guns/entity/EntityKineticBullet;';
var CD_MULTIPART = 'com/tacz/guns/entity/EntityKineticBullet$MaybeMultipartEntity';
var CD_EVENT = 'Lnet/minecraftforge/eventbus/api/Event;';
var CD_GUN_HURT_PRE = 'com/tacz/guns/api/event/common/EntityHurtByGunEvent$Pre';

function cdCall(name, desc) {
    return new CD_Call(CD_O.INVOKESTATIC, CD_HOOK, name, desc, false);
}

function cdAlready(node) {
    for each (var method in node.methods) for each (var insn in method.instructions.toArray())
        if (insn.getOpcode() === CD_O.INVOKESTATIC && insn.owner === CD_HOOK) return true;
    return false;
}

function cdLiving(node) {
    if (cdAlready(node)) return node;
    var hurts = 0, getters = 0;
    for each (var method in node.methods) {
        var getter = (method.name === 'getHealth' || method.name === 'm_21223_') && method.desc === '()F';
        if (getter) {
            getters++;
            for each (var ret in method.instructions.toArray()) if (ret.getOpcode() === CD_O.FRETURN) {
                var read = new CD_List();
                read.add(new CD_Var(CD_O.ALOAD, 0));
                read.add(new CD_Insn(CD_O.SWAP));
                read.add(cdCall('readHealth', '(' + CD_LIVING + 'F)F'));
                method.instructions.insertBefore(ret, read);
            }
            method.maxStack += 2;
        } else {
            // Cover subclass getter overrides when native LivingEntity logic performs
            // a virtual read during the bounded creative damage/death operation.
            for each (var call in method.instructions.toArray()) {
                if (call.getOpcode() !== CD_O.INVOKEVIRTUAL || call.owner !== node.name ||
                    (call.name !== 'getHealth' && call.name !== 'm_21223_') || call.desc !== '()F') continue;
                method.instructions.insertBefore(call, new CD_Insn(CD_O.DUP));
                method.instructions.insert(call, cdCall('readHealth', '(' + CD_LIVING + 'F)F'));
                method.maxStack += 1;
            }
        }
        if ((method.name === 'hurt' || method.name === 'm_6469_') && method.desc === '(' + CD_SOURCE + 'F)Z') {
            hurts++;
            var prefix = new CD_List(), ordinary = new CD_Label();
            prefix.add(new CD_Var(CD_O.ALOAD, 0));
            prefix.add(new CD_Var(CD_O.ALOAD, 1));
            prefix.add(cdCall('eligible', '(' + CD_LIVING + CD_SOURCE + ')Z'));
            prefix.add(new CD_Jump(CD_O.IFEQ, ordinary));
            prefix.add(new CD_Var(CD_O.ALOAD, 0));
            prefix.add(new CD_Var(CD_O.ALOAD, 1));
            prefix.add(new CD_Var(CD_O.FLOAD, 2));
            prefix.add(cdCall('attack', '(' + CD_ENTITY + CD_SOURCE + 'F)Z'));
            prefix.add(new CD_Insn(CD_O.IRETURN));
            prefix.add(ordinary);
            method.instructions.insert(prefix);
            method.maxStack = Math.max(method.maxStack, 3);
        }
    }
    if (hurts !== 1 || getters !== 1)
        throw new Error('Creative damage: unsupported LivingEntity layout (' + hurts + '/' + getters + ')');
    return node;
}

function cdBullet(node) {
    if (cdAlready(node)) return node;
    var captures = 0, impacts = 0, attacks = 0, posts = 0, custom = 0;
    for each (var method in node.methods) {
        if ((method.name === 'shoot' || method.name === 'shootFromRotation') &&
            method.desc.endsWith(')V') && (method.access & CD_O.ACC_STATIC) === 0) {
            var freeze = new CD_List();
            freeze.add(new CD_Var(CD_O.ALOAD, 0));
            freeze.add(cdCall('freeze', '(Ljava/lang/Object;)V'));
            method.instructions.insert(freeze);
            method.maxStack = Math.max(method.maxStack, 1);
        }
        if (method.name === '<init>') {
            var args = CD_Type.getArgumentTypes(method.desc), slot = 1, stackSlot = -1;
            for (var index = 0; index < args.length; index++) {
                if (String(args[index].getDescriptor()) === 'Lnet/minecraft/world/item/ItemStack;') stackSlot = slot;
                slot += args[index].getSize();
            }
            if (stackSlot >= 0) for each (var ret in method.instructions.toArray())
                if (ret.getOpcode() === CD_O.RETURN) {
                    var capture = new CD_List();
                    capture.add(new CD_Var(CD_O.ALOAD, 0));
                    capture.add(new CD_Var(CD_O.ALOAD, stackSlot));
                    capture.add(cdCall('capture', '(' + CD_BULLET + 'Lnet/minecraft/world/item/ItemStack;)V'));
                    method.instructions.insertBefore(ret, capture);
                    method.maxStack = Math.max(method.maxStack, 2);
                    captures++;
                }
        }
        if (method.name === 'onHitEntity' && method.desc ===
            '(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V') {
            impacts++;
            var awaitingPrePost = false;
            for each (var insn in method.instructions.toArray()) {
                if (insn.getOpcode() === CD_O.INSTANCEOF && insn.desc === 'com/tacz/guns/api/entity/ITargetEntity') {
                    var filter = new CD_List();
                    method.instructions.insertBefore(insn, new CD_Insn(CD_O.DUP));
                    filter.add(new CD_Var(CD_O.ALOAD, 0));
                    filter.add(cdCall('filterCustom', '(' + CD_ENTITY + 'Z' + CD_BULLET + ')Z'));
                    method.instructions.insert(insn, filter);
                    custom++;
                }
                if (insn.getOpcode() === CD_O.INVOKESPECIAL && insn.owner === CD_GUN_HURT_PRE && insn.name === '<init>')
                    awaitingPrePost = true;
                if (awaitingPrePost && insn.getOpcode() === CD_O.INVOKEINTERFACE &&
                    insn.owner === 'net/minecraftforge/eventbus/api/IEventBus' &&
                    insn.name === 'post' && insn.desc === '(' + CD_EVENT + ')Z') {
                    // Leave the native invocation available to Mixin redirects. Save
                    // [original amount, event] under the receiver and argument it consumes.
                    var snapshot = new CD_List();
                    snapshot.add(new CD_Insn(CD_O.DUP));
                    snapshot.add(cdCall('snapshotPreDamage', '(' + CD_EVENT + ')F'));
                    snapshot.add(new CD_Insn(CD_O.DUP_X2));
                    snapshot.add(new CD_Insn(CD_O.POP));
                    snapshot.add(new CD_Insn(CD_O.DUP_X1));
                    method.instructions.insertBefore(insn, snapshot);
                    method.instructions.insert(insn, cdCall('finishPreDamage', '(F' + CD_EVENT + 'Z)Z'));
                    awaitingPrePost = false;
                    posts++;
                }
            }
            method.maxStack += 2;
        }
        if (method.name === 'tacAttackEntity' && method.desc ===
            '(L' + CD_MULTIPART + ';FLorg/apache/commons/lang3/tuple/Pair;)V') {
            attacks++;
            var prefix = new CD_List(), ordinary = new CD_Label();
            prefix.add(new CD_Var(CD_O.ALOAD, 0));
            prefix.add(new CD_Var(CD_O.ALOAD, 1));
            prefix.add(new CD_Call(CD_O.INVOKEVIRTUAL, CD_MULTIPART, 'core', '()' + CD_ENTITY, false));
            prefix.add(new CD_Var(CD_O.FLOAD, 2));
            prefix.add(new CD_Var(CD_O.ALOAD, 3));
            prefix.add(new CD_Call(CD_O.INVOKEVIRTUAL, 'org/apache/commons/lang3/tuple/Pair', 'getLeft', '()Ljava/lang/Object;', false));
            prefix.add(new CD_Cast(CD_O.CHECKCAST, 'net/minecraft/world/damagesource/DamageSource'));
            prefix.add(cdCall('bullet', '(' + CD_BULLET + CD_ENTITY + 'F' + CD_SOURCE + ')Z'));
            prefix.add(new CD_Jump(CD_O.IFEQ, ordinary));
            prefix.add(new CD_Insn(CD_O.RETURN));
            prefix.add(ordinary);
            method.instructions.insert(prefix);
            method.maxStack = Math.max(method.maxStack, 4);
        }
    }
    // 1.1.5, 1.1.8-hotfix2 and 1.2.0 all have these native entry points.
    if (captures < 1 || impacts !== 1 || attacks !== 1 || posts !== 1 || custom !== 1)
        throw new Error('Creative damage: unsupported TaCZ layout (' + captures + '/' + impacts + '/' + attacks + '/' + posts + '/' + custom + ')');
    return node;
}

// Native unspawned shockwave/Zeus templates copy an existing projectile instead
// of constructing from an ItemStack. Keep its original authoritative snapshot.
function cdCopiedBullet(node) {
    if (cdAlready(node)) return node;
    var copied = 0;
    for each (var method in node.methods) {
        if (method.name !== '<init>' || method.desc.indexOf('(' + CD_BULLET) !== 0) continue;
        for each (var ret in method.instructions.toArray()) if (ret.getOpcode() === CD_O.RETURN) {
            var prefix = new CD_List();
            prefix.add(new CD_Var(CD_O.ALOAD, 0));
            prefix.add(new CD_Var(CD_O.ALOAD, 1));
            prefix.add(cdCall('inherit', '(' + CD_BULLET + CD_BULLET + ')V'));
            method.instructions.insertBefore(ret, prefix);
            method.maxStack = Math.max(method.maxStack, 2);
            copied++;
        }
    }
    if (copied !== 1) throw new Error('Creative damage: unsupported copied bullet ' + node.name);
    return node;
}

function cdCrownEvents(node) {
    if (cdAlready(node)) return node;
    var count = 0;
    for each (var method in node.methods) {
        if (method.name !== 'post' || method.desc !== '(Lnet/minecraftforge/eventbus/api/Event;)V') continue;
        for each (var insn in method.instructions.toArray())
            if (insn.getOpcode() === CD_O.INVOKEINTERFACE && insn.owner === 'net/minecraftforge/eventbus/api/IEventBus' &&
                insn.name === 'post' && insn.desc === '(Lnet/minecraftforge/eventbus/api/Event;)Z') {
                method.instructions.set(insn, cdCall('post',
                    '(Lnet/minecraftforge/eventbus/api/IEventBus;Lnet/minecraftforge/eventbus/api/Event;)Z'));
                count++;
            }
    }
    if (count !== 1) throw new Error('Creative damage: unsupported crown event boundary');
    return node;
}

// Local skill calls must enter before a foreign entity override can return from
// hurt without delegating to LivingEntity. Ordinary attacks retain virtual hurt.
function cdSkillCalls(node) {
    if (cdAlready(node)) return node;
    var count = 0;
    for each (var method in node.methods) for each (var insn in method.instructions.toArray()) {
        if (insn.getOpcode() !== CD_O.INVOKEVIRTUAL ||
            (insn.name !== 'hurt' && insn.name !== 'm_6469_') || insn.desc !== '(' + CD_SOURCE + 'F)Z') continue;
        method.instructions.set(insn, cdCall('route', '(' + CD_ENTITY + CD_SOURCE + 'F)Z'));
        count++;
    }
    var expected = node.name === 'cn/ssjjparasite2/client/ParasiteDestructionDamage' ? 2 : 1;
    if (count !== expected) throw new Error('Creative damage: unsupported skill boundary ' + node.name + ' (' + count + ')');
    return node;
}

function cdRefund(node) {
    if (cdAlready(node)) return node;
    var count = 0;
    for each (var method in node.methods) {
        if (method.name !== 'acceptedHit' || method.desc !== '(' + CD_LIVING + CD_SOURCE + 'F)Z') continue;
        var prefix = new CD_List(), ordinary = new CD_Label();
        prefix.add(new CD_Var(CD_O.ALOAD, 0));
        prefix.add(new CD_Var(CD_O.ALOAD, 1));
        prefix.add(cdCall('eligible', '(' + CD_LIVING + CD_SOURCE + ')Z'));
        prefix.add(new CD_Jump(CD_O.IFEQ, ordinary));
        prefix.add(new CD_Var(CD_O.ALOAD, 0));
        prefix.add(new CD_Var(CD_O.ALOAD, 1));
        prefix.add(new CD_Var(CD_O.FLOAD, 2));
        prefix.add(cdCall('attack', '(' + CD_ENTITY + CD_SOURCE + 'F)Z'));
        prefix.add(new CD_Insn(CD_O.IRETURN));
        prefix.add(ordinary);
        method.instructions.insert(prefix);
        method.maxStack = Math.max(method.maxStack, 3);
        count++;
    }
    if (count !== 1) throw new Error('Creative damage: unsupported hit refund boundary');
    return node;
}

function initializeCoreMod() {
    return {
        'ssjj_creative_health': {target: {type: 'CLASS', name: 'net.minecraft.world.entity.LivingEntity'}, transformer: cdLiving},
        'ssjj_creative_bullet': {target: {type: 'CLASS', name: 'com.tacz.guns.entity.EntityKineticBullet'}, transformer: cdBullet},
        'ssjj_creative_crown_events': {target: {type: 'CLASS', name: 'cn.judgfire.AttackBonuses'}, transformer: cdCrownEvents},
        'ssjj_creative_parasite_copy': {target: {type: 'CLASS', name: 'cn.ssjjparasite2.client.ParasiteShockwave$NativeWave'}, transformer: cdCopiedBullet},
        'ssjj_creative_zeus_copy': {target: {type: 'CLASS', name: 'cn.ssjjzeus.ZeusThunder$NativeBlast'}, transformer: cdCopiedBullet},
        'ssjj_creative_crown_route': {target: {type: 'CLASS', name: 'cn.judgfire.EmperorCrown'}, transformer: cdSkillCalls},
        'ssjj_creative_field_route': {target: {type: 'CLASS', name: 'cn.ssjjdimension.DimensionField'}, transformer: cdSkillCalls},
        'ssjj_creative_mark_route': {target: {type: 'CLASS', name: 'cn.ssjjdimension.DimensionMarks'}, transformer: cdSkillCalls},
        'ssjj_creative_charge_route': {target: {type: 'CLASS', name: 'cn.ssjjhyper.HyperCharge'}, transformer: cdSkillCalls},
        'ssjj_creative_destruction_route': {target: {type: 'CLASS', name: 'cn.ssjjparasite2.client.ParasiteDestructionDamage'}, transformer: cdSkillCalls},
        'ssjj_creative_refund': {target: {type: 'CLASS', name: 'cn.punishmenteye.EyeHitRefund'}, transformer: cdRefund}
    };
}
