package cn.punishmenteye;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only this server ledger decides the 10s mark and damage bonus. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID)
public final class GazeMarks {
    private static final Map<ServerLevel,GazeLedger> LEVELS=new WeakHashMap<>();
    public static boolean marked(LivingEntity target){
        if(!(target.level() instanceof ServerLevel level))return false;
        var ledger=LEVELS.get(level);return ledger!=null&&ledger.active(target.getUUID(),level.getGameTime());
    }
    public static void refresh(LivingEntity target,int duration){
        if(!(target.level() instanceof ServerLevel level)||!target.isAlive())return;
        var ledger=LEVELS.computeIfAbsent(level,k->new GazeLedger());
        if(ledger.remaining(target.getUUID(),level.getGameTime())==duration)return;
        ledger.refresh(target.getUUID(),level.getGameTime(),duration);
        GazeNetwork.broadcast(target,duration);
    }
    private static void remove(LivingEntity target){
        if(!(target.level() instanceof ServerLevel level))return;
        var ledger=LEVELS.get(level);
        if(ledger!=null){ledger.remove(target.getUUID());GazeNetwork.broadcast(target,0);}
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){
        if(e.getEntity() instanceof ServerPlayer player&&e.getTarget() instanceof LivingEntity target&&target.level() instanceof ServerLevel level){
            var ledger=LEVELS.get(level);int remaining=ledger==null?0:ledger.remaining(target.getUUID(),level.getGameTime());
            if(remaining>0)GazeNetwork.send(player,target,remaining);
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent e){remove(e.getEntity());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){remove(e.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){
        for(var entry:LEVELS.entrySet())entry.getValue().remove(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level))return;
        var ledger=LEVELS.get(level);if(ledger==null)return;
        for(var uuid:ledger.targets()){
            var entity=level.getEntity(uuid);
            if(!ledger.active(uuid,level.getGameTime())||entity==null||!entity.isAlive()||entity.level()!=level){
                ledger.remove(uuid);if(entity instanceof LivingEntity target)GazeNetwork.broadcast(target,0);
            }
        }
        if(ledger.targets().isEmpty())LEVELS.remove(level);
    }
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){LEVELS.clear();}
}
