package cn.ssjjdimension;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class DimensionDecoyNetwork {
 private static final SimpleChannel CHANNEL=create();
 public static void initNetwork(){Objects.requireNonNull(CHANNEL);}
 private DimensionDecoyNetwork(){}
 private static SimpleChannel create(){
  var c=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dimension","decoy"),()->"3","3"::equals,"3"::equals);
  c.registerMessage(0,Action.class,Action::encode,Action::decode,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid()&&ctx.getSender()!=null)DimensionDecoy.request(ctx.getSender(),m.slot(),m.forward(),m.left());});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  c.registerMessage(1,Status.class,Status::encode,Status::decode,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjdimension.client.DimensionDecoyClient.status(m));});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  c.registerMessage(2,Visual.class,Visual::encode,Visual::decode,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->{if(m.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjdimension.client.DimensionDecoyClient.visual(m));});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  return c;
 }
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
 public static class Setup {@SubscribeEvent public static void setup(FMLCommonSetupEvent e){Objects.requireNonNull(CHANNEL);}}
 public record Action(int slot,int forward,int left){
  public Action(int slot){this(slot,1,0);}
  public boolean valid(){return slot>=0&&slot<9&&forward>=-1&&forward<=1&&left>=-1&&left<=1;}
  public static void encode(Action m,FriendlyByteBuf b){b.writeVarInt(m.slot);b.writeByte(m.forward);b.writeByte(m.left);}
  public static Action decode(FriendlyByteBuf b){return new Action(b.readVarInt(),b.readByte(),b.readByte());}
 }
 public record Status(ResourceLocation dimension,int cooldown,int protection,int dash,float damageFactor,boolean overloaded,boolean recovering){
  public boolean valid(){return dimension!=null&&cooldown>=0&&cooldown<=72000&&protection>=0&&protection<=1200&&dash>=0&&dash<=120&&Float.isFinite(damageFactor)&&damageFactor>=.000001f&&damageFactor<=1;}
  public static void encode(Status m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeVarInt(m.cooldown);b.writeVarInt(m.protection);b.writeVarInt(m.dash);b.writeFloat(m.damageFactor);b.writeBoolean(m.overloaded);b.writeBoolean(m.recovering);}
  public static Status decode(FriendlyByteBuf b){return new Status(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readBoolean(),b.readBoolean());}
 }
 public record Visual(ResourceLocation dimension,UUID id,UUID owner,int entity,Vec3 position,float yaw,int kind,int ticks){
  public boolean valid(){return dimension!=null&&id!=null&&owner!=null&&entity>=0&&DimensionFieldRules.finite(position)&&Float.isFinite(yaw)&&kind>=0&&kind<=6&&ticks>=0&&ticks<=72000;}
  public static void encode(Visual m,FriendlyByteBuf b){b.writeResourceLocation(m.dimension);b.writeUUID(m.id);b.writeUUID(m.owner);b.writeVarInt(m.entity);b.writeDouble(m.position.x);b.writeDouble(m.position.y);b.writeDouble(m.position.z);b.writeFloat(m.yaw);b.writeByte(m.kind);b.writeVarInt(m.ticks);}
  public static Visual decode(FriendlyByteBuf b){return new Visual(b.readResourceLocation(),b.readUUID(),b.readUUID(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readUnsignedByte(),b.readVarInt());}
 }
 public static void request(int slot){request(slot,1,0);}
 public static void request(int slot,int forward,int left){var action=new Action(slot,forward,left);if(action.valid())CHANNEL.sendToServer(action);}
 public static void status(ServerPlayer p){
  var m=new Status(p.level().dimension().location(),DimensionDecoy.cooldown(p),DimensionDecoy.protection(p),DimensionDecoy.dashTicks(p),(float)DimensionOverload.factor(p),DimensionOverload.active(p),DimensionOverload.recovering(p));
  if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->p),m);
 }
 public static void visual(ServerLevel level,DimensionDecoy.Decoy d,int kind,int ticks,int entity){send(level,new Visual(level.dimension().location(),d.id(),d.owner(),entity,d.position(),d.yaw(),kind,ticks));}
 public static void overload(LivingEntity e,boolean active){
  if(!(e.level() instanceof ServerLevel level))return;
  send(level,new Visual(level.dimension().location(),e.getUUID(),e.getUUID(),e.getId(),e.position(),e.getYRot(),active?3:4,0));
  if(e instanceof ServerPlayer p)status(p);
 }
 public static void tracked(LivingEntity target,ServerPlayer viewer){
  if(target.level() instanceof ServerLevel level){
   var m=new Visual(level.dimension().location(),target.getUUID(),target.getUUID(),target.getId(),target.position(),target.getYRot(),3,0);
   if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),m);
  }
 }
 private static Visual cloakMessage(ServerPlayer p,int ticks){return new Visual(p.level().dimension().location(),p.getUUID(),p.getUUID(),p.getId(),p.position(),p.getYRot(),ticks>0?5:6,ticks);}
 public static void cloak(ServerPlayer p,int ticks){if(p.level() instanceof ServerLevel level)send(level,cloakMessage(p,ticks));}
 public static void cloakTo(ServerPlayer target,ServerPlayer viewer,int ticks){var m=cloakMessage(target,ticks);if(m.valid())CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),m);}
 private static void send(ServerLevel level,Visual m){if(m.valid())CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),m);}
}
