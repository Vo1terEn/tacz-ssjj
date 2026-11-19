package cn.ssjjhyper;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned, non-stacking bidirectional hold-time overload for the tier-two family. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperOverload {
    public static final ResourceLocation MAGAZINE=new ResourceLocation("ssjj_hyper","energy_overload_mag");
    private static final int IDLE=0,HYPER=1,OTHER=2,DURATION=200;
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final class State {
        final ItemStack source;final ServerLevel level;final UUID token=UUID.randomUUID();
        long tick,untilA,untilB;int mode,pending,chargeA,chargeB,partialA,partialB,activeA,activeB;boolean enhanced;
        State(ServerPlayer p,ItemStack source,long tick){this.source=source;level=(ServerLevel)p.level();this.tick=tick;mode=mode(p,source);enhanced=enhanced(source);}
    }
    /** Amounts are per mille: 25 = 2.5%; remaining durations are server ticks. */
    public record Snapshot(int chargeA,int chargeB,int activeA,int activeB,int remainingA,int remainingB,boolean enhanced,UUID token){}
    private HyperOverload(){}
    public static boolean enhanced(ItemStack stack){
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);
        return HyperConfig.enabled("magazine_enabled")&&gun!=null&&HyperCharge.tier(stack)==2&&MAGAZINE.equals(gun.getAttachmentId(stack,AttachmentType.EXTENDED_MAG));
    }
    public static boolean weapon(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        if(IGun.getIGunOrNull(stack)!=null)return true;
        var item=stack.getItem();
        return item instanceof ProjectileWeaponItem||item instanceof TridentItem||item instanceof SwordItem||item instanceof AxeItem
            ||!stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }
    private static boolean carries(ServerPlayer p,ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i)==stack)return true;
        return false;
    }
    private static ItemStack source(ServerPlayer p,State old){
        var held=p.getMainHandItem();if(HyperCharge.tier(held)==2&&carries(p,held))return held;
        if(old!=null&&HyperCharge.tier(old.source)==2&&carries(p,old.source))return old.source;
        for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(HyperCharge.tier(stack)==2)return stack;}
        return ItemStack.EMPTY;
    }
    private static int mode(ServerPlayer p,ItemStack source){var held=p.getMainHandItem();return held==source?HYPER:HyperCharge.tier(held)<0&&weapon(held)?OTHER:IDLE;}
    private static long clock(ServerPlayer p){var level=(ServerLevel)p.level();var server=level.getServer();return server!=null&&server.overworld()!=null?server.overworld().getGameTime():level.getGameTime();}
    private static int capA(State s){return (int)Math.min(1000000,HyperConfig.integer("overload_a_cap_permille")*(s.enhanced?HyperConfig.number("magazine_overload_multiplier"):1));}
    private static int capB(State s){return (int)Math.min(1000000,HyperConfig.integer("overload_b_cap_permille")*(s.enhanced?HyperConfig.number("magazine_overload_multiplier"):1));}
    private static void accrue(State s,long elapsed){
        if(elapsed<=0||s.mode==IDLE)return;
        int partial=s.mode==HYPER?s.partialA:s.partialB;
        long steps=Math.min(72000,elapsed/20)+(partial+elapsed%20)/20;
        int rest=(int)((partial+elapsed%20)%20),gain=(int)Math.min(1000000,steps*HyperConfig.integer("overload_step_permille")*(s.enhanced?HyperConfig.number("magazine_overload_multiplier"):1));
        if(s.mode==HYPER){s.chargeA=Math.min(capA(s),s.chargeA+gain);s.partialA=rest;}
        else{s.chargeB=Math.min(capB(s),s.chargeB+gain);s.partialB=rest;}
    }
    private static State update(ServerPlayer p){
        if(p==null)return null;
        if(!HyperConfig.enabled("overload_enabled")||!p.isAlive()||p.isRemoved()||p.isSpectator()||!(p.level() instanceof ServerLevel)){STATES.remove(p.getUUID());return null;}
        var old=STATES.get(p.getUUID());var source=source(p,old);
        if(source.isEmpty()){STATES.remove(p.getUUID());return null;}
        long now=clock(p);
        if(old==null||old.source!=source||old.level!=p.level()||now<old.tick){var created=new State(p,source,now);STATES.put(p.getUUID(),created);return created;}
        accrue(old,now-old.tick);old.tick=now;
        boolean upgraded=enhanced(source);
        if(old.enhanced!=upgraded){old.enhanced=upgraded;old.chargeA=Math.min(capA(old),old.chargeA);old.chargeB=Math.min(capB(old),old.chargeB);old.activeA=Math.min(capA(old),old.activeA);old.activeB=Math.min(capB(old),old.activeB);old.partialA=old.partialB=0;}
        if(now>=old.untilA){old.activeA=0;old.untilA=0;}if(now>=old.untilB){old.activeB=0;old.untilB=0;}
        int next=mode(p,source);
        if(next!=old.mode){
            if(old.mode!=IDLE)old.pending=old.mode;
            if(next==HYPER){
                old.activeA=0;old.untilA=0;
                if(old.pending==OTHER){old.activeB=old.chargeB;old.untilB=old.activeB>0?now+HyperConfig.integer("overload_duration_ticks"):0;old.chargeB=old.partialB=0;}
                old.pending=IDLE;
            }else if(next==OTHER){
                old.activeB=0;old.untilB=0;
                if(old.pending==HYPER){old.activeA=old.chargeA;old.untilA=old.activeA>0?now+HyperConfig.integer("overload_duration_ticks"):0;old.chargeA=old.partialA=0;}
                old.pending=IDLE;
            }
            old.mode=next;
        }
        return old;
    }
    public static boolean available(ServerPlayer p){return update(p)!=null;}
    public static UUID token(ServerPlayer p){var s=update(p);return s==null?null:s.token;}
    public static boolean validToken(ServerPlayer p,UUID token){var s=update(p);return s!=null&&s.token.equals(token);}
    public static double multiplier(ServerPlayer p,ItemStack weapon){
        var s=update(p);if(s==null||!weapon(weapon)||!carries(p,weapon))return 1;
        int amount=weapon==s.source?s.activeB:HyperCharge.tier(weapon)<0?s.activeA:0;
        return 1+amount/1000d;
    }
    public static Snapshot snapshot(ServerPlayer p){
        var s=update(p);if(s==null)return new Snapshot(0,0,0,0,0,0,false,null);
        long now=clock(p);return new Snapshot(s.chargeA,s.chargeB,s.activeA,s.activeB,(int)Math.max(0,s.untilA-now),(int)Math.max(0,s.untilB-now),s.enhanced,s.token);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer p)update(p);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer p)STATES.remove(p.getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){STATES.clear();}
}
