package cn.ssjjparasite2.client;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server memory only, independently owned by shooter and target; no potion or extra gun event. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class ParasiteDestruction {
    public record Mark(UUID shooter, int count, String profile) {}
    public record Ticket(UUID shooter, UUID target, ParasiteDestructionRules.Stack previous, boolean eligible) {}
    private record State(ParasiteDestructionRules.Stack stack, String profile) {}
    // Values contain UUIDs and scalars, never a strong reference to the weak target key.
    private static final Map<LivingEntity, Map<UUID, State>> MARKS = new WeakHashMap<>();
    private ParasiteDestruction() {}

    public static List<Mark> marks(LivingEntity target) {
        if (target == null || !(target.level() instanceof ServerLevel) || !target.isAlive()) return List.of();
        var owners = MARKS.get(target);if (owners == null) return List.of();
        var result = new ArrayList<Mark>();
        owners.forEach((owner, state) -> { if (state.stack.count() > 0 && state.stack.count() < 5) result.add(new Mark(owner, state.stack.count(), state.profile)); });
        return List.copyOf(result);
    }

    /** A native lethal hit can dispatch death before returning to the wave. Keep only
     * this stack snapshot locally; normal death cleanup still runs immediately. */
    public static Ticket capture(LivingEntity shooter, LivingEntity target) {
        if (shooter == null || target == null) return null;
        var owners = MARKS.get(target);var old = owners == null ? null : owners.get(shooter.getUUID());
        return new Ticket(shooter.getUUID(), target.getUUID(), old == null ? null : old.stack,
            target.level() instanceof ServerLevel && shooter.level() == target.level()
                && ParasiteDestructionTargets.eligible(target) && ParasiteShockwave.enemy(shooter, target));
    }

    /** Called once after the whole native wave hit has returned, including both AP parts and Post. */
    public static void confirm(LivingEntity shooter, LivingEntity target, double loss, String profile) {
        confirm(capture(shooter, target), shooter, target, loss, profile);
    }
    public static void confirm(Ticket ticket, LivingEntity shooter, LivingEntity target, double loss, String profile) {
        if (shooter == null || target == null || !(target.level() instanceof ServerLevel)
            || ticket == null || !ticket.eligible || !shooter.getUUID().equals(ticket.shooter) || !target.getUUID().equals(ticket.target)
            || shooter.level() != target.level() || !ParasiteDestructionTargets.eligible(target)
            || !(loss > 0) || !Double.isFinite(loss) || target.isAlive() && !ParasiteShockwave.enemy(shooter, target)) return;
        if (profile == null || !ParasiteShockwave.PROFILES.contains(profile)) profile = "parasitic_sniper2";
        var owners = MARKS.computeIfAbsent(target, key -> new HashMap<>());
        UUID owner = shooter.getUUID();var previous = owners.get(owner);
        // Only lethal native cleanup needs the saved stack. A living target may
        // already have completed a cycle in a reentrant native callback.
        var next = ParasiteDestructionRules.hit(previous == null ? (!target.isAlive() ? ticket.previous : null) : previous.stack, loss);
        if (next.count() < 5) {
            if (!target.isAlive()) { if (owners.isEmpty()) MARKS.remove(target);return; }
            owners.put(owner, new State(next, profile));
            ParasiteEffectsNetwork.destruction(target, shooter, next.count(), false, profile);
            return;
        }
        // Commit the next cycle before callbacks/native hurt. Only ordinary losses
        // enter its sliding window, so a reentrant hit cannot replay this explosion.
        float chance = target.isAlive() ? ParasiteUltimate.retentionChance(shooter.getMainHandItem()) : 0;
        boolean retained = chance > 0 && ParasiteDestructionRules.retain(chance, shooter.getRandom().nextFloat());
        if (retained) owners.put(owner, new State(ParasiteDestructionRules.retained(next), profile));
        else owners.remove(owner);
        if (owners.isEmpty()) MARKS.remove(target);
        ParasiteEffectsNetwork.destruction(target, shooter, 5, true, profile);
        if (target.isAlive()) ParasiteDestructionDamage.apply(shooter, target, next.damage());
        // Native callbacks may kill/unload the target or advance the retained cycle.
        // Synchronize the current state rather than overwriting it with an old four.
        var currentOwners = MARKS.get(target);
        var current = currentOwners == null ? null : currentOwners.get(owner);
        if (target.isAlive() && !target.isRemoved() && current != null)
            ParasiteEffectsNetwork.destruction(target, shooter, current.stack.count(), false, current.profile);
    }

    private static void clear(LivingEntity target) {
        var owners = MARKS.remove(target);
        if (owners == null || !(target.level() instanceof ServerLevel level)) return;
        owners.forEach((owner, state) -> ParasiteEffectsNetwork.destructionUuid(target, owner, 0, false, state.profile));
    }
    private static void ownerGone(UUID owner) {
        for (var entry : MARKS.entrySet()) {
            State state = entry.getValue().remove(owner);
            if (state != null && entry.getKey().level() instanceof ServerLevel level) {
                ParasiteEffectsNetwork.destructionUuid(entry.getKey(), owner, 0, false, state.profile);
            }
        }
        MARKS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) { clear(event.getEntity());ownerGone(event.getEntity().getUUID()); }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity target) { clear(target);ownerGone(target.getUUID()); }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) ownerGone(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { MARKS.clear(); }
}
