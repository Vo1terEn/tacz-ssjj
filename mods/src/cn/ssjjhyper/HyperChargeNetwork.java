package cn.ssjjhyper;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Clients submit input and bounded view settings, never targets or gameplay values. */
public final class HyperChargeNetwork {
    private static final String PROTOCOL="1";
    private static final Set<String> PROFILES=Set.of("hyper_revolver1","hyper_revolver2","hyper_revolver1_totem","hyper_revolver2_totem","hyper_revolver1_star","hyper_revolver2_star","hyper_revolver1_colorful_spar","hyper_revolver2_colorful_spar");
    private static final SimpleChannel CHANNEL=create();
    private HyperChargeNetwork(){}
    public static void init(){Objects.requireNonNull(CHANNEL);HyperCoreNetwork.init();}
    private static SimpleChannel create(){
        var c=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_hyper","charge"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
        c.registerMessage(0,Action.class,Action::encode,Action::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(!m.valid()||ctx.getSender()==null)return;if(HyperCore.returning(ctx.getSender())&&m.verticalFov!=-1)return;if(m.cast)HyperCharge.cast(ctx.getSender(),m.slot,m.verticalFov,m.aspect);else if(m.verticalFov==-1)HyperCharge.interrupt(ctx.getSender(),m.slot);else HyperCharge.activate(ctx.getSender(),m.slot);});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(1,Status.class,Status::encode,Status::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjhyper.client.HyperChargeClient.receive(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(2,Visual.class,Visual::encode,Visual::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjhyper.client.HyperChargeClient.effect(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));return c;
    }
    public record Action(int slot,boolean cast,float verticalFov,float aspect){
        public boolean valid(){return slot>=0&&slot<9&&Float.isFinite(verticalFov)&&Float.isFinite(aspect)&&(HyperChargeRules.validView(verticalFov,aspect)||(!cast&&verticalFov==-1&&aspect==1));}
        public static void encode(Action m,FriendlyByteBuf b){b.writeVarInt(m.slot);b.writeBoolean(m.cast);b.writeFloat(m.verticalFov);b.writeFloat(m.aspect);}
        public static Action decode(FriendlyByteBuf b){return new Action(b.readVarInt(),b.readBoolean(),b.readFloat(),b.readFloat());}
    }
    public record Status(ResourceLocation dimension,int slot,int activeTicks,int cooldownTicks,int totalTicks,int energy,int marks,int cap,String profile,UUID session){
        public boolean valid(){return dimension!=null&&slot>=0&&slot<9&&totalTicks>=0&&totalTicks<=320&&activeTicks>=0&&activeTicks<=totalTicks&&cooldownTicks>=0&&cooldownTicks<=200&&energy>=0&&energy<=1000&&cap>=0&&cap<=40&&marks>=0&&marks<=cap&&PROFILES.contains(profile)&&session!=null;}
        public static void encode(Status m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.slot);b.writeVarInt(m.activeTicks);b.writeVarInt(m.cooldownTicks);b.writeVarInt(m.totalTicks);b.writeVarInt(m.energy);b.writeVarInt(m.marks);b.writeVarInt(m.cap);b.writeUtf(m.profile,48);b.writeUUID(m.session);}
        public static Status decode(FriendlyByteBuf b){return new Status(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(48),b.readUUID());}
    }
    /** Types: ring, persistent mark, detonation, remove mark, clear owner session, movement-only root, one owner cast action per successful batch. */
    public record Visual(ResourceLocation dimension,int type,UUID id,UUID owner,int target,UUID targetUuid,Vec3 center,float radius,int life,long tick,String profile){
        public boolean valid(){return dimension!=null&&type>=0&&type<=6&&id!=null&&owner!=null&&targetUuid!=null&&HyperChargeRules.finite(center)&&Float.isFinite(radius)&&radius>=0&&radius<=128&&life>=0&&life<=320&&tick>=0&&PROFILES.contains(profile);}
        public static void encode(Visual m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.type);b.writeUUID(m.id);b.writeUUID(m.owner);b.writeVarInt(m.target);b.writeUUID(m.targetUuid);b.writeDouble(m.center.x);b.writeDouble(m.center.y);b.writeDouble(m.center.z);b.writeFloat(m.radius);b.writeVarInt(m.life);b.writeLong(m.tick);b.writeUtf(m.profile,48);}
        public static Visual decode(FriendlyByteBuf b){return new Visual(b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readUUID(),b.readVarInt(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt(),b.readLong(),b.readUtf(48));}
    }
    public static void request(int slot,boolean cast,float verticalFov,float aspect){var m=new Action(slot,cast,verticalFov,aspect);if(m.valid())CHANNEL.sendToServer(m);}
    /** Existing wire layout: activation never uses a negative FOV, so -1 cancels only the sender's session. */
    public static void interrupt(int slot){var m=new Action(slot,false,-1,1);if(m.valid())CHANNEL.sendToServer(m);}
    public static void status(ServerPlayer player,Status m){if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->player),m);}
    public static void visual(ServerLevel level,Visual m){if(m.valid())CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(m.center.x,m.center.y,m.center.z,160,level.dimension())),m);}
    public static void clear(ServerLevel level,Visual m){if(m.valid())CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),m);}
    public static void direct(ServerPlayer player,Visual m){if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->player),m);}
}
