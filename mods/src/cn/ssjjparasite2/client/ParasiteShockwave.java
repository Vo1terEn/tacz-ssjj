package cn.ssjjparasite2.client;

import cn.fusionarsenal.config.ArsenalConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Replaces a cannon projectile before tracking with one authoritative shockwave. */
public final class ParasiteShockwave {
    public static final double RANGE = ParasiteShockwaveRules.RANGE, RADIUS = ParasiteShockwaveRules.RADIUS;
    public static final Set<String> PROFILES = Set.of("parasitic_sniper2", "parasitic_sniper2_frost", "parasitic_sniper2_ship");
    private static final String RESOLVED = "ssjjparasite2_shockwave_resolved";
    private static final SimpleChannel CHANNEL = createChannel();
    private ParasiteShockwave() {}

    public static double range() { return ArsenalConfig.number("parasite.shockwave_range"); }
    public static boolean attractionEnabled() { return ArsenalConfig.enabled("parasite.attraction_enabled"); }

    /** Called by the main mod constructor before Forge locks channel registration. */
    public static void initNetwork() { Objects.requireNonNull(CHANNEL); }

    private static SimpleChannel createChannel() {
        var channel = NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation("ssjjparasite2", "shockwave"),
            () -> "2", "2"::equals, "2"::equals);
        channel.registerMessage(0, Pulse.class, Pulse::write, Pulse::read, (message, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> { if (message.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ParasiteShockwaveClient.accept(message)); });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }

    @Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setupShockwaveNetwork(FMLCommonSetupEvent event) { initNetwork(); }
    }

    public record Pulse(int shooter, Vec3 start, Vec3 end, int seed, String profile, double radius) {
        public Pulse(int shooter, Vec3 start, Vec3 end, int seed, String profile) { this(shooter, start, end, seed, profile, RADIUS); }
        public boolean valid() {
            return shooter >= 0 && start != null && end != null && finite(start) && finite(end)
                && start.distanceToSqr(end) <= (ParasiteShockwaveRules.MAX_RANGE + .01) * (ParasiteShockwaveRules.MAX_RANGE + .01)
                && profile != null && PROFILES.contains(profile) && ParasiteShockwaveRules.validRadius(radius);
        }
        private static boolean finite(Vec3 p) {
            return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) <= 30000000 && Math.abs(p.y) <= 30000000 && Math.abs(p.z) <= 30000000;
        }
        public static void write(Pulse p, FriendlyByteBuf b) {
            b.writeVarInt(p.shooter); b.writeDouble(p.start.x); b.writeDouble(p.start.y); b.writeDouble(p.start.z);
            b.writeDouble(p.end.x); b.writeDouble(p.end.y); b.writeDouble(p.end.z); b.writeInt(p.seed); b.writeUtf(p.profile, 64); b.writeDouble(p.radius);
        }
        public static Pulse read(FriendlyByteBuf b) {
            return new Pulse(b.readVarInt(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readInt(), b.readUtf(64), b.readDouble());
        }
    }

    public static boolean enemy(LivingEntity shooter, LivingEntity target) {
        boolean allied = shooter.isAlliedTo(target) || target.isAlliedTo(shooter)
            || target instanceof OwnableEntity owned && shooter.getUUID().equals(owned.getOwnerUUID());
        boolean pvp = !(shooter instanceof ServerPlayer owner && target instanceof Player player)
            || owner.canHarmPlayer(player) && owner.getServer() != null && owner.getServer().isPvpAllowed();
        return !(target instanceof ArmorStand) && ParasiteShockwaveRules.enemy(target.isAlive(), target.isSpectator(),
            target.isAttackable() && !target.isInvulnerable(), target == shooter, allied,
            target instanceof Player player && player.isCreative(), pvp, target.isPassengerOfSameVehicle(shooter));
    }

    /** Called after the existing cannon form has been captured on the original native bullet. */
    public static void replace(EntityJoinLevelEvent event, EntityKineticBullet source) {
        if (!ParasiteShockwaveRules.replace(!event.getLevel().isClientSide(), event.isCanceled(),
            ParasiteScopes.GUN.equals(source.getGunId()), source.getPersistentData().getBoolean(ParasiteCannon.BULLET),
            source.getOwner() instanceof LivingEntity)) return;
        event.setCanceled(true); // No world insertion, entity tracking, vanilla trajectory, or later projectile tick.
        if (source.getPersistentData().getBoolean(RESOLVED)) return;
        source.getPersistentData().putBoolean(RESOLVED, true);
        var shooter = (LivingEntity) source.getOwner();
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var trace = new NativeWave(source);
        String profile = ParasiteEffectProfiles.key(shooter.getMainHandItem());
        if (profile == null || !PROFILES.contains(profile)) profile = "parasitic_sniper2";
        ParasiteEffectsNetwork.remember(trace, profile);
        Vec3 direction = source.getDeltaMovement().normalize();
        if (direction.lengthSqr() < .5) direction = shooter.getViewVector(1).normalize();
        Vec3 start = source.position(), end = start.add(direction.scale(range()));
        var wall = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        double radius = ParasiteAttachments.waveRadius(shooter.getMainHandItem());
        resolve(level, shooter, trace, start, end, radius);
        Pulse pulse = new Pulse(shooter.getId(), start, end, source.getId() ^ (int) level.getGameTime(), profile, radius);
        if (pulse.valid()) {
            Vec3 middle = start.add(end).scale(.5);
            CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(middle.x, middle.y, middle.z, 128, level.dimension())), pulse);
        }
        trace.discard();
    }

    private record Hit(LivingEntity core, Entity part, Vec3 point) {}

    private static void resolve(ServerLevel level, LivingEntity shooter, NativeWave trace, Vec3 start, Vec3 end, double radius) {
        Map<UUID, Hit> unique = new HashMap<>();
        for (Entity candidate : level.getEntities(trace, new AABB(start, end).inflate(radius), e -> e.isAlive() && e.isPickable() && !e.isSpectator())) {
            Entity core = EntityKineticBullet.MaybeMultipartEntity.of(candidate).core();
            if (!(core instanceof LivingEntity living) || !enemy(shooter, living)) continue;
            var contact = ParasiteShockwaveRules.contact(candidate.getBoundingBox(), start, end, radius);
            if (contact.isEmpty()) continue;
            Hit hit = new Hit(living, candidate, contact.get()), previous = unique.get(core.getUUID());
            if (previous == null || start.distanceToSqr(hit.point) < start.distanceToSqr(previous.point)) unique.put(core.getUUID(), hit);
        }
        var hits = new ArrayList<>(unique.values());
        hits.sort(Comparator.comparingDouble(hit -> start.distanceToSqr(hit.point)));
        for (Hit hit : hits) {
            double healthBefore = hit.core.getHealth(), absorptionBefore = hit.core.getAbsorptionAmount();
            var dummyDamage = ParasiteDummyDamage.capture(hit.core);
            var destruction = ParasiteDestruction.capture(shooter, hit.core);
            trace.hit(hit.part, hit.point, start, end);
            double loss = ParasiteDestructionRules.loss(healthBefore, absorptionBefore, hit.core.getHealth(), hit.core.getAbsorptionAmount());
            double destructionLoss = ParasiteDummyDamage.confirmedLoss(dummyDamage, hit.core);
            if (destructionLoss > 0) {
                String profile = ParasiteEffectProfiles.key(shooter.getMainHandItem());
                ParasiteDestruction.confirm(destruction, shooter, hit.core, destructionLoss, profile);
            }
            if (loss > 0 && hit.core.isAlive() && attractionEnabled()) pull(level, hit.core, start, end);
        }
    }

    private static boolean safe(ServerLevel level, LivingEntity target, AABB box) {
        var border = level.getWorldBorder();
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
            || box.minX < border.getMinX() || box.maxX > border.getMaxX()
            || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) return false;
        for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ})
            if (!level.hasChunkAt(BlockPos.containing(x, box.minY, z))) return false;
        return level.noCollision(target, box);
    }

    private static void pull(ServerLevel level, LivingEntity target, Vec3 start, Vec3 end) {
        if (target.isPassenger() || target.isVehicle()) return;
        var destination = ParasiteShockwaveRules.destination(target.getBoundingBox(), target.position(), start, end, box -> safe(level, target, box));
        if (destination.isEmpty()) return;
        Vec3 p = destination.get();
        if (target instanceof ServerPlayer player) player.connection.teleport(p.x, p.y, p.z, player.getYRot(), player.getXRot());
        else target.teleportTo(p.x, p.y, p.z);
        target.fallDistance = 0;
        target.setDeltaMovement(Vec3.ZERO); target.hasImpulse = true; target.hurtMarked = true;
        level.getChunkSource().broadcast(target, new ClientboundTeleportEntityPacket(target));
        level.getChunkSource().broadcastAndSend(target, new ClientboundSetEntityMotionPacket(target));
    }

    /** Native onHitEntity retains gun modifiers, Forge events, AP split and visceral hit confirmation. */
    static final class NativeWave extends EntityKineticBullet {
        private static final List<Field> STATE = new ArrayList<>();
        static {
            for (Field field : EntityKineticBullet.class.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true); STATE.add(field);
            }
        }
        NativeWave(EntityKineticBullet source) {
            super(EntityKineticBullet.TYPE, source.level());
            try { for (Field field : STATE) field.set(this, field.get(source)); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Cannot snapshot native TaCZ cannon projectile", error); }
            setOwner(source.getOwner()); setPos(source.position()); setDeltaMovement(source.getDeltaMovement());
            getPersistentData().merge(source.getPersistentData());
        }
        void hit(Entity target, Vec3 point, Vec3 start, Vec3 end) {
            super.onHitEntity(new TacHitResult(new EntityKineticBullet.EntityResult(target, point, false)), start, end);
        }
    }
}
