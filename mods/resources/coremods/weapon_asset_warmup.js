var O = Java.type('org.objectweb.asm.Opcodes');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');

function prepareWeapons(node) {
    var methods=0, returns=0;
    for(var i=0;i<node.methods.size();i++) {
        var method=node.methods.get(i);
        if(method.name!=='reload' || method.desc!=='()V')continue;
        if((method.access & O.ACC_STATIC)===0)throw new Error('SSJJRPG: reload is no longer static');
        methods++;
        var instructions=method.instructions.toArray();
        for(var j=0;j<instructions.length;j++)if(instructions[j].getOpcode()===O.RETURN) {
            method.instructions.insertBefore(instructions[j],new Call(O.INVOKESTATIC,
                'cn/ssjjcompat/WeaponAssetWarmup','prepare','()V',false));
            returns++;
        }
    }
    if(methods!==1 || returns!==1)throw new Error('SSJJRPG: unsupported client resource reload layout');
    return node;
}

function initializeCoreMod() {
    return {'ssjj_weapon_asset_warmup': {
        target:{type:'CLASS',name:'com.tacz.guns.client.resource.ClientIndexManager'},
        transformer:prepareWeapons
    }};
}
