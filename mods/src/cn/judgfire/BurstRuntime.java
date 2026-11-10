package cn.judgfire;

import com.tacz.guns.api.event.common.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID)
public final class BurstRuntime {
  private static final String DATA = "JudgfireBurst", BOOST = "JudgfireBurstShot";
  private static final Map<ServerPlayer, UUID> HUD_HELD = new WeakHashMap<>();

  private static CompoundTag data(ServerPlayer player) {
    var root = player.getPersistentData();
    if (!root.contains(DATA)) root.put(DATA, new CompoundTag());
    return root.getCompound(DATA);
  }

  private static long now(ServerPlayer player) {
    return player.server.overworld().getGameTime();
  }

  public static boolean loaded(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return gun != null && (gun.getCurrentAmmoCount(stack) > 0 || gun.hasBulletInBarrel(stack));
  }

  public static boolean active(ServerPlayer player, ItemStack stack) {
    var tag = data(player);
    return player.isAlive()
        && JudgmentFlame.burst(stack)
        && tag.hasUUID("Weapon")
        && tag.getUUID("Weapon").equals(EmperorCrown.id(stack))
        && BurstTiming.active(now(player), tag.getLong("Until"));
  }

  public static double energyBonus(LivingEntity entity) {
    return energyBonus(entity, false);
  }

  public static double energyBonus(LivingEntity entity, boolean penetration) {
    return entity instanceof ServerPlayer player
            && player.getInventory() != null
            && active(player, player.getMainHandItem())
        ? CombatConfig.burstEnergy(penetration)
        : 0;
  }

  private static void sync(ServerPlayer player, boolean started) {
    var tag = data(player);
    var held = player.getMainHandItem();
    UUID weapon =
        tag.hasUUID("Weapon")
            ? tag.getUUID("Weapon")
            : JudgmentFlame.burst(held) ? EmperorCrown.id(held) : null;
    if (weapon == null) return;
    var packet =
        new JudgmentFlame.BurstState(
            player.getId(),
            weapon,
            BurstTiming.remaining(now(player), tag.getLong("Until")),
            BurstTiming.remaining(now(player), tag.getLong("Cooldown")),
            started,
            WeaponSkins.skin(held));
    JudgmentFlame.CHANNEL.send(
        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
  }

  public static void activate(ServerPlayer player, UUID weapon) {
    if (player == null || !player.isAlive() || player.isSpectator() || BurstStun.stunned(player))
      return;
    var stack = player.getMainHandItem();
    if (!JudgmentFlame.burst(stack) || !loaded(stack) || !EmperorCrown.id(stack).equals(weapon))
      return;
    var tag = data(player);
    long now = now(player);
    if (tag.contains("LastRequest") && now - tag.getLong("LastRequest") < 4) return;
    tag.putLong("LastRequest", now);
    int cooldown = BurstTiming.remaining(now, tag.getLong("Cooldown"));
    if (cooldown > 0) {
      sync(player, false);
      player.displayClientMessage(
          Component.translatable("message.judgfire.burst_cooldown", (cooldown + 19) / 20), true);
      return;
    }
    tag.putUUID("Weapon", weapon);
    tag.putLong("Until", now + BurstTiming.DURATION);
    tag.putLong("Cooldown", now + RedLotus.BURST_COOLDOWN.get());
    EnergyAttributes.syncBurst(player, true);
    // Active marks are owner-specific. Iterate all loaded living entities, with
    // no new range/visibility limit beyond the existing mark lifetime.
    var level = player.serverLevel();
    for (var entity : level.getAllEntities())
      if (entity instanceof LivingEntity target
          && target != player
          && target.isAlive()
          && !target.isSpectator()
          && !player.isAlliedTo(target)
          && RedLotus.marked(player, target, level)) {
        if (target instanceof net.minecraft.world.entity.player.Player other
            && (other.isCreative()
                || !player.canHarmPlayer(other)
                || !player.server.isPvpAllowed())) continue;
        BurstStun.apply(target, RedLotus.BURST_STUN.get());
      }
    EmperorCrown.beginBurst(player, stack);
    sync(player, true);
    level.playSound(
        null,
        player.getX(),
        player.getY(),
        player.getZ(),
        BurstStun.SOUND.get(),
        net.minecraft.sounds.SoundSource.PLAYERS,
        1f,
        1f);
  }

  public static void tag(EntityKineticBullet bullet, ItemStack stack) {
    if (bullet.getOwner() instanceof ServerPlayer player && active(player, stack))
      bullet.getPersistentData().putBoolean(BOOST, true);
  }

  static boolean boosted(EntityKineticBullet bullet) {
    return bullet.getPersistentData().getBoolean(BOOST);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void damage(EntityHurtByGunEvent.Pre event) {
    if (event.getLogicalSide().isClient()
        || !JudgmentFlame.GUN.equals(event.getGunId())
        || !(event.getBullet() instanceof EntityKineticBullet bullet)) return;
    // Every body part uses the same base, before the native armor split. Preserve
    // the firing-time burst snapshot, rather than stacking a boost per component.
    event.setHeadshotMultiplier(1f);
    event.setHeadshot(false);
    event.setBaseAmount(AttackBonuses.gun(event.getAttacker(), bullet, event.getBaseAmount()));
  }

  public static void crownHit(ServerPlayer player, LivingEntity target) {
    if (active(player, player.getMainHandItem()))
      BurstStun.apply(target, RedLotus.BURST_RESTUN.get());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
      return;
    var heldStack = player.getMainHandItem();
    UUID held = JudgmentFlame.burst(heldStack) ? EmperorCrown.id(heldStack) : null;
    if (held == null) HUD_HELD.remove(player);
    else if (!held.equals(HUD_HELD.put(player, held))) sync(player, false);
    var tag = data(player);
    EnergyAttributes.syncBurst(player, active(player, heldStack));
    if (!tag.hasUUID("Weapon")) {
      // A new client level clears its HUD even when the held weapon is unchanged.
      // Keep supplying ready snapshots before the first activation as well.
      if (held != null && player.tickCount % 20 == 0) sync(player, false);
      return;
    }
    if (tag.getLong("Until") > 0 && !active(player, player.getMainHandItem())) {
      tag.putLong("Until", 0);
      sync(player, false);
    } else if (player.tickCount % 20 == 0) sync(player, false);
  }

  private static void cancel(ServerPlayer player) {
    EnergyAttributes.syncBurst(player, false);
    var tag = data(player);
    if (tag.getLong("Until") > 0) {
      tag.putLong("Until", 0);
      sync(player, false);
    }
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      HUD_HELD.remove(player);
      cancel(player);
    }
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      HUD_HELD.remove(player);
      cancel(player);
    }
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) cancel(player);
  }

  @SubscribeEvent
  public static void clone(PlayerEvent.Clone event) {
    var saved = event.getOriginal().getPersistentData().getCompound(DATA).copy();
    saved.putLong("Until", 0);
    event.getEntity().getPersistentData().put(DATA, saved);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void reload(GunReloadEvent event) {
    if (BurstStun.stunned(event.getEntity())
        || JudgmentFlame.burst(event.getGunItemStack()) && loaded(event.getGunItemStack()))
      event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void fire(GunFireEvent event) {
    if (BurstStun.stunned(event.getShooter())) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void attack(LivingAttackEvent event) {
    if (event.getSource().getEntity() instanceof LivingEntity entity && BurstStun.stunned(entity))
      event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void use(LivingEntityUseItemEvent.Start event) {
    if (BurstStun.stunned(event.getEntity())) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void interact(PlayerInteractEvent event) {
    if (event.isCancelable() && BurstStun.stunned(event.getEntity())) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void melee(AttackEntityEvent event) {
    if (BurstStun.stunned(event.getEntity())) event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void breaking(BlockEvent.BreakEvent event) {
    if (BurstStun.stunned(event.getPlayer())) event.setCanceled(true);
  }
}
