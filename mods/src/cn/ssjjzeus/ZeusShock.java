package cn.ssjjzeus;

import cn.ssjjparasite2.client.ParasiteDummyDamage;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Five seconds of server-owned vulnerability, with no potion or attribute effect. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ZeusShock {
    public static final ResourceLocation GRIP=new ResourceLocation("ssjj_zeus","thunder_shock_grip");
    public static final int LIFETIME=100, RECOVERY=160;
    private static final Set<ResourceLocation> GUNS=Set.of(new ResourceLocation("ssjj_zeus","hand_of_zeuss"),
        new ResourceLocation("ssjj_zeus","hand_of_zeuss1"),ZeusEnergy.GUN);
    private record Mark(ServerLevel level,long expires) {}
    private static final Map<Entity,Mark> ACTIVE=new IdentityHashMap<>();
    private ZeusShock() {}
    public static int lifetime(){return ZeusConfig.integer("shock_ticks");}
    public static int recovery(){return (int)Math.round(ZeusAdventRules.recharge()*ZeusConfig.number("shock_recovery_fraction"));}

    public static boolean grip(ItemStack stack) {
        IGun gun=IGun.getIGunOrNull(stack);
        return ZeusConfig.enabled("grip_enabled")&&gun!=null&&ZeusEnergy.GUN.equals(gun.getGunId(stack))&&GRIP.equals(gun.getAttachmentId(stack,AttachmentType.GRIP));
    }
    public static int remaining(Entity part) {
        Entity target=ZeusThunder.core(part);Mark mark=ACTIVE.get(target);
        if(!ZeusConfig.enabled("shock_enabled")||mark==null||target.level()!=mark.level||target.isRemoved()||!target.isAlive())return 0;
        return (int)Math.max(0,Math.min(72000,mark.expires-mark.level.getGameTime()));
    }
    public static boolean active(Entity target){return remaining(target)>0;}
    public static float multiplier(Entity target,ResourceLocation gun,boolean enhanced) {
        return gun!=null&&GUNS.contains(gun)&&active(target)?(float)(1+ZeusConfig.number(enhanced&&ZeusEnergy.GUN.equals(gun)?"grip_shock_damage_bonus":"shock_damage_bonus")):1;
    }
    public static void apply(ServerLevel level,Entity part) {
        Entity target=ZeusThunder.core(part);
        if(!ZeusConfig.enabled("shock_enabled")||target.level()!=level||target.isRemoved()||!target.isAlive())return;
        ACTIVE.put(target,new Mark(level,level.getGameTime()+lifetime()));
        ZeusAdventNetwork.shock(level,target,lifetime(),false);
    }
    private static void clear(Entity target) {
        Mark mark=ACTIVE.remove(target);if(mark!=null)ZeusAdventNetwork.shock(mark.level,target,0,true);
    }
    /** Capture the old state before damage: a first application cannot refund its own charge. */
    static boolean chainHit(ServerLevel level,LivingEntity owner,Entity part,ZeusThunder.NativeBlast trace,
                            Vec3 point,Vec3 start,Vec3 end) {
        Entity target=ZeusThunder.core(part);boolean previous=active(target);
        var before=target instanceof LivingEntity living?ParasiteDummyDamage.capture(living):null;
        trace.hit(part,point,start,end);
        boolean accepted=target instanceof LivingEntity living?
            ParasiteDummyDamage.confirmedLoss(before,living)>0||!living.isAlive():target instanceof ITargetEntity;
        if(!accepted)return false;
        apply(level,target);
        if(previous&&ZeusConfig.enabled("shock_recovery_enabled")&&owner instanceof ServerPlayer player&&owner.level()==level)ZeusAdvent.recover(player,recovery());
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var entry:new ArrayList<>(ACTIVE.entrySet())) {
            Entity target=entry.getKey();int remaining=remaining(target);
            if(remaining==0){clear(target);continue;}
            if(entry.getValue().level.getGameTime()%20==0)ZeusAdventNetwork.shock(entry.getValue().level,target,remaining,false);
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer viewer) {
            int remaining=remaining(event.getTarget());if(remaining>0)ZeusAdventNetwork.shockTo(viewer,ZeusThunder.core(event.getTarget()),remaining);
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(!event.getLevel().isClientSide())clear(event.getEntity());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){ACTIVE.clear();}
}
