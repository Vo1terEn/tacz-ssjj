package cn.punishmenteye;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class NofaNetwork {
 private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("eyeofpunishment","nofa"),()->"6","6"::equals,"6"::equals);
 public record WeaponState(UUID player,ResourceLocation dimension,int active,boolean permanent,int duration){
  public WeaponState(UUID player,ResourceLocation dimension,int active){this(player,dimension,active,false);}
  public WeaponState(UUID player,ResourceLocation dimension,int active,boolean permanent){this(player,dimension,active,permanent,active>TimerRules.BASE_DURATION?TimerRules.STOCK_DURATION:TimerRules.BASE_DURATION);}
  public boolean valid(){return (duration==TimerRules.BASE_DURATION||duration==TimerRules.STOCK_DURATION)&&active>=0&&active<=duration&&(!permanent||active==0);}
  void write(FriendlyByteBuf b){b.writeUUID(player);b.writeResourceLocation(dimension);b.writeVarInt(active);b.writeBoolean(permanent);b.writeVarInt(duration);}
  static WeaponState read(FriendlyByteBuf b){return new WeaponState(b.readUUID(),b.readResourceLocation(),b.readVarInt(),b.readBoolean(),b.readVarInt());}
 }
 public record Effect(ResourceLocation dimension,int type,UUID id,Vec3 pos,Vec3 velocity,int ticks,String skin){
  public Effect {skin=effectSkin(skin);}
  public Effect(ResourceLocation dimension,int type,UUID id,Vec3 pos,Vec3 velocity,int ticks){this(dimension,type,id,pos,velocity,ticks,"");}
  void write(FriendlyByteBuf b){b.writeResourceLocation(dimension);b.writeVarInt(type);b.writeUUID(id);vec(b,pos);vec(b,velocity);b.writeVarInt(ticks);b.writeUtf(skin,16);}
  static Effect read(FriendlyByteBuf b){return new Effect(b.readResourceLocation(),b.readVarInt(),b.readUUID(),vec(b),vec(b),b.readVarInt(),b.readUtf(16));}
  private static String effectSkin(String skin){if(skin==null||skin.isEmpty())return "";for(String key:SkinRules.KEYS)if(key.equals(skin))return key;throw new IllegalArgumentException("Unknown effect skin: "+skin);}
  static void vec(FriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
  static Vec3 vec(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 }
 public static void init(){
  CHANNEL.messageBuilder(Effect.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Effect::write).decoder(Effect::read).consumerMainThread((m,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->NofaVisuals.accept(m));c.get().setPacketHandled(true);}).add();
  CHANNEL.messageBuilder(Float.class,1,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeFloat(m)).decoder(FriendlyByteBuf::readFloat).consumerMainThread((m,c)->{var p=c.get().getSender();if(p!=null)NofaPower.view(p,m);c.get().setPacketHandled(true);}).add();
  CHANNEL.messageBuilder(WeaponState.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(WeaponState::write).decoder(WeaponState::read).consumerMainThread((m,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SkillVisualState.accept(m));c.get().setPacketHandled(true);}).add();
 }
 public static void view(float slope){CHANNEL.sendToServer(slope);}
 static WeaponState state(ServerPlayer p){var state=SkillTimer.snapshot(p);return new WeaponState(p.getUUID(),p.level().dimension().location(),state.active(),state.permanent(),state.duration());}
 static void active(ServerPlayer p){CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),state(p));}
 static void active(ServerPlayer viewer,ServerPlayer owner){CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),state(owner));}
 static void emit(ServerLevel level,int type,UUID id,Vec3 pos,Vec3 velocity,int ticks){CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),new Effect(level.dimension().location(),type,id,pos,velocity,ticks));}
 static void emit(ServerLevel level,int type,UUID id,Vec3 pos,Vec3 velocity,int ticks,String skin){CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),new Effect(level.dimension().location(),type,id,pos,velocity,ticks,skin));}
 static void send(ServerPlayer p,int type,UUID id,Vec3 pos,Vec3 velocity,int ticks){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Effect(p.level().dimension().location(),type,id,pos,velocity,ticks));}
 static void send(ServerPlayer p,int type,UUID id,Vec3 pos,Vec3 velocity,int ticks,String skin){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Effect(p.level().dimension().location(),type,id,pos,velocity,ticks,skin));}
}
