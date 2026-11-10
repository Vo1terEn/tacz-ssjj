package cn.judgfire;

import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Red Lotus only; crown and active burst are outside this attachment's skill. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID)
public final class RedLotus {
  public static final String SHOT_TAG = "JudgfireRedLotus";
  public static final ForgeConfigSpec CONFIG;
  static final ForgeConfigSpec.DoubleValue REDUCTION;
  static final ForgeConfigSpec.IntValue DURATION;
  static final ForgeConfigSpec.DoubleValue CROWN_DAMAGE, CROWN_MID, CROWN_HIGH;
  static final ForgeConfigSpec.IntValue BURST_COOLDOWN, BURST_STUN, BURST_RESTUN, BURST_IMMUNITY;
  private static final Map<ServerLevel, LotusMarks> LEVELS = new WeakHashMap<>();

  static {
    var builder = new ForgeConfigSpec.Builder();
    REDUCTION =
        builder
            .comment(
                "Damage reduction against your marked enemy while holding the Red Lotus grip gun.")
            .defineInRange("red_lotus_damage_reduction", .25, 0, 1);
    DURATION =
        builder
            .comment("Mark lifetime in server ticks; 100 ticks = 5 seconds.")
            .defineInRange("red_lotus_duration_ticks", 100, 1, 1200);
    CROWN_DAMAGE =
        builder
            .comment("Damage per autonomous Emperor Crown attack; armor still applies.")
            .defineInRange("emperor_crown_damage", 24.0, 0, 10000);
    CROWN_MID =
        builder
            .comment("Charge percent entering 1 attack/second tier.")
            .defineInRange("emperor_crown_mid_percent", 34.0, 0, 100);
    CROWN_HIGH =
        builder
            .comment("Charge percent entering 5 attacks/second tier.")
            .defineInRange("emperor_crown_high_percent", 67.0, 0, 100);
    BURST_COOLDOWN =
        builder
            .comment("Red Lotus Burst cooldown from activation; 500 ticks = 25s.")
            .defineInRange("burst_cooldown_ticks", 500, 300, 72000);
    BURST_STUN =
        builder
            .comment("Initial stun; 60 ticks = 3s.")
            .defineInRange("burst_stun_ticks", 60, 1, 1200);
    BURST_RESTUN =
        builder
            .comment("Crown damage during Burst can stun again; 30 ticks = 1.5s.")
            .defineInRange("burst_restun_ticks", 30, 1, 1200);
    BURST_IMMUNITY =
        builder
            .comment(
                "After stun ends, immunity for 20 ticks = 1s. Hits do not refresh an ongoing stun.")
            .defineInRange("burst_stun_immunity_ticks", 20, 1, 1200);
    CONFIG = builder.build();
  }

  private static LotusMarks ledger(ServerLevel level) {
    return LEVELS.computeIfAbsent(level, key -> new LotusMarks());
  }

  public static boolean marked(LivingEntity owner, LivingEntity target, ServerLevel level) {
    return ledger(level).active(owner.getUUID(), target.getUUID(), level.getGameTime());
  }

  private static boolean enemy(LivingEntity owner, LivingEntity other) {
    return owner != other && other.isAlive() && !owner.isAlliedTo(other);
  }

  private static void mark(LivingEntity owner, LivingEntity target, ServerLevel level) {
    if (!enemy(owner, target)) return;
    int duration = DURATION.get();
    ledger(level).refresh(owner.getUUID(), target.getUUID(), level.getGameTime(), duration);
    if (owner instanceof ServerPlayer player)
      JudgmentFlame.CHANNEL.send(
          PacketDistributor.PLAYER.with(() -> player),
          new JudgmentFlame.Mark(
              target.getId(), duration, WeaponSkins.skin(owner.getMainHandItem())));
  }

  /** Crown damage shares the grip's mark lifetime, owner and refresh rules. */
  public static void crownHit(ServerPlayer owner, LivingEntity target) {
    if (JudgmentFlame.lotus(owner.getMainHandItem())) mark(owner, target, owner.serverLevel());
  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void reduce(LivingDamageEvent event) {
    if (AttackBonuses.calculating(event)) return;
    var victim = event.getEntity();
    if (!(victim.level() instanceof ServerLevel level)
        || !JudgmentFlame.lotus(victim.getMainHandItem())
        || !(event.getSource().getEntity() instanceof LivingEntity attacker)
        || !enemy(victim, attacker)) return;
    // Scale final post-armor damage by exactly 25%, once per native component.
    boolean active =
        ledger(level).active(victim.getUUID(), attacker.getUUID(), level.getGameTime());
    if (event.getSource().getDirectEntity() instanceof EntityKineticBullet bullet) {
      // TaCZ splits one impact into armor and piercing damage. A new mark from
      // its first component must not change the second component of that hit.
      var data = bullet.getPersistentData();
      String key = "JudgfireLotusDefence_" + victim.getUUID();
      if (data.contains(key)) active = data.getBoolean(key);
      else data.putBoolean(key, active);
    }
    event.setAmount(LotusMarks.damage(event.getAmount(), CombatConfig.reduction(), active));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damaged(LivingDamageEvent event) {
    if (AttackBonuses.calculating(event)) return;
    if (event.getAmount() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) return;
    var victim = event.getEntity();
    var source = event.getSource();
    if (!(source.getEntity() instanceof LivingEntity attacker) || !enemy(victim, attacker)) return;
    // Only confirmed damage marks a target. The first incoming hit precedes its new mark.
    if (JudgmentFlame.lotus(victim.getMainHandItem())) mark(victim, attacker, level);
    if (source.getDirectEntity() instanceof EntityKineticBullet bullet
        && JudgmentFlame.GUN.equals(bullet.getGunId())
        && bullet.getPersistentData().getBoolean(SHOT_TAG)) mark(attacker, victim, level);
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent event) {
    if (event.getEntity().level() instanceof ServerLevel level)
      ledger(level).removeOwner(event.getEntity().getUUID());
  }

  @SubscribeEvent
  public static void tick(TickEvent.LevelTickEvent event) {
    if (event.phase == TickEvent.Phase.END
        && event.level instanceof ServerLevel level
        && level.getGameTime() % 20 == 0) ledger(level).prune(level.getGameTime());
  }
}
