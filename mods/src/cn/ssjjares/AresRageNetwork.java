package cn.ssjjares;

import cn.ssjjares.client.AresRageBoundary;
import cn.ssjjares.client.AresMissileExplosion;
import java.util.Optional;
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
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AresRageNetwork {
    private static final String VERSION = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(AresArmorItems.NAMESPACE, "war_rage"), () -> VERSION, VERSION::equals, VERSION::equals);
    private AresRageNetwork() {}

    private enum Cast { REQUEST }

    public record Boundary(ResourceLocation dimension, int id, UUID owner, double x, double z, int visibleTicks) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeVarInt(id);
            buffer.writeUUID(owner);
            buffer.writeDouble(x);
            buffer.writeDouble(z);
            buffer.writeVarInt(visibleTicks);
        }

        private static Boundary read(FriendlyByteBuf buffer) {
            return new Boundary(buffer.readResourceLocation(), buffer.readVarInt(), buffer.readUUID(),
                buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
        }

        private boolean valid() {
            return id >= 0 && visibleTicks >= 0 && visibleTicks <= 15
                && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000;
        }
    }

    public record Impact(ResourceLocation dimension, Vec3 point, boolean advanced) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeDouble(point.x);
            buffer.writeDouble(point.y);
            buffer.writeDouble(point.z);
            buffer.writeBoolean(advanced);
        }

        private static Impact read(FriendlyByteBuf buffer) {
            return new Impact(buffer.readResourceLocation(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readBoolean());
        }

        private boolean valid() {
            return Math.abs(point.x) <= 30_000_000 && Math.abs(point.y) <= 30_000_000 && Math.abs(point.z) <= 30_000_000;
        }
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        CHANNEL.registerMessage(0, Cast.class, (cast, buffer) -> {}, buffer -> Cast.REQUEST,
            (cast, supplier) -> {
                var context = supplier.get();
                context.enqueueWork(() -> {
                    var sender = context.getSender();
                    if (sender != null) AresRage.activate(sender);
                });
                context.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Boundary.class, Boundary::write, Boundary::read, (message, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> {
                if (message.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AresRageBoundary.receive(message));
            });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Impact.class, Impact::write, Impact::read, (message, supplier) -> {
            var context = supplier.get();
            context.enqueueWork(() -> {
                if (message.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AresMissileExplosion.receive(message));
            });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void request() {
        CHANNEL.sendToServer(Cast.REQUEST);
    }

    static void boundary(ServerPlayer viewer, ServerLevel level, ServerPlayer owner, Vec3 center, int visibleTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
            new Boundary(level.dimension().location(), owner.getId(), owner.getUUID(), center.x, center.z, visibleTicks));
    }

    static void impact(ServerLevel level, Vec3 point, boolean advanced) {
        CHANNEL.send(PacketDistributor.NEAR.with(() ->
            new PacketDistributor.TargetPoint(point.x, point.y, point.z, 128, level.dimension())),
            new Impact(level.dimension().location(), point, advanced));
    }
}
