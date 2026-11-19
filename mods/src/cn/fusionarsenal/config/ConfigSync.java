package cn.fusionarsenal.config;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
/** Only gameplay values travel server -> client. Local render options never leave the machine. */
public final class ConfigSync {
 private static final String VERSION = "3";
 private static SimpleChannel channel;
 public record Snapshot(Map<String,Object> values) {
  public Snapshot {values=Collections.unmodifiableMap(new LinkedHashMap<>(values));}
  public static void encode(Snapshot packet,FriendlyByteBuf out){out.writeVarInt(packet.values.size());for(var e:packet.values.entrySet()){out.writeUtf(e.getKey(),128);Object v=e.getValue();if(v instanceof Boolean b){out.writeByte(0);out.writeBoolean(b);}else if(v instanceof Integer n){out.writeByte(1);out.writeInt(n);}else {out.writeByte(2);out.writeDouble(((Number)v).doubleValue());}}}
  public static Snapshot decode(FriendlyByteBuf in) {
   int count = in.readVarInt();
   long expected = ArsenalConfig.defaults().keySet().stream().filter(key -> !key.startsWith("render.")).count();
   if (count != expected) throw new IllegalArgumentException("Invalid config count: " + count + " (expected " + expected + ")");
   var values = new LinkedHashMap<String, Object>();
   for (int index = 0; index < count; index++) {
    String key = in.readUtf(128);
    if (key.startsWith("render.")) throw new IllegalArgumentException("Remote visual config denied");
    Object value = switch (in.readUnsignedByte()) {
     case 0 -> in.readBoolean();
     case 1 -> in.readInt();
     case 2 -> in.readDouble();
     default -> throw new IllegalArgumentException("Invalid config type");
    };
    if (values.putIfAbsent(key, ArsenalConfig.checked(key, value)) != null)
     throw new IllegalArgumentException("Duplicate config key: " + key);
   }
   return new Snapshot(values);
  }
  public static void handle(Snapshot packet,Supplier<NetworkEvent.Context> source){var context=source.get();if(context.getDirection()!=NetworkDirection.PLAY_TO_CLIENT)throw new IllegalArgumentException("Config direction");context.enqueueWork(()->ArsenalConfig.applyServer(packet.values));context.setPacketHandled(true);}
 }
 private ConfigSync(){}
 public static synchronized void init(){if(channel!=null)return;channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("eye_judgment_arsenal","global_config"),()->VERSION,VERSION::equals,VERSION::equals);channel.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
 public static void send(ServerPlayer player){channel.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(ArsenalConfig.serverSnapshot()));}
 public static void sendAll(MinecraftServer server){for(var player:server.getPlayerList().getPlayers())send(player);}
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
 public static final class ServerEvents {
  @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)send(player);}
 }
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
 public static final class ClientEvents {
  @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){ArsenalConfig.clearServer();}
 }
}
