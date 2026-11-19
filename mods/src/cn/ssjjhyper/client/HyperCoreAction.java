package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperCharge;
import cn.ssjjhyper.HyperCore;
import cn.ssjjhyper.HyperCoreNetwork;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;

/** The authored TORSO_SKILL left-hand gesture replaces native draw on returning from core charging. */
public final class HyperCoreAction {
    private static final String CLIP="hyper_core_return";
    private static final int TRACK=16;
    private static final LinkedHashSet<UUID> SEEN=new LinkedHashSet<>();
    private static ClientLevel level;
    private static HyperCoreNetwork.Return event;
    private static ItemStack stack;
    private static Object display,player;
    private static AnimationStateContext context;
    private static AnimationStateMachine<?> machine;
    private static AnimationController controller;
    private static int track=-1;
    private HyperCoreAction(){}

    private static void world(){
        var next=Minecraft.getInstance().level;
        if(next!=level){clear();SEEN.clear();level=next;}
    }
    public static void receive(HyperCoreNetwork.Return next){
        world();var mc=Minecraft.getInstance();
        if(level==null||mc.player==null||!next.valid()||!next.dimension().equals(level.dimension().location())
            ||!next.owner().equals(mc.player.getUUID())||next.entityId()!=mc.player.getId())return;
        if(next.cancel()){
            remember(next.id());if(event!=null&&event.id().equals(next.id()))clear();return;
        }
        if(SEEN.contains(next.id())||expired(next,level.getGameTime()))return;
        remember(next.id());clear();
        var held=mc.player.getMainHandItem();
        if(!mc.player.isAlive()||mc.player.isSpectator()||mc.player.getInventory().selected!=next.slot()
            ||HyperCharge.tier(held)<0||!HyperCore.appearance(held).equals(next.profile()))return;
        event=next;stack=held;player=mc.player;display=TimelessAPI.getGunDisplay(held).orElse(null);
        tick();
    }
    private static void remember(UUID id){SEEN.add(id);while(SEEN.size()>64)SEEN.remove(SEEN.iterator().next());}
    private static boolean expired(HyperCoreNetwork.Return next,long now){return now>=next.tick()+next.durationTicks();}
    /** Late packets seek into the actual clip rather than replaying the gesture after damage settled. */
    private static long elapsedNs(HyperCoreNetwork.Return next,long now){return Math.min(next.durationTicks()*50_000_000L,Math.max(0,now-next.tick())*50_000_000L);}
    private static boolean held(){
        var p=Minecraft.getInstance().player;
        return event!=null&&p!=null&&p==player&&p.isAlive()&&!p.isSpectator()
            &&p.getMainHandItem()==stack&&p.getInventory().selected==event.slot()
            &&HyperCharge.tier(stack)>=0&&HyperCore.appearance(stack).equals(event.profile())
            &&TimelessAPI.getGunDisplay(stack).orElse(null)==display;
    }
    private static boolean bound(){return context!=null&&machine!=null&&controller!=null&&context.getStateMachine()==machine&&machine.getContext()==context&&machine.isInitialized();}
    private static void replaceNativeDraw(){
        if(bound())HyperNativeActions.replaceDraw(context,controller);
    }
    public static boolean busy(){
        world();if(event==null)return false;
        if(level==null||expired(event,level.getGameTime())||!held()){clear();return false;}return true;
    }
    public static void tick(){
        world();if(event==null)return;
        if(level==null||expired(event,level.getGameTime())||!held()){clear();return;}
        if(context!=null){if(!bound()){clear();return;}replaceNativeDraw();return;}
        // Native item switching can initialize the new display one client tick later.
        // Keep the acknowledged return pending, without waiting for ordinary draw to finish.
        var current=TimelessAPI.getGunDisplay(stack).orElse(null);if(current==null||current!=display)return;
        var next=current.getAnimationStateMachine();if(next==null||!next.isInitialized())return;
        var value=next.getContext();if(value==null||value.getStateMachine()!=next||!value.hasAnimationPrototype(CLIP))return;
        value.ensureTracksAmount(0,TRACK+1);int index=value.getTrack(0,TRACK);if(index<0)return;
        context=value;machine=next;controller=next.getAnimationController();track=index;
        replaceNativeDraw();context.runAnimation(CLIP,track,false,1,0);
        var runner=controller.getAnimation(track);if(runner!=null)runner.setProgressNs(elapsedNs(event,level.getGameTime()));
    }
    /** The old controller survives a native machine exit; remove its runner and queued action directly. */
    private static void releaseTrack(AnimationController old,int index){
        if(old==null||index<0)return;var runner=old.getAnimation(index);
        if(runner!=null&&runner.getAnimation().name.equals(CLIP))old.removeAnimation(index);
    }
    private static void clear(){
        var old=controller;int index=track;event=null;stack=null;display=null;player=null;context=null;machine=null;controller=null;track=-1;
        releaseTrack(old,index);
    }
    public static void abandon(){clear();}
}
