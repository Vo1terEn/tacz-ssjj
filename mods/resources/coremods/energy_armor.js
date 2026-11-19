// Keep both native CombatRules calls as targets for other mods' Mixin redirects.
// Restore a proportion of their actual reduction after the original call returns.
var O=Java.type('org.objectweb.asm.Opcodes');
var L=Java.type('org.objectweb.asm.tree.InsnList');
var V=Java.type('org.objectweb.asm.tree.VarInsnNode');
var C=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var HOOK='cn/judgfire/EnergyArmorPenetration';
var ENTITY='Lnet/minecraft/world/entity/LivingEntity;';
var SOURCE='Lnet/minecraft/world/damagesource/DamageSource;';
var DESC='(FF'+ENTITY+SOURCE+')F';

function nextCode(node){
    do{node=node.getNext();}while(node!==null&&node.getOpcode()<0);
    return node;
}

function wired(call,name){
    var n=nextCode(call);
    if(!(n instanceof V)||n.getOpcode()!==O.FLOAD||n.var!==2)return false;
    n=nextCode(n);if(!(n instanceof V)||n.getOpcode()!==O.ALOAD||n.var!==0)return false;
    n=nextCode(n);if(!(n instanceof V)||n.getOpcode()!==O.ALOAD||n.var!==1)return false;
    n=nextCode(n);return n instanceof C&&n.getOpcode()===O.INVOKESTATIC&&n.owner===HOOK&&n.name===name&&n.desc===DESC;
}

function transform(node){
    var stages=0;
    for(var i=0;i<node.methods.size();i++){
        var method=node.methods.get(i);
        if(method.desc!=='('+SOURCE+'F)F')continue;
        var armor=method.name==='getDamageAfterArmorAbsorb'||method.name==='m_21161_';
        var magic=method.name==='getDamageAfterMagicAbsorb'||method.name==='m_6515_';
        if(!armor&&!magic)continue;
        var nativeCall=null,nativeCount=0,hooks=0,name=armor?'afterArmor':'afterMagic';
        var insns=method.instructions.toArray();
        for(var j=0;j<insns.length;j++){
            var call=insns[j];
            if(!(call instanceof C)||call.getOpcode()!==O.INVOKESTATIC)continue;
            if(call.owner===HOOK){
                if(call.name!==name||call.desc!==DESC)throw new Error('Energy armour penetration: incompatible existing hook');
                hooks++;continue;
            }
            if(call.owner!=='net/minecraft/world/damagesource/CombatRules')continue;
            var match=armor?((call.name==='getDamageAfterAbsorb'||call.name==='m_19272_')&&call.desc==='(FFF)F'):
                ((call.name==='getDamageAfterMagicAbsorb'||call.name==='m_19269_')&&call.desc==='(FF)F');
            if(match){nativeCall=call;nativeCount++;}
        }
        if(nativeCount!==1||hooks>1)throw new Error('Energy armour penetration: unsupported native stage '+method.name+'/'+nativeCount+'/'+hooks);
        if(hooks===1){
            if(!wired(nativeCall,name))throw new Error('Energy armour penetration: unexpected existing hook location');
        }else{
            var tail=new L();
            tail.add(new V(O.FLOAD,2));tail.add(new V(O.ALOAD,0));tail.add(new V(O.ALOAD,1));
            tail.add(new C(O.INVOKESTATIC,HOOK,name,DESC,false));
            method.instructions.insert(nativeCall,tail);
            method.maxStack=Math.max(method.maxStack,4);
        }
        stages++;
    }
    if(stages!==2)throw new Error('Energy armour penetration: expected exactly two native stages, found '+stages);
    return node;
}

function initializeCoreMod(){return {energy_armor:{target:{type:'CLASS',name:'net.minecraft.world.entity.LivingEntity'},transformer:transform}};}
