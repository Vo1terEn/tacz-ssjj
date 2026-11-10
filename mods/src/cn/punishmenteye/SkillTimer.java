package cn.punishmenteye;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.function.Supplier;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server deadlines survive weapon switches. Client receives bar state, never actionbar text. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class SkillTimer {
    static final String ACTIVE="eyeofpunishment_active_until", READY="eyeofpunishment_ready_at", PROFILE_DURATION="eyeofpunishment_skill_duration", PROFILE_COOLDOWN="eyeofpunishment_skill_cooldown";
    static final ResourceLocation GUN=GripRequirement.WEAPON;
    static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("eyeofpunishment","timer"),()->"4","4"::equals,"4"::equals);
    private static final Set<UUID> SYNC_ACTIVE=new HashSet<>();
    public record State(int active,int cooldown,boolean permanent,int duration,int cooldownTotal) {
        public State(int active,int cooldown){this(active,cooldown,false);}
        public State(int active,int cooldown,boolean permanent){this(active,cooldown,permanent,TimerRules.BASE_DURATION,TimerRules.BASE_COOLDOWN);}
        public boolean valid(){return TimerRules.profile(duration,cooldownTotal)&&active>=0&&active<=duration&&cooldown>=0&&cooldown<=cooldownTotal&&(!permanent||(active==0&&cooldown==0));}
        void write(FriendlyByteBuf b){b.writeVarInt(active);b.writeVarInt(cooldown);b.writeBoolean(permanent);b.writeVarInt(duration);b.writeVarInt(cooldownTotal);}
        static State read(FriendlyByteBuf b){return new State(b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt());}
    }
    public static void init(){
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Request()).consumerMainThread(SkillTimer::activate).add();
        CHANNEL.messageBuilder(State.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(State::write).decoder(State::read).consumerMainThread((m,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->TimerHud.accept(m));c.get().setPacketHandled(true);}).add();
    }
    public static final class Request {}
    public static void request(){CHANNEL.sendToServer(new Request());}
    static boolean holds(ServerPlayer p){return SkillEligibility.eligible(p.getMainHandItem());}
    static int duration(ServerPlayer p){var n=p.getPersistentData();return TimerRules.profile(n.getInt(PROFILE_DURATION),n.getInt(PROFILE_COOLDOWN))?n.getInt(PROFILE_DURATION):SkillEligibility.duration(p.getMainHandItem());}
    static int cooldown(ServerPlayer p){var n=p.getPersistentData();return TimerRules.profile(n.getInt(PROFILE_DURATION),n.getInt(PROFILE_COOLDOWN))?n.getInt(PROFILE_COOLDOWN):SkillEligibility.cooldown(p.getMainHandItem());}
    public static boolean active(ServerPlayer p){return UltimateRules.active(p.isAlive(),p.isSpectator(),holds(p),UltimateRequirement.installed(p.getMainHandItem()),p.server.overworld().getGameTime(),p.getPersistentData().getLong(ACTIVE));}
    static State snapshot(ServerPlayer p){
        long now=p.server.overworld().getGameTime();var n=p.getPersistentData();
        var state=UltimateRules.status(active(p),UltimateRequirement.installed(p.getMainHandItem()),now,n.getLong(ACTIVE),n.getLong(READY),duration(p),cooldown(p));
        return new State(state.active(),state.cooldown(),state.permanent(),duration(p),cooldown(p));
    }
    static void sync(ServerPlayer p){
        var state=snapshot(p);if(state.permanent()||state.active()>0)SYNC_ACTIVE.add(p.getUUID());else SYNC_ACTIVE.remove(p.getUUID());
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),state);NofaNetwork.active(p);
    }
    static void activate(Request request,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();var p=ctx.getSender();
        if(p!=null){
            long now=p.server.overworld().getGameTime();var nbt=p.getPersistentData();
            if(!UltimateRequirement.installed(p.getMainHandItem())&&now>=nbt.getLong(ACTIVE)&&KingHeartRules.canActivate(p.isAlive(),p.isSpectator(),holds(p),now,nbt.getLong(READY))){int duration=SkillEligibility.duration(p.getMainHandItem()),cooldown=SkillEligibility.cooldown(p.getMainHandItem());nbt.putInt(PROFILE_DURATION,duration);nbt.putInt(PROFILE_COOLDOWN,cooldown);nbt.putLong(ACTIVE,now+duration);nbt.putLong(READY,TimerRules.readyAt(now,duration,cooldown));}
            sync(p);
        }
        ctx.setPacketHandled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%10!=0)return;
        long now=p.server.overworld().getGameTime();var nbt=p.getPersistentData();boolean tracked=nbt.contains(ACTIVE)||nbt.contains(READY);
        if(nbt.getLong(ACTIVE)<=now)nbt.remove(ACTIVE);
        if(nbt.getLong(READY)<=now)nbt.remove(READY);
        if(!nbt.contains(ACTIVE)&&!nbt.contains(READY)){nbt.remove(PROFILE_DURATION);nbt.remove(PROFILE_COOLDOWN);}
        if(tracked||holds(p)||SYNC_ACTIVE.contains(p.getUUID()))sync(p);
    }
    static void endEarly(ServerPlayer p){var n=p.getPersistentData();long now=p.server.overworld().getGameTime();n.putLong(READY,TimerRules.endEarly(now,n.getLong(ACTIVE),n.getLong(READY),cooldown(p)));n.remove(ACTIVE);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p){endEarly(p);sync(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){endEarly(p);SYNC_ACTIVE.remove(p.getUUID());}}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        if(e.isWasDeath()&&e.getOriginal() instanceof ServerPlayer original)endEarly(original);
        var old=e.getOriginal().getPersistentData();var next=e.getEntity().getPersistentData();
        next.putLong(READY,old.getLong(READY));next.putInt(PROFILE_DURATION,old.getInt(PROFILE_DURATION));next.putInt(PROFILE_COOLDOWN,old.getInt(PROFILE_COOLDOWN));
        if(!e.isWasDeath()&&old.contains(ACTIVE))next.putLong(ACTIVE,old.getLong(ACTIVE));else next.remove(ACTIVE);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){
        var n=p.getPersistentData();long now=p.server.overworld().getGameTime();
        if(n.contains(ACTIVE)||n.contains(READY))n.putLong(READY,TimerRules.normalizeReady(now,n.getLong(ACTIVE),n.getLong(READY),cooldown(p)));
        sync(p);
    }}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer viewer&&e.getTarget() instanceof ServerPlayer owner)NofaNetwork.active(viewer,owner);}
}
