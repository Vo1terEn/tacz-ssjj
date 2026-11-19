package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperChargeNetwork;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Original normal-to-special, special hold/walk/attack and special-to-normal actions. */
final class HyperChargeAction {
    private static ClientLevel level;
    private static ItemStack requestedStack,animatedStack;
    private static Object requestedPlayer,requestedDisplay,display;
    private static long requestedAt;
    private static int requestedSlot;
    private static UUID session,lastAttack;
    private static AnimationStateContext context;
    private static AnimationStateMachine<?> machine;
    private static AnimationController controller;
    private static int track;
    private static String phase;
    private static boolean finishing,suspended;
    private HyperChargeAction() {}
    private static void world() {
        var next=Minecraft.getInstance().level;
        if(next!=level){clear();cancelPending();session=null;lastAttack=null;level=next;}
    }
    static void requested() {
        world();cancelPending();var mc=Minecraft.getInstance();
        if(level==null||mc.player==null||!mc.player.isAlive()||HyperChargeClient.heldTier()==0)return;
        requestedStack=mc.player.getMainHandItem();requestedPlayer=mc.player;requestedSlot=mc.player.getInventory().selected;
        requestedDisplay=TimelessAPI.getGunDisplay(requestedStack).orElse(null);requestedAt=level.getGameTime();
    }
    private static boolean pending() {
        var mc=Minecraft.getInstance();
        return level!=null&&mc.player!=null&&mc.player==requestedPlayer&&mc.player.isAlive()&&HyperChargeClient.heldTier()>0
            &&mc.player.getMainHandItem()==requestedStack&&mc.player.getInventory().selected==requestedSlot
            &&level.getGameTime()>=requestedAt&&level.getGameTime()-requestedAt<=20
            &&TimelessAPI.getGunDisplay(requestedStack).orElse(null)==requestedDisplay;
    }
    static void receive(HyperChargeNetwork.Status state) {
        world();if(state.activeTicks()<=0){if(context!=null)finishing=true;return;}
        boolean fresh=!state.session().equals(session);session=state.session();
        if(!fresh||!pending()||state.slot()!=requestedSlot){cancelPending();return;}
        var stack=requestedStack;cancelPending();
        TimelessAPI.getGunDisplay(stack).ifPresent(current->{
            var next=current.getAnimationStateMachine();if(next==null||!next.isInitialized())return;
            var value=next.getContext();
            if(value==null||value.getStateMachine()!=next)return;
            for(String name:new String[]{"start","idle","walk","attack","end"})if(!value.hasAnimationPrototype("hyper_charge_"+name))return;
            clear();value.ensureTracksAmount(0,16);track=value.getTrack(0,15);if(track<0)return;
            context=value;machine=next;controller=next.getAnimationController();display=current;animatedStack=stack;play("start");
        });
    }
    static boolean attack(HyperChargeNetwork.Visual event) {
        world();if(event.id().equals(lastAttack)||context==null||!bound()||!held()||finishing)return false;
        if(Minecraft.getInstance().level.getGameTime()>=event.tick()+event.life())return false;
        lastAttack=event.id();if(nativeBusy())return false;
        play("attack");return true;
    }
    private static void play(String next) {
        if(!bound())return;phase=next;suspended=false;
        // Match the deployed runtime: keep the final pose until tick selects the
        // next phase. HOLD=0 prevents a default-pose frame between client ticks.
        context.runAnimation("hyper_charge_"+next,track,false,next.equals("idle")||next.equals("walk")?2:0,0);
    }
    private static void cancelPending(){requestedStack=null;requestedPlayer=null;requestedDisplay=null;requestedAt=0;requestedSlot=-1;}
    private static boolean bound(){return context!=null&&machine!=null&&context.getStateMachine()==machine&&machine.getContext()==context&&machine.isInitialized();}
    private static boolean held(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.isAlive()&&mc.player.getMainHandItem()==animatedStack&&HyperChargeClient.heldTier()>0&&TimelessAPI.getGunDisplay(animatedStack).orElse(null)==display;}
    private static boolean nativeBusy() {
        var p=Minecraft.getInstance().player;if(p==null)return true;
        // Native logical MAIN_TRACK differs between TaCZ 1.1.5 and newer releases.
        // Yield to that actual runner as well as network/local action guards.
        if(bound()&&HyperNativeActions.busy(context,controller))return true;
        var op=IGunOperator.fromLivingEntity(p);if(op==null)return true;
        var reload=op.getSynReloadState();
        if(op.getSynMeleeCoolDown()>0||op.getSynDrawCoolDown()>0||op.getSynIsBolting()||reload!=null&&reload.getStateType().isReloading())return true;
        var client=IClientPlayerGunOperator.fromLocalPlayer(p);if(client==null)return false;var data=client.getDataHolder();
        return data!=null&&(data.isBolting||data.clientStateLock);
    }
    /** A native hotbar switch exits the state machine before our END tick. Its controller survives. */
    private static void clear(){var old=controller;int index=track;context=null;machine=null;controller=null;display=null;animatedStack=null;track=0;phase=null;finishing=false;suspended=false;releaseTrack(old,index);}
    private static void releaseTrack(AnimationController old,int index){
        if(old==null||index<0)return;
        // Remove the owned runner and queue even while its machine is uninitialized.
        // A later native initialize otherwise resurrects the old special hold/attack.
        var runner=old.getAnimation(index);
        if(runner!=null&&runner.getAnimation().name.startsWith("hyper_charge_"))old.removeAnimation(index);
    }
    static void abandon(){clear();cancelPending();session=null;lastAttack=null;}
    // This is the same transition decision used by tick; tests exercise complete state sequences.
    private static String nextPhase(String current,boolean active,boolean stopped,boolean walking,boolean busy) {
        if(busy)return active?"suspend":"clear";
        if(!active){if("end".equals(current))return stopped?"clear":"keep";if("attack".equals(current)&&!stopped)return "keep";return "end";}
        if(("start".equals(current)||"attack".equals(current))&&!stopped)return "keep";
        return walking?"walk":"idle";
    }
    private static boolean completed(){return context.isStopped(track)||context.isHolding(track);}
    static void tick() {
        world();if(requestedStack!=null&&!pending())cancelPending();if(context==null)return;
        if(!bound()||!held()){clear();return;}var mc=Minecraft.getInstance();
        boolean walking=mc.player.input!=null&&(Math.abs(mc.player.input.forwardImpulse)>1e-4||Math.abs(mc.player.input.leftImpulse)>1e-4);
        String next=nextPhase(phase,!finishing&&HyperChargeClient.activeTicks()>0,completed(),walking,nativeBusy());
        if(next.equals("clear")){clear();return;}
        if(next.equals("suspend")){if(!suspended)context.stopAnimation(track);suspended=true;return;}
        if(!next.equals("keep")&&(suspended||!next.equals(phase)||completed()))play(next);
    }
}
