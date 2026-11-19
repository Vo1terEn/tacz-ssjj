package cn.ssjjhyper;

import cn.ssjjparasite2.client.ParasiteDestructionDamage;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Outgoing weapon damage, captured at launch instead of borrowed from the impact-time hand. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperOverloadDamage {
    private static final String FACTOR="SSJJHyperOverloadFactor",TOKEN="SSJJHyperOverloadToken",OWNER="SSJJHyperOverloadOwner";
    private static final Map<EntityHurtByGunEvent.Pre,Boolean> GUN_EVENTS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<net.minecraft.world.damagesource.DamageSource,Boolean> GUN_SOURCES=Collections.synchronizedMap(new WeakHashMap<>());
    private HyperOverloadDamage(){}
    private static boolean valid(double factor){return Double.isFinite(factor)&&factor>=1&&factor<=1.300001;}
    private static float scale(float amount,double factor){return (float)Math.min(Float.MAX_VALUE,(double)amount*factor);}

    /** A neutral launch is also recorded, preventing an old shot from acquiring a later buff. */
    public static void launched(Entity entity){
        if(!(entity instanceof Projectile projectile)||!(entity.level() instanceof ServerLevel))return;
        var data=entity.getPersistentData();if(data.contains(FACTOR))return;
        double factor=1;UUID token=null;ServerPlayer owner=projectile.getOwner() instanceof ServerPlayer p?p:null;
        if(owner!=null){
            var weapon=owner.getMainHandItem();boolean matches=HyperCharge.tier(weapon)<0&&HyperOverload.weapon(weapon);
            if(entity instanceof EntityKineticBullet bullet){var gun=IGun.getIGunOrNull(weapon);matches=gun!=null&&Objects.equals(bullet.getGunId(),gun.getGunId(weapon));}
            if(matches){factor=HyperOverload.multiplier(owner,weapon);token=HyperOverload.token(owner);}
            data.putUUID(OWNER,owner.getUUID());
        }
        data.putDouble(FACTOR,valid(factor)&&token!=null?factor:1);
        if(token!=null)data.putUUID(TOKEN,token);
    }
    /** Hyper rays replace the native projectile; preserve exactly its launch provenance. */
    public static void copyShot(Entity original,Entity copy){
        var from=original.getPersistentData();var to=copy.getPersistentData();
        to.putDouble(FACTOR,from.contains(FACTOR)&&valid(from.getDouble(FACTOR))?from.getDouble(FACTOR):1);
        if(from.hasUUID(TOKEN))to.putUUID(TOKEN,from.getUUID(TOKEN));
        if(from.hasUUID(OWNER))to.putUUID(OWNER,from.getUUID(OWNER));
    }
    public static double shotFactor(Entity entity){
        if(!(entity instanceof Projectile projectile)||!(entity.level() instanceof ServerLevel)||!(projectile.getOwner() instanceof ServerPlayer owner))return 1;
        var data=entity.getPersistentData();double factor=data.getDouble(FACTOR);
        if(!valid(factor)||factor<=1||!data.hasUUID(OWNER)||!owner.getUUID().equals(data.getUUID(OWNER))||!data.hasUUID(TOKEN))return 1;
        return HyperOverload.validToken(owner,data.getUUID(TOKEN))?factor:1;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void launch(EntityJoinLevelEvent event){if(!event.isCanceled()&&!event.getLevel().isClientSide())launched(event.getEntity());}

    /** TaCZ performs headshot and armor/piercing splitting after this common base-amount event. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void gun(EntityHurtByGunEvent.Pre event){
        if(event.isCanceled()||event.getLogicalSide()!=LogicalSide.SERVER||!(event.getAttacker() instanceof ServerPlayer)
            ||!(event.getBullet() instanceof EntityKineticBullet bullet)||!Objects.equals(event.getGunId(),bullet.getGunId())
            ||!(event.getBaseAmount()>0)||!Float.isFinite(event.getBaseAmount())||GUN_EVENTS.putIfAbsent(event,true)!=null)return;
        // Unspawned native contexts (for example crown hits) never dispatch EntityJoinLevelEvent.
        if(!bullet.getPersistentData().contains(FACTOR))launched(bullet);
        for(var part:com.tacz.guns.api.event.common.GunDamageSourcePart.values()){var source=event.getDamageSource(part);if(source!=null)GUN_SOURCES.put(source,true);}
        double factor=shotFactor(bullet);if(factor>1)event.setBaseAmount(scale(event.getBaseAmount(),factor));
    }
    /** Explicit independent skill damage; settled records (core or destruction) must never call this. */
    public static float skillDamage(LivingEntity owner,ItemStack weapon,float amount){
        if(!(owner instanceof ServerPlayer player)||!(amount>0)||!Float.isFinite(amount))return amount;
        double factor=HyperOverload.multiplier(player,weapon);return factor>1?scale(amount,factor):amount;
    }
    /** Independent custom abilities identify their carried weapon family explicitly. */
    public static float skillDamage(LivingEntity owner,String namespace,float amount){
        if(!(owner instanceof ServerPlayer player)||namespace==null||!(amount>0)||!Float.isFinite(amount))return amount;
        double factor=familyFactor(player,namespace);return factor>1?scale(amount,factor):amount;
    }
    /** A field's constant seeded mark is captured once here; later mark replay stays neutral. */
    public static float fieldMarkDamage(LivingEntity owner,float amount){return skillDamage(owner,"ssjj_dimension",amount);}
    private static double familyFactor(ServerPlayer owner,String namespace){
        double factor=1;
        for(int i=0;i<owner.getInventory().getContainerSize();i++){
            var weapon=owner.getInventory().getItem(i);var gun=IGun.getIGunOrNull(weapon);if(gun==null)continue;var id=gun.getGunId(weapon);
            if(id!=null&&namespace.equals(id.getNamespace()))factor=Math.max(factor,HyperOverload.multiplier(owner,weapon));
        }
        return factor;
    }
    private static String independentFamily(net.minecraft.world.damagesource.DamageSource source){
        var key=source.typeHolder().unwrapKey();if(key.isEmpty())return "";
        return switch(key.get().location().toString()){
            case "eyeofpunishment:nofa_lightning","eyeofpunishment:nofa_cloud"->"eyeofpunishment";
            case "judgfire:emperor_crown"->"judgfire";
            default->"";
        };
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void damage(LivingHurtEvent event){
        float amount=event.getAmount();var source=event.getSource();
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel)||!(amount>0)||!Float.isFinite(amount)||source instanceof ParasiteDestructionDamage||GUN_SOURCES.containsKey(source))return;
        var direct=source.getDirectEntity();double factor=1;
        if(direct instanceof Projectile){
            // Native bullet parts were already scaled in Pre. Its separate explosion was not.
            if(direct instanceof EntityKineticBullet&&!source.is(DamageTypeTags.IS_EXPLOSION))return;
            factor=shotFactor(direct);
        }else if(direct instanceof ServerPlayer player&&source.getEntity()==player){
            if(source.is(DamageTypes.PLAYER_ATTACK))factor=HyperOverload.multiplier(player,player.getMainHandItem());
            else if(!GUN_SOURCES.containsKey(source)){var family=independentFamily(source);if(!family.isEmpty())factor=familyFactor(player,family);}
        }
        if(factor>1)event.setAmount(scale(amount,factor));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){GUN_EVENTS.clear();GUN_SOURCES.clear();}
}
