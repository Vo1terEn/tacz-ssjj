var O = Java.type('org.objectweb.asm.Opcodes');
var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Insns = Java.type('org.objectweb.asm.tree.InsnList');
var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
var Insn = Java.type('org.objectweb.asm.tree.InsnNode');
var Label = Java.type('org.objectweb.asm.tree.LabelNode');
var Frame = Java.type('org.objectweb.asm.tree.FrameNode');
var Catch = Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
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
    var methods = [];
    var beginCount = 0;
    var endCount = 0;
    var legacyCount = 0;
    for (var i = 0; i < node.methods.size(); i++) {
        var method = node.methods.get(i);
        // The descriptor is stable across Mojmap and Forge's runtime SRG names.
        if (method.desc !== '(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)V') continue;
        var instructions = method.instructions.toArray();
        for (var j = 0; j < instructions.length; j++) {
            var call = instructions[j];
            if (!(call instanceof Call)) continue;
            if (call.owner === BRIDGE) {
                if (call.name === 'beginDraw' && call.desc === '()Ljava/lang/Object;') beginCount++;
                if (call.name === 'endDraw' && call.desc === '(Ljava/lang/Object;)V') endCount++;
                if (call.name === 'draw') legacyCount++;
            }
            if (call.owner === 'com/mojang/blaze3d/vertex/BufferUploader'
                && (call.name === 'drawWithShader' || call.name === 'm_231202_')
                && call.desc === '(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V'
                && call.getOpcode() === O.INVOKESTATIC && !call.itf) {
                calls.push(call);
                methods.push(method);
            }
        }
    }
    if (calls.length !== 1 || legacyCount !== 0) {
        throw new Error('SSJJRPG: unsupported native RenderType buffered draw layout');
    }
    // A second pass must retain the same single vanilla invocation and one scope.
    if (beginCount === 1 && endCount === 2) return node;
    if (beginCount !== 0 || endCount !== 0) {
        throw new Error('SSJJRPG: unsupported partial native RenderType draw scope');
    }
    var method = methods[0];
    // The supported vanilla method has no handlers. An enclosing foreign handler
    // would need its range extended over our rethrow, so reject that changed layout.
    if (!method.tryCatchBlocks.isEmpty()) {
        throw new Error('SSJJRPG: unsupported native RenderType buffered draw handlers');
    }
    var token = method.maxLocals;
    var start = new Label();
    var end = new Label();
    var handler = new Label();
    var before = new Insns();
    // The original RenderedBuffer remains below beginDraw's return value.
    before.add(new Call(O.INVOKESTATIC, BRIDGE, 'beginDraw', '()Ljava/lang/Object;', false));
    before.add(new Var(O.ASTORE, token));
    before.add(start);
    method.instructions.insertBefore(calls[0], before);
    var after = new Insns();
    after.add(end);
    after.add(new Var(O.ALOAD, token));
    after.add(new Call(O.INVOKESTATIC, BRIDGE, 'endDraw', '(Ljava/lang/Object;)V', false));
    method.instructions.insert(calls[0], after);

    // Keep the vanilla invocation unchanged: other mods' @ModifyArg/@Inject
    // hooks must still find BufferUploader.drawWithShader in RenderType.end.
    // The exceptional path never rejoins normal control flow and reads only the
    // new token local. All original locals can therefore be TOP in its frame.
    // Existing frames and local-variable slots remain untouched; ClassWriter(0)
    // can emit this without loading game classes to compute common ancestors.
    // Forge permits ASM nodes and java.util collections, not java.lang class
    // lookups. Build the frame through its existing Java lists; Opcodes.TOP is
    // already the boxed Integer ASM expects. Avoid Java.to(Object[]) as well.
    var frame = new Frame(O.F_FULL, 0, null, 0, null);
    for (var index = 0; index < token; index++) frame.local.add(O.TOP);
    frame.local.add('java/lang/Object');
    frame.stack.add('java/lang/Throwable');
    method.instructions.add(handler);
    method.instructions.add(frame);
    // Leave the original Throwable on the operand stack during restoration.
    method.instructions.add(new Var(O.ALOAD, token));
    method.instructions.add(new Call(O.INVOKESTATIC, BRIDGE, 'endDraw', '(Ljava/lang/Object;)V', false));
    method.instructions.add(new Insn(O.ATHROW));
    method.tryCatchBlocks.add(new Catch(start, end, handler, null));
    method.maxLocals = token + 1;
    method.maxStack = Math.max(method.maxStack + 1, 2);
    return node;
}

function initializeCoreMod() {
    return {
        'ssjj_dimension_cloak_hand_scope': {target: {type: 'CLASS', name: 'net.minecraftforge.client.ForgeHooksClient'}, transformer: handScope},
        'ssjj_dimension_cloak_buffered_draw': {target: {type: 'CLASS', name: 'net.minecraft.client.renderer.RenderType'}, transformer: handDraw}
    };
}
