package cn.ssjjhyper;

import cn.judgfire.BurstStun;
import cn.ssjjparasite2.client.ParasiteDummyDamage;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server-owned charge sessions: expanding marks, view/scene checked detonations and movement-only roots. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperCharge {
    public static final ResourceLocation GRIP=new ResourceLocation("ssjj_hyper","energy_bomb_grip");
    private static final UUID ROOT_SPEED=UUID.fromString("edb678c8-e7b7-4b95-a48d-e505093d4cb6");
    private static final UUID NONE=new UUID(0,0);
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Root> ROOTS=new HashMap<>();
    private static final class Mark {
        final UUID id=UUID.randomUUID(),target;int hits,slow;long slowUntil;
        Mark(UUID target){this.target=target;}
        int slow(long now){return slowUntil>now?slow:0;}
    }
    private record Pulse(UUID id,Vec3 center,long born){}
    private static final class Session {
        final UUID id=UUID.randomUUID(),owner;final ServerLevel level;final ItemStack stack;final int slot,tier,total,cap;
        final float radius;final boolean grip;final String profile;final long started;
        final Map<UUID,Mark> marks=new LinkedHashMap<>();final List<Pulse> pulses=new ArrayList<>();
        long until,cooldownUntil,nextCast,recoveryStarted;int energy=HyperChargeRules.ENERGY,recoveryEnergy;boolean first,second;
        Session(ServerPlayer p,int slot,long now){
            owner=p.getUUID();level=(ServerLevel)p.level();stack=p.getMainHandItem();this.slot=slot;tier=tier(stack);grip=grip(stack);
            total=HyperChargeRules.duration(grip);cap=HyperChargeRules.cap(tier,grip);radius=HyperChargeRules.radius(tier,grip);
            started=now;until=now+total;nextCast=now+HyperChargeRules.ENTER_TICKS;profile=profile(stack);
        }
    }
    private record Root(LivingEntity target,ServerLevel level,Vec3 anchor,long until,UUID owner,String profile){}
    private HyperCharge(){}
    public static int tier(ItemStack stack){
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);var id=gun==null?null:gun.getGunId(stack);
        if(id==null||!"ssjj_hyper".equals(id.getNamespace()))return -1;
        return switch(id.getPath()){case "hyper_revolver"->0;case "hyper_revolver1"->1;case "hyper_revolver2"->2;default->-1;};
    }
    public static boolean grip(ItemStack stack){var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);return HyperConfig.enabled("grip_enabled")&&gun!=null&&tier(stack)>=1&&GRIP.equals(gun.getAttachmentId(stack,AttachmentType.GRIP));}
    public static String profile(ItemStack stack){
        var gun=IGun.getIGunOrNull(stack);if(gun==null||tier(stack)<1)return "hyper_revolver1";
        String skin=HyperScopes.skin(gun.getAttachmentId(stack,AttachmentType.SCOPE));return gun.getGunId(stack).getPath()+(skin.isEmpty()?"":"_"+skin);
    }
    /** One server clock keeps cooldowns stable across dimensions with different local times. */
    private static long clock(ServerLevel level){var server=level.getServer();return server!=null&&server.overworld()!=null?server.overworld().getGameTime():level.getGameTime();}
    private static long now(LivingEntity e){return e.level() instanceof ServerLevel level?clock(level):e.level().getGameTime();}
    private static boolean bound(ServerPlayer p,Session s){return s!=null&&p.isAlive()&&!p.isSpectator()&&p.level()==s.level&&p.getInventory().selected==s.slot&&p.getMainHandItem()==s.stack&&tier(s.stack)==s.tier;}
    public static boolean active(LivingEntity entity){
        if(!(entity instanceof ServerPlayer p))return false;var s=SESSIONS.get(p.getUUID());return HyperConfig.enabled("charge_enabled")&&bound(p,s)&&s.until>now(p);
    }
    public static int activeTicks(ServerPlayer p){var s=SESSIONS.get(p.getUUID());return bound(p,s)?(int)Math.max(0,s.until-now(p)):0;}
    public static int cooldownTicks(ServerPlayer p){var s=SESSIONS.get(p.getUUID());return s==null?0:(int)Math.max(0,Math.min(200,s.cooldownUntil-now(p)));}
    public static int energy(ServerPlayer p){var s=SESSIONS.get(p.getUUID());return s==null?HyperChargeRules.ENERGY:energy(s,now(p));}
    /** Settle passive recovery before adding a core refund; the old recovery clock must not overwrite it. */
    public static void refundEnergy(ServerPlayer p,int amount){
        if(p==null||amount<=0)return;var s=SESSIONS.get(p.getUUID());if(s==null)return;long now=now(p);
        s.energy=(int)Math.min(HyperChargeRules.ENERGY,(long)energy(s,now)+amount);
        if(s.until==0){s.recoveryEnergy=s.energy;s.recoveryStarted=now;s.cooldownUntil=now+HyperChargeRules.recoveryTicks(s.energy);}
        sync(p,s);
    }
    private static int energy(Session s,long now){return s.until>0?s.energy:HyperChargeRules.recoverEnergy(s.recoveryEnergy,now-s.recoveryStarted);}
    private static boolean held(ServerPlayer p,int slot){return p!=null&&slot>=0&&slot<9&&p.isAlive()&&!p.isSpectator()&&p.level() instanceof ServerLevel&&p.getInventory().selected==slot&&tier(p.getMainHandItem())>=1;}
    private static boolean ready(ServerPlayer p){
        var op=IGunOperator.fromLivingEntity(p);if(op==null||op.getSynDrawCoolDown()>0||op.getSynIsBolting()||op.getSynMeleeCoolDown()>0||BurstStun.stunned(p))return false;
        var reload=op.getSynReloadState();return reload==null||!reload.getStateType().isReloading();
    }
    public static void activate(ServerPlayer p,int slot){
        if(!HyperConfig.enabled("charge_enabled")||!held(p,slot)||!ready(p))return;long now=now(p);var old=SESSIONS.get(p.getUUID());
        if(old!=null&&(old.until>now||old.cooldownUntil>now))return;
        var s=new Session(p,slot,now);SESSIONS.put(p.getUUID(),s);sync(p,s);
    }
    /** A client may abandon its own session even if a rapid switch was coalesced server-side. */
    public static void interrupt(ServerPlayer p,int slot){
        if(p==null||slot<0||slot>=9)return;var s=SESSIONS.get(p.getUUID());
        if(s!=null&&s.until>0)finish(p,s,now(p));
    }
    private static void pulse(ServerPlayer p,Session s,long now){
        prune(p,s);var wave=new Pulse(UUID.randomUUID(),p.position(),now);s.pulses.add(wave);
        emit(s,0,wave.id(),-1,NONE,wave.center(),s.radius,HyperChargeRules.RING_TICKS,now);
    }
    private static boolean enemy(LivingEntity owner,LivingEntity target){
        if(owner==null||target==null||target==owner||!target.isAlive()||target.isRemoved()||target.isSpectator()||target instanceof ArmorStand||!target.isAttackable()||target.isInvulnerable()||target.isPassengerOfSameVehicle(owner)||owner.isAlliedTo(target)||target.isAlliedTo(owner))return false;
        if(target instanceof OwnableEntity pet&&owner.getUUID().equals(pet.getOwnerUUID()))return false;
        if(target instanceof Player player){if(player.isCreative())return false;if(owner instanceof ServerPlayer p&&(!p.canHarmPlayer(player)||p.getServer()==null||!p.getServer().isPvpAllowed()))return false;}
        return true;
    }
    private static LivingEntity target(Session s,UUID id){var e=s.level.getEntity(id);return e instanceof LivingEntity l?l:null;}
    private static void prune(ServerPlayer p,Session s){
        for(var it=s.marks.values().iterator();it.hasNext();){var m=it.next();var t=target(s,m.target);if(t==null||!enemy(p,t)){it.remove();emit(s,3,m.id,t==null?-1:t.getId(),m.target,t==null?p.position():t.position(),0,0,now(p));}}
    }
    private static void wave(ServerPlayer p,Session s,Pulse pulse,long now){
        long age=now-pulse.born();if(age<0||age>=HyperChargeRules.RING_TICKS)return;
        double inner=s.radius*age/HyperChargeRules.RING_TICKS,outer=s.radius*(age+1)/HyperChargeRules.RING_TICKS;
        var c=pulse.center();
        var unique=new LinkedHashMap<UUID,LivingEntity>();
        // Traverse loaded entities: a build-height query would still reject airborne targets.
        for(var e:s.level.getAllEntities()){
            if(e==p||!e.isAlive())continue;
            var core=EntityKineticBullet.MaybeMultipartEntity.of(e).core();
            if(core instanceof LivingEntity t&&enemy(p,t)&&HyperChargeRules.shell(c,inner,outer,e.getBoundingBox()))unique.put(t.getUUID(),t);
        }
        var targets=new ArrayList<>(unique.values());targets.sort(Comparator.comparingDouble((LivingEntity t)->{double x=t.getX()-c.x,z=t.getZ()-c.z;return x*x+z*z;}).thenComparingInt(Entity::getId));
        for(var t:targets){var m=s.marks.get(t.getUUID());if(m==null){if(s.marks.size()>=s.cap)continue;m=new Mark(t.getUUID());s.marks.put(t.getUUID(),m);}mark(s,m,t,now);}
    }
    private static void mark(Session s,Mark m,LivingEntity t,long now){emit(s,1,m.id,t.getId(),t.getUUID(),t.position(),0,(int)Math.max(0,s.until-now),now);}
    /** Visible means inside the server-oriented camera rectangle and unobstructed by scene colliders. */
    private static boolean visible(ServerPlayer p,LivingEntity t,float fov,float aspect){
        return visible(p,t,fov,aspect,false);
    }
    private static boolean visible(ServerPlayer p,LivingEntity t,float fov,float aspect,boolean outsideView){
        Vec3 eye=p.getEyePosition(),view=p.getViewVector(1);AABB b=t.getBoundingBox();
        if(b.contains(eye))return true;
        var points=new ArrayList<Vec3>();points.add(b.getCenter());points.add(t.getEyePosition());
        for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY+.01,b.maxY-.01})for(double z:new double[]{b.minZ,b.maxZ})points.add(new Vec3(x,y,z));
        for(var point:points){if(!outsideView&&!HyperChargeRules.viewport(eye,view,point,fov,aspect))continue;var hit=p.level().clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()==HitResult.Type.MISS||eye.distanceToSqr(hit.getLocation())+1e-6>=eye.distanceToSqr(point))return true;}
        return false;
    }
    private static List<Mark> visibleMarks(ServerPlayer p,Session s,float fov,float aspect){
        var result=new ArrayList<Mark>();
        boolean outsideView=HyperStones.ability(s.stack)&&HyperConfig.enabled("ability_outside_view_enabled");
        for(var m:s.marks.values()){var t=target(s,m.target);if(t!=null&&enemy(p,t)&&visible(p,t,fov,aspect,outsideView))result.add(m);}
        return result;
    }
    private static int cost(List<Mark> marks,long now){
        return HyperChargeRules.cost()+(marks.stream().anyMatch(m->m.slow(now)>=5)?HyperChargeRules.rootCost():0);
    }
    public static void cast(ServerPlayer p,int slot,float fov,float aspect){
        if(!HyperConfig.enabled("charge_enabled")||!held(p,slot)||!ready(p)||!HyperChargeRules.validView(fov,aspect))return;var s=SESSIONS.get(p.getUUID());long now=now(p);
        if(!bound(p,s)||s.until<=now||s.nextCast>now)return;prune(p,s);
        var candidates=visibleMarks(p,s,fov,aspect);
        if(!candidates.isEmpty()&&s.energy<cost(candidates,now)){finish(p,s,now);return;}
        s.nextCast=now+HyperChargeRules.SHOT_WAIT;
        // An accepted trigger always has its special attack, flash and cast sound.
        // Only confirmed target damage below spends energy; an empty view is free.
        emit(s,6,UUID.randomUUID(),p.getId(),p.getUUID(),p.position(),0,8,now);
        if(candidates.isEmpty()){sync(p,s);return;}
        int successes=0,kills=0;boolean rooted=false;
        for(var m:candidates){
            var t=target(s,m.target);if(t==null||!enemy(p,t))continue;boolean root=m.slow(now)>=5;Vec3 point=t.position();int entityId=t.getId();
            if(!damage(t,p,HyperChargeRules.damage(m.hits,HyperStones.ultimate(s.stack))))continue;
            successes++;m.hits++;if(root)rooted=true;emit(s,2,UUID.randomUUID(),entityId,m.target,point,0,22,now);
            if(!t.isAlive()){kills++;continue;}
            int slow=m.slow(now);m.slow=Math.min(5,slow+1);m.slowUntil=now+HyperChargeRules.slowTicks();
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,HyperChargeRules.slowTicks(),m.slow-1,false,true,true));
            if(root&&HyperChargeRules.rootTicks()>0)root(t,p,s.profile,now);
        }
        int cost=successes==0?0:HyperChargeRules.cost()+(rooted?HyperChargeRules.rootCost():0);
        s.energy=HyperChargeRules.energyAfter(s.energy,cost,kills);prune(p,s);
        // Settle every kill refund first, then use the same next-batch cost as the input guard.
        // With no visible targets, the minimum possible future detonation still needs ten percent.
        if(successes>0&&s.energy<cost(visibleMarks(p,s,fov,aspect),now)){finish(p,s,now);return;}
        sync(p,s);
    }
    private static boolean damage(LivingEntity target,LivingEntity owner,float damage){
        var before=ParasiteDummyDamage.capture(target);int immunity=target.invulnerableTime;target.invulnerableTime=0;
        try{target.hurt(target.damageSources().indirectMagic(owner,owner),HyperOverloadDamage.skillDamage(owner,owner.getMainHandItem(),damage));}finally{target.invulnerableTime=immunity;}
        return ParasiteDummyDamage.confirmedLoss(before,target)>0;
    }
    private static void root(LivingEntity t,ServerPlayer owner,String profile,long now){
        if(!(t.level() instanceof ServerLevel level))return;var old=ROOTS.get(t.getUUID());Vec3 anchor=old!=null&&old.level()==level&&old.until()>now?old.anchor():t.position();
        var state=new Root(t,level,anchor,now+HyperChargeRules.rootTicks(),owner.getUUID(),profile);ROOTS.put(t.getUUID(),state);
        var speed=t.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null&&speed.getModifier(ROOT_SPEED)==null)speed.addTransientModifier(new AttributeModifier(ROOT_SPEED,"SSJJ energy bomb movement root",-1,AttributeModifier.Operation.MULTIPLY_TOTAL));
        t.setDeltaMovement(Vec3.ZERO);if(t instanceof ServerPlayer player)rootSync(player,state,HyperChargeRules.rootTicks());
    }
    private static void rootSync(ServerPlayer p,Root state,int life){HyperChargeNetwork.direct(p,new HyperChargeNetwork.Visual(p.level().dimension().location(),5,p.getUUID(),state.owner(),p.getId(),p.getUUID(),p.position(),0,life,p.level().getGameTime(),state.profile()));}
    private static void unroot(UUID id,Root state){
        var speed=state.target().getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(ROOT_SPEED);
        if(state.target() instanceof ServerPlayer p&&p.connection!=null)rootSync(p,state,0);ROOTS.remove(id);
    }
    private static void roots(ServerLevel level,long now){
        for(var entry:new ArrayList<>(ROOTS.entrySet())){var r=entry.getValue();if(r.level()!=level)continue;var t=r.target();
            if(r.until()<=now||!t.isAlive()||t.isRemoved()||t.level()!=level){unroot(entry.getKey(),r);continue;}
            t.setDeltaMovement(Vec3.ZERO);t.fallDistance=0;
            if(t.position().distanceToSqr(r.anchor())>1e-8){if(t instanceof ServerPlayer p&&p.connection!=null)p.connection.teleport(r.anchor().x,r.anchor().y,r.anchor().z,p.getYRot(),p.getXRot());else t.setPos(r.anchor());}
        }
    }
    private static void sync(ServerPlayer p,Session s){HyperChargeNetwork.status(p,new HyperChargeNetwork.Status(p.level().dimension().location(),s.slot,activeTicks(p),cooldownTicks(p),s.total,energy(s,now(p)),s.marks.size(),s.cap,s.profile,s.id));}
    private static void emit(Session s,int type,UUID id,int target,UUID targetUuid,Vec3 center,float radius,int life,long tick){HyperChargeNetwork.visual(s.level,new HyperChargeNetwork.Visual(s.level.dimension().location(),type,id,s.owner,target,targetUuid,center,radius,life,s.level.getGameTime(),s.profile));}
    private static void finish(ServerPlayer p,Session s,long now){
        if(s.until==0)return;s.until=0;s.recoveryStarted=now;s.recoveryEnergy=s.energy;s.cooldownUntil=now+HyperChargeRules.recoveryTicks(s.energy);s.marks.clear();s.pulses.clear();
        HyperChargeNetwork.clear(s.level,new HyperChargeNetwork.Visual(s.level.dimension().location(),4,s.id,s.owner,-1,NONE,p.position(),0,0,Math.max(0,s.level.getGameTime()),s.profile));sync(p,s);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;var s=SESSIONS.get(p.getUUID());if(s==null)return;long now=now(p);
        if(s.until>0&&(!HyperConfig.enabled("charge_enabled")||!bound(p,s)||s.until<=now))finish(p,s,now);
        if(s.until>0){
            prune(p,s);if(!s.first&&now-s.started>=HyperChargeRules.ENTER_TICKS){s.first=true;pulse(p,s,now);}
            if(HyperConfig.enabled("charge_second_pulse_enabled")&&!s.second&&now-s.started>=HyperChargeRules.secondPulse(s.grip)){s.second=true;pulse(p,s,now);}
            for(var pulse:new ArrayList<>(s.pulses)){wave(p,s,pulse,now);if(now-pulse.born()+1>=HyperChargeRules.RING_TICKS)s.pulses.remove(pulse);}
            if(now%20==0)for(var m:s.marks.values()){var t=target(s,m.target);if(t!=null)mark(s,m,t,now);}
        }
        if(s.until==0)s.energy=energy(s,now);
        if(now%5==0)sync(p,s);if(s.until==0&&s.cooldownUntil<=now){sync(p,s);SESSIONS.remove(p.getUUID());}
    }
    @SubscribeEvent public static void levelTick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level))return;long now=clock(level);roots(level,now);
        SESSIONS.entrySet().removeIf(entry->entry.getValue().until==0&&entry.getValue().cooldownUntil<=now);
    }
    /** Cancel native fire before any projectile, ammo or old ray replacement is produced. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent e){if(!e.getLogicalSide().isClient()&&active(e.getShooter()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void melee(GunMeleeEvent e){if(!e.getLogicalSide().isClient()&&active(e.getShooter()))e.setCanceled(true);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){
        if(!(e.getEntity() instanceof ServerPlayer viewer)||!(e.getTarget() instanceof LivingEntity t))return;long now=now(t);
        for(var s:SESSIONS.values())if(s.until>now&&s.level==viewer.level()){var m=s.marks.get(t.getUUID());if(m!=null)HyperChargeNetwork.direct(viewer,new HyperChargeNetwork.Visual(s.level.dimension().location(),1,m.id,s.owner,t.getId(),t.getUUID(),t.position(),0,(int)(s.until-now),s.level.getGameTime(),s.profile));}
    }
    private static void exit(ServerPlayer p){var s=SESSIONS.get(p.getUUID());if(s!=null)finish(p,s,now(p));var r=ROOTS.get(p.getUUID());if(r!=null)unroot(p.getUUID(),r);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);var r=ROOTS.get(e.getEntity().getUUID());if(r!=null)unroot(e.getEntity().getUUID(),r);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var entry:new ArrayList<>(ROOTS.entrySet()))unroot(entry.getKey(),entry.getValue());ROOTS.clear();SESSIONS.clear();}
}
