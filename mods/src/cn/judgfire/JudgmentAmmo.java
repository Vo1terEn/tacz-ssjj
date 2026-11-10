package cn.judgfire;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IGun;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Finite ammunition. Refunds settle after TaCZ has consumed the shot. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class JudgmentAmmo {
  public static final int CAPACITY = 15;
  private static final String VERSION = "JudgfireAmmoVersion";
  private static final String WEAPON = "JudgfireAmmoWeapon";
  private static final Map<ServerPlayer, IdentityHashMap<ItemStack, Integer>> PENDING =
      new WeakHashMap<>();

  public static int normalized(int magazine, boolean barrel) {
    return (int) Math.min(CAPACITY, Math.max(0L, magazine) + (barrel ? 1 : 0));
  }

  public static int afterHits(int magazine, int hits) {
    return (int) Math.min(CAPACITY, Math.max(0L, magazine) + Math.max(0L, hits));
  }

  public static void normalize(ItemStack stack) {
    if (!JudgmentFlame.ours(stack)) return;
    var gun = IGun.getIGunOrNull(stack);
    var tag = stack.getOrCreateTag();
    int old = gun.getCurrentAmmoCount(stack);
    boolean barrel = gun.hasBulletInBarrel(stack);
    // A charged legacy one-cell gun represented 100%, so convert it once.
    int count = tag.getInt(VERSION) < 4 && old == 1 ? CAPACITY : normalized(old, barrel);
    if (count != old) gun.setCurrentAmmoCount(stack, count);
    if (barrel) gun.setBulletInBarrel(stack, false);
    if (tag.getInt(VERSION) < 4) tag.putInt(VERSION, 4);
    tag.remove("JudgfireCrownAutoDisabled");
  }

  public static boolean blocksReload(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return gun != null && JudgmentFlame.ours(stack)
        && normalized(gun.getCurrentAmmoCount(stack), gun.hasBulletInBarrel(stack)) >= CAPACITY;
  }

  public static boolean blocksReload(LivingEntity owner) {
    return owner != null && blocksReload(owner.getMainHandItem());
  }

  public static void tag(Entity bullet, ItemStack stack) {
    if (bullet == null || !JudgmentFlame.ours(stack)) return;
    var data = stack.getOrCreateTag();
    if (!data.hasUUID(WEAPON)) data.putUUID(WEAPON, UUID.randomUUID());
    bullet.getPersistentData().putUUID(WEAPON, data.getUUID(WEAPON));
  }

  private static ItemStack weapon(ServerPlayer owner, Entity bullet) {
    if (bullet == null || !bullet.getPersistentData().hasUUID(WEAPON)) return null;
    UUID id = bullet.getPersistentData().getUUID(WEAPON);
    for (var stack : owner.getInventory().items) {
      var data = stack.getTag();
      if (JudgmentFlame.ours(stack) && data != null && data.hasUUID(WEAPON)
          && id.equals(data.getUUID(WEAPON))) return stack;
    }
    return null;
  }

  private static boolean claim(Entity bullet, Entity target) {
    String key = "JudgfireAmmoHit_" + target.getUUID();
    var data = bullet.getPersistentData();
    if (data.getBoolean(key)) return false;
    data.putBoolean(key, true);
    return true;
  }

  private static void queue(ServerPlayer owner, ItemStack stack) {
    if (stack == null || !JudgmentFlame.ours(stack)) return;
    var pending = PENDING.computeIfAbsent(owner, unused -> new IdentityHashMap<>());
    pending.merge(stack, 1, (a, b) -> Math.min(CAPACITY, a + b));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void hit(EntityHurtByGunEvent.Post event) {
    if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getBaseAmount() > 0)
        || !JudgmentFlame.GUN.equals(event.getGunId())
        || !(event.getAttacker() instanceof ServerPlayer owner)
        || event.getBullet() == null || event.getHurtEntity() == null
        || event.getHurtEntity() == owner) return;
    ItemStack stack = weapon(owner, event.getBullet());
    if (stack != null && claim(event.getBullet(), event.getHurtEntity())) queue(owner, stack);
  }

  // Native TaCZ emits Kill instead of Post for a lethal hit.
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void killed(EntityKillByGunEvent event) {
    if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getBaseDamage() > 0)
        || !JudgmentFlame.GUN.equals(event.getGunId())
        || !(event.getAttacker() instanceof ServerPlayer owner)
        || event.getBullet() == null || event.getKilledEntity() == null
        || event.getKilledEntity() == owner) return;
    ItemStack stack = weapon(owner, event.getBullet());
    if (stack != null && claim(event.getBullet(), event.getKilledEntity())) queue(owner, stack);
  }

  /** Also covers automatic attacks whose optional native bullet context was unavailable. */
  public static void autoHit(ServerPlayer owner, ItemStack stack, LivingEntity target,
      Entity bullet, double lost) {
    if (!(lost > 0) || !Double.isFinite(lost) || target == owner) return;
    if (bullet == null || claim(bullet, target)) queue(owner, stack);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
        || !(event.player instanceof ServerPlayer owner)) return;
    var pending = PENDING.remove(owner);
    if (pending == null) return;
    boolean changed = false;
    for (var entry : pending.entrySet()) {
      ItemStack stack = entry.getKey();
      boolean held = false;
      for (var item : owner.getInventory().items) if (item == stack) { held = true; break; }
      if (!held || !JudgmentFlame.ours(stack)) continue;
      normalize(stack);
      var gun = IGun.getIGunOrNull(stack);
      int old = gun.getCurrentAmmoCount(stack), count = afterHits(old, entry.getValue());
      if (count != old) { gun.setCurrentAmmoCount(stack, count); changed = true; }
    }
    if (changed) owner.getInventory().setChanged();
  }

  private JudgmentAmmo() {}
}
