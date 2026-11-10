package ssjj.dark;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

/** 技能请求不携带客户端伤害或计时；状态由服务器核定后同步给所属玩家。 */
public final class DarkNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(DarkCrystalMod.ID, "skill"), () -> "3", "3"::equals, "3"::equals);

    public static void register() {
        CHANNEL.messageBuilder(Activate.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buffer) -> {}).decoder(buffer -> new Activate())
            .consumerMainThread((packet, context) -> {
                ServerPlayer player = context.get().getSender();
                if (player != null) DarkSkills.activate(player);
                context.get().setPacketHandled(true);
            }).add();
        CHANNEL.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(State::encode).decoder(State::decode)
            .consumerMainThread((packet, context) -> {
                DarkClientState.accept(packet);
                context.get().setPacketHandled(true);
            }).add();
        CHANNEL.messageBuilder(DarkVisualState.Cast.class, 2, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((v, b) -> { b.writeInt(v.entityId()); b.writeLong(v.playerId().getMostSignificantBits()); b.writeLong(v.playerId().getLeastSignificantBits()); b.writeLong(v.startTick()); b.writeInt(v.chargeTicks()); b.writeLong(v.activeUntil()); b.writeFloat(v.yaw()); b.writeBoolean(v.stopped()); })
            .decoder(b -> new DarkVisualState.Cast(b.readInt(), new java.util.UUID(b.readLong(), b.readLong()), b.readLong(), b.readInt(), b.readLong(), b.readFloat(), b.readBoolean()))
            .consumerMainThread((v, context) -> { DarkVisualState.accept(v); context.get().setPacketHandled(true); }).add();
    }

    public record Activate() {}

    public record State(int charge, int active, int cooldown, int maxActive, int maxCooldown, float shield, float shieldMax) {
        public static void encode(State packet, FriendlyByteBuf buffer) {
            buffer.writeInt(packet.charge); buffer.writeInt(packet.active); buffer.writeInt(packet.cooldown);
            buffer.writeInt(packet.maxActive); buffer.writeInt(packet.maxCooldown);
            buffer.writeFloat(packet.shield); buffer.writeFloat(packet.shieldMax);
        }
        public static State decode(FriendlyByteBuf buffer) {
            return new State(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat());
        }
    }

    public static void send(ServerPlayer player, State state) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state);
    }

    public static void visual(ServerPlayer player, DarkVisualState.Cast cast) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), cast);
    }
    private DarkNetwork() {}
}
