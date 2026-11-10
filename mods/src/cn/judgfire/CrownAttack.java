package cn.judgfire;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * An unspawned native projectile supplies the current gun cache/property context for one crown hit.
 */
final class CrownAttack {
  private static final String CROWN = "JudgfireCrownDamageContext";
  private static final AtomicBoolean WARNED = new AtomicBoolean();

  static boolean crown(EntityKineticBullet bullet) {
    return bullet.getPersistentData().getBoolean(CROWN);
  }

  static float base(boolean burst) {
    return BurstTiming.crownDamage(CombatConfig.crownDamage(), burst);
  }

  static EntityKineticBullet create(ServerPlayer player, ItemStack stack) {
    try {
      var gun = IGun.getIGunOrNull(stack);
      if (gun == null || !JudgmentFlame.GUN.equals(gun.getGunId(stack))) return null;
      if (IGunOperator.fromLivingEntity(player).getCacheProperty() == null) return null;
      var index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElse(null);
      if (index == null) return null;
      var shot =
          new EntityKineticBullet(
              player.serverLevel(),
              player,
              stack,
              index.getGunData().getAmmoId(),
              gun.getGunId(stack),
              gun.getGunDisplayId(stack),
              false,
              index.getGunData(),
              index.getBulletData());
      shot.getPersistentData().putBoolean(CROWN, true);
      BurstRuntime.tag(shot, stack);
      WeaponDamage.tag(shot, stack);
      return shot;
    } catch (RuntimeException error) {
      if (WARNED.compareAndSet(false, true))
        LogUtils.getLogger()
            .warn("Crown gun bonus context unavailable; retaining base/attribute bonuses", error);
      return null;
    }
  }
}
