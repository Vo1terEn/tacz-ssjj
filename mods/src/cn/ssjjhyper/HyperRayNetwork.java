package cn.ssjjhyper;

import cn.ssjjhyper.client.HyperRayClient;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Only server-authored, scene-clipped visual segments are sent to clients. */
public final class HyperRayNetwork {
    private static final String PROTOCOL="2";
    private static final Set<String> PROFILES=Set.of("hyper_revolver1","hyper_revolver2","hyper_revolver1_totem","hyper_revolver2_totem","hyper_revolver1_star","hyper_revolver2_star","hyper_revolver1_colorful_spar","hyper_revolver2_colorful_spar");
    private static final SimpleChannel CHANNEL=createChannel();
    private HyperRayNetwork(){}
    public static void init(){Objects.requireNonNull(CHANNEL);}
    private static SimpleChannel createChannel(){
        var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_hyper","energy_ray"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
        channel.registerMessage(0,Effect.class,Effect::write,Effect::read,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->HyperRayClient.accept(message));});context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));return channel;
    }
    public record Effect(ResourceLocation dimension,UUID id,int type,Vec3 from,Vec3 to,boolean returning,long tick,String profile,UUID burst,int slot,Vec3 origin){
        public Effect(ResourceLocation dimension,UUID id,int type,Vec3 from,Vec3 to,boolean returning,long tick,String profile){this(dimension,id,type,from,to,returning,tick,profile,id,0,from);}
        public boolean valid(){return dimension!=null&&id!=null&&burst!=null&&slot>=0&&slot<3&&finite(origin)&&type>=0&&type<=3&&finite(from)&&finite(to)&&tick>=0&&profile!=null&&PROFILES.contains(profile)&&(type==0?from.distanceToSqr(to)<=HyperRays.SPEED*HyperRays.SPEED+.001:from.equals(to));}
        private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30000000&&Math.abs(v.y)<=30000000&&Math.abs(v.z)<=30000000;}
        public static void write(Effect e,FriendlyByteBuf b){b.writeResourceLocation(e.dimension);b.writeUUID(e.id);b.writeVarInt(e.type);vector(b,e.from);vector(b,e.to);b.writeBoolean(e.returning);b.writeLong(e.tick);b.writeUtf(e.profile,48);b.writeUUID(e.burst);b.writeVarInt(e.slot);vector(b,e.origin);}
        public static Effect read(FriendlyByteBuf b){return new Effect(b.readResourceLocation(),b.readUUID(),b.readVarInt(),vector(b),vector(b),b.readBoolean(),b.readLong(),b.readUtf(48),b.readUUID(),b.readVarInt(),vector(b));}
        private static void vector(FriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
        private static Vec3 vector(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    }
    public static void segment(ServerLevel level,UUID id,Vec3 from,Vec3 to,boolean returning,String profile){emit(level,new Effect(level.dimension().location(),id,0,from,to,returning,level.getGameTime(),profile));}
    public static void effect(ServerLevel level,UUID id,int type,Vec3 point,String profile){emit(level,new Effect(level.dimension().location(),id,type,point,point,false,level.getGameTime(),profile));}
    public static void segment(ServerLevel level,UUID id,Vec3 from,Vec3 to,boolean returning,String profile,UUID burst,int slot,Vec3 origin){emit(level,new Effect(level.dimension().location(),id,0,from,to,returning,level.getGameTime(),profile,burst,slot,origin));}
    public static void effect(ServerLevel level,UUID id,int type,Vec3 point,String profile,UUID burst,int slot,Vec3 origin){emit(level,new Effect(level.dimension().location(),id,type,point,point,false,level.getGameTime(),profile,burst,slot,origin));}
    private static void emit(ServerLevel level,Effect effect){if(!effect.valid())return;var p=effect.from;CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.x,p.y,p.z,160,level.dimension())),effect);}
}
