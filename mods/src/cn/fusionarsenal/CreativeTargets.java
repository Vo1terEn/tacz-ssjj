package cn.fusionarsenal;

import com.tacz.guns.entity.EntityKineticBullet;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.entity.PartEntity;
import net.minecraft.world.phys.Vec3;

/** Resolve public multipart parent contracts and broaden only creative-shot rays. */
public final class CreativeTargets {
    private CreativeTargets() {}
    private static final String[] PARENT_METHODS = {
        "getParent", "getParentEntity", "getLivingParent", "getRootEntity"
    };
    private static final ClassValue<List<Method>> PARENTS = new ClassValue<>() {
        @Override protected List<Method> computeValue(Class<?> type) {
            List<Method> result = new ArrayList<>();
            for (String name : PARENT_METHODS) try {
                Method method = type.getMethod(name);
                if (!Modifier.isStatic(method.getModifiers())
                    && Entity.class.isAssignableFrom(method.getReturnType())) {
                    method.trySetAccessible();
                    result.add(method);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
            return List.copyOf(result);
        }
    };

    /** Never use an attacker's owner relation as an entity's multipart parent. */
    public static Entity resolve(Entity entity) {
        if (entity == null || entity instanceof LivingEntity) return entity;
        Entity current = entity;
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; depth < 8 && seen.add(current); depth++) {
            if (current instanceof LivingEntity) return current;
            Entity parent = null;
            if (current instanceof PartEntity<?> part) parent = part.getParent();
            if (parent == null || parent == current) {
                for (Method method : PARENTS.get(current.getClass())) try {
                    Object value = method.invoke(current);
                    if (value instanceof Entity candidate && candidate != current
                        && candidate.level() == entity.level()) { parent = candidate; break; }
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
            if (parent == null || parent == current || parent.level() != entity.level()) break;
            current = parent;
        }
        return entity;
    }

    private static boolean creative(Projectile projectile) {
        return projectile instanceof EntityKineticBullet bullet && CreativeDamage.bulletEligible(bullet);
    }

    private static boolean target(Entity entity) {
        if (entity == null || entity.isRemoved() || entity.isSpectator()) return false;
        Entity core = resolve(entity);
        return core instanceof LivingEntity living && !living.isRemoved() && !living.isSpectator()
            && !CreativeHealthState.terminal(living);
    }

    /** Preserve the exact original predicate object for all ordinary projectiles. */
    public static Predicate<Entity> predicate(Predicate<Entity> original, Projectile projectile) {
        if (!creative(projectile)) return original;
        Entity owner = projectile.getOwner();
        return entity -> {
            Entity core = resolve(entity);
            if (core != null && owner != null && (core == owner || core.isPassengerOfSameVehicle(owner))) return false;
            return target(entity) || original.test(entity);
        };
    }

    public static boolean alive(Entity entity, Projectile projectile) {
        return creative(projectile) && target(entity) || entity.isAlive();
    }

    /** A single piercing segment must not commit health repeatedly through its parts. */
    public static List<EntityKineticBullet.EntityResult> results(
        List<EntityKineticBullet.EntityResult> original, Projectile projectile, Vec3 start) {
        if (!creative(projectile)) return original;
        Map<Entity,Integer> cores = new IdentityHashMap<>();
        List<EntityKineticBullet.EntityResult> result = new ArrayList<>(original.size());
        for (EntityKineticBullet.EntityResult hit : original) {
            Entity core = resolve(hit.getEntity());
            if (!(core instanceof LivingEntity)) { result.add(hit); continue; }
            Integer index = cores.get(core);
            if (index == null) { cores.put(core,result.size());result.add(hit); }
            else if (hit.getHitPos().distanceToSqr(start) < result.get(index).getHitPos().distanceToSqr(start))
                result.set(index,hit);
        }
        return result;
    }
}
