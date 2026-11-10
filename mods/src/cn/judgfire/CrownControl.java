package cn.judgfire;

import com.tacz.guns.api.entity.IGunOperator;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Per-weapon, server-owned switch. Missing NBT preserves the original automatic behavior. */
public final class CrownControl {
  private static final String DISABLED = "JudgfireCrownAutoDisabled";

  public static boolean enabled(ItemStack stack) {
    var tag = stack.getTag();
    return tag == null || !tag.getBoolean(DISABLED);
  }

  static boolean toggle(ItemStack stack) {
    boolean enabled = !enabled(stack);
    stack.getOrCreateTag().putBoolean(DISABLED, !enabled);
    return enabled;
  }

  public static void request(ServerPlayer player, UUID weapon) {
    if (player == null || !player.isAlive() || player.isSpectator() || BurstStun.stunned(player))
      return;
    var stack = player.getMainHandItem();
    if (!JudgmentFlame.crown(stack) || !EmperorCrown.id(stack).equals(weapon)) return;
    var operator = IGunOperator.fromLivingEntity(player);
    if (operator.getSynIsAiming() || operator.getSynAimingProgress() > 0) return;
    boolean enabled = toggle(stack);
    player.getInventory().setChanged();
    player.displayClientMessage(
        Component.translatable(
            enabled ? "message.judgfire.crown_enabled" : "message.judgfire.crown_disabled"),
        true);
  }

  private CrownControl() {}
}
