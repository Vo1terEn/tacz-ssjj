package cn.fusionarsenal.launch;

import cpw.mods.modlauncher.api.*;
import java.util.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Early SERVICE view of the main mod archive; SelfModLocator provides its GAME view. */
public final class HealthTransformationService implements ITransformationService {
    @Override public String name() { return "ssjjrpg_health"; }
    @Override public void initialize(IEnvironment environment) { }
    @Override public void onLoad(IEnvironment environment, Set<String> otherServices) { }
    @Override public List<ITransformer> transformers() {
        Set<String> classes;
        try { classes = HierarchyTargets.discover(); HealthBytecode.hierarchyDiscovered(true); }
        catch (Exception unavailable) {
            HealthBytecode.hierarchyDiscovered(false);
            System.err.println("[SSJJRPG] Health load-time hierarchy discovery unavailable: " + unavailable);
            classes = HierarchyTargets.select(Map.of(), name -> null);
        }
        System.err.println("[SSJJRPG] Health load-time hooks registered for " + classes.size() + " hierarchy classes");
        return List.of(new HealthTransformer(classes));
    }

    public static final class HealthTransformer implements ITransformer<ClassNode> {
        private final Set<Target> targets;
        public HealthTransformer(Set<String> classes) {
            var selected = new LinkedHashSet<Target>();
            for (String name : classes) selected.add(Target.targetClass(name));
            targets = Collections.unmodifiableSet(selected);
            HealthBytecode.targetsRegistered(classes.size());
        }
        @Override public ClassNode transform(ClassNode node, ITransformerVotingContext context) {
            // Preserve the input at this launch-stage boundary, before our wrappers.
            // This intentionally makes no claim about later Mixin/plugin output.
            try {
                var input = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(input);
                HealthBytecode.remember(Thread.currentThread().getContextClassLoader(), node.name, input.toByteArray());
            } catch (RuntimeException unavailable) { HealthBytecode.captureFailed(unavailable); }
            HealthBytecode.transformed(HealthWeaver.weave(node));
            return node;
        }
        @Override public TransformerVoteResult castVote(ITransformerVotingContext context) { return TransformerVoteResult.YES; }
        @Override public Set<Target> targets() { return targets; }
        @Override public String[] labels() { return new String[]{"ssjjrpg_health_loadtime"}; }
    }
}
