package cn.ssjjdark;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Clients receive only server-clipped wave segments; this channel accepts no client actions. */
public final class DarkDragonNetwork {
    private static final String PROTOCOL="1";
    private static final Set<String> PROFILES=Set.of("darkdiamond1","darkdiamond2","darkdiamond1_pure","darkdiamond2_pure",
        "darkdiamond1_mix","darkdiamond2_mix","darkdiamond1_nightguardian","darkdiamond2_nightguardian");
    private static final SimpleChannel CHANNEL=createChannel();
    private DarkDragonNetwork(){}
    public static void init(){Objects.requireNonNull(CHANNEL);}
    private static SimpleChannel createChannel(){
        var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dark","dragon_seal"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
        channel.registerMessage(0,Effect.class,Effect::write,Effect::read,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{
                if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjdark.client.DarkDragonClient.accept(message));
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    public record Effect(ResourceLocation dimension,UUID id,int type,Vec3 from,Vec3 to,long tick,int tier,String profile){
        public Effect(ResourceLocation dimension,UUID id,int type,Vec3 from,Vec3 to,long tick,int tier){this(dimension,id,type,from,to,tick,tier,"darkdiamond"+tier);}
        public boolean valid(){
            if(dimension==null||id==null||type<0||type>1||!finite(from)||!finite(to)||tick<0||tier<1||tier>2
                ||profile==null||!PROFILES.contains(profile)||!profile.startsWith("darkdiamond"+tier))return false;
            double distance=from.distanceToSqr(to);
            return type==0?distance>0&&distance<=DarkDragonRules.SPEED*DarkDragonRules.SPEED+.00001:distance==0;
        }
        private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30000000&&Math.abs(v.y)<=30000000&&Math.abs(v.z)<=30000000;}
        public static void write(Effect e,FriendlyByteBuf b){b.writeResourceLocation(e.dimension);b.writeUUID(e.id);b.writeVarInt(e.type);vector(b,e.from);vector(b,e.to);b.writeLong(e.tick);b.writeVarInt(e.tier);b.writeUtf(e.profile,48);}
        public static Effect read(FriendlyByteBuf b){return new Effect(b.readResourceLocation(),b.readUUID(),b.readVarInt(),vector(b),vector(b),b.readLong(),b.readVarInt(),b.readUtf(48));}
        private static void vector(FriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
        private static Vec3 vector(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    }
    public static void segment(ServerLevel level,UUID id,Vec3 from,Vec3 to,int tier){emit(level,new Effect(level.dimension().location(),id,0,from,to,level.getGameTime(),tier));}
    public static void finish(ServerLevel level,UUID id,Vec3 pos,int tier){emit(level,new Effect(level.dimension().location(),id,1,pos,pos,level.getGameTime(),tier));}
    public static void segment(ServerLevel level,UUID id,Vec3 from,Vec3 to,int tier,String profile){emit(level,new Effect(level.dimension().location(),id,0,from,to,level.getGameTime(),tier,profile));}
    public static void finish(ServerLevel level,UUID id,Vec3 pos,int tier,String profile){emit(level,new Effect(level.dimension().location(),id,1,pos,pos,level.getGameTime(),tier,profile));}
    private static void emit(ServerLevel level,Effect effect){
        if(!effect.valid())return;
        var pos=effect.from();CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(pos.x,pos.y,pos.z,96,level.dimension())),effect);
    }
}
