package cn.ssjjzeus;

import cn.ssjjzeus.client.ZeusAdventClient;
import cn.ssjjzeus.client.ZeusFocusClient;
import cn.ssjjzeus.client.ZeusShockClient;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** The only client intent is a hotbar slot. Server chooses every gameplay value. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ZeusAdventNetwork {
    private static final SimpleChannel CHANNEL=create();
    private ZeusAdventNetwork() {}
    public static void init(){Objects.requireNonNull(CHANNEL);}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){init();}
    private static SimpleChannel create(){
        var c=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_zeus","advent"),()->"4","4"::equals,"4"::equals);
        c.registerMessage(0,Action.class,Action::write,Action::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid()&&ctx.getSender()!=null)ZeusAdvent.summon(ctx.getSender(),m.slot);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(1,Status.class,Status::write,Status::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ZeusAdventClient.status(m));});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(2,Ghost.class,Ghost::write,Ghost::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ZeusAdventClient.ghost(m));});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(3,FocusIntent.class,FocusIntent::write,FocusIntent::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid()&&ctx.getSender()!=null)ZeusAdvent.focus(ctx.getSender(),m.slot);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(4,Focus.class,Focus::write,Focus::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ZeusFocusClient.accept(m));});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(5,Shock.class,Shock::write,Shock::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ZeusShockClient.accept(m));});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return c;
    }
    public record Action(int slot){
        public boolean valid(){return slot>=0&&slot<9;}
        public static void write(Action m,FriendlyByteBuf b){b.writeVarInt(m.slot);}
        public static Action read(FriendlyByteBuf b){return new Action(b.readVarInt());}
    }
    public record FocusIntent(int slot){
        public boolean valid(){return slot>=0&&slot<9;}
        public static void write(FocusIntent m,FriendlyByteBuf b){b.writeVarInt(m.slot);}
        public static FocusIntent read(FriendlyByteBuf b){return new FocusIntent(b.readVarInt());}
    }
    public record Focus(ResourceLocation dimension,int targetId,UUID targetUUID){
        public boolean valid(){return dimension!=null&&targetUUID!=null&&(targetId>=0||targetId==-1&&targetUUID.equals(new UUID(0,0)));}
        public static void write(Focus m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeInt(m.targetId);b.writeUUID(m.targetUUID);}
        public static Focus read(FriendlyByteBuf b){return new Focus(b.readResourceLocation(),b.readInt(),b.readUUID());}
    }
    /** Visual state only; the server owns effect duration and damage modifiers. */
    public record Shock(ResourceLocation dimension,int targetId,UUID targetUUID,int remainingTicks,boolean remove){
        public boolean valid(){return dimension!=null&&targetUUID!=null&&targetId>=0&&remainingTicks>=0&&remainingTicks<=72000&&(remove?remainingTicks==0:remainingTicks>0);}
        public static void write(Shock m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.targetId);b.writeUUID(m.targetUUID);b.writeVarInt(m.remainingTicks);b.writeBoolean(m.remove);}
        public static Shock read(FriendlyByteBuf b){return new Shock(b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readVarInt(),b.readBoolean());}
    }
    public record Status(ResourceLocation dimension,int slot,int charges,int maxCharges,int active,int maxActive,int rechargeTicks,int rechargePeriod,boolean cast){
        public Status(ResourceLocation dimension,int slot,int charges,int maxCharges,int active,int maxActive,int rechargeTicks,boolean cast){this(dimension,slot,charges,maxCharges,active,maxActive,rechargeTicks,800,cast);}
        public boolean valid(){return dimension!=null&&slot>=0&&slot<9&&maxCharges>=1&&maxCharges<=32&&charges>=0&&charges<=maxCharges&&active>=0&&active<=16&&maxActive>=1&&maxActive<=16&&rechargePeriod>=1&&rechargePeriod<=72000&&rechargeTicks>=0&&rechargeTicks<=rechargePeriod;}
        public static void write(Status m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.slot);b.writeVarInt(m.charges);b.writeVarInt(m.maxCharges);b.writeVarInt(m.active);b.writeVarInt(m.maxActive);b.writeVarInt(m.rechargeTicks);b.writeVarInt(m.rechargePeriod);b.writeBoolean(m.cast);}
        public static Status read(FriendlyByteBuf b){return new Status(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());}
    }
    public record Ghost(ResourceLocation dimension,UUID id,UUID owner,Vec3 position,float yaw,float pitch,int remainingTicks,boolean remove,boolean summoned,boolean shot,ItemStack weapon){
        public boolean valid(){return dimension!=null&&id!=null&&owner!=null&&position!=null&&Double.isFinite(position.x)&&Double.isFinite(position.y)&&Double.isFinite(position.z)
            &&Math.abs(position.x)<=30000000&&Math.abs(position.y)<=2048&&Math.abs(position.z)<=30000000&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&Math.abs(yaw)<=360000&&Math.abs(pitch)<=90
            &&remainingTicks>=0&&remainingTicks<=72000&&(!remove||!summoned&&!shot)&&weapon!=null&&(remove||ZeusEnergy.tierII(weapon));}
        public static void write(Ghost m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeUUID(m.id);b.writeUUID(m.owner);b.writeDouble(m.position.x);b.writeDouble(m.position.y);b.writeDouble(m.position.z);b.writeFloat(m.yaw);b.writeFloat(m.pitch);b.writeVarInt(m.remainingTicks);b.writeBoolean(m.remove);b.writeBoolean(m.summoned);b.writeBoolean(m.shot);b.writeItem(m.weapon);}
        public static Ghost read(FriendlyByteBuf b){return new Ghost(b.readResourceLocation(),b.readUUID(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readItem());}
    }
    public static void shock(ServerLevel level,net.minecraft.world.entity.Entity target,int remainingTicks,boolean remove){
        Shock packet=new Shock(level.dimension().location(),target.getId(),target.getUUID(),remainingTicks,remove);
        if(packet.valid())CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(target.getX(),target.getY(),target.getZ(),96,level.dimension())),packet);
    }
    public static void shockTo(ServerPlayer player,net.minecraft.world.entity.Entity target,int remainingTicks){
        Shock packet=new Shock(target.level().dimension().location(),target.getId(),target.getUUID(),remainingTicks,false);
        if(packet.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);
    }
    public static void request(int slot){Action m=new Action(slot);if(m.valid())CHANNEL.sendToServer(m);}
    public static void requestFocus(int slot){FocusIntent m=new FocusIntent(slot);if(m.valid())CHANNEL.sendToServer(m);}
    public static void focus(ServerPlayer player,net.minecraft.world.entity.Entity entity){
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Focus(player.level().dimension().location(),entity==null?-1:entity.getId(),entity==null?new UUID(0,0):entity.getUUID()));
    }
    public static void status(ServerPlayer player,Status m){if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->player),m);}
    public static void ghost(ServerLevel level,Ghost m){if(m.valid())CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(m.position.x,m.position.y,m.position.z,96,level.dimension())),m);}
    public static void remove(ServerLevel level,UUID id,UUID owner,Vec3 position){CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),new Ghost(level.dimension().location(),id,owner,position,0,0,0,true,false,false,ItemStack.EMPTY));}
}
