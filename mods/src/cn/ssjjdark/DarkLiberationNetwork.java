package cn.ssjjdark;

import cn.ssjjdark.client.DarkLiberationClient;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Clients request a slot only. Every cost, time, target and shield value is server-owned. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DarkLiberationNetwork {
    private static final SimpleChannel CHANNEL=create();
    private DarkLiberationNetwork(){}
    public static void init(){Objects.requireNonNull(CHANNEL);}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){init();}
    private static SimpleChannel create(){
        var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dark","absolute_liberation"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Action.class,Action::write,Action::read,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{if(message.valid()&&context.getSender()!=null)DarkLiberation.activate(context.getSender(),message.slot());});context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(1,Status.class,Status::write,Status::read,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->DarkLiberationClient.status(message));});context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    public record Action(int slot){
        public boolean valid(){return slot>=0&&slot<9;}
        public static void write(Action m,FriendlyByteBuf b){b.writeVarInt(m.slot);}
        public static Action read(FriendlyByteBuf b){return new Action(b.readVarInt());}
    }
    public record Status(ResourceLocation dimension,int slot,int chargeTicks,int activeTicks,int cooldownTicks,int invulnerableTicks,boolean core,float shield,float shieldCapacity,boolean cast){
        public boolean valid(){return dimension!=null&&slot>=0&&slot<9&&chargeTicks>=0&&chargeTicks<=60&&activeTicks>=0&&activeTicks<=360
            &&cooldownTicks>=0&&cooldownTicks<=120&&invulnerableTicks>=0&&invulnerableTicks<=200
            &&Float.isFinite(shield)&&Float.isFinite(shieldCapacity)&&shield>=0&&shieldCapacity>=0&&shield<=shieldCapacity;}
        public static void write(Status m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.slot);b.writeVarInt(m.chargeTicks);b.writeVarInt(m.activeTicks);b.writeVarInt(m.cooldownTicks);b.writeVarInt(m.invulnerableTicks);b.writeBoolean(m.core);b.writeFloat(m.shield);b.writeFloat(m.shieldCapacity);b.writeBoolean(m.cast);}
        public static Status read(FriendlyByteBuf b){return new Status(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readFloat(),b.readFloat(),b.readBoolean());}
    }
    public static void request(int slot){var request=new Action(slot);if(request.valid())CHANNEL.sendToServer(request);}
    public static void status(ServerPlayer player,Status status){if(status.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->player),status);}
}

