package cn.ssjjares;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class AresMissile extends Projectile {
    private static final EntityDataAccessor<Boolean> ADVANCED =
        SynchedEntityData.defineId(AresMissile.class, EntityDataSerializers.BOOLEAN);
    private final AresMissileFlight flight = new AresMissileFlight();
    private LivingEntity target;
    private Vec3 lastHead;
    private int ticksLeft;
    private Vec3 serverPosition;
    private float serverYaw;
    private float serverPitch;

    public AresMissile(EntityType<? extends AresMissile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() { entityData.define(ADVANCED, false); }
    public boolean advanced() { return entityData.get(ADVANCED); }

    @Override public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        // The thin collision box would otherwise hide the much larger model at about 15 blocks.
        return distanceSquared < 144 * 144;
    }

    @Override public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.9);
    }

    static void launch(ServerLevel level, LivingEntity owner, LivingEntity target, int tier, int side) {
        var missile = new AresMissile(AresCannons.missileType(), level);
        missile.setOwner(owner);
        missile.target = target;
        missile.entityData.set(ADVANCED, tier == 2);
        var forward = Vec3.directionFromRotation(0, owner.getYRot());
        var right = new Vec3(forward.z, 0, -forward.x);
        var mouth = owner.position().add(0, owner.getBbHeight() + 0.95, 0).add(right.scale(side * 0.57));
        missile.setPos(mouth);
        missile.lastHead = head(target);
        missile.ticksLeft = 140 + (int)Math.ceil(mouth.distanceTo(missile.lastHead) / AresMissileFlight.TRANSFER_SPEED);
        missile.setDeltaMovement(0, 0.75, 0);
        missile.setYRot(owner.getYRot());
        missile.setXRot(-90);
        level.addFreshEntity(missile);
    }

    private static Vec3 head(LivingEntity target) {
        var box = target.getBoundingBox();
        var center = box.getCenter();
        return new Vec3(center.x, box.maxY, center.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (serverPosition != null) {
                setPos(serverPosition);
                setYRot(serverYaw);
                setXRot(serverPitch);
                serverPosition = null;
            }
            return;
        }
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.level() != level()
            || lastHead == null || ticksLeft-- <= 0) {
            discard();
            return;
        }
        boolean tracked = target != null && AresCannons.hostile(owner, target);
        if (tracked) lastHead = head(target);
        else target = null;
        var from = position();
        var step = flight.advance(from, lastHead);
        var to = step.point();
        var wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        var end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        if (step.phase() == AresMissileFlight.Phase.DESCENT && tracked) {
            var box = target.getBoundingBox().inflate(0.10);
            var hit = box.contains(from) ? from : box.clip(from, end).orElse(null);
            if (hit != null) {
                AresMissileDamage.hit(this, owner, target);
                impact(hit);
                return;
            }
        }
        if (wall.getType() != HitResult.Type.MISS || step.atHead()) {
            impact(end);
        } else {
            var velocity = to.subtract(from);
            turn(step.phase(), velocity);
            setDeltaMovement(velocity);
            setPos(to);
        }
    }

    private void turn(AresMissileFlight.Phase phase, Vec3 velocity) {
        if (phase == AresMissileFlight.Phase.RISE) return;
        double horizontal = velocity.horizontalDistance();
        if (horizontal > 0.001) {
            float yaw = (float)Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            setYRot(Mth.approachDegrees(getYRot(), yaw, 32));
        }
        float pitch = phase == AresMissileFlight.Phase.DESCENT ? 90
            : (float)Math.toDegrees(-Math.atan2(velocity.y, horizontal));
        setXRot(Mth.approachDegrees(getXRot(), pitch, 22));
    }

    private void impact(Vec3 point) {
        var level = (ServerLevel)level();
        AresRageNetwork.impact(level, point, advanced());
        level.playSound(null, point.x, point.y, point.z, SoundEvents.FIREWORK_ROCKET_BLAST,
            SoundSource.PLAYERS, 0.55f, advanced() ? 1.28f : 0.92f);
        discard();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        serverPosition = new Vec3(x, y, z);
        serverYaw = yaw;
        serverPitch = pitch;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
