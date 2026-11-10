package cn.judgfire;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Magazine enhancement is live equipment state, independent of the retained damage snapshot. */
public final class FireBurstEnergy {
  private FireBurstEnergy() {}
  public static boolean allowed(Player player, boolean requested) {
    return requested && player instanceof ServerPlayer server && server.getInventory()!=null
      && FireBurstRules.installed(server.getMainHandItem())
      && BurstRuntime.active(server,server.getMainHandItem());
  }
  public static double bonus(LivingEntity entity,boolean penetration) {
    return entity instanceof Player player && allowed(player,true) ? CombatConfig.burstEnergy(penetration) : 0d;
  }
}
