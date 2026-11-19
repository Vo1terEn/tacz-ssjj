package cn.ssjjhyper;

import cn.judgfire.BurstStun;
import cn.ssjjparasite2.client.ParasiteDummyDamage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.TacHitResult;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative travelling rays. Native shooting still owns ammo and cadence. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperRays {
    public static final Set<ResourceLocation> GUNS=Set.of(new ResourceLocation("ssjj_hyper","hyper_revolver1"),new ResourceLocation("ssjj_hyper","hyper_revolver2"));
    public static final ResourceLocation MUZZLE=new ResourceLocation("ssjj_hyper","energy_ray_muzzle");
    public static final double RANGE=128,DAMAGE_DISTANCE=64,SPEED=4,EPS=.0001;
    public static final int STUN_TICKS=10,MAX_AGE=72;
    private static final String REPLACED="ssjj_hyper_ray_replaced";
    private static final List<Ray> ACTIVE=new ArrayList<>();
    private static final Map<UUID,Burst> FORMING=new HashMap<>();
    public static final double FORMATION_RADIUS=.9;
    private static final class Burst {
        final UUID id=UUID.randomUUID();final ServerLevel level;final LivingEntity owner;final ItemStack weapon;final ResourceLocation gun;
        final long tick;final String profile;final boolean enhanced;final Vec3 origin,direction,right,up;int count;
        Burst(ServerLevel level,LivingEntity owner,EntityKineticBullet source){
            this.level=level;this.owner=owner;weapon=owner.getMainHandItem();gun=source.getGunId();tick=level.getGameTime();origin=source.position();profile=profile(source,weapon);enhanced=enhanced(weapon);
            Vec3 aim=owner.getViewVector(1);if(aim.lengthSqr()<.00001)aim=source.getDeltaMovement();direction=aim.normalize();
            Vec3 sideways=direction.cross(new Vec3(0,1,0));
            if(sideways.lengthSqr()<.00001){double yaw=Math.toRadians(owner.getYRot());sideways=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));}
            right=sideways.normalize();up=right.cross(direction).normalize();
        }
        boolean accepts(ServerLevel level,LivingEntity owner,EntityKineticBullet source){return count<3&&this.level==level&&this.owner==owner&&weapon==owner.getMainHandItem()&&tick==level.getGameTime()&&gun.equals(source.getGunId())&&profile.equals(profile(source,weapon));}
        Vec3 vertex(int slot){return origin.add(formationOffset(right,up,slot));}
    }
    private static String profile(EntityKineticBullet source,ItemStack weapon){var gun=IGun.getIGunOrNull(weapon);String skin=gun==null?"":HyperScopes.skin(gun.getAttachmentId(weapon,AttachmentType.SCOPE));return source.getGunId().getPath()+(skin.isEmpty()?"":"_"+skin);}
    /** A fixed equilateral cross-section: upper left, upper right, lower centre. */
    public static Vec3 formationOffset(Vec3 right,Vec3 up,int slot){
        if(slot<0||slot>2)throw new IllegalArgumentException("Invalid formation slot");
        return slot==2?up.scale(-FORMATION_RADIUS):up.scale(FORMATION_RADIUS*.5).add(right.scale((slot==0?-1:1)*FORMATION_RADIUS*Math.sqrt(3)/2));
    }
    private HyperRays() {}
    public static boolean eligible(ResourceLocation id){return id!=null&&GUNS.contains(id);}
    public static boolean enhanced(ItemStack stack){var gun=IGun.getIGunOrNull(stack);return HyperConfig.enabled("muzzle_enabled")&&gun!=null&&eligible(gun.getGunId(stack))&&MUZZLE.equals(gun.getAttachmentId(stack,AttachmentType.MUZZLE));}
    /** Cumulative distance, including the return leg. One extra base damage per 64 blocks. */
    public static double multiplier(double distance,boolean enhanced){return 1+Math.min(HyperConfig.number(enhanced?"muzzle_ray_damage_multiplier_cap":"ray_damage_multiplier_cap")-1,Math.max(0,distance)/HyperConfig.number("ray_damage_distance"));}
    public static int afterHits(int ammo,int capacity,int hits){return hits<=0||capacity<=0||ammo>=capacity?ammo:(int)Math.min(capacity,(long)Math.max(0,ammo)+(long)hits*HyperConfig.integer("muzzle_refund_per_hit"));}
    private static final class Ray {
        final ServerLevel level;final LivingEntity owner;final ItemStack weapon;final boolean enhanced;
        final UUID id=UUID.randomUUID(),burst;final int slot;final Vec3 origin,direction;final Damage trace;final String profile;final long launchTick;
        final Set<UUID> visitedOut=new HashSet<>(),visitedBack=new HashSet<>(),damagedOut=new HashSet<>();
        Vec3 pos;double distance;boolean returning;int age;
        Ray(ServerLevel level,LivingEntity owner,EntityKineticBullet source,Burst shot,int slot){
            this.level=level;this.owner=owner;weapon=shot.weapon;enhanced=shot.enhanced;origin=shot.origin;direction=shot.direction;burst=shot.id;this.slot=slot;
            pos=shot.vertex(slot);
            // Expanding from the common muzzle to a vertex must never place a
            // projectile across a nearby solid wall or outside a loaded chunk.
            if(!level.hasChunkAt(BlockPos.containing(pos))){pos=origin;returning=true;}
            else{var wall=level.clip(new ClipContext(origin,pos,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
                if(wall.getType()!=HitResult.Type.MISS){Vec3 delta=wall.getLocation().subtract(origin);double length=delta.length();pos=length>EPS?wall.getLocation().subtract(delta.scale(EPS/length)):origin;returning=true;}
            }
            trace=new Damage(source);launchTick=level.getGameTime();
            profile=shot.profile;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void replace(EntityJoinLevelEvent event){
        if(!HyperConfig.enabled("ray_enabled")||event.isCanceled()||!(event.getLevel()instanceof ServerLevel level)||!(event.getEntity()instanceof EntityKineticBullet source)||!eligible(source.getGunId()))return;
        event.setCanceled(true);
        if(source.getPersistentData().getBoolean(REPLACED))return;
        source.getPersistentData().putBoolean(REPLACED,true);
        if(!(source.getOwner()instanceof LivingEntity owner)||!owner.isAlive()||owner.isSpectator())return;
        // Three native pellet events form one explicitly identified shot. Their
        // random velocity spread is discarded in favour of one frozen aim axis.
        Burst shot=FORMING.get(owner.getUUID());if(shot==null||!shot.accepts(level,owner,source)){shot=new Burst(level,owner,source);FORMING.put(owner.getUUID(),shot);}
        int slot=shot.count++;ACTIVE.add(new Ray(level,owner,source,shot,slot));if(shot.count==3)FORMING.remove(owner.getUUID());
    }
    private static Entity core(Entity part){return EntityKineticBullet.MaybeMultipartEntity.of(part).core();}
    private static boolean enemy(LivingEntity owner,Entity target){
        if(target==owner||!target.isAlive()||target.isSpectator()||!target.isPickable()||target.isPassengerOfSameVehicle(owner)||owner.isAlliedTo(target)||target.isAlliedTo(owner))return false;
        if(target instanceof OwnableEntity pet&&owner.getUUID().equals(pet.getOwnerUUID()))return false;
        if(target instanceof Player player){
            if(player.isCreative())return false;
            if(owner instanceof ServerPlayer shooter&&(!shooter.canHarmPlayer(player)||shooter.getServer()==null||!shooter.getServer().isPvpAllowed()))return false;
        }
        return target instanceof LivingEntity||target instanceof ITargetEntity;
    }
    private record Contact(Entity part,Entity core,Vec3 point,double distance){}
    private static List<Contact> contacts(Ray ray,Vec3 end){
        var nearest=new HashMap<UUID,Contact>();var visited=ray.returning?ray.visitedBack:ray.visitedOut;
        for(Entity part:ray.level.getEntities(ray.owner,new AABB(ray.pos,end).inflate(.1),e->enemy(ray.owner,core(e)))){
            Entity target=core(part);if(visited.contains(target.getUUID()))continue;
            // Use the actual target box: an inflated box must not reach through a wall.
            AABB box=part.getBoundingBox();var hit=box.contains(ray.pos)?Optional.of(ray.pos):box.clip(ray.pos,end);if(hit.isEmpty())continue;
            Vec3 point=hit.get();double distance=ray.pos.distanceTo(point);var old=nearest.get(target.getUUID());
            if(old==null||distance<old.distance)nearest.put(target.getUUID(),new Contact(part,target,point,distance));
        }
        return nearest.values().stream().sorted(Comparator.comparingDouble(Contact::distance).thenComparingInt(c->c.core.getId())).toList();
    }
    private static void refund(Ray ray){
        if(!ray.enhanced||!ray.owner.isAlive())return;
        if(ray.owner instanceof Player player){boolean carried=false;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i)==ray.weapon){carried=true;break;}if(!carried)return;}
        else if(ray.owner.getMainHandItem()!=ray.weapon)return;
        var gun=IGun.getIGunOrNull(ray.weapon);if(gun==null||!eligible(gun.getGunId(ray.weapon)))return;
        TimelessAPI.getCommonGunIndex(gun.getGunId(ray.weapon)).ifPresent(index->{
            int old=gun.getCurrentAmmoCount(ray.weapon),capacity=AttachmentDataUtils.getAmmoCountWithAttachment(ray.weapon,index.getGunData()),next=afterHits(old,capacity,1);
            if(old==next)return;gun.setCurrentAmmoCount(ray.weapon,next);
            if(ray.owner instanceof ServerPlayer player){player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();if(player.containerMenu!=player.inventoryMenu)player.containerMenu.broadcastChanges();}
        });
    }
    private static void hit(Ray ray,Contact contact){
        var visited=ray.returning?ray.visitedBack:ray.visitedOut;if(!visited.add(contact.core.getUUID()))return;
        var before=contact.core instanceof LivingEntity living?ParasiteDummyDamage.capture(living):null;
        ray.trace.amount=(float)(ray.trace.baseDamage*multiplier(ray.distance+contact.distance,ray.enhanced));
        ray.trace.hit(contact.part,contact.point,ray.pos,contact.point);
        boolean accepted=contact.core instanceof LivingEntity living?ParasiteDummyDamage.confirmedLoss(before,living)>0:contact.core instanceof ITargetEntity;
        if(!accepted)return;
        refund(ray);HyperRayNetwork.effect(ray.level,UUID.randomUUID(),1,contact.point,ray.profile);
        if(!ray.returning)ray.damagedOut.add(contact.core.getUUID());
        else if(ray.damagedOut.contains(contact.core.getUUID())){
            if(contact.core instanceof LivingEntity living&&living.isAlive())BurstStun.refresh(living,HyperConfig.integer("ray_stun_ticks"));
            if(ray.owner.isAlive())ray.owner.heal(ray.owner.getMaxHealth()*.05f);
            HyperRayNetwork.effect(ray.level,UUID.randomUUID(),3,contact.core.getBoundingBox().getCenter(),ray.profile);
        }
    }
    private static void finish(Ray ray){HyperRayNetwork.effect(ray.level,ray.id,2,ray.pos,ray.profile,ray.burst,ray.slot,ray.origin);ray.trace.discard();}
    private static boolean advance(Ray ray){
        if(!HyperConfig.enabled("ray_enabled")||!ray.owner.isAlive()||ray.owner.isRemoved()||ray.owner.level()!=ray.level||++ray.age>MAX_AGE){finish(ray);return false;}
        Vec3 toward=ray.returning?ray.origin.subtract(ray.pos):ray.direction;
        double remaining=ray.returning?toward.length():RANGE-ray.distance;
        if(remaining<=EPS){finish(ray);return false;}
        Vec3 unit=ray.returning?toward.scale(1/remaining):ray.direction;
        Vec3 end=ray.pos.add(unit.scale(Math.min(SPEED,remaining)));
        if(!ray.level.hasChunkAt(BlockPos.containing(end))){
            if(ray.returning){finish(ray);return false;}ray.returning=true;return true;
        }
        var wall=ray.level.clip(new ClipContext(ray.pos,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ray.owner));boolean blocked=wall.getType()!=HitResult.Type.MISS;
        if(blocked)end=wall.getLocation();
        for(Contact contact:contacts(ray,end))hit(ray,contact);
        Vec3 from=ray.pos;double segment=from.distanceTo(end);ray.distance+=segment;ray.pos=end;
        HyperRayNetwork.segment(ray.level,ray.id,from,end,ray.returning,ray.profile,ray.burst,ray.slot,ray.origin);
        if(ray.returning){if(blocked||end.distanceToSqr(ray.origin)<=EPS*EPS){finish(ray);return false;}}
        else if(blocked||ray.distance>=RANGE-EPS){
            ray.returning=true;
            // Start outside the struck solid face so its surface is not hit again at t=0.
            if(blocked&&segment>EPS)ray.pos=end.subtract(unit.scale(EPS));
        }
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        FORMING.values().removeIf(shot->shot.level.getGameTime()>shot.tick||!shot.owner.isAlive()||shot.owner.isRemoved());
        var pending=new ArrayList<>(ACTIVE);ACTIVE.clear();
        for(var ray:pending){if(ray.level.getGameTime()<=ray.launchTick){ACTIVE.add(ray);continue;}if(advance(ray))ACTIVE.add(ray);}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){for(var ray:ACTIVE)ray.trace.discard();ACTIVE.clear();FORMING.clear();}
    /** Native TaCZ hit events, armour split, target adapters and kill credit remain intact. */
    static final class Damage extends EntityKineticBullet {
        final float baseDamage;float amount;
        private static final List<Field> STATE=new ArrayList<>();
        static{for(Field f:EntityKineticBullet.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);STATE.add(f);}}
        Damage(EntityKineticBullet source){
            super(EntityKineticBullet.TYPE,source.level());
            try{for(var f:STATE)f.set(this,f.get(source));}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot snapshot Hyper ray",error);}
            setOwner(source.getOwner());setPos(source.position());setDeltaMovement(source.getDeltaMovement());
            baseDamage=super.getDamage(source.position());amount=baseDamage;
            getPersistentData().putBoolean("ssjj_hyper_ray",true);
            HyperOverloadDamage.copyShot(source,this);
        }
        @Override public float getDamage(Vec3 point){return amount;}
        void hit(Entity target,Vec3 point,Vec3 from,Vec3 to){setPos(from);super.onHitEntity(new TacHitResult(new EntityKineticBullet.EntityResult(target,point,false)),from,to);}
    }
}
