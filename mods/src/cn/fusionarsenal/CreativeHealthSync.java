package cn.fusionarsenal;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Authoritative committed health follows the tracked entity; no client-origin health updates. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class CreativeHealthSync {
    private static SimpleChannel channel;
    private CreativeHealthSync() {}
    public record Snapshot(int entity,UUID uuid,float health,boolean terminal,boolean active) {
        static void encode(Snapshot value,FriendlyByteBuf out){out.writeVarInt(value.entity);out.writeUUID(value.uuid);out.writeFloat(value.health);out.writeBoolean(value.terminal);out.writeBoolean(value.active);}
        static Snapshot decode(FriendlyByteBuf in){return new Snapshot(in.readVarInt(),in.readUUID(),in.readFloat(),in.readBoolean(),in.readBoolean());}
        static void handle(Snapshot value,Supplier<NetworkEvent.Context> supplier){
            NetworkEvent.Context context=supplier.get();
            if(context.getDirection()!=NetworkDirection.PLAY_TO_CLIENT||!Float.isFinite(value.health)||value.health<0)
                throw new IllegalArgumentException("Invalid creative health snapshot");
            context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.accept(value)));
            context.setPacketHandled(true);
        }
    }
    public static synchronized void init(){
        if(channel!=null)return;
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("eye_judgment_arsenal","creative_health"),()->"2","2"::equals,"2"::equals);
        channel.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(LivingEntity target,float health,boolean terminal){
        if(target.level().isClientSide)return;
        init();channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->target),new Snapshot(target.getId(),target.getUUID(),health,terminal,true));
    }
    public static void release(LivingEntity target){
        if(target.level().isClientSide||CreativeHealthState.terminal(target))return;
        float health;
        try{health=target.getHealth();}catch(RuntimeException|LinkageError unavailable){health=Float.NaN;}
        if(!Float.isFinite(health)||health<0)health=cn.judgfire.CreativeHealthBridge.read(target);
        if(!Float.isFinite(health)||health<0)return;
        init();channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->target),new Snapshot(target.getId(),target.getUUID(),health,false,false));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(event.getEntity() instanceof ServerPlayer player&&event.getTarget() instanceof LivingEntity target){
            var state=CreativeHealthState.state(target);
            if(state!=null){init();channel.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(target.getId(),target.getUUID(),state.health,state.terminal,true));}
        }
    }
    private static final class Client {
        static void accept(Snapshot packet){
            var level=net.minecraft.client.Minecraft.getInstance().level;if(level==null)return;
            var entity=level.getEntity(packet.entity);
            if(entity instanceof LivingEntity living&&living.getUUID().equals(packet.uuid))
                if(packet.active)CreativeHealthState.client(living,packet.health,packet.terminal);else CreativeHealthState.releaseClient(living,packet.health);
        }
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(CreativeHealthSync::init);}
    }
}
