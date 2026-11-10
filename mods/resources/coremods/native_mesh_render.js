var O = Java.type('org.objectweb.asm.Opcodes');
var Method = Java.type('org.objectweb.asm.tree.MethodNode');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Label = Java.type('org.objectweb.asm.Label');

var VBO = 'com/tacz/guns/client/renderer/item/ExternalGunItemVboRenderer';
var RESULT = 'com/tacz/guns/client/renderer/item/StaticGunRenderResult';
var DESC = '(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)L' + RESULT + ';';
var BRIDGE = 'ssjj$tryExternalGunModel';

function preserveNativeMesh(node) {
    var matches = [];
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        if (method.name === BRIDGE && method.desc === DESC) return node;
        var instructions = method.instructions.toArray();
        for (var j = 0; j < instructions.length; j++) {
            var call = instructions[j];
            if (!(call instanceof Call) || call.owner !== VBO || call.name !== 'tryRender') continue;
            if (call.getOpcode() !== O.INVOKESTATIC || call.desc !== DESC || !method.name.startsWith('lambda$renderByItem$')) {
                throw new Error('SSJJRPG: unsupported external gun render signature');
            }
            matches.push(call);
        }
    }
    // 1.1.8 has no external-model VBO entry and retains its original class bytes.
    if (matches.length === 0) return node;
    if (matches.length !== 1) throw new Error('SSJJRPG: unsupported external gun render count ' + matches.length);

    var bridge = new Method(O.ACC_PRIVATE | O.ACC_STATIC | O.ACC_SYNTHETIC, BRIDGE, DESC, null, null);
    bridge.visitCode();
    var external = new Label();
    bridge.visitVarInsn(O.ALOAD, 0);
    bridge.visitMethodInsn(O.INVOKESTATIC, 'cn/ssjjcompat/NativeMeshRenderCompat', 'preferNative', '(Lnet/minecraft/world/item/ItemStack;)Z', false);
    bridge.visitJumpInsn(O.IFEQ, external);
    bridge.visitFieldInsn(O.GETSTATIC, RESULT, 'UNSUPPORTED', 'L' + RESULT + ';');
    bridge.visitInsn(O.ARETURN);
    bridge.visitLabel(external);
    bridge.visitFrame(O.F_SAME, 0, null, 0, null);
    bridge.visitVarInsn(O.ALOAD, 0);
    bridge.visitVarInsn(O.ALOAD, 1);
    bridge.visitVarInsn(O.ILOAD, 2);
    bridge.visitVarInsn(O.ALOAD, 3);
    bridge.visitVarInsn(O.ALOAD, 4);
    bridge.visitVarInsn(O.ILOAD, 5);
    bridge.visitVarInsn(O.ILOAD, 6);
    bridge.visitMethodInsn(O.INVOKESTATIC, VBO, 'tryRender', DESC, false);
    bridge.visitInsn(O.ARETURN);
    bridge.visitMaxs(7, 7);
    bridge.visitEnd();
    node.methods.add(bridge);
    matches[0].owner = node.name;
    matches[0].name = BRIDGE;
    return node;
}

function initializeCoreMod() {
    return {'ssjj_native_mesh_render': {
        target: {type: 'CLASS', name: 'com.tacz.guns.client.renderer.item.GunItemRendererWrapper'},
        transformer: preserveNativeMesh
    }};
}
