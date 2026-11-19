package cn.ssjjdark;

import cn.judgfire.BarrierHealth;
import cn.judgfire.BurstStun;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Absolute Liberation: one server session per owner, independent of slot swaps or extra copies. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DarkLiberation {
    public static final ResourceLocation CORE_ID=new ResourceLocation("ssjj_dark","crystal_core");
    public static final String DATA="ssjj_dark_absolute_liberation",CHARGING="ssjj_dark_liberation_charge_until";
    private static final UUID SPEED=UUID.fromString("76df1d61-2f35-46c6-b9e8-f617cba87e15");
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static final class Session {
        final ServerPlayer owner;final ServerLevel level;final long born;final boolean core,ultimate;
        final Vec3 origin;final float yaw,pitch;float shield,capacity,protectedHP;boolean exploded;
        Session(ServerPlayer owner,long now,boolean core){this(owner,now,core,false);}
        Session(ServerPlayer owner,long now,boolean core,boolean ultimate){
            this.owner=owner;level=owner.serverLevel();born=now;this.core=core;this.ultimate=ultimate;origin=owner.position();yaw=owner.getYRot();pitch=owner.getXRot();
            capacity=DarkLiberationRules.capacity(owner.getMaxHealth(),core);
        }
    }
    private DarkLiberation(){}
    private static long now(ServerPlayer player){return player.getServer()==null?0:player.getServer().overworld().getGameTime();}
    private static Session session(LivingEntity entity){
        if(!(entity instanceof ServerPlayer player))return null;
        Session state=ACTIVE.get(player.getUUID());
        return state!=null&&state.owner==player&&state.level==player.level()&&now(player)>=state.born
            &&now(player)<state.born+DarkLiberationRules.DURATION?state:null;
    }
    public static boolean active(ServerPlayer player){return player!=null&&session(player)!=null;}
    public static boolean ultimateActive(ServerPlayer player){Session state=player==null?null:session(player);return state!=null&&state.ultimate;}
    public static boolean healthLocked(LivingEntity entity){return entity instanceof ServerPlayer player&&ultimateActive(player)&&!DarkLiberationBypass.allowed(player);}
    /** Shared by the existing global action lock. The health hook must never read health here. */
    public static boolean charging(LivingEntity entity){
        if(entity==null)return false;
        if(entity.level().isClientSide)return entity.getPersistentData().getLong(CHARGING)>entity.level().getGameTime();
        Session state=session(entity);
        return state!=null&&!state.ultimate&&now(state.owner)<state.born+DarkLiberationRules.CHARGE;
    }
    public static boolean invulnerable(LivingEntity entity){return entity instanceof ServerPlayer player&&invulnerable(player);}
    public static boolean invulnerable(ServerPlayer player){
        Session state=player==null?null:session(player);
        return state!=null&&!DarkLiberationBypass.allowed(player)&&now(player)<state.born+DarkLiberationRules.invulnerability(state.core);
    }
    public static float protectedHealth(ServerPlayer player){Session state=player==null?null:session(player);return state==null?0:state.ultimate&&!DarkLiberationBypass.allowed(player)?1:state.protectedHP;}
    public static void allowBypassHealth(ServerPlayer player,float health){
        Session state=player==null?null:session(player);
        if(state!=null&&DarkLiberationBypass.allowed(player)&&Float.isFinite(health)&&health>=0&&health<state.protectedHP)state.protectedHP=health;
    }
    /** Called only at the outer explicit bypass scope boundary, never from the health boundary itself. */
    public static void reconcileBypassHealth(ServerPlayer player){
        Session state=player==null?null:session(player);if(state==null||!DarkLiberationBypass.allowed(player))return;
        try {float actual=player.getHealth();if(Float.isFinite(actual)&&actual>=0)state.protectedHP=actual;}
        catch(RuntimeException|LinkageError ignored){}
    }
    public static float dragonMultiplier(ServerPlayer player){return active(player)?(float)DarkCrystalConfig.number("liberation_dragon_multiplier"):1;}
    public static double wrathReductionMultiplier(ServerPlayer player){return active(player)?DarkCrystalConfig.number("liberation_wrath_reduction_multiplier"):1;}
    public static boolean core(ItemStack stack){
        if(DarkWrath.tier(stack)!=2)return false;
        IGun gun=IGun.getIGunOrNull(stack);
        return gun!=null&&CORE_ID.equals(gun.getAttachmentId(stack,AttachmentType.STOCK))
            &&gun.allowAttachmentType(stack,AttachmentType.STOCK)
            &&AllowAttachmentTagMatcher.match(gun.getGunId(stack),CORE_ID);
    }
    public static boolean eligible(ServerPlayer player,int slot){
        if(player==null||slot<0||slot>=9||!player.isAlive()||player.isRemoved()||player.isSpectator()||player.isSleeping()
            ||player.getInventory().selected!=slot||player.containerMenu!=player.inventoryMenu||DarkWrath.tier(player.getMainHandItem())!=2
            ||BurstStun.stunned(player)||active(player))return false;
        var operator=IGunOperator.fromLivingEntity(player);if(operator==null)return false;
        var reload=operator.getSynReloadState();
        return operator.getCacheProperty()!=null&&operator.getSynDrawCoolDown()<=0&&!operator.getSynIsBolting()
            &&operator.getSynMeleeCoolDown()<=0&&(reload==null||!reload.getStateType().isReloading());
    }
    public static void activate(ServerPlayer player,int slot){
        if(!eligible(player,slot))return;
        long time=now(player);CompoundTag data=player.getPersistentData().getCompound(DATA);
        if(data.contains("lastRequest")&&time>=data.getLong("lastRequest")&&time-data.getLong("lastRequest")<4)return;
        data.putLong("lastRequest",time);player.getPersistentData().put(DATA,data);
        if(DarkLiberationRules.remaining(time,data.getLong("cooldownUntil"),DarkLiberationRules.COOLDOWN)>0){sync(player,false);return;}
        float before=player.getHealth(),maximum=player.getMaxHealth();
        if(!Float.isFinite(before)||before<=0||!Float.isFinite(maximum)||maximum<=0)return;
        boolean core=core(player.getMainHandItem()),ultimate=DarkUltimate.installed(player.getMainHandItem());
        // This explicit restore scope bypasses energy resistance, shields and the skill's own invulnerability.
        BarrierHealth.beginRestore(player);
        try {player.setHealth(DarkLiberationRules.castHealth(before,ultimate));}
        finally {BarrierHealth.endRestore();}
        float after=player.getHealth();
        if(!Float.isFinite(after)||after<=0||(!ultimate&&after>before))return;
        Session state=new Session(player,time,core,ultimate);state.protectedHP=after;
        state.shield=DarkLiberationRules.add(0,Math.max(0,(double)before-after)*DarkLiberationRules.sacrificeShieldFactor(core),state.capacity);
        ACTIVE.put(player.getUUID(),state);
        if(!ultimate)player.getPersistentData().putLong(CHARGING,player.level().getGameTime()+DarkLiberationRules.CHARGE);
        else player.getPersistentData().remove(CHARGING);
        data.putLong("cooldownUntil",time+DarkLiberationRules.CYCLE);player.getPersistentData().put(DATA,data);
        if(!ultimate)player.stopUsingItem();speed(player,true);if(!ultimate)freeze(state);knockback(state);
        DarkWrath.refresh(player);sync(player,true);
        DarkSkillPresentation.start(player,ultimate);
    }
    /** Called once for each actual health change after existing energy/strength arithmetic. */
    public static float healthBoundary(LivingEntity entity,float old,float adjusted,boolean restoring){
        if(entity==null||entity.level().isClientSide)return adjusted;
        Session state=session(entity);if(state==null)return adjusted;
        if(state.ultimate&&!DarkLiberationBypass.allowed(state.owner)){
            state.protectedHP=1;
            if(adjusted>1)state.shield=DarkLiberationRules.add(state.shield,(double)adjusted-1,state.capacity);
            else if(!restoring&&!invulnerable(state.owner))state.shield=DarkLiberationRules.boundary(1,adjusted,state.shield,state.capacity,false).shield();
            return 1;
        }
        // Attacks also use restoration scopes: the explicit skill bypass is the only invulnerability escape.
        if(invulnerable(state.owner)){
            float baseline=state.protectedHP;
            if(!Float.isFinite(baseline)||baseline<0)baseline=Float.isFinite(old)&&old>0?old:0;
            if(adjusted>baseline)state.shield=DarkLiberationRules.add(state.shield,(double)adjusted-baseline,state.capacity);
            return baseline;
        }
        if(restoring){if(Float.isFinite(adjusted)&&adjusted>=0)state.protectedHP=adjusted;return adjusted;}
        var result=DarkLiberationRules.boundary(old,adjusted,state.shield,state.capacity,false);
        state.shield=result.shield();
        if(Float.isFinite(result.health()))state.protectedHP=Math.max(0,result.health());
        return result.health();
    }
    private static void speed(ServerPlayer player,boolean active){
        var attribute=player.getAttribute(Attributes.MOVEMENT_SPEED);if(attribute==null)return;
        var old=attribute.getModifier(SPEED);
        if(old!=null&&(!active||old.getAmount()!=DarkLiberationRules.speedBonus()||old.getOperation()!=AttributeModifier.Operation.MULTIPLY_TOTAL)){attribute.removeModifier(SPEED);old=null;}
        if(active&&old==null)attribute.addTransientModifier(new AttributeModifier(SPEED,"Absolute Liberation",DarkLiberationRules.speedBonus(),AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    private static void freeze(Session state){
        var player=state.owner;player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        if(player.position().distanceToSqr(state.origin)>1e-8||player.getYRot()!=state.yaw||player.getXRot()!=state.pitch)
            player.connection.teleport(state.origin.x,state.origin.y,state.origin.z,state.yaw,state.pitch);
    }
    private static boolean enemy(ServerPlayer owner,LivingEntity target){
        if(target==owner||!target.isAlive()||target.isRemoved()||target.isSpectator()||!target.isPickable()
            ||target.isPassengerOfSameVehicle(owner)||owner.isAlliedTo(target)||target.isAlliedTo(owner))return false;
        if(target instanceof OwnableEntity pet&&owner.getUUID().equals(pet.getOwnerUUID()))return false;
        return !(target instanceof Player other)||( !other.isCreative()&&owner.getServer()!=null&&owner.getServer().isPvpAllowed()&&owner.canHarmPlayer(other));
    }
    private static List<LivingEntity> targets(Session state,Vec3 origin){
        Vec3 center=origin.add(0,1,0);double radius=DarkLiberationRules.radius();
        var result=new ArrayList<LivingEntity>();
        for(var target:state.level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),t->enemy(state.owner,t))){
            Vec3 point=target.getBoundingBox().getCenter();
            if(point.distanceToSqr(center)>radius*radius)continue;
            if(state.level.clip(new ClipContext(center,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,state.owner)).getType()!=HitResult.Type.MISS)continue;
            result.add(target);
        }
        result.sort(Comparator.comparingInt(LivingEntity::getId));return result;
    }
    private static void knockback(Session state){
        for(var target:targets(state,state.origin)){
            double x=target.getX()-state.origin.x,z=target.getZ()-state.origin.z;
            if(x*x+z*z<1e-8){x=1;z=0;}
            target.knockback(1.5,-x,-z);target.hurtMarked=true;
        }
    }
    private static void explode(Session state){
        state.exploded=true;
        Vec3 origin=state.ultimate?state.origin:state.owner.position();
        for(var target:targets(state,origin))DarkCombat.damage(state.owner,target,DarkLiberationRules.explosionDamage());
    }
    private static void sync(ServerPlayer player,boolean cast){
        int slot=player.getInventory().selected;if(slot<0||slot>=9)return;
        Session state=session(player);long time=now(player);
        int charge=state==null||state.ultimate?0:DarkLiberationRules.remaining(time,state.born+DarkLiberationRules.CHARGE,DarkLiberationRules.CHARGE);
        int active=state==null?0:DarkLiberationRules.remaining(time,state.born+DarkLiberationRules.DURATION,DarkLiberationRules.DURATION);
        int invul=state==null?0:DarkLiberationRules.remaining(time,state.born+DarkLiberationRules.invulnerability(state.core),200);
        int cooldown=active>0?0:DarkLiberationRules.remaining(time,player.getPersistentData().getCompound(DATA).getLong("cooldownUntil"),DarkLiberationRules.COOLDOWN);
        DarkLiberationNetwork.status(player,new DarkLiberationNetwork.Status(player.level().dimension().location(),slot,charge,active,cooldown,invul,
            state!=null?state.core:core(player.getMainHandItem()),state==null?0:state.shield,state==null?0:state.capacity,cast));
    }
    private static void clear(ServerPlayer player,boolean send){
        DarkSkillPresentation.stop(player);
        ACTIVE.remove(player.getUUID());player.getPersistentData().remove(CHARGING);speed(player,false);
        if(send)sync(player,false);
    }
    private static void finish(ServerPlayer player,Session state){
        clear(player,false);
        if(state.ultimate){
            float restored=DarkLiberationRules.endingHealth(player.getMaxHealth());
            if(restored>0){BarrierHealth.beginRestore(player);try{cn.fusionarsenal.DarkCrystalRecovery.restore(player,restored);}finally{BarrierHealth.endRestore();}}
        }
        sync(player,false);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        Session state=ACTIVE.get(player.getUUID());
        if(state!=null){
            long time=now(player);
            if(state.owner!=player||!player.isAlive()||player.isRemoved()||player.isSpectator()||state.level!=player.level()
                ||time<state.born){clear(player,true);return;}
            if(time>=state.born+DarkLiberationRules.DURATION){finish(player,state);return;}
            if(state.ultimate&&!DarkLiberationBypass.allowed(player))player.setHealth(1);
            state.capacity=DarkLiberationRules.capacity(player.getMaxHealth(),state.core);
            state.shield=DarkLiberationRules.add(state.shield,0,state.capacity);speed(player,true);
            if(charging(player)){
                freeze(state);
            }else {
                player.getPersistentData().remove(CHARGING);
                if(time>=state.born+DarkLiberationRules.CHARGE&&!state.exploded)explode(state);
            }
        }else if(player.getPersistentData().contains(CHARGING)){player.getPersistentData().remove(CHARGING);speed(player,false);}
        if(player.tickCount%5==0&&(state!=null||DarkWrath.tier(player.getMainHandItem())==2))sync(player,false);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event){if(event.getEntity() instanceof ServerPlayer player&&invulnerable(player))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingHurtEvent event){if(event.getEntity() instanceof ServerPlayer player&&invulnerable(player))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void damage(LivingDamageEvent event){if(event.getEntity() instanceof ServerPlayer player&&invulnerable(player))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void death(LivingDeathEvent event){
        if(event.getEntity() instanceof ServerPlayer player){if(invulnerable(player)||healthLocked(player)){event.setCanceled(true);return;}if(!event.isCanceled())clear(player,false);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)clear(player,false);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)clear(player,true);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player){clear(player,true);}}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(event.getOriginal() instanceof ServerPlayer original)clear(original,false);
        if(event.getOriginal().getPersistentData().contains(DATA))event.getEntity().getPersistentData().put(DATA,event.getOriginal().getPersistentData().getCompound(DATA).copy());
        event.getEntity().getPersistentData().remove(CHARGING);
        if(event.getEntity() instanceof ServerPlayer player)clear(player,true);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){for(var state:new ArrayList<>(ACTIVE.values()))clear(state.owner,false);ACTIVE.clear();}
}





