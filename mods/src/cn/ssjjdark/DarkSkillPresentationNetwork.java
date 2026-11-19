package cn.ssjjdark;

import cn.ssjjdark.client.DarkLiberationPresentation;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Presentation-only S2C channel. No client intent or gameplay action is accepted here. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DarkSkillPresentationNetwork {
    public static final Set<String> PROFILES=Set.of("darkdiamond2","darkdiamond2_pure","darkdiamond2_mix","darkdiamond2_nightguardian");
    private static final SimpleChannel CHANNEL=create();
    private DarkSkillPresentationNetwork(){}
    public static void init(){Objects.requireNonNull(CHANNEL);}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){init();}
    private static SimpleChannel create(){
        var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dark","liberation_presentation"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Cast.class,Cast::write,Cast::read,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{
                if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->DarkLiberationPresentation.accept(message));
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    public record Cast(ResourceLocation dimension,UUID id,UUID owner,int entityId,Vec3 origin,float yaw,long startTick,int chargeTicks,long activeUntil,String profile,boolean phantom,boolean stopped){
        public boolean valid(){
            return dimension!=null&&id!=null&&owner!=null&&entityId>=0&&origin!=null
                &&Double.isFinite(origin.x)&&Double.isFinite(origin.y)&&Double.isFinite(origin.z)
                &&Math.abs(origin.x)<=30000000&&Math.abs(origin.y)<=2048&&Math.abs(origin.z)<=30000000
                &&Float.isFinite(yaw)&&Math.abs(yaw)<=360000&&startTick>=0&&startTick<=Long.MAX_VALUE-360
                &&chargeTicks==60&&activeUntil==startTick+360&&profile!=null&&PROFILES.contains(profile);
        }
        public Cast stopping(){return new Cast(dimension,id,owner,entityId,origin,yaw,startTick,chargeTicks,activeUntil,profile,phantom,true);}
        public static void write(Cast m,FriendlyByteBuf b){
            b.writeResourceLocation(m.dimension);b.writeUUID(m.id);b.writeUUID(m.owner);b.writeVarInt(m.entityId);
            b.writeDouble(m.origin.x);b.writeDouble(m.origin.y);b.writeDouble(m.origin.z);b.writeFloat(m.yaw);
            b.writeLong(m.startTick);b.writeVarInt(m.chargeTicks);b.writeLong(m.activeUntil);b.writeUtf(m.profile,40);b.writeBoolean(m.phantom);b.writeBoolean(m.stopped);
        }
        public static Cast read(FriendlyByteBuf b){
            return new Cast(b.readResourceLocation(),b.readUUID(),b.readUUID(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readLong(),b.readVarInt(),b.readLong(),b.readUtf(40),b.readBoolean(),b.readBoolean());
        }
    }
    public static void start(ServerPlayer player,Cast cast){if(cast.valid())CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->player),cast);}
    public static void player(ServerPlayer player,Cast cast){if(cast.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->player),cast);}
    /** Original observers can stop tracking a freely moving owner before its phantom expires. */
    public static void stop(ServerLevel level,Cast cast){if(cast.valid())CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),cast.stopping());}
}

