package cn.ssjjparasite2.client;

import cn.fusionarsenal.config.ArsenalConfig;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

/** Persistent per-weapon form, switched by the server after the original reload pose. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ParasiteCannon {
 public static final String ACTIVE="ssjjparasite2_cannon",TOKEN="ssjjparasite2_form_token",BULLET="ssjjparasite2_cannon_shot";
 public static final int SWITCH_TICKS=57;
 public static int switchTicks(ItemStack stack){return ParasiteUltimate.installed(stack)?(SWITCH_TICKS+1)/2:SWITCH_TICKS;}
 public static final float DAMAGE=210;
 public static final String ENHANCED="ssjjparasite2_cannon_muzzle";
 public static final String CONFIGURED="ssjjparasite2_configured_shot";
 private static final Map<ServerPlayer,Pending> pending=new WeakHashMap<>();
 private record Pending(ItemStack stack,int slot,UUID token,long until,boolean target){}
 private static final class Network {static final SimpleChannel CHANNEL=createChannel();}
 private static SimpleChannel createChannel(){
  var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjjparasite2","cannon"),()->"1","1"::equals,"1"::equals);
  channel.registerMessage(0,Request.class,(m,b)->b.writeVarInt(m.slot),b->new Request(b.readVarInt()),(m,s)->{var ctx=s.get();ctx.enqueueWork(()->request(ctx.getSender(),m.slot));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
  channel.registerMessage(1,State.class,State::write,State::read,(m,s)->{var ctx=s.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ParasiteCannonClient.accept(m)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  return channel;
 }
 private ParasiteCannon(){}
 /** Called by the main mod constructor before Forge locks channel registration. */
 public static void initNetwork(){java.util.Objects.requireNonNull(Network.CHANNEL);}
 public static boolean eligible(ItemStack stack){var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);return gun!=null&&ParasiteScopes.GUN.equals(gun.getGunId(stack));}
 public static boolean active(ItemStack stack){return eligible(stack)&&stack.hasTag()&&stack.getTag().getBoolean(ACTIVE);}
 public static boolean busy(net.minecraft.world.entity.LivingEntity entity){return entity instanceof ServerPlayer player&&pending.containsKey(player);}
 public static boolean canStart(boolean eligible,boolean alive,boolean spectator,boolean aiming,boolean reloading,boolean busy,long drawCooldown,long shootCooldown){return eligible&&alive&&!spectator&&!aiming&&!reloading&&!busy&&drawCooldown<=0&&shootCooldown<=0;}
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Setup {
  @SubscribeEvent public static void setupCannonNetwork(FMLCommonSetupEvent e){
   initNetwork();
  }
 }
 public record Request(int slot){}
 public record State(int slot,UUID token,boolean active,int remaining){
  public boolean valid(){return slot>=0&&slot<9&&token!=null&&remaining>=0&&remaining<=SWITCH_TICKS;}
  public static void write(State m,FriendlyByteBuf b){b.writeVarInt(m.slot);b.writeUUID(m.token);b.writeBoolean(m.active);b.writeVarInt(m.remaining);}
  public static State read(FriendlyByteBuf b){return new State(b.readVarInt(),b.readUUID(),b.readBoolean(),b.readVarInt());}
 }
 public static void request(int slot){Network.CHANNEL.sendToServer(new Request(slot));}
 private static void send(ServerPlayer player,Pending p,int remaining){Network.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new State(p.slot,p.token,remaining>0?active(p.stack):p.target,remaining));}
 private static void request(ServerPlayer player,int slot){
  if(player==null||slot<0||slot>=9||player.getInventory().selected!=slot)return;
  ItemStack stack=player.getMainHandItem();var op=IGunOperator.fromLivingEntity(player);
  if(!canStart(eligible(stack),player.isAlive(),player.isSpectator(),op.getSynIsAiming()||op.getSynAimingProgress()>.01f,op.getSynReloadState().getStateType()!=com.tacz.guns.api.entity.ReloadState.StateType.NOT_RELOADING,pending.containsKey(player),op.getSynDrawCoolDown(),op.getSynShootCoolDown()))return;
  UUID token=UUID.randomUUID();stack.getOrCreateTag().putUUID(TOKEN,token);
  int duration=switchTicks(stack);
  Pending p=new Pending(stack,slot,token,player.level().getGameTime()+duration,!active(stack));pending.put(player,p);
  player.setSprinting(false);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();send(player,p,duration);
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer player))return;Pending p=pending.get(player);if(p==null)return;
  if(!player.isAlive()||player.isSpectator()||player.getInventory().selected!=p.slot||player.getMainHandItem()!=p.stack||!eligible(p.stack)){pending.remove(player);return;}
  player.setSprinting(false);
  if(player.level().getGameTime()<p.until)return;
  p.stack.getOrCreateTag().putBoolean(ACTIVE,p.target);pending.remove(player);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();send(player,p,0);
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity()instanceof ServerPlayer player)pending.remove(player);}
 @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity()instanceof ServerPlayer player)pending.remove(player);}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity()instanceof ServerPlayer player)pending.remove(player);}
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void shoot(GunShootEvent e){if(busy(e.getShooter())||e.getLogicalSide().isClient()&&Boolean.TRUE.equals(DistExecutor.unsafeCallWhenOn(Dist.CLIENT,()->()->ParasiteCannonClient.blocks(e.getShooter()))))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void reload(GunReloadEvent e){if(active(e.getGunItemStack())||busy(e.getEntity())||e.getLogicalSide().isClient()&&Boolean.TRUE.equals(DistExecutor.unsafeCallWhenOn(Dist.CLIENT,()->()->ParasiteCannonClient.blocks(e.getEntity()))))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGH)public static void projectile(EntityJoinLevelEvent e){
  if(e.isCanceled()||e.getLevel().isClientSide()||!(e.getEntity()instanceof EntityKineticBullet b)||!ParasiteScopes.family(b.getGunId())||!(b.getOwner()instanceof net.minecraft.world.entity.LivingEntity owner))return;
  var stack=owner.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
  if(gun==null||!b.getGunId().equals(gun.getGunId(stack)))return;
  b.getPersistentData().putBoolean(CONFIGURED,true);
  b.getPersistentData().putBoolean(ENHANCED,ParasiteAttachments.enhancedDamage(stack));
  if(active(stack)){b.getPersistentData().putBoolean(BULLET,true);ParasiteShockwave.replace(e,b);}
 }
 /** Only shoot-path ammo queries are wrapped. Stored ammo and inventory remain untouched. */
 public static int shootAmmo(IGun gun,ItemStack stack){int actual=gun.getCurrentAmmoCount(stack);return active(stack)?Math.max(1,actual):actual;}
 public static boolean consumeAmmo(IGunOperator op){return !(op instanceof net.minecraft.world.entity.LivingEntity entity&&active(entity.getMainHandItem()))&&op.consumesAmmoOrNot();}
 public static boolean freeShot(ModernKineticGunScriptAPI api){return active(api.getItemStack());}
 /** Shared native interval feeds client prediction, server gate and gun scripts.
  * Keep the selected trigger mode, while the cannon uses the server's configured rate.
  */
 public static long shotInterval(long original,ItemStack stack){return active(stack)?Math.max(1,Math.round(60000d/ArsenalConfig.integer("parasite.cannon_rpm"))):original;}
 /** Replace source distance damage before native modifiers, headshots, armor and events. */
 public static float damage(float original,ResourceLocation id,net.minecraft.nbt.CompoundTag projectile){
  if(ParasiteScopes.GUN.equals(id)&&projectile.getBoolean(BULLET))return (float)(ArsenalConfig.number("parasite.cannon_damage")*(projectile.getBoolean(ENHANCED)?ArsenalConfig.number("parasite.muzzle_damage_multiplier"):1));
  if(!ParasiteScopes.family(id)||!projectile.getBoolean(CONFIGURED))return original;
  // Native distance pairs already include the exclusive muzzle's original x2.
  // Scale that captured result once, retaining falloff and all native modifiers.
  return (float)(original*(ArsenalConfig.number("parasite.normal_damage")/15d)*(projectile.getBoolean(ENHANCED)?ArsenalConfig.number("parasite.muzzle_damage_multiplier")/2d:1));
 }
 public static float distanceDamage(ExtraDamage.DistanceDamagePair pair,EntityKineticBullet bullet){return damage(pair.getDamage(),bullet.getGunId(),bullet.getPersistentData());}
}
