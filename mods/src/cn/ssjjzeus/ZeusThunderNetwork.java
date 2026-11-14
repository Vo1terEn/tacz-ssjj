package cn.ssjjzeus;

import cn.ssjjzeus.client.ZeusThunderClient;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-authored visual/sound messages; clients cannot submit damage or charge. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ZeusThunderNetwork {
    private static final String PROTOCOL="3";
    private static final Set<String> PROFILES=Set.of("hand_of_zeuss1","hand_of_zeuss2",
        "hand_of_zeuss1_lava","hand_of_zeuss2_lava","hand_of_zeuss1_galaxy","hand_of_zeuss2_galaxy");
    private static final SimpleChannel CHANNEL=createChannel();
    private ZeusThunderNetwork() {}
    public static void init() { Objects.requireNonNull(CHANNEL); }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) { init(); }
    private static SimpleChannel createChannel() {
        SimpleChannel channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_zeus","thunder"),
            ()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
        channel.registerMessage(0,Effect.class,Effect::write,Effect::read,(message,supplier)->{
            var context=supplier.get();
            context.enqueueWork(()->{if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ZeusThunderClient.accept(message));});
            context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    /** Types: moving orb, impact, removal, source chain, source anchor, release sound. */
    public record Effect(ResourceLocation dimension,int type,UUID id,Vec3 position,Vec3 velocity,int life,long tick,double size,String profile) {
        public Effect(ResourceLocation d,int t,UUID id,Vec3 p,Vec3 v,int life,long tick) {this(d,t,id,p,v,life,tick,1,"hand_of_zeuss1");}
        public Effect(ResourceLocation d,int t,UUID id,Vec3 p,Vec3 v,int life,long tick,double size) {this(d,t,id,p,v,life,tick,size,"hand_of_zeuss1");}
        public boolean valid() {
            if(dimension==null||id==null||type<0||type>5||!finite(position)||!finite(velocity)||tick<0||profile==null||!PROFILES.contains(profile))return false;
            if(type==0)return size==1&&life>=1&&life<=1200&&velocity.lengthSqr()>.0001&&velocity.lengthSqr()<=64+1e-8;
            if(type==3)return size==1&&life>=1&&life<=1200&&velocity.lengthSqr()>.0001&&velocity.lengthSqr()<=64096;
            if(velocity.lengthSqr()!=0)return false;
            if(type==2||type==5)return life==0&&size==1;
            return life>=1&&life<=100&&(size==1||type==1&&Double.isFinite(size)&&size>=.1&&size<=16);
        }
        private static boolean finite(Vec3 v) {return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30000000&&Math.abs(v.y)<=30000000&&Math.abs(v.z)<=30000000;}
        public static void write(Effect v,FriendlyByteBuf b) {
            b.writeResourceLocation(v.dimension);b.writeVarInt(v.type);b.writeUUID(v.id);vector(b,v.position);vector(b,v.velocity);
            b.writeVarInt(v.life);b.writeLong(v.tick);b.writeDouble(v.size);b.writeUtf(v.profile,48);
        }
        public static Effect read(FriendlyByteBuf b) {return new Effect(b.readResourceLocation(),b.readVarInt(),b.readUUID(),vector(b),vector(b),b.readVarInt(),b.readLong(),b.readDouble(),b.readUtf(48));}
        private static void vector(FriendlyByteBuf b,Vec3 v) {b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
        private static Vec3 vector(FriendlyByteBuf b) {return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    }
    public static void spawn(ServerLevel l,UUID id,Vec3 p,Vec3 v,int life) {spawn(l,id,p,v,life,"hand_of_zeuss1");}
    public static void spawn(ServerLevel l,UUID id,Vec3 p,Vec3 v,int life,String profile) {emit(l,new Effect(l.dimension().location(),0,id,p,v,life,l.getGameTime(),1,profile));}
    public static void burst(ServerLevel l,UUID id,Vec3 p) {burst(l,id,p,1);}
    public static void burst(ServerLevel l,UUID id,Vec3 p,double size) {burst(l,id,p,size,"hand_of_zeuss1");}
    public static void burst(ServerLevel l,UUID id,Vec3 p,double size,String profile) {emit(l,new Effect(l.dimension().location(),1,id,p,Vec3.ZERO,40,l.getGameTime(),size,profile));}
    public static void remove(ServerLevel l,UUID id,Vec3 p) {emit(l,new Effect(l.dimension().location(),2,id,p,Vec3.ZERO,0,l.getGameTime()));}
    public static void chain(ServerLevel l,Vec3 start,Vec3 end,String profile) {emit(l,new Effect(l.dimension().location(),3,UUID.randomUUID(),start,end.subtract(start),80,l.getGameTime(),1,profile));}
    public static void chainAnchor(ServerLevel l,Vec3 center,String profile) {emit(l,new Effect(l.dimension().location(),4,UUID.randomUUID(),center,Vec3.ZERO,80,l.getGameTime(),1,profile));}
    public static void release(ServerLevel l,Vec3 center,String profile) {emit(l,new Effect(l.dimension().location(),5,UUID.randomUUID(),center,Vec3.ZERO,0,l.getGameTime(),1,profile));}
    private static void emit(ServerLevel l,Effect effect) {
        if(!effect.valid())return;Vec3 p=effect.position;
        CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.x,p.y,p.z,164,l.dimension())),effect);
    }
}
