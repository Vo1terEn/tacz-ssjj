package cn.judgfire;

import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Server owns targeting, charge, damage and cadence; the client only displays it. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID)
public final class EmperorCrown {
  public static final String ID = "JudgfireCrownId", CHARGE = "JudgfireCrownCharge";
  private static final String SHOT = "JudgfireCrownShot";
  private static final ResourceKey<DamageType> DAMAGE =
      ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("judgfire", "emperor_crown"));
  private static final Map<Player, Held> HELD = new WeakHashMap<>();

  /** One impact source, one charge credit, even if another handler reposts it. */
  private static final class CrownImpact extends WeaponDamage {
    final UUID weapon;
    boolean charged;

    CrownImpact(ServerLevel level, ServerPlayer owner, UUID weapon, DamageSource bonus) {
      super(
          new DamageSource(
              level
                  .registryAccess()
                  .registryOrThrow(Registries.DAMAGE_TYPE)
                  .getHolderOrThrow(DAMAGE),
              bonus == null ? owner : bonus.getDirectEntity(),
              owner),
          WeaponDamage.weaponName(owner.getMainHandItem()),
          true,
          JudgmentPiercing.active(owner.getMainHandItem()));
      this.weapon = weapon;
    }
  }

  private static final class Held {
    final UUID id;
    final CrownCharge state;
    double sent = -1;
    int tier = -1;

    Held(UUID id, double charge, int elapsed) {
      this.id = id;
      state = new CrownCharge(charge, elapsed);
    }
  }

  public static UUID id(ItemStack stack) {
    var tag = stack.getOrCreateTag();
    if (!tag.hasUUID(ID)) tag.putUUID(ID, UUID.randomUUID());
    return tag.getUUID(ID);
  }

  public static void tag(EntityKineticBullet bullet, ItemStack stack) {
    if (JudgmentFlame.crown(stack)) bullet.getPersistentData().putUUID(SHOT, id(stack));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingDamageEvent event) {
    if (AttackBonuses.calculating(event)) return;
    if (event.isCanceled()
        || event.getAmount() <= 0
        || !(event.getEntity().level() instanceof ServerLevel)
        || !(event.getSource().getEntity() instanceof ServerPlayer player)
        || !valid(player, event.getEntity())) return;
    confirmed(player, event.getEntity(), event.getSource());
  }

  static void confirmed(ServerPlayer player, LivingEntity target, DamageSource source) {
    UUID weapon;
    if (source instanceof CrownImpact impact) {
      if (impact.charged) return;
      weapon = impact.weapon;
    } else if (source.getDirectEntity() instanceof EntityKineticBullet bullet
        && JudgmentFlame.GUN.equals(bullet.getGunId())
        && bullet.getPersistentData().hasUUID(SHOT)) {
      // Native armor/piercing components of one bullet count as one hit per victim.
      String seen = "JudgfireCrownCharged_" + target.getUUID();
      var data = bullet.getPersistentData();
      if (data.getBoolean(seen)) return;
      data.putBoolean(seen, true);
      weapon = data.getUUID(SHOT);
    } else return;
    var stack = player.getMainHandItem();
    if (!JudgmentFlame.crown(stack) || !id(stack).equals(weapon)) return;
    if (source instanceof CrownImpact impact) impact.charged = true;
    Held held = held(player, stack);
    held.state.lock(BurstRuntime.active(player, stack));
    held.state.hit();
    save(stack, held);
    sync(player, held);
    if (source instanceof CrownImpact) {
      RedLotus.crownHit(player, target);
      BurstRuntime.crownHit(player, target);
    }
  }

  public static void beginBurst(ServerPlayer player, ItemStack stack) {
    if (JudgmentFlame.crown(stack)) {
      var held = held(player, stack);
      held.state.lock(true);
      save(stack, held);
      sync(player, held);
    }
  }

  private static Held held(Player player, ItemStack stack) {
    UUID id = id(stack);
    var old = HELD.get(player);
    if (old == null || !old.id.equals(id)) {
      old =
          new Held(
              id,
              stack.getOrCreateTag().getDouble(CHARGE),
              stack.getOrCreateTag().getInt("JudgfireCrownDecayTicks"));
      HELD.put(player, old);
    }
    return old;
  }

  private static void save(ItemStack stack, Held held) {
    var tag = stack.getOrCreateTag();
    tag.putDouble(CHARGE, held.state.value());
    tag.putInt("JudgfireCrownDecayTicks", held.state.decayProgress());
  }

  private static void sync(ServerPlayer player, Held held) {
    int tier = held.state.tier(RedLotus.CROWN_MID.get(), RedLotus.CROWN_HIGH.get());
    if (held.sent != held.state.value() || tier != held.tier || player.tickCount % 20 == 0) {
      JudgmentFlame.CHANNEL.send(
          PacketDistributor.PLAYER.with(() -> player),
          new JudgmentFlame.CrownState(held.id, held.state.value(), tier));
      held.sent = held.state.value();
      held.tier = tier;
    }
  }

  private static boolean valid(LivingEntity owner, LivingEntity target) {
    if (target == owner
        || !target.isAlive()
        || target.isSpectator()
        || !target.isAttackable()
        || owner.isAlliedTo(target)) return false;
    if (target instanceof Player player)
      return !player.isCreative()
          && owner instanceof Player attacker
          && attacker.canHarmPlayer(player)
          && owner.getServer() != null
          && owner.getServer().isPvpAllowed();
    return true;
  }

  private static boolean enemy(ServerPlayer owner, LivingEntity target, ServerLevel level) {
    return valid(owner, target)
        && (CombatConfig.crownThroughWalls() || CrownTargets.visible(owner, target))
        && CrownHoming.accepts(CrownTargets.hostile(target), RedLotus.marked(owner, target, level));
  }

  @SubscribeEvent
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END
        || event.side != LogicalSide.SERVER
        || !(event.player instanceof ServerPlayer player)) return;
    var stack = player.getMainHandItem();
    if (!player.isAlive() || player.isSpectator() || !JudgmentFlame.crown(stack)) {
      HELD.remove(player);
      return;
    }
    var held = held(player, stack);
    double mid = RedLotus.CROWN_MID.get(), high = RedLotus.CROWN_HIGH.get();
    held.state.lock(BurstRuntime.active(player, stack));
    boolean ready = held.state.tick(mid, high);
    save(stack, held);
    sync(player, held);
    if (!ready || !CrownControl.enabled(stack) || CombatConfig.range() <= 0) return;
    ServerLevel level = player.serverLevel();
    var targets = CrownTargets.collect(level, player, target -> enemy(player, target, level));
    // Prefer your own active marks, then nearest target. A stable UUID breaks ties.
    targets.sort(
        Comparator.<LivingEntity>comparingInt(t -> RedLotus.marked(player, t, level) ? 0 : 1)
            .thenComparingDouble(player::distanceToSqr)
            .thenComparing(LivingEntity::getUUID));
    if (targets.isEmpty()) return;
    var target = targets.get(0);
    held.state.attacked();
    int tier = held.state.tier(mid, high);
    Vec3 start = player.position().add(0, player.getBbHeight() + .63, 0),
        end = target.getBoundingBox().getCenter();
    // User-requested instant damage; all subsequent flight/trail visuals are cosmetic.
    var shot = CrownAttack.create(player, stack);
    try {
      var bonus =
          shot == null
              ? null
              : new WeaponDamage(
                  com.tacz.guns.init.ModDamageTypes.Sources.bullet(
                      level.registryAccess(), shot, player, true),
                  WeaponDamage.weaponName(stack),
                  true,
                  JudgmentPiercing.active(stack));
      var source = new CrownImpact(level, player, held.id, bonus);
      float damage =
          shot == null
              ? AttackBonuses.attributes(
                  player, CrownAttack.base(BurstRuntime.active(player, stack)))
              : 0;
      if (shot != null)
        damage = AttackBonuses.bullet(shot, player, target, bonus, shot.getDamage(end));
      damage = CrownRangeBonus.apply(stack, player.position().distanceTo(target.position()), damage);
      JudgmentDamage.Result direct =
          JudgmentPiercing.active(stack)
              ? JudgmentDamage.apply(player, target, source, damage)
              : new JudgmentDamage.Result(false, 0);
      double lost;
      if (direct.handled()) lost = direct.loss();
      else {
        float before = target.getHealth();
        lost =
            damage > 0 && target.hurt(source, damage)
                ? Math.max(0, before - target.getHealth())
                : 0;
      }
      if (shot != null) AttackBonuses.bulletHit(shot, target, player, bonus, lost);
    } finally {
      if (shot != null) shot.discard();
    }
    var packet = new JudgmentFlame.CrownStrike(start, end, tier, WeaponSkins.skin(stack));
    JudgmentFlame.CHANNEL.send(
        PacketDistributor.NEAR.with(
            () ->
                new PacketDistributor.TargetPoint(
                    player.getX(), player.getY(), player.getZ(), 96, level.dimension())),
        packet);
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof Player player) HELD.remove(player);
  }
}
