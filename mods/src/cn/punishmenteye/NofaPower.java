package cn.punishmenteye;
import java.util.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.damagesource.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server replaces only this equipped muzzle's native bullet after native ammo consumption. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID)
public final class NofaPower {
 public static final ResourceLocation MUZZLE=new ResourceLocation("eyeofpunishment","nofa_thunder");
 private static final String COUNTER="eyeofpunishment_nofa_shots";
 private static final Map<UUID,View> VIEWS=new HashMap<>();
 private record View(double slope,long tick){}
 private static final List<Ball> BALLS=new ArrayList<>();
 private static final List<Cloud> CLOUDS=new ArrayList<>();
 private static final class Ball {ServerLevel level;UUID owner,weapon,id=UUID.randomUUID();Vec3 pos,velocity;int age;String skin;}
 private record Cloud(ServerLevel level,UUID owner,UUID weapon,UUID id,Vec3 pos,long born,String skin){}
 public static boolean installed(ItemStack s){return s.getItem() instanceof IGun g&&GripRequirement.WEAPON.equals(g.getGunId(s))&&MUZZLE.equals(g.getAttachmentId(s,AttachmentType.MUZZLE));}
 static void view(ServerPlayer p,double slope){if(Double.isFinite(slope)&&slope>=.005&&slope<=.6&&installed(p.getMainHandItem()))VIEWS.put(p.getUUID(),new View(slope,p.serverLevel().getGameTime()));}
 static boolean valid(ServerPlayer p,LivingEntity t){return t.isAlive()&&!t.isSpectator()&&PassivePower.enemy(p,t);}
 static boolean inSight(ServerPlayer p,LivingEntity t,double slope){
  Vec3 eye=p.getEyePosition(),forward=p.getLookAngle(),right=forward.cross(new Vec3(0,1,0)).normalize();
  if(right.lengthSqr()<.01)right=new Vec3(1,0,0);Vec3 up=right.cross(forward).normalize();
  AABB box=t.getBoundingBox();if(box.contains(eye))return true;var points=new ArrayList<double[]>();
  for(double x:new double[]{box.minX,box.maxX})for(double y:new double[]{box.minY,box.maxY})for(double z:new double[]{box.minZ,box.maxZ}){
   Vec3 d=new Vec3(x,y,z).subtract(eye);double depth=d.dot(forward);if(depth<=.001)continue;
   points.add(new double[]{d.dot(right)/depth,d.dot(up)/depth});
  }
  return NofaRules.silhouette(points,slope);
 }
 static DamageSource source(ServerPlayer p,String type){
  var key=ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("eyeofpunishment",type));
  return new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key),p);
 }
 static boolean visible(ServerPlayer p,LivingEntity t){
  // Vanilla hasLineOfSight has a 128-block cutoff. Use uncapped block clipping instead.
  Vec3 a=p.getEyePosition(),b=t.getEyePosition();var level=p.serverLevel();
  int x=(int)Math.floor(a.x/16),z=(int)Math.floor(a.z/16),endX=(int)Math.floor(b.x/16),endZ=(int)Math.floor(b.z/16);
  int sx=Double.compare(b.x,a.x),sz=Double.compare(b.z,a.z);double dx=b.x-a.x,dz=b.z-a.z;
  double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(x+1)*16:x*16)-a.x)/dx,tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(z+1)*16:z*16)-a.z)/dz;
  double stepX=sx==0?Double.POSITIVE_INFINITY:16/Math.abs(dx),stepZ=sz==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
  while(true){if(!level.hasChunk(x,z))return false;if(x==endX&&z==endZ)break;if(tx<tz){x+=sx;tx+=stepX;}else if(tz<tx){z+=sz;tz+=stepZ;}else{x+=sx;z+=sz;tx+=stepX;tz+=stepZ;}}
  return level.clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
 }
 @SubscribeEvent(priority=EventPriority.LOWEST)
 public static void bullet(EntityJoinLevelEvent e){
  if(e.isCanceled()||e.getLevel().isClientSide()||!(e.getEntity() instanceof EntityKineticBullet b)||!GripRequirement.WEAPON.equals(b.getGunId())||!(b.getOwner() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()||!installed(p.getMainHandItem()))return;
  e.setCanceled(true); // No native bullet, tracer damage, block damage or distance falloff remains.
  var stack=p.getMainHandItem();UUID weapon=MuzzleRefund.identify(p,stack);
  var gun=IGun.getIGunOrNull(stack);String skin=SkinRules.key(gun.getGunId(stack),gun.getAttachmentId(stack,AttachmentType.SCOPE));
  long now=p.serverLevel().getGameTime();
  // The validated native weapon has exactly one bullet per shot; ammo was consumed upstream.
  var view=VIEWS.get(p.getUUID());double slope=view!=null&&now-view.tick<40?view.slope:32d/180*Math.tan(Math.toRadians(35));
  var targets=new ArrayList<LivingEntity>();
  boolean active=SkillTimer.active(p);
  for(Entity entity:p.serverLevel().getAllEntities())if(entity instanceof LivingEntity t&&valid(p,t)){
   boolean marked=GazeMarks.marked(t),aimed=marked&&active||inSight(p,t,slope),canSee=marked||aimed&&visible(p,t);
   if(NofaRules.target(marked,active,aimed,canSee))targets.add(t);
  }
  var damage=source(p,"nofa_lightning");
  for(var target:targets){float amount=NofaRules.LIGHTNING;
   if(GazeMarks.marked(target)&&PassivePower.carries(p))amount*=PassiveRules.MULTIPLIER;
   amount=KingHeartRules.lightning(amount,active,target.getHealth(),target.getMaxHealth());
   MuzzleRefund.hit(p,weapon,target.hurt(damage,amount));NofaNetwork.emit(p.serverLevel(),active?4:0,target.getUUID(),target.getBoundingBox().getCenter(),Vec3.ZERO,8,skin);
  }
  int n=NofaRules.count(stack.getOrCreateTag().getInt(COUNTER));stack.getOrCreateTag().putInt(COUNTER,n);
  if(n==0){var ball=new Ball();ball.level=p.serverLevel();ball.owner=p.getUUID();ball.weapon=weapon;ball.skin=skin;ball.pos=p.getEyePosition().add(p.getLookAngle().scale(.6));ball.velocity=p.getLookAngle().scale(NofaTuning.SPEED);BALLS.add(ball);NofaNetwork.emit(ball.level,1,ball.id,ball.pos,ball.velocity,NofaTuning.LIFETIME,skin);}
 }
 private static void cloud(Ball b){var c=new Cloud(b.level,b.owner,b.weapon,b.id,b.pos,b.level.getGameTime(),b.skin);CLOUDS.add(c);NofaNetwork.emit(b.level,2,b.id,b.pos,Vec3.ZERO,NofaRules.DURATION,b.skin);}
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;
  for(var it=BALLS.iterator();it.hasNext();){var b=it.next();var p=b.level.getServer().getPlayerList().getPlayer(b.owner);
   if(p==null||!p.isAlive()||p.serverLevel()!=b.level||b.age++>=NofaTuning.LIFETIME){NofaNetwork.emit(b.level,3,b.id,b.pos,Vec3.ZERO,0);it.remove();continue;}
   Vec3 end=b.pos.add(b.velocity);if(!b.level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))){NofaNetwork.emit(b.level,3,b.id,b.pos,Vec3.ZERO,0);it.remove();continue;}
   var block=b.level.clip(new ClipContext(b.pos,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
   Vec3 hit=block.getType()!=HitResult.Type.MISS?block.getLocation():null;double distance=hit==null?Double.MAX_VALUE:hit.distanceToSqr(b.pos);
   for(var t:b.level.getEntitiesOfClass(LivingEntity.class,new AABB(b.pos,end).inflate(.4),t->valid(p,t))){var clipped=t.getBoundingBox().inflate(.2).clip(b.pos,end);if(clipped.isPresent()&&clipped.get().distanceToSqr(b.pos)<distance){hit=clipped.get();distance=hit.distanceToSqr(b.pos);}}
   if(hit!=null){b.pos=hit;cloud(b);it.remove();}else b.pos=end;
  }
  for(var it=CLOUDS.iterator();it.hasNext();){var c=it.next();var p=c.level.getServer().getPlayerList().getPlayer(c.owner);long age=c.level.getGameTime()-c.born;
   if(p==null||!p.isAlive()||p.serverLevel()!=c.level||age>=NofaRules.DURATION){NofaNetwork.emit(c.level,3,c.id,c.pos,Vec3.ZERO,0);it.remove();continue;}
   if(!NofaRules.pulse(age))continue;
   var sourceRange=CloudRanges.profile(c.skin);if(sourceRange==null)continue;var range=sourceRange.at(age);
   var b=range.bounds();
   // Native entity broad-phase uses strict AABB overlap. A microscopic pad
   // preserves the inclusive contact boundary used by the shared contour.
   var box=new AABB(c.pos.x+b.minX(),c.pos.y+b.minY(),c.pos.z+b.minZ(),c.pos.x+b.maxX(),c.pos.y+b.maxY(),c.pos.z+b.maxZ()).inflate(1e-7);
   for(var t:c.level.getEntitiesOfClass(LivingEntity.class,box,t->valid(p,t))){var hit=t.getBoundingBox();if(range.intersects(hit.minX-c.pos.x,hit.minY-c.pos.y,hit.minZ-c.pos.z,hit.maxX-c.pos.x,hit.maxY-c.pos.y,hit.maxZ-c.pos.z))MuzzleRefund.hit(p,c.weapon,t.hurt(source(p,"nofa_cloud"),NofaRules.CLOUD));}
  }
  VIEWS.entrySet().removeIf(x->{var p=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(x.getKey());return p==null||p.serverLevel().getGameTime()-x.getValue().tick>40;});
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent e){BALLS.clear();CLOUDS.clear();VIEWS.clear();}
 @SubscribeEvent public static void join(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
 @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){VIEWS.remove(p.getUUID());sync(p);}}
 static void sync(ServerPlayer p){for(var b:BALLS)if(b.level==p.serverLevel())NofaNetwork.send(p,1,b.id,b.pos,b.velocity,NofaTuning.LIFETIME-b.age,b.skin);for(var c:CLOUDS)if(c.level==p.serverLevel())NofaNetwork.send(p,2,c.id,c.pos,Vec3.ZERO,(int)(NofaRules.DURATION-(c.level.getGameTime()-c.born)),c.skin);}
}
