package cn.ssjjzeus;

import cn.judgfire.BurstStun;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import com.tacz.guns.api.entity.ITargetEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stationary phantoms are server records and client artwork, never targetable entities. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ZeusAdvent {
    public static final ResourceLocation STOCK=new ResourceLocation("ssjj_zeus","thunder_advent_stock");
    public static final String DATA="ssjj_zeus_advent";
    private static final Map<UUID,List<Phantom>> ACTIVE=new HashMap<>();
    private record FocusTarget(Entity core,Entity part) {}
    private static final Map<UUID,FocusTarget> FOCUS=new HashMap<>();
    private static final class Phantom {
        final UUID id=UUID.randomUUID(),owner;final ServerLevel level;final Vec3 position;
        final ItemStack weapon,sourceWeapon;final ZeusThunder.NativeBlast template;final long born;final int lifetime,fireInterval;
        Entity tracked;
        long nextShot;float yaw,pitch,sentYaw,sentPitch;
        Phantom(ServerPlayer player,ItemStack weapon,ItemStack sourceWeapon,ZeusThunder.NativeBlast template,long now){
            owner=player.getUUID();level=player.serverLevel();position=player.position();this.weapon=weapon;this.sourceWeapon=sourceWeapon;
            this.template=template;born=now;lifetime=ZeusAdventRules.lifetime(stock(weapon));
            fireInterval=ZeusAdventRules.fireInterval(IGunOperator.fromLivingEntity(player).getCacheProperty().getCache(GunProperties.ROUNDS_PER_MINUTE));
            nextShot=now;this.yaw=player.getYRot();pitch=player.getXRot();
        }
        Vec3 muzzle(){return position.add(0,1.4,0);}
    }
    private ZeusAdvent() {}
    public static boolean stock(ItemStack stack){
        IGun gun=IGun.getIGunOrNull(stack);
        return ZeusConfig.enabled("stock_enabled")&&gun!=null&&ZeusEnergy.GUN.equals(gun.getGunId(stack))&&STOCK.equals(gun.getAttachmentId(stack,AttachmentType.STOCK));
    }
    private static long now(ServerPlayer player){return player.getServer().overworld().getGameTime();}
    private static ZeusAdventRules.State state(ServerPlayer player,int capacity){
        CompoundTag tag=player.getPersistentData().getCompound(DATA);
        if(!tag.getBoolean("initialized"))return ZeusAdventRules.full(capacity);
        if(tag.getInt("rechargePeriod")!=ZeusAdventRules.recharge()){
            int oldPeriod=tag.getInt("rechargePeriod");if(oldPeriod<=0)oldPeriod=200;
            long progress=Math.max(0,Math.min(oldPeriod-1,tag.getInt("progress")));
            tag.putInt("progress",(int)(progress*ZeusAdventRules.recharge()/oldPeriod));tag.putInt("rechargePeriod",ZeusAdventRules.recharge());
            player.getPersistentData().put(DATA,tag);
        }
        return ZeusAdventRules.normalize(new ZeusAdventRules.State(tag.getInt("charges"),tag.getInt("progress"),capacity),capacity);
    }
    private static void store(ServerPlayer player,ZeusAdventRules.State state){
        CompoundTag tag=player.getPersistentData().getCompound(DATA);
        tag.putBoolean("initialized",true);tag.putInt("charges",state.charges());tag.putInt("progress",state.progress());tag.putInt("capacity",state.capacity());
        tag.putInt("rechargePeriod",ZeusAdventRules.recharge());
        player.getPersistentData().put(DATA,tag);
    }
    static void recover(ServerPlayer player,int ticks){
        if(player==null||!player.isAlive()||player.isRemoved()||player.isSpectator()||ticks<=0)return;
        var held=player.getMainHandItem();var tag=player.getPersistentData().getCompound(DATA);
        int capacity=ZeusEnergy.tierII(held)?ZeusAdventRules.capacity(stock(held)):(tag.contains("capacity")?Math.max(1,Math.min(32,tag.getInt("capacity"))):ZeusAdventRules.capacity(false));
        store(player,ZeusAdventRules.advance(state(player,capacity),capacity,ticks));
        sync(player,false);
    }
    public static int ownedCount(ServerPlayer player){return ACTIVE.getOrDefault(player.getUUID(),List.of()).size();}
    public static boolean eligible(ServerPlayer player,int slot){
        if(!ZeusConfig.enabled("advent_enabled")||!ZeusConfig.enabled("thunder_enabled")||player==null||slot<0||slot>=9||!player.isAlive()||player.isSpectator()||player.isSleeping()
            ||player.getInventory().selected!=slot||!ZeusEnergy.tierII(player.getMainHandItem()))return false;
        var op=IGunOperator.fromLivingEntity(player);var reload=op.getSynReloadState();
        return op.getCacheProperty()!=null&&op.getSynDrawCoolDown()<=0&&!op.getSynIsBolting()&&op.getSynMeleeCoolDown()<=0
            &&(reload==null||!reload.getStateType().isReloading())&&!BurstStun.stunned(player);
    }
    public static void summon(ServerPlayer player,int slot){
        if(!eligible(player,slot))return;
        var held=player.getMainHandItem();boolean enhanced=stock(held);int capacity=ZeusAdventRules.capacity(enhanced),limit=ZeusAdventRules.limit(enhanced);
        long now=now(player);var state=state(player,capacity);var tag=player.getPersistentData().getCompound(DATA);
        long last=tag.contains("lastCast")?tag.getLong("lastCast"):-1;
        // An early animation request must not postpone the first legal cast at tick 37.
        if(last>=0&&now>=last&&now-last<ZeusAdventRules.castInterval())return;
        if(tag.contains("lastRequest")&&now>=tag.getLong("lastRequest")&&now-tag.getLong("lastRequest")<ZeusAdventRules.REQUEST_INTERVAL)return;
        tag.putLong("lastRequest",now);player.getPersistentData().put(DATA,tag);
        if(!ZeusAdventRules.canCast(state,ownedCount(player),limit,now,last)||!ZeusConfig.enabled("replace_oldest_phantom")&&ownedCount(player)>=limit){sync(player,false);return;}
        // Capture while the actual held gun and its native cache still match; reject without spending if unavailable.
        var template=ZeusThunder.capturePhantom(player,held);if(template==null)return;
        ItemStack snapshot=held.copy();if(snapshot.hasTag())snapshot.getTag().remove(ZeusEnergy.ENERGY);
        var phantom=new Phantom(player,snapshot,held,template,now);
        var owned=ACTIVE.computeIfAbsent(player.getUUID(),id->new ArrayList<>());
        // Only replace after native template capture succeeds. Insertion order is the summon order.
        while(owned.size()>=limit)remove(owned.remove(0));
        owned.add(phantom);
        store(player,ZeusAdventRules.spend(state));tag=player.getPersistentData().getCompound(DATA);tag.putLong("lastCast",now);
        send(phantom,now,true,false);sync(player,true);
    }
    private static void sync(ServerPlayer player,boolean cast){
        var held=player.getMainHandItem();if(!ZeusEnergy.tierII(held))return;
        boolean enhanced=stock(held);var state=state(player,ZeusAdventRules.capacity(enhanced));
        ZeusAdventNetwork.status(player,new ZeusAdventNetwork.Status(player.level().dimension().location(),player.getInventory().selected,
            state.charges(),state.capacity(),ownedCount(player),ZeusAdventRules.limit(enhanced),ZeusAdventRules.remaining(state),ZeusAdventRules.recharge(),cast));
    }
    private static void send(Phantom p,long now,boolean summoned,boolean shot){
        ZeusAdventNetwork.ghost(p.level,new ZeusAdventNetwork.Ghost(p.level.dimension().location(),p.id,p.owner,p.position,p.yaw,p.pitch,
            (int)Math.max(0,p.lifetime-(now-p.born)),false,summoned,shot,p.weapon));
        p.sentYaw=p.yaw;p.sentPitch=p.pitch;
    }
    private static void remove(Phantom p){p.template.discard();ZeusAdventNetwork.remove(p.level,p.id,p.owner,p.position);}
    private static void clear(UUID owner){
        var old=ACTIVE.remove(owner);if(old!=null)for(var p:old)remove(p);
        var focus=FOCUS.remove(owner);
        if(focus!=null&&focus.core.level() instanceof ServerLevel level){
            ServerPlayer player=level.getServer().getPlayerList().getPlayer(owner);if(player!=null)ZeusAdventNetwork.focus(player,null);
        }
    }
    static Entity focused(ServerPlayer owner){
        FocusTarget focus=FOCUS.get(owner.getUUID());if(focus==null)return null;
        if(!ZeusConfig.enabled("focus_enabled")){FOCUS.remove(owner.getUUID());ZeusAdventNetwork.focus(owner,null);return null;}
        if(focus.core.level()!=owner.level()||focus.core.isRemoved()||focus.part.isRemoved()||!ZeusThunder.enemy(owner,focus.core)){
            FOCUS.remove(owner.getUUID());ZeusAdventNetwork.focus(owner,null);return null;
        }
        return focus.part;
    }
    /** Single click: resolve the nearest eligible entity before the first solid block from server eye/rotation. */
    public static void focus(ServerPlayer player,int slot){
        if(!ZeusConfig.enabled("focus_enabled")||player==null||slot<0||slot>=9||!player.isAlive()||player.isSpectator()||player.getInventory().selected!=slot
            ||!ZeusEnergy.tierII(player.getMainHandItem())||BurstStun.stunned(player))return;
        long now=now(player);CompoundTag tag=player.getPersistentData().getCompound(DATA);
        if(tag.contains("lastFocus")&&now>=tag.getLong("lastFocus")&&now-tag.getLong("lastFocus")<2)return;
        tag.putLong("lastFocus",now);player.getPersistentData().put(DATA,tag);
        Vec3 start=player.getEyePosition(),end=start.add(player.getViewVector(1).normalize().scale(ZeusAdventRules.focusRange()));
        var block=player.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double nearest=block.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:start.distanceToSqr(block.getLocation());
        Entity selected=null;
        for(Entity part:player.serverLevel().getEntities(player,new AABB(start,end).inflate(.3),e->ZeusThunder.enemy(player,ZeusThunder.core(e)))){
            var box=part.getBoundingBox().inflate(part.getPickRadius());var point=box.contains(start)?Optional.of(start):box.clip(start,end);
            if(point.isEmpty())continue;double distance=start.distanceToSqr(point.get());
            if(distance<nearest){nearest=distance;selected=part;}
        }
        if(selected==null){FOCUS.remove(player.getUUID());ZeusAdventNetwork.focus(player,null);}
        else {Entity core=ZeusThunder.core(selected);FOCUS.put(player.getUUID(),new FocusTarget(core,selected));ZeusAdventNetwork.focus(player,core);}
    }
    static boolean autoTarget(ServerPlayer owner,Entity part){
        Entity core=ZeusThunder.core(part);if(!ZeusThunder.enemy(owner,core))return false;
        if(core instanceof Enemy||core instanceof ITargetEntity||owner.getLastHurtByMob()==core||owner.getLastHurtMob()==core)return true;
        if(core instanceof Mob mob){LivingEntity victim=mob.getTarget();return victim!=null&&(victim==owner||owner.isAlliedTo(victim)
            ||victim instanceof OwnableEntity pet&&owner.getUUID().equals(pet.getOwnerUUID()));}
        return false;
    }
    private static Entity target(ServerPlayer owner,Phantom p){
        Entity focus=focused(owner);
        if(focus!=null){
            Vec3 point=focus.getBoundingBox().getCenter(),origin=p.muzzle();
            return point.distanceToSqr(origin)<=ZeusAdventRules.range()*ZeusAdventRules.range()
                &&p.level.clip(new ClipContext(origin,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()==HitResult.Type.MISS?focus:null;
        }
        Vec3 origin=p.muzzle();Map<UUID,Entity> candidates=new HashMap<>();
        if(p.tracked!=null&&autoTarget(owner,p.tracked)&&p.tracked.level()==p.level&&!p.tracked.isRemoved()
            &&p.tracked.getBoundingBox().getCenter().distanceToSqr(origin)<=ZeusAdventRules.range()*ZeusAdventRules.range()
            &&p.level.clip(new ClipContext(origin,p.tracked.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()==HitResult.Type.MISS)return p.tracked;
        for(Entity part:p.level.getEntities(owner,new AABB(origin,origin).inflate(ZeusAdventRules.range()),e->autoTarget(owner,e))){
            Entity core=ZeusThunder.core(part);Vec3 point=part.getBoundingBox().getCenter();
            if(point.distanceToSqr(origin)>ZeusAdventRules.range()*ZeusAdventRules.range())continue;
            if(p.level.clip(new ClipContext(origin,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS)continue;
            Entity previous=candidates.get(core.getUUID());
            if(previous==null||point.distanceToSqr(origin)<previous.getBoundingBox().getCenter().distanceToSqr(origin))candidates.put(core.getUUID(),part);
        }
        return candidates.values().stream().min(Comparator.comparingDouble((Entity e)->e.getBoundingBox().getCenter().distanceToSqr(origin)).thenComparingInt(Entity::getId)).orElse(null);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        CompoundTag tag=player.getPersistentData().getCompound(DATA);boolean held=ZeusEnergy.tierII(player.getMainHandItem());
        Entity focus=focused(player);if(focus!=null&&player.tickCount%20==0)ZeusAdventNetwork.focus(player,ZeusThunder.core(focus));
        if(!held&&!tag.getBoolean("initialized"))return;
        int capacity=held?ZeusAdventRules.capacity(stock(player.getMainHandItem())):(tag.contains("capacity")?Math.max(1,Math.min(32,tag.getInt("capacity"))):ZeusAdventRules.capacity(false));
        var before=state(player,capacity);var after=ZeusAdventRules.advance(before,capacity,1);store(player,after);
        if(held&&(player.tickCount%20==0||after.charges()!=before.charges()))sync(player,false);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        ZeusAim.prune();
        if(!ZeusConfig.enabled("advent_enabled")||!ZeusConfig.enabled("thunder_enabled")){for(UUID owner:new ArrayList<>(ACTIVE.keySet()))clear(owner);return;}
        for(var group:new ArrayList<>(ACTIVE.entrySet())){
            var list=group.getValue();if(list.isEmpty()){ACTIVE.remove(group.getKey());continue;}
            ServerPlayer owner=list.get(0).level.getServer().getPlayerList().getPlayer(group.getKey());
            if(owner==null||!owner.isAlive()||owner.isSpectator()||owner.serverLevel()!=list.get(0).level){clear(group.getKey());continue;}
            long now=now(owner);var held=owner.getMainHandItem();int cap=ZeusEnergy.tierII(held)?ZeusAdventRules.limit(stock(held)):Math.max(ZeusAdventRules.limit(false),ZeusAdventRules.limit(true));
            while(list.size()>cap)remove(list.remove(0));
            for(Iterator<Phantom> it=list.iterator();it.hasNext();){
                Phantom p=it.next();
                if(now-p.born>=p.lifetime||!p.level.hasChunkAt(net.minecraft.core.BlockPos.containing(p.position))){remove(p);it.remove();continue;}
                boolean shot=false;
                Entity target=target(owner,p);p.tracked=target;
                var aim=target==null?null:ZeusAim.aim(p.level,owner,p.muzzle(),target);
                if(aim!=null){Vec3 direction=aim.direction();
                    p.yaw=(float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90);p.pitch=(float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,direction.y))));
                }
                if(now>=p.nextShot){
                    p.nextShot=now+p.fireInterval;
                    if(aim!=null){
                        ZeusThunder.launchPhantom(p.level,owner,p.weapon,p.sourceWeapon,p.template,p.muzzle(),aim.direction());shot=true;
                    }
                }
                if(ACTIVE.get(owner.getUUID())!=list)break;
                if(shot||now%20==0||now%4==0&&(Math.abs((p.sentYaw-p.yaw+540)%360-180)>.5f||Math.abs(p.sentPitch-p.pitch)>.5f))send(p,now,false,shot);
            }
            if(list.isEmpty())ACTIVE.remove(group.getKey());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){clear(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){clear(event.getEntity().getUUID());}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof Player player)clear(player.getUUID());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(event.getOriginal().getPersistentData().contains(DATA))event.getEntity().getPersistentData().put(DATA,event.getOriginal().getPersistentData().getCompound(DATA).copy());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){ACTIVE.clear();FOCUS.clear();ZeusAim.clear();}
}
