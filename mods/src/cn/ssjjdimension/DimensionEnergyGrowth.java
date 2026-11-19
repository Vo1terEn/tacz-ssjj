package cn.ssjjdimension;

import cn.judgfire.EnergyAttributes;
import cn.ssjjparasite2.client.ParasiteDummyDamage;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Growth belongs to the player; only its trigger requires a held tier-two Dimension Star. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionEnergyGrowth {
    public static final String KEY="SSJJDimensionEnergyGrowth";
    public static final double CAP=1000;
    public static final int DURATION=1200;
    public static final UUID PENETRATION_ID=UUID.fromString("d62fe312-61b1-47ba-84ce-68f25c151001");
    public static final UUID RESISTANCE_ID=UUID.fromString("d62fe312-61b1-47ba-84ce-68f25c151002");
    private static final String P="Penetration",R="Resistance",PU="PenetrationUntil",RU="ResistanceUntil",DEAD="DeathHalved";
    private static final ThreadLocal<ArrayDeque<Hit>> HITS=new ThreadLocal<>();
    private static final Set<ServerPlayer> PENDING_DEATH=Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Hit {
        final LivingEntity target;final DamageSource source;
        final ParasiteDummyDamage.Snapshot before;
        final ServerPlayer attacker,victim;final double penetration,resistance;
        boolean done;double nestedLoss,accountedLoss;
        Hit(LivingEntity target,DamageSource source){
            this.target=target;this.source=source;
            var owner=owner(source);
            attacker=eligible(owner)&&owner!=target?owner:null;
            victim=target instanceof ServerPlayer p&&eligible(p)?p:null;
            if(attacker!=null)reconcile(attacker);
            if(victim!=null&&victim!=attacker)reconcile(victim);
            penetration=attacker==null?0:EnergyAttributes.penetration(attacker);
            resistance=victim==null?0:EnergyAttributes.resistance(victim);
            before=attacker==null&&victim==null&&!watched(target)?null:ParasiteDummyDamage.capture(target);
        }
    }
    private DimensionEnergyGrowth(){}
    private static boolean watched(LivingEntity target){var stack=HITS.get();if(stack!=null)for(var hit:stack)if(hit.target==target&&hit.before!=null)return true;return false;}
    private static boolean eligible(ServerPlayer player){return player!=null&&player.isAlive()&&!player.isSpectator()&&DimensionField.tier(player.getMainHandItem())==2;}
    private static ServerPlayer owner(DamageSource source){
        if(source==null)return null;
        if(source.getEntity() instanceof ServerPlayer p)return p;
        var direct=source.getDirectEntity();
        for(int i=0;i<4&&direct!=null;i++){
            if(direct instanceof ServerPlayer p)return p;
            if(!(direct instanceof Projectile projectile))break;
            var next=projectile.getOwner();if(next==direct)break;direct=next;
        }
        return null;
    }
    private static long clock(Player player){
        if(!(player.level() instanceof ServerLevel level))return 0;
        var server=level.getServer();return server!=null&&server.overworld()!=null?server.overworld().getGameTime():level.getGameTime();
    }
    private static double finite(double amount){return Double.isFinite(amount)?Math.max(0,Math.min(CAP,amount)):0;}
    private static CompoundTag state(Player player){return player.getPersistentData().getCompound(KEY);}
    private static void save(Player player,CompoundTag tag){player.getPersistentData().put(KEY,tag);}
    private static long deadline(long now){return now>Long.MAX_VALUE-DURATION?Long.MAX_VALUE:now+DURATION;}
    private static void settle(CompoundTag tag,long now,boolean penetration){
        String value=penetration?P:R,timer=penetration?PU:RU;
        double amount=finite(tag.getDouble(value));long until=tag.getLong(timer);
        if(amount>=CAP){
            if(until<=0||until>deadline(now))until=deadline(now);
            else if(until<=now){amount*=.5;until=0;}
        }else until=0;
        tag.putDouble(value,amount);tag.putLong(timer,until);
    }
    private static void sync(ServerPlayer player,CompoundTag tag,boolean penetration){
        var type=penetration?EnergyAttributes.PENETRATION:EnergyAttributes.RESISTANCE;
        if(!type.isPresent()||player.getAttributes()==null)return;
        var attribute=player.getAttribute(type.get());if(attribute==null)return;
        var id=penetration?PENETRATION_ID:RESISTANCE_ID;
        double amount=finite(tag.getDouble(penetration?P:R));var old=attribute.getModifier(id);
        if(old!=null&&(old.getAmount()!=amount||old.getOperation()!=AttributeModifier.Operation.ADDITION)){attribute.removeModifier(id);old=null;}
        if(amount>0&&old==null)attribute.addTransientModifier(new AttributeModifier(id,"Dimension Star temporary energy growth",amount,AttributeModifier.Operation.ADDITION));
    }
    public static void reconcile(ServerPlayer player){
        if(player==null||!(player.level() instanceof ServerLevel)||!player.getPersistentData().contains(KEY))return;
        var tag=state(player);long now=clock(player);settle(tag,now,true);settle(tag,now,false);save(player,tag);
        sync(player,tag,true);sync(player,tag,false);
    }
    public static double bonus(Player player,boolean penetration){
        if(player==null)return 0;if(player instanceof ServerPlayer p)reconcile(p);
        return finite(state(player).getDouble(penetration?P:R));
    }
    public static long remainingTicks(Player player,boolean penetration){
        if(player==null)return 0;if(player instanceof ServerPlayer p)reconcile(p);
        return Math.max(0,Math.min(DURATION,state(player).getLong(penetration?PU:RU)-clock(player)));
    }
    /** Hooked into the existing unified hurt/actuallyHurt scope, before its attribute snapshot. */
    public static void enter(LivingEntity target,DamageSource source,boolean delegation){
        var stack=HITS.get();
        if(delegation&&stack!=null)for(var hit:stack)if(hit.target==target&&hit.source==source){stack.push(hit);return;}
        var hit = target != null && target.level() instanceof ServerLevel ? new Hit(target,source) : new Hit(null,null);
        if (stack == null) HITS.set(stack = new ArrayDeque<>());
        stack.push(hit);
    }
    private static void add(ServerPlayer player,boolean penetration,double current){
        var tag=state(player);String value=penetration?P:R,timer=penetration?PU:RU;long now=clock(player);
        settle(tag,now,penetration);double old=tag.getDouble(value);
        if(old<CAP){
            double gain=20+(Double.isFinite(current)?Math.max(0,current)*.05:0);
            double next=Math.min(CAP,old+gain);tag.putDouble(value,next);
            if(next>=CAP)tag.putLong(timer,deadline(now));
            save(player,tag);sync(player,tag,penetration);
        }
    }
    /** Only actual native health/absorption loss or an accepted infinite-dummy hit grants growth. */
    public static void exit(){
        var stack=HITS.get();if(stack==null||stack.isEmpty())return;
        var hit=stack.pop();if(stack.isEmpty())HITS.remove();
        if(hit.before!=null){
            double loss=ParasiteDummyDamage.confirmedLoss(hit.before,hit.target);
            // A nested independent damage source must not make its enclosing rejected hit look successful.
            double delta=Math.max(0,loss-hit.accountedLoss);hit.accountedLoss=Math.max(hit.accountedLoss,loss);
            if(delta>0&&!stack.isEmpty()){
                var counted=Collections.newSetFromMap(new IdentityHashMap<Hit,Boolean>());
                for(var outer:stack)if(outer!=hit&&outer.target==hit.target&&counted.add(outer))outer.nestedLoss+=delta;
            }
            if(!hit.done&&loss>hit.nestedLoss){
                hit.done=true;
                if(hit.attacker!=null)add(hit.attacker,true,hit.penetration);
                if(hit.victim!=null)add(hit.victim,false,hit.resistance);
            }
        }
        if (!PENDING_DEATH.isEmpty())
            for(var player:new ArrayList<>(PENDING_DEATH))if(!unsettled(player)){PENDING_DEATH.remove(player);halve(player);}
    }
    private static boolean unsettled(ServerPlayer player){var stack=HITS.get();if(stack!=null)for(var hit:stack)if(!hit.done&&(hit.target==player||hit.attacker==player))return true;return false;}
    private static void halve(ServerPlayer player){
        var tag=state(player);if(tag.getBoolean(DEAD))return;
        tag.putDouble(P,finite(tag.getDouble(P))*.5);tag.putDouble(R,finite(tag.getDouble(R))*.5);
        tag.putLong(PU,0);tag.putLong(RU,0);tag.putBoolean(DEAD,true);save(player,tag);sync(player,tag,true);sync(player,tag,false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player))return;
        if(unsettled(player))PENDING_DEATH.add(player);else halve(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var tag=state(event.getOriginal()).copy();
        if(event.isWasDeath()&&!tag.getBoolean(DEAD)){tag.putDouble(P,finite(tag.getDouble(P))*.5);tag.putDouble(R,finite(tag.getDouble(R))*.5);tag.putLong(PU,0);tag.putLong(RU,0);}
        tag.putBoolean(DEAD,false);save(player,tag);PENDING_DEATH.remove(event.getOriginal());reconcile(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)reconcile(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)reconcile(p);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer p)reconcile(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){HITS.remove();PENDING_DEATH.clear();}
}
