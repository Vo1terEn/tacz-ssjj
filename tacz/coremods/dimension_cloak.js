var O = Java.type('org.objectweb.asm.Opcodes');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var BRIDGE = 'cn/ssjjdimension/client/DimensionCloakRender';

function handScope(node) {
    var calls = [];
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        if (method.name !== 'renderSpecificFirstPersonHand') continue;
        var instructions = method.instructions.toArray();
        for (var j = 0; j < instructions.length; j++) {
            var call = instructions[j];
            if (!(call instanceof Call)) continue;
            if (call.owner === BRIDGE && call.name === 'postHand') return node;
            if (call.owner === 'net/minecraftforge/eventbus/api/IEventBus' && call.name === 'post'
                && call.desc === '(Lnet/minecraftforge/eventbus/api/Event;)Z') calls.push(call);
        }
    }
    if (calls.length !== 1) throw new Error('SSJJRPG: unsupported Forge first-person hand event layout');
    var target = calls[0];
    target.setOpcode(O.INVOKESTATIC);target.owner = BRIDGE;target.name = 'postHand';
    target.desc = '(Lnet/minecraftforge/eventbus/api/IEventBus;Lnet/minecraftforge/eventbus/api/Event;)Z';target.itf = false;
    return node;
}
function handDraw(node) {
    var calls = [];
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        // The descriptor is stable across Mojmap and Forge's runtime SRG names.
        if (method.desc !== '(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)V') continue;
        var instructions = method.instructions.toArray();
        for (var j = 0; j < instructions.length; j++) {
            var call = instructions[j];
            if (!(call instanceof Call)) continue;
            if (call.owner === BRIDGE && call.name === 'draw') return node;
            if (call.owner === 'com/mojang/blaze3d/vertex/BufferUploader'
                && call.desc === '(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V'
                && call.getOpcode() === O.INVOKESTATIC) calls.push(call);
        }
    }
    if (calls.length !== 1) throw new Error('SSJJRPG: unsupported native RenderType buffered draw layout');
    calls[0].owner = BRIDGE;calls[0].name = 'draw';
    return node;
}
function initializeCoreMod() {
    return {
        'ssjj_dimension_cloak_hand_scope': {target: {type: 'CLASS', name: 'net.minecraftforge.client.ForgeHooksClient'}, transformer: handScope},
        'ssjj_dimension_cloak_buffered_draw': {target: {type: 'CLASS', name: 'net.minecraft.client.renderer.RenderType'}, transformer: handDraw}
    };
}
