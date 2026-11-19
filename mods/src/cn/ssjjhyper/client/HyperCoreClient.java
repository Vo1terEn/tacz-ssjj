package cn.ssjjhyper.client;

import cn.ssjjhyper.*;
import cn.ssjjcompat.ArsenalKeyBindings;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.client.input.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Input/status bridge for source-authored core return, colored target orbs and explosions. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperCoreClient {
    public static final KeyMapping KEY=ArsenalKeyBindings.create("key.ssjj_hyper.energy_core",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G);
    private static ClientLevel level;
    private static boolean active;
    private static int count;
    private static float fov=70;
    private static long sent=-100,viewSent=-100;
    private HyperCoreClient(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {@SubscribeEvent public static void register(RegisterKeyMappingsEvent e){ArsenalKeyBindings.register(e,KEY);}}
    private static void world(){var next=Minecraft.getInstance().level;if(next!=level){level=next;active=false;count=0;sent=viewSent=-100;HyperCoreAction.abandon();}}
    private static float aspect(){var w=Minecraft.getInstance().getWindow();return Math.max(.25f,Math.min(6,(float)w.getWidth()/Math.max(1,w.getHeight())));}
    private static boolean playable(){var mc=Minecraft.getInstance();return mc.player!=null&&level!=null&&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()&&mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed()&&mc.player.isAlive()&&!mc.player.isSpectator();}
    private static void inputs(){
        boolean locked=HyperCoreAction.busy();
        HyperActionKeys.update(HyperActionKeys.CORE,locked,InspectKey.INSPECT_KEY,MeleeKey.MELEE_KEY,ReloadKey.RELOAD_KEY,ShootKey.SHOOT_KEY);
        if(!locked)return;var mc=Minecraft.getInstance();ShootKey.SHOOT_KEY.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
        for(var key:new KeyMapping[]{InspectKey.INSPECT_KEY,MeleeKey.MELEE_KEY,ReloadKey.RELOAD_KEY}){key.setDown(false);while(key.consumeClick()){}}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(!e.usedConfiguredFov())return;float next=(float)e.getFOV();if(Float.isFinite(next))fov=Math.max(10,Math.min(170,next));}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeTick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.START){world();inputs();}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;world();HyperCoreAction.tick();inputs();var mc=Minecraft.getInstance();if(level==null||mc.player==null)return;long now=level.getGameTime();
        while(KEY.consumeClick())if(HyperConfig.enabled("core_enabled")&&playable()&&!HyperCoreAction.busy()&&HyperCharge.tier(mc.player.getMainHandItem())>=1&&now-sent>=5){sent=now;HyperCoreNetwork.input(mc.player.getInventory().selected,true,fov,aspect());}
        if(HyperConfig.enabled("core_enabled")&&active&&playable()&&now-viewSent>=2){viewSent=now;HyperCoreNetwork.input(mc.player.getInventory().selected,false,fov,aspect());}
    }
    /** Keep the acknowledged return owner current before native hand rendering. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeRender(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.START){world();HyperCoreAction.tick();inputs();}}
    public static void receive(HyperCoreNetwork.State m){world();var mc=Minecraft.getInstance();if(!m.valid()||level==null||mc.player==null||!m.dimension().equals(level.dimension().location()))return;active=m.active();count=m.count();if(m.selectedSlot()>=0)mc.player.getInventory().selected=m.selectedSlot();}
    public static void receive(HyperCoreNetwork.Orb m){world();if(!m.valid()||level==null||!m.dimension().equals(level.dimension().location()))return;HyperCoreEffects.accept(m);}
    public static void receive(HyperCoreNetwork.Return m){
        world();if(!m.valid()||level==null||!m.dimension().equals(level.dimension().location()))return;
        HyperCoreAction.receive(m);if(!m.cancel())HyperCoreAudio.returning(m.profile(),m.id(),m.center(),m.tick());inputs();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre e){if(playable()&&HyperCoreAction.busy()&&(e.getButton()==0||e.getButton()==1)){e.setCanceled(true);inputs();}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(InputEvent.InteractionKeyMappingTriggered e){if(playable()&&HyperCoreAction.busy()){e.setCanceled(true);e.setSwingHand(false);inputs();}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent e){if(e.getLogicalSide().isClient()&&e.getShooter()==Minecraft.getInstance().player&&HyperCoreAction.busy())e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void melee(GunMeleeEvent e){if(e.getLogicalSide().isClient()&&e.getShooter()==Minecraft.getInstance().player&&HyperCoreAction.busy())e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reload(GunReloadEvent e){if(e.getLogicalSide().isClient()&&e.getEntity()==Minecraft.getInstance().player&&HyperCoreAction.busy())e.setCanceled(true);}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent e){ /* Skill descriptions intentionally omitted from weapon tooltips. */ }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e){
        if(!e.getOverlay().id().equals(new net.minecraft.resources.ResourceLocation("minecraft","hotbar")))return;world();var mc=Minecraft.getInstance();if(!active||mc.player==null||mc.options.hideGui)return;
        var text=Component.literal("聚能核心 · 蓄能 "+count+"/"+HyperCoreRules.maxOrbs()+" · 切回引爆");int x=(e.getWindow().getGuiScaledWidth()-mc.font.width(text))/2;
        e.getGuiGraphics().drawString(mc.font,text,x,e.getWindow().getGuiScaledHeight()-65,0x66CCFF,true);
    }
}
