package cn.judgfire;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Loaded entities and standard combat/boss structure, independent of vendor registration names. */
public final class CrownTargets {
  private static final ClassValue<Boolean> BOSS =
      new ClassValue<>() {
        protected Boolean computeValue(Class<?> type) {
          for (Class<?> current = type;
              current != null && current != Mob.class;
              current = current.getSuperclass()) {
            try {
              for (var field : current.getDeclaredFields())
                if (!Modifier.isStatic(field.getModifiers())
                    && ServerBossEvent.class.isAssignableFrom(field.getType())) return true;
            } catch (LinkageError | SecurityException ignored) {
              // Unknown metadata is not evidence of hostility.
            }
          }
          return false;
        }
      };

  static LivingEntity parent(Entity entity) {
    if (entity instanceof net.minecraftforge.entity.PartEntity<?> part) entity = part.getParent();
    return entity instanceof LivingEntity living ? living : null;
  }

  static boolean hostile(LivingEntity target) {
    if (target.getType().getCategory() == MobCategory.MONSTER || target instanceof Enemy)
      return true;
    return target instanceof Mob mob
        && (BOSS.get(target.getClass()) || mob.getTarget() instanceof Player);
  }

  static boolean visible(LivingEntity owner, LivingEntity target) {
    // Use the configured range, without the vanilla sight helper's independent distance cap.
    return owner.level() == target.level()
        && owner
                .level()
                .clip(
                    new ClipContext(
                        owner.getEyePosition(),
                        target.getEyePosition(),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        owner))
                .getType()
            == HitResult.Type.MISS;
  }

  static List<LivingEntity> collect(
      ServerLevel level, LivingEntity owner, Predicate<LivingEntity> eligible) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<LivingEntity, Boolean>());
    var result = new ArrayList<LivingEntity>();
    // Giant/multipart bodies can have a tiny, displaced or empty parent hitbox. Acquisition uses
    // the same 48-block position rule, without depending on that hitbox or its spatial index.
    for (var entity : level.getAllEntities()) {
      var target = parent(entity);
      if (target != null
          && seen.add(target)
          && CrownCharge.inRange(
              target.getX() - owner.getX(),
              target.getY() - owner.getY(),
              target.getZ() - owner.getZ())
          && eligible.test(target)) result.add(target);
    }
    return result;
  }

  private CrownTargets() {}
}
