package cn.punishmenteye;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** Installed dedicated-grip ownership and damage are server-authoritative. No V dependency. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID)
public final class PassivePower {
    private record Position(net.minecraft.resources.ResourceLocation dimension,double x,double z){}
    private static final java.util.Map<java.util.UUID,Position> LAST=new java.util.HashMap<>();
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue MARK_SECONDS;
    static {
        var b=new ForgeConfigSpec.Builder();
        MARK_SECONDS=b.comment("Confirmed default: 10 seconds. Successful enemy attacks refresh an existing mark.")
            .defineInRange("godGazeDurationSeconds",PassiveRules.DEFAULT_MARK_SECONDS,1,3600);
        CONFIG=b.build();
    }
    public static void init(){PhantomNetwork.init();GazeNetwork.init();}
    /** v1.8.0 saved a potion effect. Discard only that retired registry entry on upgrade. */
    @SubscribeEvent public static void retiredPotion(MissingMappingsEvent e){
        for(var mapping:e.getMappings(ForgeRegistries.Keys.MOB_EFFECTS,EyeCrosshair.MOD_ID))
            if(mapping.getKey().getPath().equals("god_gaze"))mapping.ignore();
    }
    public static boolean isEye(ItemStack stack){return stack.getItem() instanceof com.tacz.guns.api.item.IGun gun&&SkillTimer.GUN.equals(gun.getGunId(stack));}
    public static boolean hasPower(ItemStack stack){
        return stack.getItem() instanceof com.tacz.guns.api.item.IGun gun
            &&GripRequirement.matches(gun.getGunId(stack),gun.getAttachmentId(stack,com.tacz.guns.api.item.attachment.AttachmentType.GRIP));
    }
    public static boolean carries(Player player){
        for(int i=0;i<player.getInventory().getContainerSize();i++)if(hasPower(player.getInventory().getItem(i)))return true;
        return false;
    }
    static boolean enemy(Player attacker,LivingEntity target){
        return target!=attacker&&!(target instanceof ArmorStand)&&!attacker.isAlliedTo(target)&&!target.isAlliedTo(attacker)
            &&(!(target instanceof OwnableEntity pet)||!attacker.getUUID().equals(pet.getOwnerUUID()))
            &&(!(target instanceof Player other)||attacker.canHarmPlayer(other));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!p.isAlive()||p.isSpectator()||!carries(p)){LAST.remove(p.getUUID());return;}
        if(p.tickCount%10==0){
            var existing=p.getEffect(MobEffects.MOVEMENT_SPEED);
            // Never replace or remove another source's longer/stronger Speed effect.
            if(existing==null||(existing.getAmplifier()==0&&existing.getDuration()<=20))
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,25,0,true,false,true));
        }
        if(p.tickCount%PassiveRules.PHANTOM_INTERVAL==0){
            var current=new Position(p.level().dimension().location(),p.getX(),p.getZ());
            var old=LAST.put(p.getUUID(),current);
            if(old!=null&&old.dimension.equals(current.dimension)&&!p.isPassenger()&&!p.isInvisible()
                &&PassiveRules.moving(current.x-old.x,current.z-old.z))PhantomNetwork.emit(p);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){LAST.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){LAST.clear();}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void bonus(EntityHurtByGunEvent.Pre event){
        if(event.getLogicalSide()!=LogicalSide.SERVER||!SkillTimer.GUN.equals(event.getGunId())
            ||!(event.getAttacker() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()||!carries(p)
            ||!(event.getHurtEntity() instanceof LivingEntity target)||!enemy(p,target))return;
        if(GazeMarks.marked(target))event.setBaseAmount(PassiveRules.damage(event.getBaseAmount(),true));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void mark(LivingDamageEvent event){
        // LivingDamageEvent runs after armor/absorption. TaCZ's Post also fires on rejected hits,
        // so it cannot establish actual damage. The Pre multiplier is decided before both AP parts.
        if(event.isCanceled()||event.getAmount()<=0||event.getEntity().level().isClientSide())return;
        var source=event.getSource();
        if(!(source.getEntity() instanceof LivingEntity attacker)||attacker==event.getEntity()||attacker.isAlliedTo(event.getEntity())||event.getEntity().isAlliedTo(attacker))return;
        boolean create=attacker instanceof ServerPlayer p&&p.isAlive()&&!p.isSpectator()&&carries(p)&&enemy(p,event.getEntity());
        if(create||GazeMarks.marked(event.getEntity()))GazeMarks.refresh(event.getEntity(),MARK_SECONDS.get()*20);
    }
}
