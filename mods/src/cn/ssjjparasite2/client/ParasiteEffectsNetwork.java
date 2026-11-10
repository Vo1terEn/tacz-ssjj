package cn.ssjjparasite2.client;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Visual-only server-to-client hits. Damage and bullet behavior stay native. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ParasiteEffectsNetwork {
    private static final ResourceLocation ID=new ResourceLocation("ssjjparasite2","parasitic_sniper2");
    private static final java.util.Map<EntityKineticBullet,String> profiles=new java.util.WeakHashMap<>();
    // Publish only a fully configured channel. A hit must never race a later
    // lifecycle callback that has not registered its encoder yet.
    private static final SimpleChannel CHANNEL=createChannel();
    private static SimpleChannel createChannel() {
        var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjjparasite2","visual_hits"),()->"3","3"::equals,"3"::equals);
        channel.registerMessage(0,Hit.class,Hit::encode,Hit::decode,(message,context)->{
            var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ParasiteEffectsClient.receive(message)));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(1,DestructionMark.class,DestructionMark::encode,DestructionMark::decode,(message,context)->{
            var ctx=context.get();ctx.enqueueWork(()->{if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ParasiteDestructionClient.receive(message));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    private ParasiteEffectsNetwork(){}
    /** Called by the main mod constructor before Forge locks channel registration. */
    public static void initNetwork(){java.util.Objects.requireNonNull(CHANNEL);}
    /** Preserve the source skin for transient native hit adapters that never join the world. */
    public static void remember(EntityKineticBullet bullet,String profile){
        if(bullet!=null&&profile!=null&&ParasiteEffectProfiles.KEYS.contains(profile))profiles.put(bullet,profile);
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setupEffectsNetwork(FMLCommonSetupEvent e) {
            // Class initialization has already registered the message exactly once.
            initNetwork();
        }
    }
    public record Hit(double x,double y,double z,float nx,float ny,float nz,int seed,String profile) {
        public boolean valid(){return profile!=null&&ParasiteEffectProfiles.KEYS.contains(profile)&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30000000&&Math.abs(y)<=30000000&&Math.abs(z)<=30000000&&Float.isFinite(nx)&&Float.isFinite(ny)&&Float.isFinite(nz)&&nx*nx+ny*ny+nz*nz<1.01f;}
        public static void encode(Hit h,FriendlyByteBuf b){b.writeDouble(h.x);b.writeDouble(h.y);b.writeDouble(h.z);b.writeFloat(h.nx);b.writeFloat(h.ny);b.writeFloat(h.nz);b.writeInt(h.seed);b.writeUtf(h.profile,64);}
        public static Hit decode(FriendlyByteBuf b){return new Hit(b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readInt(),b.readUtf(64));}
    }
    /** Target UUID prevents entity-ID reuse; shooter UUID keeps independent stacks distinct. */
    public record DestructionMark(int target,UUID targetUuid,UUID shooter,int count,boolean exploded,double x,double y,double z,String profile) {
        public boolean valid(){return target>=0&&targetUuid!=null&&shooter!=null&&count>=0&&count<=5&&(!exploded||count==5)
            &&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30000000&&Math.abs(y)<=30000000&&Math.abs(z)<=30000000
            &&profile!=null&&ParasiteShockwave.PROFILES.contains(profile);}
        public static void encode(DestructionMark m,FriendlyByteBuf b){b.writeVarInt(m.target);b.writeUUID(m.targetUuid);b.writeUUID(m.shooter);b.writeByte(m.count);b.writeBoolean(m.exploded);b.writeDouble(m.x);b.writeDouble(m.y);b.writeDouble(m.z);b.writeUtf(m.profile,64);}
        public static DestructionMark decode(FriendlyByteBuf b){return new DestructionMark(b.readVarInt(),b.readUUID(),b.readUUID(),b.readUnsignedByte(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readUtf(64));}
    }
    private static DestructionMark mark(LivingEntity target,UUID shooter,int count,boolean exploded,String profile){
        Vec3 p=target.getBoundingBox().getCenter();return new DestructionMark(target.getId(),target.getUUID(),shooter,count,exploded,p.x,p.y,p.z,profile);
    }
    public static void destruction(LivingEntity target,LivingEntity shooter,int count,boolean exploded,String profile){
        if(shooter!=null)destructionUuid(target,shooter.getUUID(),count,exploded,profile);
    }
    public static void destructionUuid(LivingEntity target,UUID shooter,int count,boolean exploded,String profile){
        if(!(target.level() instanceof ServerLevel)||shooter==null)return;
        var message=mark(target,shooter,count,exploded,profile);if(message.valid())CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->target),message);
    }
    @SubscribeEvent public static void syncDestructionOnTracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer viewer)||!(event.getTarget() instanceof LivingEntity target))return;
        for(var state:ParasiteDestruction.marks(target)){
            var message=mark(target,state.shooter(),state.count(),false,state.profile());if(message.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),message);
        }
    }
    @SubscribeEvent public static void spawned(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide||!(e.getEntity()instanceof EntityKineticBullet bullet)||!ParasiteScopes.family(bullet.getGunId()))return;
        String profile=ParasiteEffectProfiles.base(bullet.getGunId());if(bullet.getOwner()instanceof net.minecraft.world.entity.LivingEntity owner){var stack=owner.getMainHandItem();var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);if(gun!=null&&bullet.getGunId().equals(gun.getGunId(stack)))profile=ParasiteEffectProfiles.key(stack);}
        profiles.put(bullet,profile);
    }
    private static String profile(net.minecraft.world.entity.Entity bullet,ResourceLocation gun){return bullet instanceof EntityKineticBullet b?profiles.getOrDefault(b,ParasiteEffectProfiles.base(gun)):ParasiteEffectProfiles.base(gun);}
    private static void hit(ServerLevel level,Vec3 pos,Vec3 normal,int seed,String profile) {
        if(!ParasiteEffectProfiles.hasImpact(profile))return;
        Vec3 p=pos.add(normal.scale(.025));Hit hit=new Hit(p.x,p.y,p.z,(float)normal.x,(float)normal.y,(float)normal.z,seed,profile);if(!hit.valid())return;
        CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.x,p.y,p.z,64,level.dimension())),hit);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void block(AmmoHitBlockEvent e) {
        if(!ParasiteScopes.family(e.getAmmo().getGunId())||!(e.getLevel()instanceof ServerLevel level))return;
        var d=e.getHitResult().getDirection();hit(level,e.getHitResult().getLocation(),new Vec3(d.getStepX(),d.getStepY(),d.getStepZ()),e.getAmmo().getId()^(int)level.getGameTime(),profile(e.getAmmo(),e.getAmmo().getGunId()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void entity(EntityHurtByGunEvent.Post e) {
        if(!e.getLogicalSide().isServer()||!ParasiteScopes.family(e.getGunId())||!(e.getHurtEntity().level()instanceof ServerLevel level)||e.getAmount()<=0)return;
        Vec3 pos=e.getBullet()==null?e.getHurtEntity().getBoundingBox().getCenter():e.getBullet().position();
        // Post event has no exact intersection field. Clamp the bullet position
        // to the damaged entity's actual bounding box, rather than using aim.
        var box=e.getHurtEntity().getBoundingBox();pos=new Vec3(Math.max(box.minX,Math.min(box.maxX,pos.x)),Math.max(box.minY,Math.min(box.maxY,pos.y)),Math.max(box.minZ,Math.min(box.maxZ,pos.z)));
        Vec3 direction=e.getBullet()==null?Vec3.ZERO:e.getBullet().getDeltaMovement().normalize().scale(-1);hit(level,pos,direction,e.getHurtEntity().getId()^(int)level.getGameTime(),profile(e.getBullet(),e.getGunId()));
    }
}
