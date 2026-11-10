package cn.punishmenteye;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.UUID;

/** Only server-to-client snapshots; clients cannot request a buff or a mark. */
public final class PhantomNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("eyeofpunishment","phantoms"),()->"1","1"::equals,"1"::equals);
    public record Snapshot(int id,UUID uuid,ResourceLocation dimension,double x,double y,double z,float bodyYaw,float pitch) {
        static Snapshot read(FriendlyByteBuf b){return new Snapshot(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat());}
        void write(FriendlyByteBuf b){b.writeVarInt(id);b.writeUUID(uuid);b.writeResourceLocation(dimension);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeFloat(bodyYaw);b.writeFloat(pitch);}
    }
    public static void init(){
        CHANNEL.messageBuilder(Snapshot.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::write).decoder(Snapshot::read)
            .consumerMainThread((message,ctx)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->PhantomVisuals.accept(message));ctx.get().setPacketHandled(true);}).add();
    }
    public static void emit(ServerPlayer p){
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new Snapshot(p.getId(),p.getUUID(),p.level().dimension().location(),p.getX(),p.getY(),p.getZ(),p.yBodyRot,p.getXRot()));
    }
}
