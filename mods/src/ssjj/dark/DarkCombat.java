package ssjj.dark;

import com.tacz.guns.api.item.IGun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ssjj.dark.api.DarkBladeAttackEvent;

/** Server-owned strikes for the three ordinary TaCZ pistol stacks. */
public final class DarkCombat {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final ResourceKey<Registry<DamageType>> DAMAGE_TYPES = ResourceKey.m_135788_(
        new ResourceLocation("minecraft", "damage_type"));
    private static final ResourceKey<DamageType> BLADE_DAMAGE = ResourceKey.m_135785_(
        DAMAGE_TYPES, new ResourceLocation("ssjj_dark", "blade"));
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("ssjj_dark", "combat"), () -> "1", "1"::equals, "1"::equals);
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.messageBuilder(Attack.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buffer) -> buffer.writeBoolean(packet.heavy()))
            .decoder(buffer -> new Attack(buffer.readBoolean()))
            .consumerMainThread((packet, context) -> {
                ServerPlayer player = context.get().getSender();
                if (player != null) accept(player, packet.heavy());
                context.get().setPacketHandled(true);
            }).add();
        MinecraftForge.EVENT_BUS.register(DarkCombat.class);
        registered = true;
    }

    /** Called by the client input handler; no entity IDs or client damage are sent. */
    public static void request(boolean heavy) { CHANNEL.sendToServer(new Attack(heavy)); }

    public record Attack(boolean heavy) {}

    /** Unknown guns are never treated as dark crystal; only known old skin IDs normalize. */
    public static String weaponId(ItemStack stack) {
        if (stack == null || stack.m_41619_()) return "";
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return "";
        ResourceLocation id = gun.getGunId(stack);
        return id == null ? "" : DarkMeleeRules.canonical(id.toString());
    }

    public static int cooldown(Player player) {
        if (!(player instanceof ServerPlayer server)) return 0;
        State state = STATES.get(player.m_20148_());
        if (state == null || state.owner != player || state.level != player.m_9236_()) return 0;
        return state.timeline.remaining(now(server));
    }

    public static boolean busy(Player player) {
        if (!(player instanceof ServerPlayer)) return DarkSkills.charging(player);
        State state = STATES.get(player.m_20148_());
        return DarkSkills.charging(player) || cooldown(player) > 0
            || state != null && state.owner == player && state.level == player.m_9236_()
                && state.timeline.hasPending();
    }

    private static long now(ServerPlayer player) {
        return player.m_20194_().m_129783_().m_46467_();
    }

    private static boolean usable(ServerPlayer player) {
        return player.m_6084_() && !player.m_5833_() && !DarkSkills.charging(player)
            && !weaponId(player.m_21205_()).isEmpty();
    }

    private static void accept(ServerPlayer player, boolean heavy) {
        if (!usable(player)) return;
        long tick = now(player);
        State state = STATES.get(player.m_20148_());
        if (state == null || state.owner != player || state.level != player.m_9236_()) {
            state = new State(player);
            STATES.put(player.m_20148_(), state);
        }
        // The first accepted request owns the entire strike and cooldown window.
        DarkMeleeRules.Scheduled scheduled = state.timeline.begin(heavy, tick);
        if (scheduled == null) return;
        state.pending = new Pending(player.m_21205_(), player.m_150109_().f_35977_,
            weaponId(player.m_21205_()), scheduled.strike(), heavy, scheduled.combo(), scheduled.hitAt());
        player.m_21011_(InteractionHand.MAIN_HAND, true);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
            || !(event.player instanceof ServerPlayer player)) return;
        State state = STATES.get(player.m_20148_());
        if (state == null) return;
        if (state.owner != player || state.level != player.m_9236_() || !player.m_6084_()) {
            STATES.remove(player.m_20148_());
            return;
        }
        Pending pending = state.pending;
        if (pending == null) return;
        if (!sameWeapon(player, pending) || !usable(player)) {
            state.pending = null;
            state.timeline.cancelPending();
            return; // Switching cancels the hit, never its already incurred cooldown.
        }
        if (state.timeline.poll(now(player)) != null) {
            state.pending = null;
            strike(player, pending);
        }
    }

    private static boolean sameWeapon(ServerPlayer player, Pending pending) {
        return player.m_21205_() == pending.stack()
            && player.m_150109_().f_35977_ == pending.slot()
            && pending.id().equals(weaponId(player.m_21205_()));
    }

    private static Vec3 contact(Vec3 eye, AABB bounds) {
        return new Vec3(DarkMeleeRules.clamp(eye.f_82479_, bounds.f_82288_, bounds.f_82291_),
            DarkMeleeRules.clamp(eye.f_82480_, bounds.f_82289_, bounds.f_82292_),
            DarkMeleeRules.clamp(eye.f_82481_, bounds.f_82290_, bounds.f_82293_));
    }

    private static void strike(ServerPlayer player, Pending pending) {
        ServerLevel level = (ServerLevel) player.m_9236_();
        DarkMeleeRules.Strike rule = pending.strike();
        Vec3 eye = player.m_20299_(1), look = player.m_20154_();
        List<Target> candidates = new ArrayList<>();
        for (LivingEntity target : level.m_6443_(LivingEntity.class,
            player.m_20191_().m_82400_(rule.range()), t -> DarkSkills.validTarget(player, t))) {
            Vec3 point = contact(eye, target.m_20191_());
            double dx = point.f_82479_ - eye.f_82479_;
            double dy = point.f_82480_ - eye.f_82480_;
            double dz = point.f_82481_ - eye.f_82481_;
            if (!DarkMeleeRules.insideCone(dx, dy, dz, look.f_82479_, look.f_82480_, look.f_82481_,
                rule.range(), rule.aperture()) || !player.m_142582_(target)) continue;
            HitResult wall = level.m_45547_(new ClipContext(eye, point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.m_6662_() != HitResult.Type.MISS) continue;
            candidates.add(new Target(target, dx * dx + dy * dy + dz * dz));
        }
        candidates.sort(Comparator.comparingDouble(Target::distance2));
        float damage = DarkMeleeRules.damage(pending.id(), rule)
            * DarkSkills.damageMultiplier(player, pending.stack());
        if (!Float.isFinite(damage) || damage <= 0) return;
        // Only this damage type bypasses the vanilla hurt cooldown. Armor, shield
        // handling and Forge hurt events remain in LivingEntity.hurt unchanged.
        DamageSource source = new DamageSource(level.m_9598_().m_175515_(DAMAGE_TYPES)
            .m_246971_(BLADE_DAMAGE), player);
        for (int i = 0; i < Math.min(DarkMeleeRules.MAX_TARGETS, candidates.size()); i++) {
            LivingEntity target = candidates.get(i).entity();
            if (!DarkSkills.validTarget(player, target)) continue;
            if (target.m_6469_(source, damage)) {
                target.m_147240_(rule.knockback(), player.m_20185_() - target.m_20185_(),
                    player.m_20189_() - target.m_20189_());
            }
        }
        MinecraftForge.EVENT_BUS.post(new DarkBladeAttackEvent(player, pending.stack(),
            pending.heavy() ? DarkBladeAttackEvent.Action.HEAVY : DarkBladeAttackEvent.Action.LIGHT,
            pending.lightIndex(), DarkMeleeRules.grade(pending.id()), eye, look, now(player)));
    }

    /** Reject vanilla attack packets for these guns so they cannot add a second hit. */
    @SubscribeEvent
    public static void vanillaAttack(AttackEntityEvent event) {
        if (!weaponId(event.getEntity().m_21205_()).isEmpty()) event.setCanceled(true);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { clear(event.getOriginal()); clear(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) clear(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { STATES.clear(); }
    private static void clear(Player player) {
        if (player instanceof ServerPlayer) STATES.remove(player.m_20148_());
    }

    private record Pending(ItemStack stack, int slot, String id, DarkMeleeRules.Strike strike,
                           boolean heavy, int lightIndex, long hitAt) {}
    private record Target(LivingEntity entity, double distance2) {}
    private static final class State {
        final ServerPlayer owner;
        final ServerLevel level;
        final DarkMeleeRules.Timeline timeline = new DarkMeleeRules.Timeline();
        Pending pending;
        State(ServerPlayer player) { owner = player; level = (ServerLevel) player.m_9236_(); }
    }
    private DarkCombat() {}
}
