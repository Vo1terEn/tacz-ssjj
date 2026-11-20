package cn.ssjjdark.client;

import cn.ssjjdark.DarkLiberation;
import cn.ssjjdark.DarkLiberationNetwork;
import cn.ssjjdark.DarkWrath;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Only the successful server status starts the charge lock and the source-art HUD. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkLiberationClient {
    private static final Map<KeyMapping,IKeyConflictContext> CONTEXTS=new IdentityHashMap<>();
    private static ClientLevel level;
    private static DarkLiberationNetwork.Status snapshot;
    private static long received;
    private static boolean viewLocked;
    private static float lockedYaw,lockedPitch;
    private DarkLiberationClient(){}
    private static void world(){
        var current=Minecraft.getInstance().level;
        if(current!=level){level=current;snapshot=null;received=0;viewLocked=false;}
    }
    public static boolean charging(){
        world();var player=Minecraft.getInstance().player;
        return player!=null&&level!=null&&player.isAlive()&&!player.isSpectator()
            &&player.getPersistentData().getLong(DarkLiberation.CHARGING)>level.getGameTime();
    }
    private static DarkLiberationHudModel.Remaining remaining(){
        return snapshot==null?new DarkLiberationHudModel.Remaining(0,0,0,0):
            DarkLiberationHudModel.remaining(snapshot.chargeTicks(),snapshot.activeTicks(),snapshot.cooldownTicks(),snapshot.invulnerableTicks(),Math.max(0,level.getGameTime()-received));
    }
    public static boolean ready(){world();var state=remaining();return state.active()==0&&state.cooldown()==0;}
    public static void status(DarkLiberationNetwork.Status packet){
        world();var mc=Minecraft.getInstance();
        if(packet==null||!packet.valid()||level==null||mc.player==null||!level.dimension().location().equals(packet.dimension()))return;
        boolean wasCharging=charging();snapshot=packet;received=level.getGameTime();
        if(packet.chargeTicks()>0)mc.player.getPersistentData().putLong(DarkLiberation.CHARGING,received+packet.chargeTicks());
        else mc.player.getPersistentData().remove(DarkLiberation.CHARGING);
        if(packet.chargeTicks()>0){if(!wasCharging||!viewLocked)captureView();clearActions();freezeView();}
    }
    private static void captureView(){
        var player=Minecraft.getInstance().player;if(player==null)return;
        lockedYaw=player.getYRot();lockedPitch=player.getXRot();viewLocked=true;
    }
    private static void freezeView(){
        var player=Minecraft.getInstance().player;if(player==null)return;
        if(!viewLocked)captureView();
        player.setYRot(lockedYaw);player.yRotO=lockedYaw;
        player.setXRot(lockedPitch);player.xRotO=lockedPitch;
    }
    private static final class ChargeContext implements IKeyConflictContext {
        private final KeyMapping binding;
        private final IKeyConflictContext previous;
        private ChargeContext(KeyMapping binding,IKeyConflictContext previous){this.binding=binding;this.previous=previous;}
        @Override public boolean isActive(){return !charging()&&!DarkLiberationKeys.reservesNative(binding)&&previous.isActive();}
        @Override public boolean conflicts(IKeyConflictContext other){return previous.conflicts(other instanceof ChargeContext c?c.previous:other);}
    }
    private static void installContexts(){
        var mc=Minecraft.getInstance();
        for(var key:mc.options.keyMappings)if(!CONTEXTS.containsKey(key)){
            var previous=key.getKeyConflictContext();CONTEXTS.put(key,previous);key.setKeyConflictContext(new ChargeContext(key,previous));
        }
    }
    private static void clearActions(){
        var mc=Minecraft.getInstance();
        for(var key:mc.options.keyMappings){key.setDown(false);while(key.consumeClick()){}}
        if(mc.player==null)return;
        var input=mc.player.input;
        input.forwardImpulse=input.leftImpulse=0;
        input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
        mc.player.setSprinting(false);mc.player.setShiftKeyDown(false);mc.player.setDeltaMovement(Vec3.ZERO);
    }
    private static void restoreMovementKeys(){
        var mc=Minecraft.getInstance();
        for(var binding:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keySprint}){
            var key=binding.getKey();long window=mc.getWindow().getWindow();boolean down=false;
            if(key.getValue()>=0){
                if(key.getType()==InputConstants.Type.KEYSYM)down=InputConstants.isKeyDown(window,key.getValue());
                else if(key.getType()==InputConstants.Type.MOUSE)down=GLFW.glfwGetMouseButton(window,key.getValue())==GLFW.GLFW_PRESS;
                else if(key.getType()==InputConstants.Type.SCANCODE){
                    for(int code=GLFW.GLFW_KEY_SPACE;code<=GLFW.GLFW_KEY_LAST;code++)
                        if(namedKey(code)&&GLFW.glfwGetKeyScancode(code)==key.getValue()){down=InputConstants.isKeyDown(window,code);break;}
                }
            }
            binding.setDown(down&&binding.getKeyModifier().isActive(binding.getKeyConflictContext()));
        }
    }
    /** GLFW rejects numeric gaps between its named key constants. */
    private static boolean namedKey(int key){
        return key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_APOSTROPHE
            ||key>=GLFW.GLFW_KEY_COMMA&&key<=GLFW.GLFW_KEY_9
            ||key==GLFW.GLFW_KEY_SEMICOLON||key==GLFW.GLFW_KEY_EQUAL
            ||key>=GLFW.GLFW_KEY_A&&key<=GLFW.GLFW_KEY_RIGHT_BRACKET||key==GLFW.GLFW_KEY_GRAVE_ACCENT
            ||key>=GLFW.GLFW_KEY_WORLD_1&&key<=GLFW.GLFW_KEY_WORLD_2
            ||key>=GLFW.GLFW_KEY_ESCAPE&&key<=GLFW.GLFW_KEY_END
            ||key>=GLFW.GLFW_KEY_CAPS_LOCK&&key<=GLFW.GLFW_KEY_PAUSE
            ||key>=GLFW.GLFW_KEY_F1&&key<=GLFW.GLFW_KEY_F25
            ||key>=GLFW.GLFW_KEY_KP_0&&key<=GLFW.GLFW_KEY_KP_EQUAL
            ||key>=GLFW.GLFW_KEY_LEFT_SHIFT&&key<=GLFW.GLFW_KEY_MENU;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent event){
        world();installContexts();
        if(charging()){clearActions();freezeView();}
        else if(viewLocked){viewLocked=false;restoreMovementKeys();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void renderTick(TickEvent.RenderTickEvent event){
        if(event.phase==TickEvent.Phase.START&&charging())freezeView();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void camera(ViewportEvent.ComputeCameraAngles event){
        if(!charging()||Minecraft.getInstance().screen!=null||DarkLiberationPresentation.normalViewActive())return;
        var mc=Minecraft.getInstance();
        boolean mirrored=mc.options.getCameraType().isMirrored();
        freezeView();event.setYaw(lockedYaw+(mirrored?180:0));event.setPitch(mirrored?-lockedPitch:lockedPitch);event.setRoll(0);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void movement(MovementInputUpdateEvent event){
        if(event.getEntity()==Minecraft.getInstance().player&&charging()){clearActions();freezeView();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void interact(InputEvent.InteractionKeyMappingTriggered event){
        if(charging()){event.setCanceled(true);event.setSwingHand(false);clearActions();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre event){
        if(charging()&&Minecraft.getInstance().screen==null){event.setCanceled(true);clearActions();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent event){
        if(charging()&&Minecraft.getInstance().screen==null){event.setCanceled(true);clearActions();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key event){if(charging())clearActions();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void opening(ScreenEvent.Opening event){
        if(!charging())return;var screen=event.getNewScreen();
        // Pause, disconnect and world/death transitions must remain usable.
        if(screen==null||screen instanceof PauseScreen||screen instanceof DeathScreen||screen instanceof DisconnectedScreen
            ||screen instanceof TitleScreen||screen instanceof ReceivingLevelScreen||screen instanceof ProgressScreen)return;
        event.setCanceled(true);
    }
    private static ResourceLocation ui(String name){return new ResourceLocation("ssjj_dark","textures/gui/liberation/"+name+".png");}
    private static void sourceCard(GuiGraphics graphics,ResourceLocation texture,boolean active){
        if(active){graphics.blit(texture,0,0,0,0,210,90,210,90);return;}
        // Skip the baked E glyph, then restore its background from a blank source row.
        graphics.blit(texture,0,0,0,0,210,44,210,90);
        graphics.blit(texture,0,44,0,44,89,14,210,90);
        graphics.blit(texture,98,44,98,44,112,14,210,90);
        graphics.blit(texture,0,58,0,58,210,32,210,90);
        for(int row=44;row<58;row++)graphics.blit(texture,89,row,89,58,9,1,210,90);
        var mc=Minecraft.getInstance();Component key=DarkLiberationKeys.skillKeyLabel();
        float fit=Math.min(1.2f,9f/Math.max(1,mc.font.width(key)));
        graphics.pose().pushPose();graphics.pose().translate(89,46,0);graphics.pose().scale(fit,1.2f,1);
        graphics.drawString(mc.font,key,0,0,0x15def1,false);graphics.pose().popPose();
    }
    private static void shieldBar(GuiGraphics graphics,DarkLiberationNetwork.Status status){
        int left=76,top=10,width=125,height=17;
        int pixels=DarkLiberationHudModel.shieldPixels(status.shield(),status.shieldCapacity(),width);
        if(pixels==0)return;
        int end=left+pixels;
        graphics.fillGradient(left,top,end,top+height,0xff55c0ff,0xff207cce);
        graphics.fill(left,top,end,top+1,0xff9ee1ff);
        graphics.fill(left,top+height-1,end,top+height,0xff2c98dc);

        var font=Minecraft.getInstance().font;
        String label=DarkLiberationHudModel.shieldLabel(status.shield());
        float labelScale=Math.min(1.5f,(width-6f)/Math.max(1,font.width(label)));
        var pose=graphics.pose();
        pose.pushPose();
        pose.translate(left+width/2f,top+height/2f,0);
        pose.scale(labelScale,labelScale,1);
        graphics.drawCenteredString(font,label,0,-4,0xfff6fcff);
        pose.popPose();
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        world();var mc=Minecraft.getInstance();
        if(mc.player==null||level==null||mc.options.hideGui||mc.screen!=null||!mc.player.isAlive()||mc.player.isSpectator()||DarkWrath.tier(mc.player.getMainHandItem())!=2)return;
        var state=remaining();var graphics=event.getGuiGraphics();
        var layout=DarkLiberationHudModel.layout(event.getWindow().getGuiScaledWidth(),event.getWindow().getGuiScaledHeight());
        graphics.pose().pushPose();graphics.pose().translate(layout.x(),layout.y(),0);graphics.pose().scale(layout.scale(),layout.scale(),1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        sourceCard(graphics,ui(DarkLiberationHudModel.texture(state)),state.active()>0);
        int pixels=Math.round(125*DarkLiberationHudModel.progress(state));
        if(pixels>0)graphics.blit(ui(state.active()>0?"skill_progress_active":"skill_progress"),76,60,0,0,pixels,7,125,7);
        if(snapshot!=null&&state.active()>0)shieldBar(graphics,snapshot);
        RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();graphics.pose().popPose();
    }
}
