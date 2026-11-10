// Optional compatibility with the audited Fantasy Ending 2.7.20 method layouts.
// Adds boundary calls only; retains every original instruction and never rewrites the on-disk mod.
var O = Java.type('org.objectweb.asm.Opcodes');
var List = Java.type('org.objectweb.asm.tree.InsnList');
var Insn = Java.type('org.objectweb.asm.tree.InsnNode');
var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var Field = Java.type('org.objectweb.asm.tree.FieldInsnNode');
var Method = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Label = Java.type('org.objectweb.asm.tree.LabelNode');
var Catch = Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
var L = 'Lnet/minecraft/world/entity/LivingEntity;';
var A = 'Lnet/minecraft/network/syncher/EntityDataAccessor;';
var S = 'Lnet/minecraft/world/damagesource/DamageSource;';
function call(owner, name, desc) { return new Method(O.INVOKESTATIC, 'cn/judgfire/'+owner, name, desc, false); }
function method(node, name, desc) {
    for each (var m in node.methods) if (m.name === name && m.desc === desc) return m;
    return null;
}
function done(m) {
    for each (var i in m.instructions.toArray())
        if (i.getOpcode() === O.INVOKESTATIC && (i.owner === 'cn/judgfire/BarrierOffset' || i.owner === 'cn/judgfire/BarrierHealth')) return true;
    return false;
}
function scope(m, prefix, owner, exit) {
    var start = new Label(); prefix.add(start);
    for each (var i in m.instructions.toArray())
        if (i.getOpcode() >= O.IRETURN && i.getOpcode() <= O.RETURN)
            m.instructions.insertBefore(i, call(owner, exit, '()V'));
    m.instructions.insert(prefix);
    var end = new Label(), handler = new Label();
    m.instructions.add(end); m.instructions.add(handler);
    m.instructions.add(call(owner, exit, '()V')); m.instructions.add(new Insn(O.ATHROW));
    m.tryCatchBlocks.add(new Catch(start, end, handler, null));
    m.maxStack = Math.max(m.maxStack, 4);
}
function offsetTransform(node) {
    var m = method(node, 'setHealthDelta', '('+L+'F)V');
    var read = method(node, 'getHealthDelta', '('+L+')F');
    var key = null;
    for each (var f in node.fields) if (f.name === 'FE_GET_HEALTH_DATA' && f.desc === A && (f.access & O.ACC_STATIC) !== 0) key = f;
    if (m === null || read === null || key === null) throw new Error('Fantasy barrier: unsupported offset layout');
    if (done(m)) return node;
    var p = new List();
    p.add(new Var(O.ALOAD, 0));
    p.add(new Field(O.GETSTATIC, node.name, key.name, key.desc));
    p.add(new Var(O.ALOAD, 0));
    p.add(new Method(O.INVOKESTATIC, node.name, read.name, read.desc, false));
    p.add(new Var(O.FLOAD, 1));
    p.add(call('BarrierOffset', 'change', '('+L+A+'FF)F'));
    p.add(new Var(O.FSTORE, 1)); m.instructions.insert(p);m.maxStack=Math.max(m.maxStack,4);
    return node;
}
function swordTransform(node) {
    var m = method(node, 'onFeWeaponClickEntity', '(Lnet/minecraft/world/item/ItemStack;'+L+'Lnet/minecraft/world/entity/Entity;ZZ)Z');
    if (m === null) throw new Error('Fantasy barrier: unsupported sword layout');
    if (done(m)) return node;
    var p = new List();p.add(new Var(O.ALOAD,1));p.add(call('BarrierOffset','enter','('+L+')V'));
    scope(m,p,'BarrierOffset','exit');return node;
}
function hurtTransform(node) {
    var m = method(node, 'actuallyHurt0', '('+S+'FZ)V');
    var target = null;
    for each(var f in node.fields) if(f.name==='entity' && f.desc===L && (f.access&O.ACC_STATIC)===0) target=f;
    if(m===null || target===null) throw new Error('Fantasy barrier: unsupported damage layout');
    if(done(m)) return node;
    var p=new List();p.add(new Var(O.ALOAD,0));p.add(new Field(O.GETFIELD,node.name,target.name,target.desc));p.add(new Var(O.ALOAD,1));
    p.add(call('BarrierHealth','enterDamage','('+L+S+')V'));scope(m,p,'BarrierHealth','exitDamage');return node;
}
function initializeCoreMod() {
    return {
      'offset': {'target':{'type':'CLASS','name':'com.mega.uom.util.entity.EntityASMUtil'},'transformer':offsetTransform},
      'sword': {'target':{'type':'CLASS','name':'com.mega.uom.common.items.combat.sword.FantasyEndingSword'},'transformer':swordTransform},
      'damage': {'target':{'type':'CLASS','name':'com.mega.uom.util.entity.EntityActuallyHurt'},'transformer':hurtTransform}
    };
}
