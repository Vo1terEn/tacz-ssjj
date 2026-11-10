package cn.ssjjparasite2.client;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Server-only visceral marks shared by tier I/II, applied after a confirmed hit. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class ParasiteVisceral {
    // Values never reference weak keys. Unloaded targets/projectiles cannot be retained here.
    private static final Map<LivingEntity, ParasiteVisceralRules.Mark> MARKS = new WeakHashMap<>();
    private static final Map<EntityKineticBullet, Map<UUID, Shot>> SHOTS = new WeakHashMap<>();
    private static final Map<EntityHurtByGunEvent.Pre, Boolean> PREPARED = new WeakHashMap<>();

    private static final class Shot {
        final double before;
        final int stacks;
        boolean finished;
        Shot(double before, int stacks) { this.before = before; this.stacks = stacks; }
    }

    private ParasiteVisceral() {}

    public static boolean eligible(ResourceLocation id) {
        return id != null && id.getNamespace().equals("ssjjparasite2")
            && (id.getPath().equals("parasitic_sniper1") || id.getPath().equals("parasitic_sniper2"));
    }

    private static LivingEntity target(Entity entity) {
        if (entity == null) return null;
        // Match native TaCZ: Pre can contain a hit part, while Post contains its living core.
        Entity core = EntityKineticBullet.MaybeMultipartEntity.of(entity).core();
        return core instanceof LivingEntity living ? living : null;
    }

    private static double pool(LivingEntity target) {
        return (double) target.getHealth() + target.getAbsorptionAmount();
    }

    public static int stacks(LivingEntity target) {
        if (target == null || !(target.level() instanceof ServerLevel level) || !target.isAlive()) return 0;
        var mark = MARKS.get(target);
        int count = ParasiteVisceralRules.stacks(mark, level.getGameTime());
        if (count == 0) MARKS.remove(target);
        return count;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(EntityHurtByGunEvent.Pre event) {
        if (event.isCanceled() || event.getLogicalSide() != LogicalSide.SERVER || !eligible(event.getGunId())
            || !(event.getBullet() instanceof EntityKineticBullet bullet) || !eligible(bullet.getGunId())
            || !Float.isFinite(event.getBaseAmount()) || event.getBaseAmount() <= 0) return;
        LivingEntity target = target(event.getHurtEntity());
        if (target == null || !(target.level() instanceof ServerLevel) || !target.isAlive()
            || PREPARED.put(event, Boolean.TRUE) != null) return;
        var victims = SHOTS.computeIfAbsent(bullet, key -> new HashMap<>());
        // Capture old stacks once per projectile/core, including multipart or reposted hits.
        Shot shot = victims.computeIfAbsent(target.getUUID(), key -> new Shot(pool(target), stacks(target)));
        event.setBaseAmount(ParasiteVisceralRules.damage(event.getBaseAmount(), shot.stacks));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && eligible(event.getGunId())
            && event.getBullet() instanceof EntityKineticBullet bullet) finish(bullet, target(event.getHurtEntity()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void kill(EntityKillByGunEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && eligible(event.getGunId())
            && event.getBullet() instanceof EntityKineticBullet bullet) finish(bullet, target(event.getKilledEntity()));
    }

    private static void finish(EntityKineticBullet bullet, LivingEntity target) {
        if (target == null || !(target.level() instanceof ServerLevel level)) return;
        var victims = SHOTS.get(bullet);
        Shot shot = victims == null ? null : victims.get(target.getUUID());
        if (shot == null || shot.finished) return;
        shot.finished = true;
        // TaCZ discards both native hurt() results and emits Post even after rejected damage.
        // Compare after the complete normal/AP pair; absorption loss also confirms a hit.
        if (!(pool(target) < shot.before)) return;
        if (!target.isAlive()) { MARKS.remove(target); return; }
        MARKS.put(target, ParasiteVisceralRules.hit(MARKS.get(target), level.getGameTime()));
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) MARKS.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof LivingEntity target) MARKS.remove(target);
        if (event.getEntity() instanceof EntityKineticBullet bullet) SHOTS.remove(bullet);
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        MARKS.entrySet().removeIf(entry -> entry.getKey().level() == level
            && (!entry.getKey().isAlive() || ParasiteVisceralRules.stacks(entry.getValue(), now) == 0));
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) { MARKS.clear(); SHOTS.clear(); PREPARED.clear(); }
}
