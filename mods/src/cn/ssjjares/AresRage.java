package cn.ssjjares;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class AresRage {
    static final int DAMAGE_PERCENT = 100;
    static final int ACTIVE_TICKS = 40 * 20;
    public static final double RADIUS = 64;
    private static final double WORLD_COORDINATE_LIMIT = 30_000_000;
    private static final String COOLDOWN = "ssjjrpg_ares_war_rage_ready";
    private static final Map<ServerLevel, LinkedHashMap<UUID, Field>> FIELDS = new HashMap<>();
    private AresRage() {}

    private record Field(ServerPlayer owner, long until, Set<LivingEntity> captives) {}

    static void activate(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || AresArmorItems.setTier(player) == 0) return;
        var level = player.serverLevel();
        var fields = FIELDS.get(level);
        long now = level.getGameTime();
        var existing = fields == null ? null : fields.get(player.getUUID());
        if (existing != null && now < existing.until) {
            message(player, "active", existing.until - now);
            return;
        }
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        long ready = persisted.getLong(COOLDOWN);
        if (now < ready) {
            message(player, "cooldown", ready - now);
            return;
        }
        int cooldown = cooldownTicks(AresCombat.equipped(player).mask);
        persisted.putLong(COOLDOWN, now + ACTIVE_TICKS + cooldown);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        var field = new Field(player, now + ACTIVE_TICKS, new HashSet<>());
        capture(level, field);
        FIELDS.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(player.getUUID(), field);
        syncBoundary(level, field, (int) Math.min(ACTIVE_TICKS, 15));
        message(player, "started", ACTIVE_TICKS);
    }

    static int cooldownTicks(boolean mask) {
        return (mask ? 20 : 40) * 20;
    }

    private static void message(ServerPlayer player, String key, long ticks) {
        player.displayClientMessage(Component.translatable("ssjj_ares.rage." + key, (ticks + 19) / 20), true);
    }

    static boolean amplifies(ServerPlayer owner, LivingEntity target) {
        var level = owner.serverLevel();
        var fields = FIELDS.get(level);
        var field = fields == null ? null : fields.get(owner.getUUID());
        return field != null && field.owner == owner && active(level, field)
            && contains(owner.position(), target.position());
    }

    static boolean contains(Vec3 center, Vec3 position) {
        double x = position.x - center.x;
        double z = position.z - center.z;
        return x * x + z * z <= RADIUS * RADIUS;
    }

    static Vec3 constrain(Vec3 center, Vec3 position) {
        if (contains(center, position)) return position;
        double x = position.x - center.x;
        double z = position.z - center.z;
        double scale = (RADIUS - 0.001) / Math.hypot(x, z);
        return new Vec3(center.x + x * scale, position.y, center.z + z * scale);
    }

    static Vec3 removeOutwardVelocity(Vec3 center, Vec3 rim, Vec3 velocity) {
        var normal = new Vec3(rim.x - center.x, 0, rim.z - center.z).normalize();
        return velocity.subtract(normal.scale(Math.max(0, velocity.dot(normal))));
    }

    private static boolean active(ServerLevel level, Field field) {
        return level.getGameTime() < field.until && eligible(level, field.owner);
    }

    private static boolean eligible(ServerLevel level, LivingEntity entity) {
        return entity.level() == level && entity.isAlive() && !entity.isSpectator();
    }

    static AABB bounds(Vec3 center) {
        // Include all usable vertical coordinates, even above and below the build limits.
        return new AABB(center.x - RADIUS, -WORLD_COORDINATE_LIMIT, center.z - RADIUS,
                        center.x + RADIUS, WORLD_COORDINATE_LIMIT, center.z + RADIUS);
    }

    private static void capture(ServerLevel level, Field field) {
        var p = field.owner.position();
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, bounds(p),
                candidate -> candidate != field.owner && eligible(level, candidate) && contains(p, candidate.position()))) {
            field.captives.add(entity);
        }
    }

    private static void pull(LivingEntity captive, Vec3 center) {
        Vec3 position = captive.position();
        Vec3 rim = constrain(center, position);
        if (rim == position) return;
        captive.stopRiding();
        if (captive instanceof ServerPlayer player) {
            player.connection.teleport(rim.x, rim.y, rim.z, player.getYRot(), player.getXRot(), Set.of(RelativeMovement.Y));
        } else {
            captive.setPos(rim.x, rim.y, rim.z);
        }
        captive.setDeltaMovement(removeOutwardVelocity(center, rim, captive.getDeltaMovement()));
        captive.hurtMarked = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) return;
        var fields = FIELDS.get(level);
        if (fields == null) return;
        for (Iterator<Field> iterator = fields.values().iterator(); iterator.hasNext();) {
            var field = iterator.next();
            if (active(level, field)) continue;
            iterator.remove();
            syncBoundary(level, field, 0);
            if (eligible(level, field.owner))
                field.owner.displayClientMessage(Component.translatable("ssjj_ares.rage.ended"), true);
        }
        if (fields.isEmpty()) {
            FIELDS.remove(level);
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            // The earliest active field keeps movement custody if two fields overlap.
            var held = new HashSet<LivingEntity>();
            for (var field : fields.values()) {
                field.captives.removeIf(entity -> !eligible(level, entity));
                for (var entity : field.captives)
                    if (held.add(entity)) pull(entity, field.owner.position());
            }
        }
        for (var field : fields.values()) {
            capture(level, field);
            if (event.phase == TickEvent.Phase.END && level.getGameTime() % 5 == 0)
                syncBoundary(level, field, (int)Math.min(15, field.until - level.getGameTime()));
        }
    }

    private static void syncBoundary(ServerLevel level, Field field, int visibleTicks) {
        var center = field.owner.position();
        double range = RADIUS + 192;
        for (var viewer : level.players()) {
            double x = viewer.getX() - center.x;
            double z = viewer.getZ() - center.z;
            if (visibleTicks == 0 || x * x + z * z <= range * range)
                AresRageNetwork.boundary(viewer, level, field.owner, center, visibleTicks);
        }
    }

    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof LivingEntity target && bindingField(target) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void teleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        var field = bindingField(target);
        if (field == null) return;
        var rim = constrain(field.owner.position(), event.getTarget());
        event.setTargetX(rim.x);
        event.setTargetZ(rim.z);
    }

    private static Field bindingField(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return null;
        var fields = FIELDS.get(level);
        if (fields == null || !eligible(level, target)) return null;
        for (var field : fields.values()) {
            if (field.owner != target && active(level, field)
                && (field.captives.contains(target) || contains(field.owner.position(), target.position()))) return field;
        }
        return null;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remove(player.serverLevel(), player.getUUID());
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            remove(player.serverLevel().getServer().getLevel(event.getFrom()), player.getUUID());
    }

    private static void remove(ServerLevel level, UUID owner) {
        var fields = FIELDS.get(level);
        if (fields == null) return;
        var field = fields.remove(owner);
        if (field != null) syncBoundary(level, field, 0);
        if (fields.isEmpty()) FIELDS.remove(level);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) FIELDS.remove(level);
    }
}
