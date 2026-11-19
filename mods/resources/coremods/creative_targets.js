// Keep native wall, distance, headshot and riding rules; repair only the target
// predicate and subsequent alive check for a captured creative-stone projectile.
var CT_O = Java.type('org.objectweb.asm.Opcodes');
var CT_Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var CT_Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var CT_HOOK = 'cn/fusionarsenal/CreativeTargets';
var CT_ENTITY = 'Lnet/minecraft/world/entity/Entity;';
var CT_PROJECTILE = 'Lnet/minecraft/world/entity/projectile/Projectile;';

function ctTargets(node) {
    for each (var method in node.methods) for each (var insn in method.instructions.toArray())
        if (insn.getOpcode() === CT_O.INVOKESTATIC && insn.owner === CT_HOOK) return node;
    var methods = 0, predicates = 0, alive = 0, lists = 0;
    for each (var method in node.methods) {
        if ((method.name !== 'findEntityOnPath' && method.name !== 'findEntitiesOnPath') ||
            method.desc.indexOf('(' + CT_PROJECTILE) !== 0 || (method.access & CT_O.ACC_STATIC) === 0) continue;
        methods++;
        for each (var insn in method.instructions.toArray()) {
            if (insn.getOpcode() === CT_O.GETSTATIC && insn.owner === node.name &&
                insn.desc === 'Ljava/util/function/Predicate;') {
                var load = new CT_Var(CT_O.ALOAD, 0);
                method.instructions.insert(insn, load);
                method.instructions.insert(load, new CT_Call(CT_O.INVOKESTATIC, CT_HOOK, 'predicate',
                    '(Ljava/util/function/Predicate;' + CT_PROJECTILE + ')Ljava/util/function/Predicate;', false));
                predicates++;
            } else if (insn.getOpcode() === CT_O.INVOKEVIRTUAL && insn.owner === 'net/minecraft/world/entity/Entity' &&
                (insn.name === 'isAlive' || insn.name === 'm_6084_') && insn.desc === '()Z') {
                method.instructions.insertBefore(insn, new CT_Var(CT_O.ALOAD, 0));
                method.instructions.set(insn, new CT_Call(CT_O.INVOKESTATIC, CT_HOOK, 'alive',
                    '(' + CT_ENTITY + CT_PROJECTILE + ')Z', false));
                alive++;
            } else if (method.name === 'findEntitiesOnPath' && insn.getOpcode() === CT_O.ARETURN) {
                method.instructions.insertBefore(insn, new CT_Var(CT_O.ALOAD, 0));
                method.instructions.insertBefore(insn, new CT_Var(CT_O.ALOAD, 1));
                method.instructions.insertBefore(insn, new CT_Call(CT_O.INVOKESTATIC, CT_HOOK, 'results',
                    '(Ljava/util/List;' + CT_PROJECTILE + 'Lnet/minecraft/world/phys/Vec3;)Ljava/util/List;', false));
                lists++;
            }
        }
        method.maxStack += 2;
    }
    if (methods !== 2 || predicates !== 2 || alive !== 2 || lists !== 1)
        throw new Error('Creative targets: unsupported TaCZ target query layout (' + methods + '/' + predicates + '/' + alive + '/' + lists + ')');
    return node;
}

function initializeCoreMod() {
    return {
        'ssjj_creative_targets': {target: {type: 'CLASS', name: 'com.tacz.guns.util.EntityUtil'}, transformer: ctTargets}
    };
}
