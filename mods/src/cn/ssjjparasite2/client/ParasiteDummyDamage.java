package cn.ssjjparasite2.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

/** Optional integration with MmmMmmMmmMmm's accepted-damage counter. */
public final class ParasiteDummyDamage {
    private static final String DUMMY = "net.mehvahdjukaar.dummmmmmy.common.TargetDummyEntity";
    private record Access(Field damage, Method infinite) {}
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override protected Optional<Access> computeValue(Class<?> type) {
            for (Class<?> current = type; current != null && LivingEntity.class.isAssignableFrom(current); current = current.getSuperclass()) {
                if (!DUMMY.equals(current.getName())) continue;
                try {
                    Field damage = current.getDeclaredField("totalDamageTakenInCombat");
                    Method infinite = current.getMethod("hasInfiniteHealth");
                    if (damage.getType() == float.class && !Modifier.isStatic(damage.getModifiers())
                        && infinite.getReturnType() == boolean.class && !Modifier.isStatic(infinite.getModifiers())
                        && damage.trySetAccessible()) return Optional.of(new Access(damage, infinite));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
                break;
            }
            return Optional.empty();
        }
    };

    public record Snapshot(LivingEntity target, double health, double absorption, double recordedDamage, boolean infinite) {}
    private ParasiteDummyDamage() {}

    public static boolean supported(LivingEntity target) {
        return target != null && ACCESS.get(target.getClass()).isPresent();
    }

    public static Snapshot capture(LivingEntity target) {
        if (target == null) return new Snapshot(null, 0, 0, Double.NaN, false);
        double damage = Double.NaN;
        boolean infinite = false;
        var access = ACCESS.get(target.getClass());
        if (access.isPresent()) {
            try {
                infinite = Boolean.TRUE.equals(access.get().infinite.invoke(target));
                damage = access.get().damage.getFloat(target);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                infinite = false;
            }
        }
        return new Snapshot(target, target.getHealth(), target.getAbsorptionAmount(), damage, infinite);
    }

    /** Capture around one synchronous native impact, before any destruction detonation. */
    public static double confirmedLoss(Snapshot before, LivingEntity target) {
        if (before == null || target == null || before.target != target) return 0;
        Snapshot after = capture(target);
        double nativeLoss = ParasiteDestructionRules.loss(before.health, before.absorption, after.health, after.absorption);
        if (!before.infinite || !after.infinite || !supported(target)
            || !Double.isFinite(before.recordedDamage) || !Double.isFinite(after.recordedDamage)
            || !Double.isFinite(before.absorption) || !Double.isFinite(after.absorption)) return nativeLoss;
        double recorded = after.recordedDamage - before.recordedDamage;
        if (!(recorded > 0) || !Double.isFinite(recorded)) return nativeLoss;
        // The dummy counter represents health damage; absorption is settled separately.
        double absorbed = Math.max(0, before.absorption - Math.max(0, after.absorption));
        double accepted = recorded + absorbed;
        return Double.isFinite(accepted) ? Math.max(nativeLoss, accepted) : nativeLoss;
    }
}
