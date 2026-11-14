package cn.ssjjzeus;

import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.TacHitResult;
import cn.ssjjparasite2.client.ParasiteDummyDamage;
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

/** Native shooting owns ammo/cadence; only the projectile is replaced before tracking. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ZeusThunder {
    public static final Set<ResourceLocation> GUNS=Set.of(
        new ResourceLocation("ssjj_zeus","hand_of_zeuss1"),new ResourceLocation("ssjj_zeus","hand_of_zeuss2"));
    public static final int LIFETIME=60;
    public static final ResourceLocation MUZZLE=new ResourceLocation("ssjj_zeus","thunder_burst");
    public static final double SPEED=1.5, HALF_SIZE=.5, CONTACT_RADIUS=.15;
    private static final String REPLACED="ssjj_zeus_thunder_replaced";
    private static final List<Ball> BALLS=new ArrayList<>();
    private ZeusThunder() {}
    public static double speed(){return ZeusConfig.number("orb_speed");}
    public static int lifetime(){return ZeusConfig.integer("orb_lifetime_ticks");}
    public static double contactRadius(){return ZeusConfig.number("orb_contact_radius");}
    private static float damageScale(boolean enhanced){return (float)(ZeusConfig.number("orb_damage_multiplier")*(enhanced?ZeusConfig.number("muzzle_damage_multiplier"):1));}
    private static final class Ball {
        final ServerLevel level; final LivingEntity owner; final NativeBlast trace; final ItemStack weapon;
        final boolean enhanced,energyMagazine,phantom;
        final long launchGameTime;
        final String profile;
        final UUID id=UUID.randomUUID(); final Vec3 velocity; Vec3 pos; int age;
        Ball(ServerLevel l,LivingEntity o,EntityKineticBullet source) {
            level=l;owner=o;weapon=o.getMainHandItem();enhanced=installed(weapon);
            phantom=false;launchGameTime=-1;energyMagazine=ZeusEnergy.magazine(weapon);profile=profile(weapon,source.getGunId());
            trace=new NativeBlast(source,damageScale(enhanced));trace.shockGrip=ZeusShock.grip(weapon);pos=source.position();
            // A single orb follows the shot direction at a configured fixed speed shared with the aim predictor.
            Vec3 direction=source.getDeltaMovement().normalize();
            velocity=(direction.lengthSqr()<.5?o.getViewVector(1).normalize():direction).scale(speed());
        }
        Ball(ServerLevel l,LivingEntity o,ItemStack snapshot,ItemStack sourceWeapon,NativeBlast template,Vec3 origin,Vec3 direction) {
            // Freeze the phantom's combat profile, but credit its hits to the exact original gun.
            level=l;owner=o;weapon=sourceWeapon;phantom=true;launchGameTime=l.getGameTime();enhanced=installed(snapshot);energyMagazine=ZeusEnergy.magazine(snapshot);
            profile=profile(snapshot,ZeusEnergy.GUN);pos=origin;velocity=direction.normalize().scale(speed());
            trace=template.phantomShot(origin,velocity);
        }
    }
    public static String profile(ItemStack weapon,ResourceLocation id) {
        String name=id==null?"hand_of_zeuss2":id.getPath();var gun=IGun.getIGunOrNull(weapon);
        ResourceLocation scope=gun==null?null:gun.getAttachmentId(weapon,AttachmentType.SCOPE);
        if(new ResourceLocation("ssjj_zeus","lava_scope").equals(scope))name+="_lava";
        else if(new ResourceLocation("ssjj_zeus","galaxy_scope").equals(scope))name+="_galaxy";
        return name;
    }
    public static boolean eligible(ResourceLocation id) {return id!=null && GUNS.contains(id);}
    public static boolean installed(ItemStack stack) {
        IGun gun=IGun.getIGunOrNull(stack);
        return ZeusConfig.enabled("muzzle_enabled") && gun!=null && eligible(gun.getGunId(stack)) && MUZZLE.equals(gun.getAttachmentId(stack,AttachmentType.MUZZLE));
    }
    static NativeBlast capturePhantom(ServerPlayer owner,ItemStack weapon) {
        IGun gun=IGun.getIGunOrNull(weapon);if(gun==null||!ZeusEnergy.GUN.equals(gun.getGunId(weapon)))return null;
        return TimelessAPI.getCommonGunIndex(ZeusEnergy.GUN).map(index->{
            var data=index.getGunData();
            var source=new EntityKineticBullet(owner.level(),owner,weapon,data.getAmmoId(),ZeusEnergy.GUN,gun.getGunDisplayId(weapon),false,data,index.getBulletData());
            try {NativeBlast template=new NativeBlast(source,damageScale(installed(weapon)));template.shockGrip=ZeusShock.grip(weapon);template.freezeDamage();return template;}
            finally {source.discard();}
        }).orElse(null);
    }
    static void launchPhantom(ServerLevel level,LivingEntity owner,ItemStack snapshot,ItemStack sourceWeapon,NativeBlast template,Vec3 origin,Vec3 direction) {
        if(!owner.isAlive()||owner.level()!=level||direction.lengthSqr()<.5)return;
        Ball ball=new Ball(level,owner,snapshot,sourceWeapon,template,origin,direction);BALLS.add(ball);
        ZeusThunderNetwork.spawn(level,ball.id,ball.pos,ball.velocity,lifetime(),ball.profile);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void replace(EntityJoinLevelEvent event) {
        if(!ZeusConfig.enabled("thunder_enabled") || event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
            || !(event.getEntity() instanceof EntityKineticBullet source) || !eligible(source.getGunId())
            || !(source.getOwner() instanceof LivingEntity owner))return;
        event.setCanceled(true);
        if(source.getPersistentData().getBoolean(REPLACED))return;
        source.getPersistentData().putBoolean(REPLACED,true);
        if(!owner.isAlive() || owner.isSpectator())return;
        Ball ball=new Ball(level,owner,source);BALLS.add(ball);
        ZeusThunderNetwork.spawn(level,ball.id,ball.pos,ball.velocity,lifetime(),ball.profile);
    }
    public static AABB blastBox(Vec3 center) {
        return blastBox(center,false);
    }
    public static AABB blastBox(Vec3 center,boolean enhanced) {
        double half=ZeusConfig.number(enhanced?"muzzle_blast_size":"blast_size")/2;
        return new AABB(center.x-half,center.y-half,center.z-half,center.x+half,center.y+half,center.z+half);
    }
    public static int afterHits(int ammo,int capacity,int hits) {
        if(hits<=0 || capacity<=0 || ammo>=capacity)return ammo;
        return (int)Math.min((long)capacity,(long)Math.max(0,ammo)+hits);
    }
    private static void refund(Ball ball,int hits) {
        if(!ZeusConfig.enabled("refund_enabled") || ball.phantom&&!ZeusConfig.enabled("phantom_refund_enabled") || !ball.enhanced || hits<=0 || !ball.owner.isAlive())return;
        // Keep the exact firing stack: switching slots cannot refill a different gun or a copied UUID.
        if(ball.owner instanceof Player player) {
            boolean carried=false;
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot)==ball.weapon){carried=true;break;}
            if(!carried)return;
        }
        IGun gun=IGun.getIGunOrNull(ball.weapon);
        if(gun==null || !eligible(gun.getGunId(ball.weapon)))return;
        TimelessAPI.getCommonGunIndex(gun.getGunId(ball.weapon)).ifPresent(index->{
            int old=gun.getCurrentAmmoCount(ball.weapon),capacity=AttachmentDataUtils.getAmmoCountWithAttachment(ball.weapon,index.getGunData());
            int next=afterHits(old,capacity,(int)Math.min(Integer.MAX_VALUE,(long)hits*ZeusConfig.integer("refund_per_target")));
            if(next!=old) {
                gun.setCurrentAmmoCount(ball.weapon,next);
                if(ball.owner instanceof ServerPlayer player) {
                    player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
                    if(player.containerMenu!=player.inventoryMenu)player.containerMenu.broadcastChanges();
                }
            }
        });
    }
    static boolean enemy(LivingEntity shooter,Entity target) {
        if(target==shooter || !target.isAlive() || target.isSpectator() || !target.isPickable()
            || target.isPassengerOfSameVehicle(shooter) || shooter.isAlliedTo(target) || target.isAlliedTo(shooter))return false;
        if(target instanceof OwnableEntity pet && shooter.getUUID().equals(pet.getOwnerUUID()))return false;
        if(target instanceof Player player) {
            if(player.isCreative())return false;
            if(shooter instanceof ServerPlayer owner && (!owner.canHarmPlayer(player)
                || owner.getServer()==null || !owner.getServer().isPvpAllowed()))return false;
        }
        return target instanceof LivingEntity || target instanceof ITargetEntity;
    }
    static Entity core(Entity part) {return EntityKineticBullet.MaybeMultipartEntity.of(part).core();}
    private record Contact(Entity part,Vec3 point) {}
    private static Contact contact(Ball ball,Vec3 end) {
        var block=ball.level.clip(new ClipContext(ball.pos,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ball.owner));
        Vec3 point=block.getType()==HitResult.Type.MISS?null:block.getLocation();
        double nearest=point==null?Double.POSITIVE_INFINITY:point.distanceToSqr(ball.pos);
        Entity part=null;
        for(Entity candidate:ball.level.getEntities(ball.owner,new AABB(ball.pos,end).inflate(contactRadius()),e->enemy(ball.owner,core(e)))) {
            var box=candidate.getBoundingBox().inflate(contactRadius());
            var hit=box.contains(ball.pos)?Optional.of(ball.pos):box.clip(ball.pos,end);
            if(hit.isPresent() && hit.get().distanceToSqr(ball.pos)<nearest) {
                point=hit.get();nearest=point.distanceToSqr(ball.pos);part=candidate;
            }
        }
        return point==null?null:new Contact(part,point);
    }
    private static Vec3 nearest(AABB box,Vec3 point) {
        return new Vec3(Math.max(box.minX,Math.min(box.maxX,point.x)),
            Math.max(box.minY,Math.min(box.maxY,point.y)),Math.max(box.minZ,Math.min(box.maxZ,point.z)));
    }
    private static void explode(Ball ball,Contact contact) {
        Vec3 center=contact.point;var box=blastBox(center,ball.enhanced);
        Map<UUID,Entity> targets=new LinkedHashMap<>();
        for(Entity candidate:ball.level.getEntities(ball.owner,box,e->enemy(ball.owner,core(e))))
            targets.putIfAbsent(core(candidate).getUUID(),candidate);
        // The contact part wins for multipart targets, and is still hit only once.
        if(contact.part!=null && enemy(ball.owner,core(contact.part)))targets.put(core(contact.part).getUUID(),contact.part);
        int accepted=0;
        List<ZeusEnergy.Anchor> anchors=new ArrayList<>();
        for(Entity target:targets.values()) {
            if(!enemy(ball.owner,core(target)))continue;
            Vec3 point=nearest(target.getBoundingBox(),center);
            // Block collision is also the blast boundary; adjacent targets cannot be hit through a wall.
            var wall=ball.level.clip(new ClipContext(center,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ball.owner));
            if(target!=contact.part && wall.getType()!=HitResult.Type.MISS && wall.getLocation().distanceToSqr(point)>1e-6)continue;
            Entity core=core(target);
            Vec3 anchorPosition=core.getBoundingBox().getCenter();float damage=ball.trace.getDamage(point);
            var before=core instanceof LivingEntity living?ParasiteDummyDamage.capture(living):null;
            ball.trace.hit(target,point,center,center.add(ball.velocity));
            boolean valid=false;
            if(core instanceof LivingEntity living) {
                valid=ParasiteDummyDamage.confirmedLoss(before,living)>0 || !living.isAlive();
            } else if(core instanceof ITargetEntity)valid=true;
            if(valid) {accepted++;anchors.add(new ZeusEnergy.Anchor(core,target,anchorPosition,point,damage));}
        }
        refund(ball,accepted);
        if(!ball.phantom||ZeusConfig.enabled("phantom_energy_enabled"))ZeusEnergy.impact(ball.level,ball.owner,ball.weapon,anchors,ball.trace,ball.energyMagazine,ball.profile);
        ZeusThunderNetwork.burst(ball.level,ball.id,center,ZeusConfig.number(ball.enhanced?"muzzle_blast_size":"blast_size"),ball.profile);
        ball.trace.discard();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        // Detach active shots before callbacks; nested shots can safely append to the next tick.
        var active=new ArrayList<>(BALLS);BALLS.clear();
        for(Ball ball:active) {
            Vec3 end=ball.pos.add(ball.velocity);
            if(ball.age>=lifetime() || !ball.owner.isAlive() || ball.owner.isRemoved() || ball.owner.level()!=ball.level
                || !ball.level.hasChunkAt(BlockPos.containing(end))) {
                ZeusThunderNetwork.remove(ball.level,ball.id,ball.pos);ball.trace.discard();continue;
            }
            // END subscriber order must not give a newly fired phantom orb an extra step.
            if(ball.phantom&&ball.level.getGameTime()<=ball.launchGameTime){BALLS.add(ball);continue;}
            ball.age++;
            Contact hit=contact(ball,end);
            if(hit!=null)explode(ball,hit);else {ball.pos=end;BALLS.add(ball);}
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {BALLS.clear();}
    /** Snapshot the already-modified native shot, including AP and per-shot damage multipliers. */
    static final class NativeBlast extends EntityKineticBullet {
        private final float damageScale;
        private float fixedDamage=Float.NaN;
        private float hitScale=1;
        boolean shockGrip;
        private record DamageBand(double limit,float damage) {}
        private DamageBand[] frozen;
        private Vec3 frozenOrigin;
        private static final List<Field> STATE=new ArrayList<>();
        static {
            for(Field field:EntityKineticBullet.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);STATE.add(field);
            }
        }
        NativeBlast(EntityKineticBullet source,float scale) {
            super(EntityKineticBullet.TYPE,source.level());
            damageScale=scale;
            try {for(Field field:STATE)field.set(this,field.get(source));}
            catch(IllegalAccessException error) {throw new IllegalStateException("Cannot snapshot Zeus thunder shot",error);}
            setOwner(source.getOwner());setPos(source.position());setDeltaMovement(source.getDeltaMovement());
            getPersistentData().merge(source.getPersistentData());
            if(source instanceof NativeBlast nativeSource){frozen=nativeSource.frozen;frozenOrigin=nativeSource.frozenOrigin;shockGrip=nativeSource.shockGrip;}
        }
        private static Field field(String name) {
            return STATE.stream().filter(f->f.getName().equals(name)).findFirst().orElseThrow(()->new IllegalStateException("Missing native bullet field "+name));
        }
        void freezeDamage() {
            try {
                Vec3 origin=(Vec3)field("startPos").get(this);
                @SuppressWarnings("unchecked") var pairs=(java.util.LinkedList<com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair>)field("damageAmount").get(this);
                if(pairs.isEmpty())throw new IllegalStateException("Missing native phantom damage curve");
                List<DamageBand> snapshot=new ArrayList<>();double previous=0;
                for(int i=0;i<pairs.size();i++) {
                    double limit=pairs.get(i).getDistance()==pairs.getFirst().getDistance()?field("distanceAmount").getFloat(this):pairs.get(i).getDistance();
                    // Native first-match semantics can make a later band unreachable after a range buff.
                    if(!(limit>previous))continue;
                    double sample=previous+Math.min(1,(limit-previous)/2);
                    float damage=getDamage(origin.add(sample,0,0));
                    if(!Float.isFinite(damage)||damage<0)throw new IllegalStateException("Invalid native phantom damage");
                    snapshot.add(new DamageBand(limit,damage));previous=limit;
                }
                if(snapshot.isEmpty())throw new IllegalStateException("Empty native phantom curve");
                frozenOrigin=origin;frozen=snapshot.toArray(DamageBand[]::new);
            } catch(IllegalAccessException error){throw new IllegalStateException("Cannot freeze native phantom curve",error);}
        }
        NativeBlast phantomShot(Vec3 origin,Vec3 velocity) {
            if(frozen==null)throw new IllegalStateException("Unfrozen phantom template");
            NativeBlast copy=new NativeBlast(this,1);copy.frozenOrigin=origin;copy.setPos(origin);copy.setDeltaMovement(velocity);
            try {field("startPos").set(copy,origin);}catch(IllegalAccessException error){throw new IllegalStateException(error);}
            return copy;
        }
        @Override public float getDamage(Vec3 point) {
            if(Float.isFinite(fixedDamage))return fixedDamage*hitScale;
            if(frozen!=null){double distance=point.distanceTo(frozenOrigin);for(DamageBand band:frozen)if(distance<band.limit)return band.damage*hitScale;return 0;}
            return super.getDamage(point)*damageScale*hitScale;
        }
        NativeBlast chain(float damage) {
            NativeBlast copy=new NativeBlast(this,1);copy.fixedDamage=Math.max(0,damage);return copy;
        }
        void hit(Entity target,Vec3 point,Vec3 start,Vec3 end) {
            setPos(start);
            // One native non-headshot hit per unique victim, no separate direct-hit damage.
            float previous=hitScale;hitScale=ZeusShock.multiplier(core(target),getGunId(),shockGrip);
            try {super.onHitEntity(new TacHitResult(new EntityKineticBullet.EntityResult(target,point,false)),start,end);}
            finally {hitScale=previous;}
        }
    }
}
