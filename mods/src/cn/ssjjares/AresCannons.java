package cn.ssjjares;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class AresCannons {
    static final int VOLLEY_TICKS = 10 * 20;
    private static final String NEXT_VOLLEY = "ssjjrpg_ares_cannons_ready";
    private static EntityType<AresMissile> missileType;

    private AresCannons() {}

    @Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.ENTITY_TYPE, helper -> {
                var id = AresArmorItems.id("shoulder_missile");
                missileType = EntityType.Builder.of(AresMissile::new, MobCategory.MISC)
                    .sized(0.24f, 0.24f).noSave().noSummon().setTrackingRange(12)
                    .setUpdateInterval(1).setShouldReceiveVelocityUpdates(true).build(id.toString());
                helper.register(id, missileType);
            });
        }
    }

    public static EntityType<AresMissile> missileType() { return missileType; }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer owner)
            || !owner.isAlive() || owner.isSpectator()) return;
        ServerLevel level = owner.serverLevel();
        long now = level.getGameTime();
        if (now % 5 != owner.getId() % 5) return;
        var data = owner.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (now < persisted.getLong(NEXT_VOLLEY)) return;
        int tier = AresCombat.equipped(owner).shoulderTier;
        if (tier == 0) return;
        var targets = targets(level, owner);
        if (targets.isEmpty()) return;
        // Set the shared cooldown before spawning so either equipment slot can supply this volley.
        persisted.putLong(NEXT_VOLLEY, now + VOLLEY_TICKS);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        AresMissile.launch(level, owner, targets.get(0), tier, -1);
        AresMissile.launch(level, owner, targets.get(targets.size() > 1 ? 1 : 0), tier, 1);
        var point = owner.position();
        level.playSound(null, point.x, point.y, point.z,
            SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.65f, tier == 2 ? 1.15f : 0.85f);
    }

    static List<LivingEntity> targets(ServerLevel level, LivingEntity owner) {
        var center = owner.position();
        var candidates = level.getEntitiesOfClass(LivingEntity.class, AresRage.bounds(center),
            target -> hostile(owner, target) && AresRage.contains(center, target.position()));
        return candidates.stream().sorted(Comparator.<LivingEntity>comparingDouble(target -> center.distanceToSqr(target.position()))
            .thenComparingInt(LivingEntity::getId)).limit(2).toList();
    }

    static boolean hostile(LivingEntity owner, LivingEntity target) {
        return target != owner && target.isAlive() && !target.isSpectator() && target.level() == owner.level()
            && (target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == owner)
            && !owner.isAlliedTo(target) && !target.isAlliedTo(owner);
    }
}
