package cn.ssjjparasite2.client;

import java.lang.reflect.Modifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;

/** Players, standard bosses, supported training dummies and living targets above 100 maximum health. */
public final class ParasiteDestructionTargets {
    private static final ClassValue<Boolean> BOSS_BAR = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null && current != Mob.class; current = current.getSuperclass()) {
                try {
                    for (var field : current.getDeclaredFields())
                        if (!Modifier.isStatic(field.getModifiers()) && ServerBossEvent.class.isAssignableFrom(field.getType())) return true;
                } catch (LinkageError | SecurityException ignored) { }
            }
            return false;
        }
    };
    private ParasiteDestructionTargets() {}
    public static boolean hasBossBar(Class<?> type) { return type != null && BOSS_BAR.get(type); }
    public static boolean eligible(LivingEntity target) {
        if (target == null) return false;
        if (target instanceof Player || ParasiteDummyDamage.supported(target)
            || target.getType().is(Tags.EntityTypes.BOSSES) || hasBossBar(target.getClass())) return true;
        float maximumHealth = target.getMaxHealth();
        return Float.isFinite(maximumHealth) && maximumHealth > 100;
    }
}
