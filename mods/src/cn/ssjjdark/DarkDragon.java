package cn.ssjjdark;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned travelling dragon waves, launched once per accepted light or heavy strike. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DarkDragon {
    private static final List<Wave> ACTIVE=new ArrayList<>();
    private DarkDragon(){}
    private static final class Wave {
        final ServerLevel level;final ServerPlayer owner;final UUID id=UUID.randomUUID();
        final Vec3 direction;final int tier;final String profile;final float damage;final long launchTick;final Set<UUID> visited=new HashSet<>();
        Vec3 pos;double distance;int age;
        Wave(ServerPlayer owner,Vec3 origin,Vec3 direction,int tier){
            this(owner,origin,direction,tier,"darkdiamond"+tier);
        }
        Wave(ServerPlayer owner,Vec3 origin,Vec3 direction,int tier,String profile){
            this.level=owner.serverLevel();this.owner=owner;this.pos=origin;this.direction=direction;
            this.tier=tier;this.profile=profile;this.damage=(float)(DarkDragonRules.damage(tier)*DarkLiberation.dragonMultiplier(owner));this.launchTick=level.getGameTime();
        }
    }
    public static void launch(ServerPlayer owner,ResourceLocation id){
        if(id==null)return;
        int tier=DarkDragonRules.tier(id.getNamespace(),id.getPath());
        if(DarkDragonRules.count(tier)==0)return;
        var stack=owner.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
        String skin=gun==null?"":DarkSkins.skin(gun.getAttachmentId(stack,AttachmentType.SCOPE));
        String profile=id.getPath()+(skin.isEmpty()?"":"_"+skin);
        Vec3 direction=owner.getLookAngle().normalize(),origin=owner.getEyePosition().add(0,-.25,0);
        Vec3 right=new Vec3(direction.z,0,-direction.x);
        right=right.lengthSqr()<DarkDragonRules.EPSILON?new Vec3(1,0,0):right.normalize();
        for(int i=0;i<DarkDragonRules.count(tier);i++){
            double angle=Math.toRadians(DarkDragonRules.angleDegrees(tier,i));
            // A local fan stays separated even when the player looks straight up or down.
            Vec3 spread=direction.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();
            ACTIVE.add(new Wave(owner,origin,spread,tier,profile));
        }
    }
    private static boolean enemy(ServerPlayer owner,LivingEntity target){
        if(target==owner||!target.isAlive()||target.isRemoved()||target.isSpectator()||!target.isPickable()
            ||target.isPassengerOfSameVehicle(owner)||owner.isAlliedTo(target)||target.isAlliedTo(owner))return false;
        if(target instanceof OwnableEntity pet&&owner.getUUID().equals(pet.getOwnerUUID()))return false;
        if(target instanceof Player player&&(player.isCreative()||owner.getServer()==null
            ||!owner.getServer().isPvpAllowed()||!owner.canHarmPlayer(player)))return false;
        return true;
    }
    private record Contact(LivingEntity target,Vec3 point,double distance){}
    private static List<Contact> contacts(Wave wave,Vec3 end,boolean blocked){
        var contacts=new ArrayList<Contact>();double limit=wave.pos.distanceToSqr(end);
        var extended=end.add(wave.direction.scale(DarkDragonRules.EPSILON));
        var bounds=new AABB(wave.pos,end).inflate(DarkDragonRules.RADIUS);
        for(var target:wave.level.getEntitiesOfClass(LivingEntity.class,bounds,t->enemy(wave.owner,t))){
            if(wave.visited.contains(target.getUUID()))continue;
            var actual=target.getBoundingBox();var box=actual.inflate(DarkDragonRules.RADIUS);
            Vec3 point=box.contains(wave.pos)?wave.pos:box.clip(wave.pos,extended).orElse(null);
            if(point==null)continue;
            double distance=wave.pos.distanceToSqr(point);
            if(distance>limit+DarkDragonRules.EPSILON||blocked&&distance>=limit-DarkDragonRules.EPSILON)continue;
            // An expanded enemy box must not grant hits through a nearby wall.
            Vec3 surface=new Vec3(Mth.clamp(point.x,actual.minX,actual.maxX),Mth.clamp(point.y,actual.minY,actual.maxY),Mth.clamp(point.z,actual.minZ,actual.maxZ));
            var wall=wave.level.clip(new ClipContext(point,surface,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,wave.owner));
            if(wall.getType()!=HitResult.Type.MISS)continue;
            contacts.add(new Contact(target,point,distance));
        }
        contacts.sort(Comparator.comparingDouble(Contact::distance).thenComparingInt(c->c.target.getId()));return contacts;
    }
    /** Never force-load even a side chunk intersected by the wave's finite width. */
    private static boolean loaded(ServerLevel level,Vec3 from,Vec3 to){
        var bounds=new AABB(from,to).inflate(DarkDragonRules.RADIUS);
        int minX=Mth.floor(bounds.minX)>>4,maxX=Mth.floor(bounds.maxX)>>4;
        int minZ=Mth.floor(bounds.minZ)>>4,maxZ=Mth.floor(bounds.maxZ)>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(!level.hasChunkAt(new BlockPos(x*16,Mth.floor(to.y),z*16)))return false;
        return true;
    }
    private static void finish(Wave wave){DarkDragonNetwork.finish(wave.level,wave.id,wave.pos,wave.tier,wave.profile);}
    private static boolean advance(Wave wave){
        if(!wave.owner.isAlive()||wave.owner.isRemoved()||wave.owner.serverLevel()!=wave.level
            ||++wave.age>DarkDragonRules.MAX_AGE){finish(wave);return false;}
        double step=DarkDragonRules.step(wave.distance);
        if(step<=DarkDragonRules.EPSILON){finish(wave);return false;}
        Vec3 end=wave.pos.add(wave.direction.scale(step));
        if(!loaded(wave.level,wave.pos,end)){finish(wave);return false;}
        var wall=wave.level.clip(new ClipContext(wave.pos,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,wave.owner));
        boolean blocked=wall.getType()!=HitResult.Type.MISS;
        if(blocked)end=wall.getLocation();
        for(var contact:contacts(wave,end,blocked))if(wave.visited.add(contact.target.getUUID()))
            DarkCombat.damage(wave.owner,contact.target,wave.damage);
        Vec3 from=wave.pos;wave.distance+=from.distanceTo(end);wave.pos=end;
        if(from.distanceToSqr(end)>DarkDragonRules.EPSILON*DarkDragonRules.EPSILON)DarkDragonNetwork.segment(wave.level,wave.id,from,end,wave.tier,wave.profile);
        if(blocked||wave.distance>=DarkDragonRules.RANGE-DarkDragonRules.EPSILON){finish(wave);return false;}
        return true;
    }
    @SubscribeEvent public static void tickDarkDragon(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||ACTIVE.isEmpty())return;
        var pending=new ArrayList<>(ACTIVE);ACTIVE.clear();
        for(var wave:pending){if(wave.level.getGameTime()<=wave.launchTick){ACTIVE.add(wave);continue;}if(advance(wave))ACTIVE.add(wave);}
    }
    @SubscribeEvent public static void stopDarkDragon(ServerStoppedEvent event){ACTIVE.clear();}
}
