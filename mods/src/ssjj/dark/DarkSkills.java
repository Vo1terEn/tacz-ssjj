package ssjj.dark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ssjj.dark.api.DarkBladeAttackEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** 服务端拥有龙印斩、怒意与力量解放状态；采用明示的MC-PVE适配参数。 */
@Mod.EventBusSubscriber(modid=DarkCrystalMod.ID)
public final class DarkSkills {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final UUID SPEED_ID = UUID.fromString("da4cc725-2768-4404-b937-1b586e747975");
    private static final String CD_TAG = "ssjj_dark_next_ready";
    private static final Map<UUID, Sent> LAST_SENT = new HashMap<>();

    static String id(ItemStack stack) {
        return DarkCombat.weaponId(stack);
    }

    static int grade(ItemStack stack) {
        String id = id(stack);
        return switch (id) {
            case "ssjj_dark:darkdiamond" -> 0;
            case "ssjj_dark:darkdiamond1" -> 1;
            case "ssjj_dark:darkdiamond2" -> 2;
            default -> -1;
        };
    }

    static boolean charging(Player player) {
        if (player.m_9236_().f_46443_) return DarkClientState.state.charge() > 0;
        State state = STATES.get(player.m_20148_());
        return state != null && state.owner == player && state.chargeUntil > now(player);
    }

    private static long now(Player player) {
        var server=player.m_20194_();
        return server == null ? player.m_9236_().m_46467_() : server.m_129783_().m_46467_();
    }

    static boolean validTarget(Player owner, Entity target) {
        if (target == owner || !target.m_6084_() || target.m_5833_() || target instanceof ArmorStand
            || owner.m_7307_(target) || owner.m_9236_() != target.m_9236_()) return false;
        if (target instanceof TamableAnimal pet && owner.m_20148_().equals(pet.m_21805_())) return false;
        if (target instanceof Player player) return DarkSettings.PVP.get() && !player.m_7500_() && owner.m_7099_(player);
        return true;
    }

    static float damageMultiplier(Player player, ItemStack stack) {
        if (!DarkSettings.SKILLS.get() || grade(stack) < 1) return 1;
        float missing = 1-player.m_21223_()/Math.max(1, player.m_21233_());
        return (float)(1+Math.max(0, missing)*DarkSettings.RAGE.get());
    }

    public static void activate(ServerPlayer player) {
        if (!DarkSettings.SKILLS.get() || grade(player.m_21205_()) != 2 || !player.m_6084_()
            || player.m_5833_()) return;
        if (DarkCombat.busy(player)) return;
        State old = STATES.get(player.m_20148_());
        if (old != null && old.activeUntil > now(player)) return;
        if (player.getPersistentData().m_128454_(CD_TAG) > now(player)) return;
        State state = new State(player);
        state.visualStart = now(player); state.visualCharge = DarkSettings.CHARGE.get(); state.visualYaw = player.m_146908_();
        state.chargeUntil = now(player)+state.visualCharge;
        state.activeUntil = state.chargeUntil+DarkSettings.DURATION.get();
        state.cooldownUntil = state.activeUntil+DarkSettings.COOLDOWN.get();
        player.getPersistentData().m_128356_(CD_TAG, state.cooldownUntil);
        // 以当前生命扣除比例，至少保留半颗心；不把耗血伪装成外部伤害。
        if (!player.m_7500_()) player.m_21153_(Math.max(1, player.m_21223_()*(1-DarkSettings.COST.get().floatValue())));
        STATES.put(player.m_20148_(), state);
        syncVisual(player, state, false);
        player.m_5496_(net.minecraft.sounds.SoundEvent.m_262824_(
            new net.minecraft.resources.ResourceLocation("ssjj_dark", "darkdiamond2_skill")), 1, 1);
        sync(player, state);
    }

    /** The original dragon strike is an adapter on the public, server-owned extension hook. */
    @SubscribeEvent public static void bladeResolved(DarkBladeAttackEvent event) {
        if (event.action() == DarkBladeAttackEvent.Action.LIGHT) launchWave(event.owner(), event.weapon());
    }

    static void launchWave(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer server) || !DarkSettings.SKILLS.get() || grade(stack) < 0) return;
        State state = STATES.get(player.m_20148_());
        boolean active = state != null && state.released && state.activeUntil > now(player);
        int count = grade(stack)+1;
        for (int i=0; i<count; i++) {
            Vec3 look = player.m_20154_();
            double angle = (i-(count-1)/2.0)*.10;
            Vec3 direction = new Vec3(look.f_82479_*Math.cos(angle)-look.f_82481_*Math.sin(angle), look.f_82480_,
                look.f_82479_*Math.sin(angle)+look.f_82481_*Math.cos(angle)).m_82541_();
            WAVES.add(new Wave(server, player.m_20299_(1), direction,
                (float)(DarkSettings.WAVE_DAMAGE.get()*(1+grade(stack)*.25)*(active?2:1))));
        }
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)) return;
        State state = STATES.get(player.m_20148_());
        boolean held = grade(player.m_21205_()) == 2;
        if (state != null && (state.owner != player || state.level != player.m_9236_() || !held || !player.m_6084_() || !DarkSettings.SKILLS.get())) {
            syncVisual(player, state, true);
            removeSpeed(player); STATES.remove(player.m_20148_()); state = null;
        }
        if (state != null) {
            long now = now(player);
            if (state.chargeUntil > now) {
                player.m_20256_(Vec3.f_82478_);
                var attribute = player.m_21051_(Attributes.f_22279_);
                if (attribute != null && attribute.m_22111_(SPEED_ID) == null)
                    attribute.m_22118_(new AttributeModifier(SPEED_ID, "黯水晶蓄力", -1, AttributeModifier.Operation.MULTIPLY_TOTAL));
                for (LivingEntity target : state.level.m_6443_(LivingEntity.class, player.m_20191_().m_82400_(2), t -> validTarget(player, t))) {
                    if (player.m_142582_(target)) target.m_147240_(.15, player.m_20185_()-target.m_20185_(), player.m_20189_()-target.m_20189_());
                }
            } else if (!state.released) {
                state.released = true; removeSpeed(player);
                state.shieldMax = (float)(player.m_21233_()*DarkSettings.SHIELD.get());
                state.shield = state.shieldMax;
                var attribute = player.m_21051_(Attributes.f_22279_);
                if (attribute != null) attribute.m_22118_(new AttributeModifier(SPEED_ID, "黯水晶力量解放", DarkSettings.SPEED.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));
                blast(player, state);
            }
            if (now >= state.activeUntil) {
                removeSpeed(player); state.shield = 0;
            }
            if (now >= state.cooldownUntil) { STATES.remove(player.m_20148_()); state = null; }
        }
        if (held || state != null) sync(player, state);
    }

    private static void removeSpeed(Player player) {
        var attribute = player.m_21051_(Attributes.f_22279_);
        if (attribute != null) attribute.m_22120_(SPEED_ID);
    }

    private static void blast(ServerPlayer player, State state) {
        double radius = DarkSettings.BLAST_RADIUS.get();
        for (LivingEntity target : state.level.m_6443_(LivingEntity.class, player.m_20191_().m_82400_(radius), t -> validTarget(player, t))) {
            if (player.m_20270_(target) <= radius && player.m_142582_(target)) {
                target.m_6469_(player.m_269291_().m_269075_(player), DarkSettings.BLAST_DAMAGE.get().floatValue());
                target.m_147240_(.6, player.m_20185_()-target.m_20185_(), player.m_20189_()-target.m_20189_());
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DarkSettings.SKILLS.get()) return;
        State state = STATES.get(player.m_20148_());
        if (state != null && state.owner == player && grade(player.m_21205_()) == 2) {
            if (state.chargeUntil > now(player)) { event.setCanceled(true); return; }
            if (state.activeUntil > now(player) && state.shield > 0) {
                float absorbed = Math.min(state.shield, event.getAmount());
                state.shield -= absorbed;
                event.setAmount(event.getAmount()-absorbed);
                if (event.getAmount() <= 0) { event.setCanceled(true); return; }
            }
        }
        if (grade(player.m_21205_()) == 2) {
            double missing = Math.max(0, 1-player.m_21223_()/Math.max(1, player.m_21233_()));
            event.setAmount((float)(event.getAmount()*(1-DarkSettings.REDUCTION.get()*missing)));
        }
    }

    @SubscribeEvent
    public static void heal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = STATES.get(player.m_20148_());
        if (state != null && state.owner == player && state.released && state.activeUntil > now(player)
            && grade(player.m_21205_()) == 2 && DarkSettings.SKILLS.get()) {
            state.shield = Math.min(state.shieldMax, state.shield+event.getAmount());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!DarkSettings.SKILLS.get()) { WAVES.clear(); return; }
        Iterator<Wave> iterator = WAVES.iterator();
        while (iterator.hasNext()) {
            Wave wave = iterator.next();
            if (!wave.owner.m_6084_() || wave.owner.m_9236_() != wave.level || ++wave.age > 20) { iterator.remove(); continue; }
            Vec3 end = wave.point.m_82549_(wave.direction.m_82490_(.6));
            var wall = wave.level.m_45547_(new ClipContext(wave.point, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wave.owner));
            Vec3 capped = wall.m_6662_() == HitResult.Type.MISS ? end : wall.m_82450_();
            AABB box = new AABB(wave.point, capped).m_82400_(.45);
            for (LivingEntity target : wave.level.m_6443_(LivingEntity.class, box, t -> validTarget(wave.owner, t))) {
                if (!wave.hit.contains(target.m_20148_()) && wave.owner.m_142582_(target)
                    && target.m_20191_().m_82400_(.3).m_82371_(wave.point, capped).isPresent()) {
                    wave.hit.add(target.m_20148_());
                    target.m_6469_(wave.owner.m_269291_().m_269075_(wave.owner), wave.damage);
                }
            }
            wave.point = capped; wave.distance += .6;
            if (wall.m_6662_() != HitResult.Type.MISS || wave.distance >= DarkSettings.WAVE_RANGE.get()) iterator.remove();
        }
    }

    private static void sync(ServerPlayer player, State state) {
        long now = now(player);
        int charge = state == null ? 0 : (int)Math.max(0, state.chargeUntil-now);
        int active = state == null || !state.released ? 0 : (int)Math.max(0, state.activeUntil-now);
        int cooldown = (int)Math.max(0, player.getPersistentData().m_128454_(CD_TAG)-now);
        float shield=state == null ? 0 : state.shield, max=state == null ? 0 : state.shieldMax;
        int phase=charge>0 ? 1 : active>0 ? 2 : cooldown>0 ? 3 : 0;
        Sent last=LAST_SENT.get(player.m_20148_());
        if (last != null && last.phase == phase && last.shield == shield && last.max == max
            && now-last.tick < (phase == 0 ? 20 : 5)) return;
        LAST_SENT.put(player.m_20148_(), new Sent(now,phase,shield,max));
        DarkNetwork.send(player, new DarkNetwork.State(charge, active, cooldown, DarkSettings.DURATION.get(),
            DarkSettings.COOLDOWN.get(), shield, max));
    }

    private static void syncVisual(ServerPlayer player, State state, boolean stopped) {
        DarkNetwork.visual(player, new DarkVisualState.Cast(player.m_19879_(), player.m_20148_(), state.visualStart, state.visualCharge, state.activeUntil, state.visualYaw, stopped));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            State state = STATES.get(target.m_20148_());
            if (state != null && now(target) < Math.max(state.activeUntil, state.chargeUntil + 24))
                DarkNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> watcher), new DarkVisualState.Cast(target.m_19879_(), target.m_20148_(), state.visualStart, state.visualCharge, state.activeUntil, state.visualYaw, false));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().m_20148_()); removeSpeed(event.getEntity());
        LAST_SENT.remove(event.getEntity().m_20148_());
        WAVES.removeIf(wave -> wave.owner == event.getEntity());
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().m_128356_(CD_TAG, event.getOriginal().getPersistentData().m_128454_(CD_TAG));
        STATES.remove(event.getEntity().m_20148_());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); WAVES.clear(); LAST_SENT.clear(); }

    private record Sent(long tick,int phase,float shield,float max) {}

    private static final class State {
        final ServerPlayer owner;
        final ServerLevel level;
        long chargeUntil, activeUntil, cooldownUntil, visualStart;
        int visualCharge;
        float visualYaw;
        boolean released;
        float shield, shieldMax;
        State(ServerPlayer player) { owner=player; level=(ServerLevel)player.m_9236_(); }
    }
    private static final class Wave {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 direction;
        final float damage;
        final Set<UUID> hit = new HashSet<>();
        Vec3 point;
        double distance;
        int age;
        Wave(ServerPlayer player, Vec3 point, Vec3 direction, float damage) {
            owner=player; level=(ServerLevel)player.m_9236_(); this.point=point; this.direction=direction; this.damage=damage;
        }
    }
}
