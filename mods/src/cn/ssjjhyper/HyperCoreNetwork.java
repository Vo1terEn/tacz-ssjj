package cn.ssjjhyper;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Inputs carry only the held slot and camera settings; the server owns all orbs and charge. */
public final class HyperCoreNetwork {
    private static final SimpleChannel CHANNEL=create();
    private HyperCoreNetwork(){}
    public static void init(){Objects.requireNonNull(CHANNEL);}
    private static SimpleChannel create(){
        var c=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_hyper","core"),()->"2","2"::equals,"2"::equals);
        c.registerMessage(0,Input.class,Input::encode,Input::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{var p=ctx.getSender();if(p==null||!m.valid())return;if(m.activate())HyperCore.activate(p,m.slot(),m.fov(),m.aspect());else HyperCore.view(p,m.fov(),m.aspect());});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(1,State.class,State::encode,State::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjhyper.client.HyperCoreClient.receive(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(2,Orb.class,Orb::encode,Orb::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjhyper.client.HyperCoreClient.receive(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(3,Return.class,Return::encode,Return::decode,(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjhyper.client.HyperCoreClient.receive(m));});ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));return c;
    }
    public record Input(int slot,boolean activate,float fov,float aspect){
        public boolean valid(){return slot>=0&&slot<9&&HyperChargeRules.validView(fov,aspect);}
        static void encode(Input m,FriendlyByteBuf b){b.writeVarInt(m.slot);b.writeBoolean(m.activate);b.writeFloat(m.fov);b.writeFloat(m.aspect);}
        static Input decode(FriendlyByteBuf b){return new Input(b.readVarInt(),b.readBoolean(),b.readFloat(),b.readFloat());}
    }
    public record State(ResourceLocation dimension,boolean active,int count,int selectedSlot){
        public boolean valid(){return dimension!=null&&count>=0&&count<=15&&selectedSlot>=-1&&selectedSlot<9;}
        static void encode(State m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeBoolean(m.active);b.writeVarInt(m.count);b.writeInt(m.selectedSlot);}
        static State decode(FriendlyByteBuf b){return new State(b.readResourceLocation(),b.readBoolean(),b.readVarInt(),b.readInt());}
    }
    /** action: 0 upsert, 1 remove, 2 burst, 3 clear the owner's orbs. */
    public record Orb(ResourceLocation dimension,UUID owner,UUID target,int entityId,Vec3 center,boolean red,float charge,float cap,int action,long tick,String profile){
        public boolean valid(){return dimension!=null&&owner!=null&&target!=null&&HyperChargeRules.finite(center)&&Float.isFinite(charge)&&Float.isFinite(cap)&&charge>=0&&cap>=0&&cap<=1000&&charge<=cap&&action>=0&&action<=3&&tick>=0&&HyperCoreNetwork.profile(profile);}
        static void encode(Orb m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeUUID(m.owner);b.writeUUID(m.target);b.writeInt(m.entityId);b.writeDouble(m.center.x);b.writeDouble(m.center.y);b.writeDouble(m.center.z);b.writeBoolean(m.red);b.writeFloat(m.charge);b.writeFloat(m.cap);b.writeVarInt(m.action);b.writeLong(m.tick);b.writeUtf(m.profile,48);}
        static Orb decode(FriendlyByteBuf b){return new Orb(b.readResourceLocation(),b.readUUID(),b.readUUID(),b.readInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readLong(),b.readUtf(48));}
    }
    public static boolean profile(String value){return value!=null&&Set.of("hyper_revolver","hyper_revolver_totem","hyper_revolver_star","hyper_revolver_colorful_spar","hyper_revolver1","hyper_revolver2","hyper_revolver1_totem","hyper_revolver2_totem","hyper_revolver1_star","hyper_revolver2_star","hyper_revolver1_colorful_spar","hyper_revolver2_colorful_spar").contains(value);}
    /** A server timestamp synchronizes the gesture, one return cue and its damage impact. */
    public record Return(ResourceLocation dimension,UUID id,UUID owner,int entityId,int slot,Vec3 center,String profile,long tick,int impactTicks,int durationTicks,boolean cancel){
        public boolean valid(){return dimension!=null&&id!=null&&owner!=null&&slot>=0&&slot<9&&HyperChargeRules.finite(center)&&HyperCoreNetwork.profile(profile)&&tick>=0&&impactTicks>=0&&durationTicks>0&&durationTicks<=40&&impactTicks<=durationTicks;}
        static void encode(Return m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeUUID(m.id);b.writeUUID(m.owner);b.writeInt(m.entityId);b.writeVarInt(m.slot);b.writeDouble(m.center.x);b.writeDouble(m.center.y);b.writeDouble(m.center.z);b.writeUtf(m.profile,48);b.writeLong(m.tick);b.writeVarInt(m.impactTicks);b.writeVarInt(m.durationTicks);b.writeBoolean(m.cancel);}
        static Return decode(FriendlyByteBuf b){return new Return(b.readResourceLocation(),b.readUUID(),b.readUUID(),b.readInt(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readUtf(48),b.readLong(),b.readVarInt(),b.readVarInt(),b.readBoolean());}
    }
    public static void input(int slot,boolean activate,float fov,float aspect){var m=new Input(slot,activate,fov,aspect);if(m.valid())CHANNEL.sendToServer(m);}
    public static void state(ServerPlayer p,boolean active,int count,int slot){var m=new State(p.level().dimension().location(),active,count,slot);if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->p),m);}
    public static void orb(ServerLevel level,UUID owner,UUID target,int entityId,Vec3 center,boolean red,float charge,float cap,int action,String profile){
        var m=new Orb(level.dimension().location(),owner,target,entityId,center,red,charge,cap,action,level.getGameTime(),profile);if(!m.valid())return;
        if(action==0)CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(center.x,center.y,center.z,160,level.dimension())),m);
        else CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),m);
    }
    public static void clear(ServerLevel level,UUID owner){orb(level,owner,new UUID(0,0),-1,Vec3.ZERO,false,0,0,3,"hyper_revolver1");}
    public static void returning(ServerPlayer p,Return m){if(!m.valid())return;CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(m.center.x,m.center.y,m.center.z,160,p.level().dimension())),m);}
}
