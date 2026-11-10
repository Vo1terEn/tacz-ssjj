package cn.judgfire;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-authoritative control state; no client requests can create or clear a stun. */
public final class StunNetwork {
    private static final SimpleChannel CHANNEL = create();
    private StunNetwork() {}
    private static SimpleChannel create() {
        var channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("judgfire_runtime", "stun_state"), () -> "1", "1"::equals, "1"::equals);
        channel.registerMessage(0, Status.class, Status::encode, Status::decode, (m,s) -> {
            var context = s.get();
            context.enqueueWork(() -> { if (m.ticks >= 0) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StunInput.receive(m)); });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) { init(); }
    }
    public static void init() { java.util.Objects.requireNonNull(CHANNEL); }
    public record Status(ResourceLocation dimension, UUID player, int entityId, int ticks) {
        static void encode(Status m, FriendlyByteBuf b) { b.writeResourceLocation(m.dimension); b.writeUUID(m.player); b.writeVarInt(m.entityId); b.writeVarInt(m.ticks); }
        static Status decode(FriendlyByteBuf b) { return new Status(b.readResourceLocation(), b.readUUID(), b.readVarInt(), b.readVarInt()); }
    }
    static void send(ServerPlayer player, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Status(player.level().dimension().location(), player.getUUID(), player.getId(), ticks));
    }
}
