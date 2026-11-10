package cn.ssjjdimension;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** All positions, damage and cooldowns are chosen by the server. */
public final class DimensionFieldNetwork {
    private static final SimpleChannel CHANNEL=create();
    private DimensionFieldNetwork() {}
    public static void initNetwork() { DimensionStoneCompat.init(); Objects.requireNonNull(CHANNEL); DimensionDecoyNetwork.initNetwork(); }
    private static SimpleChannel create() {
        var c=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dimension","fields"),()->"5","5"::equals,"5"::equals);
        c.registerMessage(0,Action.class,Action::encode,Action::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{
                if(!m.valid()||ctx.getSender()==null)return;
                if(m.cast())DimensionField.cast(ctx.getSender(),m.slot());else DimensionField.toggle(ctx.getSender(),m.slot());
            });ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(1,Status.class,Status::encode,Status::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjdimension.client.DimensionFieldClient.receive(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(2,Visual.class,Visual::encode,Visual::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjdimension.client.DimensionFieldEffects.receive(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return c;
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setupDimensionFields(FMLCommonSetupEvent e) { initNetwork(); }
    }
    public record Action(int slot,boolean cast) {
        public boolean valid(){return slot>=0&&slot<9;}
        public static void encode(Action m,FriendlyByteBuf b){b.writeVarInt(m.slot);b.writeBoolean(m.cast);}
        public static Action decode(FriendlyByteBuf b){return new Action(b.readVarInt(),b.readBoolean());}
    }
    public record Status(ResourceLocation dimension,int slot,int activeTicks,int cooldownTicks,int count,int castWait) {
        public Status(ResourceLocation dimension,int slot,int activeTicks,int cooldownTicks,int count){this(dimension,slot,activeTicks,cooldownTicks,count,0);}
        public boolean valid(){return dimension!=null&&slot>=0&&slot<9&&activeTicks>=0&&activeTicks<=72000&&cooldownTicks>=0&&cooldownTicks<=72000&&count>=0&&count<=16&&castWait>=0&&castWait<=1200;}
        public static void encode(Status m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.slot);b.writeVarInt(m.activeTicks);b.writeVarInt(m.cooldownTicks);b.writeVarInt(m.count);b.writeVarInt(m.castWait);}
        public static Status decode(FriendlyByteBuf b){return new Status(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());}
    }
    public record Visual(ResourceLocation dimension,UUID field,UUID owner,Vec3 center,float radius,int tier,int remainingTicks,boolean impact,boolean remove,boolean restored,String skin,int totalTicks) {
        public Visual(ResourceLocation dimension,UUID field,UUID owner,Vec3 center,float radius,int tier,int remainingTicks,boolean impact,boolean remove,boolean restored){this(dimension,field,owner,center,radius,tier,remainingTicks,impact,remove,restored,"",60);}
        public Visual(ResourceLocation dimension,UUID field,UUID owner,Vec3 center,float radius,int tier,int remainingTicks,boolean impact,boolean remove,boolean restored,String skin){this(dimension,field,owner,center,radius,tier,remainingTicks,impact,remove,restored,skin,60);}
        public boolean valid(){return dimension!=null&&field!=null&&owner!=null&&center!=null&&Double.isFinite(center.x)&&Double.isFinite(center.y)&&Double.isFinite(center.z)
            &&Math.abs(center.x)<=30000000&&Math.abs(center.y)<=30000000&&Math.abs(center.z)<=30000000&&Float.isFinite(radius)&&radius>0&&radius<=64
            &&DimensionVisualScope.isSkin(skin)&&tier>=1&&tier<=2&&totalTicks>=1&&totalTicks<=144000&&remainingTicks>=0&&remainingTicks<=totalTicks&&!(impact&&remove)&&(!restored||tier==2&&!remove);}
        public static void encode(Visual m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeUUID(m.field);b.writeUUID(m.owner);b.writeDouble(m.center.x);b.writeDouble(m.center.y);b.writeDouble(m.center.z);b.writeFloat(m.radius);b.writeByte(m.tier);b.writeVarInt(m.remainingTicks);b.writeBoolean(m.impact);b.writeBoolean(m.remove);b.writeBoolean(m.restored);b.writeUtf(m.skin,16);b.writeVarInt(m.totalTicks);}
        public static Visual decode(FriendlyByteBuf b){return new Visual(b.readResourceLocation(),b.readUUID(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readUnsignedByte(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readUtf(16),b.readVarInt());}
    }
    public static void request(int slot,boolean cast){var m=new Action(slot,cast);if(m.valid())CHANNEL.sendToServer(m);}
    public static void status(ServerPlayer p,int active,int cooldown){
        var m=new Status(p.level().dimension().location(),p.getInventory().selected,active,cooldown,DimensionField.ownedCount(p),DimensionField.castWait(p));
        if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->p),m);
    }
    public static void field(ServerLevel level,UUID field,UUID owner,Vec3 center,float radius,int tier,int remaining){field(level,field,owner,center,radius,tier,remaining,"");}
    public static void field(ServerLevel level,UUID field,UUID owner,Vec3 center,float radius,int tier,int remaining,String skin){field(level,field,owner,center,radius,tier,remaining,skin,60);}
    public static void field(ServerLevel level,UUID field,UUID owner,Vec3 center,float radius,int tier,int remaining,String skin,int totalTicks){send(level,new Visual(level.dimension().location(),field,owner,center,radius,tier,remaining,false,false,tier==2,skin,totalTicks));}
    public static void impact(ServerLevel level,UUID owner,Vec3 center,float radius,int tier,boolean restored){impact(level,owner,center,radius,tier,restored,"");}
    public static void impact(ServerLevel level,UUID owner,Vec3 center,float radius,int tier,boolean restored,String skin){send(level,new Visual(level.dimension().location(),UUID.randomUUID(),owner,center,radius,tier,0,true,false,restored,skin));}
    public static void remove(ServerLevel level,UUID id){
        var m=new Visual(level.dimension().location(),id,new UUID(0,0),Vec3.ZERO,1,1,0,false,true,false);
        // Removals reach existing trackers even when the owner leaves the area.
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),m);
    }
    private static void send(ServerLevel level,Visual m){
        if(m.valid())CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(m.center.x,m.center.y,m.center.z,Math.max(96,DimensionFieldRules.range()+m.radius()),level.dimension())),m);
    }
}
