package cn.ssjjdark;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned original-material presentation snapshots; gameplay remains in DarkLiberation. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DarkSkillPresentation {
    private record State(ServerPlayer owner,ServerLevel level,DarkSkillPresentationNetwork.Cast cast){}
    private static final Map<UUID,State> ACTIVE=new HashMap<>();
    private DarkSkillPresentation(){}
    private static long now(ServerPlayer player){return player.getServer()==null?player.serverLevel().getGameTime():player.getServer().overworld().getGameTime();}
    public static DarkSkillPresentationNetwork.Cast current(ServerPlayer player){
        if(player==null)return null;State state=ACTIVE.get(player.getUUID());
        return state!=null&&state.owner==player&&state.level==player.serverLevel()&&now(player)>=state.cast.startTick()&&now(player)<state.cast.activeUntil()?state.cast:null;
    }
    public static void start(ServerPlayer player,boolean phantom){
        if(player==null||player.isRemoved())return;
        var held=player.getMainHandItem();var gun=IGun.getIGunOrNull(held);if(gun==null||DarkWrath.tier(held)!=2)return;
        long time=now(player);String skin=DarkSkins.skin(gun.getAttachmentId(held,AttachmentType.SCOPE));
        String profile="darkdiamond2"+(skin.isEmpty()?"":"_"+skin);
        var cast=new DarkSkillPresentationNetwork.Cast(player.level().dimension().location(),UUID.randomUUID(),player.getUUID(),player.getId(),player.position(),player.getYRot(),time,60,time+360,profile,phantom,false);
        if(!cast.valid())return;
        stop(player);
        ACTIVE.put(player.getUUID(),new State(player,player.serverLevel(),cast));
        DarkSkillPresentationNetwork.start(player,cast);
    }
    public static void stop(ServerPlayer player){
        if(player==null)return;State state=ACTIVE.remove(player.getUUID());if(state!=null)DarkSkillPresentationNetwork.stop(state.level,state.cast);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer watcher)||!(event.getTarget() instanceof ServerPlayer owner))return;
        var cast=current(owner);if(cast!=null&&watcher.level().dimension()==owner.level().dimension())DarkSkillPresentationNetwork.player(watcher,cast);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||ACTIVE.isEmpty())return;
        for(var state:new ArrayList<>(ACTIVE.values())){
            var owner=state.owner;long time=now(owner);
            if(!owner.isAlive()||owner.isRemoved()||owner.serverLevel()!=state.level||time<state.cast.startTick()||time>=state.cast.activeUntil())stop(owner);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){if(!event.isCanceled()&&event.getEntity() instanceof ServerPlayer player)stop(player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)stop(player);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)stop(player);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){if(event.getOriginal() instanceof ServerPlayer original)stop(original);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.clear();}
}


