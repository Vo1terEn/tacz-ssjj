package cn.ssjjdimension;

import cn.judgfire.BurstStun;
import cn.ssjjparasite2.client.ParasiteDummyDamage;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative high-energy input, deployed fields and mark-burst interaction. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionField {
    public record Area(UUID id,UUID owner,Vec3 center,float radius,int tier,long until,String skin,int totalTicks,DimensionAttachments.Snapshot equipment) {
        public Area(UUID id,UUID owner,Vec3 center,float radius,int tier,long until,String skin){this(id,owner,center,radius,tier,until,skin,DimensionFieldRules.fieldDuration(),DimensionAttachments.NONE);}
    }
    private record Session(ItemStack stack,int slot,int tier,ResourceLocation dimension,DimensionFieldRules.State timing) {
        Session timing(DimensionFieldRules.State next) { return new Session(stack,slot,tier,dimension,next); }
    }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<ServerLevel,List<Area>> FIELDS=new WeakHashMap<>();
    private static boolean resolvingBonus;
    private static final Set<UUID> EXTRA_FIELDS=new HashSet<>();
    private DimensionField() {}
    public static int tier(ItemStack stack) {
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);
        var id=gun==null?null:gun.getGunId(stack);
        if(id==null||!"ssjj_dimension".equals(id.getNamespace()))return -1;
        return switch(id.getPath()) {case "dimension_star"->0;case "dimension_star1"->1;case "dimension_star2"->2;default->-1;};
    }
    private static long now(LivingEntity entity) { return entity.level().getGameTime(); }
    private static boolean bound(ServerPlayer player,Session s) {
        return s!=null&&player.isAlive()&&!player.isSpectator()&&player.level() instanceof ServerLevel
            &&player.getInventory().selected==s.slot()&&player.getMainHandItem()==s.stack()
            &&tier(s.stack())==s.tier()&&s.dimension().equals(player.level().dimension().location());
    }
    public static boolean active(LivingEntity entity) {
        if(!(entity instanceof ServerPlayer player))return false;
        var s=SESSIONS.get(player.getUUID());
        return bound(player,s)&&DimensionFieldRules.activeTicks(s.timing(),now(player))>0;
    }
    public static int activeTicks(ServerPlayer player) {
        var s=SESSIONS.get(player.getUUID());return bound(player,s)?DimensionFieldRules.activeTicks(s.timing(),now(player)):0;
    }
    public static int cooldownTicks(ServerPlayer player) {
        var s=SESSIONS.get(player.getUUID());return s==null?0:DimensionFieldRules.cooldownTicks(s.timing(),now(player));
    }
    public static int castWait(ServerPlayer player){var s=SESSIONS.get(player.getUUID());return bound(player,s)?(int)Math.max(0,Math.min(1200,s.timing().nextCast()-now(player))):0;}
    private static boolean operating(ServerPlayer player) {
        return DimensionFieldRules.actionReady(IGunOperator.fromLivingEntity(player))&&!BurstStun.stunned(player);
    }
    /** Native held-slot, draw/reload/melee and stun guards shared by both C2S actions. */
    public static boolean eligible(ServerPlayer player,int slot) {
        return heldEligible(player,slot)&&operating(player);
    }
    private static boolean heldEligible(ServerPlayer player,int slot) {
        return player!=null&&slot>=0&&slot<=8&&player.isAlive()&&!player.isSpectator()
            &&player.level() instanceof ServerLevel&&player.getInventory().selected==slot
            &&tier(player.getMainHandItem())>=1;
    }
    public static void activate(ServerPlayer player,int slot) {
        if(!heldEligible(player,slot)||!DimensionFieldRules.activationReady(IGunOperator.fromLivingEntity(player))||BurstStun.stunned(player))return;
        var prior=SESSIONS.get(player.getUUID());long now=now(player);
        var state=prior==null?DimensionFieldRules.idle():prior.timing();
        if(!DimensionFieldRules.canActivate(state,now))return;
        var stack=player.getMainHandItem();
        SESSIONS.put(player.getUUID(),new Session(stack,slot,tier(stack),player.level().dimension().location(),DimensionFieldRules.activate(state,now)));
        DimensionFieldSounds.activate(player);
        sync(player);
    }
    /** V toggles an active session even while reloading; activation keeps native guards. */
    public static void toggle(ServerPlayer player,int slot) {
        if(player==null||slot<0||slot>8||player.getInventory().selected!=slot)return;
        var session=SESSIONS.get(player.getUUID());
        if(bound(player,session)&&DimensionFieldRules.activeTicks(session.timing(),now(player))>0){
            SESSIONS.put(player.getUUID(),session.timing(DimensionFieldRules.deactivate(session.timing(),now(player))));
            sync(player);
        }else activate(player,slot);
    }
    public static void cast(ServerPlayer player,int slot) {
        if(!eligible(player,slot))return;
        var s=SESSIONS.get(player.getUUID());long now=now(player);
        if(!bound(player,s)||!DimensionFieldRules.canCast(s.timing(),now)
            ||!(player.level() instanceof ServerLevel level))return;
        Vec3 center=aim(player,level);
        if(!DimensionFieldRules.finite(center)||!level.hasChunkAt(net.minecraft.core.BlockPos.containing(center)))return;
        String skin=DimensionVisualScope.skin(player.getMainHandItem());var equipment=DimensionAttachments.capture(player.getMainHandItem());
        var area=new Area(UUID.randomUUID(),player.getUUID(),ground(level,player,center),
            equipment.radius(s.tier()),s.tier(),now+equipment.duration(),skin,equipment.duration(),equipment);
        SESSIONS.put(player.getUUID(),s.timing(DimensionFieldRules.cast(s.timing(),now)));
        blast(level,area,player);
        while(ownedCount(player)>=DimensionFieldRules.maxFields())evictOldest(player.getUUID());
        FIELDS.computeIfAbsent(level,k->new ArrayList<>()).add(area);
        DimensionFieldSounds.cast(player,area);
        DimensionFieldNetwork.impact(level,player.getUUID(),area.center(),area.radius(),area.tier(),area.tier()==2,area.skin());
        DimensionFieldNetwork.field(level,area.id(),area.owner(),area.center(),area.radius(),area.tier(),area.totalTicks(),area.skin(),area.totalTicks());
        fill(level,area,player);
        sync(player);
    }
    /** One decoy reaction damages only the attacker and adds an independently capped field. */
    public static void decoyBlast(ServerPlayer owner,LivingEntity attacker,String skin) {decoyBlast(owner,attacker,skin,DimensionAttachments.NONE);}
    public static boolean decoyBlast(ServerPlayer owner,LivingEntity attacker,String skin,DimensionAttachments.Snapshot equipment) {
        if(!(owner.level() instanceof ServerLevel level)||attacker.level()!=level||!counterEnemy(owner,attacker))return false;
        Vec3 center=ground(level,owner,attacker.position());
        var area=new Area(UUID.randomUUID(),owner.getUUID(),center,equipment.radius(2),2,now(owner)+equipment.duration(),skin,equipment.duration(),equipment);
        EXTRA_FIELDS.add(area.id());FIELDS.computeIfAbsent(level,k->new ArrayList<>()).add(area);
        if(enemy(owner,attacker)&&damage(attacker,owner,DimensionFieldRules.damage(),equipment)&&attacker.isAlive())attacker.addEffect(new MobEffectInstance(BurstStun.STUN.get(),equipment.stun(),0,false,false,true));
        if(attacker.isAlive())DimensionOverload.apply(attacker);
        DimensionFieldNetwork.impact(level,owner.getUUID(),center,area.radius(),2,true,area.skin());
        DimensionFieldNetwork.field(level,area.id(),area.owner(),center,area.radius(),2,area.totalTicks(),area.skin(),area.totalTicks());
        fill(level,area,owner);sync(owner);return true;
    }
    /** Identical one-shot high-energy blast used at deployment and upgraded field expiration. */
    private static void blast(ServerLevel level,Area area,LivingEntity owner){
        for(var target:targets(level,owner,area.center(),area.radius(),true))if(damage(target,owner,DimensionFieldRules.damage(),area.equipment())&&target.isAlive())
            target.addEffect(new MobEffectInstance(BurstStun.STUN.get(),area.equipment().stun(),0,false,false,true));
    }
    private static void terminal(ServerLevel level,Area area){
        if(!area.equipment().ultimate()||!(level.getEntity(area.owner()) instanceof LivingEntity owner)||!owner.isAlive()||owner.isRemoved()||owner.level()!=level)return;
        blast(level,area,owner);fill(level,area,owner);
        DimensionFieldNetwork.impact(level,area.owner(),area.center(),area.radius(),area.tier(),area.tier()==2,area.skin());
        level.playSound(null,area.center().x,area.center().y,area.center().z,net.minecraft.sounds.SoundEvent.createFixedRangeEvent(new ResourceLocation("ssjj_dimension","field_impact"),96),net.minecraft.sounds.SoundSource.PLAYERS,1,1);
    }
    /** Uses server eye/view vectors only; no client-selected position can bypass blockers. */
    private static Vec3 aim(ServerPlayer player,ServerLevel level) {
        Vec3 start=player.getEyePosition(),end=start.add(player.getViewVector(1).scale(DimensionFieldRules.range()));
        var wall=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        Vec3 result=end;double nearest=start.distanceToSqr(end);
        for(var e:level.getEntities(player,new AABB(start,end).inflate(1),candidate->candidate.isAlive()&&!candidate.isSpectator()&&candidate.isPickable())) {
            var box=e.getBoundingBox().inflate(.1);var hit=box.contains(start)?Optional.of(start):box.clip(start,end);
            if(hit.isPresent()) {double distance=start.distanceToSqr(hit.get());if(distance<nearest){result=hit.get();nearest=distance;}}
        }
        return result;
    }
    private static Vec3 ground(ServerLevel level,ServerPlayer owner,Vec3 hit) {
        var bottom=new Vec3(hit.x,Math.min(hit.y-.1,level.getMinBuildHeight()),hit.z);
        var floor=level.clip(new ClipContext(hit.add(0,.1,0),bottom,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        return floor.getType()==HitResult.Type.MISS?hit:new Vec3(hit.x,floor.getLocation().y,hit.z);
    }
    /** Allegiance check for an incoming attack; immunity must not suppress the retaliation field. */
    public static boolean counterEnemy(LivingEntity shooter,LivingEntity target) {
        if(shooter==null||target==null||target==shooter||!target.isAlive()||target.isSpectator()
            ||shooter.isAlliedTo(target)||target.isAlliedTo(shooter)
            ||target instanceof OwnableEntity owned&&shooter.getUUID().equals(owned.getOwnerUUID()))return false;
        return !(shooter instanceof ServerPlayer owner&&target instanceof Player p)
            ||owner.canHarmPlayer(p)&&owner.getServer()!=null&&owner.getServer().isPvpAllowed();
    }
    public static boolean enemy(LivingEntity shooter,LivingEntity target) {
        return counterEnemy(shooter,target)&&!(target instanceof ArmorStand)&&target.isAttackable()&&!target.isInvulnerable()
            &&!(target instanceof Player p&&p.isCreative());
    }
    private static List<LivingEntity> targets(ServerLevel level,LivingEntity owner,Vec3 center,float radius,boolean field) {
        var unique=new LinkedHashMap<UUID,LivingEntity>();
        var bounds=field?new AABB(center.x-radius,center.y-DimensionFieldRules.FIELD_BELOW,center.z-radius,
            center.x+radius,center.y+DimensionFieldRules.FIELD_ABOVE,center.z+radius):new AABB(center,center).inflate(radius);
        for(var e:level.getEntities(owner,bounds.inflate(1e-6),candidate->candidate.isAlive()&&!candidate.isSpectator())) {
            var core=EntityKineticBullet.MaybeMultipartEntity.of(e).core();
            if(core instanceof LivingEntity living&&enemy(owner,living)
                &&(field?DimensionFieldRules.containsField(center,radius,e.getBoundingBox()):DimensionFieldRules.contains(center,radius,e.getBoundingBox())))unique.put(living.getUUID(),living);
        }
        return new ArrayList<>(unique.values());
    }
    private static boolean damage(LivingEntity target,LivingEntity owner,float amount,DimensionAttachments.Snapshot equipment) {
        var before=ParasiteDummyDamage.capture(target);int immunity=target.invulnerableTime;target.invulnerableTime=0;
        try { target.hurt(cn.ssjjcompat.CombatMessages.remember(target.damageSources().indirectMagic(owner,owner),"dimension_field"),amount*DimensionAttachments.boost(equipment,target)); }
        finally { target.invulnerableTime=immunity; }
        return ParasiteDummyDamage.confirmedLoss(before,target)>0;
    }
    private static void fill(ServerLevel level,Area area,LivingEntity owner) {
        for(var target:targets(level,owner,area.center(),area.radius(),true))DimensionMarks.fillToFive(target,owner,DimensionFieldRules.damage(),area.tier()+1,area.skin(),area.equipment());
    }
    public static List<Area> fields(ServerLevel level) { return List.copyOf(FIELDS.getOrDefault(level,List.of())); }
    public static int ownedCount(ServerPlayer player) {
        int count=0;for(var list:FIELDS.values())for(var area:list)if(area.owner().equals(player.getUUID())&&!EXTRA_FIELDS.contains(area.id()))count++;return count;
    }
    private static void evictOldest(UUID owner) {
        Area oldest=null;ServerLevel where=null;
        for(var e:FIELDS.entrySet())for(var area:e.getValue())if(area.owner().equals(owner)&&!EXTRA_FIELDS.contains(area.id())&&(oldest==null||area.until()-area.totalTicks()<oldest.until()-oldest.totalTicks())){oldest=area;where=e.getKey();}
        if(oldest!=null){FIELDS.get(where).remove(oldest);EXTRA_FIELDS.remove(oldest.id());DimensionFieldNetwork.remove(where,oldest.id());terminal(where,oldest);}
    }
    /** Explicit successful-mark callback, never called by bonus damage itself. */
    public static void onMarkExplosion(LivingEntity owner,LivingEntity marked) {
        if(resolvingBonus||owner==null||marked==null||!(marked.level() instanceof ServerLevel level)||owner.level()!=level||!owner.isAlive())return;
        var areas=FIELDS.get(level);if(areas==null||!enemy(owner,marked))return;
        Area inside=null;long now=level.getGameTime();
        for(var area:List.copyOf(areas)) {
            if(area.until()<=now||!area.owner().equals(owner.getUUID())||!DimensionFieldRules.containsField(area.center(),area.radius(),marked.getBoundingBox()))continue;
            if(inside==null||area.equipment().ability())inside=area;
        }
        if(inside==null)return;
        resolvingBonus=true;
        try { damage(marked,owner,DimensionFieldRules.bonusDamage(),inside.equipment()); }
        finally { resolvingBonus=false; }
    }
    private static void sync(ServerPlayer player) { DimensionFieldNetwork.status(player,activeTicks(player),cooldownTicks(player)); }
    private static void finish(ServerPlayer player) {
        var s=SESSIONS.get(player.getUUID());if(s==null)return;
        SESSIONS.put(player.getUUID(),s.timing(DimensionFieldRules.finish(s.timing(),now(player))));sync(player);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer player))return;
        var s=SESSIONS.get(player.getUUID());if(s==null)return;
        long now=now(player);
        if(s.timing().activeUntil()>0&&(!bound(player,s)||DimensionFieldRules.activeTicks(s.timing(),now)==0))finish(player);
        if(now%10==0)sync(player);
        s=SESSIONS.get(player.getUUID());if(s!=null&&s.timing().activeUntil()==0&&DimensionFieldRules.cooldownTicks(s.timing(),now)==0)SESSIONS.remove(player.getUUID());
    }
    // Mark processing runs first, so missing layers are replenished without resetting live timers.
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void levelTick(TickEvent.LevelTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level))return;
        long now=level.getGameTime();var areas=FIELDS.get(level);var changedOwners=new HashSet<UUID>();
        if(areas!=null) {
            for(var area:new ArrayList<>(areas)) {
                var entity=level.getEntity(area.owner());
                if(area.until()<=now||!(entity instanceof LivingEntity owner)||!owner.isAlive()||owner.isRemoved()) {
                    areas.remove(area);EXTRA_FIELDS.remove(area.id());changedOwners.add(area.owner());DimensionFieldNetwork.remove(level,area.id());if(area.until()<=now)terminal(level,area);continue;
                }
                fill(level,area,(LivingEntity)entity);
                if(now%10==0)DimensionFieldNetwork.field(level,area.id(),area.owner(),area.center(),area.radius(),area.tier(),(int)(area.until()-now),area.skin(),area.totalTicks());
            }
            if(areas.isEmpty())FIELDS.remove(level);
        }
        for(var owner:changedOwners)if(level.getEntity(owner) instanceof ServerPlayer p)sync(p);
        SESSIONS.entrySet().removeIf(entry->entry.getValue().timing().activeUntil()==0&&DimensionFieldRules.cooldownTicks(entry.getValue().timing(),now)==0);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent e) { if(!e.getLogicalSide().isClient()&&active(e.getShooter()))e.setCanceled(true); }
    private static void clearOwned(ServerPlayer p){
        for(var entry:FIELDS.entrySet())for(var it=entry.getValue().iterator();it.hasNext();){var area=it.next();if(!area.owner().equals(p.getUUID()))continue;it.remove();EXTRA_FIELDS.remove(area.id());DimensionFieldNetwork.remove(entry.getKey(),area.id());}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p){finish(p);clearOwned(p);} }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p){finish(p);clearOwned(p);} }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if(e.getEntity() instanceof ServerPlayer p){finish(p);clearOwned(p);} }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { FIELDS.clear();EXTRA_FIELDS.clear();SESSIONS.clear();resolvingBonus=false; }
}
