package cn.fusionarsenal.launch;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Signature-only load-time weaving. No runtime game classes are resolved here. */
public final class HealthWeaver {
    private static final String HOOKS = "cn/fusionarsenal/CreativeRuntimeHooks";
    private static final String SOURCE = "Lnet/minecraft/world/damagesource/DamageSource;";
    private HealthWeaver() {}
    private static boolean matches(String owner, MethodNode method) {
        if (forgeDeath(owner, method)) return true;
        if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return false;
        return hurt(method) || health(method) || setter(method) || death(method) || life(method);
    }
    private static boolean hurt(MethodNode m) {
        return (m.name.equals("hurt") || m.name.equals("m_6469_")) && m.desc.equals("(" + SOURCE + "F)Z");
    }
    private static boolean health(MethodNode m) {
        return (m.name.equals("getHealth") || m.name.equals("m_21223_")) && m.desc.equals("()F");
    }
    private static boolean setter(MethodNode m) {
        return (m.name.equals("setHealth") || m.name.equals("m_21153_")) && m.desc.equals("(F)V");
    }
    private static boolean death(MethodNode m) {
        return (m.name.equals("die") || m.name.equals("m_6667_")) && m.desc.equals("(" + SOURCE + ")V");
    }
    private static boolean life(MethodNode m) {
        return (m.name.equals("isAlive") || m.name.equals("m_6084_") ||
            m.name.equals("isDeadOrDying") || m.name.equals("m_21224_")) && m.desc.equals("()Z");
    }
    private static boolean forgeDeath(String owner, MethodNode m) {
        return owner.equals("net/minecraftforge/common/ForgeHooks") && m.name.equals("onLivingDeath") &&
            m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;" + SOURCE + ")Z");
    }
    private static MethodInsnNode call(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }
    private static boolean already(MethodNode method, String hook) {
        for (var insn : method.instructions)
            if (insn instanceof MethodInsnNode c && c.owner.equals(HOOKS) && c.name.equals(hook)) return true;
        return false;
    }

    private static boolean wrapped(AbstractInsnNode returnInsn, String hook) {
        AbstractInsnNode previous = returnInsn.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous instanceof MethodInsnNode call && call.owner.equals(HOOKS) && call.name.equals(hook);
    }

    /** Public only for bytecode regression tests; accepts no runtime entities. */
    public static int weave(ClassNode node) {
        int changed = 0;
        for (var method : node.methods) {
            if (!matches(node.name, method)) continue;
            if (forgeDeath(node.name, method)) { if (!needsReturnHook(method, Opcodes.IRETURN, "deathResult")) continue; injectDeathResult(method); }
            else if (hurt(method)) { if (already(method, "hurt")) continue; injectHurt(method); }
            else if (health(method)) { if (!needsReturnHook(method, Opcodes.FRETURN, "health")) continue; injectHealth(method); }
            else if (setter(method)) { if (already(method, "request")) continue; injectSetter(method); }
            else if (death(method)) { if (already(method, "enterDeath")) continue; injectDeath(node.name, method); }
            else if (life(method)) { if (!needsReturnHook(method, Opcodes.IRETURN, "life")) continue; injectLife(method); }
            changed++;
        }
        return changed;
    }

    private static boolean needsReturnHook(MethodNode method, int opcode, String hook) {
        for (var insn : method.instructions)
            if (insn.getOpcode() == opcode && !wrapped(insn, hook)) return true;
        return false;
    }

    private static void injectHurt(MethodNode method) {
        var code = new InsnList();
        var ordinary = new LabelNode();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.FLOAD, 2));
        code.add(call("hurt", "(Ljava/lang/Object;Ljava/lang/Object;F)I"));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new JumpInsnNode(Opcodes.IFLT, ordinary));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(ordinary);
        code.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[]{Opcodes.INTEGER}));
        code.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(code);
        method.maxStack = Math.max(method.maxStack, 3);
    }

    private static void injectHealth(MethodNode method) {
        for (var insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.FRETURN && !wrapped(insn, "health")) {
            var code = new InsnList();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new InsnNode(Opcodes.SWAP));
            code.add(call("health", "(Ljava/lang/Object;F)F"));
            method.instructions.insertBefore(insn, code);
        }
        method.maxStack += 1;
    }

    private static void injectSetter(MethodNode method) {
        var code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.FLOAD, 1));
        code.add(call("request", "(Ljava/lang/Object;F)F"));
        code.add(new VarInsnNode(Opcodes.FSTORE, 1));
        method.instructions.insert(code);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static void injectLife(MethodNode method) {
        boolean dying = method.name.equals("isDeadOrDying") || method.name.equals("m_21224_");
        for (var insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.IRETURN && !wrapped(insn, "life")) {
            var code = new InsnList();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new InsnNode(Opcodes.SWAP));
            code.add(new InsnNode(dying ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
            code.add(call("life", "(Ljava/lang/Object;ZZ)Z"));
            method.instructions.insertBefore(insn, code);
        }
        method.maxStack += 2;
    }

    private static void injectDeathResult(MethodNode method) {
        for (var insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.IRETURN && !wrapped(insn, "deathResult")) {
            var code = new InsnList();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new InsnNode(Opcodes.SWAP));
            code.add(call("deathResult", "(Ljava/lang/Object;Z)Z"));
            method.instructions.insertBefore(insn, code);
        }
        method.maxStack += 1;
    }

    private static void injectDeath(String owner, MethodNode method) {
        String id = owner + "." + method.name + method.desc;
        var start = new LabelNode();
        var end = new LabelNode();
        var handler = new LabelNode();
        var admitted = new LabelNode();
        var prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(new LdcInsnNode(id));
        prefix.add(call("enterDeath", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Z"));
        prefix.add(new JumpInsnNode(Opcodes.IFNE, admitted));
        prefix.add(new InsnNode(Opcodes.RETURN));
        prefix.add(admitted);
        prefix.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        prefix.add(start);
        // Only normal returns get inline cleanup; all throws use one catch-all finally.
        for (var insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.RETURN) {
            var cleanup = new InsnList();
            cleanup.add(new VarInsnNode(Opcodes.ALOAD, 0));
            cleanup.add(new LdcInsnNode(id));
            cleanup.add(call("exitDeath", "(Ljava/lang/Object;Ljava/lang/String;)V"));
            method.instructions.insertBefore(insn, cleanup);
        }
        method.instructions.insert(prefix);
        method.instructions.add(end);
        method.instructions.add(handler);
        // Only this is needed after an exception. Dropping parameter/local slots to
        // TOP avoids loading foreign hierarchies to recompute existing stack maps.
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 1, new Object[]{owner},
            1, new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new LdcInsnNode(id));
        method.instructions.add(call("exitDeath", "(Ljava/lang/Object;Ljava/lang/String;)V"));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
        method.maxStack = Math.max(method.maxStack + 2, 3);
    }
}
