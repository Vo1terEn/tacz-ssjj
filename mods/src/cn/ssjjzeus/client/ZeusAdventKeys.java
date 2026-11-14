package cn.ssjjzeus.client;

import cn.ssjjzeus.ZeusAdventNetwork;
import cn.ssjjzeus.ZeusEnergy;
import cn.ssjjzeus.ZeusAdventRules;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.AnimationController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusAdventKeys {
    private static final KeyMapping KEY=new KeyMapping("key.ssjj_zeus.advent",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.eyeofpunishment");
    private static final int CAST_TICKS=37; // ceil((55 source frames / 30 fps) * 20).
    private static final CastGate GATE=new CastGate();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static int clock;
    private static ObjectAnimationRunner castAnimation;
    private static AnimationController castController;
    private static int castSlot=-1;
    private ZeusAdventKeys() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {@SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(KEY);}}
    public static Component skillKeyLabel(){return KEY.getTranslatedKeyMessage();}
    /** Kept across slots and gun displays, so putting a gun away cannot reset V. */
    public static final class CastGate {
        private int acceptedUntil,requestUntil;
        public boolean request(int tick,boolean animationRunning){
            if(tick<acceptedUntil||tick<requestUntil||animationRunning)return false;
            requestUntil=tick+20;return true;
        }
        public void accepted(int tick){requestUntil=0;acceptedUntil=Math.max(acceptedUntil,tick+Math.max(CAST_TICKS,ZeusAdventRules.castInterval()));}
        public void reset(){acceptedUntil=requestUntil=0;}
        public static boolean activeIn(AnimationController controller,ObjectAnimationRunner saved){
            if(controller==null||saved==null)return false;
            for(int track:controller.getUpdatingTrackArray()){
                var runner=controller.getAnimation(track);
                while(runner!=null&&runner.getTransitionTo()!=null)runner=runner.getTransitionTo();
                if(runner==saved)return !saved.isStopped();
            }
            return false;
        }
    }
    private static void world(){
        var current=Minecraft.getInstance().level;
        if(current!=level){level=current;clock=0;castAnimation=null;castController=null;castSlot=-1;GATE.reset();}
    }
    /** Called only for a successful server cast, never for a status refresh. */
    public static void castStarted(){world();GATE.accepted(clock);}
    /** Lock to the actual native runner as well as the server's minimum interval. */
    public static void animationStarted(){
        world();GATE.accepted(clock);var mc=Minecraft.getInstance();if(mc.player==null)return;
        TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(display->{
            var machine=display.getAnimationStateMachine();if(machine==null)return;
            var controller=machine.getAnimationController();
            for(int track:controller.getUpdatingTrackArray()){
                var runner=controller.getAnimation(track);
                while(runner!=null&&runner.getTransitionTo()!=null)runner=runner.getTransitionTo();
                if(runner!=null&&"zeus_advent".equals(runner.getAnimation().name)&&!runner.isStopped()){
                    castAnimation=runner;castController=controller;castSlot=mc.player.getInventory().selected;
                }
            }
        });
    }
    private static boolean currentCastRunning(){
        var mc=Minecraft.getInstance();
        boolean held=mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&mc.player.getInventory().selected==castSlot&&ZeusEnergy.tierII(mc.player.getMainHandItem());
        boolean active=held&&TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).map(display->{
            var machine=display.getAnimationStateMachine();
            return machine!=null&&machine.isInitialized()&&machine.getAnimationController()==castController
                &&CastGate.activeIn(castController,castAnimation);
        }).orElse(false);
        // Native display teardown may leave an old, unupdated runner frozen.
        // Discard only that visual reference; the successful-cast lock survives.
        if(!active){castAnimation=null;castController=null;castSlot=-1;}
        return active;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;world();
        if(level!=null&&!Minecraft.getInstance().isPaused()){clock++;currentCastRunning();}
    }
    public static boolean reserved(InputEvent.Key event){
        var mc=Minecraft.getInstance();return mc.player!=null&&mc.screen==null&&ZeusEnergy.tierII(mc.player.getMainHandItem())
            &&event.getAction()==GLFW.GLFW_PRESS&&KEY.matches(event.getKey(),event.getScanCode());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key event){
        if(!reserved(event))return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.getOverlay()!=null||mc.isPaused()||!mc.isWindowActive()||!mc.mouseHandler.isMouseGrabbed()
            ||!mc.player.isAlive()||mc.player.isSpectator())return;
        world();
        if(!GATE.request(clock,currentCastRunning()))return;
        ZeusAdventNetwork.request(mc.player.getInventory().selected);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Post event){
        if(event.getButton()!=GLFW.GLFW_MOUSE_BUTTON_RIGHT||event.getAction()!=GLFW.GLFW_PRESS)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null||mc.isPaused()||!mc.isWindowActive()
            ||!mc.mouseHandler.isMouseGrabbed()||!mc.player.isAlive()||mc.player.isSpectator()||!ZeusEnergy.tierII(mc.player.getMainHandItem()))return;
        // Publish the current native look rotation first, so a fast click uses this frame's crosshair.
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround()));
        ZeusAdventNetwork.requestFocus(mc.player.getInventory().selected);
        // Native right-button aiming continues normally; marking is one request per press.
    }
}
