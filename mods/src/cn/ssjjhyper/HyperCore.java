package cn.ssjjhyper;

import cn.judgfire.BurstStun;
import cn.ssjjparasite2.client.ParasiteDestructionDamage;
import cn.ssjjparasite2.client.ParasiteDestructionTargets;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server-owned orbs, charged by other weapons and settled on returning to Hyper Armor. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperCore {
    public static final ResourceLocation STOCK=new ResourceLocation("ssjj_hyper","energy_core_stock");
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
    private static final class Orb {
        final UUID target;final boolean red;final float cap;float charge;
        Orb(LivingEntity target,boolean enhanced){this.target=target.getUUID();red=red(target);cap=HyperCoreRules.capacity(red,enhanced);}
    }
    private static final class Session {
        final ServerLevel level;final ItemStack stack;final boolean enhanced;final String profile;
        final Map<UUID,Orb> orbs=new LinkedHashMap<>();
        float fov,aspect;
        ItemStack returnStack;int returnSlot;String returnProfile;UUID returnId;long returnStart;boolean impactDone;
        Session(ServerPlayer p,float fov,float aspect){level=(ServerLevel)p.level();stack=p.getMainHandItem();enhanced=enhanced(stack);profile=appearance(stack);this.fov=fov;this.aspect=aspect;}
    }
    private HyperCore(){}
    private static long clock(ServerPlayer p){var level=(ServerLevel)p.level();var server=level.getServer();return server!=null&&server.overworld()!=null?server.overworld().getGameTime():level.getGameTime();}
    public static int cooldownTicks(ServerPlayer p){
        if(p==null||!(p.level() instanceof ServerLevel))return 0;long now=clock(p),until=COOLDOWNS.getOrDefault(p.getUUID(),0L);
        if(until<=now){COOLDOWNS.remove(p.getUUID());return 0;}return (int)Math.min(72000,until-now);
    }
    public static boolean enhanced(ItemStack stack){var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);return HyperConfig.enabled("stock_enabled")&&gun!=null&&HyperCharge.tier(stack)>=1&&STOCK.equals(gun.getAttachmentId(stack,AttachmentType.STOCK));}
    public static String appearance(ItemStack stack){var gun=IGun.getIGunOrNull(stack);if(gun==null||HyperCharge.tier(stack)<0)return "hyper_revolver1";String skin=HyperScopes.skin(gun.getAttachmentId(stack,AttachmentType.SCOPE));return gun.getGunId(stack).getPath()+(skin.isEmpty()?"":"_"+skin);}
    public static boolean active(ServerPlayer p){var s=p==null?null:SESSIONS.get(p.getUUID());return charging(p,s);}
    public static int count(ServerPlayer p){var s=p==null?null:SESSIONS.get(p.getUUID());return charging(p,s)?s.orbs.size():0;}
    private static boolean carries(ServerPlayer p,Session s){
        if(!HyperConfig.enabled("core_enabled")||p==null||s==null||!p.isAlive()||p.isSpectator()||p.level()!=s.level||HyperCharge.tier(s.stack)<1)return false;
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i)==s.stack)return true;
        return false;
    }
    private static boolean charging(ServerPlayer p,Session s){return carries(p,s)&&s.returnStack==null&&HyperCharge.tier(p.getMainHandItem())<0;}
    private static boolean returnBound(ServerPlayer p,Session s){return carries(p,s)&&s.returnStack!=null&&p.getMainHandItem()==s.returnStack&&p.getInventory().selected==s.returnSlot&&appearance(s.returnStack).equals(s.returnProfile);}
    public static boolean returning(LivingEntity entity){return entity instanceof ServerPlayer p&&returnBound(p,SESSIONS.get(p.getUUID()));}
    private static boolean ready(ServerPlayer p){
        var op=IGunOperator.fromLivingEntity(p);if(op==null||op.getSynDrawCoolDown()>0||op.getSynIsBolting()||op.getSynMeleeCoolDown()>0||BurstStun.stunned(p))return false;
        var reload=op.getSynReloadState();return reload==null||!reload.getStateType().isReloading();
    }
    public static void activate(ServerPlayer p,int slot,float fov,float aspect){
        if(!HyperConfig.enabled("core_enabled")||p==null||slot<0||slot>=9||p.getInventory().selected!=slot||!p.isAlive()||p.isSpectator()||!(p.level() instanceof ServerLevel)
            ||HyperCharge.tier(p.getMainHandItem())<1||!HyperChargeRules.validView(fov,aspect)||!ready(p))return;
        int next=-1;
        for(int i=0;i<9;i++){var stack=p.getInventory().getItem(i);if(!stack.isEmpty()&&HyperCharge.tier(stack)<0){next=i;break;}}
        if(next<0){p.displayClientMessage(Component.literal("【聚能核心】快捷栏需要至少一件非聚能装甲物品"),true);return;}
        // A rapid return + G must play the same gesture instead of bypassing its impact deadline.
        var previous=SESSIONS.get(p.getUUID());if(carries(p,previous)){if(previous.returnStack==null)beginReturn(p,previous);return;}else clear(p);
        int remaining=cooldownTicks(p);if(remaining>0){p.displayClientMessage(Component.literal("【聚能核心】冷却中："+((remaining+19)/20)+"秒"),true);return;}
        HyperCharge.interrupt(p,slot);
        var s=new Session(p,fov,aspect);SESSIONS.put(p.getUUID(),s);
        p.getInventory().selected=next;
        if(p.connection!=null)p.connection.send(new ClientboundSetCarriedItemPacket(next));
        var op=IGunOperator.fromLivingEntity(p);if(op!=null)op.draw(p::getMainHandItem);
        HyperCoreNetwork.state(p,true,0,next);
    }
    /** Only the sender's own active session accepts validated camera updates. */
    public static void view(ServerPlayer p,float fov,float aspect){
        if(p==null||!HyperChargeRules.validView(fov,aspect))return;var s=SESSIONS.get(p.getUUID());if(!carries(p,s))return;s.fov=fov;s.aspect=aspect;
    }
    private static boolean enemy(LivingEntity owner,LivingEntity t){
        if(owner==null||t==null||owner==t||!t.isAlive()||t.isRemoved()||t.isSpectator()||t instanceof ArmorStand||!t.isAttackable()||t.isInvulnerable()
            ||t.isPassengerOfSameVehicle(owner)||owner.isAlliedTo(t)||t.isAlliedTo(owner))return false;
        if(t instanceof OwnableEntity pet&&owner.getUUID().equals(pet.getOwnerUUID()))return false;
        if(t instanceof Player player){if(player.isCreative())return false;if(owner instanceof ServerPlayer p&&(!p.canHarmPlayer(player)||p.getServer()==null||!p.getServer().isPvpAllowed()))return false;}
        return true;
    }
    public static boolean red(LivingEntity t){return t instanceof Player||t instanceof EnderDragon||t instanceof WitherBoss||t.getType().is(Tags.EntityTypes.BOSSES)||ParasiteDestructionTargets.hasBossBar(t.getClass());}
    private static LivingEntity target(Session s,UUID id){var e=s.level.getEntity(id);return e instanceof LivingEntity t?t:null;}
    private static Vec3 center(LivingEntity t){return new Vec3(t.getX(),t.getBoundingBox().maxY,t.getZ());}
    private static void emit(ServerPlayer p,Session s,Orb orb,LivingEntity t,int action){HyperCoreNetwork.orb(s.level,p.getUUID(),orb.target,t==null?-1:t.getId(),t==null?p.position():action==2?t.position():center(t),orb.red,orb.charge,orb.cap,action,s.profile);}
    private static void prune(ServerPlayer p,Session s){
        for(var it=s.orbs.values().iterator();it.hasNext();){var orb=it.next();var t=target(s,orb.target);if(t==null||!enemy(p,t)){it.remove();emit(p,s,orb,t,1);}}
    }
    private static LivingEntity attacker(DamageSource source){
        if(source==null)return null;if(source.getEntity() instanceof LivingEntity t)return t;
        Entity e=source.getDirectEntity();for(int i=0;i<4&&e!=null;i++){if(e instanceof LivingEntity t)return t;if(e instanceof Projectile projectile){var next=projectile.getOwner();if(next==e)break;e=next;}else break;}return null;
    }
    private static boolean otherWeapon(ServerPlayer p,DamageSource source){
        Entity direct=source.getDirectEntity();
        if(direct instanceof EntityKineticBullet bullet){var id=bullet.getGunId();return id!=null&&!"ssjj_hyper".equals(id.getNamespace());}
        if(direct instanceof Projectile)return true;
        return !p.getMainHandItem().isEmpty()&&HyperCharge.tier(p.getMainHandItem())<0;
    }
    /** A target is visible when any tested point is inside the camera and scene colliders allow sight. */
    private static boolean visible(ServerPlayer p,LivingEntity t,float fov,float aspect){
        Vec3 eye=p.getEyePosition(),forward=p.getViewVector(1);AABB b=t.getBoundingBox();if(b.contains(eye))return true;
        var points=new ArrayList<Vec3>();points.add(b.getCenter());points.add(t.getEyePosition());
        for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY+.01,b.maxY-.01})for(double z:new double[]{b.minZ,b.maxZ})points.add(new Vec3(x,y,z));
        for(var point:points){if(!HyperChargeRules.viewport(eye,forward,point,fov,aspect))continue;var hit=p.level().clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()==HitResult.Type.MISS||eye.distanceToSqr(hit.getLocation())+1e-6>=eye.distanceToSqr(point))return true;}
        return false;
    }
    /** Charge from final post-armor damage; drain only the attacking marked enemy's own orb. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){
        if(event.isCanceled()||!(event.getAmount()>0)||!Float.isFinite(event.getAmount())||!(event.getEntity().level() instanceof ServerLevel))return;
        var t=event.getEntity();var source=event.getSource();var owner=attacker(source);
        if(t instanceof ServerPlayer p){
            var s=SESSIONS.get(p.getUUID());
            if(HyperConfig.enabled("core_shield_enabled")&&charging(p,s)&&owner!=null&&owner.level()==s.level&&enemy(p,owner)){
                var orb=s.orbs.get(owner.getUUID());
                if(orb!=null&&!visible(p,owner,s.fov,s.aspect)){
                    float absorbed=HyperCoreRules.absorb(orb.charge,event.getAmount());
                    if(absorbed>0){orb.charge=Math.max(0,orb.charge-absorbed);event.setAmount(Math.max(0,event.getAmount()-absorbed));emit(p,s,orb,owner,0);}
                }
            }
        }
        if(!(event.getAmount()>0)||!(owner instanceof ServerPlayer p))return;
        var s=SESSIONS.get(p.getUUID());if(!charging(p,s)||!enemy(p,t)||!otherWeapon(p,source))return;
        prune(p,s);var orb=s.orbs.get(t.getUUID());
        if(orb==null){if(s.orbs.size()>=HyperCoreRules.maxOrbs())return;orb=new Orb(t,s.enhanced);s.orbs.put(t.getUUID(),orb);}
        float chargeDamage=event.getAmount();if(HyperStones.ultimate(s.stack))chargeDamage=(float)Math.min(Float.MAX_VALUE,(double)chargeDamage*HyperConfig.number("ultimate_core_charge_multiplier"));
        orb.charge=HyperCoreRules.charge(orb.charge,chargeDamage,orb.cap);emit(p,s,orb,t,0);HyperCoreNetwork.state(p,true,s.orbs.size(),-1);
    }
    private static void returnSignal(ServerPlayer p,Session s,boolean cancel){HyperCoreNetwork.returning(p,new HyperCoreNetwork.Return(s.level.dimension().location(),s.returnId,p.getUUID(),p.getId(),s.returnSlot,p.position(),s.returnProfile,s.returnStart,HyperCoreRules.RETURN_IMPACT_TICKS,HyperCoreRules.RETURN_DURATION_TICKS,cancel));}
    private static void beginReturn(ServerPlayer p,Session s){
        if(s.returnStack!=null||HyperCharge.tier(p.getMainHandItem())<0)return;
        s.returnStack=p.getMainHandItem();s.returnSlot=p.getInventory().selected;s.returnProfile=appearance(s.returnStack);s.returnId=UUID.randomUUID();s.returnStart=s.level.getGameTime();s.impactDone=false;
        HyperCharge.interrupt(p,s.returnSlot);returnSignal(p,s,false);HyperCoreNetwork.state(p,false,s.orbs.size(),-1);
    }
    private static void cancelReturn(ServerPlayer p,Session s){
        returnSignal(p,s,true);s.returnStack=null;s.returnId=null;
        if(s.impactDone){clear(p);return;}HyperCoreNetwork.state(p,true,s.orbs.size(),-1);
    }
    private static void clear(ServerPlayer p){var s=SESSIONS.remove(p.getUUID());if(s!=null){if(s.returnStack!=null)returnSignal(p,s,true);s.orbs.clear();HyperCoreNetwork.clear(s.level,p.getUUID());HyperCoreNetwork.state(p,false,0,-1);}}
    private static void detonate(ServerPlayer p,Session s){
        prune(p,s);var orbs=new ArrayList<>(s.orbs.values());
        // Settle the batch first; a finishing gesture retains only its action lock.
        s.impactDone=true;s.orbs.clear();HyperCoreNetwork.clear(s.level,p.getUUID());HyperCoreNetwork.state(p,false,0,-1);
        int detonated=0;boolean ability=HyperStones.ability(s.stack);
        for(var orb:orbs){var t=target(s,orb.target);if(t==null||!enemy(p,t))continue;emit(p,s,orb,t,2);
            detonated++;
            // The existing settled-damage path avoids applying armor, resistance and energy scaling a second time.
            if(orb.charge>0)ParasiteDestructionDamage.apply(p,t,orb.charge);
            HyperCharge.refundEnergy(p,HyperCoreRules.refund(s.enhanced));
        }
        if(detonated>0){int ticks=(int)Math.max(0,(long)HyperCoreRules.cooldown()-(ability?(long)HyperConfig.integer("ability_core_cooldown_reduction_ticks")*detonated:0));if(ticks>0)COOLDOWNS.put(p.getUUID(),clock(p)+ticks);else COOLDOWNS.remove(p.getUUID());}
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;var s=SESSIONS.get(p.getUUID());if(s==null)return;
        if(!HyperConfig.enabled("core_enabled")||!carries(p,s)){clear(p);return;}
        if(s.returnStack!=null){
            if(!returnBound(p,s)){cancelReturn(p,s);return;}
            long age=s.level.getGameTime()-s.returnStart;
            if(!s.impactDone&&age>=HyperCoreRules.RETURN_IMPACT_TICKS)detonate(p,s);
            if(age>=HyperCoreRules.RETURN_DURATION_TICKS){SESSIONS.remove(p.getUUID());returnSignal(p,s,true);}return;
        }
        if(HyperCharge.tier(p.getMainHandItem())>=0){beginReturn(p,s);return;}
        prune(p,s);if(p.tickCount%20==0){for(var orb:s.orbs.values()){var t=target(s,orb.target);if(t!=null)emit(p,s,orb,t,0);}HyperCoreNetwork.state(p,true,s.orbs.size(),-1);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent e){if(!e.getLogicalSide().isClient()&&returning(e.getShooter()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void melee(GunMeleeEvent e){if(!e.getLogicalSide().isClient()&&returning(e.getShooter()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reload(GunReloadEvent e){if(!e.getLogicalSide().isClient()&&returning(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer viewer)||!(event.getTarget() instanceof LivingEntity t))return;
        for(var entry:SESSIONS.entrySet()){var s=entry.getValue();if(s.level!=viewer.level())continue;var orb=s.orbs.get(t.getUUID());var owner=s.level.getServer()==null?null:s.level.getServer().getPlayerList().getPlayer(entry.getKey());if(orb!=null&&owner!=null)emit(owner,s,orb,t,0);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){
        if(e.getEntity() instanceof ServerPlayer p){clear(p);COOLDOWNS.remove(p.getUUID());}
        for(var entry:SESSIONS.entrySet()){var s=entry.getValue();var orb=s.orbs.remove(e.getEntity().getUUID());if(orb!=null){var owner=s.level.getServer()==null?null:s.level.getServer().getPlayerList().getPlayer(entry.getKey());if(owner!=null){emit(owner,s,orb,e.getEntity(),1);HyperCoreNetwork.state(owner,true,s.orbs.size(),-1);}}}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){SESSIONS.clear();COOLDOWNS.clear();}
}
