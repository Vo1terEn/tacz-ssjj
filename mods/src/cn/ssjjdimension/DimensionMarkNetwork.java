package cn.ssjjdimension;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-confirmed, independent cast-time visual identity for each mark layer. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionMarkNetwork {
    private static final SimpleChannel CHANNEL=createChannel();
    private DimensionMarkNetwork() {}
    public static List<String> profiles() {
        var result=new ArrayList<String>();
        for(int tier=0;tier<=2;tier++)for(String skin:DimensionVisualScope.SKINS)result.add(DimensionVisualScope.profile(tier,skin));
        return List.copyOf(result);
    }
    public static boolean profile(String p) { return p!=null&&profiles().contains(p); }
    private static String legacy(String p) { if(p==null)return "";return "default".equals(p)?"tier2":DimensionVisualScope.isSkin(p)?DimensionVisualScope.profile(2,p):p; }
    private static SimpleChannel createChannel() {
        var c=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dimension","target_marks"),()->"2","2"::equals,"2"::equals);
        c.registerMessage(0,Visual.class,Visual::encode,Visual::decode,(message,context)->{
            var ctx=context.get();
            ctx.enqueueWork(()->{if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->cn.ssjjdimension.client.DimensionMarkEffects.receive(message));});
            ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return c;
    }
    public static void initNetwork() { Objects.requireNonNull(CHANNEL); }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setupDimensionMarkNetwork(FMLCommonSetupEvent e) { initNetwork(); }
    }
    public record Visual(ResourceLocation dimension,int target,UUID uuid,int count,boolean burst,long tick,List<String> layers) {
        public Visual { layers=layers==null?List.of():List.copyOf(layers); }
        public Visual(ResourceLocation dimension,int target,UUID uuid,int count,boolean burst,long tick,String profile) {
            this(dimension,target,uuid,count,burst,tick,Collections.nCopies(Math.max(0,Math.min(5,count)),legacy(profile)));
        }
        public String profile(){return layers.isEmpty()?"tier2":layers.get(0);}
        public boolean valid() { return dimension!=null&&target>=0&&uuid!=null&&count>=0&&count<=5
            &&(!burst||count>0)&&tick>=0&&layers.size()==count&&layers.stream().allMatch(DimensionMarkNetwork::profile); }
        public static void encode(Visual m,FriendlyByteBuf b) {
            b.writeResourceLocation(m.dimension);b.writeVarInt(m.target);b.writeUUID(m.uuid);
            b.writeByte(m.count);b.writeBoolean(m.burst);b.writeLong(m.tick);
            b.writeVarInt(m.layers.size());for(String profile:m.layers)b.writeUtf(profile,16);
        }
        public static Visual decode(FriendlyByteBuf b) {
            var dimension=b.readResourceLocation();int target=b.readVarInt();var uuid=b.readUUID();int count=b.readUnsignedByte();boolean burst=b.readBoolean();long tick=b.readLong();
            int n=b.readVarInt();if(n<0||n>5)throw new IllegalArgumentException("Invalid mark layer count");
            var layers=new ArrayList<String>();for(int i=0;i<n;i++)layers.add(b.readUtf(16));
            return new Visual(dimension,target,uuid,count,burst,tick,layers);
        }
    }
    private static Visual visual(LivingEntity t,List<String> layers,boolean burst) {
        return new Visual(t.level().dimension().location(),t.getId(),t.getUUID(),layers.size(),burst,t.level().getGameTime(),layers);
    }
    private static void send(LivingEntity target,List<String> layers,boolean burst) {
        if(!(target.level() instanceof ServerLevel))return;
        var m=visual(target,layers,burst);
        if(m.valid())CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->target),m);
    }
    public static void state(LivingEntity target,int count) { state(target,count,"default"); }
    public static void state(LivingEntity target,int count,String profile) { state(target,Collections.nCopies(Math.max(0,Math.min(5,count)),legacy(profile))); }
    public static void state(LivingEntity target,List<String> layers) { send(target,layers,false); }
    public static void burst(LivingEntity target,int count) { burst(target,count,"default"); }
    public static void burst(LivingEntity target,int count,String profile) { burst(target,Collections.nCopies(Math.max(0,Math.min(5,count)),legacy(profile))); }
    public static void burst(LivingEntity target,List<String> layers) { send(target,layers,true); }
    @SubscribeEvent public static void trackDimensionMarks(PlayerEvent.StartTracking e) {
        if(!(e.getEntity() instanceof ServerPlayer viewer)||!(e.getTarget() instanceof LivingEntity target))return;
        var layers=DimensionMarks.profiles(target);if(layers.isEmpty())return;
        var m=visual(target,layers,false);if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),m);
    }
}
