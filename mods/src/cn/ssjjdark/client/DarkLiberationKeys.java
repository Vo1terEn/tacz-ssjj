package cn.ssjjdark.client;

import cn.judgfire.BurstStun;
import cn.ssjjcompat.ArsenalKeyBindings;
import cn.ssjjdark.DarkLiberationNetwork;
import cn.ssjjdark.DarkWrath;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkLiberationKeys {
    private static final KeyMapping KEY=ArsenalKeyBindings.create("key.ssjj_dark.liberation",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V);
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static long requestAfter;
    private DarkLiberationKeys(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){ArsenalKeyBindings.register(event,KEY);}
    }
    public static Component skillKeyLabel(){return KEY.getTranslatedKeyMessage();}
    /** TaCZ routes a press immediately, before the server can acknowledge this cast.
     * Reserve this weapon's skill binding even if it is rebound over inspect/refit. */
    public static boolean reservesNative(KeyMapping binding){
        var mc=Minecraft.getInstance();
        return binding!=KEY&&binding.getName().startsWith("key.tacz.")&&KEY.getKey().getValue()>=0
            &&KEY.getKey().equals(binding.getKey())&&mc.player!=null&&mc.screen==null
            &&DarkWrath.tier(mc.player.getMainHandItem())==2;
    }
    public static boolean reserved(InputEvent.Key event){
        var mc=Minecraft.getInstance();return mc.player!=null&&mc.screen==null&&DarkWrath.tier(mc.player.getMainHandItem())==2
            &&event.getAction()==GLFW.GLFW_PRESS&&KEY.matches(event.getKey(),event.getScanCode());
    }
    private static void request(){
        var mc=Minecraft.getInstance();
        if(mc.level!=level){level=mc.level;requestAfter=0;}
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null||mc.isPaused()||!mc.isWindowActive()
            ||!mc.mouseHandler.isMouseGrabbed()||!mc.player.isAlive()||mc.player.isSpectator()
            ||DarkWrath.tier(mc.player.getMainHandItem())!=2||BurstStun.stunned(mc.player)||DarkLiberationClient.charging()
            ||!DarkLiberationClient.ready()||level.getGameTime()<requestAfter)return;
        // A physical press makes one request. A rejected request has no predicted cost, lock or cooldown.
        requestAfter=level.getGameTime()+10;
        DarkLiberationNetwork.request(mc.player.getInventory().selected);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key event){if(reserved(event))request();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Post event){
        if(event.getAction()==GLFW.GLFW_PRESS&&KEY.matchesMouse(event.getButton()))request();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase==TickEvent.Phase.END)while(KEY.consumeClick()){}
    }
}
