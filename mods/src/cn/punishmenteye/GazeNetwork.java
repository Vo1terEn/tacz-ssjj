package cn.punishmenteye;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GazeNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("eyeofpunishment","gaze"),()->"1","1"::equals,"1"::equals);
    public record Mark(int id,UUID uuid,ResourceLocation dimension,int remaining){
        static Mark read(FriendlyByteBuf b){return new Mark(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readVarInt());}
        void write(FriendlyByteBuf b){b.writeVarInt(id);b.writeUUID(uuid);b.writeResourceLocation(dimension);b.writeVarInt(remaining);}
    }
    public static void init(){
        CHANNEL.messageBuilder(Mark.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Mark::write).decoder(Mark::read)
            .consumerMainThread((mark,ctx)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->GazeVisuals.accept(mark));ctx.get().setPacketHandled(true);}).add();
    }
    static Mark packet(LivingEntity target,int ticks){return new Mark(target.getId(),target.getUUID(),target.level().dimension().location(),ticks);}
    public static void broadcast(LivingEntity target,int ticks){CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->target),packet(target,ticks));}
    public static void send(ServerPlayer viewer,LivingEntity target,int ticks){CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet(target,ticks));}
}
